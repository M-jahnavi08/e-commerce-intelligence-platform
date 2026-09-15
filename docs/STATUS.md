# Verification status

Last reconciled: 2026-09-15. This replaces historical troubleshooting notes that no longer described the current code.

## Evidence from this development workspace

- Frontend: 9 tests and TypeScript/Vite build passed during application verification.
- ML: 27 tests passed.
- Backend: 15 tests passed against the disposable PostgreSQL database before the two later Kafka configuration regressions were added. Those two focused tests passed separately; a combined post-fix database run is not claimed.
- Kafka regressions reproduced duplicate bean registration and incorrect default DLT topic naming before their respective fixes, then passed. DLT transport is mocked in that focused test.
- Latest attempted Compose script run passed HTTP authorization, catalog, checkout, idempotency, ownership, inventory, analytics and ML. It stopped when launching Docker for Redis inspection returned Windows access denied. Kafka/DLT/resilience checks were not reached in that run.
- Three CloudFormation templates validate locally. AWS deployment and hosted GitHub Actions have not been executed from this workspace.

## Public-release preparation checks

On 2026-09-15, 10 infrastructure/release unit tests passed. The release-candidate secret/artifact scan found no findings; Git ignore rules, required source/assets, documentation links, fresh secret generation and overwrite protection, Compose schema, workflow YAML and all three CloudFormation templates validated. Application functionality was not changed during this cleanup. This is not a Git-history or comprehensive security audit.

## External verification still required

Run the complete `--compose --resilience` suite from a Docker-enabled terminal after rebuilding the current backend. Verify the corrected DLT routing on a real broker, cache outage recovery and Kafka outage recovery. Deploy only after CI passes and the AWS prerequisites, operational alarms, TLS and restore procedures are reviewed in the target account.

The live demo will be provided after deployment. No public demo or full production certification is claimed.
