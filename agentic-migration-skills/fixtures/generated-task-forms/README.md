# KYC Generated Task Forms fixture

This directory is a manual evaluation fixture for the `migrate-c7-to-c8-code`
skill. It intentionally lives under `agentic-migration-skills/fixtures/` rather
than under `diagram-converter/`: the Diagram Converter reports and removes
Generated Task Form metadata, while the migration skill creates and reviews the
Camunda 8 form resources.

Open `generated-task-forms-c7.bpmn` directly in Camunda Modeler. It is a
deliberately focused Camunda 7 process with only a start event, user tasks,
sequence flows, and an end event. Every form-bearing owner uses
`camunda:formData`, so the modeler can show the generated-form configuration
without unrelated service tasks, gateways, message definitions, or legacy
form-property-only tasks.

The visualization is a realistic KYC onboarding flow:

```text
Application received
  -> Capture applicant information
  -> Verify identity documents
  -> Assess customer risk
  -> Approve customer
  -> KYC complete
```

The source is a Camunda 7 model, not a deployable Camunda 8 process. Unsupported
and ambiguous form metadata is deliberately included so the skill creates
visible warnings and requests decisions instead of silently guessing. The
fixture uses attributes understood by the current Camunda Modeler BPMN moddle.
The historical Camunda 7 `writeable` spelling is documented by the skill but is
not encoded as an unknown XML attribute here, so opening the file remains
warning-free.

Legacy `camunda:formProperty` is intentionally outside this pure
`formData`/`formField` fixture. It is a separate deprecated form representation,
and its migration remains covered by the implementation and skill guidance in
the parent issue.

## Running the evaluation

1. Use `generated-task-forms-c7.bpmn` as the only model in a temporary project
   directory. Keep the original file unchanged.
2. Open the BPMN in Camunda Modeler and inspect the visual layout and each
   generated-form setting.
3. Run the migration skill with **Models only**, target **Camunda 8.9**, and
   the Diagram Converter CLI approach when Java 21 is available. The agentic
   model-rewrite approach is an alternative when the CLI cannot be run.
4. Let the skill inventory the original BPMN before conversion. The source path,
   process id `kycOnboarding`, and owner ids in the tables below must be retained
   when pairing generated forms with the converted model.
5. Review every generated draft and answer each decision gate. Do not accept or
   link a draft merely because its JSON parses.
6. Repeat generation from the same source and recorded decisions. The accepted
   bytes and draft bytes should be identical on the rerun.

The fixture does not require a Maven or Gradle build. It is intended for a
models-only migration evaluation; if the host requires a project root, use the
temporary directory containing this BPMN as that root.

## Expected converter findings

The current converter should emit one owner-level `form-data` TASK finding for
each of the five generated-form owners, without additional child-level
generated-form findings. Unrelated informational findings for custom properties
may also be present.

| Owner | Generated-form source | Expected finding |
|---|---|---|
| `Start_KycApplication` | one `camunda:formData` | `messageId=form-data`, message element `formData` |
| `Task_CaptureApplicant` | one `camunda:formData` | `messageId=form-data`, message element `formData` |
| `Task_VerifyIdentity` | duplicate `camunda:formData` blocks | `messageId=form-data`, message element `formData` |
| `Task_AssessRisk` | one `camunda:formData` | `messageId=form-data`, message element `formData` |
| `Task_ApproveCustomer` | one `camunda:formData` | `messageId=form-data`, message element `formData` |

The converted copy should remove every Camunda 7 `formData`, `formField`,
`validation`, `constraint`, `value`, and `properties` element. The skill must
use the original BPMN for form generation and must not reconstruct the fields
from the converted copy or erase the converter finding from the report.

## Coverage matrix

### `Start_KycApplication`

This owner is the broad `formData` surface and uses
`camunda:formData@businessKey="applicationId"`:

| Field | C7 case | Expected C8 candidate or review |
|---|---|---|
| `applicationId` | `string`, static label/default, `required`, custom properties, business-key field | `textfield`; business-key handling is blocking |
| `applicantName` | `string`, static default, `minlength`, `maxlength` | `textfield` with length validation; review C7/C8 enforcement differences |
| `annualIncome` | bounded `long`, static default, `min`, strict C7 `max` | `number` with integer precision and strict-maximum decision |
| `reportedAssetValue` | `long` outside JavaScript safe-integer range | `number` only after a safe-integer or string contract decision |
| `consentGiven` | `boolean`, static default, `required` | `checkbox`; decide whether unchecked is valid |
| `dateOfBirth` | `date`, explicit `dd/MM/yyyy` pattern, static default, `readonly` | date-only `datetime`; review ISO conversion and read-only enforcement |
| `riskTier` | ordered `enum`, static default, three `camunda:value` options | `select`; preserve option order and default |
| `nationalId` | built-in `validator` constraint naming a custom validator | `textfield` plus visible warning and backend redesign decision |
| `invalidConstraint` | `validator` with dynamic configuration | visible warning; do not evaluate or invent a validation rule |

### `Task_CaptureApplicant`

This owner demonstrates a small, clean form that can become accepted after the
normal form review:

| Field | C7 case | Expected C8 candidate |
|---|---|---|
| `legalName` | `string` with a static label | `textfield` |
| `countryOfResidence` | ordered `enum` with a static default | `select`; preserve option order and default |
| `phoneNumber` | plain `string` | `textfield` |
| `consentConfirmed` | `boolean` with a static default | `checkbox` |

The skill should still create a deterministic draft, present it to the user,
and add a deployment-bound `zeebe:formDefinition` only after explicit
acceptance.

### `Task_VerifyIdentity`

This owner groups ambiguous cases that must not be silently normalized:

* `documentComment` has a dynamic JUEL label and default plus `required`.
* `applicant.address` is a dotted key and must not be auto-mapped as a flat C7
  variable.
* `document-number` contains punctuation that requires a key decision.
* `missingLabel` has no label; the draft may use the id as a temporary label,
  but must show a warning.
* `missingType` has no type and requires a component/data-contract decision.
* `beneficialOwner` uses an unmappable custom type and carries custom properties.
* `documentTemplate` uses Camunda Forms templating syntax in a C7 label.
* `screeningReason` invokes a Java method from a JUEL label.
* `documentType` has duplicate option ids and an option without an id.
* A second `camunda:formData` block tests duplicate form-data handling.
* `businessKey="missingBusinessKeyField"` names no field and is blocking.

Expected result: a draft with stable component and warning ids, a complete
mapping/report table, and no automatic acceptance until all decisions are made.

### `Task_AssessRisk`

This owner keeps the main example domain-specific while exercising additional
numeric, boolean, enum, date, and string mappings:

| Field | C7 case | Expected C8 candidate or review |
|---|---|---|
| `riskScore` | bounded `long`, `min`, strict C7 `max` | `number` with integer precision and strict-maximum decision |
| `politicallyExposed` | `boolean` with a static default | `checkbox` |
| `riskDecision` | ordered `enum` with a static default | `select`; preserve option order and default |
| `riskReviewDate` | `date` with an explicit `yyyy-MM-dd` pattern | date-only `datetime`; review target format |
| `riskNotes` | `string` with `maxlength` | `textfield` with maximum-length review |

### `Task_ApproveCustomer`

This owner demonstrates a small approval form:

| Field | C7 case | Expected C8 candidate or review |
|---|---|---|
| `approvalDecision` | required boolean with a static default | `checkbox`; review the `false`/required semantic difference |
| `approvalNotes` | plain string field | `textfield` |

## Review and validation checklist

The evaluation is complete only when the agent has:

* created one deterministic draft per generated-form owner;
* preserved source field and enum order;
* represented every unmappable type, constraint, label/default expression,
  business-key difference, and duplicate-definition conflict in both the draft
  and `MIGRATION_REPORT.md`;
* cross-checked custom validators and form consumers without editing unrelated
  application code;
* presented the field mapping, warnings, and rendered/JSON form to the user;
* linked only explicitly accepted forms with matching `formId` values and
  `bindingType="deployment"`;
* kept draft, blocked, and declined forms out of the BPMN and deployment;
* validated form JSON against a target-compatible form schema and parsed the
  converted BPMN;
* confirmed exactly one `zeebe:userTask` for each accepted user-task form, no
  `zeebe:userTask` for the accepted start form, and no leftover C7 generated-form
  metadata in linked owners;
* rerun generation and compared bytes; and
* confirmed the original Camunda 7 BPMN is unchanged.

Expected output is a set of reviewable drafts and a report, not a single
automatically accepted form. The custom and conflicting cases are included to
verify that the agent surfaces uncertainty rather than claiming lossless
conversion.
