from dataclasses import dataclass, field
import json
import os
from pathlib import Path
import re

MODEL_ID = "intfloat/multilingual-e5-small"
DIMENSION = 384
SCHEMA_VERSION = "semantic-v1"
TEMPLATE_VERSION = "canonical-v1"
AGGREGATION_VERSION = "token-weighted-mean-v1"
LOCK_PATH = Path(__file__).resolve().parents[1] / "model-lock.json"


@dataclass(frozen=True)
class Settings:
    token: str = field(repr=False)
    model_path: Path | None = None
    model_revision: str = ""
    max_request_bytes: int = 262144
    max_inputs: int = 16
    max_chunks: int = 4
    encode_batch_size: int = 8
    cpu_threads: int = 1
    timeout_seconds: float = 10.0

    def __post_init__(self):
        if not re.fullmatch(r"[A-Za-z0-9_-]{32,256}", self.token):
            raise ValueError("AI_INTERNAL_TOKEN must be a 32–256 character opaque token")
        if not re.fullmatch(r"[a-f0-9]{40}", self.model_revision):
            raise ValueError("AI_MODEL_REVISION must be a pinned commit")
        if not 1024 <= self.max_request_bytes <= 1048576:
            raise ValueError("Invalid request byte limit")
        if not 1 <= self.max_inputs <= 16 or not 1 <= self.max_chunks <= 4:
            raise ValueError("Invalid batch/chunk limits")
        if not 1 <= self.encode_batch_size <= 16 or not 1 <= self.cpu_threads <= 2:
            raise ValueError("Invalid CPU/batch limits")
        if not 0.01 <= self.timeout_seconds <= 60:
            raise ValueError("Invalid inference deadline")

    @classmethod
    def from_env(cls):
        lock = json.loads(LOCK_PATH.read_text(encoding="utf-8"))
        revision = os.environ.get("AI_MODEL_REVISION", lock["revision"])
        if revision != lock["revision"]:
            raise ValueError("Model revision differs from the reviewed model lock")
        path = os.environ.get("AI_MODEL_PATH", "")
        return cls(
            token=os.environ.get("AI_INTERNAL_TOKEN", ""),
            model_path=Path(path) if path else None,
            model_revision=revision,
            max_request_bytes=int(os.environ.get("AI_MAX_REQUEST_BYTES", "262144")),
            max_inputs=int(os.environ.get("AI_MAX_INPUTS", "16")),
            max_chunks=int(os.environ.get("AI_MAX_CHUNKS", "4")),
            encode_batch_size=int(os.environ.get("AI_ENCODE_BATCH_SIZE", "8")),
            cpu_threads=int(os.environ.get("AI_CPU_THREADS", "1")),
            timeout_seconds=float(os.environ.get("AI_TIMEOUT_SECONDS", "10")),
        )
