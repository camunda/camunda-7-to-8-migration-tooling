package org.camunda.migration.rewrite.recipes.external;

import java.util.List;
import java.util.stream.Collectors;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.jgit.annotations.NonNull;

public class CleanupExternalWorkerRecipe extends Recipe {

  /** Instantiates a new instance. */
  public CleanupExternalWorkerRecipe() {}

  @Override
  public String getDisplayName() {
    return "Removes external worker-related code";
  }

  @Override
  public String getDescription() {
    return "Removes external worker-related code.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        new UsesType<>("org.camunda.bpm.client.task.ExternalTask", true);

    return Preconditions.check(
        check,
        new JavaIsoVisitor<>() {

          final AnnotationMatcher subscription =
              new AnnotationMatcher(
                  "@org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription");

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
                                id.getType(), "org.camunda.bpm.client.task.ExternalTaskHandler"))
                    .collect(Collectors.toList());

            List<Statement> filteredStatements =
                classDecl.getBody().getStatements().stream()
                    .filter(
                        (statement ->
                            !(statement instanceof J.MethodDeclaration methDecl
                                && methDecl.getSimpleName().equals("execute"))))
                    .toList();

            List<J.Annotation> filteredAnnotations =
                classDecl.getLeadingAnnotations().stream()
                    .filter((annotation -> !(subscription.matches(annotation))))
                    .toList();

            maybeRemoveImport("org.camunda.bpm.client.task.ExternalTask");
            maybeRemoveImport("org.camunda.bpm.client.task.ExternalTaskService");
            maybeRemoveImport("org.camunda.bpm.client.task.ExternalTaskHandler");
            maybeRemoveImport("org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription");

            return classDecl
                .withBody(classDecl.getBody().withStatements(filteredStatements))
                .withImplements(updatedImplements.isEmpty() ? null : updatedImplements)
                .withLeadingAnnotations(filteredAnnotations);
          }
        });
  }
}
