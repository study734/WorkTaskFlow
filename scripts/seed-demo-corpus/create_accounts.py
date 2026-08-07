"""시드용 계정을 만든다.

회원가입은 이메일 인증번호가 필요하고 그 번호는 로그에 남지 않는다.
Mailpit(메일 캐처)을 띄우고 백엔드를 MAIL_ENABLED=true 로 켜면
번호가 Mailpit API 로 들어오므로 자동으로 읽어 쓸 수 있다.

    docker run -d --name mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit
    python create_accounts.py --config config.json

이미 있는 계정은 건너뛴다. 만든 계정 정보는 config.json 에 그대로 쓰여 있어야
seed.py 가 이어서 쓸 수 있다.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
from pathlib import Path

import httpx

BASE_DIR = Path(__file__).resolve().parent
CODE_PATTERN = re.compile(r"\b(\d{6})\b")


def verification_code(mailpit: str, address: str, attempts: int = 20) -> str:
    """Mailpit 받은 편지함에서 해당 주소로 온 최신 인증번호를 읽는다."""
    with httpx.Client(timeout=10.0) as client:
        for _ in range(attempts):
            response = client.get(f"{mailpit}/api/v1/messages", params={"limit": 50})
            response.raise_for_status()
            for message in response.json().get("messages", []):
                recipients = [to.get("Address", "") for to in message.get("To") or []]
                if address.lower() not in [value.lower() for value in recipients]:
                    continue
                detail = client.get(f"{mailpit}/api/v1/message/{message['ID']}")
                detail.raise_for_status()
                found = CODE_PATTERN.search(detail.json().get("Text") or "")
                if found:
                    return found.group(1)
            time.sleep(0.5)
    raise RuntimeError(f"{address} 로 온 인증번호를 찾지 못했습니다. Mailpit 이 떠 있는지 확인해 주세요.")


def create(client: httpx.Client, base_url: str, mailpit: str, account: dict) -> str:
    username = account["username"]
    password = account["password"]
    email = account["email"]

    login = client.post(f"{base_url}/api/v1/auth/login",
                        json={"username": username, "password": password})
    if login.status_code == 200:
        return "이미 있음"

    sent = client.post(f"{base_url}/api/v1/auth/email-verifications", json={"email": email})
    if sent.status_code >= 400:
        return f"인증 메일 발송 실패: {sent.status_code} {sent.text[:120]}"

    code = verification_code(mailpit, email)
    signup = client.post(f"{base_url}/api/v1/auth/signup", json={
        "username": username,
        "email": email,
        "name": account["name"],
        "password": password,
        "verificationCode": code,
        "termsAgreed": True,
        "privacyAgreed": True,
        "ageConfirmed": True,
        "notificationAgreed": False,
        "marketingAgreed": False,
    })
    if signup.status_code >= 400:
        return f"가입 실패: {signup.status_code} {signup.text[:160]}"
    return "생성됨"


def main() -> int:
    parser = argparse.ArgumentParser(description="시드용 계정 생성")
    parser.add_argument("--config", default=str(BASE_DIR / "config.json"))
    parser.add_argument("--mailpit", default="http://localhost:8025")
    args = parser.parse_args()

    config = json.loads(Path(args.config).read_text(encoding="utf-8"))
    base_url = config["baseUrl"].rstrip("/")

    with httpx.Client(timeout=30.0) as client:
        for account in config["accounts"]:
            if "email" not in account:
                print(f"  {account['username']}: email 이 설정에 없습니다", file=sys.stderr)
                return 1
            print(f"  {account['username']}: {create(client, base_url, args.mailpit, account)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
