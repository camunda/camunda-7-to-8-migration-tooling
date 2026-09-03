# Agentic Migration Skills

[Agent Skills](https://agentskills.io/) for migrating Camunda 7 projects to Camunda 8 — both Java code and BPMN/DMN models. The skill is written in intent-first, platform-agnostic terms so compatible AI coding agents can adapt execution to Windows, macOS, or Linux.

## Install

### Claude Code

```bash
claude plugin marketplace add camunda/camunda-7-to-8-migration-tooling
claude plugin install camunda-migration
```

### GitHub Copilot CLI

```bash
copilot plugin marketplace add camunda/camunda-7-to-8-migration-tooling
copilot plugin install camunda-migration@camunda
```

### All other agents

Use [`gh skill install`](https://cli.github.com/manual/gh_skill_install) (GitHub CLI 2.90+) — see [install commands for 22 agents](#install-commands-for-other-agents) at the bottom of this file.

### Manual installation

Copy the skill directory into your agent's skills directory:

| Scope | Path |
|---|---|
| Project | `.agents/skills/migrate-c7-to-c8-code` (Copilot, Cursor, Codex, Gemini CLI, Cline, and others) |
| Project | `.github/skills/migrate-c7-to-c8-code` (GitHub Copilot) |
| Project | `.claude/skills/migrate-c7-to-c8-code` (Claude Code) |
| User | `~/.copilot/skills/migrate-c7-to-c8-code` (GitHub Copilot) |
| User | `~/.claude/skills/migrate-c7-to-c8-code` (Claude Code) |
| User | `~/.agents/skills/migrate-c7-to-c8-code` (generic fallback) |

Check your agent's documentation for its exact skills directory.

## Use

From your Camunda 7 project directory:

```
/camunda-migration:migrate-c7-to-c8-code
```

The skill asks what to migrate — **code**, **models**, or **both** — then walks you through the approaches for each.

### Model recommendation

The skill recommends a model intended for complex reasoning, with illustrative IDs such as `claude-sonnet-*`, `claude-opus-*`, `gpt-5.6-luna`, `gpt-5.6-terra`, or `gpt-5.6-sol`. These are routing hints, not a benchmark. At activation, it warns about lightweight or unverifiable models and lets the user switch or continue with extra review; it never changes or claims to have changed the model.

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

For Camunda 7 Generated Task Forms (`camunda:formData`/`formField` and legacy
`formProperty`), the Diagram Converter deliberately leaves a manual finding. The skill uses the
original BPMN metadata to generate deterministic standard Camunda 8 `.form` files, presents every
semantic gap for user review, and links/deploys only explicitly accepted forms.

Every other Camunda 7 form type is *referenced* rather than defined on the element. The skill
classifies each `camunda:formKey` from the original BPMN — embedded HTML/JavaScript, Camunda Form,
external/custom application, or runtime expression — and every `camunda:formRef` as a Camunda Form
reference. It inventories every affected user task and start event alongside those that had no form
at all, and asks one decision per category. Camunda Forms are relinked by form id. For the rest it
offers to rebuild the form as a Camunda 8 form and generates one only on explicit request,
reproducing the data contract rather than the Camunda 7 user interface. A copied form-key reference
is never reported as a completed migration.

If no BPMN/DMN model is found under the project root, the skill can offer C7 engine REST as a source. It asks for a reachable C7 REST URL and the required authentication, persists the original definitions, then runs the Diagram Converter locally; when local models are present, it does not offer or request engine access. REST supports optional Basic authentication and latest or named BPMN/DMN definitions; database-only and OIDC access require a separately supported extractor.

The skill fetches the latest [pattern catalog](../code-conversion/patterns/ALL_IN_ONE.md) and diagram-converter docs at runtime, resolves the latest Diagram Converter CLI release automatically, and describes what the agent should inspect/download/run rather than prescribing a POSIX shell dialect.

## Structure

```
plugin.json                         ← Copilot CLI plugin manifest
skills/
└── migrate-c7-to-c8-code/
    └── SKILL.md    ← skill definition (agentskills.io format)
```

## License

[Camunda License 1.0](../CAMUNDA-LICENSE-1.0.txt)

---

## Install commands for other agents

Use `--scope user` to install globally instead of project-scoped.

```bash
# Amp (Sourcegraph)
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent amp

# Augment Code
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent augment

# Cline (VS Code)
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent cline

# Continue.dev
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent continue

# Cursor
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent cursor

# Devin
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent devin

# Gemini CLI
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent gemini-cli

# Goose (Block)
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent goose

# Grok
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent grok

# Junie (JetBrains)
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent junie

# Kilo Code
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent kilo

# Kimi Code CLI
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent kimi-cli

# Kiro CLI (AWS)
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent kiro-cli

# OpenAI Codex CLI
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent codex

# OpenCode
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent opencode

# OpenHands
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent openhands

# Qwen Code
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent qwen-code

# Replit
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent replit

# Roo Code
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent roo

# Trae
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent trae

# Warp
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent warp

# Zencoder
gh skill install camunda/camunda-7-to-8-migration-tooling migrate-c7-to-c8-code --agent zencoder
```
