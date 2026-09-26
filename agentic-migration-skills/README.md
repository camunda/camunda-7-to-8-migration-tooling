# Agentic Migration Skills

[Agent Skills](https://agentskills.io/) for migrating Camunda 7 projects to Camunda 8 — both Java code and BPMN/DMN models. Works with any Agent Skills-compatible AI coding agent.

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
/camunda-migration:migrate-c7-to-c8-code
```

The skill asks what to migrate — **code**, **models**, or **both** — then walks you through the approaches for each.

**Code migration:**

| Approach | What it does |
|----------|-------------|
| **AI only (AI-first)** *(recommended with a capable coding model)* | Applies migration patterns directly to the source. Use it for semantic, mixed, or complex Java code. Review every change. |
| **OpenRewrite + AI** | Runs recipes for repeated, supported syntax transformations, then AI cleans and reviews the generated code. Expect scaffolding, TODOs, and cleanup. |
| **Assessment only** | Scans the codebase and reports files, complexity, and effort estimate — no code changes |

Compare both code paths on representative classes when practical. Recipes help with repeated,
well-supported syntax changes. They can add cleanup for semantic or mixed delegate/client code.
Recipes do not decide domain behavior, eventual consistency, transaction boundaries, or architecture.
Review and validate both paths.

**Model migration (BPMN/DMN):**

| Approach | What it does |
|----------|-------------|
| **Diagram Converter CLI** *(recommended)* | Downloads the official converter CLI from GitHub releases and runs it locally against your diagrams, targeting your C8 version. Deterministic; produces converted files + CSV/XLSX analysis. Requires Java 21+ |
| **Agentic AI** | AI rewrites the BPMN/DMN XML directly — for when Java 21 is unavailable or you want to review every change |
| **Online converter** | Opt out to the hosted [diagram-converter.camunda.io](https://diagram-converter.camunda.io/) — no local Java needed |

If no BPMN/DMN model is found under the project root, the skill can offer the Diagram Converter's C7 engine source mode instead. It asks for a reachable C7 REST URL and the required authentication before fetching; when local models are present, it does not offer or request engine access. The released engine mode supports REST with optional Basic authentication and fetches latest BPMN/DMN definitions; database-only and OIDC access require a separately supported extractor.

The skill fetches the [maintenance/0.3 pattern catalog](../code-conversion/patterns/ALL_IN_ONE.md) and current Diagram Converter docs at runtime.
It resolves the latest Diagram Converter CLI release.
It describes which files and artifacts to inspect, download, and run.
It chooses commands for the current environment instead of assuming POSIX shell syntax.

## Structure

```
skills/
└── migrate-c7-to-c8-code/
    └── SKILL.md    ← skill definition (agentskills.io format)
```

The `fixtures/delegate-transaction-boundaries` path test checks C7
`camunda:asyncAfter` boundaries before a JavaDelegate.

The [`domain-license-dependency`](fixtures/domain-license-dependency) fixture checks that a
compatible active library under an `org.camunda.bpm` group survives migration. Its tests cover both
synthetic license types and the downstream membership update. Its blocked-case report shows the
blocked finding and manual follow-up required when the project owner has not approved a replacement.

## License

[Camunda License 1.0](../CAMUNDA-LICENSE-1.0.txt)
