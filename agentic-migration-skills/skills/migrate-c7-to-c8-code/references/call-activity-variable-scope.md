# Call-Activity Variable Scope

Compare each C7 call's `camunda:in`, `camunda:out`, and delegated mappings with the converted copy.
Camunda 8.9 supports [call-activity mappings](https://docs.camunda.io/docs/8.9/components/modeler/bpmn/call-activities/#variable-mappings).
Without mappings or a variable-mapping delegate, C7 passes no variables in either direction.
Camunda 8.9 copies all parent and child variables by default.

| C7 contract | Camunda 8.9 mapping |
|---|---|
| Selected parent inputs | Set `propagateAllParentVariables="false"` and add a `zeebe:input` for each value. |
| No C7 input mappings | Set `propagateAllParentVariables="false"` without input mappings. |
| All parent inputs | Keep all-parent propagation only when C7 sends the same scope. |
| Selected child outputs | Keep child propagation enabled; `zeebe:output` mappings restrict the variables returned to the caller. |
| No C7 output mappings | Set `propagateAllChildVariables="false"` without output mappings. |
| All child outputs | Keep all-child propagation only when C7 returns the same scope. |
| Custom mapping delegate | Compare its behavior with C8 mappings; keep mismatches unresolved. |

Camunda 8.9 passes the parent's Business ID to the child independently of process variables.
If the C7 call sets a different child business key, then keep the call **needs review** until the
user selects a compatible mapping.

Test selected inputs with an extra parent-only variable. Check the child's Business ID separately.
If deployment blocks testing, then record the blocker and keep scope parity **needs review**.
