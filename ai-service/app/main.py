import asyncio
from concurrent.futures import ThreadPoolExecutor
from contextlib import asynccontextmanager
import hmac
from threading import BoundedSemaphore
from fastapi import FastAPI, HTTPException
from fastapi.exceptions import RequestValidationError
from starlette.responses import JSONResponse
from .config import Settings
from .embedding import EmbeddingEngine, load_engine
from .schemas import (EmbeddingRequest, EmbeddingResponse, EmbeddingResult,
                      SimilarityRequest, SimilarityResponse, SimilarityResult, ErrorResponse)

INTERNAL_ERRORS = {status: {"model": ErrorResponse} for status in (400, 403, 408, 413, 415, 422, 503)}


def error(code: str, status: int):
    return JSONResponse({"errorCode": code}, status_code=status)


class InternalBoundary:
    """Authenticate before buffering; bound bytes even without Content-Length."""
    def __init__(self, app, settings: Settings):
        self.app, self.settings = app, settings

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or not scope["path"].startswith("/internal/"):
            return await self.app(scope, receive, send)
        headers = scope.get("headers", [])
        tokens = [v for k, v in headers if k.lower() == b"x-ai-token"]
        if len(tokens) != 1 or not hmac.compare_digest(tokens[0], self.settings.token.encode("ascii")):
            return await error("ACCESS_DENIED", 403)(scope, receive, send)
        lengths = [v for k, v in headers if k.lower() == b"content-length"]
        if len(lengths) > 1 or (lengths and (not lengths[0].isdigit()
                or len(lengths[0]) > 10 or int(lengths[0]) > self.settings.max_request_bytes)):
            return await error("PAYLOAD_TOO_LARGE", 413)(scope, receive, send)
        encodings = [v for k, v in headers if k.lower() == b"content-encoding"]
        if encodings and encodings != [b"identity"]:
            return await error("UNSUPPORTED_ENCODING", 415)(scope, receive, send)
        types = [v for k, v in headers if k.lower() == b"content-type"]
        if len(types) != 1 or types[0].split(b";", 1)[0].strip().lower() != b"application/json":
            return await error("UNSUPPORTED_MEDIA_TYPE", 415)(scope, receive, send)
        body = bytearray()
        deadline = asyncio.get_running_loop().time() + self.settings.timeout_seconds
        while True:
            try:
                remaining = deadline - asyncio.get_running_loop().time()
                if remaining <= 0:
                    raise TimeoutError
                event = await asyncio.wait_for(receive(), remaining)
            except TimeoutError:
                return await error("REQUEST_TIMEOUT", 408)(scope, receive, send)
            if event["type"] == "http.disconnect":
                return
            if event["type"] != "http.request":
                return await error("INVALID_REQUEST", 400)(scope, receive, send)
            block = event.get("body", b"")
            if len(body) + len(block) > self.settings.max_request_bytes:
                return await error("PAYLOAD_TOO_LARGE", 413)(scope, receive, send)
            body.extend(block)
            if not event.get("more_body", False):
                break
        delivered = False

        async def replay():
            nonlocal delivered
            if not delivered:
                delivered = True
                return {"type": "http.request", "body": bytes(body), "more_body": False}
            return await receive()
        await self.app(scope, replay, send)


def create_app(settings: Settings | None = None, loader=load_engine):
    # Dependency injection is for tests. The ASGI factory always defaults to the real loader.
    settings = settings or Settings.from_env()
    pool = ThreadPoolExecutor(max_workers=1, thread_name_prefix="e5-inference")
    gate = BoundedSemaphore(1)
    engine: EmbeddingEngine | None = None

    @asynccontextmanager
    async def lifespan(app):
        nonlocal engine
        try:
            engine = await asyncio.to_thread(loader, settings)
        except Exception:
            engine = None  # Never include third-party error text or local paths in responses/logs.
        try:
            yield
        finally:
            pool.shutdown(wait=False, cancel_futures=True)
            engine = None

    app = FastAPI(title="UT.Bridora internal semantic service", lifespan=lifespan,
                  docs_url=None, redoc_url=None, openapi_url=None)
    app.add_middleware(InternalBoundary, settings=settings)

    @app.exception_handler(RequestValidationError)
    async def invalid_request(request, exc):
        return error("INVALID_REQUEST", 422)  # Pydantic errors can contain the original private input.

    @app.get("/health")
    async def health():
        return {"status": "ALIVE"}

    @app.get("/ready", responses={503: {"model": ErrorResponse}})
    async def ready():
        if engine is None:
            return error("MODEL_UNAVAILABLE", 503)
        return {"status": "READY", "modelRevision": settings.model_revision}

    async def infer(operation):
        if engine is None:
            raise HTTPException(503, detail="MODEL_UNAVAILABLE")
        if not gate.acquire(blocking=False):
            raise HTTPException(503, detail="AI_BUSY")
        try:
            future = pool.submit(operation)
        except RuntimeError:
            gate.release()
            raise HTTPException(503, detail="MODEL_UNAVAILABLE") from None
        # A timed-out caller does not free the slot while CPU work is still running.
        future.add_done_callback(lambda completed: gate.release())
        wrapped = asyncio.wrap_future(future)
        wrapped.add_done_callback(lambda done: None if done.cancelled() else done.exception())
        try:
            return await asyncio.wait_for(asyncio.shield(wrapped), settings.timeout_seconds)
        except TimeoutError:
            raise HTTPException(503, detail="AI_TIMEOUT") from None
        except asyncio.CancelledError:
            raise
        except Exception:
            raise HTTPException(503, detail="MODEL_UNAVAILABLE") from None

    @app.exception_handler(HTTPException)
    async def safe_http_error(request, exc):
        allowed = {"MODEL_UNAVAILABLE", "AI_BUSY", "AI_TIMEOUT", "BATCH_TOO_LARGE"}
        return error(exc.detail if exc.detail in allowed else "REQUEST_FAILED", exc.status_code)

    @app.post("/internal/v1/embeddings", response_model=EmbeddingResponse, responses=INTERNAL_ERRORS)
    async def embeddings(request: EmbeddingRequest):
        if len(request.inputs) > settings.max_inputs:
            raise HTTPException(413, detail="BATCH_TOO_LARGE")

        def operation():
            encoded = engine.encode([(x.role, x.text) for x in request.inputs])
            return EmbeddingResponse(requestId=request.requestId, templateVersion=request.templateVersion, modelRevision=settings.model_revision,
                results=[EmbeddingResult(key=x.key, status="READY" if v.vector is not None else "NO_DATA",
                    vector=None if v.vector is None else v.vector.tolist(), chunks=v.chunks, limited=v.limited)
                    for x, v in zip(request.inputs, encoded, strict=True)])
        return await infer(operation)

    @app.post("/internal/v1/similarity", response_model=SimilarityResponse, responses=INTERNAL_ERRORS)
    async def similarity(request: SimilarityRequest):
        if len(request.passages) > settings.max_inputs:
            raise HTTPException(413, detail="BATCH_TOO_LARGE")

        def operation():
            encoded = engine.encode([("query", request.query)] + [("passage", p.text) for p in request.passages])
            query, passages = encoded[0], encoded[1:]
            results = []
            for item, passage in zip(request.passages, passages, strict=True):
                score = engine.cosine(query, passage)
                results.append(SimilarityResult(key=item.key, status="NO_DATA" if score is None else "READY",
                    score=score, queryChunks=query.chunks, passageChunks=passage.chunks,
                    limited=query.limited or passage.limited))
            return SimilarityResponse(requestId=request.requestId, templateVersion=request.templateVersion, modelRevision=settings.model_revision,
                                      results=results)
        return await infer(operation)
    return app
