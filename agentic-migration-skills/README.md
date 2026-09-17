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

The skill asks what to migrate — **code**, **models**, or **both** — then guides you through the approaches for each.

### Scope and modes

The skill separates assessment, model analysis, model conversion, and complete migration.

| Goal | Select or ask for | Result |
|---|---|---|
| Inventory a Camunda 7 project | **Assessment only** | The skill inventories code and models. It changes no project files. |
| Analyze BPMN/DMN models | **Models only** and analyze-only | The converter reports gaps. CLI runs use `--check` and create no converted copies. |
| Convert BPMN/DMN models | **Models only** and **Diagram Converter CLI** | The converter writes `converted-c8-*` copies with JSON/XLSX findings reports. It preserves each source model. |
| Migrate Java/Spring code | **Code only** | The skill runs OpenRewrite with AI cleanup, or uses an AI-only approach. |
| Migrate code and models | **Code + models** | The skill converts models, migrates code, then cross-checks their integration. |

The Diagram Converter CLI needs Java 21 or later. See the
[Diagram Converter guide](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter/)
for its installation and CLI options. Runtime, history, and identity data migration are outside this
skill's scope.

### Code + models walkthrough

Use this workflow to move a Camunda 7 project with Java code and BPMN/DMN models toward Camunda 8:

1. Start the skill in the project directory. Choose **Code + models** and select the target Camunda 8 version.
2. Review the code and model inventory. Select **OpenRewrite + AI** for code and **Diagram Converter CLI + AI** for models.
3. The skill checks Java, downloads the CLI, and analyzes models with `--check` or writes converted model copies.
4. The skill runs OpenRewrite, resolves remaining code work, and cross-checks the code with the converted models.
5. Review `MIGRATION_REPORT.md`, resolve findings that need a decision, and run the recorded validation checks.

### Model recommendation

The skill recommends a model built for complex reasoning. Example identifiers are `claude-sonnet-*`, `claude-opus-*`, `gpt-5.6-luna`, `gpt-5.6-terra`, and `gpt-5.6-sol`. These are routing hints, not a benchmark. At activation the skill warns you about a lightweight or unverified model. You can then switch models or continue with extra review. The skill never changes the model, and it never claims to have changed it.

**Code migration:**

| Approach | What it does |
|----------|-------------|
| **OpenRewrite + AI** *(recommended)* | Runs OpenRewrite recipes for the bulk transforms, then AI resolves the remaining TODOs, config, and test code |
| **AI only** | AI migrates everything directly. Use it for a non-Maven/Gradle build, or when you want to review every change |
| **Assessment only** | Scans the codebase and reports the files, the complexity, and an effort estimate. No code changes |

**Model migration (BPMN/DMN):**

| Approach | What it does |
|----------|-------------|
| **[Diagram Converter CLI](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter/)** *(recommended)* | Downloads the official converter CLI from GitHub releases. Runs it locally against your diagrams, for your Camunda 8 version. Deterministic. Produces converted files plus a JSON/XLSX analysis. Needs Java 21+ |
| **Agentic AI** | AI rewrites the BPMN/DMN XML directly. Use it when Java 21 is unavailable, or when you want to review every change |
| **Online converter** | Opt out to the hosted [diagram-converter.camunda.io](https://diagram-converter.camunda.io/). No local Java needed |

For Camunda 7 Generated Task Forms (`camunda:formData`/`formField` and legacy `formProperty`), the
Diagram Converter leaves a manual finding on purpose. The skill reads the original BPMN metadata and
generates deterministic standard Camunda 8 `.form` files. It presents every semantic gap for your
review, and it links and deploys only the forms you accept.
Every other Camunda 7 form type is *referenced* rather than defined on the element. The skill reads
each `camunda:formKey` from the original BPMN and classifies it as embedded HTML/JavaScript, a
Camunda Form, an external or custom application, or a runtime expression. It classifies each
`camunda:formRef` as a Camunda Form reference. It inventories every affected user task and start
event, including the process-level none start events that had no form at all. It then asks one
decision per integration group inside each category. It relinks a Camunda Form by form id. For every
other category it offers to rebuild the form as a Camunda 8 form, and it generates one only when you
ask. A rebuilt form reproduces the data contract, not the Camunda 7 user interface. The skill never
reports a copied form-key reference as a completed migration.

If the project root holds no BPMN/DMN model, the skill can offer the Camunda 7 engine REST API as a
source. It asks for a reachable Camunda 7 REST URL and the required authentication, saves the original
definitions, then runs the Diagram Converter locally. While local models exist, it does not offer or
request engine access. REST supports optional Basic authentication. It can fetch all latest BPMN/DMN
definitions or multiple named definitions. Database-only access and OIDC access need a separately
supported extractor.

At run time the skill fetches only the [pattern-catalog files required by the code
inventory](skills/migrate-c7-to-c8-code/references/pattern-catalog-sources.md), with `ALL_IN_ONE.md`
as a fallback. It also fetches the diagram-converter docs and resolves the latest Diagram Converter
CLI release automatically. It describes what the agent must inspect, download, and run, instead of
prescribing one shell dialect.

## Structure

```
plugin.json                                ← Copilot CLI plugin manifest
skills/
└── migrate-c7-to-c8-code/
    ├── SKILL.md                           ← skill definition (agentskills.io format)
    └── references/                        ← procedures loaded on demand
fixtures/                                  ← sample projects for manual regression walkthroughs
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
