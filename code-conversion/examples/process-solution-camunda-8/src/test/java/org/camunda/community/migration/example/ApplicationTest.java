package org.camunda.community.migration.example;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ElementSelectors.byName;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.process.test.api.CamundaSpringProcessTest;

@SpringBootTest
//(properties = {"camunda.client.worker.defaults.enabled=true"})
@CamundaSpringProcessTest
public class ApplicationTest {

    @Autowired
    private CamundaClient client;

    @Test
    void shouldRunProcess_withUserTask() {
		HashMap<String, Object> variables = new HashMap<String, Object>();
		variables.put("x", 7);
		
		ProcessInstanceEvent processInstance = client.newCreateInstanceCommand()
				.bpmnProcessId("sample-process-solution-process")
				.latestVersion()
				.variables(variables)
				.send().join();    	
      
      assertThat(processInstance).hasActiveElements(byName("Say hello to demo"));
      
      // C7 convenience: complete(task());
      complete(task(processInstance));
      
      assertThat(processInstance).isCompleted()
      	.hasCompletedElements("Event_GreaterThan5");
      
      // Additional check to verify the expression is working properly
      assertThat(processInstance).hasVariableNames("theAnswer");
      assertThat(processInstance).hasVariable("theAnswer", 42);
    }

	@Test
    void shouldRunProcess_withoutUserTask() {
		HashMap<String, Object> variables = new HashMap<String, Object>();
		variables.put("x", 5);
		
		ProcessInstanceEvent processInstance = client.newCreateInstanceCommand()
				.bpmnProcessId("sample-process-solution-process")
				.latestVersion()
				.variables(variables)
				.send().join();    	
      
      assertThat(processInstance).isCompleted()
      	.hasCompletedElements("Event_SmallerThan5");
    }

    private void complete(UserTask task) {
        client.newUserTaskCompleteCommand(task.getUserTaskKey()).send().join();
	}

	private UserTask task(ProcessInstanceEvent processInstance) {
		SearchResponse<UserTask> tasks = client.newUserTaskSearchRequest()
		  	.filter((f) -> f.bpmnProcessId(processInstance.getBpmnProcessId()))
		  	.send().join();
		  assertEquals(1, tasks.items().size());
		  return tasks.items().get(0);
	}

}