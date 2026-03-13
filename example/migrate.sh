#!/usr/bin/env bash
#
# Migration Script for the Example Module
#
# Migrates the Camunda 7 example to Camunda 8 using:
#   1. Diagram Converter — converts BPMN/DMN files (camunda: → zeebe: namespace)
#   2. Code Conversion   — converts Java code (JavaDelegate/ExecutionListener → @JobWorker)
#
# The script copies C7 sources into the camunda8-scaffold module, then runs
# both conversion tools. Compare the result against camunda8-reference/.
#
# Usage:
#   ./migrate.sh              Run all migration steps (copy + diagrams + code)
#   ./migrate.sh --diagrams   Run diagram conversion only (assumes sources already copied)
#   ./migrate.sh --code       Run code conversion only (assumes sources already copied)
#   ./migrate.sh --copy       Copy C7 sources to scaffold only (no conversion)
#   ./migrate.sh --clean      Reset scaffold to empty state
#   ./migrate.sh --build      Build prerequisites first, then run all steps
#   ./migrate.sh --help       Show this help message
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

C7_DIR="$SCRIPT_DIR/camunda7"
SCAFFOLD_DIR="$SCRIPT_DIR/camunda8-scaffold"
REFERENCE_DIR="$SCRIPT_DIR/camunda8-reference"

C7_JAVA_DIR="$C7_DIR/src/main/java"
C7_RESOURCES_DIR="$C7_DIR/src/main/resources"

SCAFFOLD_JAVA_DIR="$SCAFFOLD_DIR/src/main/java"
SCAFFOLD_RESOURCES_DIR="$SCAFFOLD_DIR/src/main/resources"

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
  banner "Step 0: Copy C7 Sources to Scaffold"

  # Clean existing scaffold sources (except application.yaml)
  info "Cleaning scaffold Java sources..."
  rm -rf "$SCAFFOLD_JAVA_DIR"

  info "Copying C7 Java sources..."
  cp -r "$C7_JAVA_DIR" "$SCAFFOLD_JAVA_DIR"

  # Copy BPMNs (not processes.xml or other C7-specific resources)
  info "Copying C7 BPMN files..."
  cp "$C7_RESOURCES_DIR"/*.bpmn "$SCAFFOLD_RESOURCES_DIR/"

  echo ""
  ok "Sources copied to scaffold:"
  info "  Java:"
  find "$SCAFFOLD_JAVA_DIR" -name "*.java" | while read -r f; do
    info "    $(echo "$f" | sed "s|$SCAFFOLD_DIR/||")"
  done
  info "  BPMNs:"
  for f in "$SCAFFOLD_RESOURCES_DIR"/*.bpmn; do
    [ -f "$f" ] && info "    $(basename "$f")"
  done
}

# ─── Step 1: Diagram Conversion ──────────────────────────────────────────

step_diagrams() {
  banner "Step 1: Convert Diagrams (BPMN/DMN)"

  info "Converting files in: $SCAFFOLD_RESOURCES_DIR"
  info "Using: --override"
  echo ""

  java -jar "$DIAGRAM_CONVERTER_CLI_JAR" \
    local "$SCAFFOLD_RESOURCES_DIR" \
    --override

  echo ""
  ok "Diagram conversion complete."
  info "Converted files:"
  for f in "$SCAFFOLD_RESOURCES_DIR"/*.bpmn "$SCAFFOLD_RESOURCES_DIR"/*.dmn; do
    [ -f "$f" ] && info "  $(basename "$f")"
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
  info "Review the changes:"
  info "  diff -r example/camunda8-scaffold/src/ example/camunda8-reference/src/"
}

# ─── Clean ────────────────────────────────────────────────────────────────

step_clean() {
  banner "Reset Scaffold to Empty State"

  info "Removing scaffold Java sources..."
  rm -rf "$SCAFFOLD_JAVA_DIR"
  mkdir -p "$SCAFFOLD_JAVA_DIR/io/camunda/conversion"

  # Restore the bare Application.java
  cat > "$SCAFFOLD_JAVA_DIR/io/camunda/conversion/Application.java" << 'JAVAEOF'
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Deployment(resources = "classpath*:*.bpmn")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
JAVAEOF

  info "Removing scaffold BPMN files..."
  rm -f "$SCAFFOLD_RESOURCES_DIR"/*.bpmn

  echo ""
  ok "Scaffold reset to empty state."
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

  ok "Diagrams and code have been converted."
  echo ""
  info "Next steps:"
  info "  1. Review the migrated code in example/camunda8-scaffold/src/"
  info "  2. Compare against the reference: diff -r camunda8-scaffold/src/ camunda8-reference/src/"
  info "  3. Note: Application.java needs manual update (@EnableProcessApplication → @Deployment)"
  info "  4. Build and test: mvn clean package -pl example/camunda8-scaffold -am -DskipTests"
  info "  5. Run: cd camunda8-scaffold && docker compose up --build"
}

# ─── Main ─────────────────────────────────────────────────────────────────

case "${1:-}" in
  --build)
    build_prerequisites
    check_prerequisites
    step_copy
    step_diagrams
    step_code
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
  --clean)
    step_clean
    ;;
  --help|-h)
    echo "Usage: $0 [OPTION]"
    echo ""
    echo "Migration Script — Converts Camunda 7 example to Camunda 8"
    echo ""
    echo "Options:"
    echo "  (no args)     Run all migration steps (copy + diagrams + code)"
    echo "  --copy        Copy C7 sources to scaffold only (no conversion)"
    echo "  --diagrams    Run diagram conversion only"
    echo "  --code        Run code conversion only (OpenRewrite)"
    echo "  --clean       Reset scaffold to empty state"
    echo "  --build       Build prerequisites first, then run all steps"
    echo "  --help, -h    Show this help message"
    ;;
  "")
    check_prerequisites
    step_copy
    step_diagrams
    step_code
    summary
    ;;
  *)
    error "Unknown option: $1"
    echo "Run '$0 --help' for usage."
    exit 1
    ;;
esac
