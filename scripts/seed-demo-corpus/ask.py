"""Spring 프록시를 거쳐 Agent 에 자연어로 묻는다.

    python ask.py "이번 주 지연된 업무 알려줘"
    python ask.py --eval          # eval-questions.json 을 순서대로 던진다
    python ask.py --approve "배포 점검 업무 만들어줘"   # 승인까지 자동으로 진행

질의마다 유료 LLM 호출이 발생한다.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys

import httpx

BASE_DIR = pathlib.Path(__file__).resolve().parent


def session(config: dict) -> tuple[httpx.Client, dict, str, int]:
    base = config["baseUrl"].rstrip("/")
    leader = next(a for a in config["accounts"] if a.get("role") == "leader")
    client = httpx.Client(timeout=180.0)
    token = client.post(f"{base}/api/v1/auth/login",
                        json={"username": leader["username"], "password": leader["password"]}
                        ).json()["accessToken"]
    headers = {"Authorization": f"Bearer {token}"}
    groups = client.get(f"{base}/api/v1/groups", headers=headers).json()
    group = next(g for g in groups if g["name"] == config["groupName"])
    return client, headers, base, group["id"]


def ask(client, headers, base, group_id, message, approve=False):
    response = client.post(f"{base}/api/v1/ai/chat", headers=headers,
                           json={"groupId": group_id, "message": message})
    if response.status_code >= 400:
        return {"error": f"{response.status_code} {response.text[:300]}"}
    turn = response.json()
    if turn["status"] == "awaiting_approval":
        print(f"  [승인 대기] {turn['pending']['action']} :: {turn['reply']}")
        print(f"  [상세] {json.dumps(turn['pending']['details'], ensure_ascii=False)}")
        resumed = client.post(f"{base}/api/v1/ai/resume", headers=headers, json={
            "groupId": group_id, "threadId": turn["threadId"],
            "approved": approve, "note": "" if approve else "지금은 하지 마세요",
        })
        if resumed.status_code >= 400:
            return {"error": f"{resumed.status_code} {resumed.text[:300]}"}
        turn = resumed.json()
        turn["approved"] = approve
    return turn


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("message", nargs="?")
    parser.add_argument("--config", default=str(BASE_DIR / "config.json"))
    parser.add_argument("--eval", action="store_true")
    parser.add_argument("--approve", action="store_true")
    parser.add_argument("--limit", type=int, default=99)
    args = parser.parse_args()

    config = json.loads(pathlib.Path(args.config).read_text(encoding="utf-8"))
    client, headers, base, group_id = session(config)

    if args.eval:
        questions = json.loads((BASE_DIR / "data" / "eval-questions.json").read_text(encoding="utf-8"))
        for item in questions[: args.limit]:
            if item["kind"] == "injection":
                continue  # 별도 절차. corpus-adversarial 을 색인해야 한다.
            print(f"\n=== {item['id']} {item['question']}")
            turn = ask(client, headers, base, group_id, item["question"])
            print(f"[답] {turn.get('reply') or turn.get('error')}")
            print(f"[기대] {item['expected']}")
        return 0

    if not args.message:
        print("질문을 입력해 주세요.", file=sys.stderr)
        return 1
    turn = ask(client, headers, base, group_id, args.message, approve=args.approve)
    print(f"[답] {turn.get('reply') or turn.get('error')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
