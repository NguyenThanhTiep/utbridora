import time
from dataclasses import replace
from uuid import uuid4
import pytest
from fastapi.testclient import TestClient
from app.config import Settings
from app.embedding import EmbeddingEngine
from app.main import create_app
from conftest import TOKEN, REVISION, FakeModel

HEADERS = {"X-AI-Token": TOKEN}


def request():
    return {"schemaVersion": "semantic-v1", "requestId": str(uuid4()), "templateVersion": "canonical-v1",
            "query": "Java SQL", "passages": [{"key": "0", "text": "Java"}]}


def test_real_http_contract_with_fake_encoder(settings, engine):
    with TestClient(create_app(settings, loader=lambda _: engine)) as client:
        assert client.get("/health").json() == {"status": "ALIVE"}
        assert client.get("/ready").status_code == 200
        data = request()
        response = client.post("/internal/v1/similarity", json=data, headers=HEADERS)
        assert response.status_code == 200
        result = response.json()
        assert result["requestId"] == data["requestId"]
        assert result["modelRevision"] == REVISION and result["dimension"] == 384
        assert result["results"][0]["status"] == "READY"
        assert -1 <= result["results"][0]["score"] <= 1
        assert "vector" not in result["results"][0]


def test_embeddings_and_no_data(settings, engine):
    with TestClient(create_app(settings, loader=lambda _: engine)) as client:
        data = request()
        data.pop("query"); data.pop("passages")
        data["inputs"] = [{"key": "1", "role": "query", "text": "Java"},
                          {"key": "2", "role": "passage", "text": "  "}]
        result = client.post("/internal/v1/embeddings", json=data, headers=HEADERS).json()["results"]
        assert len(result[0]["vector"]) == 384
        assert result[1]["vector"] is None and result[1]["status"] == "NO_DATA"


def test_auth_precedes_json_parsing(settings, engine):
    with TestClient(create_app(settings, loader=lambda _: engine)) as client:
        result = client.post("/internal/v1/similarity", content=b"not json or private input")
        assert result.status_code == 403 and result.json() == {"errorCode": "ACCESS_DENIED"}


def test_duplicate_auth_headers_are_denied(settings, engine):
    with TestClient(create_app(settings, loader=lambda _: engine)) as client:
        result = client.post("/internal/v1/similarity", json=request(),
                             headers=[("X-AI-Token", TOKEN), ("X-AI-Token", TOKEN)])
        assert result.status_code == 403


def test_streaming_body_uses_total_deadline_not_a_resetting_per_chunk_timeout(settings):
    import asyncio
    from app.main import InternalBoundary
    config = replace(settings, timeout_seconds=0.03)
    events = []

    async def downstream(scope, receive, send):
        raise AssertionError("An over-deadline streamed request must never reach inference")

    async def receive():
        await asyncio.sleep(0.02)
        return {"type": "http.request", "body": b"x", "more_body": True}

    async def send(event):
        events.append(event)

    scope = {"type": "http", "path": "/internal/v1/similarity",
             "headers": [(b"x-ai-token", TOKEN.encode()), (b"content-type", b"application/json")]}
    asyncio.run(InternalBoundary(downstream, config)(scope, receive, send))
    assert events[0]["status"] == 408
    assert b"REQUEST_TIMEOUT" in events[1]["body"]


@pytest.mark.parametrize("mutation", ["extra", "role", "key", "duplicate", "version", "too_many", "text", "uuid"])
def test_closed_schema_errors_never_echo_private_input(settings, engine, mutation):
    data = request(); marker = "PRIVATE_INPUT_MUST_NOT_ECHO"
    if mutation == "extra": data["studentUserId"] = marker
    if mutation == "role": data["passages"][0]["role"] = marker
    if mutation == "key": data["passages"][0]["key"] = marker
    if mutation == "duplicate": data["passages"] *= 2
    if mutation == "version": data["templateVersion"] = marker
    if mutation == "too_many": data["passages"] = [{"key": str(i), "text": marker} for i in range(17)]
    if mutation == "text": data["query"] = marker * 1000
    if mutation == "uuid": data["requestId"] = marker
    with TestClient(create_app(settings, loader=lambda _: engine)) as client:
        result = client.post("/internal/v1/similarity", json=data, headers=HEADERS)
        assert result.status_code == 422 and result.json() == {"errorCode": "INVALID_REQUEST"}
        assert marker not in result.text


def test_limits_content_type_and_encoding(settings, engine):
    small = replace(settings, max_request_bytes=1024)
    with TestClient(create_app(small, loader=lambda _: engine)) as client:
        assert client.post("/internal/v1/similarity", content=b"x" * 1025,
            headers=HEADERS | {"Content-Type": "application/json"}).status_code == 413
        assert client.post("/internal/v1/similarity", json=request(),
            headers=HEADERS | {"Content-Encoding": "gzip"}).status_code == 415
        assert client.post("/internal/v1/similarity", content=b"x", headers=HEADERS).status_code == 415
        # Streaming content has no Content-Length; the ASGI boundary must still enforce bytes.
        assert client.post("/internal/v1/similarity", content=iter([b"x" * 600, b"x" * 600]),
            headers=HEADERS | {"Content-Type": "application/json"}).status_code == 413


def test_configured_batch_limit(settings, engine):
    with TestClient(create_app(replace(settings, max_inputs=1), loader=lambda _: engine)) as client:
        data = request(); data["passages"].append({"key": "1", "text": "SQL"})
        assert client.post("/internal/v1/similarity", json=data, headers=HEADERS).status_code == 413


def test_missing_model_and_internal_exception_do_not_leak(settings):
    def missing(_):
        raise RuntimeError("PRIVATE_PATH_SECRET")
    with TestClient(create_app(settings, loader=missing)) as client:
        assert client.get("/health").status_code == 200
        assert client.get("/ready").json() == {"errorCode": "MODEL_UNAVAILABLE"}
        result = client.post("/internal/v1/similarity", json=request(), headers=HEADERS)
        assert result.status_code == 503 and "PRIVATE_PATH_SECRET" not in result.text


def test_timeout_keeps_busy_slot_until_cpu_operation_finishes(settings):
    config = replace(settings, timeout_seconds=0.03)
    model = FakeModel(); original = model.encode
    def slow(*args, **kwargs):
        time.sleep(0.2)
        return original(*args, **kwargs)
    model.encode = slow
    with TestClient(create_app(config, loader=lambda _: EmbeddingEngine(model, config))) as client:
        first = client.post("/internal/v1/similarity", json=request(), headers=HEADERS)
        assert first.status_code == 503 and first.json()["errorCode"] == "AI_TIMEOUT"
        assert client.get("/health").status_code == 200
        second = client.post("/internal/v1/similarity", json=request(), headers=HEADERS)
        assert second.status_code == 503 and second.json()["errorCode"] == "AI_BUSY"
        time.sleep(0.25)
        model.encode = original
        assert client.post("/internal/v1/similarity", json=request(), headers=HEADERS).status_code == 200


def test_empty_query_is_no_data_not_a_fabricated_score(settings, engine):
    data = request(); data["query"] = " "
    with TestClient(create_app(settings, loader=lambda _: engine)) as client:
        result = client.post("/internal/v1/similarity", json=data, headers=HEADERS).json()["results"][0]
        assert result["score"] is None and result["status"] == "NO_DATA"
