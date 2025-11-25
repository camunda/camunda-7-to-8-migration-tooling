package org.camunda.migration.rewrite.recipes.client.migrate;

import org.camunda.migration.rewrite.recipes.client.MigrateCancelProcessInstanceMethodsRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceCancelProcessInstanceMethodsTest implements RewriteTest {

    @Test
    void replaceCancelProcessInstanceMethodsTest() {
    rewriteRun(
        spec -> spec.recipe(new MigrateCancelProcessInstanceMethodsRecipe()),
        // language=java
        java(
            """
                                package org.camunda.community.migration.example;

                                import org.camunda.bpm.engine.ProcessEngine;
                                import io.camunda.client.CamundaClient;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.stereotype.Component;

                                @Component
                                public class CancelProcessInstanceTestClass {

                                    @Autowired
                                    private ProcessEngine engine;

                                    @Autowired
                                    private CamundaClient camundaClient;

                                    public void cancelProcessInstance(String processInstanceId, String deleteReason) {
                                        engine.getRuntimeService().deleteProcessInstance(processInstanceId, deleteReason);
                                    }
                                }
                                """,
            """
                                package org.camunda.community.migration.example;

                                import org.camunda.bpm.engine.ProcessEngine;
                                import io.camunda.client.CamundaClient;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.stereotype.Component;

                                @Component
                                public class CancelProcessInstanceTestClass {

                                    @Autowired
                                    private ProcessEngine engine;

                                    @Autowired
                                    private CamundaClient camundaClient;

                                    public void cancelProcessInstance(String processInstanceId, String deleteReason) {
                                        // delete reason was removed
                                        camundaClient
                                                .newCancelInstanceCommand(Long.valueOf(processInstanceId))
                                                .send()
                                                .join();
                                    }
                                }
                                """));
    }
}
