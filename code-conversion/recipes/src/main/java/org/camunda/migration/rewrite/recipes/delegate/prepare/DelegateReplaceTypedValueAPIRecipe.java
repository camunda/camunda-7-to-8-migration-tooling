package org.camunda.migration.rewrite.recipes.delegate.prepare;

import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

public class DelegateReplaceTypedValueAPIRecipe extends Recipe {

  /** Instantiates a new instance. */
  public DelegateReplaceTypedValueAPIRecipe() {}

  @Override
  public String getDisplayName() {
    return "Replaces delegate specific typed value methods";
  }

  @Override
  public String getDescription() {
    return "Replaces delegate specific typed value methods.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        Preconditions.or(
            new UsesMethod<ExecutionContext>(
                "org.camunda.bpm.engine.delegate.VariableScope getVariableTyped(..)"),
            new UsesMethod<ExecutionContext>(
                    "org.camunda.bpm.engine.delegate.VariableScope getVariableLocalTyped(..)"));

    return Preconditions.check(
        check,
        new JavaVisitor<ExecutionContext>() {

          @Override
          public J visitMethodInvocation(J.MethodInvocation invoc, ExecutionContext ctx) {

            if (invoc.getSimpleName().equals("getVariableTyped") || invoc.getSimpleName().equals("getVariableLocalTyped")) {
              J.Identifier newIdent =
                  RecipeUtils.createSimpleIdentifier("getVariable", "java.lang.String");
              return invoc.withName(newIdent);
            }

            return super.visitMethodInvocation(invoc, ctx);
          }
        });
  }
}
