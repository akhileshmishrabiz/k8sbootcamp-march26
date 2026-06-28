import uuid
from urllib.parse import urlparse

from qdrant_client import QdrantClient
from qdrant_client.models import Distance, FieldCondition, Filter, MatchValue, PointStruct, VectorParams

from config import settings


def _parse_qdrant_host() -> tuple[str, int]:
    parsed = urlparse(settings.qdrant_url)
    host = parsed.hostname or "localhost"
    port = parsed.port or 6333
    return host, port


class QdrantStore:
    def __init__(self) -> None:
        host, port = _parse_qdrant_host()
        self.client = QdrantClient(host=host, port=port)
        self.collection = settings.collection_name

    def ensure_collection(self, vector_size: int) -> None:
        collections = [item.name for item in self.client.get_collections().collections]
        if self.collection not in collections:
            self.client.create_collection(
                collection_name=self.collection,
                vectors_config=VectorParams(size=vector_size, distance=Distance.COSINE),
            )

    def upsert_chunks(
        self,
        doc_id: str,
        doc_title: str,
        chunks: list[str],
        vectors: list[list[float]],
    ) -> int:
        points = []
        for index, (chunk, vector) in enumerate(zip(chunks, vectors)):
            points.append(
                PointStruct(
                    id=str(uuid.uuid4()),
                    vector=vector,
                    payload={
                        "doc_id": doc_id,
                        "doc_title": doc_title,
                        "chunk_index": index,
                        "text": chunk,
                    },
                )
            )
        self.client.upsert(collection_name=self.collection, points=points)
        return len(points)

    def search(self, query_vector: list[float], top_k: int) -> list[dict]:
        results = self.client.search(
            collection_name=self.collection,
            query_vector=query_vector,
            limit=top_k,
        )
        return [
            {
                "score": hit.score,
                "doc_id": hit.payload.get("doc_id"),
                "doc_title": hit.payload.get("doc_title"),
                "chunk_index": hit.payload.get("chunk_index"),
                "text": hit.payload.get("text"),
            }
            for hit in results
        ]

    def list_documents(self) -> list[dict]:
        seen: dict[str, dict] = {}
        offset = None
        while True:
            points, offset = self.client.scroll(
                collection_name=self.collection,
                limit=100,
                offset=offset,
                with_payload=True,
                with_vectors=False,
            )
            for point in points:
                doc_id = point.payload.get("doc_id")
                if not doc_id or doc_id in seen:
                    continue
                seen[doc_id] = {
                    "doc_id": doc_id,
                    "title": point.payload.get("doc_title", "Untitled"),
                }
            if offset is None:
                break

        return sorted(seen.values(), key=lambda item: item["title"].lower())

    def delete_document(self, doc_id: str) -> None:
        self.client.delete(
            collection_name=self.collection,
            points_selector=Filter(
                must=[FieldCondition(key="doc_id", match=MatchValue(value=doc_id))]
            ),
        )
