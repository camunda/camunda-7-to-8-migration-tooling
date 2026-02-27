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
#   ./migrate.sh              Run all migration steps (diagrams + code)
#   ./migrate.sh --start-c7   Start the C7 app to inspect the process before migration
#   ./migrate.sh --diagrams   Run diagram conversion only
#   ./migrate.sh --code       Run code conversion only
#   ./migrate.sh --data       Show data migration instructions
#   ./migrate.sh --build      Build prerequisites first, then migrate
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

PROCESS_SOLUTION_DIR="$SCRIPT_DIR/process-solution"
C7_DATA_GENERATOR_DIR="$SCRIPT_DIR/c7-data-generator"
RESOURCES_DIR="$PROCESS_SOLUTION_DIR/src/main/resources"

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
  banner "Step 0: Start Camunda 7 Invoice Application"

  info "Starting the C7 invoice app with embedded engine and H2 database..."
  info "Once started, open http://localhost:8080 (login: demo / demo)"
  info ""
  info "  Cockpit:  http://localhost:8080/camunda/app/cockpit/"
  info "  Tasklist: http://localhost:8080/camunda/app/tasklist/"
  info ""
  info "Press Ctrl+C to stop the application."
  echo ""

  mvn spring-boot:run -f "$C7_DATA_GENERATOR_DIR/pom.xml"
}

# ─── Step 1: Diagram Conversion ────────────────────────────────────────────

step_diagrams() {
  banner "Step 1: Convert Diagrams (BPMN/DMN)"

  info "Converting files in: $RESOURCES_DIR"
  info "Using: --override --add-data-migration-execution-listener"
  echo ""

  java -jar "$DIAGRAM_CONVERTER_CLI_JAR" \
    local "$RESOURCES_DIR" \
    --override \
    --add-data-migration-execution-listener

  echo ""
  ok "Diagram conversion complete."
  info "Converted files:"
  for f in "$RESOURCES_DIR"/*.bpmn "$RESOURCES_DIR"/*.dmn; do
    [ -f "$f" ] && info "  $(basename "$f")"
  done
}

# ─── Step 2: Code Conversion ───────────────────────────────────────────────

step_code() {
  banner "Step 2: Convert Code (JavaDelegate -> @JobWorker)"

  info "Running OpenRewrite recipes on process-solution..."
  echo ""

  mvn rewrite:run -f "$PROCESS_SOLUTION_DIR/pom.xml"

  echo ""
  ok "Code conversion complete."
  info "Review the changes:"
  info "  git diff big-bang-migration-example/process-solution/src/"
}

# ─── Step 3: Data Migration (instructions) ─────────────────────────────────

step_data() {
  banner "Step 3: Migrate Data"

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

# ─── Build ──────────────────────────────────────────────────────────────────

build_prerequisites() {
  banner "Building Prerequisites"

  info "Building all modules (this may take a few minutes)..."
  echo ""

  mvn install -DskipTests -f "$ROOT_DIR/pom.xml"

  echo ""
  ok "Build complete."
}

# ─── Main ───────────────────────────────────────────────────────────────────

run_all() {
  check_prerequisites
  step_diagrams
  step_code
  step_data

  banner "Migration Complete"
  ok "Diagrams and code have been migrated."
  ok "Follow the data migration instructions above to complete the big bang migration."
}

case "${1:-}" in
  --build)
    build_prerequisites
    check_prerequisites
    step_diagrams
    step_code
    step_data
    banner "Migration Complete"
    ok "Diagrams and code have been migrated."
    ok "Follow the data migration instructions above to complete the big bang migration."
    ;;
  --start-c7)
    step_start_c7
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
  --help|-h)
    echo "Usage: $0 [OPTION]"
    echo ""
    echo "Big Bang Migration Script - Migrates Camunda 7 invoice example to Camunda 8"
    echo ""
    echo "Options:"
    echo "  (no args)     Run all migration steps (diagrams + code + data instructions)"
    echo "  --start-c7    Start the Camunda 7 app to inspect the process before migration"
    echo "  --build       Build prerequisites first, then run all steps"
    echo "  --diagrams    Run diagram conversion only (BPMN/DMN)"
    echo "  --code        Run code conversion only (JavaDelegate -> @JobWorker)"
    echo "  --data        Show data migration instructions"
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
