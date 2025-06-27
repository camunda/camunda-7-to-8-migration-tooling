package org.camunda.migration.rewrite.recipes.client.cleanup;

import java.util.*;
import org.camunda.migration.rewrite.recipes.utils.RecipeConstants;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.*;
import org.openrewrite.java.tree.*;

public class RemoveEngineDependencyRecipe extends Recipe {
  /** Instantiates a new instance. */
  public RemoveEngineDependencyRecipe() {}

  @Override
  public String getDisplayName() {
    return "Remove process engine dependency";
  }

  @Override
  public String getDescription() {
    return "Removes process engine dependency. Tries to remove import.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        new UsesType<>(RecipeConstants.Type.PROCESS_ENGINE, true);

    return Preconditions.check(
        check,
        new JavaVisitor<ExecutionContext>() {

          /**
           * Removing an LST element cannot be done by visiting it directly. Visiting
           * J.ClassDeclaration targets the smallest LST element to filter out the dependencies to
           * be removed. The body of the class contains statements, which can be any type that is
           * allowed to be in a class body. All statements apart from the ones to be removed are
           * collected. If the statement is instanceof J.VariableDeclarations and defines variables
           * of the type to remove, it is not collected. The class declaration is returned with new
           * statements in its body.
           */
          @Override
          public J.ClassDeclaration visitClassDeclaration(
              J.ClassDeclaration classDecl, ExecutionContext ctx) {

            List<Statement> newStatements = new ArrayList<>();
            for (Statement statement : classDecl.getBody().getStatements()) {
              if (statement instanceof J.VariableDeclarations varDecls
                  && (TypeUtils.isOfClassType(
                          varDecls.getType(), RecipeConstants.Type.PROCESS_ENGINE)
                      || TypeUtils.isOfClassType(
                          varDecls.getType(), RecipeConstants.Type.RUNTIME_SERVICE)
                      || TypeUtils.isOfClassType(
                          varDecls.getType(), RecipeConstants.Type.TASK_SERVICE)
                      || TypeUtils.isOfClassType(
                          varDecls.getType(), RecipeConstants.Type.REPOSITORY_SERVICE))) {
                // This is the statement we want to remove, so skip adding it
                continue;
              }
              newStatements.add(statement);
            }

            maybeRemoveImport(RecipeConstants.Type.PROCESS_ENGINE);

            return classDecl.withBody(classDecl.getBody().withStatements(newStatements));
          }
        });
  }
}
