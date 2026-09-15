"""Create explicitly labelled interview catalog/accounts using the real API. No sales are fabricated."""
from pathlib import Path
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
    req = urllib.request.Request('http://127.0.0.1:8080/api' + path, method=method,
                                 data=json.dumps(body).encode() if body is not None else None, headers=headers)
    with urllib.request.urlopen(req, timeout=25) as response:
        return json.load(response)

admin = request('POST', '/auth/login', {'email': values['ADMIN_EMAIL'], 'password': values['ADMIN_PASSWORD']})['token']
category_ids = {c['name']: c['id'] for c in request('GET', '/categories')}
for name in ['Audio', 'Workspace', 'Everyday carry', 'Home']:
    if name not in category_ids:
        category_ids[name] = request('POST', '/admin/categories', {'name': name}, admin)['id']
catalog = [
    ('HEADPHONES', 'Studio wireless headphones', 'Audio', 6999.00, 24, 'Over-ear wireless headphones with balanced audio and a comfortable padded headband.'),
    ('EARBUDS', 'Everyday wireless earbuds', 'Audio', 2499.00, 36, 'Compact wireless earbuds with a charging case for daily listening.'),
    ('SPEAKER', 'Portable Bluetooth speaker', 'Audio', 3499.00, 18, 'Portable wireless audio speaker with a rechargeable battery.'),
    ('KEYBOARD', 'Compact mechanical keyboard', 'Workspace', 4999.00, 20, 'Compact desktop keyboard with tactile switches and a detachable cable.'),
    ('LAMP', 'Adjustable desk lamp', 'Workspace', 1499.00, 15, 'Adjustable task lighting for a focused, comfortable workspace.'),
    ('STAND', 'Aluminium laptop stand', 'Workspace', 1799.00, 30, 'Elevated laptop stand with an open design for desktop organisation.'),
    ('BAG', 'Daily commuter backpack', 'Everyday carry', 2499.00, 16, 'Practical backpack with a padded laptop compartment and everyday storage.'),
    ('BOTTLE', 'Insulated water bottle', 'Everyday carry', 899.00, 42, 'Reusable stainless steel water bottle for commuting and daily use.'),
    ('NOTEBOOK', 'Hardcover daily notebook', 'Everyday carry', 499.00, 50, 'Durable hardcover notebook for notes, ideas and daily plans.'),
    ('MUG', 'Stoneware coffee mug', 'Home', 599.00, 28, 'Simple stoneware mug for coffee, tea and slow mornings.'),
    ('THROW', 'Woven cotton throw', 'Home', 1999.00, 12, 'Soft woven cotton throw for a reading chair or sofa.'),
    ('TRAY', 'Oak organiser tray', 'Home', 999.00, 22, 'Compact organiser tray for keys, stationery and everyday essentials.'),
]
existing = {p['sku'] for p in request('GET', '/admin/inventory', token=admin)}
created = 0
for sku, name, category, price, stock, description in catalog:
    sku = 'DEMO-' + sku
    if sku not in existing:
        request('POST', '/admin/products', {'sku': sku, 'name': name, 'description': description + ' Interview demo catalog item.',
                'categoryId': category_ids[category], 'price': price, 'stock': stock}, admin)
        created += 1
credentials = {'email': values['DEMO_CUSTOMER_EMAIL'], 'password': values['DEMO_CUSTOMER_PASSWORD']}
try:
    request('POST', '/auth/register', credentials)
except urllib.error.HTTPError as error:
    if error.code != 409:
        raise
request('POST', '/auth/login', credentials)
print(f'Demo catalog ready: {created} new products. Admin and customer sign-in verified. Credentials are in .env.')
