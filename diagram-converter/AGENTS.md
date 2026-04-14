# Diagram Converter - Agent Instructions

See also the root [AGENTS.md](../AGENTS.md) for repo-wide conventions (commit messages, PR guidelines, CI, license headers, always-green policy, defect-category discipline).

## Overview

The diagram-converter analyzes and converts BPMN/DMN models from Camunda 7 to Camunda 8 format. Four submodules: `core/` (conversion engine), `cli/` (Picocli command-line tool), `webapp/` (Spring Boot REST API + React SPA), `extension-example/` (SPI reference, not released).

## Build Commands

```bash
# Full build (auto-formats code via Spotless)
cd diagram-converter && mvn clean install

# Build without tests
cd diagram-converter && mvn clean install -DskipTests

# Format check only (CI mode — fails on violations)
mvn verify -PcheckFormat

# Full CI build (what CI runs)
mvn verify -Pdistro,checkFormat --batch-mode

# Frontend (webapp)
cd diagram-converter/webapp/src/main/javascript && npm install && npm run build
```

Key profiles: `autoFormat` (default, auto-fixes formatting), `checkFormat` (CI, fails on violations), `distro` (default, all modules), `central-release` (excludes extension-example).

## Architecture: Two-Phase Conversion

The core uses a **visitor + conversion** pattern with strict phase separation:

**Phase 1 — Visiting (read-only):** `DiagramConverter.traverse()` walks the DOM depth-first. Registered `DomElementVisitor` implementations inspect elements and populate `Convertible` data objects with transformation metadata, record messages, and mark elements/attributes for removal. **Visitors must not modify the DOM.**

**Phase 2 — Conversion (DOM mutation):** `DiagramConverter.check()` executes removals, then invokes `Conversion` implementations against the `Convertible` objects. Conversions modify the XML (add Zeebe extension elements, transform expressions, etc.).

This separation prevents visitors from interfering with each other. All DOM mutations happen in Phase 2.

## Adding New Element/Attribute Support

When adding a new BPMN element or attribute handler:

1. Create or modify a `Visitor` in `visitor/impl/`
2. Create or reuse a `Convertible` in `convertible/`
3. Create or extend a `Conversion` in `conversion/`
4. Register new visitors/conversions in their `META-INF/services/` files
5. Add YAML test cases in `core/src/test/resources/BPMN_CONVERSION.yaml`

See `extension-example/` for a working reference showing both direct `DomElementVisitor` implementation and `AbstractSupportedAttributeVisitor` subclassing.

## Message Conventions

`MessageFactory` produces messages with severity levels (WARNING, TASK, REVIEW, INFO) from Mustache templates in `core/src/main/resources/message-templates.properties`. When adding new messages:
- Add a template entry in `message-templates.properties` with `.message`, `.severity`, and `.link` keys
- **Always include a `.link` field** pointing to the relevant Camunda docs page
- Add a static factory method in `MessageFactory`

## Version Gating

`SemanticVersion` enum controls feature availability per target Camunda 8 version. When a feature is only available from a specific version, check `version.ordinal() >= SemanticVersion._8_X.ordinal()` in the visitor before emitting messages or scheduling conversions.

## Testing

YAML-driven conversion tests in `BpmnConversionTest` are the primary test mechanism. Each case defines `givenBpmn`, `expectedBpmn`, `expectedMessages`, and optional `properties` (e.g., `platformVersion`). New BPMN element support must include test cases here.

`CoverageTest` validates all BPMN element types have corresponding visitors — fails if a new element type is added upstream without a visitor.

## Deployment

The webapp runs at https://diagram-converter.camunda.io (Docker on AWS ECS, `eclipse-temurin:21-jre-alpine`, health check at `/actuator/health`). Deployment via `.github/workflows/deploy-diagram-converter.yml`.
