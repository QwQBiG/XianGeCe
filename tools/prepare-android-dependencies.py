#!/usr/bin/env python3
"""Prepare Android AARs from pinned upstream releases or verify an offline checkout."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys
import tempfile
from urllib.request import urlopen
from zipfile import BadZipFile, ZipFile, ZipInfo

ROOT = Path(__file__).resolve().parents[1]
LIBS = ROOT / "app" / "libs"


def verify(path, expected):
    if path.stat().st_size != expected["bytes"]:
        raise ValueError(f"{path.name}: unexpected length")
    digest = hashlib.sha256()
    with path.open("rb") as archive:
        for block in iter(lambda: archive.read(1024 * 1024), b""):
            digest.update(block)
    if digest.hexdigest() != expected["sha256"]:
        raise ValueError(f"{path.name}: SHA-256 mismatch")


def remove_bundled_runtime(source, destination, excluded):
    with ZipFile(source) as upstream, ZipFile(destination, "w") as output:
        if not excluded.issubset(upstream.namelist()):
            raise ValueError("Expected ONNX Runtime entries are missing")
        for entry in upstream.infolist():
            if entry.filename in excluded:
                continue
            # A stored ZIP with fixed metadata is reproducible across operating systems and zlib versions.
            info = ZipInfo(entry.filename)
            info.create_system = 3
            info.external_attr = 0o644 << 16
            output.writestr(info, upstream.read(entry))


def prepare(dependency, check_only):
    destination = LIBS / dependency["file"]
    if destination.exists() or check_only:
        verify(destination, dependency)
    else:
        build = ROOT / "build"
        build.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=build) as tmp:
            download = Path(tmp) / "upstream.aar"
            print("Downloading", dependency["url"], flush=True)
            with urlopen(dependency["url"], timeout=60) as response, download.open("wb") as out:
                shutil.copyfileobj(response, out, length=1024 * 1024)
            source = dependency.get("upstream", dependency)
            verify(download, source)
            ready = download
            if "exclude" in dependency:
                ready = Path(tmp) / destination.name
                remove_bundled_runtime(download, ready, set(dependency["exclude"]))
                verify(ready, dependency)
            ready.replace(destination)
    print("OK", destination.name, dependency["sha256"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify installed files without downloading")
    args = parser.parse_args()
    manifest = json.loads((LIBS / "dependency-manifest.json").read_text(encoding="utf-8"))
    for dependency in manifest:
        try:
            prepare(dependency, args.check)
        except (OSError, ValueError, BadZipFile) as error:
            sys.exit(f'{dependency["file"]}: {error}')


if __name__ == "__main__":
    main()
