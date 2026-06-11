# Camunda 8 Scaffold (Migration Target)

Empty Camunda 8 Spring Boot project — the starting point for migration. Run the diagram-converter and code-conversion tools to populate it from the Camunda 7 source.

## What's Here

- Spring Boot app with Camunda 8 SDK dependency
- Docker Compose setup (PostgreSQL + Camunda 8 platform + worker app)
- **No BPMNs** — add them via diagram-converter
- **No worker code** — add it via code-conversion

## Quick Start

```bash
# From the repository root
mvn clean package -pl example/camunda8-scaffold -am -DskipTests

# Start PostgreSQL + Camunda 8 + Worker App
cd example/camunda8-scaffold
docker compose up --build
```

## Migration Workflow

1. Convert BPMNs: run diagram-converter on `example/camunda7/src/main/resources/*.bpmn` → place output in `src/main/resources/`
2. Convert Java: run code-conversion on `example/camunda7/src/main/java/` → place output in `src/main/java/`
3. Rebuild and test: `mvn clean package && docker compose up --build`
4. Compare result against `example/camunda8-reference/`
