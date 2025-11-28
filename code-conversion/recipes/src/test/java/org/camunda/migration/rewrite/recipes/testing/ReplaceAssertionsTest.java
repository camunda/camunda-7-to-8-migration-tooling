package org.camunda.migration.rewrite.recipes.testing;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

public class ReplaceAssertionsTest implements RewriteTest {

    @Test
    void replaceTestingMethodsTest() {
    rewriteRun(
        spec ->
            spec.recipeFromResources(
                "org.camunda.migration.rewrite.recipes.AllClientMigrateRecipes"),
        // language=java
        java(
            """
                                package org.camunda.community.migration.example;

                                import org.camunda.bpm.engine.RuntimeService;
                                import org.camunda.bpm.engine.runtime.ProcessInstance;
                                import io.camunda.client.CamundaClient;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.boot.test.context.SpringBootTest;
                                import org.junit.jupiter.api.Test;

                                import java.util.Map;

                                import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.assertThat;

                                @SpringBootTest
                                public class Testcases {

                                    @Autowired
                                    private CamundaClient camundaClient;

                                    @Autowired
                                    private RuntimeService runtimeService;

                                    @Test
                                    void somePath() {
                                        ProcessInstance processInstance = runtimeService
                                                 .startProcessInstanceByKey(
                                                         "sample-process-solution-process",
                                                         Map.ofEntries(Map.entry("x", 7))
                                                 );
                                        assertThat(processInstance).isWaitingAt("xxx");
                                        assertThat(processInstance).isEnded().hasPassed("yyy");
                                    }
                                }
                                """,
            """
                                package org.camunda.community.migration.example;
                                import io.camunda.client.api.response.ProcessInstanceEvent;
                                import org.camunda.bpm.engine.RuntimeService;
                                import io.camunda.client.CamundaClient;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.boot.test.context.SpringBootTest;
                                import org.junit.jupiter.api.Test;

                                import java.util.Map;

                                import static io.camunda.process.test.api.CamundaAssert.assertThat;

                                @SpringBootTest
                                public class Testcases {

                                    @Autowired
                                    private CamundaClient camundaClient;

                                    @Autowired
                                    private RuntimeService runtimeService;

                                    @Test
                                    void somePath() {
                                        ProcessInstanceEvent processInstance = camundaClient
                                                .newCreateInstanceCommand()
                                                .bpmnProcessId("sample-process-solution-process")
                                                .latestVersion()
                                                .variables(Map.ofEntries(Map.entry("x", 7)))
                                                .send()
                                                .join();
                                        assertThat(processInstance).hasActiveElements("xxx");
                                        assertThat(processInstance).isCompleted().hasCompletedElements("yyy");
                                    }
                                }
                                """));
    }

    @Test
    void replaceTaskTestingMethodsTest() {
        rewriteRun(
                spec ->
                        spec.recipeFromResources(
                                "org.camunda.migration.rewrite.recipes.AllClientMigrateRecipes"),
                // language=java
                java(
                        """
package org.camunda.community.migration.example;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import io.camunda.client.CamundaClient;
import org.camunda.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.assertThat;

@SpringBootTest
public class Testcases {

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private RuntimeService runtimeService;
    
    @Autowired
    private TaskService taskService;

    @Test
    void somePath(Date someDate) {
        ProcessInstance processInstance = runtimeService
                 .startProcessInstanceByKey(
                         "sample-process-solution-process",
                         Map.ofEntries(Map.entry("x", 7))
                 );
                 
        List<Task> userTasks = taskService.createTaskQuery()
                .processDefinitionKey("sample-process-solution-process")
                .dueBefore(someDate)
                .list();
                
        Task firstTask = userTasks.get(0);

        assertThat(processInstance).isWaitingAt("xxx");
        assertThat(firstTask).isAssignedTo("John Doe");
        assertThat(processInstance).isEnded().hasPassed("yyy");
        assertThat(processInstance).variables().containsEntry("a", "b");
    }
}
""",
                        """
package org.camunda.community.migration.example;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.UserTask;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import io.camunda.client.CamundaClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byTaskName;

@SpringBootTest
public class Testcases {

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private RuntimeService runtimeService;
    
    @Autowired
    private TaskService taskService;

    @Test
    void somePath(Date someDate) {
        ProcessInstanceEvent processInstance = camundaClient
                .newCreateInstanceCommand()
                .bpmnProcessId("sample-process-solution-process")
                .latestVersion()
                .variables(Map.ofEntries(Map.entry("x", 7)))
                .send()
                .join();
                
        List<UserTask> userTasks = camundaClient
                .newUserTaskSearchRequest()
                .filter(filter -> filter.bpmnProcessId("sample-process-solution-process")
                        .dueDate(dateTimeProperty -> dateTimeProperty.lt(someDate.toInstant().atOffset(ZoneOffset.UTC))))
                .send()
                .join()
                .items();

        UserTask firstTask = userTasks.get(0);
        
        assertThat(processInstance).hasActiveElements("xxx");
        assertThat(byTaskName(firstTask.getName())).hasAssignee("John Doe");
        assertThat(processInstance).isCompleted().hasCompletedElements("yyy");
        assertThat(processInstance).isCreated().hasVariable("a", "b");
    }
}
                                            """));
    }
}
