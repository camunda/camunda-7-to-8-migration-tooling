package org.camunda.migration.rewrite.recipes.delegate.prepare;

import java.util.List;

import org.camunda.migration.rewrite.recipes.utils.RecipeConstants;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

public class InjectJobWorkerRecipe extends Recipe {

  /** Instantiates a new instance. */
  public InjectJobWorkerRecipe() {}

  @Override
  public String getDisplayName() {
    return "Injects a job worker prototype";
  }

  @Override
  public String getDescription() {
    return "Injects a job worker prototype.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        Preconditions.and(
            Preconditions.not(
                new UsesType<>(RecipeConstants.Type.ACTIVATED_JOB, true)),
            new UsesType<>(RecipeConstants.Type.DELEGATE_EXECUTION, true));

    return Preconditions.check(
        check,
        new JavaVisitor<ExecutionContext>() {

          JavaTemplate jobWorker =
              JavaTemplate.builder(
                      """
                @JobWorker(type = \"#{}\", autoComplete = true)
                public Map<String, Object> executeJob(ActivatedJob job) throws Exception {
                    Map<String, Object> resultMap = new HashMap<>();
                    return resultMap;
                }
                """)
                  .imports(
                          RecipeConstants.Type.JOB_WORKER,
                      RecipeConstants.Type.ACTIVATED_JOB,
                      "java.util.Map",
                      "java.util.HashMap")
                  .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                  .build();

          @Override
          public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {

            // Skip interfaces
            if (classDecl.getKind() != J.ClassDeclaration.Kind.Type.Class) {
              return classDecl;
            }

            List<Statement> currentStatements = classDecl.getBody().getStatements();

            for (Statement stmt : currentStatements) {
              if (stmt instanceof J.MethodDeclaration methDecl
                  && methDecl.getSimpleName().equals("execute")) {
                String workerName = Character.toLowerCase(classDecl.getSimpleName().charAt(0)) + classDecl.getSimpleName().substring(1);

                maybeAddImport(RecipeConstants.Type.JOB_WORKER);
                maybeAddImport(RecipeConstants.Type.ACTIVATED_JOB);
                maybeAddImport("java.util.Map");
                maybeAddImport("java.util.HashMap");

                // Insert the new field at the bottom of the class body
                return jobWorker.apply(
                    updateCursor(classDecl),
                    classDecl.getBody().getCoordinates().lastStatement(),
                    workerName);
              }
            }
            return super.visitClassDeclaration(classDecl, ctx);
          }
        });
  }
}
