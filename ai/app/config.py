"""환경 설정. 모든 값은 AI_ 접두사 환경 변수에서 읽는다."""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AI_", env_file=".env", extra="ignore")

    enabled: bool = False
    port: int = 8090

    spring_base_url: str = "http://localhost:8085"
    internal_secret: str = ""

    openai_api_key: str = ""
    openai_base_url: str = "https://api.openai.com/v1"
    chat_model: str = "gpt-5.6-luna"
    embedding_model: str = "text-embedding-3-small"
    # 추론 모델은 chat/completions 에서 도구 호출과 reasoning 을 같이 못 쓴다.
    # 비추론 모델로 바꿀 때만 false 로 둔다.
    use_responses_api: bool = True

    chroma_dir: Path = Path("./var/chroma")
    checkpoint_db: Path = Path("./var/checkpoints.sqlite")

    # "다음 주 금요일", "이번 주" 같은 표현을 날짜로 옮기려면 오늘이 며칠인지 알아야 한다.
    timezone: str = "Asia/Seoul"
    max_tool_calls: int = 12
    request_timeout_seconds: float = 30.0

    def missing_requirements(self) -> list[str]:
        """켜져 있는데 빠진 필수 값을 모은다. 기동 시 한 번에 알려주기 위함."""
        missing = []
        if not self.internal_secret:
            missing.append("AI_INTERNAL_SECRET")
        if not self.openai_api_key:
            missing.append("AI_OPENAI_API_KEY")
        return missing


@lru_cache
def get_settings() -> Settings:
    return Settings()
