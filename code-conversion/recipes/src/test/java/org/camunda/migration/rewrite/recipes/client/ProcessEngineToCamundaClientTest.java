package org.camunda.migration.rewrite.recipes.client;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class ProcessEngineToCamundaClientTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipeFromResources("org.camunda.migration.rewrite.recipes.AllClientRecipes")
            .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
  }
  @Test
  void variousProcessEngineFunctionsTest() {
    rewriteRun(
        // language=java
        java(
"""
package org.camunda.community.migration.example;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CancelProcessInstanceTestClass {

    @Autowired
    private ProcessEngine engine;

    public void variousProcessEngineFunctions(String processDefinitionKey, String signalName, String deleteReason) {

        ProcessInstance instance1 = engine.getRuntimeService().startProcessInstanceByKey(processDefinitionKey);
        String processInstanceId = instance1.getProcessInstanceId();
        System.out.println(instance1.getProcessInstanceId());

        engine.getRuntimeService().createSignalEvent(signalName).send();

        engine.getRuntimeService().deleteProcessInstance(processInstanceId, deleteReason);
    }
}
""",
"""
package org.camunda.community.migration.example;

import io.camunda.client.api.response.ProcessInstanceEvent;
import org.camunda.migration.rewrite.recipes.client.CamundaClientWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CancelProcessInstanceTestClass {

    @Autowired
    private CamundaClientWrapper camundaClientWrapper;

    public void variousProcessEngineFunctions(String processDefinitionKey, String signalName, String deleteReason) {

        ProcessInstanceEvent instance1 = camundaClientWrapper.createProcessByBPMNModelIdentifier(processDefinitionKey);
        String processInstanceId = instance1.getProcessInstanceKey().toString();
        System.out.println(instance1.getProcessInstanceKey().toString());

        camundaClientWrapper.broadcastSignal(signalName);

        camundaClientWrapper.cancelProcessInstance(Long.valueOf(processInstanceId));
    }
}
"""));
  }
}
