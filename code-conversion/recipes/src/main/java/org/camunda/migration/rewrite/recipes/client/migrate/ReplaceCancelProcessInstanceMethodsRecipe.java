package org.camunda.migration.rewrite.recipes.client.migrate;

import java.util.Collections;
import java.util.List;
import org.camunda.migration.rewrite.recipes.sharedRecipes.AbstractMigrationRecipe;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.openrewrite.*;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;

public class ReplaceCancelProcessInstanceMethodsRecipe extends AbstractMigrationRecipe {

  /** Instantiates a new instance. */
  public ReplaceCancelProcessInstanceMethodsRecipe() {}

  @Override
  public String getDisplayName() {
    return "Convert cancel process instance methods";
  }

  @Override
  public String getDescription() {
    return "Replaces Camunda 7 cancel process instance methods with Camunda 8 client.";
  }

  @Override
  protected TreeVisitor<?, ExecutionContext> preconditions() {
    return new UsesMethod<>(
        "org.camunda.bpm.engine.RuntimeService deleteProcessInstance(java.lang.String, java.lang.String)",
        true);
  }

  @Override
  protected List<RecipeUtils.MethodInvocationSimpleReplacementSpec> simpleMethodInvocations() {
    return List.of(
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            // "signalEventReceived(String signalName)"
            new MethodMatcher(
                "org.camunda.bpm.engine.RuntimeService deleteProcessInstance(java.lang.String, java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                #{camundaClient:any(io.camunda.client.CamundaClient)}
                    .newCancelInstanceCommand(Long.valueOf(#{processInstanceKey:any(String)}))
                    .send()
                    .join();
                """),
            RecipeUtils.createSimpleIdentifier("camundaClient", "io.camunda.client.CamundaClient"),
            null,
            RecipeUtils.ReturnTypeStrategy.VOID,
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg(
                    "processInstanceKey", 0)),
            List.of(" delete reason was removed")));
  }

  @Override
  protected List<RecipeUtils.MethodInvocationBuilderReplacementSpec> builderMethodInvocations() {
    return Collections.emptyList();
  }

  @Override
  protected List<RecipeUtils.MethodInvocationReturnReplacementSpec> returnMethodInvocations() {
    return Collections.emptyList();
  }
}
