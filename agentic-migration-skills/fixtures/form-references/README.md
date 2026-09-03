# Loan form reference fixture

This directory is a small manual evaluation fixture for the
`migrate-c7-to-c8-code` skill. It covers the Camunda 7 form types that are
*referenced* rather than defined on the BPMN element, which the Diagram
Converter reports but cannot migrate: embedded HTML/JavaScript forms, external
form keys resolved by a custom application, and user tasks or start events with no form at all.

The companion fixture for `camunda:formData` Generated Task Forms is
[`../generated-task-forms`](../generated-task-forms). Keep the two separate: a
form belongs to exactly one procedure.

```text
form-references-c7.bpmn
src/main/webapp/forms/loan-details.html
```

Open `form-references-c7.bpmn` directly in Camunda Modeler. The model is a
deliberately simple, life-like loan process:

```text
Loan requested -> Capture loan details -> Approve loan -> Archive decision -> Loan processed
```

`src/main/webapp/forms/loan-details.html` is the embedded form referenced by the
first user task, placed where Camunda 7 expects an `embedded:app:` resource. It
contains only documented Camunda Forms SDK markers — no scripts, no AngularJS,
no `cam-choices`, no business key, no file handling — so it is the
`simple` branch of the embedded-form classification.

There are no service tasks, gateways, generated task forms, `formRef`
attributes, form handler classes, or dynamic form keys. The file is a Camunda 7
source model, not a deployable Camunda 8 process.

## Running the evaluation

1. Copy this directory into a temporary project directory and keep the
   originals unchanged.
2. Run the migration skill with **Models only**, target **Camunda 8.9**, and the
   Diagram Converter CLI approach when Java 21 is available. The agentic
   model-rewrite approach is an alternative when the CLI cannot be run.
3. Let the skill classify and inventory the form references from the original
   BPMN before it acts on any of them.
4. Answer the per-category questions. Exercise both branches at least once:
   decline the rebuild for one category and explicitly request it for another.
5. Review any generated draft and explicitly accept it before the skill links
   it. Do not accept a form merely because its JSON parses.

The fixture does not require a Maven or Gradle build. Use the temporary
directory as the migration skill project root.

## Expected classification

| Owner | Reference | Expected category | Content available |
|---|---|---|---|
| `Task_CaptureLoanDetails` | `embedded:app:forms/loan-details.html` | `c7-embedded-html-form` | yes — `src/main/webapp/forms/loan-details.html` |
| `Task_ApproveLoan` | `https://loans.example.com/forms/approval` | `c7-external-form-reference` | n/a |
| `Task_ArchiveDecision` | none | `c7-generic-task-form` | n/a |
| `Start_LoanRequested` | none | `c7-generic-task-form` | n/a |

A converter release with classified form-key findings emits one `form-key-embedded` TASK finding
on `Task_CaptureLoanDetails` and one `form-key-external` TASK finding on `Task_ApproveLoan`.
Older releases emit the generic `form-key` finding for both; the skill must still classify them
apart, because it classifies from the original source rather than from findings.

`Task_ArchiveDecision` and the top-level none start event `Start_LoanRequested`
produce no finding at all. Both must still appear in the inventory: Camunda 7
Tasklist rendered an ad-hoc form for each of them, and Camunda 8 renders
nothing, so a findings-only review misses the change entirely. The start event
is the one to watch — it is easy to skip when only user tasks are treated as
form owners.

The converted BPMN should copy both form keys verbatim into
`zeebe:formDefinition@externalReference`. That is a preserved reference, not a
migrated form, and neither category may be closed as "no action" because of it.

## Embedded form coverage

If the user asks to rebuild `Task_CaptureLoanDetails` as a Camunda 8 form, the
markers map to these candidates — every one still requires explicit review:

| Marker | Camunda 7 case | Expected Camunda 8 candidate |
|---|---|---|
| `applicantName` | `String`, labelled, HTML `required` | `textfield` with `validate.required` |
| `loanAmount` | `Double`, labelled, HTML `required` | `number`, decimal digits to be decided |
| `termMonths` | `Integer`, labelled | `number` with `decimalDigits: 0` |
| `firstPaymentDate` | `Date`, labelled | `datetime`, Java `Date`-to-ISO review |
| `termsAccepted` | `Boolean`, wrapped label | `checkbox` |

Layout, CSS classes, and the wrapped-label markup are not carried over. The
rebuilt form reproduces the data contract only.

## Review and validation checklist

The evaluation is complete when the agent has:

* classified all four owners into their categories from the original BPMN;
* inventoried the form-free user task **and** the form-free start event even
  though neither produced a finding;
* resolved and read `loan-details.html` and classified it `simple`;
* asked one decision per category instead of one per task;
* generated no form for a category the user did not explicitly ask to rebuild;
* for an explicitly requested rebuild, produced a deterministic draft, presented
  it for acceptance, linked it with a matching `formId` and a recorded binding
  decision, and removed the copied `externalReference` from that element;
* recorded the custom-application checklist, with an owner, for any kept
  external reference;
* left the original Camunda 7 BPMN and HTML untouched; and
* left no form-reference category marked "no action" on the strength of the
  copied reference alone.

Expected output is an inventory, explicit decisions, and at most the forms the
user asked for — not an automatically migrated set of forms.
