# Investigation prompt — Row #9: `DecisionInstanceEntity.decisionDefinitionType`

## Your job

Read the migrator code that writes this field and the C8 read path that
consumes it. Produce the four Phase B items (root cause, data in scope,
implementation cost per option, user-facing impact) from scratch — derived
from code, not from existing docs.

Then compare your findings against `NULLABILITY-status.md` row #9 and any
per-field doc. Treat those docs as evidence to be audited, not briefing to
be trusted. Flag every discrepancy.

See `_PROMPT-PREAMBLE.md` for methodology, options, principles, and
deliverable shape.


### Output rules — non-negotiable

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

## Anti-patterns from prior runs (do not repeat)

- "The job's terminal state is COMPLETED, so any timestamp is equally
  last" — this reasoned about C8 row state to conclude something about
  C7 log timestamps. Different layers; the inference doesn't hold.
- "Returns logs in fetch order (verifiable, but I haven't read that
  path yet)" appearing only in a retraction — any unverified hedge
  must surface in the original report, not after challenge.
