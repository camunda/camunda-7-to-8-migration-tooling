# E2E Tests for Cockpit Plugin

This directory contains end-to-end tests for the Camunda 7 to 8 Data Migrator Cockpit Plugin using Playwright.

## Overview

These tests validate that:
- Camunda 7 starts successfully with the plugin deployed
- The Cockpit UI is accessible
- The plugin UI is visible on the processes dashboard
- The plugin can interact with the UI to display migrated/skipped entities

## Prerequisites

- Node.js 18+ and npm
- Docker and Docker Compose
- Built Cockpit plugin JAR and distribution

## Version Management

All component versions (Camunda 7, Camunda 8, Elasticsearch, Project version) are managed in the **root `pom.xml`** and automatically resolved via **Maven resource filtering** applied directly to the `docker-compose.yml` file.

The `docker-compose.yml` is generated from a template during the Maven build process:
- Source: `src/main/resources/docker-compose.yml.template`
- Generated: `docker-compose.yml` (git-ignored, filtered with actual versions)
- Maven properties used:
  - `${version.camunda-7}` - Camunda 7 version
  - `${version.camunda-8}` - Camunda 8 version
  - `${version.elasticsearch}` - Elasticsearch version
  - `${project.version}` - Project version for JAR/ZIP paths

This ensures consistency between Maven builds and Docker containers with direct property substitution, no intermediate `.env` file needed.

## Module Structure

The E2E tests are now a proper Maven module under `qa/`:

```
qa/
├── pom.xml                    # Parent POM
├── integration-tests/         # Java integration tests
│   ├── pom.xml
│   └── src/
└── e2e-tests/                 # This module - Playwright E2E tests
    ├── pom.xml
    ├── src/main/resources/
    │   └── .env.template
    ├── tests/
    └── docker-compose.yml
```

## Setup

1. **Build the project from root:**
   ```bash
   cd ../..
   mvn clean install
   ```

2. **Or build just the E2E tests module:**
   ```bash
   cd qa/e2e-tests
   mvn clean install
   ```

## Running Tests

### Via Maven (Recommended)
```bash
cd qa/e2e-tests
mvn verify
```

This will:
1. Generate `docker-compose.yml` from template with filtered versions
2. Install npm dependencies
3. Install Playwright browsers
4. Run all tests across all browsers
5. Generate reports

### Via npm (Manual)
```bash
cd qa/e2e-tests
# Ensure docker-compose.yml exists (run: mvn process-resources)
npm test
```

This will run all tests across three browsers:
- **Chromium** (Chrome-based)
- **Firefox**
- **Microsoft Edge**

### Run tests on a specific browser
```bash
# Run only on Chromium
npx playwright test --project=chromium

# Run only on Firefox
npx playwright test --project=firefox

# Run only on Edge
npx playwright test --project=edge
```

### Run tests with UI mode (interactive)
```bash
npm run test:ui
```

### Run tests in headed mode (see the browser)
```bash
npm run test:headed

# Or for a specific browser
npx playwright test --project=firefox --headed
```

### Run tests in debug mode
```bash
npm run test:debug
```

## How It Works

### Test Setup with Real Migration Data

1. **Docker Compose** starts the complete stack:
   - **Camunda 7** (with PostgreSQL) - source system
   - **Camunda 8 Run** - target system (includes Zeebe, Operate, Tasklist)
   - **Data Migrator** - runs migration to populate test data
   - **Cockpit Plugin** - mounted with real migration_mapping table data

2. **Migration Wait Process:**
   - **`start-services.sh`** starts all Docker containers
   - **`global-setup.ts`** (Playwright global setup hook) waits for migration completion
   - Checks Docker logs for "Migration completed - test data ready" message
   - Only after migration completes, tests are allowed to start
   - This ensures the database is fully populated before any test runs

3. **Playwright Tests:**
   - Tests navigate to the Cockpit, login, and interact with the plugin UI
   - Validates plugin behavior with actual migrated/skipped entities
   - Screenshots are captured for verification and debugging

**Why the wait is important:**
The migration process can take 30-60 seconds to complete. Without waiting, tests would run against an empty database and fail. The global setup ensures tests always have the required test data.

**Manual approach:**
```bash
# Start the services
bash start-services.sh &

# Wait for migration message in logs, then:
npm test

# Cleanup
docker compose down -v
```

## Test Structure

- `docker-compose.yml` - Complete test stack with Camunda 7, Camunda 8 Run, and Data Migrator
- `start-services.sh` - Helper script to start services and wait for migration completion
- `global-setup.ts` - Playwright global setup that waits for migration before tests start
- `playwright.config.ts` - Playwright configuration with webServer and globalSetup
- `tests/cockpit-plugin.spec.ts` - Main E2E test suite

## Test Coverage

The test suite includes:
- ✅ Camunda Cockpit loads successfully
- ✅ Plugin UI appears on the processes page
- ✅ Migrated and skipped entity tabs are visible
- ✅ Entity type selector works
- ✅ Empty state is displayed correctly
- ✅ No JavaScript/React errors in console

## Troubleshooting

### Tests fail with "Connection refused"
- Ensure Docker is running
- Check that port 8080 is not already in use
- Increase the timeout in `playwright.config.ts`

### Plugin not found error
- Verify the plugin JAR exists at `../../plugins/cockpit/target/`
- Build the plugin with `mvn clean install -pl plugins/cockpit`

### Camunda takes too long to start
- Increase `webServer.timeout` in `playwright.config.ts`
- Check Docker logs: `docker compose logs -f`

### docker-compose command not found
- The tests use Docker Compose V2 (`docker compose` with a space)
- If you have the older standalone `docker-compose`, update `playwright.config.ts` to use `docker-compose up`

## CI/CD Integration

The E2E tests run automatically in CI on every pull request and push to main.

The CI job:
1. Builds the Cockpit plugin JAR
2. Installs Node.js and npm dependencies
3. Installs Playwright browsers
4. Starts Camunda 7 with Docker Compose
5. Runs the E2E test suite
6. Uploads test reports and screenshots as artifacts

To run in CI:
```bash
# Set CI environment variable
CI=true npm test
```

This enables:
- Retries on failure
- Parallel execution disabled
- GitHub Actions annotations for test results
- Better error reporting

## Screenshots

Test screenshots are saved to `test-results/` directory for debugging.
