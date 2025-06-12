package org.camunda.migration.rewrite.recipes.client.migrate;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

class ReplaceStartProcessInstanceMethodsTest implements RewriteTest {

  @Test
  void replaceStartProcessInstanceMethodsTest() {
    rewriteRun(
        spec ->
            spec.recipe(
                new ReplaceStartProcessInstanceMethodsRecipe(
                    "org.camunda.migration.rewrite.recipes.client.CamundaClientWrapper")),
        // language=java
        java(
            """
                                package org.camunda.community.migration.example;

                                import org.camunda.bpm.engine.ProcessEngine;
                                import org.camunda.bpm.engine.runtime.ProcessInstance;
                                import org.camunda.migration.rewrite.recipes.client.CamundaClientWrapper;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.stereotype.Component;
                                
                                import java.util.Map;

                                @Component
                                public class CancelProcessInstanceTestClass {

                                    @Autowired
                                    private ProcessEngine engine;

                                    @Autowired
                                    private CamundaClientWrapper camundaClientWrapper;

                                    public void startProcessInstance(String processDefinitionKey, String processDefinitionId, String businessKey, String tenantId, Map<String, Object> variableMap, String messageName) {
                                        // by BPMNModelIdentifier
                                        ProcessInstance instance1 = engine.getRuntimeService().startProcessInstanceByKey(processDefinitionKey);
                                        String id = instance1.getProcessInstanceId();
                                        System.out.println(instance1.getProcessInstanceId());

                                        engine.getRuntimeService().startProcessInstanceByKey(processDefinitionKey, businessKey);

                                        engine.getRuntimeService().startProcessInstanceByKey(processDefinitionKey, variableMap);

                                        engine.getRuntimeService().startProcessInstanceByKey(processDefinitionKey, businessKey, variableMap);

                                        engine.getRuntimeService().createProcessInstanceByKey(processDefinitionKey)
                                                        .execute();

                                        engine.getRuntimeService().createProcessInstanceByKey(processDefinitionKey)
                                                        .businessKey(businessKey)
                                                        .execute();

                                        engine.getRuntimeService().createProcessInstanceByKey(processDefinitionKey)
                                                        .processDefinitionTenantId(tenantId)
                                                        .execute();

                                        engine.getRuntimeService().createProcessInstanceByKey(processDefinitionKey)
                                                        .setVariables(variableMap)
                                                        .execute();

                                        engine.getRuntimeService().createProcessInstanceByKey(processDefinitionKey)
                                                        .businessKey(businessKey)
                                                        .processDefinitionTenantId(tenantId)
                                                        .execute();

                                        engine.getRuntimeService().createProcessInstanceByKey(processDefinitionKey)
                                                        .businessKey(businessKey)
                                                        .setVariables(variableMap)
                                                        .execute();

                                        engine.getRuntimeService().createProcessInstanceByKey(processDefinitionKey)
                                                        .processDefinitionTenantId(tenantId)
                                                        .setVariables(variableMap)
                                                        .execute();

                                        engine.getRuntimeService().createProcessInstanceByKey(processDefinitionKey)
                                                        .businessKey(businessKey)
                                                        .processDefinitionTenantId(tenantId)
                                                        .setVariables(variableMap)
                                                        .execute();

                                        // by key assigned on deployment
                                        engine.getRuntimeService().startProcessInstanceById(processDefinitionId);

                                        engine.getRuntimeService().startProcessInstanceById(processDefinitionId, businessKey);

                                        engine.getRuntimeService().startProcessInstanceById(processDefinitionId, variableMap);

                                        engine.getRuntimeService().startProcessInstanceById(processDefinitionId, businessKey, variableMap);

                                        engine.getRuntimeService().createProcessInstanceById(processDefinitionId)
                                                        .businessKey(businessKey)
                                                        .execute();

                                        engine.getRuntimeService().createProcessInstanceById(processDefinitionId)
                                                        .processDefinitionTenantId(tenantId)
                                                        .execute();

                                        engine.getRuntimeService().createProcessInstanceById(processDefinitionId)
                                                        .setVariables(variableMap)
                                                        .execute();

                                        engine.getRuntimeService().createProcessInstanceById(processDefinitionId)
                                                        .processDefinitionTenantId(tenantId)
                                                        .setVariables(variableMap)
                                                        .execute();

                                        engine.getRuntimeService().createProcessInstanceById(processDefinitionId)
                                                        .businessKey(businessKey)
                                                        .setVariables(variableMap)
                                                        .execute();

                                        engine.getRuntimeService().createProcessInstanceById(processDefinitionId)
                                                        .businessKey(businessKey)
                                                        .processDefinitionTenantId(tenantId)
                                                        .execute();

                                        engine.getRuntimeService().createProcessInstanceById(processDefinitionId)
                                                        .businessKey(businessKey)
                                                        .processDefinitionTenantId(tenantId)
                                                        .setVariables(variableMap)
                                                        .execute();

                                        // by message
                                        ProcessInstance instance2 = engine.getRuntimeService().startProcessInstanceByMessage(messageName);
                                        String id2 = instance2.getProcessInstanceId();
                                        System.out.println(instance2.getProcessInstanceId());

                                        engine.getRuntimeService().startProcessInstanceByMessage(messageName, businessKey);

                                        engine.getRuntimeService().startProcessInstanceByMessage(messageName, variableMap);

                                        engine.getRuntimeService().startProcessInstanceByMessage(messageName, businessKey, variableMap);

                                        engine.getRuntimeService().startProcessInstanceByMessageAndProcessDefinitionId(messageName, processDefinitionId);

                                        engine.getRuntimeService().startProcessInstanceByMessageAndProcessDefinitionId(messageName, processDefinitionId, businessKey);

                                        engine.getRuntimeService().startProcessInstanceByMessageAndProcessDefinitionId(messageName, processDefinitionId, variableMap);

                                        engine.getRuntimeService().startProcessInstanceByMessageAndProcessDefinitionId(messageName, processDefinitionId, businessKey, variableMap);
                                    }
                                }
                                """,
            """
                                package org.camunda.community.migration.example;

                                import io.camunda.client.api.response.CorrelateMessageResponse;
                                import io.camunda.client.api.response.ProcessInstanceEvent;
                                import org.camunda.bpm.engine.ProcessEngine;
                                import org.camunda.bpm.engine.runtime.ProcessInstance;
                                import org.camunda.migration.rewrite.recipes.client.CamundaClientWrapper;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.stereotype.Component;

                                import java.util.Map;

                                @Component
                                public class CancelProcessInstanceTestClass {

                                    @Autowired
                                    private ProcessEngine engine;

                                    @Autowired
                                    private CamundaClientWrapper camundaClientWrapper;

                                    public void startProcessInstance(String processDefinitionKey, String processDefinitionId, String businessKey, String tenantId, Map<String, Object> variableMap, String messageName) {
                                        // by BPMNModelIdentifier
                                        ProcessInstanceEvent instance1 = camundaClientWrapper.createProcessByBPMNModelIdentifier(processDefinitionKey);
                                        String id = instance1.getProcessInstanceKey().toString();
                                        System.out.println(instance1.getProcessInstanceKey().toString());

                                        // businessKey was removed
                                        camundaClientWrapper.createProcessByBPMNModelIdentifier(processDefinitionKey);

                                        camundaClientWrapper.createProcessByBPMNModelIdentifierWithVariables(processDefinitionKey, variableMap);

                                        // businessKey was removed
                                        camundaClientWrapper.createProcessByBPMNModelIdentifierWithVariables(processDefinitionKey, variableMap);

                                        camundaClientWrapper.createProcessByBPMNModelIdentifier(processDefinitionKey);

                                        // businessKey was removed
                                        camundaClientWrapper.createProcessByBPMNModelIdentifier(processDefinitionKey);

                                        camundaClientWrapper.createProcessByBPMNModelIdentifierWithTenantId(processDefinitionKey, tenantId);

                                        camundaClientWrapper.createProcessByBPMNModelIdentifierWithVariables(processDefinitionKey, variableMap);

                                        // businessKey was removed
                                        camundaClientWrapper.createProcessByBPMNModelIdentifierWithTenantId(processDefinitionKey, tenantId);

                                        // businessKey was removed
                                        camundaClientWrapper.createProcessByBPMNModelIdentifierWithVariables(processDefinitionKey, variableMap);

                                        camundaClientWrapper.createProcessByBPMNModelIdentifierWithTenantIdWithVariables(processDefinitionKey, tenantId, variableMap);

                                        // businessKey was removed
                                        camundaClientWrapper.createProcessByBPMNModelIdentifierWithTenantIdWithVariables(processDefinitionKey, tenantId, variableMap);

                                        // by key assigned on deployment
                                        camundaClientWrapper.createProcessByKeyAssignedOnDeployment(processDefinitionId);

                                        // businessKey was removed
                                        camundaClientWrapper.createProcessByKeyAssignedOnDeployment(processDefinitionId);

                                        camundaClientWrapper.createProcessByKeyAssignedOnDeploymentWithVariables(processDefinitionId, variableMap);

                                        // businessKey was removed
                                        camundaClientWrapper.createProcessByKeyAssignedOnDeploymentWithVariables(processDefinitionId, variableMap);

                                        // businessKey was removed
                                        camundaClientWrapper.createProcessByKeyAssignedOnDeployment(processDefinitionId);

                                        camundaClientWrapper.createProcessByKeyAssignedOnDeploymentWithTenantId(processDefinitionId, tenantId);

                                        camundaClientWrapper.createProcessByKeyAssignedOnDeploymentWithVariables(processDefinitionId, variableMap);

                                        camundaClientWrapper.createProcessByKeyAssignedOnDeploymentWithTenantIdWithVariables(processDefinitionId, tenantId, variableMap);

                                        // businessKey was removed
                                        camundaClientWrapper.createProcessByKeyAssignedOnDeploymentWithVariables(processDefinitionId, variableMap);

                                        // businessKey was removed
                                        camundaClientWrapper.createProcessByKeyAssignedOnDeploymentWithTenantId(processDefinitionId, tenantId);

                                        // businessKey was removed
                                        camundaClientWrapper.createProcessByKeyAssignedOnDeploymentWithTenantIdWithVariables(processDefinitionId, tenantId, variableMap);

                                        // by message
                                        CorrelateMessageResponse instance2 = camundaClientWrapper.correlateMessage(messageName, "someCorrelationKey");
                                        String id2 = instance2.getProcessInstanceKey().toString();
                                        System.out.println(instance2.getProcessInstanceKey().toString());

                                        // businessKey was removed
                                        camundaClientWrapper.correlateMessage(messageName, "someCorrelationKey");

                                        camundaClientWrapper.correlateMessageWithVariables(messageName, "someCorrelationKey", variableMap);

                                        // businessKey was removed
                                        camundaClientWrapper.correlateMessageWithVariables(messageName, "someCorrelationKey", variableMap);

                                        // processDefinitionId was removed
                                        camundaClientWrapper.correlateMessage(messageName, "someCorrelationKey");

                                        // businessKey was removed
                                        // processDefinitionId was removed
                                        camundaClientWrapper.correlateMessage(messageName, "someCorrelationKey");

                                        // processDefinitionId was removed
                                        camundaClientWrapper.correlateMessageWithVariables(messageName, "someCorrelationKey", variableMap);

                                        // businessKey was removed
                                        // processDefinitionId was removed
                                        camundaClientWrapper.correlateMessageWithVariables(messageName, "someCorrelationKey", variableMap);
                                    }
                                }
                                """));
  }
}
