"""HITL 흐름 검증.

확인하는 것
  1. 쓰기 도구가 승인 전에 실행되지 않는다.
  2. 승인하면 실행되고 대화가 이어진다.
  3. 거절하면 실행되지 않는다.
  4. 프로세스가 죽어도 승인 대기가 살아남는다(체크포인터 파일 재열기).
"""

from __future__ import annotations

import sqlite3

import pytest
from langchain_core.messages import AIMessage
from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.prebuilt import create_react_agent
from langgraph.types import Command

from app import context, tools
from app.agent.prompts import SYSTEM_PROMPT
from tests.fakes import FakeSpring, ScriptedChatModel

CREATE_CALL = AIMessage(
    content="",
    tool_calls=[{
        "name": "create_task",
        "args": {"title": "배포 점검", "priority": "HIGH"},
        "id": "call-1",
    }],
)
FINAL = AIMessage(content="처리했습니다.")


@pytest.fixture
def spring(monkeypatch):
    fake = FakeSpring()
    monkeypatch.setattr(tools, "_client", lambda: fake)
    return fake


def build_agent(db_path: str):
    model = ScriptedChatModel(responses=[CREATE_CALL, FINAL])
    saver = SqliteSaver(sqlite3.connect(db_path, check_same_thread=False))
    saver.setup()
    return create_react_agent(model, tools.ALL_TOOLS, prompt=SYSTEM_PROMPT, checkpointer=saver)


def run(agent, payload, thread="t1"):
    with context.use(context.RequestContext(user_token="token", group_id=1)):
        return agent.invoke(payload, {"configurable": {"thread_id": thread}})


def test_write_tool_pauses_before_executing(tmp_path, spring):
    agent = build_agent(str(tmp_path / "cp.sqlite"))
    state = run(agent, {"messages": [{"role": "user", "content": "배포 점검 업무 만들어줘"}]})

    interrupts = state.get("__interrupt__")
    assert interrupts, "승인 대기로 멈춰야 한다"
    request = interrupts[0].value
    assert request["action"] == "create_task"
    assert request["details"]["title"] == "배포 점검"
    assert spring.writes == [], "승인 전에는 아무것도 쓰지 않아야 한다"


def test_approval_executes_write(tmp_path, spring):
    agent = build_agent(str(tmp_path / "cp.sqlite"))
    run(agent, {"messages": [{"role": "user", "content": "만들어줘"}]})

    state = run(agent, Command(resume={"approved": True, "note": ""}))

    assert not state.get("__interrupt__")
    assert [name for name, _ in spring.writes] == ["create_task"]
    assert spring.writes[0][1]["title"] == "배포 점검"


def test_rejection_skips_write(tmp_path, spring):
    agent = build_agent(str(tmp_path / "cp.sqlite"))
    run(agent, {"messages": [{"role": "user", "content": "만들어줘"}]})

    run(agent, Command(resume={"approved": False, "note": "지금은 하지 마"}))

    assert spring.writes == [], "거절했으면 쓰기가 없어야 한다"


def test_pending_approval_survives_restart(tmp_path, spring):
    """같은 파일을 새 연결로 다시 열어 프로세스 재시작을 흉내낸다."""
    db_path = str(tmp_path / "cp.sqlite")
    first = build_agent(db_path)
    run(first, {"messages": [{"role": "user", "content": "만들어줘"}]})

    revived = build_agent(db_path)
    state = run(revived, Command(resume={"approved": True, "note": ""}))

    assert not state.get("__interrupt__")
    assert [name for name, _ in spring.writes] == ["create_task"]
