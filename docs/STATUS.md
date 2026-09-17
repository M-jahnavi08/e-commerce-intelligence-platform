# Verification status

Last verified: 2026-09-17.

## Passed locally

- 19 backend tests, none skipped, against a dedicated PostgreSQL `commerce_verification` database. Includes concurrent checkout/stock protection, idempotency, ownership, roles, profiles, fulfillment, CORS, image validation, cache fallback, outbox and Kafka/DLT configuration regressions.
- 10 frontend tests, TypeScript checking and Vite production build.
- 27 ML tests and 11 infrastructure/release/catalog-asset tests.
- Backend sources compiled with the local ECJ runner; executable Spring Boot JAR packaged and ZIP contents validated.
- Real browser customer and admin flows against Spring Boot, PostgreSQL and FastAPI: login/register, filters, galleries, recommendations, cart, declined and paid simulated checkout, orders, profiles, product/category editing, product creation/archiving, stock adjustment/audit, customers, analytics and fulfillment. Forecast/anomaly endpoints return honest insufficient-history states.
- Local verification fixtures were removed after a database snapshot, preserving other purchases. The optional seed provides 18 products; this existing database also retains a USB-C hub, giving 19 active products. Every active product has a locally served full/detail gallery.
- Current release candidates exclude secrets, generated artifacts and database snapshots. Compose schema, workflow YAML and all three CloudFormation templates validate. No public deployment is claimed.

## Environment limitations

Docker is not available in this session. Native frontend (5173), backend (8080), PostgreSQL (55432) and ML (8000) run successfully. Native mode deliberately disables Redis/Kafka. Their unit/contract checks pass, but real broker delivery, cache outages and the full Compose resilience suite still require a Docker-enabled machine.

Standard Maven/javac fails in Windows `ZipFileSystemProvider.removeFileSystem` with `AccessDeniedException` during dependency JAR `toRealPath`. The local compilation/packaging path succeeds without changing dependencies or deleting the cache. Standard Maven and Docker builds remain configured for CI, but a successful hosted run has not been observed here.

## Remaining external validation

From a Docker-enabled terminal, run the complete stack and resilience suite on a disposable database:

```sh
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 240
python scripts/verify-stack.py --compose --resilience
```

Keep verification fixtures in disposable environments. Seed the presentation catalog with `python scripts/demo-data.py`. AWS deployment, hosted CI, TLS and operational recovery drills require the target accounts/environment. The live demo will be provided after deployment.
