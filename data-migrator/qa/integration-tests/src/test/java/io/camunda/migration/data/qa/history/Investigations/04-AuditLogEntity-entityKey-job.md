# Investigation prompt — Row #4: `AuditLogEntity.entityKey` (JOB)

## Your job

Read the migrator code that writes this field for the C7 `JOB` entity type
and the C8 read path that consumes it. Produce the four Phase B items
(root cause, data in scope, implementation cost per option, user-facing
impact) from scratch — derived from code, not from existing docs.

Then compare your findings against `NULLABILITY-status.md` row #4 and any
per-field doc. Treat those docs as evidence to be audited, not briefing to
be trusted. Flag every discrepancy.

See `_PROMPT-PREAMBLE.md` for methodology, options, principles, and
deliverable shape.

## Output rules — non-negotiable

1. **Read inventory.** Begin the report with a flat list:
   `path/to/file.java:LINE-LINE — why I read it`
   one line per range, in the order opened. If you cite anything later
   that isn't here, you've contradicted yourself — say so explicitly.

2. **Verbatim quotes.** Every claim about what the code does must be
   backed by a fenced code block containing the actual lines, with
   `// path/to/file.java:LINE` as the first line of the block. Not
   paraphrase. Not "the code does X" without the snippet.

3. **Assumption / Finding tags.** Tag each behavioural claim:
   - `[FINDING]` — directly read; verbatim quote follows.
   - `[ASSUMPTION]` — inferred, not read. Must include what you'd need
     to read to upgrade it, and why you didn't.
   The Phase B report is INVALID if it contains `[ASSUMPTION]` tags on
   load-bearing claims. Stop and read the missing path before submitting.

4. **Falsifier per claim.** After each `[FINDING]`, one sentence:
   "This is wrong if: <concrete code-level condition>."
   If you cannot state a falsifier, the claim isn't a finding — demote
   it to `[ASSUMPTION]` and read further.

5. **Stop-and-flag.** If your reasoning requires a fact you haven't
   directly read, do not infer it. Stop, read it, and continue. If
   reading it is out of scope, stop the whole report and say what's
   missing — do not paper over the gap.