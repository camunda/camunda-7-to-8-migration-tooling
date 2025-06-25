package org.camunda.migration.rewrite.recipes.client.prepare;

import org.camunda.migration.rewrite.recipes.utils.RecipeConstants;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

public class EnsureProcessEngineRecipe extends Recipe {

  /** Instantiates a new instance. */
  public EnsureProcessEngineRecipe() {}

  @Override
  public String getDisplayName() {
    return "Ensuring process engine";
  }

  @Override
  public String getDescription() {
    return "Replaces specific services with process engine.";
  }

  private String getServiceReference(Expression expr) {
    return expr instanceof J.Identifier ident
            && ident.getType() instanceof JavaType.FullyQualified fqType
        ? fqType.getFullyQualifiedName()
        : null;
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        Preconditions.or(
            new UsesType<>(RecipeConstants.Type.RUNTIME_SERVICE, true),
            new UsesType<>(RecipeConstants.Type.TASK_SERVICE, true),
            new UsesType<>(RecipeConstants.Type.REPOSITORY_SERVICE, true));

    return Preconditions.check(
        check,
        new JavaIsoVisitor<ExecutionContext>() {

          final JavaTemplate template =
              JavaTemplate.builder(
                      """
                          @Autowired
                          private ProcessEngine engine;
                      """)
                  .imports(
                      "org.springframework.beans.factory.annotation.Autowired",
                      RecipeConstants.Type.PROCESS_ENGINE)
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
                                JavaType.ShallowClass.build(RecipeConstants.Type.PROCESS_ENGINE)));

            if (hasField) {
              return super.visitClassDeclaration(classDecl, ctx);
            }

            maybeAddImport(RecipeConstants.Type.PROCESS_ENGINE);
            maybeAddImport("org.springframework.beans.factory.annotation.Autowired");

            // Insert the new field at the top of the class body
            return super.visitClassDeclaration(
                template.apply(
                    updateCursor(classDecl), classDecl.getBody().getCoordinates().firstStatement()),
                ctx);
          }

          final JavaTemplate processEngineRuntimeService =
              RecipeUtils.createSimpleJavaTemplate(
                  "#{any(" + RecipeConstants.Type.PROCESS_ENGINE + ")}.getRuntimeService()");

          final JavaTemplate processEngineTaskService =
              RecipeUtils.createSimpleJavaTemplate(
                  "#{any(" + RecipeConstants.Type.PROCESS_ENGINE + ")}.getTaskService()");

          final JavaTemplate processEngineRepositoryService =
              RecipeUtils.createSimpleJavaTemplate(
                  "#{any(" + RecipeConstants.Type.PROCESS_ENGINE + ")}.getRepositoryService()");

          /**
           * One method is replaced with another method, thus visiting J.MethodInvocations works.
           * The base identifier of the method invocation changes from runtimeService to engine.
           * This new identifier needs to be constructed manually, providing simple name and java
           * type.
           */
          @Override
          public J.MethodInvocation visitMethodInvocation(
              J.MethodInvocation methodInvocation, ExecutionContext ctx) {

            if (getServiceReference(methodInvocation.getSelect()) == null) {
              return super.visitMethodInvocation(methodInvocation, ctx);
            }

            // This is the replacement identifier for ProcessEngine
            J.Identifier processEngineJ =
                RecipeUtils.createSimpleIdentifier("engine", RecipeConstants.Type.PROCESS_ENGINE);

            return switch (getServiceReference(methodInvocation.getSelect())) {
              case RecipeConstants.Type.RUNTIME_SERVICE ->
                  methodInvocation
                      .withSelect(
                          processEngineRuntimeService.apply(
                              getCursor(),
                              methodInvocation.getCoordinates().replace(),
                              processEngineJ))
                      .withPrefix(Space.EMPTY);
              case RecipeConstants.Type.TASK_SERVICE ->
                  methodInvocation
                      .withSelect(
                          processEngineTaskService.apply(
                              getCursor(),
                              methodInvocation.getCoordinates().replace(),
                              processEngineJ))
                      .withPrefix(Space.EMPTY);
              case RecipeConstants.Type.REPOSITORY_SERVICE ->
                  methodInvocation
                      .withSelect(
                          processEngineRepositoryService.apply(
                              getCursor(),
                              methodInvocation.getCoordinates().replace(),
                              processEngineJ))
                      .withPrefix(Space.EMPTY);
              default -> super.visitMethodInvocation(methodInvocation, ctx);
            };
          }
        });
  }
}
