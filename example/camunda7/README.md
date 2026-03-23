# Camunda 7 Example Application

A runnable Camunda 7 Spring Boot application with example processes, configured to use PostgreSQL.

## Prerequisites

- Java 21+
- Maven
- Docker

## Quick Start (Docker Compose)

Build the fat JAR, then start everything with Docker Compose:

```bash
# From the repository root
mvn clean package -pl example/camunda7 -am -DskipTests

# Start PostgreSQL + Camunda 7
cd example/camunda7
docker compose up --build
```

Run the smoke tests (in a separate terminal):

```bash
cd example/camunda7
./test.sh
```

Shut down:

```bash
docker compose down
```

## Manual Start

### 1. Start PostgreSQL

```bash
docker run --name postgres -p 5432:5432 \
  -e POSTGRES_PASSWORD=camunda \
  -e POSTGRES_USER=camunda \
  -e POSTGRES_DB=process-engine \
  postgres:17
```

### 2. Build

From the repository root:

```bash
mvn clean package -pl example/camunda7 -am -DskipTests
```

### 3. Run

```bash
mvn spring-boot:run -pl example/camunda7
```

Or run the JAR directly:

```bash
java -jar example/camunda7/target/migration-example-camunda7-0.3.0-SNAPSHOT.jar
```

## Available Endpoints

| URL | Description |
|-----|-------------|
| http://localhost:8010/camunda/app/cockpit/ | Cockpit (login: demo/demo) |
| http://localhost:8010/camunda/app/tasklist/ | Tasklist (login: demo/demo) |
| http://localhost:8010/engine-rest/ | REST API |

## Deployed Processes

### my-project-process (process.bpmn)
Simple process: Start → User Task ("Say hello to demo") → End.

The user task is assigned to candidate user `demo`. Complete it via Tasklist.

### execution-listener-test (execution-listener-test.bpmn)
Process with a service task that has an execution listener (`MyExecutionListener`).

Start it via:
```bash
curl -X POST "http://localhost:8010/test/execution-listener?foo=bar"
```

## Smoke Tests

The `test.sh` script exercises both processes via the REST API:

1. Starts `execution-listener-test` via the custom controller endpoint
2. Starts `execution-listener-test` via the engine REST API
3. Starts `my-project-process`, claims and completes the user task, verifies completion

```bash
./test.sh              # default: http://localhost:8010
./test.sh http://localhost:9090  # custom URL
```

## Cleanup

```bash
docker compose down
# or if started manually:
docker rm -f postgres
```
