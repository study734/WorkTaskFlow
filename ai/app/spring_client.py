"""Spring 백엔드 호출 클라이언트.

DB 를 직접 보지 않는다. 권한 검사·상태 전이 규칙·낙관적 락·알림·활동 로그가
전부 Spring 서비스 계층에 있어서, 여기서 DB 를 만지면 AI 경로만 규칙을
우회하는 두 번째 진실이 생긴다.

Origin 헤더는 보내지 않는다. 보내면 SameOriginMutationFilter 가 403 으로 막는다.
"""

from __future__ import annotations

from typing import Any

import httpx

from .config import get_settings


class SpringError(RuntimeError):
    """Spring 이 4xx/5xx 로 답한 경우. 사용자에게 보여줄 문구를 담는다."""

    def __init__(self, status: int, code: str, message: str) -> None:
        super().__init__(f"{code}: {message}")
        self.status = status
        self.code = code
        self.message = message


class SpringUnreachable(RuntimeError):
    pass


class SpringClient:
    def __init__(self, token: str) -> None:
        settings = get_settings()
        self._base_url = settings.spring_base_url.rstrip("/")
        self._client = httpx.Client(
            timeout=settings.request_timeout_seconds,
            headers={"Authorization": f"Bearer {token}"},
        )

    def close(self) -> None:
        self._client.close()

    def __enter__(self) -> "SpringClient":
        return self

    def __exit__(self, *_: object) -> None:
        self.close()

    def _call(self, method: str, path: str, *, json_body: Any = None) -> Any:
        try:
            response = self._client.request(method, f"{self._base_url}{path}", json=json_body)
        except httpx.HTTPError as error:
            raise SpringUnreachable(f"백엔드에 연결하지 못했습니다: {error}") from error
        if response.status_code >= 400:
            code, message = "UNKNOWN", response.text[:200]
            try:
                payload = response.json()
                code = payload.get("code") or code
                message = payload.get("message") or message
            except ValueError:
                pass
            raise SpringError(response.status_code, code, message)
        if not response.content:
            return None
        return response.json()

    # 조회 -------------------------------------------------------------
    def my_groups(self) -> list[dict[str, Any]]:
        return self._call("GET", "/api/v1/groups") or []

    def group_tasks(self, group_id: int) -> list[dict[str, Any]]:
        return self._call("GET", f"/api/v1/groups/{group_id}/tasks") or []

    def task(self, task_id: int) -> dict[str, Any]:
        return self._call("GET", f"/api/v1/tasks/{task_id}")

    def task_histories(self, task_id: int) -> list[dict[str, Any]]:
        return self._call("GET", f"/api/v1/tasks/{task_id}/histories") or []

    def task_comments(self, task_id: int) -> list[dict[str, Any]]:
        return self._call("GET", f"/api/v1/tasks/{task_id}/comments") or []

    def members(self, group_id: int) -> list[dict[str, Any]]:
        return self._call("GET", f"/api/v1/groups/{group_id}/members") or []

    def me(self) -> dict[str, Any]:
        return self._call("GET", "/api/v1/auth/me")

    def group_resources(self, group_id: int) -> list[dict[str, Any]]:
        return self._call("GET", f"/api/v1/groups/{group_id}/resources") or []

    def download_resource(self, resource_id: int) -> bytes:
        """파일 본문. 권한 검사를 통과해야만 내려오므로 볼륨 직접 접근보다 안전하다."""
        try:
            response = self._client.get(f"{self._base_url}/api/v1/resources/{resource_id}/download")
        except httpx.HTTPError as error:
            raise SpringUnreachable(f"백엔드에 연결하지 못했습니다: {error}") from error
        if response.status_code >= 400:
            raise SpringError(response.status_code, "RESOURCE_DOWNLOAD_FAILED", response.text[:200])
        return response.content

    # 변경 -------------------------------------------------------------
    def create_task(self, group_id: int, body: dict[str, Any]) -> dict[str, Any]:
        return self._call("POST", f"/api/v1/groups/{group_id}/tasks", json_body=body)

    def update_task(self, task_id: int, body: dict[str, Any]) -> dict[str, Any]:
        return self._call("PATCH", f"/api/v1/tasks/{task_id}", json_body=body)

    def transition_task(self, task_id: int, body: dict[str, Any]) -> dict[str, Any]:
        return self._call("POST", f"/api/v1/tasks/{task_id}/transitions", json_body=body)

    def create_comment(self, task_id: int, content: str) -> dict[str, Any]:
        return self._call("POST", f"/api/v1/tasks/{task_id}/comments", json_body={"content": content})
