package org.camunda.migration.rewrite.recipes.client.migrate;

import static org.openrewrite.java.Assertions.java;

import org.camunda.migration.rewrite.recipes.client.MigrateMessageMethodsRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

class ReplaceMessageMethodsTest implements RewriteTest {

  @Test
  void replaceMessageMethodsTest() {
    rewriteRun(
        spec -> spec.recipe(new MigrateMessageMethodsRecipe()),
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
public class CorrelateMessagesTestClass {

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private RuntimeService runtimeService;

    public void correlateMessageMethods(String messageName, String executionId, String businessKey, String tenantId, Map<String, Object> correlationKeys, Map<String, Object> variableMap) {
        engine.getRuntimeService().messageEventReceived(messageName, executionId);

        engine.getRuntimeService().messageEventReceived(messageName, executionId, variableMap);

        engine.getRuntimeService().correlateMessage(messageName);

        engine.getRuntimeService().correlateMessage(messageName, businessKey);

        engine.getRuntimeService().correlateMessage(messageName, correlationKeys);

        engine.getRuntimeService().correlateMessage(messageName, correlationKeys, variableMap);

        engine.getRuntimeService().correlateMessage(messageName, businessKey, correlationKeys, variableMap);

        runtimeService.createMessageCorrelation(messageName)
                .correlate();

        engine.getRuntimeService().createMessageCorrelation(messageName)
                .processInstanceBusinessKey(businessKey)
                .correlate();

        engine.getRuntimeService().createMessageCorrelation(messageName)
                .tenantId(tenantId)
                .correlate();

        engine.getRuntimeService().createMessageCorrelation(messageName)
                .setVariables(variableMap)
                .correlate();

        engine.getRuntimeService().createMessageCorrelation(messageName)
                .processInstanceBusinessKey(businessKey)
                .tenantId(tenantId)
                .correlate();

        engine.getRuntimeService().createMessageCorrelation(messageName)
                .tenantId(tenantId)
                .processInstanceBusinessKey(businessKey)
                .correlate();

        engine.getRuntimeService().createMessageCorrelation(messageName)
                .processInstanceBusinessKey(businessKey)
                .setVariables(variableMap)
                .correlate();

        engine.getRuntimeService().createMessageCorrelation(messageName)
                .setVariables(variableMap)
                .processInstanceBusinessKey(businessKey)
                .correlate();

        engine.getRuntimeService().createMessageCorrelation(messageName)
                .tenantId(tenantId)
                .setVariables(variableMap)
                .correlate();

        engine.getRuntimeService().createMessageCorrelation(messageName)
                .setVariables(variableMap)
                .tenantId(tenantId)
                .correlate();

        engine.getRuntimeService().createMessageCorrelation(messageName)
                .processInstanceBusinessKey(businessKey)
                .tenantId(tenantId)
                .setVariables(variableMap)
                .correlate();

        engine.getRuntimeService().createMessageCorrelation(messageName)
                .processInstanceBusinessKey(businessKey)
                .setVariables(variableMap)
                .tenantId(tenantId)
                .correlate();

        engine.getRuntimeService().createMessageCorrelation(messageName)
                .tenantId(tenantId)
                .processInstanceBusinessKey(businessKey)
                .setVariables(variableMap)
                .correlate();

        engine.getRuntimeService().createMessageCorrelation(messageName)
                .tenantId(tenantId)
                .setVariables(variableMap)
                .processInstanceBusinessKey(businessKey)
                .correlate();

        engine.getRuntimeService().createMessageCorrelation(messageName)
                .setVariables(variableMap)
                .processInstanceBusinessKey(businessKey)
                .tenantId(tenantId)
                .correlate();

        engine.getRuntimeService().createMessageCorrelation(messageName)
                .setVariables(variableMap)
                .tenantId(tenantId)
                .processInstanceBusinessKey(businessKey)
                .correlate();
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
public class CorrelateMessagesTestClass {

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private RuntimeService runtimeService;

    public void correlateMessageMethods(String messageName, String executionId, String businessKey, String tenantId, Map<String, Object> correlationKeys, Map<String, Object> variableMap) {
        // executionId was removed
        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .send()
                .join();

        // executionId was removed
        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .variables(variableMap)
                .send()
                .join();

        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .send()
                .join();

        // processInstanceBusinessKey was removed
        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .send()
                .join();

        // correlationKeys were removed
        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .send()
                .join();

        // correlationKeys were removed
        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .variables(variableMap)
                .send()
                .join();

        // processInstanceBusinessKey was removed
        // correlationKeys were removed
        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .variables(variableMap)
                .send()
                .join();

        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .send()
                .join();

        // processInstanceBusinessKey was removed
        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .send()
                .join();

        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .tenantId(tenantId)
                .send()
                .join();

        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .variables(variableMap)
                .send()
                .join();

        // processInstanceBusinessKey was removed
        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .tenantId(tenantId)
                .send()
                .join();

        // processInstanceBusinessKey was removed
        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .tenantId(tenantId)
                .send()
                .join();

        // processInstanceBusinessKey was removed
        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .variables(variableMap)
                .send()
                .join();

        // processInstanceBusinessKey was removed
        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .variables(variableMap)
                .send()
                .join();

        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .variables(variableMap)
                .tenantId(tenantId)
                .send()
                .join();

        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .variables(variableMap)
                .tenantId(tenantId)
                .send()
                .join();

        // processInstanceBusinessKey was removed
        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .variables(variableMap)
                .tenantId(tenantId)
                .send()
                .join();

        // processInstanceBusinessKey was removed
        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .variables(variableMap)
                .tenantId(tenantId)
                .send()
                .join();

        // processInstanceBusinessKey was removed
        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .variables(variableMap)
                .tenantId(tenantId)
                .send()
                .join();

        // processInstanceBusinessKey was removed
        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .variables(variableMap)
                .tenantId(tenantId)
                .send()
                .join();

        // processInstanceBusinessKey was removed
        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .variables(variableMap)
                .tenantId(tenantId)
                .send()
                .join();

        // processInstanceBusinessKey was removed
        // Hint: In Camunda 8 messages could also be correlated asynchronously
        camundaClient
                .newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey("add correlationKey here")
                .variables(variableMap)
                .tenantId(tenantId)
                .send()
                .join();
    }
}
                                """));
  }
}
