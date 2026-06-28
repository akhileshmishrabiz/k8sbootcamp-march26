import asyncio
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from config import settings
from rag.chunker import chunk_text
from rag.ollama_client import (
    check_models_ready,
    check_ollama_ready,
    embed_texts,
    generate_answer,
    generate_chat,
)
from rag.qdrant_store import QdrantStore

store: QdrantStore | None = None


async def wait_for_dependencies() -> None:
    for _ in range(90):
        ollama_ready = await check_ollama_ready()
        models_ready = await check_models_ready()
        try:
            qdrant = QdrantStore()
            qdrant.client.get_collections()
            qdrant_ready = True
        except Exception:
            qdrant_ready = False

        if ollama_ready and models_ready and qdrant_ready:
            return

        await asyncio.sleep(5)

    raise RuntimeError("Timed out waiting for Ollama models and Qdrant")


@asynccontextmanager
async def lifespan(_: FastAPI):
    global store
    await wait_for_dependencies()
    store = QdrantStore()
    probe_vectors = await embed_texts(["startup probe"])
    store.ensure_collection(vector_size=len(probe_vectors[0]))
    yield


app = FastAPI(
    title="RAG API",
    description="MVP RAG pipeline: ingest, chunk, embed, retrieve, generate",
    version="0.1.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class ChatRequest(BaseModel):
    question: str = Field(min_length=1, max_length=4000)
    top_k: int | None = Field(default=None, ge=1, le=20)


class DocumentCreateRequest(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    content: str = Field(min_length=1)


def build_rag_prompt(question: str, contexts: list[dict]) -> str:
    if not contexts:
        context_block = "No relevant documents were found."
    else:
        lines = []
        for index, item in enumerate(contexts, start=1):
            lines.append(
                f"[{index}] Source: {item['doc_title']} (chunk {item['chunk_index']})\n{item['text']}"
            )
        context_block = "\n\n".join(lines)

    return (
        "You are a helpful assistant. Answer the question using ONLY the context below. "
        "If the context does not contain the answer, say you do not know.\n\n"
        f"Context:\n{context_block}\n\n"
        f"Question: {question}\n\n"
        "Answer:"
    )


async def ingest_document(title: str, content: str) -> dict:
    if store is None:
        raise HTTPException(status_code=503, detail="Vector store is not ready")

    chunks = chunk_text(content, settings.chunk_size, settings.chunk_overlap)
    if not chunks:
        raise HTTPException(status_code=400, detail="Document has no usable text")

    vectors = await embed_texts(chunks)
    doc_id = str(uuid.uuid4())
    chunk_count = store.upsert_chunks(doc_id, title, chunks, vectors)

    return {
        "doc_id": doc_id,
        "title": title,
        "chunk_count": chunk_count,
        "message": "Document ingested successfully",
    }


@app.get("/health")
async def health():
    ollama_ready = await check_ollama_ready()
    qdrant_ready = False
    if store is not None:
        try:
            store.client.get_collections()
            qdrant_ready = True
        except Exception:
            qdrant_ready = False

    status = "ok" if ollama_ready and qdrant_ready else "degraded"
    return {
        "status": status,
        "ollama": ollama_ready,
        "qdrant": qdrant_ready,
        "embed_model": settings.embed_model,
        "llm_model": settings.llm_model,
    }


@app.get("/api/documents")
async def list_documents():
    if store is None:
        raise HTTPException(status_code=503, detail="Vector store is not ready")
    documents = store.list_documents()
    return {"documents": documents, "count": len(documents)}


@app.post("/api/documents")
async def create_document(payload: DocumentCreateRequest):
    return await ingest_document(payload.title, payload.content)


@app.post("/api/documents/upload")
async def upload_document(
    title: str = Form(...),
    file: UploadFile = File(...),
):
    raw = await file.read()
    try:
        content = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise HTTPException(status_code=400, detail="Only UTF-8 text files are supported in MVP") from exc

    doc_title = title.strip() or (file.filename or "Uploaded document")
    return await ingest_document(doc_title, content)


@app.delete("/api/documents/{doc_id}")
async def delete_document(doc_id: str):
    if store is None:
        raise HTTPException(status_code=503, detail="Vector store is not ready")
    store.delete_document(doc_id)
    return {"doc_id": doc_id, "message": "Document deleted"}


@app.post("/api/chat")
async def chat(payload: ChatRequest):
    question = payload.question.strip()
    top_k = payload.top_k or settings.top_k

    if not await check_ollama_ready():
        raise HTTPException(status_code=503, detail="LLM is not ready yet")

    has_documents = store is not None and bool(store.list_documents())
    if not has_documents:
        answer = await generate_chat(question)
        return {
            "question": question,
            "answer": answer,
            "mode": "chat",
            "sources": [],
        }

    if store is None:
        raise HTTPException(status_code=503, detail="Vector store is not ready")

    query_vector = (await embed_texts([question]))[0]
    contexts = store.search(query_vector, top_k=top_k)
    prompt = build_rag_prompt(question, contexts)
    answer = await generate_answer(prompt)

    return {
        "question": question,
        "answer": answer,
        "mode": "rag",
        "sources": [
            {
                "doc_id": item["doc_id"],
                "doc_title": item["doc_title"],
                "chunk_index": item["chunk_index"],
                "score": item["score"],
                "excerpt": item["text"][:240],
            }
            for item in contexts
        ],
    }
