# KYC Generated Task Form fixture

This directory is a small manual evaluation fixture for the
`migrate-c7-to-c8-code` skill. It intentionally lives under
`agentic-migration-skills/fixtures/`: the Diagram Converter reports and removes
Camunda 7 Generated Task Form metadata, while the migration skill creates and
reviews the Camunda 8 form resource.

Open `generated-task-forms-c7.bpmn` directly in Camunda Modeler. The model is a
deliberately simple, life-like KYC process:

```text
Application received -> Review KYC application -> Application reviewed
```

It contains only a none start event, one user task with one
`camunda:formData` block, one end event, sequence flows, and complete BPMN DI.
There are no service tasks, gateways, message events, form references,
`camunda:formProperty` elements, business-key metadata, custom types,
expressions, duplicate definitions, or malformed values. The file is a
Camunda 7 source model, not a deployable Camunda 8 process.

## Running the evaluation

1. Copy `generated-task-forms-c7.bpmn` into a temporary project directory and
   keep the original unchanged.
2. Open the BPMN in Camunda Modeler and inspect the visual flow and the
   generated-form settings on **Review KYC application**.
3. Run the migration skill with **Models only**, target **Camunda 8.9**, and
   the Diagram Converter CLI approach when Java 21 is available. The agentic
   model-rewrite approach is an alternative when the CLI cannot be run.
4. Let the skill inventory the original BPMN before conversion. The source path,
   process id `kycApplicationReview`, and owner id
   `Task_ReviewKycApplication` must be retained when pairing the generated form
   with the converted model.
5. Review the generated draft and explicitly accept it before the skill links
   it. Do not accept a form merely because its JSON parses.
6. Repeat generation from the same source and recorded decisions. The accepted
   bytes and draft bytes should be identical on the rerun.

The fixture does not require a Maven or Gradle build. Use the temporary project
directory as the migration skill project root.

## Expected converter result

The current converter should emit exactly one owner-level `form-data` TASK
finding:

| Owner | Source | Expected finding |
|---|---|---|
| `Task_ReviewKycApplication` | one `camunda:formData` with seven `camunda:formField` elements | `messageId=form-data`, message element `formData` |

The start and end events should have no generated-form findings. The converted
copy should remove the Camunda 7 `formData`, `formField`, `validation`,
`constraint`, and `value` elements. The skill must use the original BPMN for
form generation and must not reconstruct the form from the converted copy.

## Form coverage

The one generated form covers the straightforward built-in mappings:

| Field | Camunda 7 case | Expected Camunda 8 candidate |
|---|---|---|
| `applicationId` | `string`, static label/default, `required` | `textfield` |
| `applicantName` | `string`, static label/default, `required`, `minlength`, `maxlength` | `textfield` with validation |
| `annualIncome` | `long`, safe static default, `min`, `max` | integer `number` |
| `consentGiven` | `boolean`, static default | `checkbox` |
| `dateOfBirth` | `date`, ISO-compatible static pattern/default | date-only `datetime` |
| `riskTier` | ordered `enum`, static default, three valid options | `select` |
| `reviewNotes` | `string`, `maxlength` | `textfield` with validation |

This is the happy-path fixture for ordinary generated forms. Ambiguous or
lossy cases such as business keys, dynamic labels/defaults, custom types and
validators, custom properties, aliases, read/write flags, duplicate fields or
options, missing types/labels, and `formProperty` are intentionally excluded.
Those cases remain documented in the skill reference and should be evaluated
separately when testing review-gated behavior.

## Review and validation checklist

The evaluation is complete when the agent has:

* created one deterministic draft for `Task_ReviewKycApplication`;
* preserved field and enum-option order;
* represented the standard field mappings and validation constraints in the
  draft and `MIGRATION_REPORT.md`;
* presented the draft and mapping to the user for explicit acceptance;
* linked only the accepted form with a matching `formId` and
  `bindingType="deployment"`;
* validated the form JSON and parsed the converted BPMN;
* confirmed one `zeebe:userTask` and one matching form definition on the
  accepted user task; and
* confirmed the original Camunda 7 BPMN is unchanged and regeneration is
  byte-for-byte deterministic.

Expected output is one reviewable Camunda 8 form and a migration report, not an
automatically accepted form.
