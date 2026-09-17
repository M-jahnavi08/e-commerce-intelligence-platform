"""Run the existing packaged application locally without Docker. Ctrl+C stops it."""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / 'work' / 'local'
STATE.mkdir(parents=True, exist_ok=True)
ENV = dict(os.environ)
config = ROOT / '.env'
if not config.exists():
    subprocess.run([sys.executable, str(ROOT / 'scripts/configure-local.py')], check=True)
for line in config.read_text(encoding='utf-8').splitlines():
    if line and not line.startswith('#') and '=' in line:
        key, value = line.split('=', 1)
        ENV[key] = value

def find_program(name, candidates):
    explicit = ENV.get(name.upper() + '_BIN')
    for item in [explicit, shutil.which(name), *map(str, candidates)]:
        if item and Path(item).is_file():
            return str(item)
    raise SystemExit(f'{name} was not found; set {name.upper()}_BIN to its executable.')

java = find_program('java', list((ROOT/'work/tools').glob('jdk-21*/bin/java.exe')) + list((ROOT/'work/tools').glob('jdk-21*/bin/java')))
# Prefer the downloaded Java 21 over a system Java 17.
local_java = list((ROOT/'work/tools').glob('jdk-21*/bin/java.exe'))
if local_java and not ENV.get('JAVA_BIN'):
    java = str(local_java[0])
pg_candidates = list((ROOT/'work/tools').glob('postgres*/bin/pg_ctl.exe'))
pg_candidates += list(Path(os.environ.get('TEMP', '/tmp')).glob('embedded-pg/PG-*/bin/pg_ctl.exe'))
pgctl = Path(find_program('pg_ctl', pg_candidates))
pgbin = pgctl.parent
extension = '.exe' if os.name == 'nt' else ''
flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
ENV.update(DATABASE_URL='jdbc:postgresql://127.0.0.1:55432/commerce', DATABASE_USER='commerce',
           CACHE_ENABLED='false', EVENTS_ENABLED='false', ML_URL='http://127.0.0.1:8000',
           CORS_ORIGIN='http://localhost:5173,http://127.0.0.1:5173', SERVER_ADDRESS='127.0.0.1',
           PGPASSWORD=ENV['DATABASE_PASSWORD'])
children = []
logs = []
database_started = False

def run(command, **kwargs):
    return subprocess.run(list(map(str, command)), env=ENV, creationflags=flags, cwd=STATE, **kwargs)

def start(name, command, cwd):
    log = (STATE / f'{name}.log').open('w', encoding='utf-8')
    logs.append(log)
    child = subprocess.Popen(list(map(str, command)), cwd=cwd, env=ENV, stdout=log, stderr=subprocess.STDOUT, creationflags=flags)
    children.append(child)
    return child

def wait(url, process, seconds=180):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f'Service exited. Inspect logs in {STATE}')
        try:
            with urllib.request.urlopen(url, timeout=2) as response:
                if response.status == 200:
                    return
        except OSError:
            pass
        time.sleep(1)
    raise RuntimeError(f'Timed out waiting for {url}; inspect {STATE}')

try:
    python = ROOT / 'ml-service/.venv' / ('Scripts/python.exe' if os.name == 'nt' else 'bin/python')
    required = [python, ROOT/'backend/target/intelligence-0.1.0.jar', ROOT/'frontend/dist/index.html']
    for artifact in required:
        if not artifact.is_file():
            raise RuntimeError(f'Required local artifact is missing: {artifact}')
    data = Path('postgres-data')
    if not (STATE / data / 'PG_VERSION').exists():
        (STATE / data).mkdir(parents=True, exist_ok=True)
        password_file = STATE / 'init-password'
        password_file.write_text(ENV['DATABASE_PASSWORD'], encoding='utf-8')
        try:
            with (STATE/'initdb.log').open('w') as log:
                run([pgbin/('initdb'+extension), '-D', data, '-U', 'commerce', '--auth=scram-sha-256', '--encoding=UTF8', '--pwfile', password_file], stdout=log, stderr=subprocess.STDOUT, check=True)
        finally:
            password_file.unlink(missing_ok=True)
    if run([pgctl, '-D', data, 'status'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
        postgres = start('postgres', [pgbin/('postgres'+extension), '-D', data, '-h', '127.0.0.1', '-p', '55432'], STATE)
        children.remove(postgres)
        database_started = True
        for attempt in range(30):
            if postgres.poll() is not None:
                raise RuntimeError('PostgreSQL exited; inspect work/local/postgres.log')
            if run([python, ROOT/'scripts/local-database.py'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError('PostgreSQL startup timed out')
    run([python, ROOT/'scripts/local-database.py'], check=True)
    print('PostgreSQL ready on 127.0.0.1:55432', flush=True)
    ml = start('ml', [python, '-m', 'uvicorn', 'app.main:app', '--host', '127.0.0.1', '--port', '8000'], ROOT/'ml-service')
    wait('http://127.0.0.1:8000/health', ml)
    backend = start('backend', [java, '-Djava.net.preferIPv4Stack=true', '-Djdk.net.unixdomain.tmpdir=' + str(STATE), '-jar', ROOT/'backend/target/intelligence-0.1.0.jar'], ROOT/'backend')
    wait('http://127.0.0.1:8080/actuator/health/readiness', backend)
    frontend = start('frontend', [sys.executable, ROOT/'scripts/serve-frontend.py'], ROOT)
    wait('http://127.0.0.1:5173', frontend)
    print('Application ready: http://127.0.0.1:5173', flush=True)
    print('Admin credentials are in .env. Keep this process running; Ctrl+C stops the demo.', flush=True)
    while all(p.poll() is None for p in children):
        time.sleep(2)
except KeyboardInterrupt:
    pass
finally:
    for process in reversed(children):
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
    for log in logs:
        log.close()
    if database_started:
        run([pgctl, '-D', 'postgres-data', '-m', 'fast', '-w', 'stop'])
