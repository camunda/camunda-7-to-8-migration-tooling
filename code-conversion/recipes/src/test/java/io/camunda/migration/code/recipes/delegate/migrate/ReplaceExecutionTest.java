/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.delegate.migrate;

import static org.openrewrite.java.Assertions.java;

import io.camunda.migration.code.recipes.delegate.MigrateExecutionRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

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

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
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

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
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
        int typedAmount = (int) job.getVariablesAsMap().get("amount");
        Integer amount = (Integer) job.getVariablesAsMap().get("AMOUNT");
        // do something...
        String typedTransactionId = "TX12345";
        resultMap.put("transactionId", typedTransactionId);
        return resultMap;
    }
}
"""));
  }

  @Test
  void flagsLocalVariableLookupForManualMigration() {
    rewriteRun(
        spec -> spec.expectedCyclesThatMakeChanges(2),
        java(
    """
            package org.camunda.conversion.java_delegates.handling_process_variables;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.springframework.stereotype.Component;

            import java.util.HashMap;
            import java.util.Map;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                    Object localValue = execution.getVariableLocal("localValue");
                    String helperValue = getVariableLocal("helperValue");
                }

                private String getVariableLocal(String variableName) {
                    return variableName;
                }

                private Object retainedHelper(DelegateExecution execution) {
                    return execution.getVariableLocal("retainedValue");
                }

                private String getVariableLocalRequiresManualMigration(String variableName) {
                    return variableName;
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
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.springframework.stereotype.Component;

            import java.util.HashMap;
            import java.util.Map;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                    Object localValue = execution.getVariableLocal("localValue");
                    String helperValue = getVariableLocal("helperValue");
                }

                private String getVariableLocal(String variableName) {
                    return variableName;
                }

                private Object retainedHelper(DelegateExecution execution) {
                    return execution.getVariableLocal("retainedValue");
                }

                private String getVariableLocalRequiresManualMigration(String variableName) {
                    return variableName;
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public Map<String, Object> executeJobMigrated(ActivatedJob job) throws Exception {
                    Map<String, Object> resultMap = new HashMap<>();
                    // TODO: getVariableLocal requires manual migration because Camunda 8 job workers do not expose the Camunda 7 execution scope.
                    Object localValue = getVariableLocalRequiresManualMigration1("localValue");
                    String helperValue = getVariableLocal("helperValue");
                    return resultMap;
                }

                private static <T> T getVariableLocalRequiresManualMigration1(String variableName) {
                    throw new UnsupportedOperationException(
                            "Manual migration required for getVariableLocal: " + variableName);
                }
            }
            """));
  }

  @Test
  void preservesTypedVariableLookupsInNonDeclarationContexts() {
    rewriteRun(
        spec ->
            spec.expectedCyclesThatMakeChanges(1)
                .typeValidationOptions(TypeValidation.none()),
        java(
            """
            package org.camunda.conversion.java_delegates.handling_process_variables;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.springframework.stereotype.Component;

            import java.util.List;
            import java.util.Map;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    String existing = null;
                    existing = execution.getVariable("assignment");
                    consume(execution.getVariable("argument"));
                    consumeValues(List.of(execution.getVariable("generic")));
                    consumeNested(execution.getVariable("nested"));
                    new Box(execution.getVariable("constructor"));
                    return true ? execution.getVariable("conditional") : "fallback";
                }

                private void consume(String value) {
                }

                private void consumeValues(List<String> values) {
                }

                private void consumeNested(Map<String, List<Integer>> value) {
                }

                public static class Box {
                    public Box(String value) {
                    }
                }

                private String retainedHelper(DelegateExecution execution) {
                    return execution.getVariable("retained");
                }

            }
            """,
            """
            package org.camunda.conversion.java_delegates.handling_process_variables;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.springframework.stereotype.Component;

            import java.util.List;
            import java.util.Map;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    String existing = null;
                    existing = (String) job.getVariablesAsMap().get("assignment");
                    consume((String) job.getVariablesAsMap().get("argument"));
                    consumeValues(List.of((String) job.getVariablesAsMap().get("generic")));
                    consumeNested((Map<String, List<Integer>>) job.getVariablesAsMap().get("nested"));
                    new Box((String) job.getVariablesAsMap().get("constructor"));
                    return true ? (String) job.getVariablesAsMap().get("conditional") : "fallback";
                }

                private void consume(String value) {
                }

                private void consumeValues(List<String> values) {
                }

                private void consumeNested(Map<String, List<Integer>> value) {
                }

                public static class Box {
                    public Box(String value) {
                    }
                }

                private String retainedHelper(DelegateExecution execution) {
                    return execution.getVariable("retained");
                }

            }
            """));
  }

  @Test
  void migratesLookupsInNestedWorkerMethods() {
    rewriteRun(
        spec ->
            spec.expectedCyclesThatMakeChanges(1)
                .typeValidationOptions(TypeValidation.none()),
        java(
            """
            package org.camunda.conversion.java_delegates.handling_process_variables;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.springframework.stereotype.Component;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    Runnable nested = new Runnable() {
                        @Override
                        public void run() {
                            String nestedValue = execution.getVariable("nested");
                        }
                    };
                    nested.run();
                    return "done";
                }
            }
            """,
            """
            package org.camunda.conversion.java_delegates.handling_process_variables;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.springframework.stereotype.Component;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    Runnable nested = new Runnable() {
                        @Override
                        public void run() {
                            String nestedValue = (String) job.getVariablesAsMap().get("nested");
                        }
                    };
                    nested.run();
                    return "done";
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
                    import io.camunda.client.annotation.JobWorker;
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
                    import io.camunda.client.exception.CamundaError;
                    import io.camunda.client.annotation.JobWorker;
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
