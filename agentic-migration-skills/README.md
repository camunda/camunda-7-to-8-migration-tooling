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
| Project | `.aws/atx/skills/migrate-c7-to-c8-code` (AWS Transform CLI v2+) |
| Project | `.agents/skills/migrate-c7-to-c8-code` (AWS Transform CLI v2+, Copilot, Cursor, Codex, Gemini CLI, Cline, and others) |
| Project | `.github/skills/migrate-c7-to-c8-code` (GitHub Copilot) |
| Project | `.claude/skills/migrate-c7-to-c8-code` (Claude Code) |
| User | `~/.aws/atx/skills/migrate-c7-to-c8-code` (AWS Transform CLI v2+) |
| User | `~/.copilot/skills/migrate-c7-to-c8-code` (GitHub Copilot) |
| User | `~/.claude/skills/migrate-c7-to-c8-code` (Claude Code) |
| User | `~/.agents/skills/migrate-c7-to-c8-code` (AWS Transform CLI v2+, generic fallback) |

Check your agent's documentation for its exact skills directory.

### AWS Transform custom

[AWS Transform custom](https://docs.aws.amazon.com/transform/latest/userguide/custom.html) CLI v2+
discovers the skill as a [client-side skill](https://docs.aws.amazon.com/transform/latest/userguide/custom-workflows.html#custom-client-side-skills)
in the AWS Transform paths above. It loads project paths only when it has a code repository path.
To run the skill as a transformation definition, use `skills/migrate-c7-to-c8-code/` as the source,
not this package root. A definition contains only `SKILL.md` and optional `references/` and
`scripts/` directories. Execution needs a Git repository. See the
[command reference](https://docs.aws.amazon.com/transform/latest/userguide/custom-command-reference.html)
for current flags.

```bash
# From this repository's root. save-draft returns the draft version. Drafts expire after 30 days.
atx custom def save-draft -n camunda-7-to-camunda-8-migration \
  --description "Migrate Camunda 7 projects to Camunda 8" \
  --sd agentic-migration-skills/skills/migrate-c7-to-c8-code/
# Test the draft on a Camunda 7 Git repository, then publish the tested version to the registry.
atx custom def exec -n camunda-7-to-camunda-8-migration --tv <draft-version> \
  -p /path/to/camunda-7-project -c "<build-command>"
atx custom def publish -n camunda-7-to-camunda-8-migration --tv <draft-version>
```

Run the draft interactively while scope or migration decisions need input. A `--non-interactive`
(`-x`) run needs every decision in the request or configuration, including acceptance of any
default. Otherwise, the skill stops before dependent changes and records the missing choices as
open items. Until the project root is confirmed, it reports them only in its response.
`--trust-all-tools` (`-t`) approves tool requests, not migration decisions.

For dashboard campaign reporting, run the command that the AWS Transform web application generates,
with its campaign and repository arguments. The
[setup guide](https://docs.aws.amazon.com/transform/latest/userguide/custom-get-started.html#custom-web-application)
states that this command logs execution results to the web application. Do not assume that a draft
test run reports to a campaign.

At the end of a run, the skill reports changed files and added and deleted lines per asset type. It
measures them with Git from the clean starting commit, or marks them unavailable instead of
estimating. It shows them in the `atx` conversation and in `MIGRATION_REPORT.md`. These counts are
local run data, not AWS dashboard metrics. AWS documents no result schema or CLI option to submit
them.

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
| Inventory a Camunda 7 project | **Assessment only** | The skill inventories code and models. It writes `MIGRATION_REPORT.md`, but does not edit source code or models. |
| Analyze BPMN/DMN models | **Models only**, **Diagram Converter CLI** or **Agentic AI**, then **Analyze-only** | The skill reports gaps without editing source models. The CLI uses `--check`. Agentic AI uses a read-only pass. |
| Convert BPMN/DMN models | **Models only** | Select the Diagram Converter CLI (recommended), Agentic AI, or Online Converter. Each path preserves source models and produces reviewable converted copies. |
| Migrate Java/Spring code | **Code only** | The skill uses a pattern-guided AI-first approach, or a recipe-assisted OpenRewrite + AI approach. |
| Migrate code and models | **Code + models** | The skill converts models, migrates code, then cross-checks their integration. |

Analyze-only is available only with **Models only** and the Diagram Converter CLI or Agentic AI.
Select **Assessment only** to inspect code and models without editing source code or models.

The Diagram Converter CLI needs Java 21 or later. See the
[Diagram Converter guide](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter/)
for its installation and CLI options. Runtime, history, and identity data migration are outside this
skill's scope.

### Code + models walkthrough

Use this workflow to move a Camunda 7 project with Java code and BPMN/DMN models toward Camunda 8:

1. Start the skill in the project directory. Choose **Code + models** and select the target Camunda 8 version.
2. Review the code and model inventory. Select a code path based on the code shape and model capability.
3. Select **Diagram Converter CLI + AI** for models when Java 21+ is available. Otherwise, select **Agentic AI** or **Online Converter**.
4. The selected local model path writes converted copies. The CLI path checks Java and downloads the converter first.
5. For the Online Converter, upload the diagrams, download the converted copies, and bring them back to the project.
6. After converted model copies are available, the skill applies the selected code path, resolves
   remaining code work, and cross-checks the code with the models.
7. Review `MIGRATION_REPORT.md`, resolve findings that need a decision, and run the recorded validation checks.

### Model recommendation

The skill recommends a model built for complex reasoning. Example identifiers are `claude-sonnet-*`, `claude-opus-*`, `gpt-5.6-luna`, `gpt-5.6-terra`, and `gpt-5.6-sol`. These are routing hints, not a benchmark. At activation the skill warns you about a lightweight or unverified model. You can then switch models or continue with extra review. The skill never changes the model, and it never claims to have changed it.

**Code migration:**

| Approach | What it does |
|----------|-------------|
| **AI only (AI-first)** *(recommended with a capable coding model)* | The skill applies the pattern catalog directly to the source. Use it for semantic, mixed, or complex Java code. Model quality affects the result. |
| **OpenRewrite + AI** | Runs recipes for repeated, supported syntax transformations, then AI cleans and reviews the generated code. Expect scaffolding, TODOs, and cleanup. |
| **Assessment only** | Scans the codebase and reports the files, the complexity, and an effort estimate. No code changes |

Compare the code paths on representative classes when practical. Recipes help with repeated,
well-supported syntactic changes. They can hurt by adding cleanup for semantic or mixed
delegate/client code.
They do not decide domain behavior, eventual consistency, transaction boundaries, or architecture.
Review and validation remain mandatory for both migration paths.

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

Before deployment, the skill inventories recurring timer starts and process IDs across the intended
deployment set. It traces `latestVersion()` callers and active timer due-date updates. Unresolved
timer or process-ID decisions keep the migration blocked.

For agentic model migration targeting Camunda 8.5 and later, every Camunda 7 user task becomes a
Camunda 8 user task by default. This includes form-free tasks. The skill preserves compatible
assignments, schedules, forms, and listeners, records unsupported semantics, and does not create a
legacy user-task job. A job-based replacement requires an explicit user decision.
For targets before Camunda 8.5, the skill does not apply this mapping. It preserves the source
implementation and records that modern user-task support is unavailable.
This model rule complements the code-side user-task migration tracked in #1556.

The agentic model-rewrite path preserves existing BPMN DI for unchanged semantic
IDs. When a source has no BPMN DI, the skill does not create a layout and records
that source provenance in `MIGRATION_REPORT.md`. The
[`fixtures/diagram-interchange`](fixtures/diagram-interchange) fixture checks
both cases.

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

The `fixtures/user-tasks` walkthrough covers a message-start process with a
form-free user task and a user task carrying assignment and form metadata.
The `fixtures/timer-preflight` walkthrough covers recurring starts, duplicate
process IDs across Maven modules, and active timer due-date update callers.

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
