"""Short-lived loopback cross-language test harness. Fake encoder, NEVER an AI mode."""
import argparse
import json
import os
from pathlib import Path
import socket
import sys
import threading
import time
import uvicorn
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.config import Settings
from app.embedding import EmbeddingEngine
from app.main import create_app
from conftest import FakeModel, REVISION, TOKEN


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ready-file", required=True, type=Path)
    parser.add_argument("--stop-file", required=True, type=Path)
    args = parser.parse_args()
    settings = Settings(token=TOKEN, model_revision=REVISION)
    app = create_app(settings, loader=lambda _: EmbeddingEngine(FakeModel(), settings))
    listener = socket.socket()
    listener.bind(("127.0.0.1", 0)); listener.listen(16)
    server = uvicorn.Server(uvicorn.Config(app, host="127.0.0.1", log_level="warning", access_log=False, limit_concurrency=4))
    thread = threading.Thread(target=lambda: server.run(sockets=[listener]), daemon=True)
    thread.start()
    until = time.monotonic() + 90
    try:
        while not server.started and thread.is_alive() and time.monotonic() < until:
            time.sleep(0.01)
        if not server.started:
            raise RuntimeError("PROTOCOL_HARNESS_START_FAILED")
        args.ready_file.write_text(json.dumps({"pid": os.getpid(), "port": listener.getsockname()[1],
            "evidence": "FAKE_ENCODER_HTTP_PROTOCOL_ONLY"}), encoding="utf-8")
        while not args.stop_file.exists() and time.monotonic() < until and thread.is_alive():
            time.sleep(0.1)
    finally:
        server.should_exit = True
        thread.join(timeout=5)
        listener.close()


if __name__ == "__main__":
    main()
