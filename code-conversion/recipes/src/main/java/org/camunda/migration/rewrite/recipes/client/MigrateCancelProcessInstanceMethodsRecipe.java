package org.camunda.migration.rewrite.recipes.client;

import java.util.Collections;
import java.util.List;
import org.camunda.migration.rewrite.recipes.sharedRecipes.AbstractMigrationRecipe;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.camunda.migration.rewrite.recipes.utils.ReplacementUtils;
import org.openrewrite.*;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;

public class MigrateCancelProcessInstanceMethodsRecipe extends AbstractMigrationRecipe {

  /** Instantiates a new instance. */
  public MigrateCancelProcessInstanceMethodsRecipe() {}

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
  protected List<ReplacementUtils.SimpleReplacementSpec> simpleMethodInvocations() {
    return List.of(
        new ReplacementUtils.SimpleReplacementSpec(
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
            ReplacementUtils.ReturnTypeStrategy.VOID,
            List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("processInstanceKey", 0)),
            List.of(" delete reason was removed")));
  }

  @Override
  protected List<ReplacementUtils.BuilderReplacementSpec> builderMethodInvocations() {
    return Collections.emptyList();
  }

  @Override
  protected List<ReplacementUtils.ReturnReplacementSpec> returnMethodInvocations() {
    return Collections.emptyList();
  }

  @Override
  protected List<ReplacementUtils.RenameReplacementSpec> renameMethodInvocations() {
    return Collections.emptyList();
  }
}
