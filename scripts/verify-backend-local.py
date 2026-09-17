"""Run Java integration tests against only commerce_verification, without Docker.

Uses the workspace Java 21, Eclipse compiler and resolved Maven dependencies.
Requires the running local PostgreSQL instance and scripts/requirements-local.txt.
The standard portable build remains `mvn verify` in backend/.
"""
from pathlib import Path
import os
import subprocess
import psycopg

ROOT = Path(__file__).resolve().parents[1]
STAGE = ROOT / 'work/ecj-build'


def main():
    env = dict(os.environ)
    env.update(line.split('=', 1) for line in (ROOT / '.env').read_text().splitlines()
               if line and not line.startswith('#') and '=' in line)
    java = next((ROOT / 'work/tools').glob('jdk-21*/bin/java.exe'))
    compiler = ROOT / 'work/tools/ecj-3.40.0.jar'
    console = ROOT / 'work/tools/junit-console.jar'
    if not compiler.exists() or not console.exists():
        raise SystemExit('Local compiler/runner absent. Use backend/mvn verify with Java 21 and Docker.')
    jars = list((STAGE / 'lib').glob('*.jar'))
    if not jars:
        raise SystemExit('Resolved application dependencies are missing from work/ecj-build/lib.')
    extras = []
    for folder in ['org/springframework/boot/spring-boot-test', 'org/springframework/boot/spring-boot-test-autoconfigure',
                   'org/springframework/spring-test', 'org/springframework/security/spring-security-test',
                   'org/assertj', 'org/mockito', 'net/bytebuddy', 'org/objenesis', 'com/jayway/jsonpath',
                   'net/minidev', 'org/hamcrest', 'org/skyscreamer', 'com/vaadin/external/google']:
        extras.extend((ROOT / 'work/m2' / folder).rglob('*.jar'))

    def invoke(name, args):
        argfile = STAGE / (name + '.args')
        argfile.write_text('\n'.join('"' + str(arg).replace('\\', '/') + '"' for arg in args))
        subprocess.run([str(java), '@' + str(argfile)], cwd=ROOT, env=env, check=True)

    classpath = os.pathsep.join(map(str, [STAGE / 'classes', ROOT / 'backend/src/main/resources', console, *jars, *extras]))
    invoke('compile-main', ['-jar', compiler, '-21', '-parameters', '-proc:none', '-nowarn', '-classpath',
           classpath, '-d', STAGE / 'classes', *(ROOT / 'backend/src/main/java').rglob('*.java')])
    test_root = ROOT / 'backend/src/test/java/com/commerce/intelligence'
    invoke('compile-tests', ['-jar', compiler, '-21', '-parameters', '-proc:none', '-nowarn', '-classpath',
           classpath, '-d', STAGE / 'test-classes', *[test_root / name for name in
           ['CommerceIntegrationContract.java', 'ExternalPostgresIntegrationTest.java', 'OutboxPublisherTest.java', 'CategoryCacheTest.java', 'KafkaBeanConfigurationTest.java', 'CorsRegistrationTest.java']]])
    database_port = int(os.environ.get('TEST_DATABASE_PORT', '55432'))
    with psycopg.connect(host='127.0.0.1', port=database_port, user='commerce', password=env['DATABASE_PASSWORD'],
                         dbname='postgres', autocommit=True, connect_timeout=5) as db:
        if not db.execute("select 1 from pg_database where datname='commerce_verification'").fetchone():
            db.execute('CREATE DATABASE commerce_verification')
    env.update(TEST_DATABASE_URL=f'jdbc:postgresql://127.0.0.1:{database_port}/commerce_verification',
               TEST_DATABASE_USER='commerce', TEST_DATABASE_PASSWORD=env['DATABASE_PASSWORD'])
    mockito = next((ROOT / 'work/m2/org/mockito/mockito-core').rglob('*.jar'))
    invoke('run-tests', ['-Djava.net.preferIPv4Stack=true', '-Djdk.net.unixdomain.tmpdir=' + str(ROOT / 'work/local'),
           '-javaagent:' + str(mockito), '-cp', str(STAGE / 'test-classes') + os.pathsep + classpath,
           'org.junit.platform.console.ConsoleLauncher', 'execute', '--select-class',
           'com.commerce.intelligence.ExternalPostgresIntegrationTest', '--select-class',
           'com.commerce.intelligence.OutboxPublisherTest', '--select-class', 'com.commerce.intelligence.CategoryCacheTest', '--select-class', 'com.commerce.intelligence.KafkaBeanConfigurationTest', '--select-class', 'com.commerce.intelligence.CorsRegistrationTest', '--details=summary', '--reports-dir', STAGE / 'reports'])


if __name__ == '__main__':
    main()
