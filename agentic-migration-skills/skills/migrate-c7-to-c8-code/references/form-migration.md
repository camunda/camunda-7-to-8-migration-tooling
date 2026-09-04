# Generated Task Form Migration

Use this procedure for C7 Generated Task Forms in BPMN XML:

- `camunda:formData` with `camunda:formField` children
- legacy `camunda:formProperty` elements directly below `bpmn:extensionElements`

Every other C7 form type is *referenced*, not defined on the element: embedded HTML/JavaScript
forms, external/custom application forms, Camunda Form references, and user tasks with no form at
all. Use `form-reference-migration.md` for those. It reuses this file's generation machinery, so keep
both procedures consistent and never route a form through both.

The Diagram Converter reports `camunda:formData` as the `form-data` TASK finding and removes the
unsupported C7 metadata. Do not change that behavior. Never reconstruct a form from the converted
BPMN alone.

## Required inputs

Require all of the following before generating:

1. The exact original C7 BPMN file.
2. Its fresh converted BPMN output from the current run.
3. The selected C8 target version.
4. The project-relative source path, process id, and owning user-task/start-event id.

Pair source and output by the run's captured paths plus BPMN element ids. If that pairing is not
provable for an imported online conversion or report, ask the user to identify the matching original
and converted files. Do not modify a stale, skipped, or ambiguously paired output.

This procedure applies to M1, M2, M3, and E1. In analyze-only mode, inventory and report the forms
but create no form files and edit no BPMN.

## Discover the complete source surface

Parse XML with a namespace-aware parser. Never parse BPMN with regular expressions. For every
`bpmn:userTask` and `bpmn:startEvent`, collect:

- `camunda:formData`, its `businessKey`, and each `camunda:formField` in document order
- direct `camunda:formProperty` children in document order
- existing C7 `formKey`/`formRef` metadata
- process id, owner id/type/name, source path, and whether a start event is a process-level none
  start event

The current converter emits one owner-level `messageId: form-data` finding for `formData` or
form-property-only definitions. Older releases may omit it for a form-property-only definition. Scan
the source anyway, even when a JSON findings report exists.

Treat these as review items and never choose precedence silently: duplicate owner ids, duplicate
field ids, more than one `formData` per owner, mixed generated-form and existing form references, or
mixed `formField`/`formProperty` definitions.

Record an inventory in `MIGRATION_REPORT.md`:

| Source BPMN | Process | Owner | C7 form kind | Fields | C8 form id | Status |
|---|---|---|---|---:|---|---|

Status is `draft`, `blocked`, `accepted`, or `declined`.

## Deterministic identity and files

Build the identity string exactly as:

```text
<project-relative-source-path-with-forward-slashes>
<process-id>
<owner-local-name>
<owner-id>
```

Separate the four values with one LF and add no trailing LF. Compute its lowercase SHA-256 hex digest
with standard hashing. Never ask a language model to estimate or invent a digest.

Create an owner slug: lowercase the owner id, replace each maximal run outside `[a-z0-9_-]` with `-`,
trim leading/trailing `-`, and use `element` if empty. Truncate the slug to 40 characters. Use the
first 12 digest characters. If two inventory identities collide, extend every colliding prefix by
four characters until unique.

- Form id: `c7-generated-<owner-slug>-<digest-prefix>`
- Draft path: `.camunda-migration/generated-form-drafts/<form-id>.form`
- Accepted filename beside the converted BPMN:
  `<converted-bpmn-stem>--<owner-slug>--<digest-prefix>.form`
- Field component ids: `Field_001`, `Field_002`, ... in source order, resetting for each form
- Visible unresolved-warning ids: `MigrationWarning_001`, `MigrationWarning_002`, ..., resetting
  for each form
- Layout rows: `Row_001`, `Row_002`, ... in final component order, resetting for each form

Never overwrite an existing draft or accepted form. Reuse it only if its identity and content match
the current source and recorded decisions exactly, otherwise ask the user how to preserve both
versions.

Serialize JSON as UTF-8 with LF line endings, two-space indentation, and exactly one trailing LF. Use
the root property order shown below. For components, apply this order where the properties exist:
`type`, `id`, `key`, `label`, `dateLabel`, `timeLabel`, `description`, `defaultValue`, type-specific
properties, `readonly`, `disabled`, `validate`, `properties`, `conditional`, `layout`. Inside
`validate`, order `required`, `minLength`, `maxLength`, `min`, `max`, `pattern`, then error messages.
Sort custom-property keys lexicographically. Preserve component and enum-option array order. The same
source, target version, and decisions must produce byte-identical output on rerun.

## Standard Camunda 8 form shape

Generate a normal linked Camunda Form, not an embedded job-worker form. A form targeting Camunda 8.9
has this shape:

```json
{
  "components": [],
  "executionPlatform": "Camunda Cloud",
  "executionPlatformVersion": "8.9.0",
  "id": "c7-generated-revieworder-0123456789ab",
  "schemaVersion": 18,
  "type": "default"
}
```

Set `executionPlatformVersion` to the target's patch-zero version. Schema version 18 is the
deterministic generated-form default for supported targets 8.8, 8.9, and 8.10. Validate every draft
against the official target-compatible form schema before acceptance. Do not fabricate a Camunda
Modeler exporter entry. If the target falls outside the supported range, load that target's form
schema and ask before choosing different metadata.

Every generated input component uses a stable id, key, and layout. Components other than `datetime`
use `label`. A date-only `datetime` uses `dateLabel`:

```json
{
  "type": "textfield",
  "id": "Field_001",
  "key": "customerName",
  "label": "Customer name",
  "layout": {
    "row": "Row_001",
    "columns": null
  }
}
```

## Field mappings

Apply only these built-in type candidates:

| Camunda 7 type | Camunda 8 component | Automatic details | Review gate |
|---|---|---|---|
| `string` | `textfield` | Preserve a static label and string `defaultValue` | C7 has no textarea signal. Use `textarea` only on user request |
| `long` | `number` | Set `decimalDigits: 0` | Always review the complete value domain: require safe-integer bounds or an explicit string/conversion design |
| `boolean` | `checkbox` | Preserve a static boolean `defaultValue` | `required` semantics differ. See validation |
| `date` | `datetime` | Set `subtype: "date"` and preserve the label as `dateLabel` | Always review the Date-to-ISO-string and date-pattern change |
| `enum` | `select` | Preserve option order. `camunda:value@id` becomes `value`, `@name` becomes `label` | Missing/duplicate ids or labels need a decision |
| missing/custom | no inferred input component | Insert a visible migration warning at the field's position | User must choose a C8 component and data contract |

The C7 field id is a flat process-variable name. A C8 form key with dots is a nested data path, and
the form schema accepts only word segments separated by dots. Auto-copy an id to `key` only when it
matches `^\w+$` and preserves the intended variable. Treat dotted ids, punctuation, whitespace,
aliases, or any required input/output mapping as review-required, with no auto-mapping.

If a label is absent, use the source field id in the draft only, insert a visible warning, and ask
the user to confirm or provide a label.

### Labels

C7 evaluates `label` as a JUEL expression.

- Plain static values: copy the label unless it begins with `=` or contains Camunda Forms templating
  such as `{{...}}`. Such values can turn literal C7 text into dynamic C8 content, so require review
  and verified escaping or replacement.
- An exact simple reference `${name}` or `#{name}` where `name` matches `[A-Za-z_][A-Za-z0-9_]*`:
  candidate-map to `= name`, unless it is a FEEL reserved word or a C7 context object (`execution`,
  `task`, `caseExecution`, `authenticatedUserId`). Record a REVIEW item and ask before accepting the
  form.
- Any method call, property path, operator, interpolation, multiple expression, or context object:
  do not invent FEEL. Insert a visible warning and ask whether to use form input data, an
  input/output mapping, a worker/listener, or another explicit design.

### Defaults

C7 also evaluates `defaultValue` (or legacy `formProperty@default`) as JUEL. Form-js treats a
component's `defaultValue` as literal initial data and does not evaluate a leading `=` as FEEL.

- Plain static values: convert to the target field's JSON type and write the result to
  `defaultValue`. An enum default must equal one generated option's `value`.
- Any `${...}` or `#{...}` default, including an exact simple variable reference: keep the otherwise
  mappable component, omit only `defaultValue`, insert a visible warning immediately before it, and
  ask whether to prepopulate through form input data, BPMN input mapping, a worker/listener, or
  another explicit design.

For every `long`, inspect constraints and application consumers to find its complete value domain. A
C7 `long` accepts values outside JavaScript's safe integer range, so an unconstrained field is not
automatically safe. Require proven bounds within `[-9007199254740991, 9007199254740991]`, or an
explicit `serializeToString` plus downstream string/conversion design. A static default must also
parse as an integer within the chosen contract. For `date`, C7 produces a Java `Date` using
`datePattern` or the engine default `dd/MM/yyyy`, and C8 produces an ISO `YYYY-MM-DD` string. Generate
a candidate ISO value only when the date parses unambiguously, and still require the user to approve
the downstream type/format change.

`camunda:properties/camunda:property` is custom metadata, not rendered form behavior. It can be copied
to the component's `properties` object with `id` as key and `value` as value, but mark it for review
and find every custom consumer. Copying metadata does not migrate that consumer.

## Validation is always review-gated

The C7 engine enforces C7 generated-form constraints at form submission. C8 form-js validation
controls form submission in the renderer and does not replace custom or server-side validation. For
every form with validation, show the affected fields and ask whether form-js-only validation is
acceptable or backend enforcement must be designed.

Scope the decision to one form by default. Reuse it across forms only when the user explicitly
says the policy applies to that named batch, and record the scope with the answer.

After that decision, use this mapping:

| C7 constraint | C8 candidate | Required user decision |
|---|---|---|
| `required` on string/long/date/enum | `validate.required: true` | Accept client-side enforcement instead of C7 engine enforcement |
| `required` on boolean | none by default | C7 accepts submitted `false`, but a C8 required checkbox rejects `false`. Ask whether unchecked is valid or affirmative consent is intended |
| `minlength=N` | `validate.minLength: N` | C7 validates an empty submitted string and counts raw characters. Form-js skips empty input and trims nonempty input |
| `maxlength=N` | `validate.maxLength: N` | C7 counts raw characters. Form-js skips empty input and trims nonempty input |
| `min=N` | `validate.min: N` | Numeric comparison matches, but backend enforcement is lost |
| `max=N` on `long` | candidate `validate.max: N - 1` | C7 requires `< N`, but form-js allows `<= max`. Ask whether to keep strictness with `N - 1` or accept inclusive `N`. Block on underflow/unsafe values |
| `readonly` | no direct equivalent | C7 rejects any submitted value. C8 `readonly` only prevents editing, not engine enforcement, and `disabled` is development-only. Ask whether to omit, display read-only, or enforce elsewhere |
| `validator` or unknown name | no direct equivalent | Locate the validator implementation and ask for a backend/application redesign |

Invalid, nonliteral, or type-incompatible constraint configuration is blocking. Never silently drop a
constraint or claim the generated form is equivalent.

## Legacy `camunda:formProperty`

Map a simple property without a design decision only when it has all of these characteristics:

- `readable` is true or absent
- `writable` is true or absent
- no `variable` alias and no `expression`
- static/default-free data compatible with one of the built-in field mappings

Use `name` as the label and `id` as the candidate key. Treat `required=true` as a separate review
item: legacy C7 form properties require only that the submission contain the key, so a present empty
string (and boolean `false`) can pass, while C8 `validate.required` rejects them. Map it only after
the user confirms the intended nonempty/affirmative behavior or chooses another enforcement design.
Map `default` through the default rules, enum values in document order, and date patterns through the
date review.

Ask before handling any of the following:

- `readable=false`: C7 omitted the property from rendered form data
- `writable=false`: C7 rejected a submitted value
- `variable`: the submission id and stored process-variable name differ
- `expression`: C7 read or wrote through a JUEL l-value
- dynamic `default`, custom type, date pattern, or ambiguous enum

Camunda 7.24's engine parser recognizes `writable`. Some extension documentation/model APIs use the
spelling `writeable`. Detect both spellings, preserve the literal source in the report, and flag
`writeable` as inconsistent metadata rather than assign C7 `writable` semantics silently.

If an owner mixes `formField` and `formProperty`, preserve each list's document order but require the
user to approve the combined C8 order.

## Business key handling

`camunda:formData businessKey="customerId"` contains a form-field id, not a boolean. C7 uses that
field as the process business key and does not submit it as a normal process variable.

No form-js property reproduces this. Treat every occurrence as blocking and ask the user to choose an
explicit design:

- Target 8.9+: start the process through an API/client that passes this value as immutable
  `businessId`, where the application's start path allows.
- Keep it as an ordinary process variable with a documented variable/correlation design.
- Use an agreed tag/correlation design suited to the selected target.
- Decline automatic migration and leave the form unlinked.

A tool that does not accept `businessId` cannot reproduce the first option. For a user-task form the
process already exists, so the field cannot set its creation-time business id. Never silently downgrade
the field to an ordinary variable.

## Visible warnings and user decisions

Generate a draft even when some fields are unresolved. Immediately before each affected component, or
instead of an unmappable component, add:

```json
{
  "type": "text",
  "id": "MigrationWarning_001",
  "text": "**Migration review required:** C7 field `creditLimit` uses custom validator `com.example.CreditValidator`; no Camunda 8 enforcement has been selected.",
  "layout": {
    "row": "Row_002",
    "columns": null
  }
}
```

Also record the same issue in `MIGRATION_REPORT.md`. Group repeated instances of the same semantic gap
and collect one policy decision at a time. Do not ask once per field when one answer covers the
category. Never bundle unrelated decisions into one question.

After applying decisions, present each form (or a coherent batch with identical decisions) for explicit
acceptance:

- **Accept and link**: only after the user reviews the field table and rendered preview/JSON.
- **Revise**: apply the requested design, regenerate deterministically, and present it again.
- **Leave unlinked**: keep the draft and `needs fix` verdict.

If the user explicitly accepts a known unresolved gap, keep its visible warning unless they also
approve removing it, and record the accepted risk. Never treat silence as acceptance.

## Link only accepted forms

Promote an accepted draft beside its converted BPMN under the accepted filename, then edit only the
converted BPMN. Preserve the exact accepted bytes, verify the destination, and remove the generated
draft so one authoritative form file remains. Keep a second copy only when the user explicitly requests
it.

For M2 or any hand-edited/imported output, remove the migrated owner's `camunda:formData`,
`camunda:formField`, `camunda:validation`, `camunda:constraint`, and direct `camunda:formProperty`
elements from the converted copy after the source inventory is captured. M1/E1 normally already removed
them. Never remove them from the original C7 BPMN.

For a user task, ensure exactly one standard Camunda user-task marker and one form definition:

```xml
<bpmn:userTask id="ReviewOrder">
  <bpmn:extensionElements>
    <zeebe:formDefinition
        formId="c7-generated-revieworder-0123456789ab"
        bindingType="deployment" />
    <zeebe:userTask />
  </bpmn:extensionElements>
</bpmn:userTask>
```

Reuse an existing `bpmn:extensionElements` and `zeebe:userTask`, and never duplicate them. If a
different `zeebe:formDefinition`, C7 form reference, or custom external reference is already present,
ask before replacing it.

For a process-level none start event, add the same `zeebe:formDefinition` but no `zeebe:userTask`. Any
message, timer, signal, conditional, event-subprocess, or nested start event requires user review
because it is not the normal interactive start-form path.

Use `bindingType="deployment"` only when BPMN and form deploy together. Otherwise ask the user to
choose and configure a supported binding.

Do not rewrite the raw converter report or erase its historical `form-data` finding. Change the
category verdict to `no action` only after every associated form is accepted, linked, validated, and
covered by deployment. Form-property-only discoveries use a synthetic `generated-form-property-source`
category with the same lifecycle.

## Cross-check application consumers for every scope

Before accepting any generated form, scan the confirmed project read-only, even when the migration
scope is models-only. Locate:

- `FormFieldValidator` implementations and validator beans/classes named in the BPMN
- `FormService`, `TaskFormData`, `StartFormData`, `FormField`, and `FormProperty` consumers
- `submitTaskForm`, `submitStartForm`, `/submit-form`, and `/form-variables` clients
- code relying on business-key extraction, custom field properties, C7 Java `Date`/full-range `long`
  values, form-property aliases/expressions, or C7 validation exceptions

When code migration is out of scope, do not edit these consumers. Record each dependency and ask the
user how it will be handled. If application code is unavailable, record the consumer check as unknown
and keep the form in `needs review` until the user confirms the external behavior. An accepted rendering
alone is never enough to mark the form complete. If application code is available and a complete scan
finds no consumers, record `no in-repository consumers found`. That result alone does not require
another user question.

## Deployment and validation

Deployment binding requires the converted BPMN and accepted `.form` file in the same deployment. Use
explicit accepted resource paths when possible. Use a recursive pattern such as
`classpath*:**/converted-c8-*.form` only when it cannot include drafts or declined forms.

Before reporting a form complete:

1. Parse the form with a real JSON parser.
2. Validate it with a target-compatible official `@bpmn-io/form-json-schema` when available. Ask before
   adding a new validation dependency to the user's project.
3. Import/render it with a target-compatible Camunda Modeler or form-js viewer when available.
4. Parse the converted BPMN with a Camunda 8 BPMN model/parser.
5. Confirm every accepted form id exactly matches one `zeebe:formDefinition@formId`.
6. Confirm every accepted user task has exactly one `zeebe:userTask`.
7. Confirm draft, blocked, and declined forms are neither linked nor deployed.
8. Confirm source field and enum order, component/key uniqueness, and stable rows.
9. Rerun generation from the same source and decisions and compare bytes.
10. Confirm the original Camunda 7 BPMN is unchanged.
11. Confirm linked owners in the converted BPMN retain no C7 generated-form metadata.

Add these report sections:

- Generated form inventory/status table
- Per-field C7-to-C8 mapping table
- Validation/backend-enforcement differences
- Visible warnings and unresolved blockers
- User decisions and explicit acceptance
- JSON/render/BPMN/determinism validation
- Form linkage and deployment coverage
