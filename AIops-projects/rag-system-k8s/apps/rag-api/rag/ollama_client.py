import httpx

from config import settings


async def embed_texts(texts: list[str]) -> list[list[float]]:
    vectors: list[list[float]] = []
    async with httpx.AsyncClient(timeout=120.0) as client:
        for text in texts:
            response = await client.post(
                f"{settings.ollama_url}/api/embeddings",
                json={"model": settings.embed_model, "prompt": text},
            )
            response.raise_for_status()
            vectors.append(response.json()["embedding"])
    return vectors


async def generate_answer(prompt: str) -> str:
    async with httpx.AsyncClient(timeout=600.0) as client:
        response = await client.post(
            f"{settings.ollama_url}/api/generate",
            json={
                "model": settings.llm_model,
                "prompt": prompt,
                "stream": False,
                "options": {"num_predict": 256, "temperature": 0.2},
            },
        )
        response.raise_for_status()
        return response.json()["response"].strip()


async def generate_chat(question: str) -> str:
    prompt = (
        "You are a helpful assistant. Answer the user's question clearly and concisely.\n\n"
        f"Question: {question}\n\n"
        "Answer:"
    )
    return await generate_answer(prompt)


async def check_ollama_ready() -> bool:
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            response = await client.get(f"{settings.ollama_url}/api/tags")
            return response.status_code == 200
    except httpx.HTTPError:
        return False


def _model_available(model_name: str, installed: list[str]) -> bool:
    prefix = f"{model_name}:"
    return any(name == model_name or name.startswith(prefix) for name in installed)


async def check_models_ready() -> bool:
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            response = await client.get(f"{settings.ollama_url}/api/tags")
            response.raise_for_status()
            installed = [item["name"] for item in response.json().get("models", [])]
            return _model_available(settings.embed_model, installed) and _model_available(
                settings.llm_model, installed
            )
    except httpx.HTTPError:
        return False
