# Camunda 8 Reference Example Application

The hand-crafted Camunda 8 equivalent of the Camunda 7 example. Use this as a reference to compare against migration tooling output.

## Architecture

Unlike Camunda 7 (embedded engine), Camunda 8 uses an external platform. This setup runs 3 containers:

- **postgres** — shared database for the Camunda 8 platform
- **camunda8** — the Camunda 8 platform (Zeebe + Operate + Tasklist)
- **camunda8-app** — Spring Boot worker app with `@JobWorker` methods

## Prerequisites

- Java 21+
- Maven
- Docker

## Quick Start (Docker Compose)

Build the fat JAR, then start everything with Docker Compose:

```bash
# From the repository root
mvn clean package -pl example/camunda8-reference -am -DskipTests

# Start PostgreSQL + Camunda 8 + Worker App
cd example/camunda8-reference
docker compose up --build
```

Run the smoke tests (in a separate terminal):

```bash
cd example/camunda8-reference
./test.sh
```

Shut down:

```bash
docker compose down
```

## Available Endpoints

| URL | Description |
|-----|-------------|
| http://localhost:8080 | Camunda 8 platform (Operate, Tasklist — login: demo/demo) |
| http://localhost:8080/v2/topology | Camunda 8 REST API |
| http://localhost:8090 | Worker app (custom controller) |

## Deployed Processes

### my-project-process (process.bpmn)
Simple process: Start → User Task ("Say hello to demo") → End.

The user task has `candidateUsers="demo"`. Complete it via Tasklist.

### execution-listener-test (execution-listener-test.bpmn)
Process with a service task handled by a `@JobWorker` (migrated from C7 execution listener).

Start it via:
```bash
curl -X POST "http://localhost:8090/test/execution-listener?foo=bar"
```

## Smoke Tests

The `test.sh` script exercises both processes via the C8 REST API:

1. Starts `execution-listener-test` via the custom controller endpoint
2. Starts `execution-listener-test` via the C8 REST API v2
3. Starts `my-project-process`, searches for user task, assigns and completes it

```bash
./test.sh                                          # defaults
./test.sh http://localhost:8080 http://localhost:8090  # custom URLs
```

## Cleanup

```bash
docker compose down
```
