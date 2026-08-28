# Generated Task Form Migration

Use this procedure for Camunda 7 Generated Task Forms stored in BPMN XML:

- `camunda:formData` with `camunda:formField` children
- legacy `camunda:formProperty` elements directly below `bpmn:extensionElements`

This is an agentic follow-up to model conversion. The Diagram Converter intentionally reports
`camunda:formData` with the `form-data` TASK finding and removes the unsupported Camunda 7
metadata from its output. Do not change that converter behavior and never reconstruct a form from
the converted BPMN alone.

## Required inputs

Before generating anything, require all of the following:

1. The exact original Camunda 7 BPMN file.
2. Its fresh converted BPMN output from the current migration run.
3. The selected Camunda 8 target version.
4. The project-relative source path, process id, and owning user-task/start-event id.

Pair source and output using the current run's captured paths plus BPMN element ids. For an
imported online conversion or report, ask the user to identify the matching original and converted
files if the pairing is not provable. Do not modify a stale, skipped, or ambiguously paired output.

This procedure applies to M1, M2, M3, and E1. In analyze-only mode, inventory and report the forms
but do not create form files or edit BPMN.

## Discover the complete source surface

Parse XML with a namespace-aware parser. Never use regular expressions to parse BPMN. For every
`bpmn:userTask` and `bpmn:startEvent`, collect:

- `camunda:formData`, its `businessKey`, and each `camunda:formField` in document order
- direct `camunda:formProperty` children in document order
- existing C7 `formKey`/`formRef` metadata
- process id, owner id/type/name, source path, and whether a start event is a top-level none start
  event

The current converter emits one owner-level `messageId: form-data` finding for `formData` or
form-property-only definitions. Older releases may omit the latter. Source scanning is therefore
mandatory even when a JSON findings report is available.

Treat duplicate owner ids, duplicate field ids, more than one `formData` per owner, mixed
generated-form and existing form references, or mixed `formField`/`formProperty` definitions as
review items. Do not silently choose precedence.

Record an inventory in `MIGRATION_REPORT.md`:

| Source BPMN | Process | Owner | C7 form kind | Fields | C8 form id | Status |
|---|---|---|---|---:|---|---|

Use `draft`, `blocked`, `accepted`, or `declined` as status.

## Deterministic identity and files

Build the identity string exactly as:

```text
<project-relative-source-path-with-forward-slashes>
<process-id>
<owner-local-name>
<owner-id>
```

Separate the four values with a single LF and do not add a trailing LF. Compute its lowercase
SHA-256 hex digest with a standard hashing implementation; never ask a language model to estimate
or invent a digest.

Create an owner slug by lowercasing the owner id, replacing each maximal run outside
`[a-z0-9_-]` with `-`, trimming leading/trailing `-`, and using `element` if empty. Truncate the
slug to 40 characters. Use the first 12 digest characters; if two identities in the inventory
collide, extend every colliding prefix by four characters until unique.

- Form id: `c7-generated-<owner-slug>-<digest-prefix>`
- Draft path: `.camunda-migration/generated-form-drafts/<form-id>.form`
- Accepted filename beside the converted BPMN:
  `<converted-bpmn-stem>--<owner-slug>--<digest-prefix>.form`
- Field component ids: `Field_001`, `Field_002`, ... in source order, resetting for each form
- Visible unresolved-warning ids: `MigrationWarning_001`, `MigrationWarning_002`, ..., resetting
  for each form
- Layout rows: `Row_001`, `Row_002`, ... in final component order, resetting for each form

Never overwrite an existing draft or accepted form. Reuse it only if its identity and content
match the current source and recorded decisions exactly; otherwise ask the user how to preserve
both versions.

Serialize JSON as UTF-8 with LF line endings, two-space indentation, and exactly one trailing LF.
Use the root property order shown below. For components, use this order when properties apply:
`type`, `id`, `key`, `label`, `dateLabel`, `timeLabel`, `description`, `defaultValue`, type-specific
properties, `readonly`, `disabled`, `validate`, `properties`, `conditional`, `layout`. Use
`required`, `minLength`, `maxLength`, `min`, `max`, `pattern`, then error messages inside
`validate`; sort custom-property keys lexicographically. Preserve component and enum-option array
order. The same source, target version, and decisions must produce byte-identical output on rerun.

## Standard Camunda 8 form shape

Generate a normal linked Camunda Form, not an embedded job-worker form:

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

Set `executionPlatformVersion` to the selected target's patch-zero version. Schema version 18 is
this skill's deterministic generated-form default for supported targets 8.8, 8.9, and 8.10.
Validate every draft against the official target-compatible form schema before acceptance. Do not
fabricate a Camunda Modeler exporter entry. If the selected target falls outside the skill's
supported range, load that target's form schema and ask before choosing different metadata.

Every generated input component uses a stable id, key, and layout. Components other than
`datetime` use `label`; a date-only `datetime` uses `dateLabel`:

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
| `string` | `textfield` | Preserve a static label and string `defaultValue` | C7 has no textarea signal; use `textarea` only when the user requests it |
| `long` | `number` | Set `decimalDigits: 0` | Always review the complete value domain: require safe-integer bounds or an explicit string/conversion design |
| `boolean` | `checkbox` | Preserve a static boolean `defaultValue` | `required` has different semantics; see validation |
| `date` | `datetime` | Set `subtype: "date"` and preserve the label as `dateLabel` | Always review the Date-to-ISO-string and date-pattern change |
| `enum` | `select` | Preserve option order; `camunda:value@id` becomes `value`, `@name` becomes `label` | Missing/duplicate ids or labels require a decision |
| missing/custom | no inferred input component | Insert a visible migration warning at the field's position | User must choose a C8 component and data contract |

The C7 field id is a flat process-variable name. A C8 form key containing dots is a nested data
path, and the form schema accepts only word segments separated by dots. Auto-copy an id to `key`
only when it matches `^\w+$` and preserves the intended variable. Treat dotted ids, punctuation,
whitespace, aliases, or any required input/output mapping as review-required (no auto-mapping).

If a label is absent, use the source field id only in the draft, insert a visible warning, and ask
the user to confirm or provide a label.

### Labels

C7 evaluates `label` as a JUEL expression. Handle it as follows:

- Plain static values: copy the label unless it begins with `=` or contains Camunda Forms
  templating syntax such as `{{...}}`; those values can change from literal C7 text into dynamic
  C8 content, so require review and verified escaping or replacement.
- An exact simple reference `${name}` or `#{name}` where `name` matches
  `[A-Za-z_][A-Za-z0-9_]*`: candidate-map to `= name`, unless it is a FEEL reserved word or a C7
  context object (`execution`, `task`, `caseExecution`, `authenticatedUserId`). Record a REVIEW
  item and ask before accepting the form.
- Any method call, property path, operator, interpolation, multiple expression, or context object:
  do not invent FEEL. Insert a visible warning and ask whether to use form input data, an
  input/output mapping, a worker/listener, or another explicit design.

### Defaults

C7 also evaluates `defaultValue` (or legacy `formProperty@default`) as JUEL. Form-js treats a
component's `defaultValue` as literal initial data; it does not evaluate a leading `=` as FEEL.

- Plain static values: convert to the target field's JSON type and write the result to
  `defaultValue`. An enum default must equal one generated option's `value`.
- Any `${...}` or `#{...}` default, including an exact simple variable reference: keep the
  otherwise mappable component, omit only `defaultValue`, insert a visible warning immediately
  before it, and ask whether to prepopulate through form input data, BPMN input mapping, a
  worker/listener, or another explicit design.

For every `long`, inspect constraints and application consumers to establish its complete value
domain. A C7 `long` accepts values outside JavaScript's safe integer range; an unconstrained field
therefore is not automatically safe. Require either proven bounds within
`[-9007199254740991, 9007199254740991]` or an explicit `serializeToString` plus downstream
string/conversion design. A static default must also parse as an integer within the chosen
contract. For `date`, C7 produces a Java `Date` using `datePattern` or the engine default
`dd/MM/yyyy`, while C8 produces an ISO `YYYY-MM-DD` string. Generate a candidate ISO value only
when the date can be parsed unambiguously, and still require the user to approve the downstream
type/format change.

`camunda:properties/camunda:property` is custom metadata, not rendered form behavior. It can be
copied to the component's `properties` object with `id` as key and `value` as value, but mark it for
review and find every custom consumer. Copying metadata does not migrate that consumer.

## Validation is always review-gated

C7 generated-form constraints are enforced by the C7 engine during form submission. Camunda 8
form-js validation controls form submission in the renderer; it is not a replacement for custom or
server-side validation. For every form with validation, show the affected fields and ask whether
form-js-only validation is acceptable or backend enforcement must be designed. The decision is
scoped to one form by default. Reuse it across multiple forms only when the user explicitly says
the policy applies to that named batch, and record the scope with the answer.

After that policy decision, use this mapping:

| C7 constraint | C8 candidate | Required user decision |
|---|---|---|
| `required` on string/long/date/enum | `validate.required: true` | Accept client-side enforcement instead of C7 engine enforcement |
| `required` on boolean | none by default | C7 accepts submitted `false`; C8 required checkbox rejects `false`. Ask whether unchecked is valid or affirmative consent is intended |
| `minlength=N` | `validate.minLength: N` | C7 validates an empty submitted string and counts raw characters; form-js skips the length check for empty input and trims nonempty input |
| `maxlength=N` | `validate.maxLength: N` | C7 counts raw characters; form-js skips empty input and trims nonempty input |
| `min=N` | `validate.min: N` | Numeric comparison matches, but backend enforcement is lost |
| `max=N` on `long` | candidate `validate.max: N - 1` | C7 requires value `< N`; form-js allows value `<= max`. Ask whether to preserve strictness with `N - 1` or accept inclusive `N`; block on underflow/unsafe values |
| `readonly` | no direct equivalent | C7 rejects any submitted value. C8 `readonly` only prevents editing and does not recreate engine enforcement; `disabled` is development-only. Ask whether to omit, display read-only, or enforce elsewhere |
| `validator` or unknown name | no direct equivalent | Locate the validator implementation and ask for a backend/application redesign |

Invalid, nonliteral, or type-incompatible constraint configuration is blocking. Do not silently
drop a constraint or claim that the generated form is equivalent.

## Legacy `camunda:formProperty`

Map only a simple property with all of these characteristics without a design decision:

- `readable` is true or absent
- `writable` is true or absent
- no `variable` alias and no `expression`
- static/default-free data compatible with one of the built-in field mappings

Use `name` as the label and `id` as the candidate key. Treat `required=true` as a separate review
item: legacy C7 form properties require only that the submission contain the key, so a present
empty string (and boolean `false`) can pass, while C8 `validate.required` rejects them. Map it only
after the user confirms the intended nonempty/affirmative behavior or chooses another enforcement
design. Map `default` through the default rules, enum values in document order, and date patterns
through the date review.

Ask before handling any of the following:

- `readable=false`: C7 omitted the property from rendered form data
- `writable=false`: C7 rejected a submitted value
- `variable`: the submission id and stored process-variable name differ
- `expression`: C7 read or wrote through a JUEL l-value
- dynamic `default`, custom type, date pattern, or ambiguous enum

Camunda 7.24's engine parser recognizes `writable`; some extension documentation/model APIs use
the spelling `writeable`. Detect both spellings, preserve the literal source in the report, and
flag `writeable` as inconsistent metadata rather than silently assigning C7 `writable` semantics.

If an owner mixes `formField` and `formProperty`, preserve each list's document order but require
the user to approve the combined C8 order.

## Business key handling

`camunda:formData businessKey="customerId"` contains a form-field id, not a boolean. C7 uses that
field as the process business key and does not submit it as a normal process variable.

There is no form-js property that reproduces this behavior. Treat every occurrence as blocking and
ask the user to choose an explicit design:

- Target 8.9+: start the process through an API/client that passes this value as immutable
  `businessId`, if the application's start path can do so.
- Keep it as an ordinary process variable and use a documented variable/correlation design.
- Use an agreed tag/correlation design when appropriate for the selected target.
- Decline automatic migration and leave the form unlinked.

Creating instances from tools that do not accept `businessId` cannot reproduce the first option.
For a user-task form, the process already exists, so the field cannot set its creation-time
business id. Never silently downgrade the field to an ordinary variable.

## Visible warnings and user decisions

Generate a draft even when some fields are unresolved. Immediately before each affected component,
or instead of an unmappable component, add:

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

Also record the same issue in `MIGRATION_REPORT.md`. Group repeated instances of the same semantic
gap and collect one policy decision at a time; do not ask once per field when one answer can cover
the category. Never bundle unrelated decisions into one question.

After applying decisions, present each form (or a coherent batch with identical decisions) for
explicit acceptance:

- **Accept and link**: only when the user has reviewed the field table and rendered preview/JSON.
- **Revise**: apply the requested design, regenerate deterministically, and present it again.
- **Leave unlinked**: keep the draft and `needs fix` verdict.

If the user explicitly accepts a known unresolved gap, keep its visible warning unless they also
approve removing it, and record the accepted risk. Never treat silence as acceptance.

## Link only accepted forms

Promote an accepted draft beside its converted BPMN using the accepted filename, then edit only
the converted BPMN. Preserve the exact accepted bytes, verify the destination, and remove the
generated draft so there is one authoritative form file. Keep a second copy only when the user
explicitly requests it.

For M2 or any hand-edited/imported output, remove the migrated owner's
`camunda:formData`, `camunda:formField`, `camunda:validation`, `camunda:constraint`, and direct
`camunda:formProperty` elements from the converted copy after the source inventory is captured.
M1/E1 normally already removed them. Never remove them from the original C7 BPMN.

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

Reuse an existing `bpmn:extensionElements` and `zeebe:userTask`; never duplicate them. If a
different `zeebe:formDefinition`, C7 form reference, or custom external reference is already
present, ask before replacing it.

For a process-level none start event, add the same `zeebe:formDefinition` but no `zeebe:userTask`.
Any message, timer, signal, conditional, event-subprocess, or nested start event requires user
review because it is not the normal interactive start-form path.

Use `bindingType="deployment"` only when BPMN and form will be deployed together. Otherwise ask
the user to choose and configure a supported binding.

Do not rewrite the raw converter report or erase its historical `form-data` finding. Change the
category verdict to `no action` only after every associated form is accepted, linked, validated,
and covered by deployment. Form-property-only discoveries use a synthetic
`generated-form-property-source` category with the same lifecycle.

## Cross-check application consumers for every scope

Before accepting any generated form, perform a read-only scan of the confirmed project even when
the selected migration scope is models-only. Locate:

- `FormFieldValidator` implementations and validator beans/classes named in the BPMN
- `FormService`, `TaskFormData`, `StartFormData`, `FormField`, and `FormProperty` consumers
- `submitTaskForm`, `submitStartForm`, `/submit-form`, and `/form-variables` clients
- code relying on business-key extraction, custom field properties, C7 Java `Date`/full-range
  `long` values, form-property aliases/expressions, or C7 validation exceptions

When code migration is out of scope, do not edit these consumers; record each dependency and ask
the user how it will be handled. If application code is unavailable, record the consumer check as
unknown and keep the form in `needs review` until the user confirms the external behavior. An
accepted rendering alone is never enough to mark the form complete. If application code is
available and a complete scan finds no consumers, record `no in-repository consumers found`; that
result alone does not require another user question.

## Deployment and validation

Deployment binding requires the converted BPMN and accepted `.form` file in the same deployment.
Use explicit accepted resource paths when possible. A recursive pattern such as
`classpath*:**/converted-c8-*.form` is allowed only when it cannot include drafts or declined
forms.

Before reporting a form complete:

1. Parse the form with a real JSON parser.
2. Validate it with a target-compatible official `@bpmn-io/form-json-schema` when available. Ask
   before adding a new validation dependency to the user's project.
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
