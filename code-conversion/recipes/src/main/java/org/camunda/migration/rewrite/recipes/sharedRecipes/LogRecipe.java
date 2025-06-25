package org.camunda.migration.rewrite.recipes.sharedRecipes;

import java.util.List;
import java.util.stream.Collectors;
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
        return new JavaVisitor<ExecutionContext>() {

	        @Override
	        public J visitClassDeclaration(J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
                J updated = (J) super.visitClassDeclaration(classDeclaration, ctx);

                System.out.println("=== After visitClassDeclaration ===");
                System.out.println(updated.print());

                return updated;
	        }
        };
    }
}
