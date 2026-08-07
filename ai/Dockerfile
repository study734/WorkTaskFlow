FROM python:3.11-slim

WORKDIR /app

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

COPY requirements.lock ./
RUN pip install --no-cache-dir -r requirements.lock

COPY app ./app

# 볼륨 마운트 지점. 색인과 승인 대기가 재시작을 넘어 살아남아야 한다.
ENV AI_CHROMA_DIR=/var/lib/totaskflow/ai/chroma \
    AI_CHECKPOINT_DB=/var/lib/totaskflow/ai/checkpoints.sqlite \
    AI_PORT=8090

RUN useradd --uid 10002 --create-home agent \
 && mkdir -p /var/lib/totaskflow/ai \
 && chown -R 10002:10002 /var/lib/totaskflow/ai
USER 10002

EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=5 \
  CMD curl --fail --silent http://127.0.0.1:8090/internal/health || exit 1

# 워커 1개. Chroma PersistentClient 와 SQLite 체크포인터가 프로세스 간 동시 쓰기에 취약하다.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090", "--workers", "1"]
