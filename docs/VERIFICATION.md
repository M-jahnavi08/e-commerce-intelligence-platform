# Tests and integration verification

Run commands from the indicated directory after installing dependencies described in SETUP.md.

| Directory | Commands | Requirements |
| --- | --- | --- |
| Root | `python -m unittest discover -s scripts/tests -v` | Python 3.13 |
| Root | `python scripts/audit-release.py` | Python and Git |
| `backend/` | `mvn verify` | Java 21, Maven; Docker for Testcontainers |
| `frontend/` | `npm ci`, `npm test`, `npm run build` | Node 22.12+ |
| `ml-service/` | `python -m pip install -r requirements.txt`, `python -m pytest -q` | Python 3.13; use a venv |
| Root | `cfn-lint infra/aws/commerce-stack.json infra/aws/data-stack.json infra/aws/github-release-role.json` | `python -m pip install cfn-lint==1.56.3` in a tooling venv |

A passing Maven command with skipped Testcontainers tests is not proof of database integration. Review the test summary. Embedded PostgreSQL is an opt-in alternative: `mvn -Dembedded.postgres=true test`. External database tests require a dedicated `commerce_verification` database and `TEST_DATABASE_URL`, `TEST_DATABASE_USER`, `TEST_DATABASE_PASSWORD`. The shared integration contract truncates test data. Never point it at the application database or valuable data.

## Complete Compose verification

```sh
python scripts/configure-local.py
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 240
python scripts/verify-stack.py --compose --resilience
```

Skip the generator when `.env` already exists. The script defaults to http://localhost:8088. `--resilience` requires `--compose` and a disposable stack. It exercises authorization, cart/checkout, idempotency, inventory, actual analytics, ML, Redis TTL/fallback/repopulation, Kafka acknowledgement, projection deduplication, poison-event dead-letter delivery and recovery after a Kafka outage. Labelled simulated order records are retained; the verification product is archived at the end.

For an already running native stack, `python scripts/verify-stack.py --base-url http://127.0.0.1:5173` tests HTTP behavior only. It does not verify Redis/Kafka and also creates labelled records.

## Troubleshooting

- Docker command missing/access denied: run from a terminal with access to a running Linux-container Docker engine. Do not interpret HTTP-only passes as broker verification.
- Frontend health: Nginx listens on container IPv4 port 8080; the probe targets `127.0.0.1:8080/health`, while the host publishes 8088. Inspect `docker compose exec frontend nginx -T` and `docker compose ps`.
- Backend health: inspect `docker compose logs --tail=200 backend`. Readiness includes PostgreSQL, not optional Redis. Kafka and HTTP exception handlers have distinct bean names.
- DLT: recovery explicitly targets `commerce.orders.DLT`, preserving the source partition. A regression test guards against the library default `-dlt` suffix. Inspect broker/backend logs without weakening the delivery assertion.
- Insufficient ML history: expected on a new database. Do not seed invented predictions to make the screen appear populated.

CI runs independent service tests, infrastructure checks, then the complete disposable Compose verification. The AWS release workflow is manual, gated by CI and the GitHub production environment. Merely committing workflow YAML does not establish that hosted CI has passed.
