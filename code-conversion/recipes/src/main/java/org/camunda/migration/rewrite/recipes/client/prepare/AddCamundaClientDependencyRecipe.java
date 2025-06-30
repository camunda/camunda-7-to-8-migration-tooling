package org.camunda.migration.rewrite.recipes.client.prepare;

import org.camunda.migration.rewrite.recipes.utils.RecipeConstants;
import org.openrewrite.*;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class AddCamundaClientDependencyRecipe extends Recipe {

  /** Instantiates a new instance. */
  public AddCamundaClientDependencyRecipe() {}

  @Override
  public String getDisplayName() {
    return "Ensure camunda 8 client";
  }

  @Override
  public String getDescription() {
    return "Adds camunda 8 client dependency.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        Preconditions.and(
            new UsesType<>(RecipeConstants.Type.PROCESS_ENGINE, true),
            Preconditions.not(new UsesType<>(RecipeConstants.Type.CAMUNDA_CLIENT, true)));

    return Preconditions.check(
        check,
        new JavaVisitor<ExecutionContext>() {

          @Override
          public J.ClassDeclaration visitClassDeclaration(
              J.ClassDeclaration classDecl, ExecutionContext ctx) {

            // Build the new field with JavaTemplate
            JavaTemplate template =
                JavaTemplate.builder(
                        """
                        @Autowired
                        private CamundaClient camundaClient;
                    """)
                    .imports(
                        "org.springframework.beans.factory.annotation.Autowired",
                        RecipeConstants.Type.CAMUNDA_CLIENT)
                    .javaParser(
                        JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .build();

            // Skip interfaces
            if (classDecl.getKind() != J.ClassDeclaration.Kind.Type.Class) {
              return classDecl;
            }

            // Check if field already exists
            boolean hasField =
                classDecl.getBody().getStatements().stream()
                    .filter(stmt -> stmt instanceof J.VariableDeclarations)
                    .map(stmt -> (J.VariableDeclarations) stmt)
                    .anyMatch(
                        varDecl ->
                            varDecl.getVariables().stream()
                                .anyMatch(v -> v.getSimpleName().equals(RecipeConstants.Type.CAMUNDA_CLIENT)));

            if (hasField) {
              return classDecl; // Already present
            }

            // Insert the new field at the top of the class body
            maybeAddImport(RecipeConstants.Type.CAMUNDA_CLIENT);
            maybeAddImport("org.springframework.beans.factory.annotation.Autowired");

            return template.apply(
                updateCursor(classDecl), classDecl.getBody().getCoordinates().firstStatement());
          }
        });
  }
}
