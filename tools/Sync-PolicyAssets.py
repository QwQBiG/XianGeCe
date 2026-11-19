"""Bundle readable policy text from the reviewed UTF-8 documents."""
import argparse
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
DOCUMENTS = {
    'license.txt': 'docs/software-and-artwork.md',
    'privacy.txt': 'docs/privacy-policy.md',
    'third-party.txt': 'THIRD_PARTY_NOTICES.md',
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    for name, source in DOCUMENTS.items():
        text = (ROOT / source).read_text(encoding='utf-8-sig')
        # Language links navigate the repository and have no target in the offline reader.
        switch = f'**简体中文** · [English]({Path(source).stem}.en.md)\n\n'
        text = text.replace(switch, '', 1)
        text = re.sub(r'^#+\s*', '', text, flags=re.MULTILINE)
        text = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', r'\1', text)
        text = text.replace('`', '').replace('**', '')
        text = re.sub(r'^\|[ -|]+\|\s*\n', '', text, flags=re.MULTILINE)
        text = re.sub(r'^\| (.+) \|$', lambda m: m[1].replace(' | ', ' · '), text, flags=re.MULTILINE)
        if name == 'license.txt':
            text += '\n' + (ROOT / 'LICENSE').read_text(encoding='utf-8')
        target = ROOT / 'app/src/main/assets/policies' / name
        if args.check:
            assert target.read_text(encoding='utf-8') == text, target
            assert b'\r\n' not in target.read_bytes(), f'{target}: expected LF line endings'
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            with target.open('w', encoding='utf-8', newline='\n') as output:
                output.write(text)
        print(target.relative_to(ROOT))


if __name__ == '__main__':
    main()
