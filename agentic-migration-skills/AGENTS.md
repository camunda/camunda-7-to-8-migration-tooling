# AGENTS.md — agentic-migration-skills

Repository-wide guidance: [../AGENTS.md](../AGENTS.md).

This file defines how to write the text inside `skills/`. The text is the product: an agent reads it
and has no way to ask which reading was meant. Author it against these conventions instead of taste.

## Scope

| Path | Audience | Conventions to apply |
| --- | --- | --- |
| `skills/*/SKILL.md` | agent, loaded on every invocation | all of them; this file is the token hot spot |
| `skills/*/references/*.md` | agent, loaded on demand | all of them |
| `README.md` | human | sentence discipline only; no EARS, no RFC 2119 keywords |
| `fixtures/**` | human and agent | keep consistent with the skill text they exercise |

## 1. Obligation is declared once, not repeated

An agent reads this text as instructions. Almost every sentence is therefore mandatory, so marking
each one is pure overhead. A measured rewrite that prefixed about 60 rules with "The skill MUST"
made `SKILL.md` **larger**, not smaller.

Declare the convention once at the top of the file, then write plain imperatives:

> Every instruction is mandatory. "Never" means MUST NOT. A preference is marked (SHOULD) and an
> option is marked (MAY).

| Force | How to write it | Example |
| --- | --- | --- |
| Hard constraint | plain imperative | Never commit without an explicit user request. |
| Preference with a stated exception | imperative + `(SHOULD)` | Prefer the deterministic path. (SHOULD) |
| Permission | imperative + `(MAY)` | Keep an imported report. (MAY) |

The [RFC 2119](https://www.rfc-editor.org/rfc/rfc2119) and
[RFC 8174](https://www.rfc-editor.org/rfc/rfc8174) meanings still apply. Only the notation is
shorter. A hard constraint must stay distinguishable from a preference.

Statements of fact take no marker. "The converter writes a new file beside the source" is background,
not an obligation.

## 2. EARS gives conditional rules their shape

Write each conditional rule in one [EARS](https://alistairmavin.com/ears/) pattern. Omit repeated
obligation keywords. Keep the actor and subject explicit.

| Pattern | Template |
| --- | --- |
| Ubiquitous | `<response>` |
| Event-driven | When `<trigger>`, `<response>` |
| State-driven | While `<state>`, `<response>` |
| Unwanted behaviour | If `<condition>`, then `<response>` |
| Optional feature | Where `<feature is present>`, `<response>` |

Reserve **If … then** for the unwanted case, **When** for a trigger that is expected to occur, and
**Where** for a configuration or an environment that may or may not apply. Keeping the three apart
tells the reader whether a branch is normal or exceptional.

Do not force a pattern onto an unconditional statement. "Never hand-edit BPMN in the code flow" needs
no wrapper.

## 3. Decision logic goes in a table

Classification rules, per-category verdicts, and approach selection are decision logic. A table
states them with one column per input and one column for the outcome, and it is much harder to
misparse than a paragraph. Order the rows when the order of evaluation matters.

**A table is a readability tool here, not a compression tool.** Measured on this corpus, each table
row costs about 6 to 8 tokens of pipes and structure. A table wins against a prose paragraph. It
loses against a terse bullet list. So convert a paragraph of decision logic into a table, and leave
an existing bullet list alone.

Prose is for the reasoning a table cannot hold: why a rule exists, what breaks without it.

## 4. Sentence discipline

Adapted from [ASD-STE100](https://www.asd-ste100.org/). The approved-word dictionary is aerospace
vocabulary and is not redistributable, so this repository keeps the structural rules only.

1. One rule per sentence. Split a bullet that carries two or three rules.
2. Active voice with a named actor: the skill, the converter, the CLI, the user.
3. Simple tenses.
4. No phrasal verb where one plain verb exists: "start", not "spin up"; "remove", not "take off".
5. At most 20 words for an instruction, and at most 25 for an explanation.
6. No semicolon. Write two sentences.
7. At most three words in a noun cluster.
8. Keep the subject, the verb, and the article. Do not drop words to save space.
9. One term per concept. Never rotate synonyms — see the glossary below.
10. Keep every hedge that carries a condition. "The report can be stale" is a fact about the world,
    not padding.

## 5. Cut these

| Cut | Why |
| --- | --- |
| Hedges and intensifiers with no condition: `proactively`, `simply`, `note that`, `it is important to` | They assert nothing |
| Meta-commentary on phrasing: "use explicit question text such as …" | Show the example instead |
| A restatement of a rule stated elsewhere | Cross-reference the one place that owns it |
| A sentence that only announces the next sentence | It costs tokens and adds no rule |

## 6. Glossary — one term per concept

| Use | Do not use |
| --- | --- |
| converted copy, `converted-c8-*` file | converted output, converted diagram, converted model file |
| finding | issue, item, result, row (when a finding is meant) |
| category | group, bucket, family |
| verdict | status (when a verdict is meant), outcome, resolution |
| owner (the user task or start event that holds a form) | element, task, host |
| form-free owner (a user task or a process-level none start event with no form metadata at all) | the long phrase, spelled out again |
| the skill | the agent, you, we |
| `MIGRATION_REPORT.md` | the report (when the file is meant), migration notes |

`analysis report` and `findings report` both mean the Diagram Converter output. Use **findings
report** when the findings matter, and **XLSX report** or **JSON report** when the format matters.

Define a compound term once per file, because a reference file can be read on its own.

## 7. Precision beats brevity

No fact, condition, scope qualifier, or exception may be dropped to shorten a sentence. Where a
shorter phrasing would lose precision, keep the longer phrasing and say so in the pull request.

Never promote a hedge to a fact. "The report can be stale" and "the report is stale" are different
claims, and a length cap is the most common reason a rewrite silently swaps one for the other.

## 8. Before you open the pull request

- [ ] Every rule you touched is still present, one rule per sentence.
- [ ] Every preference carries `(SHOULD)` and every option carries `(MAY)`. Everything else reads as
      mandatory.
- [ ] Every new classification rule is a table. Convert existing decision logic to a table when it
      improves readability, not only to reduce tokens.
- [ ] No new synonym for a glossary term.
- [ ] `README.md`, `plugin.json`, `skills/`, and `fixtures/` still agree with each other.
- [ ] The fixture walkthroughs still describe what the skill does.
- [ ] You measured the token count before and after, and you reported the honest figure.
