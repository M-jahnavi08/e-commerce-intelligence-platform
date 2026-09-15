# Security

Do not submit passwords, JWTs, environment files, database dumps or private customer data in public issues or pull requests. Use GitHub private vulnerability reporting if enabled; otherwise request a private reporting channel without publishing exploit details or secrets.

Generate local configuration with `python scripts/configure-local.py`. `.env` and variants, work data, build outputs and private-key containers are excluded from version control/build contexts. `.env.example` contains blank secret placeholders. The RDS PEM file is a public CA certificate bundle and must remain in the source tree.

Run `python scripts/audit-release.py` before staging. It checks release candidates for local secret matches, private keys, AWS access-key identifiers and generated files without printing secret values. It is a targeted safeguard, not a complete secret scanner or Git history audit. Enable GitHub secret scanning/push protection where available. If a credential was published, revoke/rotate it; deleting it from the working tree is not sufficient.

Production configuration and authentication limitations are documented in README.md and infra/aws/README.md. Payments are simulated; this repository must not collect real payment information.
