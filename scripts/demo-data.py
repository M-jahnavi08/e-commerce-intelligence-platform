"""Create the optional sample catalog/accounts using the real API. No sales are fabricated."""
from pathlib import Path
import os
import json
import secrets
import urllib.request
import urllib.error

ROOT = Path(__file__).resolve().parents[1]
config = ROOT / '.env'
values = dict(line.split('=', 1) for line in config.read_text(encoding='utf-8').splitlines() if line and not line.startswith('#'))
if not values.get('DEMO_CUSTOMER_PASSWORD'):
    values['DEMO_CUSTOMER_EMAIL'] = 'customer@example.test'
    values['DEMO_CUSTOMER_PASSWORD'] = secrets.token_urlsafe(18)
    with config.open('a', encoding='utf-8') as file:
        file.write(f"DEMO_CUSTOMER_EMAIL={values['DEMO_CUSTOMER_EMAIL']}\nDEMO_CUSTOMER_PASSWORD={values['DEMO_CUSTOMER_PASSWORD']}\n")

def request(method, path, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(os.environ.get('COMMERCE_BASE_URL', 'http://127.0.0.1:8080').rstrip('/') + '/api' + path, method=method,
                                 data=json.dumps(body).encode() if body is not None else None, headers=headers)
    with urllib.request.urlopen(req, timeout=25) as response:
        return json.load(response)

admin = request('POST', '/auth/login', {'email': values['ADMIN_EMAIL'], 'password': values['ADMIN_PASSWORD']})['token']
category_ids = {c['name']: c['id'] for c in request('GET', '/categories')}
for name in ['Electronics', 'Workspace', 'Accessories', 'Home', 'Fashion', 'Beauty']:
    if name not in category_ids:
        category_ids[name] = request('POST', '/admin/categories', {'name': name}, admin)['id']
catalog = [
    ('HEADPHONES', 'Studio wireless headphones', 'Electronics', 6999.00, 24, 'Over-ear wireless headphones with balanced audio and a comfortable padded headband.'),
    ('EARBUDS', 'Everyday wireless earbuds', 'Electronics', 2499.00, 36, 'Compact wireless earbuds with a charging case for daily listening.'),
    ('SPEAKER', 'Portable Bluetooth speaker', 'Electronics', 3499.00, 18, 'Portable wireless audio speaker with a rechargeable battery.'),
    ('KEYBOARD', 'Compact mechanical keyboard', 'Workspace', 4999.00, 20, 'Compact desktop keyboard with tactile switches and a detachable cable.'),
    ('LAMP', 'Adjustable desk lamp', 'Workspace', 1499.00, 15, 'Adjustable task lighting for a focused, comfortable workspace.'),
    ('STAND', 'Aluminium laptop stand', 'Workspace', 1799.00, 30, 'Elevated laptop stand with an open design for desktop organisation.'),
    ('BAG', 'Daily commuter backpack', 'Accessories', 2499.00, 16, 'Practical backpack with a padded laptop compartment and everyday storage.'),
    ('BOTTLE', 'Insulated water bottle', 'Accessories', 899.00, 42, 'Reusable stainless steel water bottle for commuting and daily use.'),
    ('NOTEBOOK', 'Hardcover daily notebook', 'Accessories', 499.00, 50, 'Durable hardcover notebook for notes, ideas and daily plans.'),
    ('MUG', 'Stoneware coffee mug', 'Home', 599.00, 28, 'Simple stoneware mug for coffee, tea and slow mornings.'),
    ('THROW', 'Woven cotton throw', 'Home', 1999.00, 12, 'Soft woven cotton throw for a reading chair or sofa.'),
    ('TRAY', 'Oak organiser tray', 'Home', 999.00, 22, 'Compact organiser tray for keys, stationery and everyday essentials.'),
]
catalog += [
    ('WATCH', 'Everyday analogue watch', 'Accessories', 3299, 18, 'A brushed steel case, readable dial and soft silicone strap for everyday wear.'),
    ('SHIRT', 'Essential cotton overshirt', 'Fashion', 1899, 24, 'A relaxed unisex cotton overshirt with a clean collar and two chest pockets. Size M.'),
    ('SNEAKERS', 'City canvas sneakers', 'Fashion', 2499, 20, 'Low-top canvas sneakers with a cushioned insole and flexible rubber sole. Size EU 40.'),
    ('SKINCARE', 'Daily cleansing duo', 'Beauty', 1299, 32, 'A fragrance-free face wash and lightweight moisturiser set. Two 100 ml bottles.'),
    ('SUNGLASSES', 'Classic round sunglasses', 'Accessories', 1499, 25, 'Lightweight round frames with tinted lenses and a protective soft sleeve.'),
    ('POUCH', 'Everyday travel pouch', 'Accessories', 799, 30, 'A compact zip pouch for cables, toiletries and small daily essentials.'),
]
existing = {p['sku']: p for p in request('GET', '/admin/inventory', token=admin)}
for sku, product in existing.items():
    if sku.startswith('VERIFY-') and product['active']:
        product['active'] = False
        request('PUT', '/admin/products/' + product['id'], product, admin)
for category in request('GET', '/admin/categories', token=admin):
    if category['name'].startswith('Verification ') and category['active']:
        request('PUT', '/admin/categories/' + category['id'], {'name': category['name'], 'active': False}, admin)
created = 0
for sku, name, category, price, stock, description in catalog:
    sku = 'DEMO-' + sku
    if sku not in existing:
        request('POST', '/admin/products', {'sku': sku, 'name': name, 'description': description,
                'categoryId': category_ids[category], 'price': price, 'stock': stock}, admin)
        created += 1
    else:
        product = existing[sku]
        product.update(name=name, description=description, categoryId=category_ids[category], price=price, active=True)
        request('PUT', '/admin/products/' + product['id'], product, admin)
credentials = {'email': values['DEMO_CUSTOMER_EMAIL'], 'password': values['DEMO_CUSTOMER_PASSWORD']}
try:
    request('POST', '/auth/register', credentials)
except urllib.error.HTTPError as error:
    if error.code != 409:
        raise
request('POST', '/auth/login', credentials)
print(f'Demo catalog ready: {created} new products. Admin and customer sign-in verified. Credentials are in .env.')
