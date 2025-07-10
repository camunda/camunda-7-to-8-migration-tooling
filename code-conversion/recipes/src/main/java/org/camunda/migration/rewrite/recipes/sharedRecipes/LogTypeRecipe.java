package org.camunda.migration.rewrite.recipes.sharedRecipes;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

public class LogTypeRecipe extends Recipe {

  @Override
  public String getDisplayName() {
    return "Remove some imports manually";
  }

  @Override
  public String getDescription() {
    return "The remove unneeded imports recipe does not work properly in some cases. This recipe removes specified imports by force.";
  }

  @Override
  public JavaVisitor<ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<ExecutionContext>() {

      @Override
      public Expression visitExpression(Expression expression, ExecutionContext ctx) {
        JavaType type = expression.getType();
        if (TypeUtils.isOfType(type, JavaType.buildType("org.camunda.bpm.engine.runtime.ProcessInstance"))) {
          System.out.println("--- ⚠️ Found lingering reference ---");
          System.out.println("Expression: " + expression);
          System.out.println("Type: " + type);
          System.out.println("Type class: " + type.getClass().getName());
          System.out.println("AST class: " + expression.getClass().getSimpleName());

          if (expression instanceof J.Identifier) {
            System.out.println("Identifier name: " + ((J.Identifier) expression).getSimpleName());
          }
        }
        return super.visitExpression(expression, ctx);
      }
    };
  }
}
