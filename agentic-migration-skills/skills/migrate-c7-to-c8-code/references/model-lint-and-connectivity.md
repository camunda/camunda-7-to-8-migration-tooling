# BPMN lint exceptions for DI-less sources and form-free user tasks

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked
(SHOULD) and an option is marked (MAY).

A **DI-less source** is a Camunda 7 BPMN file with no `bpmndi:BPMNDiagram` and no `bpmn:incoming`
or `bpmn:outgoing` children. A **form-free owner** is defined in
[`form-reference-migration.md`](form-reference-migration.md#generic-task-forms).

The converted copy of a DI-less source stays DI-less. Never invent BPMN DI. Never edit the original
source to silence a lint finding.

## Classify lint findings

Record the lint command, exit code, and complete output in `MIGRATION_REPORT.md`. Never describe
lint output with findings as clean. Never disable or ignore a rule in `.bpmnlintrc` to accept a
finding.

Accept a finding only when it matches a row below. Record each accepted finding in
`MIGRATION_REPORT.md` with its rule ID, process ID, element ID, and evidence.

| Finding | Accept only when | Required checks |
|---|---|---|
| `no-bpmndi` | The original source and the converted copy both lack `bpmndi:BPMNDiagram`. | None |
| `no-disconnected`, `no-implicit-start`, or `no-implicit-end` | The original source is DI-less. | Graph check and runtime check |
| `label-required` | The source element has no label, and the user chooses to keep it unlabeled. | None |
| `camunda-compat/user-task-definition` reporting only a missing form | The source user task is a form-free owner. The user chooses to keep it form-free. The converted copy has no `zeebe:formDefinition`. | Runtime check |
| Any other finding, or a listed rule with a different cause | Never | Keep the finding blocking. |

Camunda 8 does not require a form on a user task. See
[User task forms](https://docs.camunda.io/docs/components/modeler/bpmn/user-tasks/#user-task-forms).

## Graph check

Parse the original source and the converted copy with a namespace-aware XML parser. Never check
connectivity with regular expressions. For each process:

1. Resolve every `bpmn:sequenceFlow@sourceRef` and `@targetRef` to one flow node in the same scope.
2. Where `bpmn:incoming` or `bpmn:outgoing` children exist, confirm that they match the sequence
   flows. Their absence is valid.
3. Confirm that every flow node is on a path from a start event to an end event. Follow BPMN
   semantics for subprocesses, boundary events, and event subprocesses.

If the skill cannot verify a construct, then it records connectivity as unverified.

## Runtime check

Deploy the exact converted copy to the selected target version, or run a target process test that
deploys it. Start an instance and confirm that it reaches the expected end event. Where the
form-free exception applies, confirm that the instance reaches the user task. Complete the user task
through the target API or the application's real completion path. Never add a substitute form.

Record the target version, command, process ID, and observed outcome in
`.camunda-migration/validation/`.

## Readiness

Model lint passes only when the lint output is clean, or every finding is accepted and its required
checks pass. If a required check is unverified or cannot run, then the skill blocks model readiness.
Never infer readiness from lint output or static XML alone.
