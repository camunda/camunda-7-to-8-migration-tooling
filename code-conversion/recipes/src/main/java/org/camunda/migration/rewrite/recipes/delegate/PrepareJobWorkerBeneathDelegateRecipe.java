package org.camunda.migration.rewrite.recipes.delegate;

import java.util.List;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

public class PrepareJobWorkerBeneathDelegateRecipe extends Recipe {

  /** Instantiates a new instance. */
  public PrepareJobWorkerBeneathDelegateRecipe() {}

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
            Preconditions.not(new UsesType<>("io.camunda.client.api.response.ActivatedJob", true)),
            new UsesType<>("org.camunda.bpm.engine.delegate.DelegateExecution", true));

    return Preconditions.check(
        check,
        new JavaIsoVisitor<>() {

          @Override
          public J.ClassDeclaration visitClassDeclaration(
              J.ClassDeclaration classDeclaration, ExecutionContext ctx) {

            // Skip interfaces
            if (classDeclaration.getKind() != J.ClassDeclaration.Kind.Type.Class) {
              return classDeclaration;
            }

            List<Statement> currentStatements = classDeclaration.getBody().getStatements();

            for (Statement stmt : currentStatements) {
              if (stmt instanceof J.MethodDeclaration methDecl
                  && methDecl.getSimpleName().equals("execute")) {
                String workerName =
                    Character.toLowerCase(classDeclaration.getSimpleName().charAt(0))
                        + classDeclaration.getSimpleName().substring(1);

                maybeAddImport("io.camunda.spring.client.annotation.JobWorker");
                maybeAddImport("io.camunda.client.api.response.ActivatedJob");
                maybeAddImport("java.util.Map");
                maybeAddImport("java.util.HashMap");

                // Insert the new field at the bottom of the class body
                return RecipeUtils.createSimpleJavaTemplate(
                        """
                      @JobWorker(type = \"#{}\", autoComplete = true)
                      public Map<String, Object> executeJob(ActivatedJob job) throws Exception {
                          Map<String, Object> resultMap = new HashMap<>();
                          return resultMap;
                      }
                      """,
                        "io.camunda.spring.client.annotation.JobWorker",
                        "io.camunda.client.api.response.ActivatedJob",
                        "java.util.Map",
                        "java.util.HashMap")
                    .apply(
                        updateCursor(classDeclaration),
                        classDeclaration.getBody().getCoordinates().lastStatement(),
                        workerName);
              }
            }
            return super.visitClassDeclaration(classDeclaration, ctx);
          }
        });
  }
}
