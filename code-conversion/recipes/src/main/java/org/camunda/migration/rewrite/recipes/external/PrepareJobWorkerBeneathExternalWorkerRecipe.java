package org.camunda.migration.rewrite.recipes.external;

import java.util.ArrayList;
import java.util.List;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

public class PrepareJobWorkerBeneathExternalWorkerRecipe extends Recipe {

  /** Instantiates a new instance. */
  public PrepareJobWorkerBeneathExternalWorkerRecipe() {}

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
            new UsesType<>("org.camunda.bpm.client.task.ExternalTask", true));

    return Preconditions.check(
        check,
        new JavaIsoVisitor<>() {

          final AnnotationMatcher configurationMatcher =
              new AnnotationMatcher("@org.springframework.context.annotation.Configuration");

          final JavaTemplate componentTemplate =
              RecipeUtils.createSimpleJavaTemplate(
                  "@org.springframework.stereotype.Component",
                  "org.springframework.stereotype.Component");

          @Override
          public J.ClassDeclaration visitClassDeclaration(
              J.ClassDeclaration classDeclaration, ExecutionContext ctx) {

            // Skip interfaces
            if (classDeclaration.getKind() != J.ClassDeclaration.Kind.Type.Class) {
              return classDeclaration;
            }

            List<J.Annotation> newAnnotations = new ArrayList<>();

            boolean changed = false;

            for (J.Annotation annotation : classDeclaration.getLeadingAnnotations()) {
              if (annotation.getAnnotationType() instanceof J.Identifier id
                  && "Configuration".equals(id.getSimpleName())) {

                // Replace with @Component
                J.Annotation componentAnnotation =
                    annotation.withAnnotationType(
                        id.withSimpleName("Component")
                            .withType(
                                JavaType.ShallowClass.build(
                                    "org.springframework.stereotype.Component")));
                newAnnotations.add(componentAnnotation);
                changed = true;

                maybeRemoveImport("org.springframework.context.annotation.Configuration");
                maybeAddImport("org.springframework.stereotype.Component");

              } else {
                newAnnotations.add(annotation);
              }
            }

            if (changed) {
              classDeclaration = classDeclaration.withLeadingAnnotations(newAnnotations);
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
            return classDeclaration;
          }
        });
  }
}
