package org.camunda.migration.rewrite.recipes.client;

import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class PrepareCamundaClientDependencyRecipe extends Recipe {

  /** Instantiates a new instance. */
  public PrepareCamundaClientDependencyRecipe() {}

  @Override
  public String getDisplayName() {
    return "Ensure camunda 8 client";
  }

  @Override
  public String getDescription() {
    return "Adds camunda 8 client dependency.";
  }

  String CAMUNDA_CLIENT = "io.camunda.client.CamundaClient";

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        Preconditions.and(
            Preconditions.or(
                new UsesType<>("org.camunda.bpm.engine.ProcessEngine", true),
                new UsesType<>("org.camunda.bpm.engine.RuntimeService", true),
                new UsesType<>("org.camunda.bpm.engine.TaskService", true),
                new UsesType<>("org.camunda.bpm.engine.RepositoryService", true)),
            Preconditions.not(new UsesType<>(CAMUNDA_CLIENT, true)));

    return Preconditions.check(
        check,
        new JavaIsoVisitor<>() {

          @Override
          public J.ClassDeclaration visitClassDeclaration(
              J.ClassDeclaration classDeclaration, ExecutionContext ctx) {

            // Build the new field with JavaTemplate
            JavaTemplate template =
                RecipeUtils.createSimpleJavaTemplate(
                    """
                        @Autowired
                        private CamundaClient camundaClient;
                    """,
                    "org.springframework.beans.factory.annotation.Autowired",
                    CAMUNDA_CLIENT);

            // Skip interfaces
            if (classDeclaration.getKind() != J.ClassDeclaration.Kind.Type.Class) {
              return classDeclaration;
            }

            // Check if field already exists
            boolean hasField =
                classDeclaration.getBody().getStatements().stream()
                    .filter(stmt -> stmt instanceof J.VariableDeclarations)
                    .map(stmt -> (J.VariableDeclarations) stmt)
                    .anyMatch(
                        varDecl ->
                            varDecl.getVariables().stream()
                                .anyMatch(v -> v.getSimpleName().equals(CAMUNDA_CLIENT)));

            if (hasField) {
              return classDeclaration; // Already present
            }

            // Insert the new field at the top of the class body
            maybeAddImport(CAMUNDA_CLIENT);
            maybeAddImport("org.springframework.beans.factory.annotation.Autowired");

            return template.apply(
                updateCursor(classDeclaration),
                classDeclaration.getBody().getCoordinates().firstStatement());
          }
        });
  }
}
