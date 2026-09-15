# AWS deployment

The templates are validated configuration, not a deployed environment. Deployment creates billable AWS resources. The application stack runs the frontend, backend and ML service together on Fargate; the optional data stack provisions private RDS PostgreSQL, encrypted/authenticated Redis and a three-broker SCRAM-authenticated MSK cluster.

## Prerequisites and provisioning order

1. Use an existing VPC with three private subnets in distinct Availability Zones, outbound HTTPS (NAT or endpoints), an HTTPS ALB, and an IP target group on port 8080 with `/health` as its health path. Restrict ALB ingress to your intended audience.
2. Create a Secrets Manager runtime secret using the AWS-managed key, initially containing no credentials. Create `commerce-stack.json` with `DesiredCount=0`, its secret ARN, the VPC/subnet/ALB parameters, your HTTPS public origin, and placeholder Redis/Kafka hostnames. No containers run at this stage.
3. Create `data-stack.json` using the application stack's `TaskSecurityGroupId` output, the same VPC and private subnets, and an available PostgreSQL 17 minor version. Discover the version with `aws rds describe-db-engine-versions --engine postgres --query 'DBEngineVersions[].EngineVersion'`. Engine availability and instance classes depend on the AWS region. Alternatively, connect compatible existing private services.
4. Populate the runtime secret with the keys below. Obtain database, Redis and Kafka credential secret ARNs from the data-stack outputs. Obtain SCRAM bootstrap brokers with `aws kafka get-bootstrap-brokers --cluster-arn <KafkaClusterArn>` and use `BootstrapBrokerStringSaslScram`. All secrets must stay in Secrets Manager; never commit them or put them in Docker build arguments.
5. Update the application stack's Redis/Kafka parameters with the real endpoints, keeping `DesiredCount=0`. Deploy the current application template before using the release workflow.
6. Configure the repository's `production` GitHub environment with `AWS_REGION`, `AWS_DEPLOY_ROLE_ARN` and `AWS_CLOUDFORMATION_ROLE_ARN`, and required reviewers. Deploy `github-release-role.json` to create this identity, supplying the exact repository, existing GitHub OIDC provider, application stack ARN, three ECR repository ARNs and your CloudFormation service role ARN. The release role can push to those repositories and update only that stack; it can pass only the named CloudFormation service role. That service role must trust `cloudformation.amazonaws.com` and have permission to update the application resources and pass its task/execution roles. Provision it using your account's deployment administrator. No long-lived AWS keys are needed.
7. Dispatch `release.yml` for the existing application stack. It runs the complete CI suite, builds/pushes immutable commit-tagged images, updates ImageTag/DesiredCount while preserving other parameters, and waits for deployment stability and verifies that the stable task definition contains all three requested commit-tagged images and the requested task count. A failed deployment uses the ECS circuit breaker. GitHub approval and the AWS account configuration must exist before this workflow can run.

## Runtime secret keys

- `DATABASE_URL`: `jdbc:postgresql://<DatabaseHost>:5432/commerce?sslmode=verify-full&sslrootcert=/app/rds-ca.pem`. The backend image includes the official AWS RDS CA bundle; refresh it from https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem when AWS rotates certificates.
- `DATABASE_USER` and `DATABASE_PASSWORD`: database credentials. Use a dedicated application role with only the DDL/DML rights needed for Flyway and this schema where organizational policy requires it.
- `JWT_SECRET` and `ML_SERVICE_TOKEN`: independently generated random secrets, each at least 32 bytes.
- `REDIS_PASSWORD`: the Redis authentication token.
- `KAFKA_SASL_JAAS_CONFIG`: `org.apache.kafka.common.security.scram.ScramLoginModule required username="commerce" password="<Kafka secret password>";`. The generated Kafka secret uses an alphanumeric password. The default application configuration is SASL_SSL / SCRAM-SHA-512. SSL-only private clusters can select `KafkaSecurityProtocol=SSL`; this omits the SASL secret mapping.

For the first deployment, add ADMIN_EMAIL/ADMIN_PASSWORD to the runtime secret and set the BootstrapAdmin parameter to true. After the administrator exists, set BootstrapAdmin=false and remove those two keys from the secret. Local bootstrap credentials are never copied into cloud images.

## Operations

Every container has a health check. Backend readiness includes PostgreSQL and application readiness, while liveness checks only application liveness. Redis outages remain observable on aggregate health but do not fail container readiness; the cache falls back to PostgreSQL. The frontend waits for a healthy backend, which waits for ML. ALB serves only the frontend; data security groups admit only the application task security group on database/cache/Kafka ports. CPU and memory alarms are included; attach notification actions appropriate to your account. RDS has backups, encryption, Multi-AZ and deletion protection; Redis has TLS, authentication, replication and snapshots; Kafka uses TLS, SCRAM and encryption at rest. Topic replication is three in the application stack.

Flyway serializes migrations at startup. Use backward-compatible migrations for rolling deployments. Roll back application images by redeploying a previous verified SHA; use corrective migrations rather than automatically rolling back the database. Restore drills, retention, alert destinations, WAF/rate limiting, private-network reachability, instance capacity and cost budgets must be verified in the target AWS account. The local authentication limiter is per process; it is not a distributed WAF.

Validate without an AWS account using `cfn-lint infra/aws/commerce-stack.json infra/aws/data-stack.json`. Account-specific create/update and connectivity have not been executed here.

## Exact remaining execution steps

On a Docker host, from the repository root:

```sh
python scripts/configure-local.py
docker compose config --quiet
docker compose build
docker compose up -d --wait --wait-timeout 240
python scripts/verify-stack.py --compose --resilience
```

Use a disposable Compose stack for `--resilience`: it temporarily stops Redis and Kafka, always attempts to restart them, and creates labelled verification orders. It verifies Redis fallback/repopulation, Kafka-offline checkout with a pending outbox row, eventual projection after recovery, duplicate event handling and poison-event dead-letter delivery. Run `docker compose logs --no-color --tail=200` on failure. Stop with `docker compose down`; volumes are retained.

For AWS, complete prerequisites 1–6 above using real account/VPC/ALB/service-role and secret values. Deploy all template changes with task count zero for initial provisioning, or use a reviewed stack update for an existing deployment. Configure the production GitHub environment variables and required reviewers, then dispatch `.github/workflows/release.yml` with the application stack name and desired count. The workflow cannot provision the VPC, ALB, GitHub OIDC provider, or your account's CloudFormation execution identity. These are explicit prerequisites, not generated credentials or guessed infrastructure IDs.

New three-replica Kafka topics require two in-sync replicas. For an existing MSK topic, apply the same setting through an authenticated Kafka admin client:

```sh
kafka-configs.sh --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" --command-config client.properties --entity-type topics --entity-name commerce.orders --alter --add-config min.insync.replicas=2
kafka-configs.sh --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" --command-config client.properties --entity-type topics --entity-name commerce.orders.DLT --alter --add-config min.insync.replicas=2
```

Keep `client.properties` outside version control with the cluster's TLS/SCRAM settings. Verify replica assignment and ISR before this change. Local single-broker Compose topics intentionally use one in-sync replica.

Local validation covers templates, release logic, PostgreSQL integration and mock broker failures. It does not substitute for the Docker resilience run, AWS rollout, HTTPS smoke test, restore drill or account-specific alarms. No Docker binary or AWS CLI/account access was available during this infrastructure pass.
