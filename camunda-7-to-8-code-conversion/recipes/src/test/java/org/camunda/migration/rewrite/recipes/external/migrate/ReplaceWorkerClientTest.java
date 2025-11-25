package org.camunda.migration.rewrite.recipes.external.migrate;

import static org.openrewrite.java.Assertions.java;

import org.camunda.migration.rewrite.recipes.external.MigrateExternalWorkerRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class ReplaceWorkerClientTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipes(new MigrateExternalWorkerRecipe())
        .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
  }

  @Test
  void replaceWorkerClientTest() {
    rewriteRun(
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
    public Map<String, Object> executeJobMigrated(ActivatedJob job) throws Exception {
        // please check type
        Integer typedAmount = job.getVariable("amount");
        int amount = typedAmount;
        // do something
        String typedTransactionId = "TX12345";
        Map<String, Object> variableMap = new HashMap<>();
        variableMap.put("transactionId", typedTransactionId);
        // local variables were removed
        return variableMap;
    }
}
"""));
  }

  @Test
  void ThrowBPMNAndExceptionTest() {
    rewriteRun(
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
                    import java.util.Collections;

                    @Component
                    public class RetrievePaymentAdapter implements ExternalTaskHandler {

                        @Override
                        public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
                            externalTaskService.handleBpmnError(externalTask, "my error code");

                            externalTaskService.handleBpmnError(externalTask, "my error code", "my error message");

                            externalTaskService.handleBpmnError(externalTask, "my error code", "my error message", Collections.emptyMap());

                            externalTaskService.handleBpmnError(externalTask.getId(), "my error code", "my error message", Collections.emptyMap());

                            externalTaskService.handleFailure(externalTask, "my error message", "my error details", externalTask.getRetries() - 1, 30000L);

                            externalTaskService.handleFailure(externalTask.getId(), "my error message", "my error details", externalTask.getRetries() - 1, 30000L);

                            externalTaskService.handleFailure(externalTask.getId(), "my error message", "my error details", externalTask.getRetries() - 1, 30000L, Collections.emptyMap(), Collections.emptyMap());

                            externalTaskService.handleFailure(externalTask, "my error message", "my error details", 0, 30000L);

                            externalTaskService.handleFailure(externalTask.getId(), "my error message", "my error details", 0, 30000L);

                            externalTaskService.handleFailure(externalTask.getId(), "my error message", "my error details", 0, 30000L, Collections.emptyMap(), Collections.emptyMap());
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
                    import io.camunda.spring.client.exception.CamundaError;
                    import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
                    import org.camunda.bpm.client.task.ExternalTask;
                    import org.camunda.bpm.client.task.ExternalTaskHandler;
                    import org.camunda.bpm.client.task.ExternalTaskService;
                    import org.springframework.stereotype.Component;

                    import java.util.HashMap;
                    import java.util.Map;
                    import java.util.Collections;

                    @Component
                    public class RetrievePaymentAdapter implements ExternalTaskHandler {

                        @Override
                        public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
                            externalTaskService.handleBpmnError(externalTask, "my error code");

                            externalTaskService.handleBpmnError(externalTask, "my error code", "my error message");

                            externalTaskService.handleBpmnError(externalTask, "my error code", "my error message", Collections.emptyMap());

                            externalTaskService.handleBpmnError(externalTask.getId(), "my error code", "my error message", Collections.emptyMap());

                            externalTaskService.handleFailure(externalTask, "my error message", "my error details", externalTask.getRetries() - 1, 30000L);

                            externalTaskService.handleFailure(externalTask.getId(), "my error message", "my error details", externalTask.getRetries() - 1, 30000L);

                            externalTaskService.handleFailure(externalTask.getId(), "my error message", "my error details", externalTask.getRetries() - 1, 30000L, Collections.emptyMap(), Collections.emptyMap());

                            externalTaskService.handleFailure(externalTask, "my error message", "my error details", 0, 30000L);

                            externalTaskService.handleFailure(externalTask.getId(), "my error message", "my error details", 0, 30000L);

                            externalTaskService.handleFailure(externalTask.getId(), "my error message", "my error details", 0, 30000L, Collections.emptyMap(), Collections.emptyMap());
                        }

                        @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                        public Map<String, Object> executeJobMigrated(ActivatedJob job) throws Exception {
                            throw CamundaError.bpmnError("my error code", "Add an error message here");

                            throw CamundaError.bpmnError("my error code", "my error message");

                            throw CamundaError.bpmnError("my error code", "my error message", Collections.emptyMap());

                            throw CamundaError.bpmnError("my error code", "my error message", Collections.emptyMap());

                            // error details were removed
                            throw CamundaError.jobError("my error message", Collections.emptyMap(), Integer.valueOf(job.getRetries() - 1), Duration.ofMillis(30000L));

                            // error details were removed
                            throw CamundaError.jobError("my error message", Collections.emptyMap(), Integer.valueOf(job.getRetries() - 1), Duration.ofMillis(30000L));

                            // error details were removed
                            // local variables were removed
                            throw CamundaError.jobError("my error message", Collections.emptyMap(), Integer.valueOf(job.getRetries() - 1), Duration.ofMillis(30000L));

                            // error details were removed
                            throw CamundaError.jobError("my error message", Collections.emptyMap(), Integer.valueOf(0), Duration.ofMillis(30000L));

                            // error details were removed
                            throw CamundaError.jobError("my error message", Collections.emptyMap(), Integer.valueOf(0), Duration.ofMillis(30000L));

                            // error details were removed
                            // local variables were removed
                            throw CamundaError.jobError("my error message", Collections.emptyMap(), Integer.valueOf(0), Duration.ofMillis(30000L));
                        }
                    }
                     """));
  }
}
