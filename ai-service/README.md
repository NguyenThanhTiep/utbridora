# UT.Bridora internal semantic service — lượt 1

Optional **school-only shadow semantics**. Uses `intfloat/multilingual-e5-small` revision `614241f622f53c4eeff9890bdc4f31cfecc418b3`, CPU, 384 dimensions. Structured canonical snapshots only; no full CV/JD parsing, PII, database credentials, public business endpoint or ranking changes.

See [architecture](../docs/guides/AI_MATCHING_ARCHITECTURE.md), [implementation](../docs/guides/AI_MATCHING_IMPLEMENTATION.md), [setup](../docs/guides/AI_MATCHING_LOCAL_SETUP.md) and [test report](../docs/guides/AI_MATCHING_TEST_REPORT.md).

## Lightweight checks

Use a private Python 3.12 virtual environment. From this directory:

```sh
python -m pip install -r requirements-test.txt
python -m pytest tests --junitxml=python-unit-report.xml
python scripts/export_openapi.py --check
python -m pip check
```

These checks do not install/import PyTorch or load weights. Encoder fixtures are fake and are labelled as such. The protocol harness in `tests/protocol_server.py` is short-lived loopback test code, not a production mode or evidence of semantic quality.

## Real model setup — isolated host only

Do not perform heavyweight setup/inference on the current shared 8 GB workstation, which was observed with less than 1 GB free. Do not run Docker here.

On a separate CPU environment, from an empty Python 3.12 venv:

```sh
python -m pip install -r requirements-torch-cpu.txt
python -m pip install -r requirements-model.txt -r requirements-test.txt
python -m pip check
python scripts/prepare_model.py --destination models/e5-614241f
python -m pip freeze > model-runtime-report.txt
```

The first requirements file uses only the official CPU wheel index; the second requires `torch==2.15.0+cpu` and cannot silently accept a CUDA wheel. Model library versions are pinned from official package metadata; their joint runtime remains **NOT RUN** until real-model validation is performed. Light dependencies have a resolved constraints file. Preserve the actual complete runtime freeze in the isolated-host evidence; do not claim a cross-platform full model lock has already been verified.

Model preparation is an explicit online setup step. It refuses nonempty destinations, downloads only the locked revision, records SHA-256 for every artifact and verifies the manifest. Keep HF offline mode unset during this online step. Runtime loading requires the manifest and checks artifact hashes, dimension, model ID and revision.

## Runtime

Supply a private random `AI_INTERNAL_TOKEN` of at least 32 URL-safe characters. Use the same secret in Java and Python; never print or commit it. Configure `AI_MODEL_PATH` with the prepared model directory and use the reviewed values in `.env.example`. The application does not automatically read `.env` files.

After setup, set `HF_HUB_OFFLINE=1`, `HF_HUB_DISABLE_TELEMETRY=1` and `AI_MODEL_PATH`; then:

```sh
python -m uvicorn app.main:create_app --factory --host 127.0.0.1 --port 18091 --workers 1 --limit-concurrency 4 --timeout-keep-alive 5 --no-access-log
```

- `/health`: liveness only.
- `/ready`: 503 if the model cannot be loaded; no fake fallback.
- `/internal/v1/embeddings` and `/internal/v1/similarity`: require `X-AI-Token`, strict JSON schemas, byte/item/text/chunk limits.
- API docs/OpenAPI routes are disabled at runtime. The reviewed artifact is [contracts/openapi.json](contracts/openapi.json).
- One inference slot, no request queue inside Python. Timeout does not release that slot until the underlying CPU operation actually completes.
- Errors do not echo inputs or third-party messages.

Embeddings use `query: ` / `passage: ` prefixes, at most 512 tokenizer tokens per chunk, at most four chunks per text, normalized token-weighted chunk aggregation and finite cosine similarity. The `limited` flag reports any unprocessed tail. Cosine score is not a percentage of eligibility or probability of hiring.

## Explicit real-model offline smoke test

Only on the isolated environment, after setup:

```sh
AI_ALLOW_REAL_MODEL_TEST=1 AI_REAL_MODEL_REPORT=real-model-report.json python -m pytest tests_real/test_model_offline.py --junitxml=real-model-report.xml
```

Supply the private token and model path through the environment. The test blocks Python socket connections/DNS before model loading and inference. Missing opt-in, dependencies or weights fail; they do not silently skip. Use OS-level outbound blocking as an additional deployment verification. This smoke test proves neither semantic quality nor VPS/R31 performance.

Lượt 3 đã bổ sung Docker recipe và advisory API/UI ở backend/web; xem [hướng dẫn hiện hành](../docs/guides/AI_MATCHING_ADVISORY.md). Lượt 4 ngày 23/11/2026 đã chuẩn bị runner môi trường riêng, đóng gói working source, HTTP microbenchmark thật và công cụ tính quality metrics. Chưa có VPS hoặc nhãn chuyên môn để thực hiện real-model/full-stack/quality/pilot; không coi bộ chạy đã chuẩn bị là kết quả PASS. No production deployment has been performed.
