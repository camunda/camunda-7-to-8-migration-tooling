package org.camunda.migration.rewrite.recipes;

import static org.openrewrite.java.Assertions.java;

import org.camunda.migration.rewrite.recipes.client.AllClientCleanupRecipes;
import org.camunda.migration.rewrite.recipes.client.AllClientMigrateRecipes;
import org.camunda.migration.rewrite.recipes.client.AllClientPrepareRecipes;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class ProcessEngineToZeebeClientTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipes(
            new AllClientPrepareRecipes(
                "org.camunda.migration.rewrite.recipes.client.CamundaClientWrapper"),
            new AllClientMigrateRecipes(
                "org.camunda.migration.rewrite.recipes.client.CamundaClientWrapper"),
            new AllClientCleanupRecipes())
        .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
  }

  @Test
  void rewriteRuntimeServiceStartProcessInstanceByKey() {
    rewriteRun(
        java(
"""
package org.camunda.community.migration.example;
import java.util.Map;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SampleProcessStarter {

    @Autowired
	private ProcessEngine processEngine;

	public void startOneProcess() {
		Map<String, Object> variables = Map.of("x", 7);
		ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceByKey("sample-process-solution-process", variables);
		System.out.println("Started " + processInstance.getId() );
	}
}
                """,
"""
package org.camunda.community.migration.example;
import java.util.Map;

import io.camunda.client.api.response.ProcessInstanceEvent;
import org.camunda.migration.rewrite.recipes.client.CamundaClientWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SampleProcessStarter {

    @Autowired
    private CamundaClientWrapper camundaClientWrapper;

	public void startOneProcess() {
		Map<String, Object> variables = Map.of("x", 7);
		ProcessInstanceEvent processInstance = camundaClientWrapper.createProcessByBPMNModelIdentifierWithVariables("sample-process-solution-process", variables);
		System.out.println("Started " + processInstance.getProcessInstanceKey().toString() );
	}
}"""));
  }
}
