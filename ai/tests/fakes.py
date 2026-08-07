"""테스트용 가짜 LLM 과 가짜 Spring."""

from __future__ import annotations

from typing import Any

from langchain_core.callbacks import CallbackManagerForLLMRun
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import AIMessage, BaseMessage
from langchain_core.outputs import ChatGeneration, ChatResult


class ScriptedChatModel(BaseChatModel):
    """정해진 AIMessage 를 순서대로 돌려준다. 도구 호출 경로를 실제로 태우기 위함."""

    responses: list[AIMessage] = []
    calls: int = 0

    @property
    def _llm_type(self) -> str:
        return "scripted"

    def bind_tools(self, tools: Any, **kwargs: Any) -> "ScriptedChatModel":
        return self

    def _generate(
        self,
        messages: list[BaseMessage],
        stop: list[str] | None = None,
        run_manager: CallbackManagerForLLMRun | None = None,
        **kwargs: Any,
    ) -> ChatResult:
        index = min(self.calls, len(self.responses) - 1)
        self.calls += 1
        return ChatResult(generations=[ChatGeneration(message=self.responses[index])])


class FakeSpring:
    """SpringClient 와 같은 모양. 호출 기록을 남겨 쓰기가 실제로 일어났는지 본다."""

    def __init__(self, tasks: dict[int, dict] | None = None) -> None:
        self.tasks = tasks or {}
        self.writes: list[tuple[str, Any]] = []

    def __enter__(self) -> "FakeSpring":
        return self

    def __exit__(self, *_: object) -> None:
        pass

    def close(self) -> None:
        pass

    def my_groups(self):
        return [{"id": 1, "name": "테스트팀", "type": "TEAM"}]

    def group_tasks(self, group_id):
        return [task for task in self.tasks.values() if task["groupId"] == group_id]

    def task(self, task_id):
        return self.tasks[task_id]

    def task_histories(self, task_id):
        return []

    def task_comments(self, task_id):
        return []

    def members(self, group_id):
        return [{"id": 10, "userId": 100, "nickname": "팀장", "role": "LEADER", "status": "ACTIVE"}]

    def me(self):
        return {"userId": 100, "username": "leader"}

    def create_task(self, group_id, body):
        self.writes.append(("create_task", body))
        created = {
            "id": 999, "groupId": group_id, "title": body["title"], "status": "REQUESTED",
            "priority": body.get("priority", "NORMAL"), "assigneeMemberId": None,
            "dueAt": body.get("dueAt"), "delayed": False, "version": 0,
            "description": body.get("description") or "",
        }
        self.tasks[999] = created
        return created

    def update_task(self, task_id, body):
        self.writes.append(("update_task", body))
        self.tasks[task_id] = {**self.tasks[task_id], **body}
        return self.tasks[task_id]

    def transition_task(self, task_id, body):
        self.writes.append(("transition_task", body))
        return self.tasks[task_id]

    def create_comment(self, task_id, content):
        self.writes.append(("create_comment", content))
        return {"id": 1, "content": content}
