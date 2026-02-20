package io.camunda.migration.code.recipes.delegate;

import java.util.List;
import io.camunda.migration.code.recipes.utils.RecipeUtils;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

public class PrepareJobWorkerBeneathExecutionListenerRecipe extends Recipe {

  public PrepareJobWorkerBeneathExecutionListenerRecipe() {}

  @Override
  public String getDisplayName() {
    return "Injects a job worker prototype for ExecutionListener";
  }

  @Override
  public String getDescription() {
    return "Injects a job worker prototype beneath ExecutionListener implementations.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    TreeVisitor<?, ExecutionContext> precondition =
        Preconditions.and(
            Preconditions.not(new UsesType<>("io.camunda.client.api.response.ActivatedJob", true)),
            new UsesType<>("org.camunda.bpm.engine.delegate.ExecutionListener", true));

    return Preconditions.check(
        precondition,
        new JavaIsoVisitor<ExecutionContext>() {
          @Override
          public J.ClassDeclaration visitClassDeclaration(
              J.ClassDeclaration classDecl, ExecutionContext ctx) {

            if (classDecl.getKind() != J.ClassDeclaration.Kind.Type.Class) {
              return classDecl;
            }

            List<Statement> statements = classDecl.getBody().getStatements();

            for (Statement stmt : statements) {
              if (stmt instanceof J.MethodDeclaration m
                  && "notify".equals(m.getSimpleName())) {

                String workerName =
                    Character.toLowerCase(classDecl.getSimpleName().charAt(0))
                        + classDecl.getSimpleName().substring(1);

                maybeAddImport("io.camunda.client.annotation.JobWorker");
                maybeAddImport("io.camunda.client.api.response.ActivatedJob");
                maybeAddImport("java.util.Map");
                maybeAddImport("java.util.HashMap");

                return RecipeUtils.createSimpleJavaTemplate(
                        """
                        @JobWorker(type = \"#{}\", autoComplete = true)
                        public Map<String, Object> executeJob(ActivatedJob job) throws Exception {
                            Map<String, Object> resultMap = new HashMap<>();
                            return resultMap;
                        }
                        """,
                        "io.camunda.client.annotation.JobWorker",
                        "io.camunda.client.api.response.ActivatedJob",
                        "java.util.Map",
                        "java.util.HashMap")
                    .apply(
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