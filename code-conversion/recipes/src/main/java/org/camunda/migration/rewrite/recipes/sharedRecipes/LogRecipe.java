package org.camunda.migration.rewrite.recipes.sharedRecipes;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;

public class LogRecipe extends Recipe {

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
    return new JavaIsoVisitor<>() {

      @Override
      public J.ClassDeclaration visitClassDeclaration(
          J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
        J.ClassDeclaration updated = super.visitClassDeclaration(classDeclaration, ctx);

        System.out.println("=== After visitClassDeclaration ===");
        System.out.println(updated.print());

        return updated;
      }
    };
  }
}
