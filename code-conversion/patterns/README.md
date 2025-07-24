# Camunda 7 to Camunda 8 Code Conversion Pattern Catalog

This catalog contains specific patterns on how to translate Camunda 7 code to Camunda 8. This patterns do not cover changes to the BPMN XML.

These patterns are programming-language-specific. For language-agnostic information about the Camunda 7 and Camunda 8 API endpoints, see the **[Camunda 7 API to Camunda 8 API Mapping Table](https://camunda-community-hub.github.io/camunda-7-to-8-code-conversion/)**.

> [!NOTE]  
> The pattern catalog was just kicked off and will be filled with more patterns throughout Q2 of 2025. The current patterns are more exemplary to discuss the structure. Feedback of course welcome.

<!-- The following content is automatically added with a Github Action from generate-catalog.js -->
<!-- BEGIN-CATALOG -->

Patterns:

- [Camunda 7 to 8 Code Conversion Patterns](ALL_IN_ONE.md)

## General thoughts and changes

Some changes need to happen on a development-project-wide level.

Patterns:

- [Maven dependency and configuration](10-general/dependencies.md)
- [Handling Process Variables](10-general/process-variables.md)

## Client code

Whenever your solutions calls the Camunda API, e.g., to start new process instances.


### `ProcessEngine`

The ProcessEngine offers various services (think RuntimeService) to interact with the Camunda 7 engine.

Patterns:

- [Class-level Changes](20-client-code/10-process-engine/adjusting-the-java-class.md)
- [Broadcast Signals](20-client-code/10-process-engine/broadcast-signals.md)
- [Cancel Process Instance](20-client-code/10-process-engine/cancel-process-instance.md)
- [Correlate Messages](20-client-code/10-process-engine/correlate-messages.md)
- [Handle Variables](20-client-code/10-process-engine/handle-process-variables.md)
- [Handle Resources](20-client-code/10-process-engine/handle-resources.md)
- [Handle User Tasks](20-client-code/10-process-engine/handle-user-tasks.md)
- [Raise Incidents](20-client-code/10-process-engine/raise-incidents.md)
- [Search Process Definitions](20-client-code/10-process-engine/search-process-definitions.md)
- [Starting Process Instances](20-client-code/10-process-engine/starting-process-instances.md)

## Glue code

Whenever you define code that is executed when a process arrives at a specific state in the process, specifically JavaDelegates and external task workers.


### JavaDelegate &#8594; Job Worker (Spring)

In Camunda 7, JavaDelegates are a common way to implement glue code. JavaDelegates might be

Patterns:

- [Class-level Changes](30-glue-code/10-java-spring-delegate/adjusting-the-java-class.md)
- [Handling a BPMN error](30-glue-code/10-java-spring-delegate/handling-a-bpmn-error.md)
- [Handling a Failure](30-glue-code/10-java-spring-delegate/handling-a-failure.md)
- [Handling an Incident](30-glue-code/10-java-spring-delegate/handling-an-incident.md)
- [Handling Process Variables](30-glue-code/10-java-spring-delegate/handling-process-variables.md)

### Expression &#8594; Job Worker (Spring)

In Camunda 7, you can use arbitrary expression in JUEL, the Java Unified Expression Language. Those expressions might access the Spring context as well as Camunda's context.


### External Task Worker (Spring) &#8594; Job Worker (Spring)

In Camunda 7, external task workers are a way to implement glue code. They are deployed independently from the engine. Thus, they cannot access the engine's services.

Patterns:

- [Class-level Changes](30-glue-code/20-java-spring-external-task-worker/adjusting-the-java-class.md)
- [Handling a BPMN error](30-glue-code/20-java-spring-external-task-worker/handling-a-bpmn-error.md)
- [Handling a Failure](30-glue-code/20-java-spring-external-task-worker/handling-a-failure.md)
- [Handling an Incident](30-glue-code/20-java-spring-external-task-worker/handling-an-incident.md)
- [Handling Process Variables](30-glue-code/20-java-spring-external-task-worker/handling-process-variables.md)

## Test Code

Code written to test your solution, e.g. using JUnit.


### Camunda Platform Assert &#8594; Camunda Process Test (CPT)

Most tests for Camunda 7 use [Camunda Platform Assert](https://github.com/camunda/camunda-bpm-platform/tree/master/test-utils/assert) combined with JUnit for automated unit tests, whereas in Camunda 8 you will use [Camunda Process Test (CPT)](https://docs.camunda.io/docs/next/apis-tools/testing/getting-started/) (starting from version 8.8).

Patterns:

- [Complete Test Case](40-test-assertions/10-assertions/10-complete-test-case.md)
- [Process Instance Assertions](40-test-assertions/10-assertions/20-process-instance.md)
- [Process Variable Assertions](40-test-assertions/10-assertions/30-process-variable.md)
- [User Task Assertions](40-test-assertions/10-assertions/40-user-task.md)
- [Message Correlation](40-test-assertions/10-assertions/50-message.md)
- [Job Execution in Test Cases](40-test-assertions/10-assertions/60-job.md)

<!-- END-CATALOG -->
