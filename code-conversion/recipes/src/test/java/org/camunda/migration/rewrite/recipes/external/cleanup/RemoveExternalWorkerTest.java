package org.camunda.migration.rewrite.recipes.external.cleanup;

import static org.openrewrite.java.Assertions.java;

import org.camunda.migration.rewrite.recipes.external.CleanupExternalWorkerRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

class RemoveExternalWorkerTest implements RewriteTest {

  @Test
  void RemoveExternalWorkerTest() {
    rewriteRun(
        spec -> spec.recipe(new CleanupExternalWorkerRecipe()),
        java(
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
        int amount = (int) externalTask.getVariable("amount");
        // do something
        Map<String, Object> variableMap = Map.ofEntries(
                Map.entry("transactionId", "TX12345")
        );
        externalTaskService.complete(externalTask.getId(), variableMap, null);
    }

    @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
    public Map<String, Object> executeJob(ActivatedJob job) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();
        return resultMap;
    }
}
""",
"""
package org.camunda.community.migration.example;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.spring.client.annotation.JobWorker;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RetrievePaymentAdapter {

    @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
    public Map<String, Object> executeJob(ActivatedJob job) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();
        return resultMap;
    }
}
"""));
  }
}
