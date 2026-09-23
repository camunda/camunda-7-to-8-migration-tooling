/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.delegate.migrate;

import static org.openrewrite.java.Assertions.java;

import io.camunda.migration.code.recipes.delegate.CleanupDelegateRecipe;
import io.camunda.migration.code.recipes.delegate.MigrateExecutionRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

class ReplaceExecutionTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipes(new MigrateExecutionRecipe())
        .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
  }

  private static Recipe clearMemberReferenceMethodTypes() {
    return new Recipe() {
      @Override
      public String getDisplayName() {
        return "Clear member reference method types";
      }

      @Override
      public String getDescription() {
        return "Removes method attribution to exercise migration fallbacks.";
      }

      @Override
      public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
          @Override
          public J.MemberReference visitMemberReference(
              J.MemberReference memberReference, ExecutionContext ctx) {
            return super.visitMemberReference(memberReference, ctx).withMethodType(null);
          }
        };
      }
    };
  }

  private static Recipe clearMethodInvocationMethodTypes() {
    return new Recipe() {
      @Override
      public String getDisplayName() {
        return "Clear method invocation method types";
      }

      @Override
      public String getDescription() {
        return "Removes method attribution to exercise chained lookup fallbacks.";
      }

      @Override
      public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
          @Override
          public J.MethodInvocation visitMethodInvocation(
              J.MethodInvocation invocation, ExecutionContext ctx) {
            return super.visitMethodInvocation(invocation, ctx).withMethodType(null);
          }
        };
      }
    };
  }

  private static Recipe clearVariableLookupMethodTypes() {
    return new Recipe() {
      @Override
      public String getDisplayName() {
        return "Clear variable lookup method types";
      }

      @Override
      public String getDescription() {
        return "Removes variable lookup method attribution to exercise migration fallbacks.";
      }

      @Override
      public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
          @Override
          public J.MethodInvocation visitMethodInvocation(
              J.MethodInvocation invocation, ExecutionContext ctx) {
            J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
            return "getVariable".equals(visited.getSimpleName())
                ? visited.withMethodType(null)
                : visited;
          }
        };
      }
    };
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
  void flagsNestedCallbackLookupsForManualMigration() {
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
                    // TODO: nested DelegateExecution calls require manual migration because the copied job worker cannot preserve an arbitrary execution scope.
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
            """));
  }

  @Test
  void migratesVariableLookupMethodReferencesInCopiedBodies() {
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

            import java.util.function.Function;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    Function<String, Object> lookup = execution::getVariable;
                    Function<String, String> typedLookup = execution::getVariable;
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

            import java.util.function.Function;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    Function<String, Object> lookup = variableName -> job.getVariablesAsMap().get(variableName);
                    Function<String, String> typedLookup = variableName -> (String) job.getVariablesAsMap().get(variableName);
                    return "done";
                }
            }
            """));
  }

  @Test
  void migratesLocalVariableMethodReferencesWhenMethodAttributionIsMissing() {
    rewriteRun(
        spec ->
            spec.recipes(clearMemberReferenceMethodTypes(), new MigrateExecutionRecipe())
                .expectedCyclesThatMakeChanges(2)
                .typeValidationOptions(TypeValidation.none()),
        java(
            """
            package org.camunda.conversion.java_delegates.handling_process_variables;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.springframework.stereotype.Component;

            import java.util.function.BiFunction;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    BiFunction<String, Boolean, Object> typedLocalLookup = execution::getVariableLocalTyped;
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

            import java.util.function.BiFunction;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    // TODO: getVariableLocal requires manual migration because Camunda 8 job workers do not expose the Camunda 7 execution scope.
                    BiFunction<String, Boolean, Object> typedLocalLookup = (variableName, argument1) -> getVariableLocalRequiresManualMigration(variableName, argument1);
                    return "done";
                }

                private static <T> T getVariableLocalRequiresManualMigration(String variableName) {
                    throw new UnsupportedOperationException(
                            "Manual migration required for getVariableLocal: " + variableName);
                }

                private static <T> T getVariableLocalRequiresManualMigration(String variableName, Object... ignored) {
                    throw new UnsupportedOperationException(
                            "Manual migration required for getVariableLocal: " + variableName);
                }
            }
            """));
  }

  @Test
  void usesManualMigrationFallbackWhenMethodReferenceArityIsUnknown() {
    rewriteRun(
        spec ->
            spec.recipes(clearMemberReferenceMethodTypes(), new MigrateExecutionRecipe())
                .typeValidationOptions(TypeValidation.none()),
        java(
            """
            package org.camunda.conversion.java_delegates.handling_process_variables;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.springframework.stereotype.Component;

            import java.util.function.Consumer;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    Consumer<String> lookup = execution::getVariable;
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

            import java.util.function.Consumer;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    // TODO: typed getVariable overload requires manual migration because job workers do not expose the Camunda 7 typed lookup contract.
                    Consumer<String> lookup = getVariableRequiresManualMigration();
                    return "done";
                }

                private static <T> T getVariableRequiresManualMigration() {
                    throw new UnsupportedOperationException(
                            "Manual migration required for getVariable method reference.");
                }
            }
            """));
  }

  @Test
  void flagsNestedMethodNamedLikeGeneratedWorkerForManualMigration() {
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
                    class NestedDelegate {
                        String executeJobMigrated(DelegateExecution execution) {
                            return (String) execution.getVariable("nested");
                        }
                    }
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
                    // TODO: nested DelegateExecution calls require manual migration because the copied job worker cannot preserve an arbitrary execution scope.
                    class NestedDelegate {
                        String executeJobMigrated(DelegateExecution execution) {
                            return (String) execution.getVariable("nested");
                        }
                    }
                    return "done";
                }
            }
            """));
  }

  @Test
  void keepsNestedExecutionLookupsOnManualPathDuringDelegateCleanup() {
    rewriteRun(
        spec ->
            spec.recipes(new MigrateExecutionRecipe(), new CleanupDelegateRecipe())
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
                    class NestedCallback {
                        String read(DelegateExecution execution, String job) {
                            execution.setVariable("updated", job);
                            return (String) execution.getVariable("nested");
                        }
                    }
                    return "done";
                }
            }
            """,
            """
            package org.camunda.conversion.java_delegates.handling_process_variables;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.springframework.stereotype.Component;

            @Component
            public class RetrievePaymentAdapter {

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    // TODO: nested DelegateExecution calls require manual migration because the copied job worker cannot preserve an arbitrary execution scope.
                    class NestedCallback {
                        String read(DelegateExecution execution, String job) {
                            execution.setVariable("updated", job);
                            return (String) execution.getVariable("nested");
                        }
                    }
                    return "done";
                }
            }
            """));
  }

  @Test
  void castsUnattributedControlPredicatesAndArrayLookups() {
    rewriteRun(
        spec ->
            spec.recipes(clearVariableLookupMethodTypes(), new MigrateExecutionRecipe())
                .expectedCyclesThatMakeChanges(1)
                .typeValidationOptions(TypeValidation.none()),
        java(
            """
            package org.camunda.conversion.java_delegates.handling_process_variables;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.camunda.bpm.engine.delegate.VariableScope;
            import org.springframework.stereotype.Component;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                private interface BooleanVariableScope extends VariableScope {
                    @Override
                    Boolean getVariable(String variableName);
                }

                private interface StringArrayVariableScope extends VariableScope {
                    @Override
                    String[] getVariable(String variableName);
                }

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    BooleanVariableScope booleanExecution = null;
                    StringArrayVariableScope arrayExecution = null;
                    if (booleanExecution.getVariable("enabled")) {
                        return arrayExecution.getVariable("values")[0];
                    }
                    return "disabled";
                }
            }
            """,
            """
            package org.camunda.conversion.java_delegates.handling_process_variables;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.camunda.bpm.engine.delegate.VariableScope;
            import org.springframework.stereotype.Component;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                private interface BooleanVariableScope extends VariableScope {
                    @Override
                    Boolean getVariable(String variableName);
                }

                private interface StringArrayVariableScope extends VariableScope {
                    @Override
                    String[] getVariable(String variableName);
                }

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    BooleanVariableScope booleanExecution = null;
                    StringArrayVariableScope arrayExecution = null;
                    if ((Boolean) job.getVariablesAsMap().get("enabled")) {
                        return ((String[]) job.getVariablesAsMap().get("values"))[0];
                    }
                    return "disabled";
                }
            }
            """));
  }

  @Test
  void castsIndividualVarargsLookupsToTheComponentType() {
    rewriteRun(
        spec ->
            spec.recipes(clearVariableLookupMethodTypes(), new MigrateExecutionRecipe())
                .expectedCyclesThatMakeChanges(1)
                .typeValidationOptions(TypeValidation.none()),
        java(
            """
            package org.camunda.conversion.java_delegates.handling_process_variables;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.camunda.bpm.engine.delegate.VariableScope;
            import org.springframework.stereotype.Component;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                private interface StringVariableScope extends VariableScope {
                    @Override
                    String getVariable(String variableName);
                }

                private static void consume(String... values) {
                }

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    StringVariableScope execution = null;
                    consume("prefix", execution.getVariable("vararg"));
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
            import org.camunda.bpm.engine.delegate.VariableScope;
            import org.springframework.stereotype.Component;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                private interface StringVariableScope extends VariableScope {
                    @Override
                    String getVariable(String variableName);
                }

                private static void consume(String... values) {
                }

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    StringVariableScope execution = null;
                    consume("prefix", (String) job.getVariablesAsMap().get("vararg"));
                    return "done";
                }
            }
            """));
  }

  @Test
  void preservesTypedLookupsInBooleanArrayBinaryAndVarargsContexts() {
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
            import org.camunda.bpm.engine.delegate.VariableScope;
            import org.springframework.stereotype.Component;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                private interface BooleanVariableScope extends VariableScope {
                    @Override
                    Boolean getVariable(String variableName);
                }

                private interface StringArrayVariableScope extends VariableScope {
                    @Override
                    String[] getVariable(String variableName);
                }

                private interface StringVariableScope extends VariableScope {
                    @Override
                    String getVariable(String variableName);
                }

                private static void consume(String... values) {
                }

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    BooleanVariableScope booleanExecution = null;
                    StringArrayVariableScope arrayExecution = null;
                    StringVariableScope stringExecution = null;
                    if (booleanExecution.getVariable("enabled")) {
                        consume("enabled");
                    }
                    String first = arrayExecution.getVariable("values")[0];
                    String concatenated = stringExecution.getVariable("value") + 1;
                    consume("prefix", stringExecution.getVariable("vararg"));
                    consume(arrayExecution.getVariable("varargs"));
                    return first + concatenated;
                }
            }
            """,
            """
            package org.camunda.conversion.java_delegates.handling_process_variables;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.camunda.bpm.engine.delegate.VariableScope;
            import org.springframework.stereotype.Component;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                private interface BooleanVariableScope extends VariableScope {
                    @Override
                    Boolean getVariable(String variableName);
                }

                private interface StringArrayVariableScope extends VariableScope {
                    @Override
                    String[] getVariable(String variableName);
                }

                private interface StringVariableScope extends VariableScope {
                    @Override
                    String getVariable(String variableName);
                }

                private static void consume(String... values) {
                }

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    BooleanVariableScope booleanExecution = null;
                    StringArrayVariableScope arrayExecution = null;
                    StringVariableScope stringExecution = null;
                    if ((Boolean) job.getVariablesAsMap().get("enabled")) {
                        consume("enabled");
                    }
                    String first = ((String[]) job.getVariablesAsMap().get("values"))[0];
                    String concatenated = (String) job.getVariablesAsMap().get("value") + 1;
                    consume("prefix", (String) job.getVariablesAsMap().get("vararg"));
                    consume((String[]) job.getVariablesAsMap().get("varargs"));
                    return first + concatenated;
                }
            }
            """));
  }

  @Test
  void castsVariableLookupUsedAsMethodReceiver() {
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
            import org.camunda.bpm.engine.delegate.VariableScope;
            import org.springframework.stereotype.Component;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                private interface StringVariableScope extends VariableScope {
                    @Override
                    String getVariable(String variableName);
                }

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    StringVariableScope execution = null;
                    String value = execution.getVariable("value").trim();
                    return value;
                }
            }
            """,
            """
            package org.camunda.conversion.java_delegates.handling_process_variables;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.camunda.bpm.engine.delegate.VariableScope;
            import org.springframework.stereotype.Component;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                private interface StringVariableScope extends VariableScope {
                    @Override
                    String getVariable(String variableName);
                }

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    StringVariableScope execution = null;
                    String value = ((String) job.getVariablesAsMap().get("value")).trim();
                    return value;
                }
            }
            """));
  }

  @Test
  void flagsChainedVariableLookupWhenReceiverTypeIsUnavailable() {
    rewriteRun(
        spec ->
            spec.recipes(clearMethodInvocationMethodTypes(), new MigrateExecutionRecipe())
                .expectedCyclesThatMakeChanges(2)
                .typeValidationOptions(TypeValidation.none()),
        java(
            """
            package org.camunda.conversion.java_delegates.handling_process_variables;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.camunda.bpm.engine.delegate.VariableScope;
            import org.springframework.stereotype.Component;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                private interface StringVariableScope extends VariableScope {
                    @Override
                    String getVariable(String variableName);
                }

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    StringVariableScope execution = null;
                    String value = execution.getVariable("value").trim();
                    return value;
                }
            }
            """,
            """
            package org.camunda.conversion.java_delegates.handling_process_variables;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.camunda.bpm.engine.delegate.VariableScope;
            import org.springframework.stereotype.Component;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                private interface StringVariableScope extends VariableScope {
                    @Override
                    String getVariable(String variableName);
                }

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    StringVariableScope execution = null;
                    // TODO: chained variable lookups require manual migration because the receiver type cannot be inferred safely.
                    String value = job.getVariablesAsMap().get("value").trim();
                    return value;
                }
            }
            """));
  }

  @Test
  void flagsUnsupportedTypedVariableLookupOverloads() {
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
                    Object typed = execution.getVariable("typed", String.class);
                    Object deserialize = execution.getVariable("deserialize", true);
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
                    // TODO: typed getVariable overload requires manual migration because job workers do not expose the Camunda 7 typed lookup contract.
                    Object typed = getVariableRequiresManualMigration("typed", String.class);
                    // TODO: typed getVariable overload requires manual migration because job workers do not expose the Camunda 7 typed lookup contract.
                    Object deserialize = getVariableRequiresManualMigration("deserialize", true);
                    return "done";
                }

                private static <T> T getVariableRequiresManualMigration(String variableName, Object... ignored) {
                    throw new UnsupportedOperationException(
                            "Manual migration required for getVariable: " + variableName);
                }
            }
            """));
  }

  @Test
  void flagsMultiArgumentVariableMethodReferences() {
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

            import java.util.function.BiFunction;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    BiFunction<String, Class<String>, Object> lookup = execution::getVariable;
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

            import java.util.function.BiFunction;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    // TODO: typed getVariable overload requires manual migration because job workers do not expose the Camunda 7 typed lookup contract.
                    BiFunction<String, Class<String>, Object> lookup = (variableName, argument1) -> getVariableRequiresManualMigration(variableName, argument1);
                    return "done";
                }

                private static <T> T getVariableRequiresManualMigration(String variableName, Object... ignored) {
                    throw new UnsupportedOperationException(
                            "Manual migration required for getVariable: " + variableName);
                }
            }
            """));
  }

  @Test
  void preservesFullyQualifiedCastTypeForVariableLookupArguments() {
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
                    java.time.Instant value = null;
                    value = execution.getVariable("instant");
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
                    java.time.Instant value = null;
                    value = (java.time.Instant) job.getVariablesAsMap().get("instant");
                    return "done";
                }
            }
            """));
  }

  @Test
  void preservesArgumentsOnUnsupportedGetVariableOverloads() {
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
                    Object value = execution.getVariable("typed", String.class);
                    Object noArgument = execution.getVariable();
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
                    // TODO: typed getVariable overload requires manual migration because job workers do not expose the Camunda 7 typed lookup contract.
                    Object value = getVariableRequiresManualMigration("typed", String.class);
                    Object noArgument = execution.getVariable();
                    return "done";
                }

                private static <T> T getVariableRequiresManualMigration(String variableName, Object... ignored) {
                    throw new UnsupportedOperationException(
                            "Manual migration required for getVariable: " + variableName);
                }
            }
            """));
  }

  @Test
  void castsVariableLookupsInTypedLambdaAndArrayExpressions() {
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

            import java.util.function.Function;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public Integer executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    Function<String, String> read = ignored -> execution.getVariable("lambda");
                    Function<String, String> blockRead =
                            ignored -> {
                                return execution.getVariable("block");
                            };
                    String[] values = {execution.getVariable("array")};
                    return 1;
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

            import java.util.function.Function;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public Integer executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    Function<String, String> read = ignored -> (String) job.getVariablesAsMap().get("lambda");
                    Function<String, String> blockRead =
                            ignored -> {
                                return (String) job.getVariablesAsMap().get("block");
                            };
                    String[] values = {(String) job.getVariablesAsMap().get("array")};
                    return 1;
                }
            }
            """));
  }

  @Test
  void flagsLocalMethodReferencesAndPreservesTypedOverloads() {
    rewriteRun(
        spec ->
            spec.expectedCyclesThatMakeChanges(2)
                .typeValidationOptions(TypeValidation.none()),
        java(
            """
            package org.camunda.conversion.java_delegates.handling_process_variables;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.springframework.stereotype.Component;

            import java.util.Map;
            import java.util.function.Function;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    Function<String, Object> localLookup = execution::getVariableLocal;
                    String typedLocal = execution.getVariableLocal("typedLocal", String.class);
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

            import java.util.Map;
            import java.util.function.Function;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    // TODO: getVariableLocal requires manual migration because Camunda 8 job workers do not expose the Camunda 7 execution scope.
                    Function<String, Object> localLookup = variableName -> getVariableLocalRequiresManualMigration(variableName);
                    // TODO: getVariableLocal requires manual migration because Camunda 8 job workers do not expose the Camunda 7 execution scope.
                    String typedLocal = getVariableLocalRequiresManualMigration("typedLocal", String.class);
                    return "done";
                }

                private static <T> T getVariableLocalRequiresManualMigration(String variableName) {
                    throw new UnsupportedOperationException(
                            "Manual migration required for getVariableLocal: " + variableName);
                }

                private static <T> T getVariableLocalRequiresManualMigration(String variableName, Object... ignored) {
                    throw new UnsupportedOperationException(
                            "Manual migration required for getVariableLocal: " + variableName);
                }
            }
            """));
  }

  @Test
  void flagsTypedLocalMethodReferenceOverload() {
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

            import java.util.function.BiFunction;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    BiFunction<String, Boolean, Object> typedLocalLookup = execution::getVariableLocalTyped;
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

            import java.util.function.BiFunction;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    // TODO: getVariableLocal requires manual migration because Camunda 8 job workers do not expose the Camunda 7 execution scope.
                    BiFunction<String, Boolean, Object> typedLocalLookup = (variableName, argument1) -> getVariableLocalRequiresManualMigration(variableName, argument1);
                    return "done";
                }

                private static <T> T getVariableLocalRequiresManualMigration(String variableName) {
                    throw new UnsupportedOperationException(
                            "Manual migration required for getVariableLocal: " + variableName);
                }

                private static <T> T getVariableLocalRequiresManualMigration(String variableName, Object... ignored) {
                    throw new UnsupportedOperationException(
                            "Manual migration required for getVariableLocal: " + variableName);
                }
            }
            """));
  }

  @Test
  void preservesReceiverInUnboundLocalVariableMethodReference() {
    rewriteRun(
        spec ->
            spec.expectedCyclesThatMakeChanges(2)
            .typeValidationOptions(TypeValidation.none()),
        java(
            """
            package org.camunda.conversion.java_delegates.handling_process_variables;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.springframework.stereotype.Component;

            import java.util.function.BiFunction;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

            @Override
            public void execute(DelegateExecution execution) {
            }

            @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
            public String executeJobMigrated(ActivatedJob job) {
                DelegateExecution execution = null;
                BiFunction<DelegateExecution, String, Object> localLookup = DelegateExecution::getVariableLocal;
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

            import java.util.function.BiFunction;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

            @Override
            public void execute(DelegateExecution execution) {
            }

            @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
            public String executeJobMigrated(ActivatedJob job) {
                DelegateExecution execution = null;
                // TODO: getVariableLocal requires manual migration because Camunda 8 job workers do not expose the Camunda 7 execution scope.
                BiFunction<DelegateExecution, String, Object> localLookup = (execution1, variableName) -> getVariableLocalRequiresManualMigration(variableName, execution1);
                return "done";
            }

                private static <T> T getVariableLocalRequiresManualMigration(String variableName) {
                    throw new UnsupportedOperationException(
                            "Manual migration required for getVariableLocal: " + variableName);
                }

                private static <T> T getVariableLocalRequiresManualMigration(String variableName, Object... ignored) {
                    throw new UnsupportedOperationException(
                            "Manual migration required for getVariableLocal: " + variableName);
                }
            }
            """));
  }

  @Test
  void leavesVariableScopeLambdaLookupsForManualMigration() {
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

            import java.util.function.Function;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    Function<DelegateExecution, Object> read =
                            execution -> execution.getVariable("nested");
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

            import java.util.function.Function;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) {
                    // TODO: nested DelegateExecution calls require manual migration because the copied job worker cannot preserve an arbitrary execution scope.
                    Function<DelegateExecution, Object> read =
                            execution -> execution.getVariable("nested");
                    return "done";
                }
            }
            """));
  }

  @Test
  void preservesLookupTypesInParenthesizedThrowVarAndFunctionalContexts() {
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
            import org.camunda.bpm.engine.delegate.VariableScope;
            import org.springframework.stereotype.Component;

            import java.util.function.BiPredicate;
            import java.util.function.Predicate;
            import java.util.function.ToIntFunction;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                private interface BooleanVariableScope extends VariableScope {
                    @Override
                    Boolean getVariable(String variableName);
                }

                private interface IntegerVariableScope extends VariableScope {
                    @Override
                    Integer getVariable(String variableName);
                }

                private interface StringVariableScope extends VariableScope {
                    @Override
                    String getVariable(String variableName);
                }

                private interface ThrowableVariableScope extends VariableScope {
                    @Override
                    Throwable getVariable(String variableName);
                }

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) throws Throwable {
                    BooleanVariableScope booleanExecution = null;
                    IntegerVariableScope integerExecution = null;
                    StringVariableScope stringExecution = null;
                    ThrowableVariableScope throwableExecution = null;
                    Predicate<String> predicate = booleanExecution::getVariable;
                    BiPredicate<String, String> biPredicate =
                            (name, ignored) -> booleanExecution.getVariable(name);
                    ToIntFunction<String> intLookup = integerExecution::getVariable;
                    String parenthesized = (stringExecution.getVariable("parenthesized"));
                    var inferred = stringExecution.getVariable("inferred");
                    inferred.trim();
                    throw throwableExecution.getVariable("failure");
                }
            }
            """,
            """
            package org.camunda.conversion.java_delegates.handling_process_variables;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.camunda.bpm.engine.delegate.VariableScope;
            import org.springframework.stereotype.Component;

            import java.util.function.BiPredicate;
            import java.util.function.Predicate;
            import java.util.function.ToIntFunction;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                private interface BooleanVariableScope extends VariableScope {
                    @Override
                    Boolean getVariable(String variableName);
                }

                private interface IntegerVariableScope extends VariableScope {
                    @Override
                    Integer getVariable(String variableName);
                }

                private interface StringVariableScope extends VariableScope {
                    @Override
                    String getVariable(String variableName);
                }

                private interface ThrowableVariableScope extends VariableScope {
                    @Override
                    Throwable getVariable(String variableName);
                }

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public String executeJobMigrated(ActivatedJob job) throws Throwable {
                    BooleanVariableScope booleanExecution = null;
                    IntegerVariableScope integerExecution = null;
                    StringVariableScope stringExecution = null;
                    ThrowableVariableScope throwableExecution = null;
                    Predicate<String> predicate = variableName -> (boolean) job.getVariablesAsMap().get(variableName);
                    BiPredicate<String, String> biPredicate =
                            (name, ignored) -> (boolean) job.getVariablesAsMap().get(name);
                    ToIntFunction<String> intLookup = variableName -> (int) job.getVariablesAsMap().get(variableName);
                    String parenthesized = (String) (job.getVariablesAsMap().get("parenthesized"));
                    var inferred = (String) job.getVariablesAsMap().get("inferred");
                    inferred.trim();
                    throw (Throwable) job.getVariablesAsMap().get("failure");
                }
            }
            """));
  }

  @Test
  void preservesReceiverInUnboundUnsupportedVariableMethodReference() {
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

            @FunctionalInterface
            private interface TriFunction<A, B, C, R> {
                R apply(A receiver, B variableName, C type);
            }

            @Override
            public void execute(DelegateExecution execution) {
            }

            @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
            public String executeJobMigrated(ActivatedJob job) {
                DelegateExecution execution = null;
                TriFunction<DelegateExecution, String, Class<String>, Object> lookup = DelegateExecution::getVariable;
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

            @FunctionalInterface
            private interface TriFunction<A, B, C, R> {
                R apply(A receiver, B variableName, C type);
            }

            @Override
            public void execute(DelegateExecution execution) {
            }

            @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
            public String executeJobMigrated(ActivatedJob job) {
                DelegateExecution execution = null;
                // TODO: typed getVariable overload requires manual migration because job workers do not expose the Camunda 7 typed lookup contract.
                TriFunction<DelegateExecution, String, Class<String>, Object> lookup = (execution1, variableName, argument1) -> getVariableRequiresManualMigration(variableName, argument1, execution1);
                return "done";
            }

                private static <T> T getVariableRequiresManualMigration(String variableName, Object... ignored) {
                    throw new UnsupportedOperationException(
                            "Manual migration required for getVariable: " + variableName);
                }
            }
            """));
  }

  @Test
  void flagsUnboundVariableMethodReferencesForManualMigration() {
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

            import java.util.function.BiFunction;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

            @Override
            public void execute(DelegateExecution execution) {
            }

            @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
            public String executeJobMigrated(ActivatedJob job) {
                DelegateExecution execution = null;
                BiFunction<DelegateExecution, String, Object> lookup = DelegateExecution::getVariable;
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

            import java.util.function.BiFunction;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

            @Override
            public void execute(DelegateExecution execution) {
            }

            @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
            public String executeJobMigrated(ActivatedJob job) {
                DelegateExecution execution = null;
                // TODO: unbound getVariable method reference requires manual migration because job workers do not expose an arbitrary Camunda 7 execution scope.
                BiFunction<DelegateExecution, String, Object> lookup = (execution1, variableName) -> getVariableRequiresManualMigration(variableName, execution1);
                return "done";
            }

                private static <T> T getVariableRequiresManualMigration(String variableName, Object... ignored) {
                    throw new UnsupportedOperationException(
                            "Manual migration required for getVariable: " + variableName);
                }
            }
            """));
  }

  @Test
  void migratesTypedVariableMethodReferenceInCopiedWorker() {
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
            import org.camunda.bpm.engine.variable.value.TypedValue;
            import org.springframework.stereotype.Component;

            import java.util.function.BiFunction;
            import java.util.function.Function;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

            @Override
            public void execute(DelegateExecution execution) {
            }

            @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
            public String executeJobMigrated(ActivatedJob job) {
                DelegateExecution execution = null;
                Function<String, TypedValue> lookup = execution::getVariableTyped;
                BiFunction<DelegateExecution, String, TypedValue> unboundLookup =
                        DelegateExecution::getVariableTyped;
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
            import org.camunda.bpm.engine.variable.value.TypedValue;
            import org.springframework.stereotype.Component;

            import java.util.function.BiFunction;
            import java.util.function.Function;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

            @Override
            public void execute(DelegateExecution execution) {
            }

            @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
            public String executeJobMigrated(ActivatedJob job) {
                DelegateExecution execution = null;
                // TODO: typed getVariable overload requires manual migration because job workers do not expose the Camunda 7 typed lookup contract.
                Function<String, TypedValue> lookup = variableName -> getVariableRequiresManualMigration(variableName);
                // TODO: typed getVariable overload requires manual migration because job workers do not expose the Camunda 7 typed lookup contract.
                BiFunction<DelegateExecution, String, TypedValue> unboundLookup =
                        (execution1, variableName) -> getVariableRequiresManualMigration(variableName, execution1);
                return "done";
            }

                private static <T> T getVariableRequiresManualMigration(String variableName, Object... ignored) {
                    throw new UnsupportedOperationException(
                            "Manual migration required for getVariable: " + variableName);
                }
            }
            """));
  }

  @Test
  void adaptsOptionalAndMapFactoryVariableLookups() {
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

            import java.util.Map;
            import java.util.Optional;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public Map<String, Integer> executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    Optional<String> optional =
                            Optional.ofNullable(execution.getVariable("optional"));
                    Map<String, Integer> values =
                            Map.of("value", execution.getVariable("value"));
                    Map<String, Integer> entries =
                            Map.ofEntries(Map.entry("entry", execution.getVariable("entry")));
                    return values;
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

            import java.util.Map;
            import java.util.Optional;

            @Component
            public class RetrievePaymentAdapter implements JavaDelegate {

                @Override
                public void execute(DelegateExecution execution) {
                }

                @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
                public Map<String, Integer> executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    Optional<String> optional =
                            Optional.ofNullable((String) job.getVariablesAsMap().get("optional"));
                    Map<String, Integer> values =
                            Map.of("value", (Integer) job.getVariablesAsMap().get("value"));
                    Map<String, Integer> entries =
                            Map.ofEntries(Map.entry("entry", (Integer) job.getVariablesAsMap().get("entry")));
                    return values;
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
