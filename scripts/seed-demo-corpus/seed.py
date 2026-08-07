"""WorkTaskFlow 데모 코퍼스 시드 스크립트.

REST API만 호출한다. DB에 직접 INSERT 하지 않는다.
상태 전이 규칙·낙관적 락·활동 로그·알림이 전부 정상 경로로 기록되어야
이후 RAG/Agent가 읽을 맥락이 실제 운영 데이터와 같은 모양이 되기 때문이다.

사용법:
    python seed.py --config config.json
    python seed.py --config config.json --dry-run
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any

import httpx

BASE_DIR = Path(__file__).resolve().parent
CORPUS_FILE = BASE_DIR / "data" / "corpus.json"
TASKS_FILE = BASE_DIR / "data" / "tasks.json"

# 서버가 허용하는 확장자만 올린다(ResourceService.EXTENSIONS).

class SeedError(RuntimeError):
    pass


class ConnectionFailed(SeedError):
    """서버에 닿지 못한 경우. 계정 문제와 구분해서 안내한다."""


@dataclass
class Account:
    key: str
    username: str
    password: str
    token: str = ""
    user_id: int = 0
    member_id: int = 0


@dataclass
class Stats:
    groups_created: int = 0
    members_joined: int = 0
    resources_uploaded: int = 0
    resources_skipped: int = 0
    tasks_created: int = 0
    tasks_skipped: int = 0
    comments_created: int = 0
    warnings: list[str] = field(default_factory=list)


class Api:
    """Origin 헤더를 보내지 않는다. 보내면 SameOriginMutationFilter가 403으로 막는다."""

    def __init__(self, base_url: str, dry_run: bool) -> None:
        self.base_url = base_url.rstrip("/")
        self.dry_run = dry_run
        self.client = httpx.Client(timeout=30.0)

    def close(self) -> None:
        self.client.close()

    def call(
        self,
        method: str,
        path: str,
        token: str | None = None,
        *,
        json_body: Any = None,
        files: Any = None,
        data: Any = None,
        allow: tuple[int, ...] = (),
        force: bool = False,
    ) -> Any:
        # 로그인은 데이터를 바꾸지 않으므로 dry-run 에서도 실제로 호출한다.
        # 토큰이 없으면 이후 조회조차 못 해 계획을 세울 수 없다.
        mutating = not force and method.upper() not in {"GET", "HEAD", "OPTIONS"}
        if self.dry_run and mutating:
            print(f"    [dry-run] {method} {path}")
            return None
        headers = {"Authorization": f"Bearer {token}"} if token else {}
        try:
            response = self.client.request(
                method, f"{self.base_url}{path}", headers=headers, json=json_body,
                files=files, data=data,
            )
        except httpx.HTTPError as error:
            raise ConnectionFailed(
                f"{self.base_url} 에 연결하지 못했습니다. 백엔드가 떠 있는지 확인해 주세요.\n"
                f"  원인: {type(error).__name__}: {error}"
            ) from error
        if response.status_code in allow:
            return None
        if response.status_code >= 400:
            raise SeedError(f"{method} {path} -> {response.status_code} {response.text[:400]}")
        if not response.content:
            return None
        return response.json()


def load_config(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise SeedError(
            f"설정 파일이 없습니다: {path}\n"
            "config.example.json 을 config.json 으로 복사한 뒤 계정 정보를 채워 주세요."
        )
    config = json.loads(path.read_text(encoding="utf-8"))
    for required in ("baseUrl", "groupName", "accounts"):
        if required not in config:
            raise SeedError(f"설정에 '{required}' 가 없습니다.")
    if not any(account.get("role") == "leader" for account in config["accounts"]):
        raise SeedError("accounts 중 정확히 한 명은 role 이 'leader' 여야 합니다.")
    return config


def login_all(api: Api, config: dict[str, Any]) -> dict[str, Account]:
    accounts: dict[str, Account] = {}
    for entry in config["accounts"]:
        account = Account(key=entry["key"], username=entry["username"], password=entry["password"])
        try:
            token = api.call(
                "POST", "/api/v1/auth/login",
                json_body={"username": account.username, "password": account.password},
                force=True,
            )
        except ConnectionFailed:
            raise
        except SeedError as error:
            raise SeedError(
                f"'{account.username}' 로그인 실패. 계정이 실제로 가입되어 있는지 확인해 주세요.\n"
                "  이메일 인증 코드가 로그에 남지 않아 회원가입은 자동화할 수 없습니다.\n"
                f"  원인: {error}"
            ) from error
        account.token = token["accessToken"]
        me = api.call("GET", "/api/v1/auth/me", account.token)
        account.user_id = me["userId"]
        if account.username.startswith("demo_"):
            raise SeedError(
                f"'{account.username}' 은 데모 계정입니다. DemoReadOnlyFilter 가 모든 쓰기를 403 으로 막습니다."
            )
        accounts[account.key] = account
        print(f"  로그인: {account.username} (userId={account.user_id})")
    return accounts


def ensure_group(api: Api, leader: Account, config: dict[str, Any], stats: Stats) -> tuple[int, str]:
    name = config["groupName"]
    groups = api.call("GET", "/api/v1/groups", leader.token) or []
    existing = next((g for g in groups if g["type"] == "TEAM" and g["name"] == name), None)
    if existing:
        print(f"  기존 그룹 재사용: {name} (id={existing['id']})")
        rotated = api.call("PUT", f"/api/v1/groups/{existing['id']}/join-code", leader.token)
        code = (rotated or {}).get("joinCode", "")
        return existing["id"], code
    created = api.call(
        "POST", "/api/v1/groups", leader.token,
        json_body={
            "name": name,
            "description": config.get("groupDescription", ""),
            "timezone": config.get("timezone", "Asia/Seoul"),
        },
    )
    if created is None:
        return 0, ""
    stats.groups_created += 1
    print(f"  그룹 생성: {name} (id={created['id']})")
    return created["id"], created.get("joinCode", "")


def ensure_members(
    api: Api, group_id: int, join_code: str, accounts: dict[str, Account], leader: Account,
    stats: Stats
) -> None:
    for account in accounts.values():
        if account.key == leader.key:
            continue
        if not join_code:
            stats.warnings.append(f"참여 코드가 없어 '{account.username}' 합류를 건너뜁니다.")
            continue
        # 이미 참여 중이면 409(GROUP_ALREADY_JOINED) 가 정상 응답이다.
        api.call(
            "POST", "/api/v1/groups/join", account.token,
            json_body={"code": join_code}, allow=(409,),
        )
        stats.members_joined += 1
    members = api.call("GET", f"/api/v1/groups/{group_id}/members", leader.token) or []
    by_user_id = {m["userId"]: m["id"] for m in members if m["status"] == "ACTIVE"}
    for account in accounts.values():
        account.member_id = by_user_id.get(account.user_id, 0)
        if not account.member_id and not api.dry_run:
            stats.warnings.append(f"'{account.username}' 의 멤버 ID를 찾지 못했습니다.")


def load_corpus(include_adversarial: bool = False) -> list[dict]:
    """코퍼스는 JSON 한 파일에 담는다.

    문서를 .txt 로 두면 저장소의 *.md/*.txt 추적 금지 규칙에 걸려 공유 .gitignore 와
    CI 게이트를 함께 고쳐야 한다. 평가용 픽스처이므로 eval-questions.json 과 같은
    형식으로 두는 편이 규칙도 안 건드리고 일관된다.
    """
    if not CORPUS_FILE.exists():
        return []
    documents = json.loads(CORPUS_FILE.read_text(encoding="utf-8"))
    return [d for d in documents if include_adversarial or not d.get("adversarial")]


def upload_resources(api: Api, group_id: int, leader: Account, accounts: dict[str, Account],
                     stats: Stats, include_adversarial: bool = False) -> None:
    documents = load_corpus(include_adversarial)
    if not documents:
        stats.warnings.append(f"코퍼스가 비어 있습니다: {CORPUS_FILE}")
        return
    existing = {r["title"] for r in (api.call("GET", f"/api/v1/groups/{group_id}/resources", leader.token) or [])}
    uploaders = [a for a in accounts.values() if a.member_id or api.dry_run] or [leader]
    for index, document in enumerate(documents):
        title = document["title"]
        if title in existing:
            stats.resources_skipped += 1
            continue
        uploader = uploaders[index % len(uploaders)]
        api.call(
            "POST", f"/api/v1/groups/{group_id}/resources/files", uploader.token,
            files={"file": (document["filename"], document["text"].encode("utf-8"),
                            document["contentType"])},
            data={"title": title},
        )
        stats.resources_uploaded += 1
        print(f"    자료 업로드: {title} ({uploader.username})")


def iso(value: datetime) -> str:
    return value.strftime("%Y-%m-%dT%H:%M:%S")


def run_scenario(api: Api, task: dict[str, Any], spec: dict[str, Any],
                 accounts: dict[str, Account], leader: Account, stats: Stats) -> None:
    """생성 직후의 REQUESTED 업무를 목표 상태까지 정상 전이시킨다."""
    scenario = spec.get("scenario", "REQUESTED")
    if scenario == "REQUESTED":
        return
    task_id = task["id"]
    version = task["version"]

    if scenario == "REJECTED":
        api.call(
            "POST", f"/api/v1/tasks/{task_id}/transitions", leader.token,
            json_body={"action": "REJECT", "reason": spec.get("reason", "이번 분기 범위가 아닙니다."),
                       "expectedVersion": version},
        )
        return

    accepted = api.call(
        "POST", f"/api/v1/tasks/{task_id}/transitions", leader.token,
        json_body={"action": "ACCEPT", "expectedVersion": version},
    )
    version = accepted["version"] if accepted else version

    assignee = accounts[spec["assignee"]]
    assigned = api.call(
        "PUT", f"/api/v1/tasks/{task_id}/assignee", leader.token,
        json_body={"assigneeMemberId": assignee.member_id, "expectedVersion": version},
    )
    version = assigned["version"] if assigned else version
    if scenario == "TODO":
        return

    if scenario == "CANCELLED":
        api.call(
            "POST", f"/api/v1/tasks/{task_id}/transitions", leader.token,
            json_body={"action": "CANCEL", "reason": spec.get("reason", "상위 일정 변경으로 취소합니다."),
                       "expectedVersion": version},
        )
        return

    started = api.call(
        "POST", f"/api/v1/tasks/{task_id}/transitions", assignee.token,
        json_body={"action": "START", "expectedVersion": version},
    )
    version = started["version"] if started else version
    if scenario == "IN_PROGRESS":
        return

    if scenario == "ON_HOLD":
        review = date.today() + timedelta(days=spec.get("reviewInDays", 3))
        api.call(
            "POST", f"/api/v1/tasks/{task_id}/transitions", assignee.token,
            json_body={
                "action": "HOLD",
                "reason": spec.get("reason", "선행 작업 대기 중입니다."),
                "blockerType": spec.get("blockerType", "DEPENDENCY"),
                "blockerNextActionType": spec.get("blockerNextActionType", "FOLLOW_UP"),
                "blockerReviewDate": review.isoformat(),
                "expectedVersion": version,
            },
        )
        return

    if scenario == "COMPLETED":
        api.call(
            "POST", f"/api/v1/tasks/{task_id}/transitions", assignee.token,
            json_body={"action": "COMPLETE", "expectedVersion": version},
        )
        return

    raise SeedError(f"알 수 없는 시나리오: {scenario}")


def create_tasks(api: Api, group_id: int, accounts: dict[str, Account], leader: Account,
                 stats: Stats) -> None:
    if not TASKS_FILE.exists():
        raise SeedError(f"업무 정의 파일이 없습니다: {TASKS_FILE}")
    specs = json.loads(TASKS_FILE.read_text(encoding="utf-8"))
    existing = {t["title"] for t in (api.call("GET", f"/api/v1/groups/{group_id}/tasks", leader.token) or [])}
    today = datetime.now().replace(hour=18, minute=0, second=0, microsecond=0)

    for spec in specs:
        if spec["title"] in existing:
            stats.tasks_skipped += 1
            continue
        requester = accounts[spec["requester"]]
        due_at = iso(today + timedelta(days=spec["dueInDays"])) if spec.get("dueInDays") is not None else None
        created = api.call(
            "POST", f"/api/v1/groups/{group_id}/tasks", requester.token,
            json_body={
                "title": spec["title"],
                "description": spec.get("description"),
                "priority": spec.get("priority", "NORMAL"),
                "dueAt": due_at,
                "checklistItems": spec.get("checklist") or None,
            },
        )
        stats.tasks_created += 1
        print(f"    업무 생성: [{spec.get('scenario', 'REQUESTED')}] {spec['title']}")
        if created is None:
            continue
        run_scenario(api, created, spec, accounts, leader, stats)
        for comment in spec.get("comments", []):
            author = accounts[comment["author"]]
            api.call(
                "POST", f"/api/v1/tasks/{created['id']}/comments", author.token,
                json_body={"content": comment["content"]},
            )
            stats.comments_created += 1


def main() -> int:
    parser = argparse.ArgumentParser(description="WorkTaskFlow 데모 코퍼스 시드")
    parser.add_argument("--config", default=str(BASE_DIR / "config.json"))
    parser.add_argument("--dry-run", action="store_true", help="쓰기 호출 없이 계획만 출력")
    # 인젝션 방어 시험용 문서는 기본 시드에 섞이지 않는다. 방어를 잴 때만 켠다.
    parser.add_argument("--adversarial", action="store_true",
                        help="프롬프트 인젝션 방어 검증용 문서까지 업로드")
    args = parser.parse_args()

    api = Api("", args.dry_run)
    stats = Stats()
    try:
        config = load_config(Path(args.config))
        api.base_url = config["baseUrl"].rstrip("/")
        print(f"대상: {api.base_url}" + (" (dry-run)" if args.dry_run else ""))

        print("\n[1/4] 로그인")
        accounts = login_all(api, config)
        leader = next(
            accounts[entry["key"]] for entry in config["accounts"] if entry.get("role") == "leader"
        )

        print("\n[2/4] 그룹 준비")
        group_id, join_code = ensure_group(api, leader, config, stats)
        if group_id:
            ensure_members(api, group_id, join_code, accounts, leader, stats)
        elif api.dry_run:
            print("  (dry-run) 그룹이 아직 없습니다. 실제 실행 시 새로 만들어집니다.")
            print(f"  (dry-run) 업로드 예정 자료 {len(load_corpus(args.adversarial))}건, "
                  f"업무 {len(json.loads(TASKS_FILE.read_text(encoding='utf-8')))}건")

        print("\n[3/4] 그룹 자료 업로드")
        if group_id:
            upload_resources(api, group_id, leader, accounts, stats, args.adversarial)

        print("\n[4/4] 업무 생성")
        if group_id:
            create_tasks(api, group_id, accounts, leader, stats)
    except SeedError as error:
        sys.stdout.flush()
        print(f"\n실패: {error}", file=sys.stderr)
        return 1
    finally:
        api.close()

    print("\n요약")
    print(f"  그룹 생성      {stats.groups_created}")
    print(f"  멤버 합류      {stats.members_joined}")
    print(f"  자료 업로드    {stats.resources_uploaded} (건너뜀 {stats.resources_skipped})")
    print(f"  업무 생성      {stats.tasks_created} (건너뜀 {stats.tasks_skipped})")
    print(f"  댓글 생성      {stats.comments_created}")
    for warning in stats.warnings:
        print(f"  경고: {warning}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
