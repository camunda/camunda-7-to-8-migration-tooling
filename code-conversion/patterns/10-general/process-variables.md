# Handling Process Variables

Handling of process variables in Camunda 7 is a complex topic. The engine supports various value types: primitive types like boolean, bytes, integer and string; file; object; and json and xml representations. The client code and glue code can specify how the variables are stored in the engine database. Camunda 7 offers two approaches to handle process variables: the [Java Object API](https://docs.camunda.org/manual/latest/user-guide/process-engine/variables/#java-object-api), and the [Typed Value API](https://docs.camunda.org/manual/latest/user-guide/process-engine/variables/#typed-value-api). Both approaches can be used at the same time.

In Camunda 8, all common value types are stored in JSON representation. This simplifies various aspects about handling process variables in client code and glue code.

The code conversion examples cover both Camunda 7 approaches to handle process variables. Naturally, both approaches are converted into the simplified JSON representation approach in Camunda 8.

TODO: Add proper links to:

* [Process variables in client code](https://github.com/camunda-community-hub/camunda-7-to-8-code-conversion/blob/conversion-pattern/patterns/20-client-code/10-process-engine/handle-process-variables.md)
* [Process variables in glue code (Java Delegate)](https://github.com/camunda-community-hub/camunda-7-to-8-code-conversion/blob/conversion-pattern/patterns/30-glue-code/10-java-spring-delegate/handling-process-variables.md)
* [Process variables in glue code (External Task Worker)](https://github.com/camunda-community-hub/camunda-7-to-8-code-conversion/blob/conversion-pattern/patterns/30-glue-code/20-java-spring-external-task-worker/handling-process-variables.md)