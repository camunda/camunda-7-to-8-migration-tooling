/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client;

import java.util.stream.Stream;
import io.camunda.migration.code.recipes.utils.RecipeUtils;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeUtils;

/**
 * Flags job-based user task workers so they can be migrated to Camunda user tasks.
 *
 * <p>Job-based user tasks are handled by a {@code @JobWorker} that activates the built-in {@code
 * io.camunda.zeebe:userTask} job type. They are deprecated in Camunda 8.8 and removed in 8.10.
 * There is no code equivalent for a Camunda user task - they are managed through the User Task API
 * / Tasklist rather than a job worker - so this recipe only attaches a {@code TODO} to the worker
 * instead of rewriting it.
 */
public class DetectJobBasedUserTaskWorkerRecipe extends Recipe {

  static final String USER_TASK_JOB_TYPE = "io.camunda.zeebe:userTask";

  static final String MIGRATION_HINT =
      " TODO: job-based user tasks are deprecated (removed in Camunda 8.10). This @JobWorker handles"
          + " the built-in \""
          + USER_TASK_JOB_TYPE
          + "\" job type. Migrate to Camunda user tasks: remove this worker and manage the task via"
          + " the User Task API / Tasklist. See"
          + " https://docs.camunda.io/docs/apis-tools/migration-manuals/migrate-to-camunda-user-tasks/";

  public DetectJobBasedUserTaskWorkerRecipe() {}

  @Override
  public String getDisplayName() {
    return "Detects job-based user task workers";
  }

  @Override
  public String getDescription() {
    return "Flags @JobWorker methods that activate the built-in \"io.camunda.zeebe:userTask\" job "
        + "type with a TODO, because job-based user tasks are deprecated and must be migrated to "
        + "Camunda user tasks before Camunda 8.10.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return Preconditions.check(
        Preconditions.or(
            new UsesType<>("io.camunda.client.annotation.JobWorker", true),
            new UsesType<>("io.camunda.spring.client.annotation.JobWorker", true)),
        new JavaIsoVisitor<ExecutionContext>() {
          @Override
          public J.MethodDeclaration visitMethodDeclaration(
              J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

            boolean handlesUserTaskJob =
                m.getLeadingAnnotations().stream()
                    .anyMatch(a -> isJobWorkerAnnotation(a) && targetsUserTaskJobType(a));
            if (!handlesUserTaskJob) {
              return m;
            }

            // avoid appending the hint again on repeated visits
            boolean alreadyFlagged =
                m.getComments().stream()
                    .anyMatch(
                        c -> c instanceof TextComment tc && tc.getText().contains(USER_TASK_JOB_TYPE));
            if (alreadyFlagged) {
              return m;
            }

            Comment hint = RecipeUtils.createSimpleComment(m, MIGRATION_HINT);
            return m.withComments(
                Stream.concat(m.getComments().stream(), Stream.of(hint)).toList());
          }
        });
  }

  private static boolean isJobWorkerAnnotation(J.Annotation annotation) {
    JavaType.FullyQualified type = TypeUtils.asFullyQualified(annotation.getType());
    if (type != null) {
      return type.getFullyQualifiedName().endsWith(".JobWorker");
    }
    // fall back to the simple name when the annotation type could not be attributed
    return "JobWorker".equals(annotation.getSimpleName());
  }

  private static boolean targetsUserTaskJobType(J.Annotation annotation) {
    if (annotation.getArguments() == null) {
      return false;
    }
    for (Expression argument : annotation.getArguments()) {
      if (argument instanceof J.Assignment assignment
          && assignment.getVariable() instanceof J.Identifier name
          && "type".equals(name.getSimpleName())
          && assignment.getAssignment() instanceof J.Literal literal
          && USER_TASK_JOB_TYPE.equals(literal.getValue())) {
        return true;
      }
    }
    return false;
  }
}
