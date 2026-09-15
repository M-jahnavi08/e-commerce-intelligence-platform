"""Package ECJ-compiled sources with the existing Maven-resolved runtime libraries.

Writes a candidate under work/ecj-build. Does not replace the running application.
Run verify-backend-local.py first; Maven remains the standard CI/Docker builder.
"""
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
classes = ROOT / 'work/ecj-build/classes'
assert (classes / 'com/commerce/intelligence/CommerceApplication.class').is_file(), 'Compile backend first'
target = ROOT / 'work/ecj-build/intelligence-0.1.0.jar'
with zipfile.ZipFile(ROOT / 'backend/target/intelligence-0.1.0.jar') as old, zipfile.ZipFile(target, 'w') as new:
    for entry in old.infolist():
        if not entry.filename.startswith('BOOT-INF/classes/'):
            new.writestr(entry, old.read(entry.filename))
    directories = {'BOOT-INF/classes/'}
    for directory in [classes, ROOT / 'backend/src/main/resources']:
        for path in directory.rglob('*'):
            if path.is_file():
                name = 'BOOT-INF/classes/' + path.relative_to(directory).as_posix()
                new.write(path, name)
                pieces = name.split('/')
                directories.update('/'.join(pieces[:i]) + '/' for i in range(2, len(pieces)))
    for name in sorted(directories):
        new.writestr(name, b'')
with zipfile.ZipFile(target) as archive:
    assert archive.testzip() is None
    assert 'BOOT-INF/classes/com/commerce/intelligence/CommerceApplication.class' in archive.namelist()
print('Verified packaged candidate:', target)
