# Handling Process Variables

Handling of process variables in Camunda 7 is a complex topic. The engine supports various value types: primitive types like boolean, bytes, integer and string; file; object; and json and xml representations. The client code and glue code can specify how the variables are stored in the engine database. Camunda 7 offers two approaches to handle process variables: the [Java Object API](https://docs.camunda.org/manual/latest/user-guide/process-engine/variables/#java-object-api), and the [Typed Value API](https://docs.camunda.org/manual/latest/user-guide/process-engine/variables/#typed-value-api). Both approaches can be used at the same time.

In Camunda 8, all common value types are stored in JSON representation. This simplifies various aspects about handling process variables in client code and glue code.

The code conversion examples cover both Camunda 7 approaches to handle process variables. Naturally, both approaches are converted into the simplified JSON representation approach in Camunda 8.

## Call-activity variable scope

Compare each C7 call's `camunda:in`, `camunda:out`, and delegated mappings with the converted copy.
Without mappings or a variable-mapping delegate, C7 passes no variables in either direction.
Camunda 8.8 copies all parent and child variables by default.

| C7 contract | Camunda 8 mapping |
|---|---|
| Selected parent inputs | Set `propagateAllParentVariables="false"` and add a `zeebe:input` for each value. |
| No C7 input mappings | Set `propagateAllParentVariables="false"` without input mappings. |
| All parent inputs | Keep all-parent propagation only when C7 sends the same scope. |
| Selected child outputs | Keep child propagation enabled and add a `zeebe:output` for each value. |
| No C7 output mappings | Set `propagateAllChildVariables="false"` without output mappings. |
| All child outputs | Keep all-child propagation only when C7 returns the same scope. |
| Custom mapping delegate | Compare its behavior with C8 mappings; keep mismatches unresolved. |

Camunda 8.8 supports [call-activity variable mappings](https://docs.camunda.io/docs/8.8/components/modeler/bpmn/call-activities/#variable-mappings).
Keep calls with unsupported or untested behavior unresolved.

TODO: Add proper links to:

* [Process variables in client code](https://github.com/camunda/camunda-7-to-8-migration-tooling/blob/main/code-conversion/patterns/20-client-code/10-process-engine/handle-process-variables.md)
* [Process variables in glue code (Java Delegate)](https://github.com/camunda/camunda-7-to-8-migration-tooling/blob/main/code-conversion/patterns/30-glue-code/10-java-spring-delegate/handling-process-variables.md)
* [Process variables in glue code (External Task Worker)](https://github.com/camunda/camunda-7-to-8-migration-tooling/blob/main/code-conversion/patterns/30-glue-code/20-java-spring-external-task-worker/handling-process-variables.md)
