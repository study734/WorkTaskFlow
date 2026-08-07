"""요청 단위 컨텍스트.

도구는 모듈 수준 함수라 인자로 사용자 토큰을 받을 수 없다.
LLM 이 토큰이나 그룹 ID 를 지어내지 못하게 하려면 그 값들이
아예 도구 스키마에 없어야 한다. 그래서 contextvar 로 넘긴다.
"""

from __future__ import annotations

from contextlib import contextmanager
from contextvars import ContextVar
from dataclasses import dataclass


@dataclass(frozen=True)
class RequestContext:
    user_token: str
    group_id: int


_current: ContextVar[RequestContext | None] = ContextVar("ai_request_context", default=None)


class ContextMissing(RuntimeError):
    pass


def current() -> RequestContext:
    context = _current.get()
    if context is None:
        raise ContextMissing("요청 컨텍스트가 없습니다. 도구를 요청 밖에서 호출했습니다.")
    return context


@contextmanager
def use(context: RequestContext):
    token = _current.set(context)
    try:
        yield context
    finally:
        _current.reset(token)
