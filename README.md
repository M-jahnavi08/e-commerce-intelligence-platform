# E-Commerce Intelligence Platform

A full-stack portfolio project combining an INR storefront, transactional commerce APIs and sales intelligence derived from actual simulated purchases.

**Live demo: a public live demo will be provided after deployment.** The application is not currently advertised as a deployed production service.

## Features

- Product search, category and price filters, local product illustrations, cart and order history.
- JWT authentication with customer/admin roles; server-priced checkout with explicitly simulated payments.
- Admin product editing/archiving, optimistic conflict detection, inventory adjustments and audit history, order history and sales analytics.
- PostgreSQL transactions, Flyway migrations, ordered row locking and customer-scoped checkout idempotency.
- Redis category caching with database fallback; Kafka transactional outbox, deduplicated projections and explicit dead-letter routing.
- TF-IDF recommendations, random-forest demand forecasting and Isolation Forest anomaly detection. Insufficient history is reported honestly; predictions and sales are not fabricated.

## Stack and structure

| Directory | Responsibility |
| --- | --- |
| `frontend/` | React, TypeScript, Vite, React Router, Tailwind, Axios, TanStack Query |
| `backend/` | Java 21, Spring Boot/MVC, JPA/Hibernate, Security/JWT, Maven |
| `ml-service/` | Python 3.13, FastAPI, Pandas, NumPy, scikit-learn |
| `infra/aws/` | CloudFormation application/data/OIDC templates and deployment notes |
| `scripts/` | Configuration, optional catalog seed, verification and release tooling |
| `.github/workflows/` | Tests, Docker integration checks and manually dispatched AWS release |
| `docs/` | Architecture, setup, verification and release guidance |

## Quick start: complete Docker stack

Install Git, Python 3.13 and Docker Engine/Desktop with Linux containers and Compose v2. Clone this repository and open a terminal in its root. Docker must be running and accessible to that terminal.

```sh
python scripts/configure-local.py
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 240
```

Open [http://localhost:8088](http://localhost:8088). Sign in using `ADMIN_EMAIL` and `ADMIN_PASSWORD` from your generated local `.env`. This file is ignored by Git; never paste its contents into an issue. The generator leaves an existing `.env` unchanged. `.env.example` documents the keys; do not copy its empty values over working configuration.

A fresh database has no products or orders. Add products in **Workspace → Inventory**, or optionally seed the twelve labelled demo products and a customer account:

```sh
python scripts/demo-data.py
```

The seed uses the backend on `127.0.0.1:8080` and stores generated customer credentials in `.env`. It creates no sales or predictions. All product images are bundled local SVG assets. Prices and order snapshots use INR (₹).

Register a customer or use the generated demo customer, add products, test a declined payment, then place a successful simulated order. Inspect inventory, orders and analytics as admin. Forecasts/anomalies require historical observations; a new database should report insufficient data.

```sh
docker compose ps
docker compose logs --tail=100 backend frontend
docker compose down
```

Stopping retains PostgreSQL/Kafka volumes. Do not delete volumes unless you intend to erase their data. Host ports 5432, 8080 and 8088 must be free; Redis, Kafka and ML are internal to Compose. Stop the optional native stack before starting Compose on port 8080.

## Verification

```sh
python -m unittest discover -s scripts/tests -v
python scripts/audit-release.py
python scripts/verify-stack.py --compose --resilience
```

The resilience command is for a **disposable local/CI stack**: it creates labelled verification records, temporarily stops Redis and Kafka, and attempts to restart them. It checks cache fallback, pending outbox durability, delivery after recovery, duplicate handling and malformed-event delivery to `commerce.orders.DLT`. It is not a production smoke test.

See [verification instructions](docs/VERIFICATION.md) for backend, frontend and ML commands, test isolation and troubleshooting. [Verification status](docs/STATUS.md) distinguishes passing local checks from broker/cloud checks still requiring external execution.

## Documentation

- [Local setup and configuration](docs/SETUP.md)
- [Architecture and consistency](docs/ARCHITECTURE.md)
- [Tests and verification](docs/VERIFICATION.md)
- [GitHub release checklist](docs/RELEASE.md)
- [AWS deployment prerequisites and operations](infra/aws/README.md)
- [Security reporting and credential handling](SECURITY.md)

## Boundaries

Payments are simulated; no real payment details are collected. JWTs last 15 minutes and live in browser memory; refresh/revocation/password-recovery flows are not implemented. Analytics use paid order snapshots. ML outputs are statistical estimates requiring review. Production launch still requires infrastructure verification, TLS, operational monitoring, restore drills, rate limiting and account-specific security review.

Workspace-specific launchers depend on ignored tools/artifacts and are not the fresh-clone setup path. Use Docker or the documented native development commands. Application code, lockfiles, migrations, Docker files and local image assets are included; databases, secrets, installed dependencies and build artifacts are not.
