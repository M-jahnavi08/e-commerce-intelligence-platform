# Local setup

## Configuration

Run `python scripts/configure-local.py` from the repository root to generate independent random database, JWT, ML and admin secrets. It never overwrites `.env`. Existing configuration must supply all keys in `.env.example` with nonempty secret values. The example contains no usable credentials.

Compose loads root `.env` for interpolation. Spring Boot and Uvicorn do not automatically load it. For native development, load it into each service terminal, keeping values private.

Bash, for the locally generated configuration:

```sh
set -a
. ./.env
set +a
```

PowerShell:

```powershell
Get-Content .env | ForEach-Object {
  if ($_ -match '^([A-Z_]+)=(.*)$') {
    [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
  }
}
```

Do not run untrusted environment files as shell scripts. Never use `VITE_` variables for secrets: frontend build variables are public.

## Native development

Requirements: Java 21, Maven 3.9+, Node 22.12+ (CI uses Node 22), Python 3.13 and PostgreSQL. The complete Docker path in the README requires no host Java/Node installation.

From the root, provision only PostgreSQL with `docker compose up -d --wait postgres`, or use your own PostgreSQL database and matching credentials. In separate terminals, after loading root `.env` as above:

Backend:

```sh
cd backend
mvn spring-boot:run
```

Defaults connect to database `commerce` on localhost:5432 as user `commerce`. Override `DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD` for another database. Flyway creates/migrates the schema; Hibernate validates it. Redis/Kafka are disabled by default in native mode. The outbox still records paid orders. Use Compose for broker/cache verification; its Kafka listener is advertised for the container network, not host JVM access.

ML:

```sh
cd ml-service
python -m venv .venv
```

Activate with `. .venv/bin/activate` on Bash or `.venv\Scripts\Activate.ps1` on PowerShell, then:

```sh
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Frontend, in another terminal:

```sh
cd frontend
npm ci
npm run dev
```

Open the URL printed by Vite (normally http://localhost:5173). Vite proxies `/api` to backend port 8080. Backend `CORS_ORIGIN` accepts comma-separated exact origins. Local defaults allow localhost and 127.0.0.1 on ports 5173 and 8088; Compose uses the same local allowlist. Production must set only its deployed origin(s). Optional root formatting tools use a separate `npm ci`; they are not required to run the application.

## Existing restricted Windows workspace only

`start-demo.cmd`, `scripts/run-local.py`, the local ECJ build/package helpers and custom Node runners were introduced for a restricted development environment. They depend on ignored downloads under `work/`, a populated Python venv and prebuilt artifacts. They are retained for continuity, but a fresh clone does not include those prerequisites. Do not interpret their absence as missing application source; use standard Maven/npm or Docker builds.

## Data and images

V1 creates schema; V2 converts legacy demo amounts to INR and adds product/order image paths; V3 adds history indexes; V4 adds customer profiles, order fulfillment/versioning and category visibility. A fresh database has no historical USD data to convert. Never edit an applied migration; add a new migration. The fixed legacy conversion is demonstration data migration, not a live exchange rate.

The optional seed creates or updates 18 sample products by SKU, preserves inventory and orders, and archives legacy verification fixtures. It creates no sales or predictions. Local product illustrations, including the fallback and USB-C hub, live under `frontend/public/images/products`. The public `backend/rds-ca.pem` is an AWS trust bundle, not a private key, and is required by the backend Docker image.
