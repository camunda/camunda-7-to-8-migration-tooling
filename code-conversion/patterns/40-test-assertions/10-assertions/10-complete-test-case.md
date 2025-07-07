# Complete Test Case

A typical test case includes:

- Bootstrapping the application (e.g. with Spring Boot as shown here)
- Starting a process instance via the client API
- Asserting key milestones such as:
  - Reaching user tasks
  - Completing the process
  - Validating variable values

Note: Distinguish between tests that rely on job workers and those that do not. See our [testing best practices](/components/best-practices/development/testing-process-definitions/) for more context and [Job Execution in Test Cases](./60-job.md) for details.

## Camunda 7: 

This example starts a process instance, waits for a user task, completes it, and validates the result.

[View full source code](https://github.com/camunda-community-hub/camunda-7-to-8-code-conversion/blob/main/examples/process-solution-camunda-7/src/test/java/org/camunda/community/migration/example/ApplicationTest.java).

```java
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.task;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.complete;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.runtimeService;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.findId;

@SpringBootTest
public class ApplicationTest {	
  @Test
  void testHappyPathWithUserTask() {
    ProcessInstance processInstance = runtimeService().startProcessInstanceByKey(
                "sample-process-solution-process",
                Variables.createVariables().putValue("x", 7));
    // assert / verify that we arrive in the user task with the name "Say hello to demo"
    assertThat(processInstance).isWaitingAt(findId("Say hello to demo"));
    // complete that task, so that the process instance advances
    complete(task());
    // Assert that it completed in the right end event, and that a Spring Bean hooked into the service task has written the expected process variable
    assertThat(processInstance).isEnded().hasPassed("Event_GreaterThan5");
    assertThat(processInstance).variables().containsEntry("theAnswer", 42);
  }
}
```

## Camunda 8: 

Camunda 8 uses its client APIs and [Camunda Process Test (CPT)](https://docs.camunda.io/docs/next/apis-tools/testing/getting-started/) for the same test case.

 [View full source code](https://github.com/camunda-community-hub/camunda-7-to-8-code-conversion/blob/main/examples/process-solution-camunda-8/src/test/java/org/camunda/community/migration/example/ApplicationTest.java)

```java
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.process.test.api.CamundaSpringProcessTest;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ElementSelectors.byName;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@CamundaSpringProcessTest
public class ApplicationTest {

    @Autowired
    private CamundaClient client;

    @Test
    void testHappyPathWithUserTask() {
		HashMap<String, Object> variables = new HashMap<String, Object>();
		variables.put("x", 7);
		
		ProcessInstanceEvent processInstance = client.newCreateInstanceCommand()
				.bpmnProcessId("sample-process-solution-process")
				.latestVersion()
				.variables(variables)
				.send().join();    	
      
      assertThat(processInstance).hasActiveElements(byName("Say hello to demo"));
      
      // C7 convenience method is missing in Camunda 8 at the moment
      complete(task(processInstance));
      
      assertThat(processInstance).isCompleted()
      	.hasCompletedElements("Event_GreaterThan5");
      
      assertThat(processInstance).hasVariable("theAnswer", 42);
    }
```

This code requires custom utility methods (`task` and `complete`) to query tasks and simulate user task completion. 
See [User Task Assertions](./40-user-task.md) for details and a code sample.
Such convenience methods may be added to CPT itself over time.
