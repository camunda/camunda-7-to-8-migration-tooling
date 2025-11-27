package org.camunda.migration.rewrite.recipes.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.camunda.migration.rewrite.recipes.sharedRecipes.AbstractMigrationRecipe;
import org.camunda.migration.rewrite.recipes.utils.BuilderSpecFactory;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.camunda.migration.rewrite.recipes.utils.ReplacementUtils;
import org.openrewrite.*;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;

public class MigrateSignalMethodsRecipe extends AbstractMigrationRecipe {

  @Override
  public String getDisplayName() {
    return "Convert signal broadcasting methods";
  }

  @Override
  public String getDescription() {
    return "Replaces Camunda 7 signal broadcasting methods with Camunda 8 client methods.";
  }

  @Override
  protected TreeVisitor<?, ExecutionContext> preconditions() {
    return Preconditions.or(
        new UsesMethod<>("org.camunda.bpm.engine.RuntimeService signalEventReceived(..)", true),
        new UsesMethod<>(
            "org.camunda.bpm.engine.RuntimeService createSignalEvent(java.lang.String)", true));
  }

  @Override
  protected List<ReplacementUtils.SimpleReplacementSpec> simpleMethodInvocations() {
    return List.of(
        new ReplacementUtils.SimpleReplacementSpec(
            // "signalEventReceived(String signalName)"
            new MethodMatcher(
                "org.camunda.bpm.engine.RuntimeService signalEventReceived(java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                    #{camundaClient:any(io.camunda.client.CamundaClient)}
                                        .newBroadcastSignalCommand()
                                        .signalName(#{signalName:any(String)})
                                        .send()
                                        .join();
                                    """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.BroadcastSignalResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("signalName", 0)),
            Collections.emptyList()),
        new ReplacementUtils.SimpleReplacementSpec(
            // "signalEventReceived(String signalName, String executionId)"
            new MethodMatcher(
                "org.camunda.bpm.engine.RuntimeService signalEventReceived(java.lang.String, java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                    #{camundaClient:any(io.camunda.client.CamundaClient)}
                                        .newBroadcastSignalCommand()
                                        .signalName(#{signalName:any(String)})
                                        .send()
                                        .join();
                                    """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.BroadcastSignalResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("signalName", 0)),
            List.of(" executionId was removed")),
        new ReplacementUtils.SimpleReplacementSpec(
            // "signalEventReceived(String signalName, Map<String, Object> variableMap)"
            new MethodMatcher(
                "org.camunda.bpm.engine.RuntimeService signalEventReceived(java.lang.String, java.util.Map)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                        #{camundaClient:any(io.camunda.client.CamundaClient)}
                            .newBroadcastSignalCommand()
                            .signalName(#{signalName:any(String)})
                            .variables(#{variableMap:any(java.util.Map)})
                            .send()
                            .join();
                        """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.BroadcastSignalResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("signalName", 0),
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("variableMap", 1)),
            Collections.emptyList()),
        new ReplacementUtils.SimpleReplacementSpec(
            // "signalEventReceived(String signalName, String executionId, Map<String, Object>
            // variableMap)"
            new MethodMatcher(
                "org.camunda.bpm.engine.RuntimeService signalEventReceived(java.lang.String, java.lang.String, java.util.Map)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newBroadcastSignalCommand()
                                    .signalName(#{signalName:any(String)})
                                    .variables(#{variableMap:any(java.util.Map)})
                                    .send()
                                    .join();
                                """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            "io.camunda.client.api.response.BroadcastSignalResponse",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("signalName", 0),
                new ReplacementUtils.SimpleReplacementSpec.NamedArg("variableMap", 2)),
            List.of(" executionId was removed")));
  }

  @Override
  protected List<ReplacementUtils.BuilderReplacementSpec> builderMethodInvocations() {
    return BuilderSpecFactory.createBuilderSpecs(
        "org.camunda.bpm.engine.runtime.SignalEventReceivedBuilder send()",
        "createSignalEvent",
        List.of("executionId", "setVariables", "tenantId"),
        Map.ofEntries(
            Map.entry(
                "createSignalEvent", ".signalName(#{signalName:any(java.lang.String)})"),
            Map.entry("setVariables", ".variables(#{variables:any(java.util.Map)})"),
            Map.entry("tenantId", ".tenantId(#{tenantId:any(java.lang.String)})")),
        """
              #{camundaClient:any(io.camunda.client.CamundaClient)}
                  .newBroadcastSignalCommand()
              """,
        "",
        """
                  .send()
                  .join();
              """,
        "io.camunda.client.api.response.BroadcastSignalResponse",
        Collections.emptyList());
  }

/*  static final MethodMatcher executeMethodMatcher =
      new MethodMatcher("org.camunda.bpm.engine.runtime.SignalEventReceivedBuilder send()");

  List<ReplacementUtils.BuilderReplacementSpec> parkedList =
      List.of(
          new ReplacementUtils.BuilderReplacementSpec(
              executeMethodMatcher,
              Set.of("createSignalEvent"),
              List.of("createSignalEvent"),
              RecipeUtils.createSimpleJavaTemplate(
                  """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newBroadcastSignalCommand()
                                    .signalName(#{signalName:any(String)})
                                    .send()
                                    .join();
                                """),
              RecipeUtils.createSimpleIdentifier(
                  "camundaClient", "io.camunda.client.CamundaClient"),
              "io.camunda.client.api.response.BroadcastSignalResponse",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              Collections.emptyList()),
          new ReplacementUtils.BuilderReplacementSpec(
              executeMethodMatcher,
              Set.of("createSignalEvent", "executionId"),
              List.of("createSignalEvent"),
              RecipeUtils.createSimpleJavaTemplate(
                  """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newBroadcastSignalCommand()
                                    .signalName(#{signalName:any(String)})
                                    .send()
                                    .join();
                                """),
              RecipeUtils.createSimpleIdentifier(
                  "camundaClient", "io.camunda.client.CamundaClient"),
              "io.camunda.client.api.response.BroadcastSignalResponse",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              List.of(" executionId was removed")),
          new ReplacementUtils.BuilderReplacementSpec(
              executeMethodMatcher,
              Set.of("createSignalEvent", "tenantId"),
              List.of("createSignalEvent", "tenantId"),
              RecipeUtils.createSimpleJavaTemplate(
                  """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newBroadcastSignalCommand()
                                    .signalName(#{signalName:any(String)})
                                    .tenantId(#{tenantId:any(String)})
                                    .send()
                                    .join();
                                """),
              RecipeUtils.createSimpleIdentifier(
                  "camundaClient", "io.camunda.client.CamundaClient"),
              "io.camunda.client.api.response.BroadcastSignalResponse",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              Collections.emptyList()),
          new ReplacementUtils.BuilderReplacementSpec(
              executeMethodMatcher,
              Set.of("createSignalEvent", "executionId", "tenantId"),
              List.of("createSignalEvent", "tenantId"),
              RecipeUtils.createSimpleJavaTemplate(
                  """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newBroadcastSignalCommand()
                                    .signalName(#{signalName:any(String)})
                                    .tenantId(#{tenantId:any(String)})
                                    .send()
                                    .join();
                                """),
              RecipeUtils.createSimpleIdentifier(
                  "camundaClient", "io.camunda.client.CamundaClient"),
              "io.camunda.client.api.response.BroadcastSignalResponse",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              List.of(" executionId was removed")),
          new ReplacementUtils.BuilderReplacementSpec(
              executeMethodMatcher,
              Set.of("createSignalEvent", "setVariables"),
              List.of("createSignalEvent", "setVariables"),
              RecipeUtils.createSimpleJavaTemplate(
                  """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newBroadcastSignalCommand()
                                    .signalName(#{signalName:any(String)})
                                    .variables(#{variableMap:any(java.util.Map)})
                                    .send()
                                    .join();
                                """),
              RecipeUtils.createSimpleIdentifier(
                  "camundaClient", "io.camunda.client.CamundaClient"),
              "io.camunda.client.api.response.BroadcastSignalResponse",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              Collections.emptyList()),
          new ReplacementUtils.BuilderReplacementSpec(
              executeMethodMatcher,
              Set.of("createSignalEvent", "executionId", "setVariables"),
              List.of("createSignalEvent", "setVariables"),
              RecipeUtils.createSimpleJavaTemplate(
                  """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newBroadcastSignalCommand()
                                    .signalName(#{signalName:any(String)})
                                    .variables(#{variableMap:any(java.util.Map)})
                                    .send()
                                    .join();
                                """),
              RecipeUtils.createSimpleIdentifier(
                  "camundaClient", "io.camunda.client.CamundaClient"),
              "io.camunda.client.api.response.BroadcastSignalResponse",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              List.of(" executionId was removed")),
          new ReplacementUtils.BuilderReplacementSpec(
              executeMethodMatcher,
              Set.of("createSignalEvent", "tenantId", "setVariables"),
              List.of("createSignalEvent", "tenantId", "setVariables"),
              RecipeUtils.createSimpleJavaTemplate(
                  """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newBroadcastSignalCommand()
                                    .signalName(#{signalName:any(String)})
                                    .tenantId(#{tenantId:any(String)})
                                    .variables(#{variableMap:any(java.util.Map)})
                                    .send()
                                    .join();
                                """),
              RecipeUtils.createSimpleIdentifier(
                  "camundaClient", "io.camunda.client.CamundaClient"),
              "io.camunda.client.api.response.BroadcastSignalResponse",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              Collections.emptyList()),
          new ReplacementUtils.BuilderReplacementSpec(
              executeMethodMatcher,
              Set.of("createSignalEvent", "executionId", "tenantId", "setVariables"),
              List.of("createSignalEvent", "tenantId", "setVariables"),
              RecipeUtils.createSimpleJavaTemplate(
                  """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newBroadcastSignalCommand()
                                    .signalName(#{signalName:any(String)})
                                    .tenantId(#{tenantId:any(String)})
                                    .variables(#{variableMap:any(java.util.Map)})
                                    .send()
                                    .join();
                                """),
              RecipeUtils.createSimpleIdentifier(
                  "camundaClient", "io.camunda.client.CamundaClient"),
              "io.camunda.client.api.response.BroadcastSignalResponse",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              List.of(" executionId was removed"))); */

  @Override
  protected List<ReplacementUtils.ReturnReplacementSpec> returnMethodInvocations() {
    return Collections.emptyList();
  }

  @Override
  protected List<ReplacementUtils.RenameReplacementSpec> renameMethodInvocations() {
    return Collections.emptyList();
  }
}
