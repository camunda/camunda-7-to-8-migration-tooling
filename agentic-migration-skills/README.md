# Agentic Migration Skills

[Agent Skills](https://agentskills.io/) for migrating Camunda 7 projects to Camunda 8 — both Java code and BPMN/DMN models. The skill is written in intent-first, platform-agnostic terms so compatible AI coding agents can adapt execution to Windows, macOS, or Linux.

## Install

**Claude Code:**

```bash
claude plugin marketplace add camunda/camunda-7-to-8-migration-tooling
claude plugin install camunda-migration
```

**GitHub Copilot CLI** (plugin marketplace — requires [GitHub CLI](https://cli.github.com/) 2.90+ for `gh skill`, or the Copilot CLI):

```bash
# Plugin marketplace (works with Copilot CLI)
copilot plugin marketplace add camunda/camunda-7-to-8-migration-tooling
copilot plugin install camunda-migration@camunda

# OR: gh skill (GitHub CLI, public preview, works without extra setup)
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code
```

**Other Tier 1 harnesses** (OpenAI Codex, Cursor, Gemini CLI, Cline):

Use the open skills installer:

```bash
npx skills add https://github.com/camunda/camunda-7-to-8-migration-tooling/tree/main/agentic-migration-skills/skills/migrate-c7-to-c8-code \
  -a codex -a cursor -a gemini-cli -a cline
```

Install to user scope with `-g`. Install to one harness by keeping a single `-a`.

### Tier 1 harness matrix

| Harness | Status | Install | Project path | User path |
|---|---|---|---|---|
| **Claude Code** | ✅ supported | `claude plugin marketplace add` | `.claude/skills/migrate-c7-to-c8-code` | `~/.claude/skills/migrate-c7-to-c8-code` |
| **GitHub Copilot** | ✅ supported | `copilot plugin marketplace add` / `gh skill install` | `.agents/skills/migrate-c7-to-c8-code` | `~/.copilot/skills/migrate-c7-to-c8-code` |
| **OpenAI Codex** | ✅ supported | `npx skills add -a codex` | `.agents/skills/migrate-c7-to-c8-code` | `~/.codex/skills/migrate-c7-to-c8-code` |
| **Cursor** | ✅ supported | `npx skills add -a cursor` | `.agents/skills/migrate-c7-to-c8-code` | `~/.cursor/skills/migrate-c7-to-c8-code` |
| **Gemini CLI** | ✅ supported | `npx skills add -a gemini-cli` | `.agents/skills/migrate-c7-to-c8-code` | `~/.gemini/skills/migrate-c7-to-c8-code` |
| **Cline** | ✅ supported | `npx skills add -a cline` | `.agents/skills/migrate-c7-to-c8-code` | `~/.agents/skills/migrate-c7-to-c8-code` |

### Manual installation

Copy the skill directory into your agent's skills directory. Most agents look for skills under `.agents/skills/` in the project or `~/.agents/skills/` globally. Check your agent's documentation for its exact skills directory path.

### Support metadata validation

Validate tier 1 support metadata and docs consistency:

```bash
node agentic-migration-skills/scripts/validate-tier1-support.js
```

## Use

From your Camunda 7 project directory:

```
/camunda-migration:migrate-c7-to-c8-code
```

The skill asks what to migrate — **code**, **models**, or **both** — then walks you through the approaches for each.

**Code migration:**

| Approach | What it does |
|----------|-------------|
| **OpenRewrite + AI** *(recommended)* | Runs OpenRewrite recipes for bulk transforms, then AI resolves remaining TODOs, config, and test code |
| **AI only** | AI migrates everything directly — for non-Maven/Gradle builds or when you want to review every change |
| **Assessment only** | Scans the codebase and reports files, complexity, and effort estimate — no code changes |

**Model migration (BPMN/DMN):**

| Approach | What it does |
|----------|-------------|
| **Diagram Converter CLI** *(recommended)* | Downloads the official converter CLI from GitHub releases and runs it locally against your diagrams, targeting your C8 version. Deterministic; produces converted files + CSV/XLSX analysis. Requires Java 21+ |
| **Agentic AI** | AI rewrites the BPMN/DMN XML directly — for when Java 21 is unavailable or you want to review every change |
| **Online converter** | Opt out to the hosted [diagram-converter.camunda.io](https://diagram-converter.camunda.io/) — no local Java needed |

The skill fetches the latest [pattern catalog](../code-conversion/patterns/ALL_IN_ONE.md) and diagram-converter docs at runtime, resolves the latest Diagram Converter CLI release automatically, and describes what the agent should inspect/download/run rather than prescribing a POSIX shell dialect.

## Structure

```
scripts/
└── validate-tier1-support.js
support-matrix.json
skills/
└── migrate-c7-to-c8-code/
    └── SKILL.md    ← skill definition (agentskills.io format)
```

## License

[Camunda License 1.0](../CAMUNDA-LICENSE-1.0.txt)
