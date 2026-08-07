"""그룹 자료 증분 색인.

자료는 한 번 올리면 내용이 바뀌지 않는다(수정 API 가 없다). 그래서
자료 ID 만으로 색인 여부를 판단할 수 있다. 목록에서 사라진 자료는
삭제된 것이므로 색인에서도 지운다.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field

from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter

from ..spring_client import SpringClient
from . import parsers, store

log = logging.getLogger(__name__)

_splitter = RecursiveCharacterTextSplitter(
    chunk_size=900,
    chunk_overlap=150,
    separators=["\n\n", "\n", ". ", " ", ""],
)


@dataclass
class IndexResult:
    indexed: int = 0
    skipped: int = 0
    removed: int = 0
    unsupported: int = 0
    failures: list[str] = field(default_factory=list)


def reindex_group(client: SpringClient, group_id: int) -> IndexResult:
    result = IndexResult()
    resources = client.group_resources(group_id)
    live_ids = {int(item["id"]) for item in resources}
    already = store.indexed_resource_ids(group_id)

    for stale_id in already - live_ids:
        store.delete_resource(group_id, stale_id)
        result.removed += 1

    for resource in resources:
        resource_id = int(resource["id"])
        if resource_id in already:
            result.skipped += 1
            continue
        if resource.get("type") != "FILE":
            # 외부 링크는 본문을 가져오지 않는다. 제목만으로는 색인 가치가 없다.
            result.skipped += 1
            continue
        filename = resource.get("originalFilename")
        if not parsers.can_extract(filename):
            result.unsupported += 1
            continue
        try:
            content = client.download_resource(resource_id)
            text = parsers.extract(content, filename)
        except Exception as error:  # 자료 하나가 실패해도 나머지 색인은 계속한다.
            log.warning("자료 %s 색인 실패: %s", resource_id, error)
            result.failures.append(f"{resource.get('title')}: {error}")
            continue
        if not text.strip():
            result.unsupported += 1
            continue
        _add(group_id, resource, text)
        result.indexed += 1

    return result


def _add(group_id: int, resource: dict, text: str) -> None:
    chunks = _splitter.split_text(text)
    documents, ids = [], []
    for index, chunk in enumerate(chunks):
        documents.append(
            Document(
                page_content=chunk,
                metadata={
                    "group_id": group_id,
                    "resource_id": int(resource["id"]),
                    "title": resource.get("title") or "",
                    "filename": resource.get("originalFilename") or "",
                    "task_id": resource.get("taskId") or 0,
                    "chunk": index,
                },
            )
        )
        ids.append(store.chunk_id(int(resource["id"]), index))
    store.add_chunks(documents, ids)
