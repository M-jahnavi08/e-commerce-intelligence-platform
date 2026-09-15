"""Generate local-only secrets without printing them or overwriting existing configuration."""
from pathlib import Path
import secrets
root = Path(__file__).resolve().parents[1]
path = root / '.env'
if path.exists():
    raise SystemExit('.env already exists; left unchanged.')
values = {'DATABASE_PASSWORD': secrets.token_urlsafe(32), 'JWT_SECRET': secrets.token_urlsafe(48),
          'ML_SERVICE_TOKEN': secrets.token_urlsafe(48), 'ADMIN_EMAIL': 'admin@example.test',
          'ADMIN_PASSWORD': secrets.token_urlsafe(24)}
path.write_text(''.join(f'{key}={value}\n' for key, value in values.items()), encoding='utf-8')
print('Created .env. Read the local ADMIN_PASSWORD there to sign in. Do not commit it.')
