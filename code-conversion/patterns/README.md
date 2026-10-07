# Camunda 7 to Camunda 8 Code Conversion Pattern Catalog

This catalog contains specific patterns on how to translate Camunda 7 code to Camunda 8. This patterns do not cover changes to the BPMN XML.

These patterns are programming-language-specific. For language-agnostic information about the Camunda 7 and Camunda 8 API endpoints, see the **[Camunda 7 API to Camunda 8 API Mapping Table](https://camunda.github.io/camunda-7-to-8-migration-tooling/)**.

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
- [Count Query Results](20-client-code/10-process-engine/count-query-results.md)
- [Evaluate Decisions (DMN)](20-client-code/10-process-engine/evaluate-decisions.md)
- [Handle Variables](20-client-code/10-process-engine/handle-process-variables.md)
- [Handle Resources](20-client-code/10-process-engine/handle-resources.md)
- [Handle User Tasks](20-client-code/10-process-engine/handle-user-tasks.md)
- [Raise Incidents](20-client-code/10-process-engine/raise-incidents.md)
- [Search Process Definitions](20-client-code/10-process-engine/search-process-definitions.md)
- [Search Process Instances](20-client-code/10-process-engine/search-process-instances.md)
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


### Camunda Platform Assert to Camunda Process Test (CPT)

Most Camunda 7 tests use [Camunda Platform Assert](https://github.com/camunda/camunda-bpm-platform/tree/master/test-utils/assert) with JUnit. Camunda 8.8 and later use [Camunda Process Test (CPT)](https://docs.camunda.io/docs/apis-tools/testing/getting-started/). See [the complete assertion mapping](https://github.com/camunda/camunda-7-to-8-migration-tooling/blob/main/code-conversion/patterns/40-test-assertions/10-assertions/80-assertion-mapping.md) for the public Camunda 7 assertions.

Patterns:

- [Complete Test Case](40-test-assertions/10-assertions/10-complete-test-case.md)
- [Process Instance Assertions](40-test-assertions/10-assertions/20-process-instance.md)
- [Process Variable Assertions](40-test-assertions/10-assertions/30-process-variable.md)
- [User Task Assertions](40-test-assertions/10-assertions/40-user-task.md)
- [Message Correlation](40-test-assertions/10-assertions/50-message.md)
- [Job Execution in Test Cases](40-test-assertions/10-assertions/60-job.md)
- [Executable Entry-Point Coverage](40-test-assertions/10-assertions/70-executable-entry-points.md)
- [Camunda 7 Assertion Mapping](40-test-assertions/10-assertions/80-assertion-mapping.md)

### Test Setup

These patterns map Camunda 7 process-test harnesses, deployments, and Spring Boot tests to Camunda Process Test (CPT). The harness and Spring APIs are available from Camunda 8.8. `@TestDeployment` requires 8.9.

Patterns:

- [JUnit Harness](40-test-assertions/20-test-setup/10-junit-harness.md)
- [Test Deployment](40-test-assertions/20-test-setup/20-deployment.md)
- [Spring Boot Test Setup](40-test-assertions/20-test-setup/30-spring-boot-test.md)

### Mocks

These patterns preserve the mock boundary of Camunda 7 process tests. CPT's mock-worker, child-process, and DMN utilities are available from Camunda 8.8.

Patterns:

- [Delegate and Worker Mocks](40-test-assertions/30-mocks/10-delegate-mocks.md)
- [Call Activity and Decision Mocks](40-test-assertions/30-mocks/20-call-activity-and-decision-mocks.md)

### Decision Tests

These patterns map Camunda 7 DMN engine and decision-service tests to Camunda Process Test. The DMN evaluation and assertion APIs are available from Camunda 8.8. `@TestDeployment` requires 8.9.

Patterns:

- [DMN Decision Test Migration](40-test-assertions/40-decisions/10-decision-tests.md)

### Coverage and Scenario Tests

Camunda 8.8 and later generate CPT coverage reports. CPT conditional behavior for scenario tests requires Camunda 8.9.

Patterns:

- [Process Test Coverage](40-test-assertions/50-coverage-and-scenarios/10-coverage.md)
- [Camunda Platform Scenario Tests](40-test-assertions/50-coverage-and-scenarios/20-scenario-tests.md)

<!-- END-CATALOG -->
