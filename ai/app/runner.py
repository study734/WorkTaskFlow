"""Agent 실행과 재개. interrupt 를 응답 모양으로 번역하는 자리."""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Any

from langgraph.types import Command

from . import context
from .agent.graph import agent, thread_config
from .spring_client import SpringClient


@dataclass
class AgentTurn:
    thread_id: str
    status: str  # "completed" | "awaiting_approval"
    reply: str
    pending: dict[str, Any] | None = None


class NotAMember(RuntimeError):
    pass


def verify_membership(token: str, group_id: int) -> None:
    """RAG 필터와 도구가 쓸 group_id 가 정말 이 사용자의 그룹인지 확인한다.

    Spring 호출은 어차피 매번 권한을 검사하지만, Chroma 검색은 Spring 을
    거치지 않는다. 그래서 여기서 한 번 막아 둔다.
    """
    with SpringClient(token) as client:
        groups = client.my_groups()
    if not any(int(group["id"]) == group_id for group in groups):
        raise NotAMember("이 그룹에 접근할 수 없습니다.")


def start(token: str, group_id: int, message: str, thread_id: str | None = None) -> AgentTurn:
    verify_membership(token, group_id)
    thread = thread_id or uuid.uuid4().hex
    with context.use(context.RequestContext(user_token=token, group_id=group_id)):
        state = agent().invoke(
            {"messages": [{"role": "user", "content": message}]}, thread_config(thread)
        )
    return _turn(thread, state)


def resume(token: str, group_id: int, thread_id: str, approved: bool, note: str = "") -> AgentTurn:
    verify_membership(token, group_id)
    with context.use(context.RequestContext(user_token=token, group_id=group_id)):
        state = agent().invoke(
            Command(resume={"approved": approved, "note": note}), thread_config(thread_id)
        )
    return _turn(thread_id, state)


def _turn(thread_id: str, state: dict[str, Any]) -> AgentTurn:
    interrupts = state.get("__interrupt__") or []
    if interrupts:
        payload = interrupts[0].value
        request = payload[0] if isinstance(payload, list) and payload else payload
        return AgentTurn(
            thread_id=thread_id,
            status="awaiting_approval",
            reply=str(request.get("summary", "승인이 필요합니다.")) if isinstance(request, dict) else "승인이 필요합니다.",
            pending=request if isinstance(request, dict) else {"summary": str(request)},
        )
    for message in reversed(state.get("messages", [])):
        if getattr(message, "type", None) != "ai":
            continue
        reply = _text_of(message.content)
        if reply:
            return AgentTurn(thread_id=thread_id, status="completed", reply=reply)
    return AgentTurn(thread_id=thread_id, status="completed", reply="")


def _text_of(content: Any) -> str:
    """Responses API 는 content 를 블록 목록으로 준다. 평문만 뽑는다."""
    if isinstance(content, str):
        return content.strip()
    if isinstance(content, list):
        parts = []
        for block in content:
            if isinstance(block, str):
                parts.append(block)
            elif isinstance(block, dict) and block.get("type") == "text":
                parts.append(block.get("text", ""))
        return "\n".join(part for part in parts if part).strip()
    return str(content or "").strip()
