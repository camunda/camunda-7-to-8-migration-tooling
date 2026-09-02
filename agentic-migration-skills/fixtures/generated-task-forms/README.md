# KYC Generated Task Forms fixture

This directory is a manual evaluation fixture for the `migrate-c7-to-c8-code`
skill. It intentionally lives under `agentic-migration-skills/fixtures/` rather
than under `diagram-converter/`: the Diagram Converter reports and removes
Generated Task Form metadata, while the migration skill creates and reviews the
Camunda 8 form resources.

Open `generated-task-forms-c7.bpmn` directly in Camunda Modeler. It includes
complete BPMN DI for the following KYC onboarding flow:

```text
Application received
  -> Capture applicant details
  -> Run sanctions screening
  -> Screening outcome?
       Clear -> Approve customer -> Customer approved
       More documents needed -> Request missing documents
                              -> Recheck screening result
                              -> Merge approval paths
                              -> Approve customer
       Reject -> Review rejected application -> Application rejected

Legacy KYC intake (message start)
  -> Review legacy KYC properties
  -> Legacy review recorded
```

The source is a Camunda 7 model, not a deployable Camunda 8 process. Unsupported
and ambiguous form metadata is deliberately included so the skill creates
visible warnings and requests decisions instead of silently guessing. The file
uses attributes understood by the current Camunda Modeler BPMN moddle. In
particular, it uses the Modeler-supported `writable` spelling; older Camunda 7
inputs that contain `writeable` are described in the legacy-property coverage
below because that unknown attribute would produce a Modeler warning.

## Running the evaluation

1. Use `generated-task-forms-c7.bpmn` as the only model in a temporary project
   directory. Keep the original file unchanged.
2. Open the BPMN in Camunda Modeler to inspect the visual layout.
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
each generated-form owner, without additional child-level generated-form
findings. For legacy properties, the message text names `formProperty`, but the
`messageId` remains `form-data`; this is the converter's shared generated-form
category. Unrelated informational findings for custom properties and the
existing form reference may also be present.

| Owner | Generated-form source | Expected finding |
|---|---|---|
| `Start_KycApplication` | one `camunda:formData` | `messageId=form-data`, message element `formData` |
| `Task_CaptureApplicant` | one `camunda:formData` | `messageId=form-data`, message element `formData` |
| `Task_RequestDocuments` | duplicate `camunda:formData` plus direct `camunda:formProperty` | `messageId=form-data`, message element `formData` |
| `Task_ApproveCustomer` | one `camunda:formData` | `messageId=form-data`, message element `formData` |
| `Start_LegacyKycIntake` | direct `camunda:formProperty` elements | `messageId=form-data`, message element `formProperty` |
| `Task_LegacyKycReview` | direct `camunda:formProperty` elements | `messageId=form-data`, message element `formProperty` |

`Task_ManualReviewExistingForm` has only an existing C7 `formRef`; it is not a
Generated Task Form owner and should not receive a `form-data` finding.

The converted copy should remove the Camunda 7 generated-form elements. The
skill must use the original BPMN for form generation and must not reconstruct
the fields from the converted copy or erase the converter finding from the
report.

## Coverage matrix

### `Start_KycApplication`

This owner is the broad `formData` surface and uses
`camunda:formData@businessKey="applicationId"`:

| Field | C7 case | Expected C8 candidate or review |
|---|---|---|
| `applicationId` | `string`, static label/default, `required`, custom properties, business-key field | `textfield`; business-key handling is blocking |
| `applicantName` | `string`, static default, `minlength`, `maxlength` | `textfield` with length validation; review C7/C8 enforcement differences |
| `annualIncome` | bounded `long`, static default, `min`, strict C7 `max` | `number` with integer precision and strict-maximum decision |
| `legacyCreditLimit` | `long` outside JavaScript safe-integer range | `number` only after a safe-integer or string contract decision |
| `consentGiven` | `boolean`, static default, `required` | `checkbox`; decide whether unchecked is valid |
| `dateOfBirth` | `date`, explicit `dd/MM/yyyy` pattern, static default, `readonly` | date-only `datetime`; review ISO conversion and read-only enforcement |
| `riskTier` | ordered `enum`, static default, three `camunda:value` options | `select`; preserve option order and default |
| `nationalId` | built-in `validator` constraint naming a custom validator | `textfield` plus visible warning and backend redesign decision |
| `invalidConstraint` | `validator` with dynamic configuration | visible warning; do not evaluate or invent a validation rule |

### `Task_CaptureApplicant`

This owner demonstrates the simplest candidate that can become accepted after
the normal form review:

| Field | C7 case | Expected C8 candidate |
|---|---|---|
| `legalName` | `string` with static label and no default | `textfield` |
| `countryOfResidence` | `string` with static label and no default | `textfield` |
| `consentConfirmed` | `boolean` with a static default | `checkbox` |

The skill should still create a deterministic draft, present it to the user,
and add a deployment-bound `zeebe:formDefinition` only after explicit
acceptance.

### `Task_RequestDocuments`

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
* Direct `camunda:formProperty` mixed with form fields tests combined ordering.
* `businessKey="missingBusinessKeyField"` names no field and is blocking.

Expected result: a draft with stable component and warning ids, a complete
mapping/report table, and no automatic acceptance until all decisions are made.

### `Task_ApproveCustomer`

This owner is another small, realistic generated task form on the successful
KYC path:

| Field | C7 case | Expected C8 candidate or review |
|---|---|---|
| `approvalDecision` | required boolean with a static default | `checkbox`; review the `false`/required semantic difference |
| `approvalNotes` | plain string field | `textfield` |

### `Start_LegacyKycIntake`

This message start event covers legacy start-form properties:

* `caseReference` is a simple, static string candidate.
* `caseAlias` uses `variable="legacyCaseReference"` and requires an
  alias/data-mapping decision.

The non-none start trigger is deliberately included because the skill must ask
for review before treating it as the normal interactive start-form path. If
accepted, the source owner is still a start event, so its linked form uses
`zeebe:formDefinition` without a `zeebe:userTask` marker.

### `Task_LegacyKycReview`

This owner covers legacy `camunda:formProperty` semantics:

| Property | C7 case | Expected review |
|---|---|---|
| `legacyRequired` | `required="true"` | C7 presence semantics versus C8 nonempty validation |
| `legacyRequiredBoolean` | required boolean property | C7 accepts `false`; decide whether an affirmative value is intended |
| `legacyUnreadable` | `readable="false"` | whether to omit or redesign the field |
| `legacyNotWritable` | `writable="false"` | submitted-value enforcement |
| `legacyWriteableSpelling` | Modeler-compatible `writable="false"`; historical `writeable` spelling is documented above | preserve and flag the spelling when found in customer input |
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
* represented every unmappable type, constraint, alias, label/default
  expression, business-key difference, and existing-form conflict in both the
  draft and `MIGRATION_REPORT.md`;
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

Expected output is a set of reviewable drafts and a report, not a single
automatically accepted form. The custom and conflicting cases are included to
verify that the agent surfaces uncertainty rather than claiming lossless
conversion.
