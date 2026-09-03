# Camunda 7 Form Reference Migration

Use this procedure for Camunda 7 user tasks and start events whose form is *referenced* rather than
defined inside the BPMN element:

- `camunda:formKey` — Embedded Task Forms, Camunda Forms, and External/custom application forms
- `camunda:formRef` with `camunda:formRefBinding` / `camunda:formRefVersion` — Camunda Forms
- no form metadata on a user task or process-level none start event — Generic Task Forms

Generated Task Forms (`camunda:formData`, `camunda:formField`, direct `camunda:formProperty`) are a
different workflow. Use `form-migration.md` for those and never recategorize them here.

None of this is automatic. Camunda 8 cannot execute Camunda 7 HTML/JavaScript, and an external
application has no form schema in the engine to convert. The agent classifies, inventories, asks,
and rebuilds a Camunda 8 form only when the user explicitly asks for it. Offer that help — do not
wait to be asked — but never rebuild a form on your own initiative.

This procedure applies to M1, M2, M3, and E1. In analyze-only mode, produce the classification and
inventory but create no form files and edit no BPMN.

## Required inputs

Same as `form-migration.md`: the exact original Camunda 7 BPMN, its fresh converted output from the
current run, the selected Camunda 8 target version, and the project-relative source path, process
id, and owning element id. Pair source and output using the current run's captured paths plus BPMN
element ids; never guess the pairing.

## Classify from the original source

Parse the original BPMN with a namespace-aware parser; never use regular expressions to parse XML
or extract its metadata. Simple regex checks on already parsed scalar values are allowed for narrow
validation, but do not use a regex to define or validate BPMN identifiers; preserve parsed ids as
provided. Never assume the Camunda 7 extension namespace uses the literal prefix `camunda` — match
on the namespace URI `http://camunda.org/schema/1.0/bpmn`.

Known Camunda 7 form-key types use the structure `FORM-TYPE:LOCATION:FORM.NAME`, while
external/custom form keys may be arbitrary strings, including URLs. Classify each raw value by its exact,
case-sensitive prefix, in this order:

| Order | Condition on the raw `camunda:formKey` | Report category |
|---|---|---|
| 1 | starts with `embedded:` | `c7-embedded-html-form` |
| 2 | starts with `camunda-forms:` | `c7-camunda-form-reference` |
| 3 | no known prefix and contains `${` or `#{` | `c7-dynamic-form-reference` |
| 4 | anything else | `c7-external-form-reference` |

`camunda:formRef` is always `c7-camunda-form-reference`, whatever its binding. A user task or
process-level none start event with no form metadata at all is `c7-generic-task-form`.

These are the names used in the inventory. In the model finding verdict table, use the specific
converter messageId (`form-key-embedded`, `form-key-camunda-form`, `form-key-external`,
`form-key-expression`) when it corroborates the source classification. If only the legacy generic
`form-key` finding exists, use the source-derived `c7-*` category to keep the form types separate;
use the synthetic `c7-*` name when no finding exists — the same convention as
`generated-form-property-source`.

Rules that decide real cases:

- **A known prefix wins over an expression.** `embedded:app:forms/${name}.html` is an embedded form
  whose location is dynamic; report the dynamic location as a review item inside that category
  rather than moving the row to `c7-dynamic-form-reference`.
- **A `.html` suffix is not evidence of an embedded form.** `forms/loan.html` and
  `app:forms/loan.html` carry no form type, so they are external references. Only the literal
  `embedded:` prefix makes a form embedded.
- **Match the prefix exactly.** Camunda 7 resolves the form type by exact string, so
  `Embedded:app:forms/loan.html` was an external form in Camunda 7 and stays one here.
- **Never infer an application from an opaque key.** The mapping from a key such as
  `loanApprovalForm` to an application can live in customer code, configuration, environment
  variables, a database, or another service. Search the project for exact matches to provide
  evidence, but never invent a mapping, dereference a URL, or test remote authentication.

Report these as their own review categories instead of forcing them into the table above:

- `camunda:formHandlerClass` — custom Java form handling with no Camunda 8 equivalent
- an element carrying more than one form definition (for example `formKey` and `formRef`, or a form
  reference together with `camunda:formData`) — do not silently choose precedence
- form references on non-process-level-none start events, which are not the ordinary interactive
  start-form path; process-level none start events with form metadata follow the normal
  reference handling, while form-free ones remain in scope for the generic-form inventory

### Cross-check the converter findings

A converter release with classified form-key support emits one of `form-key-embedded`,
`form-key-camunda-form`, `form-key-external`, or `form-key-expression` for user-task form keys.
Older releases emit a single generic `form-key` finding for all of them, and start-event form keys
surface as an `attribute-not-supported` warning.

Use findings as corroboration only. The source classification above is authoritative, because a
report can be stale, imported, or produced by an older converter. If a finding and the source
classification disagree, report the disagreement instead of picking one silently.

## Inventory every reference

Record this table in `MIGRATION_REPORT.md` before asking anything:

| Source BPMN | Process | Owner | Type | Category | Reference (report-safe) | Content available | Complexity | Decision | Status |
|---|---|---|---|---|---|---|---|---|---|

- `Owner` is the element id plus name; `Type` is `userTask` or `startEvent`.
- `Reference (report-safe)` is rendered from the exact source value, byte for byte, except that
  **credential-like URL query values** (for example `token`, `apikey`, `password`, `secret`,
  `signature`) and URL userinfo passwords are rendered as `<redacted>` in `MIGRATION_REPORT.md`.
  When applying a migration action, read the original value from the source BPMN and preserve it
  unchanged where the reference is retained; never copy the redacted report rendering into a model.
- `Content available` is recorded as `yes — <resolved path>`, `no — <reason>`, or `n/a`.
  Allowed `no` reasons are `file missing`, `dynamic path`, and `engine-source mode`.
  Use `n/a` where content does not apply. Engine-source mode (E1) usually has no local content.
- `Complexity` is `simple`, `complex`, or `unknown` (see below); `n/a` outside embedded forms.
- `Status` is `pending`, `kept`, `relinked`, `draft`, `accepted`, `declined`, `deferred`, or
  `blocked`.

The inventory is the deliverable even when nothing is migrated. Customers routinely do not know how
many embedded forms they have, and per-task planning data is the main value of this step.

### Resolve referenced content

For `embedded:app:<path>` and `camunda-forms:app:<path>` look under the C7 web application
resources, conventionally `src/main/webapp/<path>` (Camunda 7 packages
`src/main/webapp/forms` into the deployment artifact).
For `embedded:deployment:<path>` and `camunda-forms:deployment:<path>` look beside the BPMN and in
the deployment resource folders. Record the resolved path, or `no` with the reason: file missing,
dynamic path, or engine-source mode.
For `camunda:formRef`, treat the value as a form reference identifier, not a file path. Search the
project and deployment resource folders for candidate `.form` files, read each converted schema's
own `id`, and report a mismatch with a literal `formRef` instead of silently rewriting either side.

Never fetch a remote URL, never execute JavaScript, and never extract deployment resources from a
live engine.

## Inspect embedded form content

When the HTML is available, read it — read-only — and classify complexity. Camunda 7 embedded forms
bind to process variables through documented Camunda Forms SDK markers.

Signals that keep a form **simple**:

- form controls carrying `cam-variable-name` with a `cam-variable-type`
- plain `<label>` text, static markup, and standard HTML `required` attributes

Signals that make a form **complex** — any single one is enough:

- `cam-script` or any `<script>` element
- AngularJS usage: `ng-*` attributes, `{{ ... }}` interpolation, custom directives
- inline event handlers such as `onclick`
- `cam-choices` (options sourced from a variable at runtime)
- `cam-business-key`
- file handling such as `cam-file-download` or file upload controls
- external assets: remote `<script src>`, `<link>`, `<img>`, `<iframe>`
- custom widgets from component libraries

Record `unknown` when the content could not be resolved. Any `cam-*` attribute you do not recognize
makes the form complex; do not guess its semantics.

Only a `simple` form with resolved content is a candidate for deriving fields automatically. A
`complex` or `unknown` form may still be rebuilt, but every field must come from the user, not from
the markup.

## Ask decisions per integration group

Group rows and ask with AskUserQuestion. Ask **one decision per group**, not one per row — but only
group references that genuinely share an integration. Heterogeneous opaque keys pointing at
different applications are separate decisions; identical or provably related references are one.

Offer these options:

- **Keep the reference** — the converted `zeebe:formDefinition` keeps the exact value and a custom
  application resolves it. This is the normal choice for `c7-external-form-reference`, and it is
  also the realistic target for a `complex` embedded form whose custom JavaScript, layout, or
  dynamic behavior a Camunda Form cannot reproduce: the custom task application serves the UI and
  the reference identifies which one.
- **Rebuild as a Camunda 8 form** — the agent generates a Camunda Form and relinks the element.
- **Leave the element without a form** — valid for `c7-generic-task-form`, and for any reference the
  user decides to drop. The copied Camunda 7 reference is removed and the loss is recorded.
- **Defer** — record the owner and the blocker; the row stays `deferred` and its category stays
  `needs review`.

`c7-camunda-form-reference` rows do not get this question; they are relinked (see below).
`c7-dynamic-form-reference` rows, `camunda:formHandlerClass`, and conflicting form definitions stay
`needs review` until the user resolves them explicitly — a form key computed at runtime may resolve
to several forms, so enumerate the possible values with the user before migrating any of them.

Never treat silence, a general "migrate everything" instruction, or an earlier approval of a
different category as consent to rebuild a form.

## Relink Camunda Form references

A `camunda-forms:` form key or a `camunda:formRef` already points at a real form-js schema, so
nothing is rebuilt. The two arrive at the converted model differently:

- `camunda:formRef` is already mapped to `zeebe:formDefinition@formId`, and `formRefBinding` is
  mapped to `bindingType` for targets 8.6+: `deployment` is written through, and `latest` is left
  implicit because Camunda 8 already defaults to `latest` when the attribute is absent. A `version`
  binding is reported as unsupported.
- A `camunda-forms:` form key is only copied verbatim into `externalReference`/`formKey`, which
  Camunda 8 does not resolve. It still has to be relinked.

For both:

1. Locate the `.form` file and convert it with the existing Diagram Converter form conversion, which
   updates the execution platform metadata only. Do not hand-edit the schema.
2. Read the form's own `id` from the converted `.form` file — do not derive it from the file name,
   and do not assume a Camunda 7 `formRef` value equals the schema id. If the source reference and
   schema id do not establish an unambiguous mapping, mark the row `blocked`, record the reference,
   schema id, and path, and ask the user which side should change. Do not relink until it is resolved.
3. Ensure the converted element carries exactly one `zeebe:formDefinition` with `formId` set to that
   id, and remove any copied Camunda 7 reference (`externalReference` or `formKey`) from it.
4. Confirm the binding as a recorded decision rather than an accident. Write `bindingType` for
   `deployment` and for `versionTag` (with its `versionTag` value). `latest` may stay implicit,
   since that is the Camunda 8 default — record the choice in `MIGRATION_REPORT.md` either way. A
   Camunda 7 `version` binding with `formRefVersion` has no numeric-version equivalent, so ask the
   user to choose `versionTag` with a real tag or accept another binding.
5. Confirm the form is deployed together with the process for `deployment` binding.

If the `.form` file cannot be found, mark the row `blocked` and ask; never fabricate a form id.

## Rebuild a reference as a Camunda 8 form

Only after an explicit per-category rebuild decision. Reuse `form-migration.md` unchanged for all
shared machinery — identity string and SHA-256 digest, owner slug, draft path, component/warning/row
id sequences, JSON serialization and property order, the standard Camunda 8 form shape and
`executionPlatformVersion`, the visible-warning mechanism, review and acceptance gates, the
application-consumer cross-check, and the deployment/validation checklist.

Two things differ:

- **Form id prefix.** Use `c7-rebuilt-<owner-slug>-<digest-prefix>`, so a rebuilt form is never
  confused with a form generated from `camunda:formData`. An owner has at most one Camunda 8 form;
  if an owner somehow qualifies for both procedures, that is a conflicting-form-definition review
  item, not two forms.
- **Field source.** There is no structured Camunda 7 form schema, so fields come from one of:

  1. **Documented SDK markers**, for a `simple` embedded form with resolved content.
  2. **The user**, for every other case — a `complex` or `unknown` embedded form, an external
     reference, or a dynamic reference. Offer to seed the draft from the element's variable contract
     (input/output mappings and the variables the surrounding process and code actually read and
     write) and confirm every field explicitly.

  Never invent a field, and never derive a field from a file you could not read.

### Marker mappings for simple embedded forms

Every row is a *candidate* that the user must confirm; nothing here is auto-accepted.

| Camunda 7 source | Camunda 8 candidate | Review gate |
|---|---|---|
| `cam-variable-name` | component `key` | Auto-copy only when it matches `^\w+$`; a dotted name is a nested C8 data path, so it needs a decision |
| `cam-variable-type="String"` | `textfield` | Use `textarea` only when the user asks; a `<textarea>` control is a hint, not a contract |
| `cam-variable-type="Boolean"` | `checkbox` | HTML `required` on a checkbox is not the same as `validate.required`; ask whether unchecked is valid |
| `cam-variable-type` `Integer`/`Short` | `number` with `decimalDigits: 0` | Confirm the value domain |
| `cam-variable-type="Long"` | `number` with `decimalDigits: 0` | Same safe-integer review as `form-migration.md`: require proven bounds or an explicit string design |
| `cam-variable-type` `Double`/`Float` | `number` | Ask for `decimalDigits`; C7 imposed none |
| `cam-variable-type="Date"` | `datetime` | Same Java `Date`-to-ISO review as `form-migration.md` |
| `cam-variable-type` `Bytes`/`File` | no inferred component | Needs an explicit Camunda 8 document design |
| `cam-variable-type` `Object`/`Json`/`Xml` | no inferred component | Needs an explicit serialization design |
| missing or unknown `cam-variable-type` | no inferred component | Ask; do not infer the type from the HTML input type |
| `<label for>` or adjacent label text | component `label` | Always confirm — HTML labels are presentation, not a schema |
| HTML `required` | `validate.required: true` | Client-side only; apply the validation policy decision from `form-migration.md` |
| `cam-choices` | no inferred component | Runtime-sourced options need an explicit dynamic-options design |
| `cam-business-key` | no inferred component | Blocking, exactly like `camunda:formData businessKey` in `form-migration.md` |

Insert a visible `MigrationWarning_...` component for every unresolved row, exactly as
`form-migration.md` describes, and record the same gap in `MIGRATION_REPORT.md`.

Layout, styling, custom JavaScript behavior, and conditional logic are never carried over. Say so
explicitly when presenting the draft: a rebuilt form reproduces the *data contract*, not the
Camunda 7 user interface. Never claim behavioral equivalence.

### Link an accepted rebuilt form

Follow the linking rules in `form-migration.md`, plus one addition: replace the copied Camunda 7
reference. The converted element must not keep both a rebuilt form and a stale Camunda 7 pointer.

```xml
<bpmn:userTask id="ApproveLoan">
  <bpmn:extensionElements>
    <zeebe:formDefinition
        formId="c7-rebuilt-approveloan-0123456789ab"
        bindingType="deployment" />
    <zeebe:userTask />
  </bpmn:extensionElements>
</bpmn:userTask>
```

Remove the `externalReference` (Camunda user tasks) or `formKey` (job-worker user tasks) attribute
the converter copied, reuse the existing `bpmn:extensionElements` and `zeebe:userTask`, and never
create a second `zeebe:formDefinition`. Leave the original Camunda 7 BPMN untouched, and leave the
referenced Camunda 7 HTML file in place — deleting it is a separate cleanup the user must request.

## Keep an external application

When the user keeps a reference, the converted model keeps the exact value and the migration work
moves into the custom application. Record this checklist in `MIGRATION_REPORT.md` and confirm an
owner for it:

- **A kept reference is not a completed migration.** Tasklist does not render a custom form
  reference; the value is only data the application reads.
- On a Camunda user task the reference lives in `zeebe:formDefinition@externalReference`; on a
  job-worker user task it is written as `zeebe:formDefinition@formKey`. Preserve the attribute
  that matches the converted task type.
- For a Camunda user task, the application must query user tasks and their variables, and assign,
  update, and complete them, through the **Camunda 8 Orchestration Cluster API**. The Camunda 7
  `FormService`, `submitTaskForm` / `submitStartForm`, and the Camunda 7 task REST API are gone;
  every caller needs rewriting. Do not carry over Tasklist V1 assumptions.
- Authentication, identity, and authorization move to the Camunda 8 identity setup; Camunda 7 user,
  group, and authorization lookups do not carry over.
- The application's own routing must still resolve the kept reference value, so the value must stay
  meaningful in the new deployment topology.

Keep the category verdict at `needs fix` until the application owner confirms the integration, or
`needs review` while the decision is outstanding. A copied reference alone never closes it.

## Generic Task Forms

Inventory every user task and process-level none start event with **no** form metadata — no
`formKey`, no `formRef`, and no `formData`/`formProperty`. Camunda 7 Tasklist rendered an ad-hoc form from
whatever variables existed; Camunda 8 shows no form at all, so the behavior silently changes.

Record them in the same inventory with category `c7-generic-task-form` and reference `none`. Report
them as an explicit choice for the user — build a Camunda Form, keep the task form-free, or handle
it in a custom application — and offer the rebuild procedure above if they choose a form. Do not
generate anything without an explicit request, and do not present the category as "no action".

## Report and verdict lifecycle

Add these sections to `MIGRATION_REPORT.md`:

- the reference inventory table above
- per-category decisions with who made them and their scope
- for kept references, the custom-application checklist and its owner
- for rebuilt forms, everything `form-migration.md` requires: field mapping, validation
  differences, visible warnings, explicit acceptance, and validation results

Verdict rules for the model finding table:

- Every category starts at `needs review` — a decision is required before any work. None of these
  categories is ever `no action` on the strength of the converter having copied a reference.
- After a decision, a category is `needs fix` until its work is finished: forms accepted, linked,
  validated and deployed for a rebuild; the integration confirmed by its owner for a keep; the
  `.form` converted, relinked, and deployed for a Camunda Form reference.
- Move a category to `no action` only when every row in it reaches a terminal state, and record
  `declined` and `deferred` rows as accepted risk rather than completed work.
- Do not rewrite the raw converter report or erase its historical findings.
