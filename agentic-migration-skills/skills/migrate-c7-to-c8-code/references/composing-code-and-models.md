# Composing Code + Model Migration

When the scope is Code + models:

## Execution Order

The two paths are independent. A reasonable default is models first (diagrams define the job types/listeners the code must implement), then code, but follow the user's preference.

## Cross-Check After Both Complete

Job types emitted by the Diagram Converter should match the `@JobWorker(type = ...)` values produced by the code migration. Flag mismatches for the user.

## Deployment Wiring

After both complete, ask whether to wire deployment of converted files in application code via AskUserQuestion:

- **Yes, add/update @Deployment for converted files** (recommended when code scope includes a Spring Boot app) - add or update `@Deployment(resources = ...)` so it targets only converted resources with explicit recursive classpath patterns. Example: `@Deployment(resources = {"classpath*:**/converted-c8-*.bpmn", "classpath*:**/converted-c8-*.dmn"})`. Never target original diagrams.
- **No, I will handle deployment outside app startup** - leave code unchanged and record this decision in MIGRATION_REPORT.md.

## Report Keeping

Keep both inventories and both sets of results in MIGRATION_REPORT.md.
