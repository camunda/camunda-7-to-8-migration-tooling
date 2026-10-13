# Accepted form-free lint findings

The Camunda 7 source has no form metadata on these user tasks, and the user chose to keep them
form-free. The `camunda-compat/user-task-definition` missing-form finding is accepted only for these
tasks. The `.bpmnlintrc` keeps the rule enabled.

| Process ID | User-task ID | User decision |
|---|---|---|
| `task-listener` | `Task_Review` | Keep form-free |
| `task-listener-never` | `Task_Review` | Keep form-free |
| `task-listener-twice` | `Task_Review_One` | Keep form-free |
| `task-listener-twice` | `Task_Review_Two` | Keep form-free |
