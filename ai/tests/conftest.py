import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

# 설정을 읽기 전에 채워 둔다. get_settings 가 lru_cache 라 import 시점 값이 굳는다.
os.environ.setdefault("AI_ENABLED", "true")
os.environ.setdefault("AI_INTERNAL_SECRET", "test-secret")
os.environ.setdefault("AI_OPENAI_API_KEY", "test-key")
os.environ.setdefault("AI_SPRING_BASE_URL", "http://spring.invalid")
