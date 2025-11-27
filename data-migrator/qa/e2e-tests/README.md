# E2E Tests for Cockpit Plugin

End-to-end tests for the Camunda 7 to 8 Data Migrator Cockpit Plugin using Playwright.

## Overview

These tests validate that the Cockpit plugin loads correctly and can display migrated/skipped entities from the data migrator.

## Prerequisites

- Docker and Docker Compose
- Java 21+ (for Maven build)
- Node.js 18+ (installed automatically by Maven)

## Version Management

The `docker-compose.yml` file is **generated from a template** during the Maven build.

- **Template**: `src/main/resources/docker-compose.yml.template`
- **Generated**: `docker-compose.yml` (git-ignored, contains actual versions)

Maven automatically substitutes these properties from the root `pom.xml`:
- `${version.camunda-7}` → Camunda 7 version
- `${version.camunda-8}` → Camunda 8 version  
- `${version.elasticsearch}` → Elasticsearch version
- `${project.version}` → Project version for JAR/ZIP paths

This ensures all components use consistent, up-to-date versions without manual coordination.

## Setup

1. **Build the project from root:**
   ```bash
   cd ../..
   mvn clean install -DskipTests
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
# Ensure docker-compose.yml exists first
mvn process-resources

# Run all tests (Chromium, Firefox, Edge)
npm test

# Run specific browser (options: chromium, firefox, edge)
npx playwright test --project=chromium

# Development modes
npm run test:ui          # Interactive UI mode
npm run test:headed      # See browser windows
npx playwright test --project=firefox --headed

npm run test:debug       # Debug mode
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
- Ensure Docker is running and ports 8090 are free
- Increase the timeout in `playwright.config.ts`

### Plugin not found error
- Build the plugin with `mvn clean install -pl plugins/cockpit`

### Camunda takes too long to start
- Migration can take up to 5 minutes on slower systems
- Increase `webServer.timeout` in `playwright.config.ts` if needed

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
