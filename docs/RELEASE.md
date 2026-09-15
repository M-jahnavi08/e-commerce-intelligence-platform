# Public GitHub release checklist

1. Run `python scripts/audit-release.py`; inspect the staged diff and file list before pushing. Never force-add ignored runtime files. Existing local databases, logs, backups and reports stay on disk but outside the release.
2. Commit application source, lockfiles, migrations, local SVG assets, Docker files and the public RDS trust bundle. Do not commit `.env`, `work/`, `outputs/`, dependencies, virtual environments or generated builds.
3. Run the service tests and full Docker verification in VERIFICATION.md. Wait for hosted CI results; do not advertise unexecuted checks as passing.
4. Review repository visibility, license choice, default branch protection, private vulnerability reporting and secret-scanning/push-protection settings. No license has been selected automatically and no repository has been published by these preparation steps.
5. Configure AWS/GitHub production environment variables and scoped OIDC permissions only when ready to deploy. The release workflow does not contain AWS credentials. Follow infra/aws/README.md; deployment remains separate from publishing source.
6. Add the live demo URL only after deployment and end-to-end verification. Until then, retain the README note that the live demo will be provided after deployment.

This local project has no `.git` directory at the time of preparation, so there is no local commit history to inspect. If importing an existing Git history, run a history-aware secret scan before making it public. The release audit evaluates current nonignored files (and tracked files when a Git repository exists), not remote branches or previous commits.
