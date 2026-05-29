# Investigation skill — best practices

Reference notes for building an agent skill that conducts multi-step
investigations (diagnose a failure, audit a contract, evaluate a set of
related cases) and produces a written artifact (decision record, analysis
doc, punch list).

## Core principles

### 1. Don't make assumptions

The first failure mode is substituting confident articulation for verified
knowledge. Being able to phrase something fluently is not the same as knowing
it's correct.

Before any factual claim lands in the artifact:

- **Verify the claim against code, data, or docs** — not against your sense
  of what's plausible. Open the file. Run the query. Check the line number.
- **If verification isn't cheap, mark the claim as unverified** in the
  artifact. Don't smooth over the gap with confident phrasing.
- **Distinguish "the docs say X" from "X is true."** Other docs in the repo
  may be stale, partial, or generalising from one case. Cross-check before
  re-asserting.

Tells that you're assuming, not knowing:

- You stated a fact in the artifact but never opened the relevant source.
- You generalised from one case to N similar-looking cases without
  independently checking the others.
- You described what a tool, library, or API does from prior knowledge
  without consulting actual behaviour.
- You used unhedged language ("always", "never", "all") about something you
  haven't enumerated.

When in doubt, the honest default is "I haven't verified this for case X"
rather than a fluent assertion that turns out to be wrong.

### 2. Iterative (agentic) mode

When an investigation spans multiple cases, or a pattern emerges from one
case, **do not bulk-apply the pattern**. Confirm scope before extending.

**Trigger iterative mode when:**
- The user agrees to a decision on case A, and case B looks structurally
  similar.
- A general principle becomes tempting ("seems like the answer is always X").
- The artifact has a table, list, or checklist where the same answer could
  be filled in for many rows.

**Behaviour in iterative mode:**
- Stay focused on the agreed scope. Do not silently generalise.
- Ask before extrapolating. Single-question prompts, one unknown at a time.
- Keep the question form-factor small to preserve context budget:
  - "Apply this conclusion to case B as well?"
  - "Should I investigate case C now, or defer?"
  - "Any cases you want me to skip entirely?"
- Default to "leave undecided" in the artifact, not "decide by analogy".
  A blank cell is more honest than a confidently-wrong cell.

**Exit iterative mode when:**
- The user explicitly broadens scope ("yes, apply to all the others").
- Investigation reveals the cases collapse into one decision (shared root
  cause, identical mechanics).

## Supporting principles

### 3. Match the artifact to the audience

A chat conversation supports rhetorical exploration: "the reason this is
better is X, Y, Z; consider the trade-off W; note that V could come up
later." A team-facing reference doc does not. Strip:

- **Process metadata** — "captured in chat", "as we discussed", "we decided
  that". The reader doesn't care where the decision came from.
- **Rhetorical defences** — preemptive justifications for choices the
  audience wouldn't naturally challenge.
- **Speculative scenarios** — "if X were to change, this would also handle
  it". Not a fact, just confidence-building.

### 4. Earn each fact

A fact earns its place in the artifact only if the reader would otherwise
have to ask the question to understand the change.

- "Why option A and not B?" — yes, answer.
- "Why is this better than the alternative we never considered?" — no,
  that's self-justification, not information.

Before writing a sentence, ask: *what question is this answering, and does
my reader have that question?*

### 5. Distinguish decision from rationale

The decision should be visible at a glance (table cell, checkmark, header
line). The rationale supports the decision but never substitutes for it.
A reader scanning the artifact for "what was decided" should not need to
parse paragraphs to find it.

### 6. Calibrate confidence in language

Use hedges in proportion to actual confidence:

- "I verified that X" — read the file, ran the test.
- "I believe X based on Y" — inferred from one source, not cross-checked.
- "X is likely but unverified" — plausible, but I haven't confirmed.
- "I don't know whether X" — say so, and offer to find out.

Reserve unhedged assertions ("X is true", "X always happens") for things
you've actually checked.

### 7. Honour the question that was asked

After an extended back-and-forth, re-read the user's last message before
responding. "Do you agree there are N options?" is not "pick one of N
options." "Can you confirm my understanding?" is not "extend my
understanding." Drift between the question and the answer is a common
failure mode in long sessions.

### 8. Hear criticism cleanly

When the user pushes back, engage with the specific point. Do not:

- Soften by re-stating their criticism in weaker form.
- Pivot to defending a related decision they didn't challenge.
- Pre-emptively apologise without acknowledging the substance.

The correct response to "you over-extended in cases B–L when we only
discussed A" is to name what you did wrong, not to re-justify any
individual case.

### 9. Cite sources in chat

When asserting a fact in chat (not the artifact), reference the file and
line. This lets the user audit cheaply and surfaces disagreements
immediately. In the artifact, citations are optional; in chat, they are
trust-builders.

## Practical patterns

### Investigation loop

For each case in scope:

1. **Read the existing material.** Source files, sibling docs, related
   tests. Note what the docs claim vs. what the code says.
2. **Form a hypothesis.** What's the failure mechanism, the data flow,
   the contract?
3. **Verify the hypothesis** against the code. If you can't verify cheaply,
   say so; do not paper over with confident phrasing.
4. **Surface the open questions.** What needs the user's input (semantic
   choice, product call, scope confirmation)? List them explicitly.
5. **Record the decision, not the deliberation.** The artifact captures
   what was decided and the supporting facts. It does not narrate the
   investigation.

### Artifact shape for a multi-case investigation

- **Table at the top:** one row per case, columns for status or options.
  Markers visible at a glance.
- **Per-case sections:** one short section per case, ordered by table.
  Each section captures *what was decided* and *the facts the reader
  would need to ask about*. Nothing else.
- **Closing section:** roll-up only if a pattern actually emerged from
  the investigation. Don't manufacture a pattern to justify having a
  closing section.

### Editing rules for the artifact

- A sentence you can delete without the reader losing information is
  clutter — delete it.
- A paragraph defending a decision against an unasked question is
  clutter — delete it.
- A reference to the investigation's process is clutter — delete it.

## Anti-patterns to flag

- **Mass-applying a pattern after one earned decision.** Each case earns
  its own decision through its own investigation.
- **Confident assertions on unverified facts.** "Always", "never", "all"
  about things you didn't enumerate.
- **Inflating the artifact to feel rigorous.** A short doc with verified
  facts beats a long doc with rhetorical bullets.
- **Conflating "I can articulate X" with "X is correct."** Fluency is
  not knowledge.
- **Answering a different question than the one asked.** Re-read before
  responding.
- **Silently extending scope.** If you find yourself thinking "while I'm
  here, let me also...", stop and ask.

## Closing posture

The skill's job is to be useful, which means being accurate and scoped, not
prolific. A short verified answer is worth more than a long unverified one.
When in doubt: investigate less but verify more; decide less but ask more.
