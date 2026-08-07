"""FastAPI 진입점.

이 서버는 외부에 노출하지 않는다. Spring 프록시만 부른다.
- 사용자 신원은 Authorization 헤더의 사용자 토큰으로만 판단한다.
  이 서버는 토큰을 검증하지 않는다. 검증하는 곳은 Spring 이다.
- X-Internal-Secret 이 맞아야 요청을 받는다.
"""

from __future__ import annotations

import hmac
import logging

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from .config import get_settings
from .rag import indexer
from .runner import NotAMember, resume, start, verify_membership
from .spring_client import SpringClient, SpringError, SpringUnreachable

logging.basicConfig(level=logging.INFO)
log = logging.getLogger(__name__)

app = FastAPI(title="WorkTaskFlow AI", docs_url=None, redoc_url=None, openapi_url=None)


def require_internal(
    x_internal_secret: str = Header(default=""),
    authorization: str = Header(default=""),
) -> str:
    settings = get_settings()
    if not settings.enabled:
        raise HTTPException(503, "AI 기능이 꺼져 있습니다.")
    missing = settings.missing_requirements()
    if missing:
        raise HTTPException(503, f"설정이 비어 있습니다: {', '.join(missing)}")
    if not hmac.compare_digest(x_internal_secret, settings.internal_secret):
        raise HTTPException(403, "내부 호출이 아닙니다.")
    if not authorization.lower().startswith("bearer "):
        raise HTTPException(401, "사용자 토큰이 없습니다.")
    return authorization[7:]


class Contract(BaseModel):
    """Spring DTO 와 같은 camelCase 로 주고받는다.

    이름이 어긋나면 필드가 조용히 null 이 된다. threadId 가 그렇게 유실되면
    승인 재개가 통째로 깨지는데 에러는 안 난다. 별칭을 한곳에서 강제한다.
    """

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ChatRequest(Contract):
    group_id: int = Field(gt=0)
    message: str = Field(min_length=1, max_length=2000)
    thread_id: str | None = Field(default=None, max_length=64)


class ResumeRequest(Contract):
    group_id: int = Field(gt=0)
    thread_id: str = Field(min_length=1, max_length=64)
    approved: bool
    note: str = Field(default="", max_length=500)


class TurnResponse(Contract):
    thread_id: str
    status: str
    reply: str
    pending: dict | None = None


class IndexResponse(Contract):
    indexed: int
    skipped: int
    removed: int
    unsupported: int
    failures: list[str]


@app.get("/internal/health")
def health() -> dict:
    settings = get_settings()
    return {
        "status": "ok" if settings.enabled and not settings.missing_requirements() else "disabled",
        "enabled": settings.enabled,
        "missing": settings.missing_requirements(),
    }


@app.post("/internal/chat", response_model=TurnResponse)
def chat(request: ChatRequest, token: str = Depends(require_internal)) -> TurnResponse:
    turn = _guard(lambda: start(token, request.group_id, request.message, request.thread_id))
    return TurnResponse(thread_id=turn.thread_id, status=turn.status, reply=turn.reply, pending=turn.pending)


@app.post("/internal/resume", response_model=TurnResponse)
def resume_turn(request: ResumeRequest, token: str = Depends(require_internal)) -> TurnResponse:
    turn = _guard(
        lambda: resume(token, request.group_id, request.thread_id, request.approved, request.note)
    )
    return TurnResponse(thread_id=turn.thread_id, status=turn.status, reply=turn.reply, pending=turn.pending)


@app.post("/internal/groups/{group_id}/index", response_model=IndexResponse)
def reindex(group_id: int, token: str = Depends(require_internal)) -> IndexResponse:
    def run():
        verify_membership(token, group_id)
        with SpringClient(token) as client:
            return indexer.reindex_group(client, group_id)

    result = _guard(run)
    return IndexResponse(
        indexed=result.indexed, skipped=result.skipped, removed=result.removed,
        unsupported=result.unsupported, failures=result.failures,
    )


def _guard(action):
    try:
        return action()
    except NotAMember as error:
        raise HTTPException(403, str(error)) from error
    except SpringError as error:
        # Spring 의 판단을 그대로 전달한다. 여기서 다시 해석하지 않는다.
        raise HTTPException(error.status, error.message) from error
    except SpringUnreachable as error:
        raise HTTPException(502, str(error)) from error
    except Exception as error:
        log.exception("AI 요청 처리 실패")
        raise HTTPException(500, f"처리 중 오류가 발생했습니다: {type(error).__name__}") from error
