"""Export the internal contract without loading weights, starting a server or opening a DB."""
import argparse
import json
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from app.config import Settings
from app.main import create_app


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    lock = json.loads((ROOT / "model-lock.json").read_text(encoding="utf-8"))
    settings = Settings(token="schema_export_only_not_a_credential_123456789", model_revision=lock["revision"])
    app = create_app(settings, loader=lambda _: None)
    value = app.openapi()
    value["info"]["description"] = "Internal optional shadow semantics. X-AI-Token required; no public business API."
    for route in ("/internal/v1/embeddings", "/internal/v1/similarity"):
        value["paths"][route]["post"]["security"] = [{"InternalToken": []}]
    value["components"]["securitySchemes"] = {"InternalToken": {"type": "apiKey", "in": "header", "name": "X-AI-Token"}}
    content = json.dumps(value, sort_keys=True, indent=2, ensure_ascii=False) + "\n"
    target = ROOT / "contracts" / "openapi.json"
    if args.check:
        if not target.is_file() or target.read_text(encoding="utf-8") != content:
            raise SystemExit("Internal OpenAPI drift")
    else:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
    print("Internal OpenAPI verified" if args.check else "Internal OpenAPI exported")


if __name__ == "__main__":
    main()
