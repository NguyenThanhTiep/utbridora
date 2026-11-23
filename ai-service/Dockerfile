FROM python:3.12.14-slim
WORKDIR /srv/ai
COPY requirements*.txt constraints-light.txt ./
RUN pip install --no-cache-dir -r requirements-torch-cpu.txt \
 && pip install --no-cache-dir -r requirements-model.txt \
 && pip check \
 && useradd --uid 10001 --create-home ai
COPY app ./app
COPY model-lock.json ./model-lock.json
USER 10001:10001
ENV AI_MODEL_PATH=/models/e5 HF_HOME=/tmp/hf-cache HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 HF_HUB_DISABLE_TELEMETRY=1 DO_NOT_TRACK=1 PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1
EXPOSE 8000
CMD ["python", "-m", "uvicorn", "app.main:create_app", "--factory", "--host", "0.0.0.0", "--port", "8000", "--workers", "1", "--limit-concurrency", "4", "--timeout-keep-alive", "5", "--no-access-log"]
