"""LangGraph Agent.

체크포인터가 있어야 interrupt 이후 재개가 된다. InMemorySaver 를 쓰면
프로세스가 재시작될 때 승인 대기 중이던 작업이 전부 사라진다.
MySQL 용 공식 체크포인터가 없어서 SQLite 파일을 볼륨에 둔다.
"""

from __future__ import annotations

import logging
import sqlite3
from datetime import datetime
from functools import lru_cache
from typing import Any
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from langchain_openai import ChatOpenAI
from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.prebuilt import create_react_agent

from ..config import get_settings
from ..tools import ALL_TOOLS
from .prompts import SYSTEM_PROMPT

log = logging.getLogger(__name__)


@lru_cache
def _checkpointer() -> SqliteSaver:
    settings = get_settings()
    settings.checkpoint_db.parent.mkdir(parents=True, exist_ok=True)
    # FastAPI 는 요청을 여러 스레드에서 처리하므로 check_same_thread 를 끈다.
    connection = sqlite3.connect(str(settings.checkpoint_db), check_same_thread=False)
    saver = SqliteSaver(connection)
    saver.setup()
    return saver


@lru_cache
def agent():
    settings = get_settings()
    # Responses API 를 쓴다. 추론 모델은 /v1/chat/completions 에서 function tool 과
    # reasoning_effort 를 같이 못 쓴다("use /v1/responses or set reasoning_effort to 'none'").
    # 추론을 끄는 대신 Responses API 로 가는 편이 도구 선택 품질에 낫다.
    # Spring 의 AI 리포트도 같은 API 를 쓰고 있다.
    model = ChatOpenAI(
        model=settings.chat_model,
        api_key=settings.openai_api_key,
        base_url=settings.openai_base_url,
        timeout=settings.request_timeout_seconds,
        use_responses_api=settings.use_responses_api,
    )
    return create_react_agent(
        model,
        ALL_TOOLS,
        prompt=_prompt,
        checkpointer=_checkpointer(),
    )


_WEEKDAYS = ["월", "화", "수", "목", "금", "토", "일"]


def _prompt(state: dict[str, Any]) -> list[Any]:
    """오늘 날짜를 매 턴 새로 넣는다.

    프롬프트를 상수로 두면 프로세스가 뜬 날짜에 고정된다. 서버는 며칠씩
    돌아가므로 "이번 주"가 조용히 틀려진다.
    """
    settings = get_settings()
    try:
        zone = ZoneInfo(settings.timezone)
    except ZoneInfoNotFoundError:
        # 타임존 설정이 틀렸다고 대화 전체가 죽으면 안 된다. 날짜만 덜 정확해진다.
        log.warning("알 수 없는 타임존 %s. 시스템 시간으로 대체한다.", settings.timezone)
        zone = None
    now = datetime.now(zone)
    header = (
        f"{SYSTEM_PROMPT}\n\n"
        f"오늘은 {now:%Y-%m-%d} {_WEEKDAYS[now.weekday()]}요일이고 현재 시각은 "
        f"{now:%H:%M} ({settings.timezone})이다.\n"
        "'이번 주', '다음 주 금요일' 같은 표현은 이 날짜를 기준으로 계산한다.\n"
        "마감일을 지정할 때는 시각을 따로 말하지 않으면 그날 18:00 으로 둔다."
    )
    return [{"role": "system", "content": header}, *state["messages"]]


def thread_config(thread_id: str) -> dict[str, Any]:
    settings = get_settings()
    return {
        "configurable": {"thread_id": thread_id},
        # 도구 호출이 무한히 돌지 않게 막는다. 한 번의 호출은 모델 1회 + 도구 1회다.
        "recursion_limit": settings.max_tool_calls * 2,
    }
