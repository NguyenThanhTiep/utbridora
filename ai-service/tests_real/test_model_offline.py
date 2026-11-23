"""Explicit separate-host command. Missing weights/permission FAIL; no silent skip."""
import json
import os
from pathlib import Path
import socket
import sys
import time
import numpy as np
import pytest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.config import Settings
from app.embedding import load_engine


@pytest.mark.real_model
def test_real_cpu_model_with_network_blocked(monkeypatch):
    assert os.environ.get("AI_ALLOW_REAL_MODEL_TEST") == "1", "Run only on an approved isolated CPU host"
    def blocked(*args, **kwargs):
        raise OSError("OUTBOUND_NETWORK_BLOCKED_BY_TEST")
    monkeypatch.setattr(socket.socket, "connect", blocked)
    monkeypatch.setattr(socket, "create_connection", blocked)
    monkeypatch.setattr(socket, "getaddrinfo", blocked)
    settings = Settings.from_env()
    start = time.perf_counter()
    engine = load_engine(settings)
    loaded = time.perf_counter()
    values = engine.encode([("query", "Java SQL internship"), ("passage", "Dự án Java và cơ sở dữ liệu SQL")])
    assert all(v.vector.shape == (384,) and np.isfinite(v.vector).all() for v in values)
    score = engine.cosine(*values)
    assert score is not None and -1 <= score <= 1
    report = {"status": "REAL_MODEL_OFFLINE_SMOKE_PASS", "modelRevision": settings.model_revision,
              "loadSeconds": loaded - start, "inferenceSeconds": time.perf_counter() - loaded,
              "dimension": 384, "semanticQualityAcceptance": False}
    output = os.environ.get("AI_REAL_MODEL_REPORT")
    if output:
        Path(output).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
