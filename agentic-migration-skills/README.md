# Agentic Migration Skills

[Agent Skills](https://agentskills.io/) for migrating Camunda 7 Java codebases to Camunda 8. Works with any Agent Skills-compatible AI coding agent.

## Install

**Claude Code:**

```bash
claude plugin marketplace add camunda/camunda-7-to-8-migration-tooling
claude plugin install camunda-migration
```

**Other agents:**

Copy the skill file into your agent's skills directory. Most agents look for skills under `.agents/skills/` in the project or `~/.agents/skills/` globally:

Check your agent's documentation for its exact skills directory path.

## Use

From your Camunda 7 project directory:

```
/camunda-migration:migrate-c7-to-c8
```

The skill will assess your codebase and walk you through three options:

| Approach | What it does |
|----------|-------------|
| **OpenRewrite + AI** *(recommended)* | Runs OpenRewrite recipes for bulk transforms, then AI resolves remaining TODOs, config, and test code |
| **AI only** | AI migrates everything directly — for non-Maven/Gradle builds or when you want to review every change |
| **Assessment only** | Scans the codebase and reports files, complexity, and effort estimate — no code changes |

The skill fetches the latest [pattern catalog](../code-conversion/patterns/ALL_IN_ONE.md) at runtime.

## Structure

```
skills/
└── migrate-c7-to-c8/
    └── SKILL.md    ← skill definition (agentskills.io format)
```

## License

[Camunda License 1.0](../CAMUNDA-LICENSE-1.0.txt)
