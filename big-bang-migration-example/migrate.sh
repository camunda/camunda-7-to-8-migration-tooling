#!/usr/bin/env bash
#
# Big Bang Migration Script
#
# Migrates the Camunda 7 invoice example to Camunda 8 using all three migration tools:
#   1. Diagram Converter  — converts BPMN/DMN files
#   2. Code Conversion    — converts Java code (JavaDelegate -> @JobWorker)
#   3. Data Migrator      — migrates runtime/history data (instructions only)
#
# Usage:
#   ./migrate.sh              Run all migration steps (copy + diagrams + code)
#   ./migrate.sh --start-c7   Start the C7 app to inspect the process before migration (H2)
#   ./migrate.sh --start-c7-postgres  Start the C7 app with PostgreSQL database
#   ./migrate.sh --copy       Copy C7 source files into the C8 module
#   ./migrate.sh --diagrams   Run diagram conversion only
#   ./migrate.sh --code       Run code conversion only
#   ./migrate.sh --data       Show data migration instructions
#   ./migrate.sh --clean      Remove generated C8 source files (reset to pre-migration state)
#   ./migrate.sh --build      Build prerequisites first, then migrate
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

PROCESS_SOLUTION_C7_DIR="$SCRIPT_DIR/process-solution-c7"
PROCESS_SOLUTION_C8_DIR="$SCRIPT_DIR/process-solution-c8"
C7_DATA_GENERATOR_DIR="$SCRIPT_DIR/c7-data-generator"
C8_RESOURCES_DIR="$PROCESS_SOLUTION_C8_DIR/src/main/resources"
C8_JAVA_DIR="$PROCESS_SOLUTION_C8_DIR/src/main/java"

DIAGRAM_CONVERTER_CLI_JAR="$ROOT_DIR/diagram-converter/cli/target/camunda-7-to-8-diagram-converter-cli-0.3.0-SNAPSHOT.jar"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

info()  { echo -e "${BLUE}[INFO]${NC}  $1"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

banner() {
  echo ""
  echo -e "${BLUE}═══════════════════════════════════════════════════${NC}"
  echo -e "${BLUE}  $1${NC}"
  echo -e "${BLUE}═══════════════════════════════════════════════════${NC}"
  echo ""
}

# ─── Prerequisites ──────────────────────────────────────────────────────────

check_prerequisites() {
  local missing=false

  if ! command -v java &> /dev/null; then
    error "Java is not installed or not on PATH"
    missing=true
  fi

  if ! command -v mvn &> /dev/null; then
    error "Maven is not installed or not on PATH"
    missing=true
  fi

  if [ ! -f "$DIAGRAM_CONVERTER_CLI_JAR" ]; then
    warn "Diagram converter CLI jar not found at:"
    warn "  $DIAGRAM_CONVERTER_CLI_JAR"
    warn "Run './migrate.sh --build' or 'mvn install -DskipTests' from the root to build it first."
    missing=true
  fi

  if [ "$missing" = true ]; then
    echo ""
    error "Prerequisites check failed. See above."
    exit 1
  fi

  ok "Prerequisites check passed"
}

# ─── Step 0: Start C7 App ──────────────────────────────────────────────────

step_start_c7() {
  local profile="${1:-}"

  banner "Step 0: Start Camunda 7 Invoice Application"

  if [ "$profile" = "postgres" ]; then
    info "Starting the C7 invoice app with PostgreSQL database..."
    info "Make sure PostgreSQL is running, e.g.:"
    info "  docker run --name postgres -p 5432:5432 \\"
    info "    -e POSTGRES_PASSWORD=camunda -e POSTGRES_USER=camunda \\"
    info "    -e POSTGRES_DB=process-engine postgres:17"
  else
    info "Starting the C7 invoice app with embedded H2 database..."
  fi
  info ""
  info "Once started, open http://localhost:8080 (login: demo / demo)"
  info ""
  info "  Cockpit:  http://localhost:8080/camunda/app/cockpit/"
  info "  Tasklist: http://localhost:8080/camunda/app/tasklist/"
  info ""
  info "Press Ctrl+C to stop the application."
  echo ""

  if [ -n "$profile" ]; then
    mvn spring-boot:run -f "$C7_DATA_GENERATOR_DIR/pom.xml" -Dspring-boot.run.profiles="$profile"
  else
    mvn spring-boot:run -f "$C7_DATA_GENERATOR_DIR/pom.xml"
  fi
}

# ─── Step 1: Copy C7 → C8 ────────────────────────────────────────────────

step_copy() {
  banner "Step 1: Copy C7 Source Files into C8 Module"

  if [ -d "$PROCESS_SOLUTION_C8_DIR/src/main" ]; then
    warn "C8 module already contains source files. Removing them first..."
    rm -rf "$PROCESS_SOLUTION_C8_DIR/src/main"
  fi

  info "Copying from: process-solution-c7/src/main/"
  info "Copying to:   process-solution-c8/src/main/"
  echo ""

  cp -r "$PROCESS_SOLUTION_C7_DIR/src/main" "$PROCESS_SOLUTION_C8_DIR/src/main"

  ok "Files copied to C8 module."
  info "Copied files:"
  for f in "$C8_RESOURCES_DIR"/*.bpmn "$C8_RESOURCES_DIR"/*.dmn; do
    [ -f "$f" ] && info "  resources/$(basename "$f")"
  done
  for f in "$C8_JAVA_DIR"/org/camunda/bpm/example/invoice/service/*.java; do
    [ -f "$f" ] && info "  java/.../service/$(basename "$f")"
  done
}

# ─── Step 2: Diagram Conversion ──────────────────────────────────────────

step_diagrams() {
  banner "Step 2: Convert Diagrams (BPMN/DMN)"

  if [ ! -d "$C8_RESOURCES_DIR" ]; then
    error "C8 module has no resources. Run './migrate.sh --copy' first."
    exit 1
  fi

  info "Converting files in: $C8_RESOURCES_DIR"
  info "Using: --override --add-data-migration-execution-listener"
  echo ""

  java -jar "$DIAGRAM_CONVERTER_CLI_JAR" \
    local "$C8_RESOURCES_DIR" \
    --override \
    --add-data-migration-execution-listener

  echo ""
  ok "Diagram conversion complete."
  info "Converted files:"
  for f in "$C8_RESOURCES_DIR"/*.bpmn "$C8_RESOURCES_DIR"/*.dmn; do
    [ -f "$f" ] && info "  $(basename "$f")"
  done
}

# ─── Step 3: Code Conversion ─────────────────────────────────────────────

step_code() {
  banner "Step 3: Convert Code (JavaDelegate -> @JobWorker)"

  if [ ! -d "$C8_JAVA_DIR" ]; then
    error "C8 module has no Java sources. Run './migrate.sh --copy' first."
    exit 1
  fi

  info "Running OpenRewrite recipes on process-solution-c8..."
  echo ""

  mvn rewrite:run -f "$PROCESS_SOLUTION_C8_DIR/pom.xml"

  echo ""
  ok "Code conversion complete."
  info "Review the changes:"
  info "  diff -r process-solution-c7/src process-solution-c8/src"
}

# ─── Step 4: Data Migration (instructions) ───────────────────────────────

step_data() {
  banner "Step 4: Migrate Data"

  info "Data migration requires a running Camunda 7 database and a Camunda 8 cluster."
  info ""
  info "1. (Optional) Use the c7-data-generator module to populate C7 with test data."
  info ""
  info "2. Configure the data-migrator:"
  info "   Edit: data-migrator/assembly/resources/application.yml"
  info "   - Set camunda.migrator.c7.data-source.jdbc-url to your C7 database"
  info "   - Set camunda.client.* to your C8 cluster"
  info ""
  info "3. Build and run the data-migrator:"
  info "   cd $ROOT_DIR/data-migrator/assembly/target/camunda-7-to-8-data-migrator-*/"
  info "   ./start.sh --runtime --history"
  info ""
  info "   --runtime  Migrates active process instances and variables"
  info "   --history  Migrates historical data"
}

# ─── Clean ───────────────────────────────────────────────────────────────

step_clean() {
  banner "Clean: Remove Generated C8 Source Files"

  if [ -d "$PROCESS_SOLUTION_C8_DIR/src/main" ]; then
    rm -rf "$PROCESS_SOLUTION_C8_DIR/src/main"
    ok "Removed process-solution-c8/src/main/ — module is back to pre-migration state."
  else
    info "Nothing to clean — process-solution-c8/src/main/ does not exist."
  fi
}

# ─── Build ───────────────────────────────────────────────────────────────

build_prerequisites() {
  banner "Building Prerequisites"

  info "Building all modules (this may take a few minutes)..."
  echo ""

  mvn install -DskipTests -f "$ROOT_DIR/pom.xml"

  echo ""
  ok "Build complete."
}

# ─── Main ────────────────────────────────────────────────────────────────

run_all() {
  check_prerequisites
  step_copy
  step_diagrams
  step_code
  step_data

  banner "Migration Complete"
  ok "C7 source files have been copied to process-solution-c8/ and converted."
  ok "The original C7 files in process-solution-c7/ remain unchanged."
  ok "Follow the data migration instructions above to complete the big bang migration."
}

case "${1:-}" in
  --build)
    build_prerequisites
    run_all
    ;;
  --start-c7)
    step_start_c7
    ;;
  --start-c7-postgres)
    step_start_c7 postgres
    ;;
  --copy)
    step_copy
    ;;
  --diagrams)
    check_prerequisites
    step_diagrams
    ;;
  --code)
    step_code
    ;;
  --data)
    step_data
    ;;
  --clean)
    step_clean
    ;;
  --help|-h)
    echo "Usage: $0 [OPTION]"
    echo ""
    echo "Big Bang Migration Script - Migrates Camunda 7 invoice example to Camunda 8"
    echo ""
    echo "Options:"
    echo "  (no args)     Run all migration steps (copy + diagrams + code + data instructions)"
    echo "  --start-c7    Start the Camunda 7 app with H2 database"
    echo "  --start-c7-postgres  Start the Camunda 7 app with PostgreSQL database"
    echo "  --build       Build prerequisites first, then run all steps"
    echo "  --copy        Step 1: Copy C7 source files into the C8 module"
    echo "  --diagrams    Step 2: Convert BPMN/DMN diagrams in the C8 module"
    echo "  --code        Step 3: Convert Java code in the C8 module (JavaDelegate -> @JobWorker)"
    echo "  --data        Step 4: Show data migration instructions"
    echo "  --clean       Remove generated C8 source files (reset to pre-migration state)"
    echo "  --help, -h    Show this help message"
    ;;
  "")
    run_all
    ;;
  *)
    error "Unknown option: $1"
    echo "Run '$0 --help' for usage."
    exit 1
    ;;
esac
