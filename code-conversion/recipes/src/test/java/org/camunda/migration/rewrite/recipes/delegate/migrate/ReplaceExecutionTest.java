package org.camunda.migration.rewrite.recipes.delegate.migrate;

import static org.openrewrite.java.Assertions.java;

import org.camunda.migration.rewrite.recipes.delegate.MigrateExecutionRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class ReplaceExecutionTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipes(new MigrateExecutionRecipe())
        .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
  }

  @Test
  void ReplaceExecutionTest() {
    rewriteRun(
        java(
"""
package org.camunda.conversion.java_delegates.handling_process_variables;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.spring.client.annotation.JobWorker;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RetrievePaymentAdapter implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        int typedAmount = (int) execution.getVariable("amount");
        Integer amount = (Integer) execution.getVariable("AMOUNT");
        // do something...
        String typedTransactionId = "TX12345";
        execution.setVariable("transactionId", typedTransactionId);
    }

    @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
    public Map<String, Object> executeJob(ActivatedJob job) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();
        return resultMap;
    }
}
""",
"""
package org.camunda.conversion.java_delegates.handling_process_variables;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.spring.client.annotation.JobWorker;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RetrievePaymentAdapter implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        int typedAmount = (int) execution.getVariable("amount");
        Integer amount = (Integer) execution.getVariable("AMOUNT");
        // do something...
        String typedTransactionId = "TX12345";
        execution.setVariable("transactionId", typedTransactionId);
    }

    @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
    public Map<String, Object> executeJobMigrated(ActivatedJob job) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();
        int typedAmount = (int) job.getVariable("amount");
        Integer amount = (Integer) job.getVariable("AMOUNT");
        // do something...
        String typedTransactionId = "TX12345";
        resultMap.put("transactionId", typedTransactionId);
        return resultMap;
    }
}
"""));
  }

  @Test
  void ThrowBPMNAndExceptionTest() {
    rewriteRun(
        java(
            """
                    package org.camunda.conversion.java_delegates.handling_process_variables;

                    import io.camunda.client.api.response.ActivatedJob;
                    import io.camunda.spring.client.annotation.JobWorker;
                    import org.camunda.bpm.engine.delegate.DelegateExecution;
                    import org.camunda.bpm.engine.delegate.JavaDelegate;
                    import org.camunda.bpm.engine.delegate.BpmnError;
                    import org.camunda.bpm.engine.ProcessEngineException;
                    import org.camunda.bpm.engine.runtime.Incident;
                    import org.springframework.stereotype.Component;

                    import java.util.HashMap;
                    import java.util.Map;
                    import java.lang.RuntimeException;

                    @Component
                    public class RetrievePaymentAdapter implements JavaDelegate {

                        @Override
                        public void execute(DelegateExecution execution) {
                            throw new BpmnError("someErrorCode");

                            throw new BpmnError("someErrorCode", "someErrorMessage");

                            throw new BpmnError("someErrorCode", "someErrorMessage", new RuntimeException());

                            throw new BpmnError("someErrorCode", new RuntimeException());

                            throw new ProcessEngineException();

                            throw new ProcessEngineException("my error message");

                            throw new ProcessEngineException("my error message", new RuntimeException());

                            throw new ProcessEngineException("my error message", 400);

                            throw new ProcessEngineException("my error message", new RuntimeException());

                            throw new ProcessEngineException(new RuntimeException());

                            Incident incident1 = execution.createIncident("someType", "someConfiguration");

                            Incident incident2 = execution.createIncident("someType", "someConfiguration", "someMessage");
                        }

                        @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                        public Map<String, Object> executeJob(ActivatedJob job) throws Exception {
                            Map<String, Object> resultMap = new HashMap<>();
                            return resultMap;
                        }
                    }
                    """,
            """
                    package org.camunda.conversion.java_delegates.handling_process_variables;

                    import io.camunda.client.api.response.ActivatedJob;
                    import io.camunda.spring.client.annotation.JobWorker;
                    import io.camunda.spring.client.exception.CamundaError;
                    import org.camunda.bpm.engine.delegate.DelegateExecution;
                    import org.camunda.bpm.engine.delegate.JavaDelegate;
                    import org.camunda.bpm.engine.delegate.BpmnError;
                    import org.camunda.bpm.engine.ProcessEngineException;
                    import org.camunda.bpm.engine.runtime.Incident;
                    import org.springframework.stereotype.Component;

                    import java.util.HashMap;
                    import java.util.Map;
                    import java.lang.RuntimeException;

                    @Component
                    public class RetrievePaymentAdapter implements JavaDelegate {

                        @Override
                        public void execute(DelegateExecution execution) {
                            throw new BpmnError("someErrorCode");

                            throw new BpmnError("someErrorCode", "someErrorMessage");

                            throw new BpmnError("someErrorCode", "someErrorMessage", new RuntimeException());

                            throw new BpmnError("someErrorCode", new RuntimeException());

                            throw new ProcessEngineException();

                            throw new ProcessEngineException("my error message");

                            throw new ProcessEngineException("my error message", new RuntimeException());

                            throw new ProcessEngineException("my error message", 400);

                            throw new ProcessEngineException("my error message", new RuntimeException());

                            throw new ProcessEngineException(new RuntimeException());

                            Incident incident1 = execution.createIncident("someType", "someConfiguration");

                            Incident incident2 = execution.createIncident("someType", "someConfiguration", "someMessage");
                        }

                        @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                        public Map<String, Object> executeJobMigrated(ActivatedJob job) throws Exception {
                            Map<String, Object> resultMap = new HashMap<>();
                            throw CamundaError.bpmnError("someErrorCode", "Add an error message here");
                            
                            throw CamundaError.bpmnError("someErrorCode", "someErrorMessage");
                            
                            throw CamundaError.bpmnError("someErrorCode", "someErrorMessage", Collections.emptyMap(), new RuntimeException());
                            
                            throw CamundaError.bpmnError("someErrorCode", "Add an error message here", Collections.emptyMap(), new RuntimeException());
                            
                            throw CamundaError.jobError("Add an error message here");
                            
                            throw CamundaError.jobError("my error message");
                            
                            throw CamundaError.jobError("my error message", Collections.emptyMap(), 3, Duration.ofSeconds(30), new RuntimeException());
                            
                            throw CamundaError.jobError("my error message");
                            
                            throw CamundaError.jobError("my error message", Collections.emptyMap(), 3, Duration.ofSeconds(30), new RuntimeException());
                            
                            throw CamundaError.jobError("Add an error message here", Collections.emptyMap(), 3, Duration.ofSeconds(30), new RuntimeException());
                            
                            // incidentType was removed
                            // configuration was removed
                            // incident created by retries being 0
                            throw CamundaError.jobError("Add an error message here", Collections.emptyMap(), 0);
                            
                            // incidentType was removed
                            // configuration was removed
                            // incident created by retries being 0
                            throw CamundaError.jobError("someType", Collections.emptyMap(), 0);
                            return resultMap;
                        }
                    }
                     """));
  }
}
