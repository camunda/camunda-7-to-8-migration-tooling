# Camunda user-task fixture

This fixture checks the model-side migration of a plain Camunda 7 user task.
It uses a message start event and a second user task with assignment and form
metadata.

```text
user-tasks-c7.bpmn
```

Run the migration skill with **Models only**, target **Camunda 8.9**, and the
Diagram Converter CLI or Agentic AI model approach. Keep the source file
unchanged and write the converted copy beside it as
`converted-c8-user-tasks-c7.bpmn`.

## Required converted model

The converted copy must contain exactly one `zeebe:userTask` extension for
each source user task:

```xml
<bpmn:userTask id="BareUserTask" name="Wait for approval">
  <bpmn:extensionElements>
    <zeebe:userTask />
  </bpmn:extensionElements>
</bpmn:userTask>
```

The task named `Review approval` must keep its user-task marker and preserve
its assignment and form metadata:

```xml
<bpmn:userTask id="AssignedUserTask" name="Review approval">
  <bpmn:extensionElements>
    <zeebe:userTask />
    <zeebe:assignmentDefinition
        assignee="=reviewer"
        candidateGroups="approvers" />
    <zeebe:formDefinition
        externalReference="camunda-forms:deployment:review.form" />
  </bpmn:extensionElements>
</bpmn:userTask>
```

The exact expression representation can differ when the converter records a
review finding. The converted copy must not omit the assignment or form
metadata. The copy must not add a `zeebe:taskDefinition` for either user task.

## Runtime regression

Deploy the converted BPMN to a Camunda 8.9 test target. Publish
`instantiationMessage` with a `reviewer` variable, then query the user-task API.
The assignment expression requires this variable. The process must expose both
user tasks instead of a job with type `io.camunda.zeebe:userTask`.

Complete `BareUserTask` and `AssignedUserTask` through the user-task API. The
process must complete. A created legacy user-task job is a failed regression.

Example c8ctl commands:

```sh
c8ctl deploy converted-c8-user-tasks-c7.bpmn --profile=local
c8ctl publish msg instantiationMessage \
  --variables='{"reviewer":"reviewer"}' \
  --profile=local
c8ctl list ut --profile=local --json
c8ctl complete ut <bare-task-key> --variables='{}' --profile=local
c8ctl list ut --profile=local --json
c8ctl complete ut <assigned-task-key> --variables='{}' --profile=local
c8ctl search jobs \
  --type=io.camunda.zeebe:userTask \
  --state=CREATED \
  --profile=local \
  --json
c8ctl get pi <process-instance-key> --profile=local --json
```

If the `reviewer` variable is missing, Camunda 8.9 raises an
`EXTRACT_VALUE_ERROR` incident when it creates `AssignedUserTask`.

## Review checklist

The evaluation is complete when the agent has:

* kept the original Camunda 7 BPMN unchanged;
* added one `zeebe:userTask` to each converted user task;
* preserved assignment and form metadata on `AssignedUserTask`;
* recorded unsupported semantics instead of silently dropping them;
* avoided a `zeebe:taskDefinition` fallback without an explicit user decision;
* deployed the converted BPMN;
* listed and completed both user tasks through the Camunda 8 user-task API; and
* asserted process completion.
