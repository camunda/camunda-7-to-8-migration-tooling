# Big Bang Migration Example

This example demonstrates a **big bang migration** of a Camunda 7 process solution to Camunda 8, using all three tools from the migration tooling suite:

1. **Diagram Converter** — converts BPMN and DMN files from Camunda 7 to Camunda 8 format
2. **Code Conversion** — converts Java code (e.g. `JavaDelegate` to `@JobWorker`) using OpenRewrite recipes
3. **Data Migrator** — migrates runtime and history data from a Camunda 7 database to a Camunda 8 cluster

## What is a Big Bang Migration?

The solution is migrated from Camunda 7 to Camunda 8, including data migration scripts. At one moment in time, the C7-based solution is stopped, the data is migrated, and the new C8-based solution is started. The Camunda 7 solution can be decommissioned right after.

Note that a big bang relates to **one process solution only**. If you run multiple processes, you typically migrate them one by one in multiple big bangs, not in one super big bang.

## Module Structure

```
big-bang-migration-example/
├── process-solution/       The Camunda 7 invoice app to be migrated to Camunda 8
│   └── src/main/
│       ├── java/           JavaDelegate service tasks (ArchiveInvoiceService, NotifyCreditorService)
│       └── resources/      BPMN processes, DMN decisions, and other resources
├── c7-data-generator/      Runnable C7 Spring Boot app with Cockpit/Tasklist and demo data
│   └── src/main/
│       ├── java/           InvoiceC7Application, DemoDataGenerator, InvoiceApplicationHelper
│       └── resources/      BPMN/DMN (C7 originals), application.yaml
└── migrate.sh              Script to run all migration steps
```

## The Invoice Process

The example uses the Camunda 7 invoice process application which includes:

- **3 BPMN processes**: `invoice.v1.bpmn`, `invoice.v2.bpmn`, `reviewInvoice.bpmn`
- **1 DMN decision**: `invoiceBusinessDecisions.dmn` (invoice classification and approver assignment)
- **2 Java delegates**: `ArchiveInvoiceService` and `NotifyCreditorService`

## Quick Start

Build the tooling and run the full migration with a single command:

```bash
mvn install -DskipTests
cd big-bang-migration-example
./migrate.sh
```

Or build and migrate in one go:

```bash
cd big-bang-migration-example
./migrate.sh --build
```

The script supports running individual steps:

```bash
./migrate.sh --start-c7            # Step 0: start C7 app with H2 database
./migrate.sh --start-c7-postgres   # Step 0: start C7 app with PostgreSQL
./migrate.sh --diagrams            # Step 1 only: convert BPMN/DMN
./migrate.sh --code                # Step 2 only: convert Java code
./migrate.sh --data                # Step 3: show data migration instructions
```

## Prerequisites

Build the migration tooling modules from the repository root:

```bash
mvn install -DskipTests
```

## Migration Steps

### Step 0: Inspect the Camunda 7 Process (optional)

Before migrating, you can start the Camunda 7 invoice application to inspect the process solution, explore the BPMN/DMN models in Cockpit, and interact with running process instances in Tasklist:

```bash
./migrate.sh --start-c7
```

This starts a Spring Boot application with an embedded Camunda 7 engine, H2 database, and the Cockpit/Tasklist web apps. Once started:

- **Cockpit**: [http://localhost:8080/camunda/app/cockpit/](http://localhost:8080/camunda/app/cockpit/)
- **Tasklist**: [http://localhost:8080/camunda/app/tasklist/](http://localhost:8080/camunda/app/tasklist/)
- **Login**: `demo` / `demo`

The application deploys both invoice process versions (v1 and v2), the DMN decision table, and the review subprocess. It also creates demo users (demo, john, mary, peter) and starts sample process instances.

Press `Ctrl+C` to stop the application. The H2 database is persisted to `c7-data-generator/camunda-h2-database` and can be used as a source for the data-migrator in step 3.

#### Using PostgreSQL instead of H2

To use a PostgreSQL database (recommended for data migration testing), start a PostgreSQL container first:

```bash
docker run --name postgres -p 5432:5432 \
  -e POSTGRES_PASSWORD=camunda -e POSTGRES_USER=camunda \
  -e POSTGRES_DB=process-engine postgres:17
```

Then start the C7 app with the `postgres` profile:

```bash
./migrate.sh --start-c7-postgres
```

The PostgreSQL database can then be used directly as the source for the data-migrator in step 3 by pointing `camunda.migrator.c7.data-source.jdbc-url` to `jdbc:postgresql://localhost:5432/process-engine`.

### Step 1: Convert Diagrams

Use the diagram-converter CLI to convert the BPMN and DMN files from Camunda 7 to Camunda 8 format:

```bash
java -jar diagram-converter/cli/target/camunda-7-to-8-diagram-converter-cli-*.jar \
  local big-bang-migration-example/process-solution/src/main/resources/ \
  --override \
  --add-data-migration-execution-listener
```

- `--override` overwrites the original files in-place (instead of creating prefixed copies)
- `--add-data-migration-execution-listener` adds execution listeners on start events that are used by the Data Migrator for runtime migration

After this step, inspect the converted BPMN/DMN files. The converter transforms Camunda 7 extensions (e.g. `camunda:class`, `camunda:delegateExpression`) into Camunda 8 Zeebe extensions (e.g. `zeebe:taskDefinition`).

### Step 2: Convert Code

Use the OpenRewrite code-conversion recipes to convert the Java delegates from `JavaDelegate` to `@JobWorker`:

```bash
mvn rewrite:run -pl big-bang-migration-example/process-solution
```

This will automatically transform:
- `ArchiveInvoiceService`: `JavaDelegate.execute(DelegateExecution)` → `@JobWorker` method using `ActivatedJob`
- `NotifyCreditorService`: Same transformation

After this step, review the changes with `git diff`. The recipes handle:
- Replacing `JavaDelegate` interface with `@JobWorker` annotation
- Converting `execution.getVariable()` to `job.getVariable()`
- Converting `execution.setVariable()` to result map entries
- Updating imports from `org.camunda.bpm` to `io.camunda`

### Step 3: Migrate Data

Use the data-migrator to migrate runtime and history data from the Camunda 7 database to Camunda 8.

#### 3a. Generate Test Data (optional)

If you ran `./migrate.sh --start-c7` in step 0, the H2 database at `c7-data-generator/camunda-h2-database` already contains demo data (users, process instances, tasks) that can be migrated. You can point the data-migrator directly at this database.

Alternatively, the `c7-data-generator` module can be deployed against any Camunda 7 instance to populate it with invoice demo data.

#### 3b. Configure the Data Migrator

Edit `data-migrator/assembly/resources/application.yml` to point to your Camunda 7 database and Camunda 8 cluster. See the [data-migrator documentation](../data-migrator/README.md) for full configuration details.

#### 3c. Run the Migration

```bash
cd data-migrator/assembly/target/camunda-7-to-8-data-migrator-*/
./start.sh --runtime --history
```

- `--runtime` migrates active process instances and their variables
- `--history` migrates historical data (process instances, tasks, variables, incidents)

## After Migration

Once all three steps are complete:
1. Deploy the converted BPMN/DMN files and the migrated Java code to your Camunda 8 cluster
2. Verify that migrated process instances continue correctly
3. Decommission the Camunda 7 instance
