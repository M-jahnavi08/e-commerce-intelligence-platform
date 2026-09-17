"""Run rebuilt services against an existing PostgreSQL database, without stopping Docker.

Requires packaged backend, frontend/dist, ml-service/.venv and a configured .env.
Uses ports 8081 (API), 8000 (ML), 5173 (frontend); Ctrl+C stops only these processes.
"""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / 'work/connected'
STATE.mkdir(parents=True, exist_ok=True)
env = dict(os.environ)
env.update(line.split('=',1) for line in (ROOT/'.env').read_text().splitlines() if line and not line.startswith('#') and '=' in line)
env.update(SERVER_ADDRESS='127.0.0.1', SERVER_PORT='8081', BACKEND_PORT='8081',
 DATABASE_URL=env.get('DATABASE_URL','jdbc:postgresql://127.0.0.1:5432/commerce'), DATABASE_USER=env.get('DATABASE_USER','commerce'),
 CACHE_ENABLED='false', EVENTS_ENABLED='false', ML_URL='http://127.0.0.1:8000', CORS_ORIGIN='http://localhost:5173,http://127.0.0.1:5173')
local_java = list((ROOT/'work/tools').glob('jdk-21*/bin/java.exe'))
java = env.get('JAVA_BIN') or (str(local_java[0]) if local_java else shutil.which('java'))
python = ROOT/'ml-service/.venv'/('Scripts/python.exe' if os.name=='nt' else 'bin/python')
children=[]
logs=[]

def start(name,args,cwd):
 log=(STATE/(name+'.log')).open('w',encoding='utf-8');logs.append(log)
 child=subprocess.Popen(list(map(str,args)),cwd=cwd,env=env,stdout=log,stderr=subprocess.STDOUT,
  creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
 children.append(child)
 return child

def wait(url,child):
 for _ in range(180):
  if child.poll() is not None: raise RuntimeError('Service exited; inspect work/connected logs')
  try:
   with urllib.request.urlopen(url,timeout=2) as response:
    if response.status==200:return
  except OSError: pass
  time.sleep(1)
 raise RuntimeError('Service startup timed out')

try:
 ml=start('ml',[python,'-m','uvicorn','app.main:app','--host','127.0.0.1','--port','8000'],ROOT/'ml-service')
 wait('http://127.0.0.1:8000/health',ml)
 backend=start('backend',[java,'-Djava.net.preferIPv4Stack=true','-Djdk.net.unixdomain.tmpdir='+str(STATE),'-jar',ROOT/'backend/target/intelligence-0.1.0.jar'],ROOT/'backend')
 wait('http://127.0.0.1:8081/actuator/health/readiness',backend)
 frontend=start('frontend',[sys.executable,ROOT/'scripts/serve-frontend.py'],ROOT)
 wait('http://127.0.0.1:5173',frontend)
 print('Updated application ready: http://127.0.0.1:5173',flush=True)
 while all(c.poll() is None for c in children):time.sleep(2)
except KeyboardInterrupt:pass
finally:
 for child in reversed(children):
  if child.poll() is None:
   child.terminate()
   try:child.wait(timeout=15)
   except subprocess.TimeoutExpired:child.kill()
 for log in logs:log.close()
