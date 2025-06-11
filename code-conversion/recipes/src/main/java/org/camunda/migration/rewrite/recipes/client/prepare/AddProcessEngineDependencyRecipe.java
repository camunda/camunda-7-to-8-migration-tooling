package org.camunda.migration.rewrite.recipes.client.prepare;

import org.camunda.migration.rewrite.recipes.client.utils.ClientConstants;
import org.openrewrite.*;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

public class AddProcessEngineDependencyRecipe extends Recipe {

  /** Instantiates a new instance. */
  public AddProcessEngineDependencyRecipe() {}

  @Override
  public String getDisplayName() {
    return "Add process engine dependency";
  }

  @Override
  public String getDescription() {
    return "Adds process engine dependency.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        Preconditions.and(
            Preconditions.not(new UsesType<>(ClientConstants.Type.PROCESS_ENGINE, true)),
            Preconditions.or(
                new UsesType<>(ClientConstants.Type.RUNTIME_SERVICE, true),
                new UsesType<>(ClientConstants.Type.TASK_SERVICE, true),
                new UsesType<>(ClientConstants.Type.REPOSITORY_SERVICE, true)));

    return Preconditions.check(
        check,
        new JavaVisitor<ExecutionContext>() {

          // Build the new field with JavaTemplate
          final JavaTemplate template =
              JavaTemplate.builder(
                      """
                          @Autowired
                          private ProcessEngine engine;
                      """)
                  .imports(
                      "org.springframework.beans.factory.annotation.Autowired",
                      ClientConstants.Type.PROCESS_ENGINE)
                  .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                  .build();

          /**
           * The class declaration is visited to insert the new dependency at the top of the class
           * body if necessary.
           */
          @Override
          public J.ClassDeclaration visitClassDeclaration(
              J.ClassDeclaration classDecl, ExecutionContext ctx) {

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
                            TypeUtils.isOfType(
                                varDecl.getType(),
                                JavaType.ShallowClass.build(ClientConstants.Type.PROCESS_ENGINE)));

            if (hasField) {
              return classDecl; // Already present
            }

            maybeAddImport(ClientConstants.Type.PROCESS_ENGINE);
            maybeAddImport("org.springframework.beans.factory.annotation.Autowired");

            // Insert the new field at the top of the class body
            return template.apply(
                updateCursor(classDecl), classDecl.getBody().getCoordinates().firstStatement());
          }
        });
  }
}
