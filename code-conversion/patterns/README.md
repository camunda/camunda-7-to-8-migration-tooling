# Camunda 7 to Camunda 8 Code Conversion Pattern Catalog

This catalog contains specific patterns on how to translate Camunda 7 code to Camunda 8. This patterns do not cover changes to the BPMN XML.

These patterns are programming-language-specific. For language-agnostic information about the Camunda 7 and Camunda 8 API endpoints, see the **[Camunda 7 API to Camunda 8 API Mapping Table](https://camunda-community-hub.github.io/camunda-7-to-8-code-conversion/)**.

> [!NOTE]  
> The pattern catalog was just kicked off and will be filled with more patterns throughout Q2 of 2025. The current patterns are more exemplary to discuss the structure. Feedback of course welcome.

<!-- The following content is automatically added with a Github Action from generate-catalog.js -->
<!-- BEGIN-CATALOG -->

## General thoughts and changes

Some changes need to happen on a project-wide level.

Patterns:

-   [Maven dependency and configuration](10-general/dependencies.md)
-   [Handling Process Variables](10-general/process-variables.md)

## Client code

Whenever your solutions calls the Camunda API, e.g., to start new process instances.

### `ProcessEngine`

The ProcessEngine offers various services (think RuntimeService) to interact with the Camunda 7 engine.

Patterns:

-   [Class-level Changes](20-client-code/10-process-engine/adjusting-the-java-class.md)
-   [Broadcast Signals](20-client-code/10-process-engine/broadcast-signals.md)
-   [Cancel Process Instance](20-client-code/10-process-engine/cancel-process-instance.md)
-   [Correlate Messages](20-client-code/10-process-engine/correlate-messages.md)
-   [Handle Variables](20-client-code/10-process-engine/handle-process-variables.md)
-   [Handle Resources](20-client-code/10-process-engine/handle-resources.md)
-   [handle user tasks](20-client-code/10-process-engine/handle-user-tasks.md)
-   [Raise Incidents](20-client-code/10-process-engine/raise-incidents.md)
-   [Search Process Definitions](20-client-code/10-process-engine/search-process-definitions.md)
-   [Starting Process Instances](20-client-code/10-process-engine/starting-process-instances.md)

## Glue code

Whenever you define code that is executed when a process arrives at a specific state in the process, specifically JavaDelegates and external task workers.

### JavaDelegate (Spring) &#8594; Job Worker (Spring)

In Camunda 7, JavaDelegates are a common way to implement glue code. Very often, JavaDelegates are Spring beans and referenced via Expression language in the BPMN xml.

Patterns:

-   [Class-level Changes](30-glue-code/10-java-spring-delegate/adjusting-the-java-class.md)
-   [Handling a BPMN error](30-glue-code/10-java-spring-delegate/handling-a-bpmn-error.md)
-   [Handling a Failure](30-glue-code/10-java-spring-delegate/handling-a-failure.md)
-   [Handling an Incident](30-glue-code/10-java-spring-delegate/handling-an-incident.md)
-   [Handling Process Variables](30-glue-code/10-java-spring-delegate/handling-process-variables.md)

### External Task Worker (Spring) &#8594; Job Worker (Spring)

In Camunda 7, external task workers are a way to implement glue code. They are deployed independently from the engine. Thus, they cannot access the engine's services.

Patterns:

-   [Class-level Changes](30-glue-code/20-java-spring-external-task-worker/adjusting-the-java-class.md)
-   [Handling a BPMN error](30-glue-code/20-java-spring-external-task-worker/handling-a-bpmn-error.md)
-   [Handling a Failure](30-glue-code/20-java-spring-external-task-worker/handling-a-failure.md)
-   [Handling an Incident](30-glue-code/20-java-spring-external-task-worker/handling-an-incident.md)
-   [Handling Process Variables](30-glue-code/20-java-spring-external-task-worker/handling-process-variables.md)

<!-- END-CATALOG -->
