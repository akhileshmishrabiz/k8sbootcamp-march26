from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env")

    ollama_url: str = "http://localhost:11434"
    qdrant_url: str = "http://localhost:6333"
    embed_model: str = "nomic-embed-text"
    llm_model: str = "llama3.2:3b"
    collection_name: str = "rag_chunks"
    chunk_size: int = 500
    chunk_overlap: int = 50
    top_k: int = 5


settings = Settings()
