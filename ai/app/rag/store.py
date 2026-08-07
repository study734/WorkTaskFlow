"""Chroma 벡터 저장소.

단일 컬렉션에 모든 그룹의 자료를 넣고 metadata.group_id 로 가른다.
그룹마다 컬렉션을 나누면 그룹 수만큼 컬렉션이 늘어난다.

필터 값은 절대 LLM 이 고르지 않는다. 호출자가 인증으로 확인한 group_id 를
넘겨야 하고, 그 값이 그대로 where 절이 된다. LLM 이 group_id 를 정할 수
있으면 그게 곧 다른 팀 자료로 가는 통로다.
"""

from __future__ import annotations

import threading
from functools import lru_cache

from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_openai import OpenAIEmbeddings

from ..config import get_settings

COLLECTION = "group_resources"

# Chroma PersistentClient 는 프로세스 내 동시 쓰기에 취약하다.
# 색인은 드물게 일어나므로 락 하나로 직렬화한다. uvicorn 워커는 1개로 둔다.
_write_lock = threading.Lock()


@lru_cache
def vector_store() -> Chroma:
    settings = get_settings()
    settings.chroma_dir.mkdir(parents=True, exist_ok=True)
    embeddings = OpenAIEmbeddings(
        model=settings.embedding_model,
        api_key=settings.openai_api_key,
        base_url=settings.openai_base_url,
    )
    return Chroma(
        collection_name=COLLECTION,
        embedding_function=embeddings,
        persist_directory=str(settings.chroma_dir),
    )


def chunk_id(resource_id: int, index: int) -> str:
    return f"resource-{resource_id}-{index}"


def indexed_resource_ids(group_id: int) -> set[int]:
    """이 그룹에서 이미 색인된 자료 ID. 증분 색인의 기준이 된다."""
    result = vector_store().get(where={"group_id": group_id}, include=["metadatas"])
    return {int(meta["resource_id"]) for meta in result.get("metadatas", []) if meta}


def add_chunks(documents: list[Document], ids: list[str]) -> None:
    if not documents:
        return
    with _write_lock:
        vector_store().add_documents(documents=documents, ids=ids)


def delete_resource(group_id: int, resource_id: int) -> None:
    with _write_lock:
        vector_store().delete(where={"$and": [{"group_id": group_id}, {"resource_id": resource_id}]})


def search(group_id: int, query: str, limit: int = 5) -> list[tuple[Document, float]]:
    """group_id 는 호출자가 인증으로 확인한 값이어야 한다."""
    return vector_store().similarity_search_with_relevance_scores(
        query, k=limit, filter={"group_id": group_id}
    )
