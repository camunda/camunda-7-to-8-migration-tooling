# Camunda 8 Result (Migration Output)

The output of running `migrate.sh` on the Camunda 7 example. Compare this against `camunda8-reference/` to evaluate migration quality.

## How This Module Is Populated

This module is populated by `example/migrate.sh`, which:

1. Copies C7 business code (not Application.java) to `camunda8-scaffold/`
2. Runs **diagram-converter** on BPMNs (camunda: → zeebe: namespace)
3. Runs **code-conversion** via OpenRewrite (ExecutionListener → @JobWorker, RuntimeService → CamundaClient)
4. Copies the converted sources here

The `Application.java` is pre-configured with C8 annotations (`@Deployment`) — this is the one manual step that migration tooling doesn't cover.

## Quick Start

```bash
# From the repository root — run migration first
cd example
./migrate.sh

# Build the result
mvn clean package -pl example/camunda8-result -am -DskipTests

# Start PostgreSQL + Camunda 8 + Worker App
cd camunda8-result
docker compose up --build
```

Run the smoke tests (in a separate terminal):

```bash
cd example/camunda8-result
./test.sh
```

## Comparing Against Reference

```bash
cd example
diff -r camunda8-result/src/ camunda8-reference/src/
```

Key expected differences:
- **Package name**: `org.camunda.conversion` (result) vs `io.camunda.conversion` (reference) — migration doesn't rename packages
- **Code style**: OpenRewrite output may differ in formatting from hand-crafted code
