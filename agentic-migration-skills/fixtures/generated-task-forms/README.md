# Generated Task Forms fixture

This directory is a manual evaluation fixture for the `migrate-c7-to-c8-code`
skill. It intentionally lives under `agentic-migration-skills/fixtures/` rather
than under `diagram-converter/`: the Diagram Converter only reports and removes
Generated Task Form metadata, while the migration skill creates and reviews the
Camunda 8 form resources.

The fixture is a Camunda 7 source model, not a deployable Camunda 8 process. Some
fields deliberately use unsupported or ambiguous metadata so that the skill has
to create visible warnings and request decisions instead of silently guessing.

## Running the evaluation

1. Use `generated-task-forms-c7.bpmn` as the only model in a temporary project
   directory. Keep the original file unchanged.
2. Run the migration skill with **Models only**, target **Camunda 8.9**, and
   the Diagram Converter CLI approach when Java 21 is available. The agentic
   model-rewrite approach is an alternative when the CLI cannot be run.
3. Let the skill inventory the original BPMN before conversion. The source path,
   process id `generatedTaskFormsFixture`, and owner ids in the tables below must
   be retained when pairing generated forms with the converted model.
4. Review every generated draft and answer each decision gate. Do not accept or
   link a draft merely because its JSON parses.
5. Repeat generation from the same source and recorded decisions. The accepted
   bytes and draft bytes should be identical on the rerun.

The fixture does not require a Maven or Gradle build. It is intended for a
models-only migration evaluation; if the host requires a project root, use the
temporary directory containing this BPMN as that root.

## Expected converter findings

The current converter should emit one owner-level `form-data` TASK finding for
each of these owners, without additional child-level generated-form findings.
For legacy properties, the message text names `formProperty`, but the
`messageId` remains `form-data`; this is the converter's shared generated-form
category. Unrelated informational findings for custom properties may also be
present.

| Owner | Generated-form source | Expected finding |
|---|---|---|
| `Start_GeneratedForm` | one `camunda:formData` | `messageId=form-data`, message element `formData` |
| `UserTask_CleanForm` | one `camunda:formData` | `messageId=form-data`, message element `formData` |
| `UserTask_MixedForm` | duplicate `camunda:formData` plus direct `camunda:formProperty` | `messageId=form-data`, message element `formData` |
| `Start_LegacyProperties` | direct `camunda:formProperty` elements | `messageId=form-data`, message element `formProperty` |
| `UserTask_LegacyProperties` | direct `camunda:formProperty` elements | `messageId=form-data`, message element `formProperty` |

The converted copy should remove the Camunda 7 generated-form elements. The
skill must use the original BPMN for form generation and must not reconstruct
the fields from the converted copy or erase the converter finding from the
report.

## Coverage matrix

### `Start_GeneratedForm`

This owner is the clean, broad `formData` surface and uses
`camunda:formData@businessKey="customerNumber"`:

| Field | C7 case | Expected C8 candidate or review |
|---|---|---|
| `customerNumber` | `string`, static label/default, `required`, custom properties, business-key field | `textfield`; business-key handling is blocking |
| `customerNote` | `string`, static default, `minlength`, `maxlength` | `textfield` with length validation; review C7/C8 enforcement differences |
| `amount` | bounded `long`, static default, `min`, strict C7 `max` | `number` with integer precision and strict-maximum decision |
| `unboundedLong` | `long` outside JavaScript safe-integer range | `number` only after a safe-integer or string contract decision |
| `approved` | `boolean`, static default, `required` | `checkbox`; decide whether unchecked is valid |
| `requestedDate` | `date`, explicit `dd/MM/yyyy` pattern, static default, `readonly` | date-only `datetime`; review ISO conversion and read-only enforcement |
| `priority` | ordered `enum`, static default, three `camunda:value` options | `select`; preserve option order and default |
| `validatedCode` | built-in `validator` constraint naming a custom validator | `textfield` plus visible warning and backend redesign decision |
| `invalidConstraint` | unknown constraint with dynamic configuration | visible warning; do not drop or invent a validation rule |

### `UserTask_CleanForm`

This owner demonstrates the simplest candidate that can become accepted after
the normal form review:

| Field | C7 case | Expected C8 candidate |
|---|---|---|
| `reviewerName` | `string` with static label and no default | `textfield` |
| `confirmed` | `boolean` with a static default | `checkbox` |

The skill should still create a deterministic draft, present it to the user,
and add a deployment-bound `zeebe:formDefinition` only after explicit
acceptance.

### `UserTask_MixedForm`

This owner groups ambiguous cases that must not be silently normalized:

* `approvalComment` has a dynamic JUEL label and default plus `required`.
* `customer.address` is a dotted key and must not be auto-mapped as a flat C7
  variable.
* `customer-number` contains punctuation that requires a key decision.
* `missingLabel` has no label; the draft may use the id as a temporary label,
  but must show a warning.
* `missingType` has no type and requires a component/data-contract decision.
* `customField` uses an unmappable custom type and carries custom properties.
* `templateLabel` uses Camunda Forms templating syntax in a C7 label.
* `methodLabel` invokes a Java method from a JUEL label.
* `ambiguousChoice` has duplicate option ids and an option without an id.
* A second `camunda:formData` block tests duplicate form-data handling.
* Direct `camunda:formProperty` mixed with form fields tests combined ordering.
* `camunda:formRef` is already present, so replacing an existing form reference
  requires an explicit decision.
* `businessKey="missingBusinessKeyField"` names no field and is blocking.

Expected result: a draft with stable component and warning ids, a complete
mapping/report table, and no automatic acceptance until all decisions are made.

### `Start_LegacyProperties`

This message start event covers legacy start-form properties:

* `startReference` is a simple, static string candidate.
* `startAlias` uses `variable="startStored"` and requires an alias/data-mapping
  decision.

The non-none start trigger is deliberately included because the skill must ask
for review before treating it as the normal interactive start-form path. If
accepted, the source owner is still a start event, so its linked form uses
`zeebe:formDefinition` without a `zeebe:userTask` marker.

### `UserTask_LegacyProperties`

This owner covers legacy `camunda:formProperty` semantics:

| Property | C7 case | Expected review |
|---|---|---|
| `legacyRequired` | `required="true"` | C7 presence semantics versus C8 nonempty validation |
| `legacyRequiredBoolean` | required boolean property | C7 accepts `false`; decide whether an affirmative value is intended |
| `legacyUnreadable` | `readable="false"` | whether to omit or redesign the field |
| `legacyNotWritable` | `writable="false"` | submitted-value enforcement |
| `legacyWriteableSpelling` | `writeable="false"` spelling | preserve and flag inconsistent metadata |
| `legacyExpression` | JUEL `expression` alias | explicit input/output or worker design |
| `legacyStaticDefault` | `long` with static default | numeric conversion and safe range |
| `legacyDynamicDefault` | JUEL `default` | prepopulation redesign; do not evaluate in form-js |
| `legacyDate` | custom date pattern and static default | ISO date conversion decision |
| `legacyEnum` | ordered enum values and default | preserve values and order |
| `legacyCustom` | custom property type | explicit C8 component and data contract |

## Review and validation checklist

The evaluation is complete only when the agent has:

* created one deterministic draft per generated-form owner;
* preserved source field and enum order;
* represented every unmappable type, constraint, alias, label/default expression,
  business-key difference, and existing-form conflict in both the draft and
  `MIGRATION_REPORT.md`;
* cross-checked custom validators and form consumers without editing unrelated
  application code;
* presented the field mapping, warnings, and rendered/JSON form to the user;
* linked only explicitly accepted forms with matching `formId` values and
  `bindingType="deployment"`;
* kept draft, blocked, and declined forms out of the BPMN and deployment;
* validated form JSON against a target-compatible form schema and parsed the
  converted BPMN;
* confirmed exactly one `zeebe:userTask` for accepted user-task forms, no
  `zeebe:userTask` for the accepted start form, and no leftover C7 generated-form
  metadata in linked owners;
* rerun generation and compared bytes; and
* confirmed the original Camunda 7 BPMN is unchanged.

Expected output is therefore a set of reviewable drafts and a report, not a
single automatically accepted form. The custom and conflicting cases are
included to verify that the agent surfaces uncertainty rather than claiming
lossless conversion.
