package org.camunda.migration.rewrite.recipes.external.prepare;

import static org.openrewrite.java.Assertions.java;

import org.camunda.migration.rewrite.recipes.sharedRecipes.ReplaceTypedValueAPIRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class ExternalWorkerTypedValueTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipes(new ReplaceTypedValueAPIRecipe())
        .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
  }

  @Test
  void ReplaceTypedValueTest() {
    rewriteRun(
        java(
"""
package org.camunda.community.migration.example;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.IntegerValue;
import org.camunda.bpm.engine.variable.value.StringValue;
import org.springframework.context.annotation.Configuration;

@Configuration
@ExternalTaskSubscription("retrievePaymentAdapter")
public class RetrievePaymentWorkerProcessVariablesTypedValueAPI implements ExternalTaskHandler {

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        IntegerValue typedAmount = externalTask.getVariableTyped("amount");
        int amount = typedAmount.getValue();
        // do something
        StringValue typedTransactionId = Variables.stringValue("TX12345");
        VariableMap variableMap = Variables.createVariables().putValueTyped("transactionId", typedTransactionId);
        externalTaskService.complete(externalTask.getId(), variableMap, null);
    }
}
                """,
"""
package org.camunda.community.migration.example;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ExternalTaskSubscription("retrievePaymentAdapter")
public class RetrievePaymentWorkerProcessVariablesTypedValueAPI implements ExternalTaskHandler {

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        // please check type
        Integer typedAmount = externalTask.getVariable("amount");
        int amount = typedAmount;
        // do something
        String typedTransactionId = "TX12345";
        Map<String, Object> variableMap = new HashMap<>();
        variableMap.put("transactionId", typedTransactionId);
        externalTaskService.complete(externalTask.getId(), variableMap, null);
    }
}
"""));
  }
}
