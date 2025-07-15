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