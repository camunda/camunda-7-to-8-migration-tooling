package org.camunda.migration.rewrite.recipes.client.migrate;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.camunda.migration.rewrite.recipes.sharedRecipes.AbstractMigrationRecipe;
import org.camunda.migration.rewrite.recipes.utils.RecipeConstants;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.openrewrite.*;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;

public class ReplaceSignalMethodsRecipe extends AbstractMigrationRecipe {

  @Override
  public String getDisplayName() {
    return "Convert signal broadcasting methods";
  }

  @Override
  public String getDescription() {
    return "Replaces Camunda 7 signal broadcasting methods with Camunda 8 client wrapper.";
  }

  @Override
  protected TreeVisitor<?, ExecutionContext> preconditions() {
    return Preconditions.and(
        Preconditions.or(
            new UsesType<>(RecipeConstants.Type.PROCESS_ENGINE, true),
            new UsesType<>(RecipeConstants.Type.RUNTIME_SERVICE, true)),
        Preconditions.or(
            new UsesMethod<>(
                RecipeConstants.Method.SIGNAL_EVENT_RECEIVED + RecipeConstants.Parameters.ANY,
                true),
            new UsesMethod<>(RecipeConstants.Method.CREATE_SIGNAL_EVENT, true)));
  }

  @Override
  protected List<RecipeUtils.MethodInvocationSimpleReplacementSpec> simpleMethodInvocations() {
    return List.of(
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "signalEventReceived(String signalName)"
                "org.camunda.bpm.engine.RuntimeService signalEventReceived(java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                    #{camundaClient:any(io.camunda.client.CamundaClient)}
                                        .newBroadcastSignalCommand()
                                        .signalName(#{signalName:any(String)})
                                        .send()
                                        .join();
                                    """),
            null,
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("signalName", 0)),
            Collections.emptyList()),
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "signalEventReceived(String signalName, String executionId)"
                "org.camunda.bpm.engine.RuntimeService signalEventReceived(java.lang.String, java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                    #{camundaClient:any(io.camunda.client.CamundaClient)}
                                        .newBroadcastSignalCommand()
                                        .signalName(#{signalName:any(String)})
                                        .send()
                                        .join();
                                    """),
            null,
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("signalName", 0)),
            List.of(" executionId was removed")),
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "signalEventReceived(String signalName, Map<String, Object> variableMap)"
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
            null,
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("signalName", 0),
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("variableMap", 1)),
            Collections.emptyList()),
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "signalEventReceived(String signalName, String executionId, Map<String, Object>
                // variableMap)"
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
            null,
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("signalName", 0),
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("variableMap", 2)),
            List.of(" executionId was removed")));
  }

  static final MethodMatcher executeMethodMatcher =
      new MethodMatcher("org.camunda.bpm.engine.runtime.SignalEventReceivedBuilder send()");

  @Override
  protected List<RecipeUtils.MethodInvocationBuilderReplacementSpec> builderMethodInvocations() {
    return List.of(
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
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
            null,
            Collections.emptyList()),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
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
            null,
            List.of(" executionId was removed")),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
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
            null,
            Collections.emptyList()),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
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
            null,
            List.of(" executionId was removed")),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
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
            null,
            Collections.emptyList()),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
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
            null,
            List.of(" executionId was removed")),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
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
            null,
            Collections.emptyList()),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
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
            null,
            List.of(" executionId was removed")));
  }

  @Override
  protected List<RecipeUtils.MethodInvocationReturnReplacementSpec> returnMethodInvocations() {
    return Collections.emptyList();
  }
}
