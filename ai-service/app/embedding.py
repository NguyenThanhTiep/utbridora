"""Pure orchestration is testable without importing PyTorch or loading weights."""
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import unicodedata
import numpy as np
from .config import DIMENSION, MODEL_ID, Settings


class ModelUnavailable(RuntimeError):
    """Safe code only: never include input, paths or third-party exceptions."""


def normalize_text(text: str) -> str:
    return re.sub(r"\s+", " ", unicodedata.normalize("NFC", text)).strip()


def verify_manifest(root: Path, revision: str):
    root = root.resolve(strict=True)
    manifest = json.loads((root / "utbridora-model-manifest.json").read_text(encoding="utf-8"))
    if (manifest.get("modelId") != MODEL_ID or manifest.get("revision") != revision
            or manifest.get("dimension") != DIMENSION or manifest.get("license") != "mit"):
        raise ModelUnavailable("MODEL_MANIFEST_INVALID")
    files = manifest.get("files", {})
    required = {"model.safetensors", "config.json", "modules.json", "tokenizer_config.json"}
    if not required.issubset(files) or not {"tokenizer.json", "vocab.txt", "sentencepiece.bpe.model"}.intersection(files):
        raise ModelUnavailable("MODEL_MANIFEST_INVALID")
    # Every loadable model/config file must be registered, not just a caller-chosen subset.
    actual = {p.relative_to(root).as_posix() for p in root.rglob("*")
              if p.is_file() and ".cache" not in p.relative_to(root).parts
              and p.name != "utbridora-model-manifest.json"}
    if actual != set(files):
        raise ModelUnavailable("MODEL_MANIFEST_INVALID")
    for relative, expected in files.items():
        path = (root / relative).resolve(strict=True)
        if not path.is_relative_to(root) or not re.fullmatch(r"[a-f0-9]{64}", expected):
            raise ModelUnavailable("MODEL_MANIFEST_INVALID")
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(block)
        if digest.hexdigest() != expected:
            raise ModelUnavailable("MODEL_CHECKSUM_MISMATCH")


@dataclass(frozen=True)
class Encoded:
    vector: np.ndarray | None
    chunks: int
    limited: bool


class EmbeddingEngine:
    def __init__(self, model, settings: Settings):
        self.model = model
        self.tokenizer = model.tokenizer
        self.settings = settings

    def _chunks(self, text: str, role: str):
        if role not in ("query", "passage"):
            raise ValueError("INVALID_ROLE")
        text = normalize_text(text)
        if not text:
            return [], [], False
        tokens = self.tokenizer.encode(text, add_special_tokens=False)
        prefix = role + ": "
        budget = 512 - self.tokenizer.num_special_tokens_to_add(pair=False) - len(
            self.tokenizer.encode(prefix, add_special_tokens=False)) - 2
        if budget < 1:
            raise ModelUnavailable("INVALID_TOKENIZER")
        chunks, weights, offset = [], [], 0
        while offset < len(tokens) and len(chunks) < self.settings.max_chunks:
            end = min(offset + budget, len(tokens))
            while end > offset:
                content = self.tokenizer.decode(tokens[offset:end], skip_special_tokens=True,
                                                clean_up_tokenization_spaces=False)
                chunk = prefix + content
                # Validate re-tokenisation as well: no silent model truncation.
                if len(self.tokenizer.encode(chunk, add_special_tokens=True)) <= 512:
                    break
                end -= 1
            if end == offset:
                raise ModelUnavailable("INVALID_TOKENIZER")
            chunks.append(chunk)
            weights.append(end - offset)
            offset = end
        return chunks, weights, offset < len(tokens)

    def encode(self, inputs: list[tuple[str, str]]) -> list[Encoded]:
        plans = [self._chunks(text, role) for role, text in inputs]
        chunks = [chunk for plan in plans for chunk in plan[0]]
        if not chunks:
            return [Encoded(None, 0, False) for _ in inputs]
        vectors = np.asarray(self.model.encode(
            chunks, batch_size=self.settings.encode_batch_size,
            normalize_embeddings=True, convert_to_numpy=True, show_progress_bar=False,
        ), dtype=np.float32)
        if vectors.shape != (len(chunks), DIMENSION) or not np.isfinite(vectors).all():
            raise ModelUnavailable("INVALID_MODEL_OUTPUT")
        norms = np.linalg.norm(vectors, axis=1)
        if np.any(norms < 1e-8):
            raise ModelUnavailable("INVALID_MODEL_OUTPUT")
        vectors = vectors / norms[:, None]
        results, offset = [], 0
        for texts, weights, limited in plans:
            count = len(texts)
            if count == 0:
                results.append(Encoded(None, 0, False))
                continue
            value = np.average(vectors[offset:offset + count], axis=0, weights=weights).astype(np.float32)
            norm = np.linalg.norm(value)
            if not np.isfinite(norm) or norm < 1e-8:
                raise ModelUnavailable("INVALID_MODEL_OUTPUT")
            results.append(Encoded(value / norm, count, limited))
            offset += count
        return results

    @staticmethod
    def cosine(left: Encoded, right: Encoded) -> float | None:
        if left.vector is None or right.vector is None:
            return None
        score = float(np.dot(left.vector.astype(np.float64), right.vector.astype(np.float64)))
        if not np.isfinite(score) or not -1.000001 <= score <= 1.000001:
            raise ModelUnavailable("INVALID_MODEL_OUTPUT")
        # Only correct machine precision at the mathematical boundary, never rescale to percent.
        return min(1.0, max(-1.0, score))


def load_engine(settings: Settings) -> EmbeddingEngine:
    try:
        if settings.model_path is None:
            raise ModelUnavailable("MODEL_UNAVAILABLE")
        verify_manifest(settings.model_path, settings.model_revision)
        os.environ["HF_HUB_OFFLINE"] = "1"
        os.environ["TRANSFORMERS_OFFLINE"] = "1"
        os.environ["HF_HUB_DISABLE_TELEMETRY"] = "1"
        os.environ["DO_NOT_TRACK"] = "1"
        os.environ["OMP_NUM_THREADS"] = str(settings.cpu_threads)
        os.environ["MKL_NUM_THREADS"] = str(settings.cpu_threads)
        import torch
        from sentence_transformers import SentenceTransformer
        if torch.version.cuda is not None:
            raise ModelUnavailable("CPU_BUILD_REQUIRED")
        torch.set_num_threads(settings.cpu_threads)
        model = SentenceTransformer(str(settings.model_path.resolve()), device="cpu",
                                    local_files_only=True, trust_remote_code=False,
                                    token=False, model_kwargs={"use_safetensors": True})
        model.eval()
        model.max_seq_length = 512
        if model.get_sentence_embedding_dimension() != DIMENSION:
            raise ModelUnavailable("INVALID_MODEL_OUTPUT")
        # SentenceTransformer.encode uses inference mode; this wrapper enforces it explicitly too.
        class InferenceModel:
            tokenizer = model.tokenizer

            def encode(self, *args, **kwargs):
                with torch.inference_mode():
                    return model.encode(*args, **kwargs)
        return EmbeddingEngine(InferenceModel(), settings)
    except Exception:
        raise ModelUnavailable("MODEL_UNAVAILABLE") from None
