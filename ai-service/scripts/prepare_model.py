"""Explicit online setup only; never imported by runtime. Does not load PyTorch."""
import argparse
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from app.embedding import verify_manifest


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--destination", type=Path, required=True)
    args = parser.parse_args()
    destination = args.destination.resolve()
    if destination.exists() and any(destination.iterdir()):
        parser.error("Destination must be empty; preserve existing model artifacts")
    lock = json.loads((ROOT / "model-lock.json").read_text(encoding="utf-8"))
    from huggingface_hub import snapshot_download
    snapshot_download(repo_id=lock["modelId"], revision=lock["revision"], local_dir=destination,
        token=False, max_workers=2,
        allow_patterns=["config.json", "modules.json", "sentence_bert_config.json", "1_Pooling/config.json",
                        "tokenizer.json", "tokenizer_config.json", "special_tokens_map.json",
                        "sentencepiece.bpe.model", "model.safetensors", "README.md"])
    files = {}
    for path in sorted(destination.rglob("*")):
        relative = path.relative_to(destination)
        if path.is_file() and ".cache" not in relative.parts:
            digest = hashlib.sha256()
            with path.open("rb") as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b""):
                    digest.update(block)
            files[relative.as_posix()] = digest.hexdigest()
    manifest = {key: lock[key] for key in ("modelId", "revision", "dimension", "license")}
    manifest["files"] = files
    (destination / "utbridora-model-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    verify_manifest(destination, lock["revision"])
    print(json.dumps({"status": "VERIFIED_DOWNLOAD", "revision": lock["revision"], "fileCount": len(files)}))


if __name__ == "__main__":
    main()
