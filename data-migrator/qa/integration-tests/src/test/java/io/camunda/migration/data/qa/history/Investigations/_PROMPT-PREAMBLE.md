# Investigation prompt preamble

Shared methodology for the per-case nullability investigation prompts in
this folder. Every per-case prompt assumes you've read this file first.

## Where you are

The Camunda 7-to-8 data migrator has 12 documented cases where the
migrator writes null to fields that the C8 search API contract requires
to be non-null. The full catalogue lives in:

- `data-migrator/qa/integration-tests/src/test/java/io/camunda/migration/data/qa/history/entity/NULLABILITY-status.md`
  — the table of 12 cases and their statuses.
- `data-migrator/qa/integration-tests/src/test/java/io/camunda/migration/data/qa/history/entity/NULLABILITY-resolution.md`
  — the in-progress decision document you're contributing to.

One case (#11 `IncidentEntity.errorMessage`) is decided. The other 11
are undecided. Each per-case prompt in this folder is scoped to one of
those 11. Your job is to investigate one row, never more.

## The five resolution options

Defined fully in `NULLABILITY-resolution.md`. Summary:

1. **Do nothing** — accept the current state.
2. **Add a canary test** — pin foreign-side coercion (LEFT JOIN
   hydration, type handler) so a future C8-side regression surfaces
   as a clear failure here rather than as a cryptic NPE downstream.
3. **Migrator-side default** — write a sensible non-null value at
   migration time, removing the dependency on read-side coercion or
   upstream contract changes.
4. **Negotiate with C8 API team** — request a sanctioned placeholder,
   a relaxed nullability annotation, or a partial-retrieval mode.
5. **Skip migrating rows with null values** — omit the row from the
   migration entirely rather than writing it with a null. Loses data
   but avoids fabricating values; defensible when the row is meaningless
   without the missing field.

These are not mutually exclusive; option 4 can run in parallel with
option 3 as a validation pass.

## Your two-phase task

### Phase B — pre-work audit

Before proposing any option, verify the pre-work supports a confident
decision. Pre-work is **sufficient** if all four are documented (in the
dedicated `NULLABILITY-<field>.md` doc if one exists, in the status doc,
or via your own direct code inspection that you record):

1. **Root cause.** Which code path writes null, under what condition,
   and why.
2. **Data in scope at the write point.** What fields/values are
   accessible to the migrator that could substitute for null. Include
   any edge cases that constrain availability (e.g., depends on C7
   history level being `FULL`).
3. **Implementation cost per viable option.** Rough estimate per option
   that isn't "do nothing" — a few lines vs an architectural change.
4. **User-facing impact.** Either documented, or explicitly flagged as
   needing a product / UX call.

**If pre-work is INSUFFICIENT:**

- Do the missing investigation yourself: read the migrator source, find
  the null write, trace what data is in scope at that point, enumerate
  edge cases.
- Write the findings into a new file
  `data-migrator/qa/integration-tests/src/test/java/io/camunda/migration/data/qa/history/entity/NULLABILITY-<field>.md`,
  following the structure of the existing `NULLABILITY-errorMessage.md`
  / `NULLABILITY-decisionDefinitionName.md` (trigger → reproduction →
  why it does/doesn't reproduce → question for C8 if relevant).
- **STOP.** Do not propose a resolution option in this run. Return a
  short report saying "Phase B done; pre-work now sufficient for a
  follow-up Phase C run." The user will spawn a follow-up if they want
  the option proposed.

**If pre-work is SUFFICIENT:**

- Proceed to Phase C.

### Phase C — resolution proposal

Only after Phase B confirms sufficiency:

- Recommend ONE of the five options.
- Justify briefly using verified facts only. No rhetoric.
- Propose (do not apply) the edit to `NULLABILITY-resolution.md`:
  - Which column gets `✅` in the table for your row.
  - What the per-case rationale section should say.

**Do not apply the edit yourself.** Other agents may be running on
other rows in parallel; the user serialises edits after reviewing
each report.

## Principles — non-negotiable

The session that produced the current state of `NULLABILITY-resolution.md`
failed by ignoring these. Hold the line.

### Don't assume

- Verify every factual claim against code, data, or docs. Do not state
  facts you haven't independently checked.
- If verification isn't cheap, mark the claim as unverified in your
  output — don't smooth over the gap with confident phrasing.
- "I can articulate X" is not the same as "X is correct."

### Honour the scope

- Investigate only your assigned row. Do not extrapolate to other rows.
- If you notice something relevant elsewhere, note it for the user;
  do not act on it.

### No bulk-apply

- Do not generalise from one verified fact to a pattern across cases.
- A generalisable rule, if there is one, emerges from multiple
  independent investigations — not from one.

### Earn each fact

- A fact earns its place in your output only if the reader would
  otherwise have to ask the question to understand the recommendation.
- Strip rhetoric, process metadata, and speculative defences.

### Calibrate confidence

- Reserve unhedged language ("always", "never", "all") for things you
  have actually enumerated. Otherwise hedge honestly.

## Deliverable shape

A short report:

1. **Phase B verdict.** Sufficient or insufficient. If sufficient,
   briefly check off the four items (1-2 sentences each).
2. **If insufficient:** location of the new `NULLABILITY-<field>.md` doc
   you wrote, summary of the gaps you filled, and "stop — handed off
   to Phase C in a follow-up run."
3. **If sufficient:** the proposed option, supporting facts (cited
   `file:line`), and the exact replacement text for the row's table
   cell and rationale section in `NULLABILITY-resolution.md`.

Keep the report concise. If you're unsure about any fact, say so rather
than smoothing it over.
