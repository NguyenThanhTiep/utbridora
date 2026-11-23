import hashlib
import json
import numpy as np
import pytest
from app.config import Settings
from app.embedding import (Encoded, EmbeddingEngine, ModelUnavailable, load_engine,
                           normalize_text, verify_manifest)
from conftest import TOKEN, REVISION, FakeModel


def test_unicode_preserves_technical_names():
    assert normalize_text("  ky\u0303 năng\n C++ C# .NET Node.js SQL  ") == "kỹ năng C++ C# .NET Node.js SQL"


def test_prefix_batch_and_normalized_dimension(engine, model):
    values = engine.encode([("query", "Java SQL"), ("passage", "Tiếng Việt C++")])
    assert model.calls == [["query: Java SQL", "passage: Tiếng Việt C++"]]
    assert all(v.vector.shape == (384,) and np.isclose(np.linalg.norm(v.vector), 1) for v in values)
    assert all(v.chunks == 1 and not v.limited for v in values)
    assert np.isfinite(engine.cosine(*values))


def test_empty_is_no_data_not_zero_score(engine, model):
    left, right = engine.encode([("query", " \n"), ("passage", "Java")])
    assert left.vector is None and left.chunks == 0
    assert engine.cosine(left, right) is None
    assert model.calls == [["passage: Java"]]


def test_all_empty_does_not_call_encoder(engine, model):
    assert engine.encode([("query", "")])[0].vector is None
    assert model.calls == []


def test_long_text_chunking_is_bounded_and_reported(settings):
    model = FakeModel()
    engine = EmbeddingEngine(model, settings)
    value = engine.encode([("passage", "A" * 4000)])[0]
    assert value.chunks == 4 and value.limited
    assert len(model.calls[0]) == 4
    assert all(len(model.tokenizer.encode(t)) <= 512 and t.startswith("passage: ") for t in model.calls[0])


def test_chunking_preserves_tail_when_within_budget(engine, model):
    value = engine.encode([("query", "A" * 510 + " Java")])[0]
    assert value.chunks == 2 and not value.limited
    assert model.calls[0][-1].endswith(" Java")


@pytest.mark.parametrize("mode", ["dimension", "nan", "infinity", "zero"])
def test_invalid_model_vectors_are_rejected(settings, mode):
    model = FakeModel()
    def invalid(texts, **kwargs):
        shape = (len(texts), 383 if mode == "dimension" else 384)
        return np.full(shape, {"nan": np.nan, "infinity": np.inf, "zero": 0}.get(mode, 1.0))
    model.encode = invalid
    with pytest.raises(ModelUnavailable, match="INVALID_MODEL_OUTPUT"):
        EmbeddingEngine(model, settings).encode([("query", "Java")])


def test_missing_model_fails_without_importing_torch(settings):
    import sys
    with pytest.raises(ModelUnavailable, match="MODEL_UNAVAILABLE"):
        load_engine(settings)
    assert "torch" not in sys.modules


def manifest_fixture(root):
    names = ["model.safetensors", "config.json", "modules.json", "tokenizer_config.json", "tokenizer.json"]
    files = {}
    for name in names:
        (root / name).write_bytes(b"synthetic manifest fixture; not model weights")
        files[name] = hashlib.sha256((root / name).read_bytes()).hexdigest()
    value = {"modelId": "intfloat/multilingual-e5-small", "revision": REVISION,
             "dimension": 384, "license": "mit", "files": files}
    (root / "utbridora-model-manifest.json").write_text(json.dumps(value), encoding="utf-8")


def test_manifest_requires_every_artifact_and_detects_tampering(tmp_path):
    manifest_fixture(tmp_path)
    verify_manifest(tmp_path, REVISION)
    (tmp_path / "config.json").write_bytes(b"tampered")
    with pytest.raises(ModelUnavailable, match="CHECKSUM"):
        verify_manifest(tmp_path, REVISION)


def test_unregistered_artifact_is_rejected(tmp_path):
    manifest_fixture(tmp_path)
    (tmp_path / "pytorch_model.bin").write_bytes(b"unregistered")
    with pytest.raises(ModelUnavailable, match="MANIFEST"):
        verify_manifest(tmp_path, REVISION)


def test_wrong_revision_is_rejected(tmp_path):
    manifest_fixture(tmp_path)
    with pytest.raises(ModelUnavailable, match="MANIFEST"):
        verify_manifest(tmp_path, "a" * 40)


@pytest.mark.parametrize("overrides", [{"token": "short"}, {"model_revision": "main"},
    {"max_chunks": 5}, {"max_inputs": 17}, {"cpu_threads": 8}, {"timeout_seconds": 0},
    {"max_request_bytes": 2}, {"encode_batch_size": 0}])
def test_invalid_resource_or_security_config_fails(overrides):
    values = {"token": TOKEN, "model_revision": REVISION} | overrides
    with pytest.raises(ValueError):
        Settings(**values)
