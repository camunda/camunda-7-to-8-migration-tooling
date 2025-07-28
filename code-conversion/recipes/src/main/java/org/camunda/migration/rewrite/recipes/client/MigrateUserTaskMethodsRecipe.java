package org.camunda.migration.rewrite.recipes.client;

import java.util.*;
import org.camunda.migration.rewrite.recipes.sharedRecipes.AbstractMigrationRecipe;
import org.camunda.migration.rewrite.recipes.utils.BuilderSpecFactory;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.camunda.migration.rewrite.recipes.utils.ReplacementUtils;
import org.openrewrite.*;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;

public class MigrateUserTaskMethodsRecipe extends AbstractMigrationRecipe {

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
        new UsesMethod<>("org.camunda.bpm.engine.RuntimeService claim(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.RuntimeService complete(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.RuntimeService getVariable(..)", true));
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
                    .newUserTaskAssignCommand(Long.valueOf(#{taskId:any(java.lang.String)}))
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
            // "complete(String taskId)"
            new MethodMatcher("org.camunda.bpm.engine.TaskService complete(java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newUserTaskCompleteCommand(Long.valueOf(#{taskId:any(java.lang.String)}))
                    .send()
                    .join();
                """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.CompleteUserTaskResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("taskId", 0)),
            Collections.emptyList()),
        new ReplacementUtils.SimpleReplacementSpec(
            // "complete(String taskId, Map<String, Object> variables)"
            new MethodMatcher(
                "org.camunda.bpm.engine.TaskService complete(java.lang.String, java.util.Map)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newUserTaskCompleteCommand(Long.valueOf(#{taskId:any(java.lang.String)}))
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
            Collections.emptyList()),
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
    return BuilderSpecFactory.createBuilderFilterSpecs(
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
        Map.ofEntries(Map.entry("dueBefore", "java.time.ZoneOffset")));
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
                "Date.from(Instant.parse((#{any()}.getDueDate()))",
                "java.util.Date",
                "java.time.Instant"),
            Collections.emptyList(),
            List.of("java.util.Date", "java.time.Instant")));
  }

  @Override
  protected List<ReplacementUtils.RenameReplacementSpec> renameMethodInvocations() {
    return Collections.emptyList();
  }
}
