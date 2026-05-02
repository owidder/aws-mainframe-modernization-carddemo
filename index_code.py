#!/usr/bin/env python3
"""
Incremental indexer: indexes source files from app/ into Qdrant.
On each run only new or modified files are processed; deleted files
are removed from the index. Uses Ollama (nomic-embed-text) for embeddings.

Usage:
    .venv/bin/python index_code.py          # incremental update
    .venv/bin/python index_code.py --full   # force full re-index
"""

import sys
import uuid
from pathlib import Path
import ollama
from qdrant_client import QdrantClient
from qdrant_client.models import (
    Distance, VectorParams, PointStruct,
    Filter, FieldCondition, MatchValue,
)

APP_DIR = Path(__file__).parent / "app"
QDRANT_URL = "http://localhost:6335"
OLLAMA_URL = "http://localhost:11434"
COLLECTION_NAME = "carddemo"
EMBEDDING_MODEL = "nomic-embed-text"
CHUNK_SIZE = 2000  # characters per chunk (nomic-embed-text: 8192 tokens max)

INCLUDE_EXTENSIONS = {
    ".cbl", ".cob",   # COBOL
    ".cpy",           # Copybooks
    ".jcl",           # JCL
    ".asm",           # Assembler
    ".bms",           # BMS maps
    ".ddl",           # DDL
    ".dcl",           # DCL
    ".ctl",           # Control
    ".csd",           # CSD
    ".psb",           # PSB
    ".dbd",           # DBD
    ".txt", ".md",    # Text / docs
    ".sql",           # SQL
    ".prc",           # Procedures
}


def ext_matches(path: Path) -> bool:
    return path.suffix.lower() in INCLUDE_EXTENSIONS


def chunk_text(text: str) -> list[str]:
    """Split text into chunks at line boundaries."""
    if len(text) <= CHUNK_SIZE:
        return [text]
    chunks = []
    start = 0
    while start < len(text):
        end = start + CHUNK_SIZE
        if end < len(text):
            nl = text.rfind("\n", start, end)
            if nl > start:
                end = nl + 1
        chunks.append(text[start:end])
        start = end
    return chunks


EXCLUDE_DIRS = {"data", "EBCDIC", "catlg"}  # directories to skip (raw data, no source code)


def collect_files(app_dir: Path) -> dict[str, float]:
    """Return {rel_path_str: mtime} for all matching files, skipping data dirs."""
    result = {}
    for f in app_dir.rglob("*"):
        if not f.is_file() or not ext_matches(f):
            continue
        # Skip files inside excluded directories
        if any(part in EXCLUDE_DIRS for part in f.parts):
            continue
        rel = str(f.relative_to(app_dir.parent))
        result[rel] = f.stat().st_mtime
    return result


def load_indexed_state(client: QdrantClient) -> dict[str, float]:
    """Return {file_path: mtime} for all files currently in Qdrant.
    Uses the mtime stored in the payload of the first chunk of each file.
    """
    state: dict[str, float] = {}
    offset = None
    while True:
        result, next_offset = client.scroll(
            collection_name=COLLECTION_NAME,
            scroll_filter=Filter(
                must=[FieldCondition(key="chunk_index", match=MatchValue(value=0))]
            ),
            limit=200,
            offset=offset,
            with_payload=["file_path", "mtime"],
            with_vectors=False,
        )
        for point in result:
            fp = point.payload.get("file_path")
            mt = point.payload.get("mtime", 0.0)
            if fp:
                state[fp] = mt
        if next_offset is None:
            break
        offset = next_offset
    return state


def delete_file_vectors(client: QdrantClient, rel_path: str):
    """Delete all vectors belonging to a given file."""
    client.delete(
        collection_name=COLLECTION_NAME,
        points_selector=Filter(
            must=[FieldCondition(key="file_path", match=MatchValue(value=rel_path))]
        ),
    )


def index_file(
    client: QdrantClient,
    file_path: Path,
    rel_path: str,
    mtime: float,
) -> int:
    """Index a single file. Returns number of vectors upserted."""
    try:
        text = file_path.read_text(encoding="utf-8", errors="replace")
    except Exception as e:
        print(f"  SKIP (read error): {rel_path}: {e}")
        return 0

    if not text.strip():
        return 0

    chunks = chunk_text(text)
    embeddings = embed(chunks)
    points = []
    for i, (chunk, embedding) in enumerate(zip(chunks, embeddings)):
        points.append(
            PointStruct(
                id=str(uuid.uuid4()),
                vector=embedding,
                payload={
                    "file_path": rel_path,
                    "file_name": file_path.name,
                    "extension": file_path.suffix.lower(),
                    "chunk_index": i,
                    "total_chunks": len(chunks),
                    "mtime": mtime,
                    "content": chunk,
                },
            )
        )

    # Upload in batches of 50
    for i in range(0, len(points), 50):
        client.upsert(collection_name=COLLECTION_NAME, points=points[i:i+50])

    return len(points)


def ensure_collection(client: QdrantClient, dim: int, full: bool):
    existing = [c.name for c in client.get_collections().collections]
    if full and COLLECTION_NAME in existing:
        print(f"  Full re-index: deleting collection '{COLLECTION_NAME}' ...")
        client.delete_collection(COLLECTION_NAME)
        existing = []
    if COLLECTION_NAME not in existing:
        client.create_collection(
            collection_name=COLLECTION_NAME,
            vectors_config=VectorParams(size=dim, distance=Distance.COSINE),
        )
        print(f"  Collection '{COLLECTION_NAME}' created.")


def embed(texts: list[str]) -> list[list[float]]:
    response = ollama.embed(model=EMBEDDING_MODEL, input=texts)
    return response.embeddings


def main():
    full = "--full" in sys.argv

    print(f"Connecting to Qdrant at {QDRANT_URL} ...")
    client = QdrantClient(url=QDRANT_URL)

    print(f"Warming up embedding model '{EMBEDDING_MODEL}' via Ollama ...")
    dim = len(embed(["test"])[0])
    print(f"Embedding dimension: {dim}")

    ensure_collection(client, dim, full)

    disk_files = collect_files(APP_DIR)
    print(f"Found {len(disk_files)} source files on disk.")

    if full:
        indexed_state: dict[str, float] = {}
    else:
        indexed_state = load_indexed_state(client)
        print(f"Currently indexed: {len(indexed_state)} files.")

    # Determine what to do
    to_add    = {p: m for p, m in disk_files.items() if p not in indexed_state}
    to_update = {p: m for p, m in disk_files.items()
                 if p in indexed_state and m > indexed_state[p] + 0.5}
    to_delete = [p for p in indexed_state if p not in disk_files]

    print(f"  New:     {len(to_add)}")
    print(f"  Changed: {len(to_update)}")
    print(f"  Deleted: {len(to_delete)}")

    if not (to_add or to_update or to_delete):
        print("\nIndex is up to date. Nothing to do.")
        return

    total_vectors = 0

    # Remove deleted files
    for rel in to_delete:
        print(f"  DEL  {rel}")
        delete_file_vectors(client, rel)

    # Add new files
    for rel, mtime in sorted(to_add.items()):
        file_path = APP_DIR.parent / rel
        n = index_file(client, file_path, rel, mtime)
        if n:
            print(f"  ADD  {rel}  ({n} vector(s))")
            total_vectors += n

    # Re-index changed files
    for rel, mtime in sorted(to_update.items()):
        file_path = APP_DIR.parent / rel
        delete_file_vectors(client, rel)
        n = index_file(client, file_path, rel, mtime)
        if n:
            print(f"  UPD  {rel}  ({n} vector(s))")
            total_vectors += n

    info = client.get_collection(COLLECTION_NAME)
    print(f"\nDone. {info.points_count} total vectors in '{COLLECTION_NAME}'.")
    print(f"This run: +{total_vectors} vectors added/updated, {len(to_delete)} file(s) removed.")


if __name__ == "__main__":
    main()
