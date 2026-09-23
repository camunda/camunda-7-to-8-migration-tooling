/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.prepare;

import static org.openrewrite.java.Assertions.java;

import io.camunda.migration.code.recipes.delegate.MigrateExecutionRecipe;
import io.camunda.migration.code.recipes.sharedRecipes.ReplaceTypedValueAPIRecipe;
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

class ReplaceTypedValueAPITest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
  }

  private static Recipe clearVariableLookupMethodTypes() {
    return new Recipe() {
      @Override
      public String getDisplayName() {
        return "Clear variable lookup method types";
      }

      @Override
      public String getDescription() {
        return "Removes variable lookup method attribution to exercise fallback behavior.";
      }

      @Override
      public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
          @Override
          public J.MethodInvocation visitMethodInvocation(
              J.MethodInvocation invocation, ExecutionContext ctx) {
            J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
            return "getVariableLocalTyped".equals(visited.getSimpleName())
                ? visited.withMethodType(null)
                : visited;
          }
        };
      }
    };
  }

  @Test
  void replaceTypedValueAPITest() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        // language=java
        java(
            """
package org.camunda.community.migration.example;

import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.value.DoubleValue;
import org.camunda.bpm.engine.variable.value.IntegerValue;
import org.camunda.bpm.engine.variable.value.StringValue;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class TypeValueTestClass {

    private class CustomObject {
        private String someString;

        private Long someLong;

        public CustomObject(String someString, Long someLong) {
            this.someString = someString;
            this.someLong = someLong;
        }
    }

    public void someMethod(CustomObject customObject, DoubleValue doubleTyped) {
        IntegerValue amountTyped = Variables.integerValue(2);
        StringValue nameTyped = Variables.stringValue("2");
        nameTyped = Variables.stringValue("3");
        String bla = "blub";

        double someDouble = doubleTyped.getValue();
        someDouble = doubleTyped.getValue();

        VariableMap map1 = Variables.createVariables().putValueTyped("name", nameTyped).putValueTyped("amount", amountTyped);
        map1.putValue("bla", bla);
        map1.putValueTyped("double", doubleTyped);

        Variables.createVariables().putValue("blub", "blub").putValueTyped("name", nameTyped);

        VariableMap map2 = Variables.fromMap(Collections.singletonMap("amount", amountTyped));

        ObjectValue objectValue = Variables.objectValue(customObject).create();
    }
}
""",
"""
package org.camunda.community.migration.example;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class TypeValueTestClass {

    private class CustomObject {
        private String someString;

        private Long someLong;

        public CustomObject(String someString, Long someLong) {
            this.someString = someString;
            this.someLong = someLong;
        }
    }

    public void someMethod(CustomObject customObject, Double doubleTyped) {
        Integer amountTyped = 2;
        String nameTyped = "2";
        nameTyped = "3";
        String bla = "blub";

        double someDouble = doubleTyped;
        someDouble = doubleTyped;
        Map<String, Object> map1 = new HashMap<>();
        map1.put("amount", amountTyped);
        map1.put("name", nameTyped);
        map1.put("bla", bla);
        map1.put("double", doubleTyped);

        Map.ofEntries(Map.entry("blub", "blub"), Map.entry("name", nameTyped));

        Map<String, Object> map2 = Collections.singletonMap("amount", amountTyped);

        // type set to java.lang.Object
        Object objectValue = customObject;
    }
}
"""));
  }

  @Test
  void keepsTaskServiceTypedLocalLookupOnVariableMigrationPath() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        java(
        """
        package org.camunda.community.migration.example;

        import org.camunda.bpm.engine.TaskService;

        public class TaskServiceTypedLocalLookup {
            public Object getLocalVariable(TaskService taskService) {
                return taskService.getVariableLocalTyped("taskId", "localName");
            }
        }
        """,
        """
        package org.camunda.community.migration.example;

        import org.camunda.bpm.engine.TaskService;

        public class TaskServiceTypedLocalLookup {
            public Object getLocalVariable(TaskService taskService) {
                return taskService.getVariable("taskId", "localName");
            }
        }
        """));
  }

  @Test
  void preservesLocalTypedLookupWhenInvocationAttributionIsMissing() {
    rewriteRun(
        spec ->
            spec.recipes(clearVariableLookupMethodTypes(), new ReplaceTypedValueAPIRecipe())
                .typeValidationOptions(TypeValidation.none()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            public class UnattributedVariableScopeLookup {
                public void read(DelegateExecution execution, TypedValue unrelated) {
                    execution.getVariableLocalTyped("local", false);
                }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            public class UnattributedVariableScopeLookup {
                public void read(DelegateExecution execution, TypedValue unrelated) {
                    execution.getVariableLocal("local", false);
                }
            }
            """));
  }

  @Test
  void routesUnattributedTypedLocalLookupToManualFallback() {
    rewriteRun(
        spec ->
            spec.recipes(
                    clearVariableLookupMethodTypes(),
                    new ReplaceTypedValueAPIRecipe(),
                    new MigrateExecutionRecipe())
                .expectedCyclesThatMakeChanges(2)
                .typeValidationOptions(TypeValidation.none()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            public class UnattributedDelegateLocalLookup implements JavaDelegate {
                @Override
                public void execute(DelegateExecution delegateExecution) {
                }

                @JobWorker(type = "worker")
                public String executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    TypedValue unrelated = null;
                    execution.getVariableLocalTyped("local");
                    return "done";
                }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.api.response.ActivatedJob;
            import io.camunda.client.annotation.JobWorker;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            public class UnattributedDelegateLocalLookup implements JavaDelegate {
                @Override
                public void execute(DelegateExecution delegateExecution) {
                }

                @JobWorker(type = "worker")
                public String executeJobMigrated(ActivatedJob job) {
                    DelegateExecution execution = null;
                    TypedValue unrelated = null;
                    // TODO: getVariableLocal requires manual migration because Camunda 8 job workers do not expose the Camunda 7 execution scope.
                    getVariableLocalRequiresManualMigration("local");
                    return "done";
                }

                private static <T> T getVariableLocalRequiresManualMigration(String variableName) {
                    throw new UnsupportedOperationException(
                            "Manual migration required for getVariableLocal: " + variableName);
                }
            }
            """));
  }
}
