"""Spring DTO 와 주고받는 필드 이름이 맞는지 못 박는다.

threadId 가 thread_id 로 나가면서 Spring 에서 null 이 된 적이 있다.
에러가 안 나고 조용히 승인 재개만 깨졌다. 이름은 테스트로 고정한다.
"""

from fastapi.testclient import TestClient

from app.main import ChatRequest, IndexResponse, ResumeRequest, TurnResponse, app


def test_responses_serialize_as_camel_case():
    payload = TurnResponse(thread_id="abc", status="completed", reply="답").model_dump(by_alias=True)
    assert set(payload) == {"threadId", "status", "reply", "pending"}

    index = IndexResponse(indexed=1, skipped=0, removed=0, unsupported=0, failures=[])
    assert set(index.model_dump(by_alias=True)) == {
        "indexed", "skipped", "removed", "unsupported", "failures"
    }


def test_requests_accept_camel_case():
    chat = ChatRequest.model_validate({"groupId": 1, "message": "안녕", "threadId": "t1"})
    assert (chat.group_id, chat.thread_id) == (1, "t1")

    resume = ResumeRequest.model_validate(
        {"groupId": 1, "threadId": "t1", "approved": True, "note": ""}
    )
    assert resume.approved and resume.thread_id == "t1"


def test_internal_secret_required():
    client = TestClient(app)
    blocked = client.post("/internal/chat", json={"groupId": 1, "message": "안녕"},
                          headers={"Authorization": "Bearer x"})
    assert blocked.status_code == 403

    unauthenticated = client.post("/internal/chat", json={"groupId": 1, "message": "안녕"},
                                  headers={"X-Internal-Secret": "test-secret"})
    assert unauthenticated.status_code == 401


def test_health_reports_camel_free_shape():
    body = TestClient(app).get("/internal/health").json()
    assert body["status"] == "ok" and body["enabled"] is True
