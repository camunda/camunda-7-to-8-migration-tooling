/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.migrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.openrewrite.java.Assertions.java;

import io.camunda.client.impl.search.response.SearchResponsePageImpl;
import io.camunda.migration.code.recipes.client.MigrateProcessInstanceQueryMethodsRecipe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

class ReplaceProcessInstanceQueryMethodsTest implements RewriteTest {

  private static final String MANUAL_HINT =
      "    // TODO: Migrate this Camunda 7 process-instance query manually; preserve its filters, runtime state and complete results (including pagination).\n";

  private static final String SOURCE =
      """
      import io.camunda.client.CamundaClient;
      import java.util.List;
      import org.camunda.bpm.engine.RuntimeService;
      import org.camunda.bpm.engine.runtime.ProcessInstance;
      import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;

      class Queries {
          RuntimeService runtimeService;
          CamundaClient camundaClient;

      %s    void check(ProcessInstanceQuery alias, String processId, String activityId, String variableName, String value) {
      %s
          }
      }
      """;

  @Test
  void convertsCompleteInlineActiveCounts() {
    assertConverted(
        """
                int size = runtimeService.createProcessInstanceQuery().active().list().size();
                long count = runtimeService.createProcessInstanceQuery().active().count();
                long streamCount = runtimeService.createProcessInstanceQuery()
                        .processDefinitionKey(processId).active().list().stream().count();
        """,
        """
                int size = java.util.Optional.of(camundaClient
                        .newProcessInstanceSearchRequest()
                        .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                        .send()
                        .join()
                        .page())
                        .filter(page -> Boolean.FALSE.equals(page.hasMoreTotalItems()))
                        .orElseThrow(() -> new IllegalStateException("Process-instance count exceeds search limit; paginate to count exactly"))
                        .totalItems().intValue();
                long count = java.util.Optional.of(camundaClient
                        .newProcessInstanceSearchRequest()
                        .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                        .send()
                        .join()
                        .page())
                        .filter(page -> Boolean.FALSE.equals(page.hasMoreTotalItems()))
                        .orElseThrow(() -> new IllegalStateException("Process-instance count exceeds search limit; paginate to count exactly"))
                        .totalItems().longValue();
                long streamCount = java.util.Optional.of(camundaClient
                        .newProcessInstanceSearchRequest()
                        .filter(filter -> filter
                                .processDefinitionId(processId)
                                .state(ProcessInstanceState.ACTIVE))
                        .send()
                        .join()
                        .page())
                        .filter(page -> Boolean.FALSE.equals(page.hasMoreTotalItems()))
                        .orElseThrow(() -> new IllegalStateException("Process-instance count exceeds search limit; paginate to count exactly"))
                        .totalItems().longValue();
        """);
  }

  @Test
  void rejectsCappedTotalInsteadOfReturningTheLowerBound() {
    var page = new SearchResponsePageImpl(10_000L, true, null, null);
    var error =
        assertThrows(
            IllegalStateException.class,
            () ->
                java.util.Optional.of(page)
                    .filter(candidate -> Boolean.FALSE.equals(candidate.hasMoreTotalItems()))
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Process-instance count exceeds search limit; paginate to count exactly"))
                    .totalItems()
                    .longValue());
    assertEquals(
        "Process-instance count exceeds search limit; paginate to count exactly",
        error.getMessage());
  }

  @Test
  void convertsParenthesizedInlineCount() {
    assertConverted(
        """
                int size = ((runtimeService.createProcessInstanceQuery().active())
                        .processDefinitionKey(processId).list()).size();
        """,
        """
                int size = java.util.Optional.of(camundaClient
                        .newProcessInstanceSearchRequest()
                        .filter(filter -> filter
                                .processDefinitionId(processId)
                                .state(ProcessInstanceState.ACTIVE))
                        .send()
                        .join()
                        .page())
                        .filter(page -> Boolean.FALSE.equals(page.hasMoreTotalItems()))
                        .orElseThrow(() -> new IllegalStateException("Process-instance count exceeds search limit; paginate to count exactly"))
                        .totalItems().intValue();
        """);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "variableValueEquals",
        "variableValueNotEquals",
        "variableValueGreaterThan",
        "variableValueGreaterThanOrEqual",
        "variableValueLessThan",
        "variableValueLessThanOrEqual",
        "variableValueLike"
      })
  void leavesEveryVariablePredicateManual(String predicate) {
    assertManual(
        "        long count = runtimeService.createProcessInstanceQuery()\n"
            + "                .processDefinitionKey(processId)."
            + predicate
            + "(variableName, value).active().count();\n");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "runtimeService.createProcessInstanceQuery().active().list();",
        "runtimeService.createProcessInstanceQuery().active().singleResult();",
        "runtimeService.createProcessInstanceQuery().active().listPage(0, 2);",
        "runtimeService.createProcessInstanceQuery().active().unlimitedList();",
        "runtimeService.createProcessInstanceQuery().active();",
        "runtimeService.createProcessInstanceQuery().processDefinitionKey(processId).count();",
        "runtimeService.createProcessInstanceQuery().suspended().count();",
        "runtimeService.createProcessInstanceQuery().processInstanceBusinessKey(value).active().count();",
        "runtimeService.createProcessInstanceQuery().processInstanceId(value).active().count();",
        "runtimeService.createProcessInstanceQuery().activityIdIn().active().count();",
        "runtimeService.createProcessInstanceQuery().activityIdIn(activityId).active().count();",
        "runtimeService.createProcessInstanceQuery().activityIdIn(\"one\", \"two\").active().count();",
        "runtimeService.createProcessInstanceQuery().processDefinitionKey(processId).activityIdIn(activityId).active().count();",
        "runtimeService.createProcessInstanceQuery().processDefinitionKey(\"one\").processDefinitionKey(\"two\").active().count();",
        "runtimeService.createProcessInstanceQuery().active().list().stream().filter(pi -> true).count();",
        "alias.active().count();",
        "alias.active().list();"
      })
  void leavesUnsupportedTerminalsFiltersAndAliasesManual(String query) {
    assertManual("        " + query + "\n");
  }

  @Test
  void marksVariableMatchedLookupAsBlockedRatherThanDroppingItsFilter() {
    assertManual(
        """
                ProcessInstance match = runtimeService.createProcessInstanceQuery()
                        .processDefinitionKey(processId).variableValueEquals(variableName, value)
                        .active().singleResult();
        """);
  }

  @Test
  void leavesListResultTypesAndPreFilteredAliasesUntouched() {
    assertManual(
        """
                List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
                        .processDefinitionKey(processId).active().list();
                ProcessInstanceQuery filtered = runtimeService.createProcessInstanceQuery()
                        .variableValueEquals(variableName, value);
                filtered.active().count();
                long streamed = instances.stream().count();
        """);
  }

  @Test
  void marksManualQueryOnlyOnceWhenAnotherCountConverts() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            SOURCE.formatted(
                "",
                """
                        long active = runtimeService.createProcessInstanceQuery().active().count();
                        long filtered = runtimeService.createProcessInstanceQuery()
                                .variableValueEquals(variableName, value).active().count();
                """),
            SOURCE.replace(
                    "import java.util.List;",
                    "import io.camunda.client.api.search.enums.ProcessInstanceState;\n\nimport java.util.List;")
                .formatted(
                    MANUAL_HINT,
                    """
                            long active = java.util.Optional.of(camundaClient
                                    .newProcessInstanceSearchRequest()
                                    .filter(filter -> filter.state(ProcessInstanceState.ACTIVE))
                                    .send()
                                    .join()
                                    .page())
                                    .filter(page -> Boolean.FALSE.equals(page.hasMoreTotalItems()))
                                    .orElseThrow(() -> new IllegalStateException("Process-instance count exceeds search limit; paginate to count exactly"))
                                    .totalItems().longValue();
                            long filtered = runtimeService.createProcessInstanceQuery()
                                    .variableValueEquals(variableName, value).active().count();
                    """)));
  }

  @Test
  void marksFieldQueriesAndDoesNotDuplicateAnExistingHint() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            import org.camunda.bpm.engine.RuntimeService;
            import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;

            class Queries {
                RuntimeService runtimeService;
                ProcessInstanceQuery query = runtimeService.createProcessInstanceQuery()
                        .variableValueEquals("name", "value");
            }
            """,
            """
            import org.camunda.bpm.engine.RuntimeService;
            import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;

            class Queries {
                RuntimeService runtimeService;
                // TODO: Migrate this Camunda 7 process-instance query manually; preserve its filters, runtime state and complete results (including pagination).
                ProcessInstanceQuery query = runtimeService.createProcessInstanceQuery()
                        .variableValueEquals("name", "value");
            }
            """));
  }

  @Test
  void doesNotTouchUnrelatedTaskQueries() {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            """
            import org.camunda.bpm.engine.TaskService;

            class Tasks {
                TaskService taskService;

                void search() {
                    taskService.createTaskQuery().processDefinitionKey("orders").list();
                }
            }
            """));
  }

  @Test
  void compositeRecipeLeavesInstanceListsAndTypesForManualMigration() {
    rewriteRun(
        spec -> spec.recipeFromResources("io.camunda.migration.code.recipes.AllClientMigrateRecipes"),
        java(
            """
            import io.camunda.client.CamundaClient;
            import java.util.List;
            import org.camunda.bpm.engine.RuntimeService;
            import org.camunda.bpm.engine.runtime.ProcessInstance;

            class Queries {
                RuntimeService runtimeService;
                CamundaClient camundaClient;

                List<ProcessInstance> search(String processId) {
                    return runtimeService.createProcessInstanceQuery()
                            .processDefinitionKey(processId).active().list();
                }
            }
            """,
            """
            import io.camunda.client.CamundaClient;
            import java.util.List;
            import org.camunda.bpm.engine.RuntimeService;
            import org.camunda.bpm.engine.runtime.ProcessInstance;

            class Queries {
                RuntimeService runtimeService;
                CamundaClient camundaClient;

                // TODO: Migrate this Camunda 7 process-instance query manually; preserve its filters, runtime state and complete results (including pagination).
                List<ProcessInstance> search(String processId) {
                    return runtimeService.createProcessInstanceQuery()
                            .processDefinitionKey(processId).active().list();
                }
            }
            """));
  }

  private void assertManual(String statements) {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(SOURCE.formatted("", statements), SOURCE.formatted(MANUAL_HINT, statements)));
  }

  private void assertConverted(String before, String after) {
    rewriteRun(
        spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
            SOURCE.formatted("", before),
            SOURCE.replace(
                    "import java.util.List;",
                    "import io.camunda.client.api.search.enums.ProcessInstanceState;\n\nimport java.util.List;")
                .formatted("", after)));
  }
}
