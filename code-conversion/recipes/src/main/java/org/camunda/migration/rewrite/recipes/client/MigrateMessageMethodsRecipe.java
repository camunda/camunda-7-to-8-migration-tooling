package org.camunda.migration.rewrite.recipes.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.camunda.migration.rewrite.recipes.sharedRecipes.AbstractMigrationRecipe;
import org.camunda.migration.rewrite.recipes.utils.BuilderSpecFactory;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.camunda.migration.rewrite.recipes.utils.ReplacementUtils;
import org.openrewrite.*;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;

public class MigrateMessageMethodsRecipe extends AbstractMigrationRecipe {

  @Override
  public String getDisplayName() {
    return "Convert message correlation methods";
  }

  @Override
  public String getDescription() {
    return "Replaces Camunda 7 message correlation methods with Camunda 8 client methods.";
  }

  @Override
  protected TreeVisitor<?, ExecutionContext> preconditions() {
    return Preconditions.or(
        new UsesMethod<>("org.camunda.bpm.engine.RuntimeService correlateMessage(..)", true),
        new UsesMethod<>("org.camunda.bpm.engine.RuntimeService messageEventReceived(..)", true),
        new UsesMethod<>(
            "org.camunda.bpm.engine.RuntimeService createMessageCorrelation(java.lang.String)",
            true));
  }

  @Override
  protected List<ReplacementUtils.SimpleReplacementSpec> simpleMethodInvocations() {
    return List.of(
        new ReplacementUtils.SimpleReplacementSpec(
            // "messageEventReceived(String messageName, String executionId)"
            new MethodMatcher(
                "org.camunda.bpm.engine.RuntimeService messageEventReceived(java.lang.String, java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCorrelateMessageCommand()
                    .messageName(#{messageName:any(java.lang.String)})
                    .correlationKey("add correlationKey here")
                    .send()
                    .join();
                """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.CorrelateMessageResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("messageName", 0)),
            List.of(
                " executionId was removed",
                " Hint: In Camunda 8 messages could also be correlated asynchronously")),
        new ReplacementUtils.SimpleReplacementSpec(
            // "messageEventReceived(String messageName, String executionId, Map<String, Objects>
            // variables)"
            new MethodMatcher(
                "org.camunda.bpm.engine.RuntimeService messageEventReceived(java.lang.String, java.lang.String, java.util.Map)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCorrelateMessageCommand()
                    .messageName(#{messageName:any(java.lang.String)})
                    .correlationKey("add correlationKey here")
                    .variables(#{variables:any(java.util.Map)})
                    .send()
                    .join();
                """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.CorrelateMessageResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("messageName", 0),
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("variables", 2)),
            List.of(
                " executionId was removed",
                " Hint: In Camunda 8 messages could also be correlated asynchronously")),
        new ReplacementUtils.SimpleReplacementSpec(
            // "correlateMessage(String messageName)"
            new MethodMatcher(
                "org.camunda.bpm.engine.RuntimeService correlateMessage(java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCorrelateMessageCommand()
                    .messageName(#{messageName:any(java.lang.String)})
                    .correlationKey("add correlationKey here")
                    .send()
                    .join();
                """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.CorrelateMessageResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("messageName", 0)),
            List.of(" Hint: In Camunda 8 messages could also be correlated asynchronously")),
        new ReplacementUtils.SimpleReplacementSpec(
            // "correlateMessage(String messageName, String businessKey)"
            new MethodMatcher(
                "org.camunda.bpm.engine.RuntimeService correlateMessage(java.lang.String, java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCorrelateMessageCommand()
                    .messageName(#{messageName:any(java.lang.String)})
                    .correlationKey("add correlationKey here")
                    .send()
                    .join();
                """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.CorrelateMessageResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("messageName", 0)),
            List.of(
                " processInstanceBusinessKey was removed",
                " Hint: In Camunda 8 messages could also be correlated asynchronously")),
        new ReplacementUtils.SimpleReplacementSpec(
            // "correlateMessage(String messageName, Map<String, Object> correlationKeys)"
            new MethodMatcher(
                "org.camunda.bpm.engine.RuntimeService correlateMessage(java.lang.String, java.util.Map)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCorrelateMessageCommand()
                    .messageName(#{messageName:any(java.lang.String)})
                    .correlationKey("add correlationKey here")
                    .send()
                    .join();
                """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.CorrelateMessageResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("messageName", 0)),
            List.of(
                " correlationKeys were removed",
                " Hint: In Camunda 8 messages could also be correlated asynchronously")),
        new ReplacementUtils.SimpleReplacementSpec(
            // "correlateMessage(String messageName, Map<String, Object> correlationKeys,
            // Map<String, Object> variables)"
            new MethodMatcher(
                "org.camunda.bpm.engine.RuntimeService correlateMessage(java.lang.String, java.util.Map, java.util.Map)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCorrelateMessageCommand()
                    .messageName(#{messageName:any(java.lang.String)})
                    .correlationKey("add correlationKey here")
                    .variables(#{variables:any(java.util.Map)})
                    .send()
                    .join();
                """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.CorrelateMessageResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("messageName", 0),
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("variables", 2)),
            List.of(
                " correlationKeys were removed",
                " Hint: In Camunda 8 messages could also be correlated asynchronously")),
        new ReplacementUtils.SimpleReplacementSpec(
            // "correlateMessage(String messageName, String businessKey, Map<String, Object>
            // correlationKeys,
            // Map<String, Object> variables)"
            new MethodMatcher(
                "org.camunda.bpm.engine.RuntimeService correlateMessage(java.lang.String, java.lang.String, java.util.Map, java.util.Map)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCorrelateMessageCommand()
                    .messageName(#{messageName:any(java.lang.String)})
                    .correlationKey("add correlationKey here")
                    .variables(#{variables:any(java.util.Map)})
                    .send()
                    .join();
                """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.CorrelateMessageResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("messageName", 0),
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("variables", 3)),
            List.of(
                " processInstanceBusinessKey was removed",
                " correlationKeys were removed",
                " Hint: In Camunda 8 messages could also be correlated asynchronously")),
        new ReplacementUtils.SimpleReplacementSpec(
            // "messageEventReceived(String messageName, String executionId)"
            new MethodMatcher(
                "org.camunda.bpm.engine.RuntimeService correlateMessage(java.lang.String, java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCorrelateMessageCommand()
                    .messageName(#{messageName:any(java.lang.String)})
                    .correlationKey("add correlationKey here")
                    .send()
                    .join();
                """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.CorrelateMessageResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("messageName", 0)),
            List.of(
                " executionId was removed",
                " Hint: In Camunda 8 messages could also be correlated asynchronously")),
        new ReplacementUtils.SimpleReplacementSpec(
            // "messageEventReceived(String messageName, String executionId, Map<String, Objects>
            // variables)"
            new MethodMatcher(
                "org.camunda.bpm.engine.RuntimeService correlateMessage(java.lang.String, java.lang.String, java.util.Map)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCorrelateMessageCommand()
                    .messageName(#{messageName:any(java.lang.String)})
                    .correlationKey("add correlationKey here")
                    .variables(#{variables:any(java.util.Map)})
                    .send()
                    .join();
                """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.CorrelateMessageResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("messageName", 0),
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("variables", 2)),
            List.of(
                " executionId was removed",
                " Hint: In Camunda 8 messages could also be correlated asynchronously")));
  }

  @Override
  protected List<ReplacementUtils.BuilderReplacementSpec> builderMethodInvocations() {
    return BuilderSpecFactory.createBuilderSpecs(
        "org.camunda.bpm.engine.runtime.MessageCorrelationBuilder correlate()",
        "createMessageCorrelation",
        List.of("processInstanceBusinessKey", "setVariables", "tenantId"),
        Map.ofEntries(
            Map.entry(
                "createMessageCorrelation", ".messageName(#{messageName:any(java.lang.String)})"),
            Map.entry("setVariables", ".variables(#{variables:any(java.util.Map)})"),
            Map.entry("tenantId", ".tenantId(#{tenantId:any(java.lang.String)})")),
        """
              #{camundaClient:any(io.camunda.client.CamundaClient)}
                  .newCorrelateMessageCommand()
              """,
        ".correlationKey(\"add correlationKey here\")",
        """
                  .send()
                  .join();
              """,
        "io.camunda.client.api.response.CorrelateMessageResponse",
        List.of(" Hint: In Camunda 8 messages could also be correlated asynchronously"));
  }

  /*static final MethodMatcher correlateMethodMatcher =
          new MethodMatcher("org.camunda.bpm.engine.runtime.MessageCorrelationBuilder correlate()");

  List<ReplacementUtils.BuilderReplacementSpec> parkedList =
      List.of(
          new ReplacementUtils.BuilderReplacementSpec(
              correlateMethodMatcher,
              Set.of("createMessageCorrelation"),
              List.of("createMessageCorrelation"),
              RecipeUtils.createSimpleJavaTemplate(
                  """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCorrelateMessageCommand()
                    .messageName(#{messageName:any(java.lang.String)})
                    .correlationKey("add correlationKey here")
                    .send()
                    .join();
                """),
              RecipeUtils.createSimpleIdentifier(
                  "camundaClient", "io.camunda.client.CamundaClient"),
              "io.camunda.client.api.response.CorrelateMessageResponse",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              List.of(" Hint: In Camunda 8 messages could also be correlated asynchronously")),
          new ReplacementUtils.BuilderReplacementSpec(
              correlateMethodMatcher,
              Set.of("createMessageCorrelation", "processInstanceBusinessKey"),
              List.of("createMessageCorrelation"),
              RecipeUtils.createSimpleJavaTemplate(
                  """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCorrelateMessageCommand()
                    .messageName(#{messageName:any(java.lang.String)})
                    .correlationKey("add correlationKey here")
                    .send()
                    .join();
                """),
              RecipeUtils.createSimpleIdentifier(
                  "camundaClient", "io.camunda.client.CamundaClient"),
              "io.camunda.client.api.response.CorrelateMessageResponse",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              List.of(
                  " businessKey was removed",
                  " Hint: In Camunda 8 messages could also be correlated asynchronously")),
          new ReplacementUtils.BuilderReplacementSpec(
              correlateMethodMatcher,
              Set.of("createMessageCorrelation", "tenantId"),
              List.of("createMessageCorrelation", "tenantId"),
              RecipeUtils.createSimpleJavaTemplate(
                  """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCorrelateMessageCommand()
                    .messageName(#{messageName:any(java.lang.String)})
                    .correlationKey("add correlationKey here")
                    .tenantId(#{tenantId:any(java.lang.String)})
                    .send()
                    .join();
                """),
              RecipeUtils.createSimpleIdentifier(
                  "camundaClient", "io.camunda.client.CamundaClient"),
              "io.camunda.client.api.response.CorrelateMessageResponse",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              List.of(" Hint: In Camunda 8 messages could also be correlated asynchronously")),
          new ReplacementUtils.BuilderReplacementSpec(
              correlateMethodMatcher,
              Set.of("createMessageCorrelation", "tenantId", "processInstanceBusinessKey"),
              List.of("createMessageCorrelation", "tenantId"),
              RecipeUtils.createSimpleJavaTemplate(
                  """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCorrelateMessageCommand()
                    .messageName(#{messageName:any(java.lang.String)})
                    .correlationKey("add correlationKey here")
                    .tenantId(#{tenantId:any(java.lang.String)})
                    .send()
                    .join();
                """),
              RecipeUtils.createSimpleIdentifier(
                  "camundaClient", "io.camunda.client.CamundaClient"),
              "io.camunda.client.api.response.CorrelateMessageResponse",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              List.of(
                  " businessKey was removed",
                  " Hint: In Camunda 8 messages could also be correlated asynchronously")),
          new ReplacementUtils.BuilderReplacementSpec(
              correlateMethodMatcher,
              Set.of("createMessageCorrelation", "setVariables"),
              List.of("createMessageCorrelation", "setVariables"),
              RecipeUtils.createSimpleJavaTemplate(
                  """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCorrelateMessageCommand()
                    .messageName(#{messageName:any(java.lang.String)})
                    .correlationKey("add correlationKey here")
                    .variables(#{variables:any(java.util.Map)})
                    .send()
                    .join();
                """),
              RecipeUtils.createSimpleIdentifier(
                  "camundaClient", "io.camunda.client.CamundaClient"),
              "io.camunda.client.api.response.CorrelateMessageResponse",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              List.of(" Hint: In Camunda 8 messages could also be correlated asynchronously")),
          new ReplacementUtils.BuilderReplacementSpec(
              correlateMethodMatcher,
              Set.of("createMessageCorrelation", "setVariables", "processInstanceBusinessKey"),
              List.of("createMessageCorrelation", "setVariables"),
              RecipeUtils.createSimpleJavaTemplate(
                  """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCorrelateMessageCommand()
                    .messageName(#{messageName:any(java.lang.String)})
                    .correlationKey("add correlationKey here")
                    .variables(#{variables:any(java.util.Map)})
                    .send()
                    .join();
                """),
              RecipeUtils.createSimpleIdentifier(
                  "camundaClient", "io.camunda.client.CamundaClient"),
              "io.camunda.client.api.response.CorrelateMessageResponse",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              List.of(
                  " businessKey was removed",
                  " Hint: In Camunda 8 messages could also be correlated asynchronously")),
          new ReplacementUtils.BuilderReplacementSpec(
              correlateMethodMatcher,
              Set.of("createMessageCorrelation", "setVariables", "tenantId"),
              List.of("createMessageCorrelation", "setVariables", "tenantId"),
              RecipeUtils.createSimpleJavaTemplate(
                  """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCorrelateMessageCommand()
                    .messageName(#{messageName:any(java.lang.String)})
                    .correlationKey("add correlationKey here")
                    .variables(#{variables:any(java.util.Map)})
                    .tenantId(#{tenantId:any(java.lang.String)})
                    .send()
                    .join();
                """),
              RecipeUtils.createSimpleIdentifier(
                  "camundaClient", "io.camunda.client.CamundaClient"),
              "io.camunda.client.api.response.CorrelateMessageResponse",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              List.of(" Hint: In Camunda 8 messages could also be correlated asynchronously")),
          new ReplacementUtils.BuilderReplacementSpec(
              correlateMethodMatcher,
              Set.of(
                  "createMessageCorrelation",
                  "setVariables",
                  "tenantId",
                  "processInstanceBusinessKey"),
              List.of("createMessageCorrelation", "setVariables", "tenantId"),
              RecipeUtils.createSimpleJavaTemplate(
                  """
                        #{camundaClient:any(io.camunda.client.CamundaClient)}
                            .newCorrelateMessageCommand()
                            .messageName(#{messageName:any(java.lang.String)})
                            .correlationKey("add correlationKey here")
                            .variables(#{variables:any(java.util.Map)})
                            .tenantId(#{tenantId:any(java.lang.String)})
                            .send()
                            .join();
                        """),
              RecipeUtils.createSimpleIdentifier(
                  "camundaClient", "io.camunda.client.CamundaClient"),
              "io.camunda.client.api.response.CorrelateMessageResponse",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              List.of(
                  " businessKey was removed",
                  " Hint: In Camunda 8 messages could also be correlated asynchronously")));*/

  @Override
  protected List<ReplacementUtils.ReturnReplacementSpec> returnMethodInvocations() {
    return Collections.emptyList();
  }

  @Override
  protected List<ReplacementUtils.RenameReplacementSpec> renameMethodInvocations() {
    return Collections.emptyList();
  }
}
