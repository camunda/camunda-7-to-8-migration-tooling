# Claude Code Instructions

Read and follow the instructions in [AGENTS.md](AGENTS.md) before starting any task. It contains project structure, build commands, conventions, and testing rules.

## Claude-Specific Notes

- When running builds, prefer `mvn clean install -DskipTests` for fast iteration, then run targeted tests
- Use `mvn test -Dtest=ArchitectureTest -pl data-migrator/qa` to validate architecture rules after structural changes
- The license header check runs automatically during `mvn verify` - ensure new Java files include it
