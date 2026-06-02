# Investigation — Row #2: JobEntity.lastUpdateTime

## Job
Read the code that writes this field and the code that reads it.
Produce the four Phase B items from scratch.

Then audit NULLABILITY-status.md row #2 and any per-field doc against
your findings. Treat them as evidence, not briefing.

See _PROMPT-PREAMBLE.md for options, principles, and deliverable shape.

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
   The Phase B report is INVALID if it contains [ASSUMPTION] tags on
   load-bearing claims. Stop and read the missing path before submitting.

4. **Falsifier per claim.** After each [FINDING], one sentence:
   "This is wrong if: <concrete code-level condition>."
   If you cannot state a falsifier, the claim isn't a finding — demote
   it to [ASSUMPTION] and read further.

5. **Stop-and-flag.** If your reasoning requires a fact you haven't
   directly read, do not infer it. Stop, read it, and continue. If
   reading it is out of scope, stop the whole report and say what's
   missing — do not paper over the gap.

## Anti-patterns from prior runs (do not repeat)

- "The job's terminal state is COMPLETED, so any timestamp is equally
  last" — this reasoned about C8 row state to conclude something about
  C7 log timestamps. Different layers; the inference doesn't hold.
- "Returns logs in fetch order (verifiable, but I haven't read that
  path yet)" appearing only in a retraction — the unverified hedge
  must surface in the original report, not after challenge.