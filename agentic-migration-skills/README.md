# Agentic Migration Skills

[Agent Skills](https://agentskills.io/) for migrating Camunda 7 projects to Camunda 8 — both Java code and BPMN/DMN models. The skill is written in intent-first, platform-agnostic terms so compatible AI coding agents can adapt execution to Windows, macOS, or Linux.

## Install

The skill can be installed via native plugin marketplaces (Claude Code, GitHub Copilot) or via [`gh skill`](https://cli.github.com/manual/gh_skill_install) (GitHub CLI 2.90+), which supports 50+ agents.

### Claude Code

```bash
claude plugin marketplace add camunda/camunda-7-to-8-migration-tooling
claude plugin install camunda-migration
```

### GitHub Copilot CLI

```bash
# Plugin marketplace
copilot plugin marketplace add camunda/camunda-7-to-8-migration-tooling
copilot plugin install camunda-migration@camunda

# OR: gh skill (same result)
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code
```

### All other agents — `gh skill install`

`gh skill install` works with all agents below. Use `--scope user` to install globally instead of project-scoped.

```bash
# Cursor
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent cursor

# OpenAI Codex CLI
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent codex

# Gemini CLI
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent gemini-cli

# Cline (VS Code)
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent cline

# Roo Code
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent roo

# Windsurf
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent windsurf

# Amp (Sourcegraph)
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent amp

# OpenHands
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent openhands

# Continue.dev
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent continue

# Goose (Block)
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent goose

# Junie (JetBrains)
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent junie

# Kiro CLI (AWS)
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent kiro-cli

# Kimi Code CLI
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent kimi-cli

# Qwen Code
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent qwen-code

# OpenCode
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent opencode

# Kilo Code
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent kilo

# Devin
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent devin

# Grok
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent grok

# Warp
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent warp

# Augment Code
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent augment

# Trae
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent trae

# Replit
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent replit

# Zencoder
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent zencoder
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
support-matrix.json
skills/
└── migrate-c7-to-c8-code/
    └── SKILL.md    ← skill definition (agentskills.io format)
```

## License

[Camunda License 1.0](../CAMUNDA-LICENSE-1.0.txt)
