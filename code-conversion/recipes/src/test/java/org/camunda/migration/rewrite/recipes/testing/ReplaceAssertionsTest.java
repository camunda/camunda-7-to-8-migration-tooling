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
}
