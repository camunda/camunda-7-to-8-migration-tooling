package org.camunda.migration.rewrite.recipes.delegate.cleanup;

import java.util.List;
import java.util.stream.Collectors;
import org.camunda.migration.rewrite.recipes.utils.RecipeConstants;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.jgit.annotations.NonNull;

public class RemoveDelegateRecipe extends Recipe {

  /** Instantiates a new instance. */
  public RemoveDelegateRecipe() {}

  @Override
  public String getDisplayName() {
    return "Removes delegate-related code";
  }

  @Override
  public String getDescription() {
    return "Removes delegate-related code.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        new UsesType<>(RecipeConstants.Type.JAVA_DELEGATE, true);

    return Preconditions.check(
        check,
        new JavaIsoVisitor<ExecutionContext>() {

          @Override
          @NonNull
          public J.ClassDeclaration visitClassDeclaration(
              @NonNull J.ClassDeclaration classDecl, ExecutionContext ctx) {

            // Skip interfaces
            if (classDecl.getKind() != J.ClassDeclaration.Kind.Type.Class) {
              return classDecl;
            }

            // Filter out the interface to remove
            List<TypeTree> updatedImplements =
                classDecl.getImplements().stream()
                    .filter(
                        id ->
                            !TypeUtils.isOfClassType(
                                id.getType(), RecipeConstants.Type.JAVA_DELEGATE))
                    .collect(Collectors.toList());

            List<Statement> filteredStatements =
                classDecl.getBody().getStatements().stream()
                    .filter(
                        (statement ->
                            !(statement instanceof J.MethodDeclaration methDecl
                                && methDecl.getSimpleName().equals("execute"))))
                    .toList();

            maybeRemoveImport(RecipeConstants.Type.JAVA_DELEGATE);
            maybeRemoveImport(RecipeConstants.Type.DELEGATE_EXECUTION);

            return classDecl
                .withBody(classDecl.getBody().withStatements(filteredStatements))
                .withImplements(updatedImplements.isEmpty() ? null : updatedImplements);
          }
        });
  }
}
