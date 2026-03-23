#!/usr/bin/env bash
#
# Migration Script for the Example Module
#
# Migrates the Camunda 7 example to Camunda 8 using:
#   1. Diagram Converter — converts BPMN/DMN files (camunda: → zeebe: namespace)
#   2. Code Conversion   — converts Java code (JavaDelegate/ExecutionListener → @JobWorker)
#
# The script copies C7 business code (not Application.java) into the camunda8-scaffold
# module, runs both conversion tools, then copies the result to camunda8-result for
# testing. Compare camunda8-result/ against camunda8-reference/.
#
# Usage:
#   ./migrate.sh              Run all migration steps
#   ./migrate.sh --diagrams   Run diagram conversion only (assumes sources already copied)
#   ./migrate.sh --code       Run code conversion only (assumes sources already copied)
#   ./migrate.sh --copy       Copy C7 sources to scaffold only (no conversion)
#   ./migrate.sh --clean      Reset scaffold and result to empty state
#   ./migrate.sh --build      Build prerequisites first, then run all steps
#   ./migrate.sh --help       Show this help message
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

C7_DIR="$SCRIPT_DIR/camunda7"
SCAFFOLD_DIR="$SCRIPT_DIR/camunda8-scaffold"
RESULT_DIR="$SCRIPT_DIR/camunda8-result"
REFERENCE_DIR="$SCRIPT_DIR/camunda8-reference"

C7_JAVA_DIR="$C7_DIR/src/main/java"
C7_RESOURCES_DIR="$C7_DIR/src/main/resources"

SCAFFOLD_JAVA_DIR="$SCAFFOLD_DIR/src/main/java"
SCAFFOLD_RESOURCES_DIR="$SCAFFOLD_DIR/src/main/resources"

RESULT_JAVA_DIR="$RESULT_DIR/src/main/java"
RESULT_RESOURCES_DIR="$RESULT_DIR/src/main/resources"

DIAGRAM_CONVERTER_CLI_JAR="$ROOT_DIR/diagram-converter/cli/target/camunda-7-to-8-diagram-converter-cli-0.3.0-SNAPSHOT.jar"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info()   { echo -e "${BLUE}[INFO]${NC}  $1"; }
ok()     { echo -e "${GREEN}[OK]${NC}    $1"; }
warn()   { echo -e "${YELLOW}[WARN]${NC}  $1"; }
error()  { echo -e "${RED}[ERROR]${NC} $1"; }

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

# ─── Step 0: Copy C7 sources to scaffold ──────────────────────────────────

step_copy() {
  banner "Step 0: Copy C7 Business Code to Scaffold"

  # Clean scaffold Java sources except the scaffold's own Application.java
  info "Cleaning scaffold Java sources (keeping Application.java)..."
  find "$SCAFFOLD_JAVA_DIR" -name "*.java" ! -path "*/io/camunda/conversion/Application.java" -delete 2>/dev/null || true
  # Remove empty C7 package dirs if they exist from a previous run
  find "$SCAFFOLD_JAVA_DIR" -type d -empty -delete 2>/dev/null || true

  # Copy C7 Java sources, excluding Application.java (scaffold has its own C8-compatible one)
  info "Copying C7 Java sources (excluding Application.java)..."
  find "$C7_JAVA_DIR" -name "*.java" ! -name "Application.java" | while read -r src; do
    rel="${src#$C7_JAVA_DIR/}"
    dest="$SCAFFOLD_JAVA_DIR/$rel"
    mkdir -p "$(dirname "$dest")"
    cp "$src" "$dest"
  done

  # Copy BPMNs (not processes.xml or other C7-specific resources)
  info "Copying C7 BPMN files..."
  cp "$C7_RESOURCES_DIR"/*.bpmn "$SCAFFOLD_RESOURCES_DIR/"

  echo ""
  ok "Sources copied to scaffold:"
  info "  Java:"
  find "$SCAFFOLD_JAVA_DIR" -name "*.java" | sort | while read -r f; do
    info "    $(echo "$f" | sed "s|$SCAFFOLD_DIR/||")"
  done
  info "  BPMNs:"
  for f in "$SCAFFOLD_RESOURCES_DIR"/*.bpmn; do
    [ -f "$f" ] && info "    $(basename "$f")" || true
  done
}

# ─── Step 1: Diagram Conversion ──────────────────────────────────────────

step_diagrams() {
  banner "Step 1: Convert Diagrams (BPMN/DMN)"

  info "Converting files in: $SCAFFOLD_RESOURCES_DIR"
  info "Using: --override --prefix (empty, replace in place)"
  echo ""

  java -jar "$DIAGRAM_CONVERTER_CLI_JAR" \
    local "$SCAFFOLD_RESOURCES_DIR" \
    --override \
    --prefix=""

  echo ""
  ok "Diagram conversion complete."
  info "Converted files:"
  for f in "$SCAFFOLD_RESOURCES_DIR"/*.bpmn; do
    [ -f "$f" ] && info "  $(basename "$f")" || true
  done
  for f in "$SCAFFOLD_RESOURCES_DIR"/*.dmn; do
    [ -f "$f" ] && info "  $(basename "$f")" || true
  done
}

# ─── Step 2: Code Conversion ─────────────────────────────────────────────

step_code() {
  banner "Step 2: Convert Code (ExecutionListener/JavaDelegate → @JobWorker)"

  info "Running OpenRewrite recipes on camunda8-scaffold..."
  echo ""

  mvn rewrite:run -f "$SCAFFOLD_DIR/pom.xml"

  echo ""
  ok "Code conversion complete."
}

# ─── Step 3: Copy to Result ──────────────────────────────────────────────

step_publish_result() {
  banner "Step 3: Publish to camunda8-result"

  # Copy converted Java files (excluding scaffold's Application.java)
  info "Copying converted Java sources to result..."
  find "$SCAFFOLD_JAVA_DIR" -name "*.java" ! -path "*/io/camunda/conversion/Application.java" | while read -r src; do
    rel="${src#$SCAFFOLD_JAVA_DIR/}"
    dest="$RESULT_JAVA_DIR/$rel"
    mkdir -p "$(dirname "$dest")"
    cp "$src" "$dest"
  done

  # Copy converted BPMNs
  info "Copying converted BPMN files to result..."
  for f in "$SCAFFOLD_RESOURCES_DIR"/*.bpmn; do
    [ -f "$f" ] && cp "$f" "$RESULT_RESOURCES_DIR/"
  done
  for f in "$SCAFFOLD_RESOURCES_DIR"/*.dmn; do
    [ -f "$f" ] && cp "$f" "$RESULT_RESOURCES_DIR/"
  done

  echo ""
  ok "Result module populated:"
  info "  Java:"
  find "$RESULT_JAVA_DIR" -name "*.java" | sort | while read -r f; do
    info "    $(echo "$f" | sed "s|$RESULT_DIR/||")"
  done
  info "  BPMNs:"
  for f in "$RESULT_RESOURCES_DIR"/*.bpmn; do
    [ -f "$f" ] && info "    $(basename "$f")" || true
  done
}

# ─── Clean ────────────────────────────────────────────────────────────────

step_clean() {
  banner "Reset Scaffold and Result"

  # Clean scaffold — remove migrated code but keep Application.java
  info "Cleaning scaffold..."
  find "$SCAFFOLD_JAVA_DIR" -name "*.java" ! -path "*/io/camunda/conversion/Application.java" -delete 2>/dev/null || true
  find "$SCAFFOLD_JAVA_DIR" -type d -empty -delete 2>/dev/null || true
  rm -f "$SCAFFOLD_RESOURCES_DIR"/*.bpmn

  # Clean result — remove migrated code but keep Application.java
  info "Cleaning result..."
  find "$RESULT_JAVA_DIR" -name "*.java" ! -path "*/org/camunda/conversion/Application.java" -delete 2>/dev/null || true
  find "$RESULT_JAVA_DIR" -type d -empty -delete 2>/dev/null || true
  rm -f "$RESULT_RESOURCES_DIR"/*.bpmn

  echo ""
  ok "Scaffold and result reset."
}

# ─── Build ────────────────────────────────────────────────────────────────

build_prerequisites() {
  banner "Building Prerequisites"

  info "Building all modules (this may take a few minutes)..."
  echo ""

  mvn install -DskipTests -f "$ROOT_DIR/pom.xml"

  echo ""
  ok "Build complete."
}

# ─── Summary ──────────────────────────────────────────────────────────────

summary() {
  banner "Migration Complete"

  ok "Diagrams and code have been converted and published to camunda8-result."
  echo ""
  info "Next steps:"
  info "  1. Review the migrated code:"
  info "       diff -r camunda8-result/src/ camunda8-reference/src/"
  info "  2. Build:"
  info "       mvn clean package -pl example/camunda8-result -am -DskipTests"
  info "  3. Run:"
  info "       cd camunda8-result && docker compose up --build"
  info "  4. Test:"
  info "       cd camunda8-result && ./test.sh"
}

# ─── Main ─────────────────────────────────────────────────────────────────

case "${1:-}" in
  --build)
    build_prerequisites
    check_prerequisites
    step_copy
    step_diagrams
    step_code
    step_publish_result
    summary
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
  --publish)
    step_publish_result
    ;;
  --clean)
    step_clean
    ;;
  --help|-h)
    echo "Usage: $0 [OPTION]"
    echo ""
    echo "Migration Script — Converts Camunda 7 example to Camunda 8"
    echo ""
    echo "Options:"
    echo "  (no args)     Run all migration steps (copy → diagrams → code → publish)"
    echo "  --copy        Copy C7 sources to scaffold only"
    echo "  --diagrams    Run diagram conversion only"
    echo "  --code        Run code conversion only (OpenRewrite)"
    echo "  --publish     Copy scaffold output to camunda8-result"
    echo "  --clean       Reset scaffold and result to empty state"
    echo "  --build       Build prerequisites first, then run all steps"
    echo "  --help, -h    Show this help message"
    ;;
  "")
    check_prerequisites
    step_copy
    step_diagrams
    step_code
    step_publish_result
    summary
    ;;
  *)
    error "Unknown option: $1"
    echo "Run '$0 --help' for usage."
    exit 1
    ;;
esac
