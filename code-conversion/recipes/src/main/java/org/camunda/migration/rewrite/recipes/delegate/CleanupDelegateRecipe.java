package org.camunda.migration.rewrite.recipes.delegate;

import java.util.List;
import java.util.stream.Collectors;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.jgit.annotations.NonNull;

public class CleanupDelegateRecipe extends Recipe {

  /** Instantiates a new instance. */
  public CleanupDelegateRecipe() {}

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
        new UsesType<>("org.camunda.bpm.engine.delegate.JavaDelegate", true);

    return Preconditions.check(
        check,
        new JavaIsoVisitor<>() {

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
                                id.getType(), "org.camunda.bpm.engine.delegate.JavaDelegate"))
                    .collect(Collectors.toList());

            List<Statement> filteredStatements =
                classDecl.getBody().getStatements().stream()
                    .filter(
                        (statement ->
                            !(statement instanceof J.MethodDeclaration methDecl
                                && methDecl.getSimpleName().equals("execute"))))
                    .toList();

            maybeRemoveImport("org.camunda.bpm.engine.delegate.JavaDelegate");
            maybeRemoveImport("org.camunda.bpm.engine.delegate.DelegateExecution");

            return classDecl
                .withBody(classDecl.getBody().withStatements(filteredStatements))
                .withImplements(updatedImplements.isEmpty() ? null : updatedImplements);
          }
        });
  }
}
