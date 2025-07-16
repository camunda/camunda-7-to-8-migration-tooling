package org.camunda.migration.rewrite.recipes.external.prepare;

import static org.openrewrite.java.Assertions.java;

import org.camunda.migration.rewrite.recipes.external.PrepareJobWorkerBeneathExternalWorkerRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class InsertJobWorkerTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipes(new PrepareJobWorkerBeneathExternalWorkerRecipe())
        .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
  }

  @Test
  void InjectJobWorkerRecipe() {
    rewriteRun(
        java(
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
public class RetrievePaymentAdapter implements ExternalTaskHandler {

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
                """,
"""
package org.camunda.community.migration.example;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.spring.client.annotation.JobWorker;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ExternalTaskSubscription("retrievePaymentAdapter")
public class RetrievePaymentAdapter implements ExternalTaskHandler {

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
    
    @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
    public Map<String, Object> executeJob(ActivatedJob job) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();
        return resultMap;
    }
}
"""));
  }

}
