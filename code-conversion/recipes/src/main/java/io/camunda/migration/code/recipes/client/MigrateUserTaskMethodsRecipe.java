/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client;

import java.util.*;
import io.camunda.migration.code.recipes.sharedRecipes.AbstractMigrationRecipe;
import io.camunda.migration.code.recipes.utils.BuilderSpecFactory;
import io.camunda.migration.code.recipes.utils.RecipeUtils;
import io.camunda.migration.code.recipes.utils.ReplacementUtils;
import org.openrewrite.*;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;

public class MigrateUserTaskMethodsRecipe extends AbstractMigrationRecipe {

  /**
   * The Camunda user task API commands ({@code newCompleteUserTaskCommand}, {@code
   * newAssignUserTaskCommand}, ...) only work against tasks that carry the {@code <zeebe:userTask
   * />} extension element. Against a job-based user task they fail with a 404, so migrated code
   * gets this hint.
   */
  private static final String ENSURE_ZEEBE_USER_TASK_HINT =
      " TODO: the Camunda user task API requires the BPMN user task element to declare"
          + " <zeebe:userTask />, otherwise this command fails with a 404. Run the Diagram Converter"
          + " to add it automatically.";

  /**
   * C7 has no dedicated {@code unclaim} on {@code TaskService}; unclaiming is done via {@code
   * setAssignee(taskId, null)}. A {@link org.openrewrite.java.MethodMatcher} cannot tell a null
   * assignee apart from a real one, so the assign command is emitted with this hint.
   */
  private static final String NULL_ASSIGNEE_UNCLAIM_HINT =
      " TODO: if the original assignee was null (unclaim), use"
          + " camundaClient.newUnassignUserTaskCommand(taskKey) instead.";

  /**
   * Camunda 8 has no task-scoped variables. {@code newSetVariablesCommand} expects an element
   * instance key rather than the task key, so the generated call needs manual review.
   */
  private static final String NO_TASK_SCOPED_VARIABLES_HINT =
      " TODO: Camunda 8 has no task-scoped variables. newSetVariablesCommand expects the element"
          + " instance key (not the task key); set the variable on the process/element instance"
          + " scope instead.";

  @Override
  public String getDisplayName() {
    return "Migrates user task related methods";
  }

  @Override
  public String getDescription() {
    return "This recipe extends the abstract migration recipe with rules specific to handling user tasks.";
  }

  @Override
  protected TreeVisitor<?, ExecutionContext> preconditions() {
    return Preconditions.or(
        new UsesMethod<>("org.camunda.bpm.engine.TaskService createTaskQuery()", true),
        new UsesMethod<>("org.camunda.bpm.engine.TaskService claim(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.TaskService setAssignee(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.TaskService complete(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.TaskService setVariable(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.TaskService getVariable(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.task.Task getTaskDefinitionKey()", true));
  }

  @Override
  protected List<ReplacementUtils.SimpleReplacementSpec> simpleMethodInvocations() {
    return List.of(
        new ReplacementUtils.SimpleReplacementSpec(
            // "claim(String taskId, String userId)"
            new MethodMatcher(
                "org.camunda.bpm.engine.TaskService claim(java.lang.String, java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newAssignUserTaskCommand(Long.valueOf(#{taskId:any(java.lang.String)}))
                    .assignee(#{userId:any(java.lang.String)})
                    .send()
                    .join();
                """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.AssignUserTaskResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("taskId", 0),
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("userId", 1)),
            Collections.emptyList()),
        new ReplacementUtils.SimpleReplacementSpec(
            // "setAssignee(String taskId, String userId)" - C7 assign; unclaim passes a null userId
            new MethodMatcher(
                "org.camunda.bpm.engine.TaskService setAssignee(java.lang.String, java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newAssignUserTaskCommand(Long.valueOf(#{taskId:any(java.lang.String)}))
                    .assignee(#{userId:any(java.lang.String)})
                    .send()
                    .join();
                """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.AssignUserTaskResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("taskId", 0),
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("userId", 1)),
            List.of(NULL_ASSIGNEE_UNCLAIM_HINT)),
        new ReplacementUtils.SimpleReplacementSpec(
            // "complete(String taskId)"
            new MethodMatcher("org.camunda.bpm.engine.TaskService complete(java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCompleteUserTaskCommand(Long.valueOf(#{taskId:any(java.lang.String)}))
                    .send()
                    .join();
                """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.CompleteUserTaskResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("taskId", 0)),
            List.of(ENSURE_ZEEBE_USER_TASK_HINT)),
        new ReplacementUtils.SimpleReplacementSpec(
            // "complete(String taskId, Map<String, Object> variables)"
            new MethodMatcher(
                "org.camunda.bpm.engine.TaskService complete(java.lang.String, java.util.Map)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCompleteUserTaskCommand(Long.valueOf(#{taskId:any(java.lang.String)}))
                    .variables(#{variables:any(java.util.Map)})
                    .send()
                    .join();
                """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.CompleteUserTaskResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("taskId", 0),
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("variables", 1)),
            List.of(ENSURE_ZEEBE_USER_TASK_HINT)),
        new ReplacementUtils.SimpleReplacementSpec(
            // "setVariable(String taskId, String variableName, Object value)"
            new MethodMatcher(
                "org.camunda.bpm.engine.TaskService setVariable(java.lang.String, java.lang.String, java.lang.Object)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newSetVariablesCommand(Long.valueOf(#{taskId:any(java.lang.String)}))
                    .variables(java.util.Map.of(#{variableName:any(java.lang.String)}, #{value:any(java.lang.Object)}))
                    .send()
                    .join();
                """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.SetVariablesResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("taskId", 0),
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("variableName", 1),
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("value", 2)),
            List.of(NO_TASK_SCOPED_VARIABLES_HINT)),
        new ReplacementUtils.SimpleReplacementSpec(
            // "getVariable(String taskId, String variableName)"
            new MethodMatcher(
                "org.camunda.bpm.engine.TaskService getVariable(java.lang.String, java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newUserTaskVariableSearchRequest(Long.valueOf(#{taskId:any(java.lang.String)}))
                    .filter(userTaskVariableFilter -> userTaskVariableFilter.name(#{variableName:any(java.lang.String)}))
                    .send()
                    .join()
                    .items()
                    .get(0)
                    .getValue();
                """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            null,
            ReplacementUtils.ReturnTypeStrategy.INFER_FROM_CONTEXT,
            List.of(
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("taskId", 0),
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("variableName", 1)),
            List.of(" please check type")));
  }


  @Override
  protected List<ReplacementUtils.BuilderReplacementSpec> builderMethodInvocations() {

    List<ReplacementUtils.BuilderReplacementSpec> specs = new ArrayList<>();
    specs.addAll(BuilderSpecFactory.createBuilderFilterSpecs(
        "org.camunda.bpm.engine.query.Query list()",
        null,
        List.of("processDefinitionKey", "dueBefore"),
        Map.ofEntries(
            Map.entry(
                "processDefinitionKey",
                ".bpmnProcessId(#{processDefinitionKey:any(java.lang.String)})"),
            Map.entry(
                "dueBefore",
                ".dueDate(dateTimeProperty -> dateTimeProperty.lt(#{date:any(java.util.Date)}.toInstant().atOffset(ZoneOffset.UTC)))")),
        """
        #{camundaClient:any(io.camunda.client.CamundaClient)}
            .newUserTaskSearchRequest()
        """,
        """
            .send()
            .join()
            .items();
        """,
        "List<io.camunda.client.api.search.response.UserTask>",
        Collections.emptyList(),
        Map.ofEntries(Map.entry("dueBefore", "java.time.ZoneOffset"))));

    specs.add(new ReplacementUtils.BuilderReplacementSpec(
        new MethodMatcher("org.camunda.bpm.engine.task.Task getTaskDefinitionKey()"),
        Set.of("taskId"),
        List.of("taskId"),
        RecipeUtils.createSimpleJavaTemplate(
            """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newUserTaskGetRequest(Long.parseLong(#{taskId:any(java.lang.String)}))
                .send()
                .join()
                .getElementId()
            """),
        RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
        "java.lang.String",
        ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList()));
        
    return specs;
  }

  /*
  static final MethodMatcher listMethodMatcher =
      new MethodMatcher("org.camunda.bpm.engine.query.Query list()");

  static final MethodMatcher unlimitedListMethodMatcher =
      new MethodMatcher("org.camunda.bpm.engine.query.Query unlimitedList()");

  static final MethodMatcher singleResultMethodMatcher =
      new MethodMatcher("org.camunda.bpm.engine.query.Query singleResult()");

  @Override
  protected List<ReplacementUtils.BuilderReplacementSpec> builderMethodInvocations() {
    return List.of(
        new ReplacementUtils.BuilderReplacementSpec(
            listMethodMatcher,
            Set.of("processDefinitionKey"),
            List.of("processDefinitionKey"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newUserTaskSearchRequest()
                    .filter(userTaskFilter -> userTaskFilter.bpmnProcessId(#{processDefinitionKey:any(java.lang.String)}))
                    .send()
                    .join()
                    .items();
                """,
                "io.camunda.client.api.search.response.UserTask"),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "List<io.camunda.client.api.search.response.UserTask>",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            Collections.emptyList()),
        new ReplacementUtils.BuilderReplacementSpec(
            listMethodMatcher,
            Set.of("processDefinitionKey", "dueBefore"),
            List.of("processDefinitionKey", "dueBefore"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                        #{camundaClient:any(io.camunda.client.CamundaClient)}
                            .newUserTaskSearchRequest()
                            .filter(userTaskFilter -> userTaskFilter.bpmnProcessId(#{processDefinitionKey:any(java.lang.String)})
                                    .dueDate(dateTimeProperty -> dateTimeProperty.lt(#{date:any(java.util.Date)}.toInstant().atOffset(ZoneOffset.UTC))))
                            .send()
                            .join()
                            .items();
                        """,
                "io.camunda.client.api.search.response.UserTask",
                "java.time.ZoneOffset"),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "List<io.camunda.client.api.search.response.UserTask>",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            Collections.emptyList(),
            Collections.emptyList(),
            List.of("java.time.ZoneOffset")));
  }
*/

  @Override
  protected List<ReplacementUtils.ReturnReplacementSpec> returnMethodInvocations() {
    return List.of(
        new ReplacementUtils.ReturnReplacementSpec(
            new MethodMatcher("org.camunda.bpm.engine.task.Task getName()"),
            RecipeUtils.createSimpleJavaTemplate("#{any()}.getName()")),
        new ReplacementUtils.ReturnReplacementSpec(
            new MethodMatcher("org.camunda.bpm.engine.task.Task getProcessInstanceId()"),
            RecipeUtils.createSimpleJavaTemplate(
                "String.valueOf(#{any()}.getProcessInstanceKey())")),
        new ReplacementUtils.ReturnReplacementSpec(
            new MethodMatcher("org.camunda.bpm.engine.task.Task getTenantId()"),
            RecipeUtils.createSimpleJavaTemplate("#{any()}.getTenantId()")),
        new ReplacementUtils.ReturnReplacementSpec(
            new MethodMatcher("org.camunda.bpm.engine.task.Task getId()"),
            RecipeUtils.createSimpleJavaTemplate("String.valueOf(#{any()}.getUserTaskKey())")),
        new ReplacementUtils.ReturnReplacementSpec(
            new MethodMatcher("org.camunda.bpm.engine.task.Task getAssignee()"),
            RecipeUtils.createSimpleJavaTemplate("#{any()}.getAssignee()")),
        new ReplacementUtils.ReturnReplacementSpec(
            new MethodMatcher("org.camunda.bpm.engine.task.Task getDueDate()"),
            RecipeUtils.createSimpleJavaTemplate(
                "Date.from(#{any()}.getDueDate().toInstant())",
                "java.util.Date"),
            Collections.emptyList(),
            List.of("java.util.Date")));
  }

  @Override
  protected List<ReplacementUtils.RenameReplacementSpec> renameMethodInvocations() {
    return Collections.emptyList();
  }
}
