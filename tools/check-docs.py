"""Validate repository Markdown without third-party packages."""
from pathlib import Path
from urllib.parse import unquote, urlsplit
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
MACHINE_PATH = re.compile(r"(?<![\w])[A-Za-z]:[\\/]|/(?:Users|home|Volumes)/")
LINK = re.compile(r"\[[^\]\n]+\]\((<[^>]+>|[^\s]+?)(?:\s+\"[^\"]*\")?\)")


def main():
    listing = subprocess.check_output(
        ["git", "-c", "core.quotepath=false", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=ROOT,
    ).decode("utf-8").split("\0")
    documents = sorted({ROOT / name for name in listing if name.endswith(".md") and (ROOT / name).is_file()})
    errors = []
    for path in documents:
        relative = path.relative_to(ROOT).as_posix()
        if path.parent == ROOT / "docs" and path.name != "README.md":
            if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*\.md", path.name):
                errors.append(f"{relative}: use an unnumbered lower-case topic filename")
        for number, line in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
            if MACHINE_PATH.search(line):
                errors.append(f"{relative}:{number}: machine-specific path")
            for match in LINK.finditer(line):
                link = match.group(1).strip("<>")
                parsed = urlsplit(link)
                if parsed.scheme or parsed.netloc or not parsed.path:
                    continue
                target = (path.parent / unquote(parsed.path)).resolve()
                if not target.is_relative_to(ROOT):
                    errors.append(f"{relative}:{number}: link escapes the repository: {link}")
                elif not target.exists():
                    errors.append(f"{relative}:{number}: missing link target: {link}")
                else:
                    current = ROOT
                    for part in target.relative_to(ROOT).parts:
                        if part not in {child.name for child in current.iterdir()}:
                            errors.append(f"{relative}:{number}: link casing differs: {link}")
                            break
                        current = current / part
    if errors:
        print("\n".join(errors))
        return 1
    print(f"Checked {len(documents)} Markdown documents: paths, filenames and relative links are valid.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
