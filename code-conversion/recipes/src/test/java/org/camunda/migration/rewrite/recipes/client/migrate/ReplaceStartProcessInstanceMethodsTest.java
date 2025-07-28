package org.camunda.migration.rewrite.recipes.client.migrate;

import static org.openrewrite.java.Assertions.java;

import org.camunda.migration.rewrite.recipes.client.MigrateStartProcessInstanceMethodsRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

class ReplaceStartProcessInstanceMethodsTest implements RewriteTest {

  @Test
  void replaceStartProcessInstanceMethodsTest() {  // new line after packages vanishes...
    rewriteRun(
        spec -> spec.recipe(new MigrateStartProcessInstanceMethodsRecipe()),
        // language=java
        java(
            """
                                package org.camunda.community.migration.example;

                                import org.camunda.bpm.engine.ProcessEngine;
                                import org.camunda.bpm.engine.runtime.ProcessInstance;
                                import io.camunda.client.CamundaClient;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.stereotype.Component;

                                import java.util.Map;

                                @Component
                                public class CancelProcessInstanceTestClass {

                                    @Autowired
                                    private ProcessEngine engine;

                                    @Autowired
                                    private CamundaClient camundaClient;

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
                                 import io.camunda.client.CamundaClient;
                                 import org.springframework.beans.factory.annotation.Autowired;
                                 import org.springframework.stereotype.Component;

                                 import java.util.Map;

                                 @Component
                                 public class CancelProcessInstanceTestClass {

                                     @Autowired
                                     private ProcessEngine engine;

                                     @Autowired
                                     private CamundaClient camundaClient;

                                     public void startProcessInstance(String processDefinitionKey, String processDefinitionId, String businessKey, String tenantId, Map<String, Object> variableMap, String messageName) {
                                         // by BPMNModelIdentifier
                                         ProcessInstanceEvent instance1 = camundaClient
                                                 .newCreateInstanceCommand()
                                                 .bpmnProcessId(processDefinitionKey)
                                                 .latestVersion()
                                                 .send()
                                                 .join();
                                         String id = String.valueOf(instance1.getProcessInstanceKey());
                                         System.out.println(String.valueOf(instance1.getProcessInstanceKey()));

                                         // businessKey was removed
                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .bpmnProcessId(processDefinitionKey)
                                                 .latestVersion()
                                                 .send()
                                                 .join();

                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .bpmnProcessId(processDefinitionKey)
                                                 .latestVersion()
                                                 .variables(variableMap)
                                                 .send()
                                                 .join();

                                         // businessKey was removed
                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .bpmnProcessId(processDefinitionKey)
                                                 .latestVersion()
                                                 .variables(variableMap)
                                                 .send()
                                                 .join();

                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .bpmnProcessId(processDefinitionKey)
                                                 .latestVersion()
                                                 .send()
                                                 .join();

                                         // businessKey was removed
                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .bpmnProcessId(processDefinitionKey)
                                                 .latestVersion()
                                                 .send()
                                                 .join();

                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .bpmnProcessId(processDefinitionKey)
                                                 .latestVersion()
                                                 .tenantId(tenantId)
                                                 .send()
                                                 .join();

                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .bpmnProcessId(processDefinitionKey)
                                                 .latestVersion()
                                                 .variables(variableMap)
                                                 .send()
                                                 .join();

                                         // businessKey was removed
                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .bpmnProcessId(processDefinitionKey)
                                                 .latestVersion()
                                                 .tenantId(tenantId)
                                                 .send()
                                                 .join();

                                         // businessKey was removed
                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .bpmnProcessId(processDefinitionKey)
                                                 .latestVersion()
                                                 .variables(variableMap)
                                                 .send()
                                                 .join();

                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .bpmnProcessId(processDefinitionKey)
                                                 .latestVersion()
                                                 .variables(variableMap)
                                                 .tenantId(tenantId)
                                                 .send()
                                                 .join();

                                         // businessKey was removed
                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .bpmnProcessId(processDefinitionKey)
                                                 .latestVersion()
                                                 .variables(variableMap)
                                                 .tenantId(tenantId)
                                                 .send()
                                                 .join();

                                         // by key assigned on deployment
                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .processDefinitionKey(Long.valueOf(processDefinitionId))
                                                 .send()
                                                 .join();

                                         // businessKey was removed
                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .processDefinitionKey(Long.valueOf(processDefinitionId))
                                                 .send()
                                                 .join();

                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .processDefinitionKey(Long.valueOf(processDefinitionId))
                                                 .variables(variableMap)
                                                 .send()
                                                 .join();

                                         // businessKey was removed
                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .processDefinitionKey(Long.valueOf(processDefinitionId))
                                                 .variables(variableMap)
                                                 .send()
                                                 .join();

                                         // businessKey was removed
                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .processDefinitionKey(Long.valueOf(processDefinitionId))
                                                 .send()
                                                 .join();

                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .processDefinitionKey(Long.valueOf(processDefinitionId))
                                                 .tenantId(tenantId)
                                                 .send()
                                                 .join();

                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .processDefinitionKey(Long.valueOf(processDefinitionId))
                                                 .variables(variableMap)
                                                 .send()
                                                 .join();

                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .processDefinitionKey(Long.valueOf(processDefinitionId))
                                                 .variables(variableMap)
                                                 .tenantId(tenantId)
                                                 .send()
                                                 .join();

                                         // businessKey was removed
                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .processDefinitionKey(Long.valueOf(processDefinitionId))
                                                 .variables(variableMap)
                                                 .send()
                                                 .join();

                                         // businessKey was removed
                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .processDefinitionKey(Long.valueOf(processDefinitionId))
                                                 .tenantId(tenantId)
                                                 .send()
                                                 .join();

                                         // businessKey was removed
                                         camundaClient
                                                 .newCreateInstanceCommand()
                                                 .processDefinitionKey(Long.valueOf(processDefinitionId))
                                                 .variables(variableMap)
                                                 .tenantId(tenantId)
                                                 .send()
                                                 .join();

                                         // by message
                                         // please configure you correlationKey
                                         CorrelateMessageResponse instance2 = camundaClient
                                                 .newCorrelateMessageCommand()
                                                 .messageName(messageName)
                                                 .correlationKey("add correlationKey here")
                                                 .send()
                                                 .join();
                                         String id2 = String.valueOf(instance2.getProcessInstanceKey());
                                         System.out.println(String.valueOf(instance2.getProcessInstanceKey()));

                                         // please configure you correlationKey
                                         // businessKey was removed
                                         camundaClient
                                                 .newCorrelateMessageCommand()
                                                 .messageName(messageName)
                                                 .correlationKey("add correlationKey here")
                                                 .send()
                                                 .join();

                                         // please configure you correlationKey
                                         camundaClient
                                                 .newCorrelateMessageCommand()
                                                 .messageName(messageName)
                                                 .correlationKey("add correlationKey here")
                                                 .variables(variableMap)
                                                 .send()
                                                 .join();

                                         // please configure you correlationKey
                                         // businessKey was removed
                                         camundaClient
                                                 .newCorrelateMessageCommand()
                                                 .messageName(messageName)
                                                 .correlationKey("add correlationKey here")
                                                 .variables(variableMap)
                                                 .send()
                                                 .join();

                                         // please configure you correlationKey
                                         // processDefinitionId was removed
                                         camundaClient
                                                 .newCorrelateMessageCommand()
                                                 .messageName(messageName)
                                                 .correlationKey("add correlationKey here")
                                                 .send()
                                                 .join();

                                         // please configure you correlationKey
                                         // businessKey was removed
                                         // processDefinitionId was removed
                                         camundaClient
                                                 .newCorrelateMessageCommand()
                                                 .messageName(messageName)
                                                 .correlationKey("add correlationKey here")
                                                 .send()
                                                 .join();

                                         // please configure you correlationKey
                                         // processDefinitionId was removed
                                         camundaClient
                                                 .newCorrelateMessageCommand()
                                                 .messageName(messageName)
                                                 .correlationKey("add correlationKey here")
                                                 .variables(variableMap)
                                                 .send()
                                                 .join();

                                         // please configure you correlationKey
                                         // businessKey was removed
                                         // processDefinitionId was removed
                                         camundaClient
                                                 .newCorrelateMessageCommand()
                                                 .messageName(messageName)
                                                 .correlationKey("add correlationKey here")
                                                 .variables(variableMap)
                                                 .send()
                                                 .join();
                                     }
                                 }
                                 """));
  }

  @Test
  void variousProcessEngineFunctionsTest() {
    rewriteRun(
        spec -> spec.recipes(new MigrateStartProcessInstanceMethodsRecipe()),
        // language=java
        java(
            """
                    package org.camunda.community.migration.example;

                    import io.camunda.client.CamundaClient;
                    import org.camunda.bpm.engine.RuntimeService;
                    import org.camunda.bpm.engine.runtime.ProcessInstance;
                    import org.springframework.beans.factory.annotation.Autowired;
                    import org.springframework.stereotype.Component;

                    @Component
                    public class VariousProcessEngineFunctionsTestClass {

                        @Autowired
                        private CamundaClient camundaClient;

                        @Autowired
                        private RuntimeService runtimeService;

                        public void variousProcessEngineFunctions(String processDefinitionKey, String signalName, String deleteReason) {

                            ProcessInstance instance1 = runtimeService.startProcessInstanceByKey(processDefinitionKey);
                        }
                    }
                    """,
            """
                    package org.camunda.community.migration.example;

                    import io.camunda.client.CamundaClient;
                    import io.camunda.client.api.response.ProcessInstanceEvent;
                    import org.camunda.bpm.engine.RuntimeService;
                    import org.springframework.beans.factory.annotation.Autowired;
                    import org.springframework.stereotype.Component;

                    @Component
                    public class VariousProcessEngineFunctionsTestClass {

                        @Autowired
                        private CamundaClient camundaClient;

                        @Autowired
                        private RuntimeService runtimeService;

                        public void variousProcessEngineFunctions(String processDefinitionKey, String signalName, String deleteReason) {

                            ProcessInstanceEvent instance1 = camundaClient
                                    .newCreateInstanceCommand()
                                    .bpmnProcessId(processDefinitionKey)
                                    .latestVersion()
                                    .send()
                                    .join();
                        }
                    }
                    """));
  }
}
