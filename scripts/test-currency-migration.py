"""Verify V1 -> V2 on isolated fixtures, rolled back inside the test database."""
from pathlib import Path
import json
import uuid
from decimal import Decimal
import psycopg

ROOT = Path(__file__).resolve().parents[1]
env = dict(line.split('=', 1) for line in (ROOT / '.env').read_text(encoding='utf-8').splitlines()
           if line and not line.startswith('#') and '=' in line)
with psycopg.connect(host='127.0.0.1', port=55432, user='commerce', password=env['DATABASE_PASSWORD'],
                     dbname='commerce_verification', connect_timeout=5) as db:
    try:
        schema = 'currency_' + uuid.uuid4().hex
        db.execute(psycopg.sql.SQL('CREATE SCHEMA {}').format(psycopg.sql.Identifier(schema)))
        db.execute(psycopg.sql.SQL('SET LOCAL search_path TO {}').format(psycopg.sql.Identifier(schema)))
        db.execute((ROOT / 'backend/src/main/resources/db/migration/V1__commerce_schema.sql').read_text(encoding='utf-8'))
        category, user, demo, other, order, event = [uuid.uuid4() for _ in range(6)]
        db.execute('INSERT INTO categories VALUES (%s, %s)', (category, 'Migration fixture'))
        db.execute("INSERT INTO users(id,email,password_hash,role) VALUES (%s,'migration@example.test','unused','CUSTOMER')", (user,))
        for product, sku, price in [(demo, 'DEMO-HEADPHONES', 129), (other, 'CUSTOM', Decimal('12.50'))]:
            db.execute('INSERT INTO products(id,sku,name,description,category_id,price) VALUES (%s,%s,%s,%s,%s,%s)',
                       (product, sku, sku, 'Fixture', category, price))
            db.execute('INSERT INTO inventory(product_id,quantity) VALUES (%s,5)', (product,))
        db.execute("INSERT INTO orders VALUES (%s,%s,%s,'PAID',154,now())", (order, user, uuid.uuid4()))
        for product, quantity, price in [(demo, 1, 129), (other, 2, Decimal('12.50'))]:
            db.execute('INSERT INTO order_items VALUES (%s,%s,%s,%s,%s,%s)',
                       (uuid.uuid4(), order, product, 'Fixture', quantity, price))
            db.execute('INSERT INTO sales_facts VALUES (%s,%s,%s,%s,now())', (order, product, quantity, price * quantity))
        db.execute("INSERT INTO outbox_events(id,aggregate_id,event_type,payload,created_at) VALUES (%s,%s,'OrderPaid',%s,now())",
                   (event, order, json.dumps({'eventId': str(event), 'orderId': str(order), 'items': []})))
        db.execute((ROOT / 'backend/src/main/resources/db/migration/V2__inr_and_product_images.sql').read_text(encoding='utf-8'))
        assert db.execute('SELECT price,image_url FROM products WHERE id=%s', (demo,)).fetchone() == (Decimal('6999'), '/images/products/headphones.svg')
        assert db.execute('SELECT price FROM products WHERE id=%s', (other,)).fetchone()[0] == Decimal('1000')
        assert db.execute('SELECT total FROM orders').fetchone()[0] == Decimal('8999')
        assert db.execute('SELECT sum(revenue) FROM sales_facts').fetchone()[0] == Decimal('8999')
        assert db.execute('SELECT sum(quantity) FROM inventory').fetchone()[0] == 10
        payload = json.loads(db.execute('SELECT payload FROM outbox_events').fetchone()[0])
        assert payload['currency'] == 'INR'
        assert sum(Decimal(str(item['unitPrice'])) * item['quantity'] for item in payload['items']) == Decimal('8999')
        assert all(item['imageUrl'].startswith('/images/products/') for item in payload['items'])
        print('PASS: INR migration preserves inventory and reconciles products, historical orders, projections, outbox and images')
    finally:
        db.rollback()
