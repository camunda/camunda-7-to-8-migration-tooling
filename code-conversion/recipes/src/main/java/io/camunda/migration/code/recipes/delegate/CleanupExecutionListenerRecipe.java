/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.delegate;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.jgit.annotations.NonNull;

public class CleanupExecutionListenerRecipe extends Recipe {

  /** Instantiates a new instance. */
  public CleanupExecutionListenerRecipe() {}

  @Override
  public String getDisplayName() {
    return "Removes ExecutionListener-related code";
  }

  @Override
  public String getDescription() {
    return "Removes ExecutionListener-related code.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        new UsesType<>("org.camunda.bpm.engine.delegate.ExecutionListener", true);

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

            // Filter out the ExecutionListener interface
            List<TypeTree> updatedImplements = classDecl.getImplements() == null ? Collections.emptyList() :
                classDecl.getImplements().stream()
                    .filter(
                        id ->
                            !TypeUtils.isOfClassType(
                                id.getType(), "org.camunda.bpm.engine.delegate.ExecutionListener"))
                    .collect(Collectors.toList());

            // Filter out the notify method with DelegateExecution signature only
            List<Statement> filteredStatements =
                classDecl.getBody().getStatements().stream()
                    .filter(
                        (statement ->
                            !(statement instanceof J.MethodDeclaration methDecl
                                && methDecl.getSimpleName().equals("notify")
                                && methDecl.getParameters().size() == 1
                                && methDecl.getParameters().get(0) instanceof J.VariableDeclarations varDecl
                                && TypeUtils.isOfClassType(
                                    varDecl.getType(),
                                    "org.camunda.bpm.engine.delegate.DelegateExecution"))))
                    .toList();

            maybeRemoveImport("org.camunda.bpm.engine.delegate.ExecutionListener");
            maybeRemoveImport("org.camunda.bpm.engine.delegate.DelegateExecution");

            return classDecl
                .withBody(classDecl.getBody().withStatements(filteredStatements))
                .withImplements(updatedImplements.isEmpty() ? null : updatedImplements);
          }
        });
  }
}

