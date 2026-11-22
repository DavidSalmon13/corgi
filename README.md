# Plainly

Business insurance, explained in plain language. A policy administration platform for the Corgi work trial (Track 1). Brokers issue, change, correct and cancel General Liability policies. Policyholders see what they're covered for, what they owe, and why.

- Brief: [`ASSIGNMENT.md`](ASSIGNMENT.md) · Decisions and assumptions: [`DECISIONS.md`](DECISIONS.md)
- Screen designs: [`design/plainly/`](design/plainly/README.md)
- Product and specs: [`playbook/products/policy-admin/`](playbook/products/policy-admin/mission.md)

All people, businesses and money in this app are fictional. Payments use Stripe test mode.

## Run locally

Requirements: Java 21, Docker, Node 20.19+.

```bash
cp .env.example .env            # optional: the defaults work as-is
docker compose up -d            # Postgres 16 on localhost:5432
cd api && ./gradlew bootRun     # API on http://localhost:8080; Flyway migrates on start
curl localhost:8080/api/health  # {"status":"UP","database":"UP"}

cd web && npm install && npm run dev   # app on http://localhost:5173, /api proxied to :8080
```

Tests run against a throwaway Postgres started by Testcontainers, so Docker must be running:

```bash
cd api && ./gradlew test
```

To start over with an empty database: `docker compose down -v`. The ledger is append-only even locally, so this is the only way to remove ledger rows.
