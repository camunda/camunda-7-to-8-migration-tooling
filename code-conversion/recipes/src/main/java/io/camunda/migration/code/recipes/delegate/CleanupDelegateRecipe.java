/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.delegate;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.jgit.annotations.NonNull;

public class CleanupDelegateRecipe extends Recipe {

  /** Instantiates a new instance. */
  public CleanupDelegateRecipe() {}

  @Override
  public String getDisplayName() {
    return "Removes delegate-related code";
  }

  @Override
  public String getDescription() {
    return "Removes delegate-related code.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        new UsesType<>("org.camunda.bpm.engine.delegate.JavaDelegate", true);

    return Preconditions.check(
        check,
        new JavaIsoVisitor<>() {

          @Override
          @NonNull
          public J.ClassDeclaration visitClassDeclaration(
              @NonNull J.ClassDeclaration classDecl, ExecutionContext ctx) {

            // Skip interfaces
            if (classDecl.getKind() != J.ClassDeclaration.Kind.Type.Class) {
              return classDecl;
            }

            // Preserve delegate code when migration already warned that the body could not be
            // copied automatically, so users can still migrate it manually.
            if (hasDelegateBodyWarning(classDecl)) {
              return super.visitClassDeclaration(classDecl, ctx);
            }

            // Filter out the JavaDelegate interface and any subinterfaces of it
            List<TypeTree> updatedImplements = classDecl.getImplements() == null ? Collections.emptyList() :
                classDecl.getImplements().stream()
                    .filter(id -> !isJavaDelegateAssignable(id.getType()))
                    .collect(Collectors.toList());

            List<Statement> filteredStatements =
                classDecl.getBody().getStatements().stream()
                    .filter(
                        (statement ->
                            !(statement instanceof J.MethodDeclaration methDecl
                                && methDecl.getSimpleName().equals("execute"))))
                    .toList();

            maybeRemoveImport("org.camunda.bpm.engine.delegate.JavaDelegate");
            maybeRemoveImport("org.camunda.bpm.engine.delegate.DelegateExecution");

            return classDecl
                .withBody(classDecl.getBody().withStatements(filteredStatements))
                .withImplements(updatedImplements.isEmpty() ? null : updatedImplements);
          }

          private boolean hasDelegateBodyWarning(J.ClassDeclaration classDecl) {
            List<Comment> comments =
                classDecl.getComments() == null ? Collections.emptyList() : classDecl.getComments();
            return comments.stream()
                .filter(c -> c instanceof TextComment)
                .map(c -> (TextComment) c)
                .anyMatch(
                    c ->
                        c.getText().contains(
                            MigrateExecutionRecipe.DELEGATE_BODY_COPY_WARNING_SENTINEL));
          }

          private boolean isJavaDelegateAssignable(JavaType type) {
            return type != null
                && isAssignableTo(type, "org.camunda.bpm.engine.delegate.JavaDelegate", new HashSet<>());
          }

          private boolean isAssignableTo(JavaType type, String fullyQualifiedName, Set<String> visited) {
            if (!visited.add(type.toString())) {
              return false;
            }
            if (TypeUtils.isOfClassType(type, fullyQualifiedName)) {
              return true;
            }
            if (type instanceof JavaType.FullyQualified fullyQualified) {
              JavaType.FullyQualified supertype = fullyQualified.getSupertype();
              if (supertype != null && isAssignableTo(supertype, fullyQualifiedName, visited)) {
                return true;
              }
              for (JavaType.FullyQualified iface : fullyQualified.getInterfaces()) {
                if (isAssignableTo(iface, fullyQualifiedName, visited)) {
                  return true;
                }
              }
            }
            return false;
          }
        });
  }
}
