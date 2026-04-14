# Copilot Instructions

Read and follow the instructions in [AGENTS.md](../AGENTS.md) for project structure, build commands, conventions, and testing rules.

## Copilot-Specific Notes

- You cannot merge pull requests. Wait for CI checks to complete and a human reviewer will merge after approval.
- **Before starting work**, verify green baseline: `mvn clean install`. Do NOT dismiss test failures as pre-existing.

- When fixing a bug, ask "what surface permitted this?" and write a category-scoped test, not just an instance test.