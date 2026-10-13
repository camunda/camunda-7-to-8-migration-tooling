# BPMN lint and connectivity for DI-less sources and form-free tasks

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked
(SHOULD) and an option is marked (MAY).

## Scope

A **DI-less source** is a Camunda 7 BPMN file with no `bpmndi:BPMNDiagram`, no `bpmn:incoming`
children, and no `bpmn:outgoing` children.

Use this reference when the original BPMN source is DI-less or the user chooses to keep a form-free
user task. The DI-less checks apply to every model-conversion approach, including M2. The form-free
exception applies regardless of source DI or conversion approach.

The skill parses both the original source and the converted copy with a namespace-aware XML parser.
The skill records DI and incoming/outgoing presence for each file before it evaluates lint findings.

The skill never edits the original source. The skill never invents BPMN DI to make an editor lint
rule pass. When the source has BPMN DI, the skill follows the existing DI-preservation check. The
form-free exception does not relax that check. A converted copy that loses source DI is a regression.

When the source is DI-less, the converted copy must remain DI-less unless the user explicitly
approves a layout change. The skill does not treat the source's missing DI as a conversion regression.

## Run and record target lint

The skill runs `c8ctl bpmn lint` on each converted copy with the selected Camunda target version.
The skill records the CLI version, target version, command, exit code, and complete lint output in
`MIGRATION_REPORT.md` or `.camunda-migration/validation/`.

The raw lint result passes only when the CLI reports zero errors and zero warnings. The skill never
describes a raw lint result with findings as clean.

The skill never adds or changes a project-wide `.bpmnlintrc` to disable lint rules. The skill
records each accepted exception against its exact rule ID, process ID, element ID, source evidence,
and user decision. The skill leaves every unclassified finding blocking.

| Finding | Required evidence before a scoped exception |
|---|---|
| `no-bpmndi` | The original source and converted copy both lack `bpmndi:BPMNDiagram`. Record the finding as inherited presentation feedback. Do not call the raw lint result clean. |
| `no-disconnected` | The namespace-aware graph check below passes for the original source and converted copy. The target deployment and process test also pass. Scope the exception to the exact lint finding. |
| `no-implicit-start` or `no-implicit-end` | The graph check confirms explicit start and end events and a complete path in the affected process. The target deployment and process test also pass. Scope the exception to the exact lint finding. |
| `camunda-compat/user-task-definition` reporting only a missing form | The original source inventory proves that the exact user task has no form metadata. The user explicitly chooses to keep that task form-free. The converted copy still has no form metadata. |
| `label-required` | Ask the user before adding a label that was absent from the source. Record the user's choice for the exact element. Do not suppress other lint findings. |
| Any other finding, or a finding with a different cause | Keep the finding blocking until it is fixed. Do not apply an exception in this table to a different cause. |

The form-free exception covers only the form-presence part of a lint finding. It does not cover a
missing `<zeebe:userTask />`, an invalid task implementation, or any other rule reported for the
same user task.

## Check the source and target graph

The skill uses BPMN namespace-aware XML queries. The skill never checks XML connectivity with
regular expressions.

For each process scope, the skill checks the original source and converted copy:

1. The skill confirms that each flow-node ID is unique within its scope.
2. The skill resolves every `bpmn:sequenceFlow@sourceRef` and `bpmn:sequenceFlow@targetRef` to one
   flow node in the same scope.
3. When `bpmn:incoming` or `bpmn:outgoing` children exist, the skill confirms that each reference
   names a sequence flow with the matching target or source. Their absence is valid.
4. The skill builds connectivity from the sequence-flow references, not from optional incoming or
   outgoing children.
5. The skill confirms that every executable flow node and sequence flow is on a valid
   start-to-end path. The skill checks event-subprocess, boundary-event, and nested-scope paths
   according to BPMN semantics.

If the checker cannot validate a BPMN construct or process scope, the skill records connectivity as
unverified. The skill does not mark model readiness as passing until a capable checker or a manual
review verifies that scope.

The regression fixture at `fixtures/model-lint-readiness/` covers a simple process. Its verifier
fails closed for subprocesses, boundary events, and transactions. The fixture verifier is not a
general BPMN validator.

## Verify the form-free decision

A **form-free owner** has no form metadata in the original BPMN source. For a user task, the skill
checks the owner for `camunda:formData`, `camunda:formProperty`, `camunda:formKey`, `camunda:formRef`,
and other source form references. The skill also checks the converted copy for
`zeebe:formDefinition`.

The skill asks the user whether to keep a form-free user task form-free. The skill applies the lint
exception only to that user task after the user chooses this option. The skill records the process
ID, user-task ID, source inventory result, and choice in `MIGRATION_REPORT.md`.

The exception does not apply when the source contains form metadata. The skill follows
`references/form-reference-migration.md` for every form-bearing owner. The skill never hides a
missing form migration by disabling a project-wide rule.

Camunda 8.9 documentation describes forms as typical for user tasks, not required. It also allows a
user task to refer users to another application. See [User task forms](https://docs.camunda.io/docs/8.9/components/modeler/bpmn/user-tasks/#user-task-forms).
For another target version, the skill checks that version's documentation before it accepts the
same UX decision.

## Require target runtime evidence

The skill deploys the exact converted copies to a Camunda 8 target that matches the selected
version, or runs the project's target-version process test suite.

For each affected executable process, the skill starts an instance with the expected inputs. The
skill verifies that the instance reaches the affected user task, completes the task through the
target API or the application's real task-completion path, and reaches the expected process end.
For a form-free user task, the skill does not create or attach a substitute form. The skill tests
the selected API or application path that completes the task.

The skill records the target version, deployment or test command, process ID, user-task ID, and
observed outcome in `.camunda-migration/validation/`. The skill summarizes the evidence in
`MIGRATION_REPORT.md`.

| Gate | Passing condition | Otherwise |
|---|---|---|
| Raw target lint | The CLI reports zero errors and zero warnings. | Record the raw findings. |
| Scoped lint exceptions | Every exception matches a row above and names its exact rule and element. | Keep the affected finding blocking. |
| Graph connectivity | Every checked process scope has resolved references and verified start-to-end paths. | Keep connectivity unverified or failing. |
| Target runtime | The deployment and process behavior checks pass on the selected target version. | Mark readiness blocked or unverified. |
| Model readiness | Raw lint is clean, or every remaining finding has a valid scoped exception. Graph and runtime gates pass. | Do not mark model readiness as passing. |

When the target runtime or a capable graph checker is unavailable, the skill records the check as
blocked or unverified. The skill never infers readiness from lint output, static XML, or a successful
compile alone.
