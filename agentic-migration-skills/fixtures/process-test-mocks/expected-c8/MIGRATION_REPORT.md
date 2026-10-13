# Scoped form-free user-task lint exceptions

The Camunda 7 source inventory finds no form metadata on these user tasks. The converted copies have
no `zeebe:formDefinition` on the listed tasks. The inventory checks `camunda:formKey`,
`camunda:formRef`, `camunda:formRefBinding`, `camunda:formRefVersion`, `camunda:formHandlerClass`,
`camunda:formData`, and `camunda:formProperty`.
The fixture records the user decision to keep each task form-free. The expected project keeps
`camunda-compat/user-task-definition` enabled. The exception covers only its missing-form finding
for these process and task IDs.

| Process ID | User-task ID | Source inventory | Converted copy | User decision |
|---|---|---|---|---|
| `task-listener` | `Task_Review` | No form metadata | No `zeebe:formDefinition` | Keep form-free |
| `task-listener-never` | `Task_Review` | No form metadata | No `zeebe:formDefinition` | Keep form-free |
| `task-listener-twice` | `Task_Review_One` | No form metadata | No `zeebe:formDefinition` | Keep form-free |
| `task-listener-twice` | `Task_Review_Two` | No form metadata | No `zeebe:formDefinition` | Keep form-free |
