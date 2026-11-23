"""Fixtures are deliberately fake. These tests never claim real-model quality."""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import numpy as np
import pytest
from app.config import Settings
from app.embedding import EmbeddingEngine

REVISION = "614241f622f53c4eeff9890bdc4f31cfecc418b3"
TOKEN = "synthetic_test_only_not_a_credential_123456789"


class CharacterTokenizer:
    def encode(self, text, add_special_tokens=True):
        values = [ord(c) for c in text]
        return [1] + values + [2] if add_special_tokens else values

    def decode(self, values, **kwargs):
        return "".join(chr(v) for v in values)

    def num_special_tokens_to_add(self, pair=False):
        return 2


class FakeModel:
    tokenizer = CharacterTokenizer()

    def __init__(self):
        self.calls = []

    def encode(self, texts, **kwargs):
        self.calls.append(list(texts))
        result = np.zeros((len(texts), 384), dtype=np.float32)
        for i, text in enumerate(texts):
            result[i, 0], result[i, 1] = 1.0, len(text) / 512
        return result


@pytest.fixture
def settings():
    return Settings(token=TOKEN, model_revision=REVISION)


@pytest.fixture
def model():
    return FakeModel()


@pytest.fixture
def engine(model, settings):
    return EmbeddingEngine(model, settings)
