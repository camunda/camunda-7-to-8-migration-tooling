package org.camunda.migration.rewrite.recipes.client.migrate;

import org.camunda.migration.rewrite.recipes.client.MigrateSignalMethodsRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceSignalMethodsTest implements RewriteTest {

    @Test
    void replaceSignalMethodsTest() {
    rewriteRun(
        spec -> spec.recipe(new MigrateSignalMethodsRecipe()),
        // language=java
        java(
            """
                                package org.camunda.community.migration.example;

                                import org.camunda.bpm.engine.ProcessEngine;
                                import org.camunda.bpm.engine.RuntimeService;
                                import io.camunda.client.CamundaClient;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.stereotype.Component;

                                import java.util.Map;

                                @Component
                                public class BroadcastSignalsTestClass {

                                    @Autowired
                                    private CamundaClient camundaClient;

                                    @Autowired
                                    private ProcessEngine engine;

                                    @Autowired
                                    private RuntimeService runtimeService;

                                    public void broadcastSignalMethods(String signalName, String executionId, String tenantId, Map<String, Object> variableMap) {
                                        engine.getRuntimeService().signalEventReceived(signalName);

                                        engine.getRuntimeService().signalEventReceived(signalName, executionId);

                                        engine.getRuntimeService().signalEventReceived(signalName, variableMap);

                                        engine.getRuntimeService().signalEventReceived(signalName, executionId, variableMap);

                                        runtimeService.createSignalEvent(signalName)
                                                .send();

                                        engine.getRuntimeService().createSignalEvent(signalName)
                                                .executionId(executionId)
                                                .send();

                                        engine.getRuntimeService().createSignalEvent(signalName)
                                                .tenantId(tenantId)
                                                .send();

                                        engine.getRuntimeService().createSignalEvent(signalName)
                                                .setVariables(variableMap)
                                                .send();

                                        engine.getRuntimeService().createSignalEvent(signalName)
                                                .executionId(executionId)
                                                .tenantId(tenantId)
                                                .send();

                                        engine.getRuntimeService().createSignalEvent(signalName)
                                                .tenantId(tenantId)
                                                .executionId(executionId)
                                                .send();

                                        engine.getRuntimeService().createSignalEvent(signalName)
                                                .executionId(executionId)
                                                .setVariables(variableMap)
                                                .send();

                                        engine.getRuntimeService().createSignalEvent(signalName)
                                                .setVariables(variableMap)
                                                .executionId(executionId)
                                                .send();

                                        engine.getRuntimeService().createSignalEvent(signalName)
                                                .tenantId(tenantId)
                                                .setVariables(variableMap)
                                                .send();

                                        engine.getRuntimeService().createSignalEvent(signalName)
                                                .setVariables(variableMap)
                                                .tenantId(tenantId)
                                                .send();

                                        engine.getRuntimeService().createSignalEvent(signalName)
                                                .executionId(executionId)
                                                .tenantId(tenantId)
                                                .setVariables(variableMap)
                                                .send();

                                        engine.getRuntimeService().createSignalEvent(signalName)
                                                .executionId(executionId)
                                                .setVariables(variableMap)
                                                .tenantId(tenantId)
                                                .send();

                                        engine.getRuntimeService().createSignalEvent(signalName)
                                                .tenantId(tenantId)
                                                .executionId(executionId)
                                                .setVariables(variableMap)
                                                .send();

                                        engine.getRuntimeService().createSignalEvent(signalName)
                                                .tenantId(tenantId)
                                                .setVariables(variableMap)
                                                .executionId(executionId)
                                                .send();

                                        engine.getRuntimeService().createSignalEvent(signalName)
                                                .setVariables(variableMap)
                                                .executionId(executionId)
                                                .tenantId(tenantId)
                                                .send();

                                        engine.getRuntimeService().createSignalEvent(signalName)
                                                .setVariables(variableMap)
                                                .tenantId(tenantId)
                                                .executionId(executionId)
                                                .send();
                                    }
                                }
                                """,
            """
                                package org.camunda.community.migration.example;

                                import org.camunda.bpm.engine.ProcessEngine;
                                import org.camunda.bpm.engine.RuntimeService;
                                import io.camunda.client.CamundaClient;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.stereotype.Component;

                                import java.util.Map;

                                @Component
                                public class BroadcastSignalsTestClass {

                                    @Autowired
                                    private CamundaClient camundaClient;

                                    @Autowired
                                    private ProcessEngine engine;

                                    @Autowired
                                    private RuntimeService runtimeService;

                                    public void broadcastSignalMethods(String signalName, String executionId, String tenantId, Map<String, Object> variableMap) {
                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .send()
                                                .join();

                                        // executionId was removed
                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .send()
                                                .join();

                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .variables(variableMap)
                                                .send()
                                                .join();

                                        // executionId was removed
                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .variables(variableMap)
                                                .send()
                                                .join();

                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .send()
                                                .join();

                                        // executionId was removed
                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .send()
                                                .join();

                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .tenantId(tenantId)
                                                .send()
                                                .join();

                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .variables(variableMap)
                                                .send()
                                                .join();

                                        // executionId was removed
                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .tenantId(tenantId)
                                                .send()
                                                .join();

                                        // executionId was removed
                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .tenantId(tenantId)
                                                .send()
                                                .join();

                                        // executionId was removed
                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .variables(variableMap)
                                                .send()
                                                .join();

                                        // executionId was removed
                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .variables(variableMap)
                                                .send()
                                                .join();

                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .variables(variableMap)
                                                .tenantId(tenantId)
                                                .send()
                                                .join();

                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .variables(variableMap)
                                                .tenantId(tenantId)
                                                .send()
                                                .join();

                                        // executionId was removed
                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .variables(variableMap)
                                                .tenantId(tenantId)
                                                .send()
                                                .join();

                                        // executionId was removed
                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .variables(variableMap)
                                                .tenantId(tenantId)
                                                .send()
                                                .join();

                                        // executionId was removed
                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .variables(variableMap)
                                                .tenantId(tenantId)
                                                .send()
                                                .join();

                                        // executionId was removed
                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .variables(variableMap)
                                                .tenantId(tenantId)
                                                .send()
                                                .join();

                                        // executionId was removed
                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .variables(variableMap)
                                                .tenantId(tenantId)
                                                .send()
                                                .join();

                                        // executionId was removed
                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .variables(variableMap)
                                                .tenantId(tenantId)
                                                .send()
                                                .join();
                                    }
                                }
                                """));
    }

}
