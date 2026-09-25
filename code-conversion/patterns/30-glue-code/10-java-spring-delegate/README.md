# JavaDelegate &#8594; Job Worker (Spring)

In Camunda 7, JavaDelegates are a common way to implement glue code. JavaDelegates might be

- Spring beans referenced via Expression language (``delegateExpression``)
- Java classes referenced via the class name (``class``)

JavaDelegates run in the same context as the engine. In addition to the DelegateExecution class that provides an interface to interact with the running process instance, a JavaDelegate can also call all engine services, like the Runtime service.

The code conversion patterns for the JavaDelegate cover the most important methods how a JavaDelegate can interact with the running process instance:

- class level changes
- getting and setting process variables
- reporting a failure
- raising an incident
- throwing a BPMN error

There are often multiple methods that achieve the same result. The patterns try to capture as many examples as possible. Delegate code that accesses the engine services is not covered here. Please refer to the patterns for the engine services. In general, delegate code that utilizes engines services is more difficult to migrate to Camunda 8.

## Transaction and security semantics

Without `camunda:asyncBefore` or another intervening asynchronous transaction boundary, a C7 JavaDelegate runs in the command that completes the preceding wait state. If the delegate fails, that command rolls back and the wait state remains incomplete. A C8 job worker runs outside the engine transaction. Its failure can consume retries and raise an incident after the preceding user task has completed. The worker does not share the C7 engine transaction or its thread-bound security context.

Do not describe moving the same Java body to a worker as equivalent synchronous behavior. Ask the user to choose C8 retries and incident handling, a BPMN error or compensation flow, or an explicit manual step. Ask the user to choose a worker-side transaction or security mechanism, or a code refactor, when the source relies on those contexts. Record the chosen behavior and accepted parity gap in `MIGRATION_REPORT.md`. The `SynchronousDelegateTransactionBoundaryTest` in the C8 code examples demonstrates the resulting process state. See [Handling a Failure](./handling-a-failure.md).
