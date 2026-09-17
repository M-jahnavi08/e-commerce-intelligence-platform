"""Exercise real services. Creates uniquely named test accounts/products/orders.

python scripts/verify-stack.py --base-url http://127.0.0.1:5173
Add --compose to also require Redis, Kafka delivery and durable projections.
Never truncates data. Credentials come from environment or the local .env file.
"""
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from decimal import Decimal

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://localhost:8088')
    parser.add_argument('--compose', action='store_true')
    parser.add_argument('--resilience', action='store_true', help='Temporarily stop/restart Redis and Kafka in the disposable Compose test stack')
    args = parser.parse_args()
    if args.resilience and not args.compose:
        parser.error('--resilience requires --compose')
    env = {}
    if (ROOT / '.env').exists():
        env.update(line.split('=', 1) for line in (ROOT / '.env').read_text().splitlines()
                   if line and not line.startswith('#') and '=' in line)
    env.update(os.environ)

    def api(method, path, body=None, token=None, expected=200, key=None):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        if key:
            headers['Idempotency-Key'] = key
        request = urllib.request.Request(args.base_url.rstrip('/') + '/api' + path,
                    data=json.dumps(body).encode() if body is not None else None,
                    headers=headers, method=method)
        try:
            response = urllib.request.urlopen(request, timeout=25)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read()
            assert response.status == expected, f'{method} {path}: expected {expected}, received {response.status}'
            return json.loads(raw) if raw else None

    def compose(*command, input=None):
        return subprocess.run(['docker', 'compose', 'exec', '-T', *command], cwd=ROOT,
                              env=env, input=input, text=True, capture_output=True,
                              check=True, timeout=40).stdout.strip()

    def sql(query):
        return compose('postgres', 'psql', '-U', 'commerce', '-d', 'commerce', '-Atc', query)

    admin = api('POST', '/auth/login', {'email': env['ADMIN_EMAIL'], 'password': env['ADMIN_PASSWORD']})['token']
    suffix = uuid.uuid4().hex[:12]
    customer = api('POST', '/auth/register', {'email': f'verify-{suffix}@example.test',
                    'password': secrets.token_urlsafe(24)}, expected=201)['token']
    api('GET', '/admin/analytics', expected=401)
    api('GET', '/admin/analytics', token=customer, expected=403)
    api('GET', '/cart', token='invalid.token.value', expected=401)
    before = api('GET', '/admin/analytics', token=admin)['summary']
    api('GET', '/categories')  # Warm cache before invalidation.
    category = api('POST', '/admin/categories', {'name': 'Verification ' + suffix}, admin, 201)
    assert category['id'] in {c['id'] for c in api('GET', '/categories')}
    product = api('POST', '/admin/products', {'sku': 'VERIFY-' + suffix,
        'name': 'Verification headphones ' + suffix, 'description': 'Wireless audio verification product',
        'categoryId': category['id'], 'price': 12.50, 'stock': 5}, admin, 201)
    pid = product['id']
    result = api('GET', '/products?q=' + suffix + '&category=' + category['id'] + '&max=13')
    assert result['totalElements'] == 1
    assert api('GET', '/products?q=' + suffix + '&max=1')['totalElements'] == 0
    api('GET', '/products?min=-1', expected=400)
    api('POST', '/cart/' + pid, token=customer)
    api('POST', '/cart/' + pid, token=customer)
    assert api('GET', '/cart', token=customer)[0]['quantity'] == 2
    declined = api('POST', '/checkout', {'simulateDecline': True}, customer, key=str(uuid.uuid4()))
    assert declined['status'] == 'PAYMENT_FAILED'
    assert api('GET', '/products/' + pid)['stock'] == 5
    assert api('GET', '/cart', token=customer)[0]['quantity'] == 2
    key = str(uuid.uuid4())
    paid = api('POST', '/checkout', {'simulateDecline': False}, customer, key=key)
    assert paid['status'] == 'PAID' and Decimal(str(paid['total'])) == Decimal('25.00')
    replayed = api('POST', '/checkout', {'simulateDecline': False}, customer, key=key)
    assert replayed == paid, {k: (paid[k], replayed[k]) for k in paid if paid[k] != replayed[k]}
    api('POST', '/checkout', {'simulateDecline': True}, customer, expected=409, key=key)
    assert api('GET', '/products/' + pid)['stock'] == 3
    assert api('GET', '/cart', token=customer) == []
    assert len(api('GET', '/orders', token=customer)) == 2
    assert paid['id'] not in {o['id'] for o in api('GET', '/orders', token=admin)}
    after = api('GET', '/admin/analytics', token=admin)['summary']
    assert after['orders'] == before['orders'] + 1
    assert Decimal(str(after['revenue'])) - Decimal(str(before['revenue'])) == Decimal('25.00')
    api('POST', '/admin/inventory/' + pid + '/adjustments', {'delta': -4, 'reason': 'Verification invalid adjustment'}, admin, 409)
    adjusted = api('POST', '/admin/inventory/' + pid + '/adjustments', {'delta': 2, 'reason': 'Verification restock'}, admin)
    assert adjusted['stock'] == 5
    recommendations = api('GET', '/products/' + pid + '/recommendations')
    assert recommendations['status'] in ('ready', 'insufficient_data')
    assert api('GET', '/admin/intelligence/forecast/' + pid, token=admin)['status'] == 'insufficient_data'
    assert api('GET', '/admin/intelligence/anomalies', token=admin)['status'] in ('ready', 'insufficient_data')
    print('PASS: authorization, catalog/filtering, declined/paid checkout, idempotency, ownership, inventory, analytics, ML')

    if args.compose:
        api('GET', '/categories')
        assert compose('redis', 'redis-cli', 'EXISTS', 'catalog:categories:v2') == '1'
        assert 0 < int(compose('redis', 'redis-cli', 'TTL', 'catalog:categories:v2')) <= 60
        order_id = str(uuid.UUID(paid['id']))
        query = f"select count(*) from sales_facts where order_id='{order_id}' and quantity=2 and revenue=25"
        deadline = time.monotonic() + 90
        while sql(query) != '1':
            assert time.monotonic() < deadline, 'Kafka projection did not arrive within 90 seconds'
            time.sleep(2)
        assert sql(f"select count(*) from outbox_events where aggregate_id='{order_id}' and published_at is not null") == '1'
        payload = sql(f"select payload from outbox_events where aggregate_id='{order_id}'")
        event_id = str(uuid.UUID(json.loads(payload)['eventId']))
        def committed():
            rows = compose('kafka', '/opt/kafka/bin/kafka-consumer-groups.sh', '--bootstrap-server',
                           'localhost:9092', '--group', 'commerce-projections', '--describe')
            return sum(int(parts[3]) for row in rows.splitlines() if len(parts := row.split()) > 5
                       and parts[0] == 'commerce-projections' and parts[1] == 'commerce.orders' and parts[3].isdigit())
        before_commit = committed()
        compose('kafka', '/opt/kafka/bin/kafka-console-producer.sh', '--bootstrap-server', 'localhost:9092',
                '--topic', 'commerce.orders', input=payload + '\n')
        deadline = time.monotonic() + 60
        while committed() <= before_commit:
            assert time.monotonic() < deadline, 'Duplicate event was not consumed'
            time.sleep(2)
        assert sql(query) == '1'
        assert sql(f"select count(*) from processed_events where id='{event_id}'") == '1'
        poison = json.dumps({'verificationPoison': suffix})
        compose('kafka', '/opt/kafka/bin/kafka-console-producer.sh', '--bootstrap-server', 'localhost:9092',
                '--topic', 'commerce.orders', input=poison + '\n')
        dlt = subprocess.run(['docker', 'compose', 'exec', '-T', 'kafka', '/opt/kafka/bin/kafka-console-consumer.sh',
            '--bootstrap-server', 'localhost:9092', '--topic', 'commerce.orders.DLT', '--from-beginning',
            '--timeout-ms', '20000'], cwd=ROOT, env=env, capture_output=True, text=True, timeout=40)
        assert suffix in dlt.stdout, 'Malformed event did not reach the dead-letter topic'
        print('PASS: Redis cache/TTL, Kafka delivery, outbox acknowledgement, deduplicated projection, dead-letter delivery')
    if args.resilience:
        def lifecycle(*command):
            subprocess.run(['docker', 'compose', *command], cwd=ROOT, env=env, check=True, timeout=180)

        try:
            lifecycle('stop', 'redis')
            assert category['id'] in {c['id'] for c in api('GET', '/categories')}
            assert compose('backend', 'wget', '-qO-', 'http://localhost:8080/actuator/health/readiness').find('UP') >= 0
            print('PASS: categories fall back to PostgreSQL during Redis outage')
        finally:
            lifecycle('up', '-d', '--wait', '--wait-timeout', '120', 'redis')
        api('GET', '/categories')
        assert compose('redis', 'redis-cli', 'EXISTS', 'catalog:categories:v2') == '1'

        try:
            lifecycle('stop', 'kafka')
            api('POST', '/cart/' + pid, token=customer)
            outage_order = api('POST', '/checkout', {'simulateDecline': False}, customer, key=str(uuid.uuid4()))
            outage_id = str(uuid.UUID(outage_order['id']))
            assert outage_order['status'] == 'PAID'
            assert sql(f"select count(*) from outbox_events where aggregate_id='{outage_id}' and published_at is null") == '1'
            print('PASS: checkout commits a durable pending outbox event while Kafka is offline')
        finally:
            lifecycle('up', '-d', '--wait', '--wait-timeout', '120', 'kafka')
        deadline = time.monotonic() + 90
        while sql(f"select count(*) from sales_facts where order_id='{outage_id}' and quantity=1 and revenue=12.50") != '1':
            assert time.monotonic() < deadline, 'Outbox did not recover after Kafka restart'
            time.sleep(2)
        assert sql(f"select count(*) from outbox_events where aggregate_id='{outage_id}' and published_at is not null") == '1'
        print('PASS: Kafka restart drains the pending outbox event into the sales projection')

    api('GET', '/admin/orders', token=customer, expected=403)
    assert paid['id'] in {o['id'] for o in api('GET', '/admin/orders', token=admin)['content']}
    history = api('GET', '/admin/inventory/' + pid + '/movements', token=admin)
    assert history['totalElements'] == (3 if args.resilience else 2)
    api('POST', '/cart/' + pid, token=customer)
    update = {k: product[k] for k in ('name', 'description', 'categoryId', 'price', 'active', 'version')}
    update['active'] = False
    api('PUT', '/admin/products/' + pid, update, customer, expected=403)
    api('PUT', '/admin/products/' + pid, update, admin)
    api('PUT', '/admin/products/' + pid, update, admin, expected=409)
    api('GET', '/products/' + pid, expected=404)
    assert api('GET', '/cart', token=customer)[0]['stock'] == 0
    api('PUT', '/cart/' + pid, {'quantity': 0}, customer)
    assert api('GET', '/cart', token=customer) == []
    api('PUT', '/admin/categories/' + category['id'], {'name': category['name'], 'active': False}, admin)
    print('PASS: admin order history, inventory audit, product archive, stale edit conflict, unavailable cart removal')
    print('Verification order:', paid['id'], '(simulated INR 25 payment; records retained, test product archived)')


if __name__ == '__main__':
    main()
