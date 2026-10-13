# Form-free user-task decisions

The Camunda 7 source has no form metadata on these user tasks, and the user chose to keep them
form-free. The `.bpmnlintrc` keeps `camunda-compat/user-task-definition` enabled. Each missing-form
finding stays blocking until the lint and runtime evidence in `model-lint-and-connectivity.md` is
recorded.

| Process ID | User-task ID | User decision |
|---|---|---|
| `task-listener` | `Task_Review` | Keep form-free |
| `task-listener-never` | `Task_Review` | Keep form-free |
| `task-listener-twice` | `Task_Review_One` | Keep form-free |
| `task-listener-twice` | `Task_Review_Two` | Keep form-free |
