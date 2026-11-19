"""Build importable model ZIPs from pinned, hash-checked public files (Python 3.10+)."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import sys
import time
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def valid(path, entry):
    return (path.is_file() and path.stat().st_size == entry['bytes']
            and sha256(path) == entry['sha256'])


def safe_path(root, name):
    relative = PurePosixPath(name)
    if relative.is_absolute() or any(x in ('..', '.') for x in relative.parts) or '\\' in name or ':' in name:
        raise ValueError(f'Unsafe archive name: {name}')
    target = root.joinpath(*relative.parts).resolve()
    if not target.is_relative_to(root.resolve()):
        raise ValueError(f'Outside destination: {name}')
    return target


def download(entry, target, mirror=False):
    if valid(target, entry):
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    part = target.with_name(target.name + '.part')
    if part.exists() and part.stat().st_size >= entry['bytes']:
        if valid(part, entry):
            part.replace(target)
            return
        part.unlink()  # Only this exact script-owned incomplete download.
    urls = [entry['domesticUrl'], entry['officialUrl']] if mirror else [entry['officialUrl'], entry['domesticUrl']]
    error = None
    for attempt in range(4):
        url = urls[attempt % 2]
        offset = part.stat().st_size if part.exists() else 0
        headers = {'User-Agent': 'Xiangece-OfflinePacks/1.0', 'Accept-Encoding': 'identity'}
        if offset:
            headers['Range'] = f'bytes={offset}-'
        try:
            request = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(request, timeout=40) as response:
                resumed = response.status == 206 and offset > 0
                if resumed and not response.headers.get('Content-Range', '').startswith(f'bytes {offset}-'):
                    raise ValueError('Server returned a mismatched resume range')
                count = offset if resumed else 0
                report_at = time.monotonic()
                with part.open('ab' if resumed else 'wb') as stream:
                    while block := response.read(1024 * 1024):
                        count += len(block)
                        if count > entry['bytes']:
                            raise ValueError('Download exceeded expected size')
                        stream.write(block)
                        if time.monotonic() - report_at > 10:
                            print(f"{target.name}: {count}/{entry['bytes']}", flush=True)
                            report_at = time.monotonic()
            if not valid(part, entry):
                raise ValueError('Downloaded file did not match the pinned size and SHA-256')
            part.replace(target)
            return
        except (OSError, ValueError) as caught:
            error = caught
            print(f'{target.name}: attempt {attempt + 1} failed ({type(caught).__name__})', flush=True)
            if part.exists() and part.stat().st_size >= entry['bytes']:
                part.unlink()
    raise RuntimeError(f'Cannot prepare {target.name}: {error}')


def validate_manifest(manifest):
    ids = set()
    for pack in manifest['packs']:
        if pack['id'] in ids or not re.fullmatch(r'[a-z0-9_-]+', pack['id']):
            raise ValueError('Invalid or duplicate pack id')
        ids.add(pack['id'])
        if not re.fullmatch(r'[a-z0-9_-]+\.zip', pack['suggestedZipName']):
            raise ValueError('Invalid ZIP name')
        names = set()
        for entry in pack['files']:
            safe_path(ROOT, entry['localName'])
            if entry['localName'] in names or entry['bytes'] <= 0 or not re.fullmatch(r'[a-f0-9]{64}', entry['sha256']):
                raise ValueError('Invalid model file entry')
            names.add(entry['localName'])
            if not all(entry[key].startswith('https://') for key in ('officialUrl', 'domesticUrl')):
                raise ValueError('Model sources must use HTTPS')
        if sum(entry['bytes'] for entry in pack['files']) != pack['totalBytes']:
            raise ValueError('Pack total does not match file sizes')


def prepare(pack, output, cache, mirror, verify_only):
    directory = safe_path(cache, pack['id'])
    for entry in pack['files']:
        target = safe_path(directory, entry['localName'])
        if verify_only:
            if not valid(target, entry):
                raise ValueError(f"Missing or invalid: {pack['id']}/{entry['localName']}")
        else:
            download(entry, target, mirror)
    output.mkdir(parents=True, exist_ok=True)
    destination = safe_path(output, pack['suggestedZipName'])
    if not verify_only:
        temporary = destination.with_suffix('.zip.part')
        with zipfile.ZipFile(temporary, 'w', compression=zipfile.ZIP_STORED, allowZip64=True) as archive:
            for entry in pack['files']:
                archive.write(safe_path(directory, entry['localName']), entry['localName'])
            archive.writestr('pack-manifest.json', json.dumps(pack, ensure_ascii=False, indent=2))
            archive.writestr('SOURCES.txt', pack.get('licenseNote', '') + '\n' + '\n'.join(e['officialUrl'] for e in pack['files']))
            archive.write(ROOT / 'docs/third-party-paddleocr-license.txt', 'LICENSE-APACHE-2.0.txt')
            if pack.get('modelLicense') == 'FunASR-model-1.1':
                archive.write(ROOT / 'docs/third-party-funasr-model-license.txt', 'MODEL_LICENSE.txt')
        temporary.replace(destination)
    with zipfile.ZipFile(destination) as archive:
        for entry in pack['files']:
            digest = hashlib.sha256()
            with archive.open(entry['localName']) as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b''):
                    digest.update(block)
            if archive.getinfo(entry['localName']).file_size != entry['bytes'] or digest.hexdigest() != entry['sha256']:
                raise ValueError('ZIP entry integrity check failed')
    return {'id': pack['id'], 'path': str(destination), 'bytes': destination.stat().st_size, 'sha256': sha256(destination)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, default=ROOT / 'docs/offline-packs-manifest.json')
    parser.add_argument('--pack', action='append', default=[])
    parser.add_argument('--all', action='store_true', help='Prepare all non-legacy packs')
    parser.add_argument('--list', action='store_true')
    parser.add_argument('--mirror', action='store_true', help='Try the mirror first, still require pinned hashes')
    parser.add_argument('--verify-only', action='store_true')
    parser.add_argument('--cache', type=Path, default=ROOT / 'artifacts/intelligent-r9/model-cache')
    parser.add_argument('--output', type=Path, default=ROOT / 'artifacts/intelligent-r9/packages')
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding='utf-8'))
    validate_manifest(manifest)
    packs = {p['id']: p for p in manifest['packs']}
    if args.list:
        print(json.dumps([{'id': p['id'], 'bytes': p['totalBytes']} for p in packs.values()], indent=2))
        return
    selected = [p['id'] for p in packs.values() if not p.get('legacy')] if args.all else args.pack
    if not selected or any(key not in packs for key in selected):
        parser.error('Use --all or --pack with an id from --list')
    results = []
    for key in dict.fromkeys(selected):
        print(f'Preparing {key}', flush=True)
        results.append(prepare(packs[key], args.output, args.cache, args.mirror, args.verify_only))
        (args.output / 'checksums.json').write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(results, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError, KeyError, zipfile.BadZipFile) as error:
        print(f'ERROR: {error}', file=sys.stderr)
        sys.exit(1)
