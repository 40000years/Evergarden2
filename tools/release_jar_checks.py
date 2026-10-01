"""Reject Eclipse compiler error stubs accidentally reused by incremental builds."""
from pathlib import Path
import zipfile


def check_jar(path):
    with zipfile.ZipFile(path) as jar:
        broken = [name for name in jar.namelist() if name.endswith('.class')
                  and b'Unresolved compilation problem' in jar.read(name)]
    assert not broken, f'Compiler error stubs in {path}: {broken}. Rebuild in an isolated checkout.'


if __name__ == '__main__':
    import sys
    for path in sys.argv[1:]:
        check_jar(Path(path))
        print(f'PASS: no compiler error stubs in {path}')
