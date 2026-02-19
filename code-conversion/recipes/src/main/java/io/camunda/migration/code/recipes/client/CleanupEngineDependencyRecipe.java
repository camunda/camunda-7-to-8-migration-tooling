/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.*;
import org.openrewrite.java.tree.*;

public class CleanupEngineDependencyRecipe extends Recipe {
  /** Instantiates a new instance. */
  public CleanupEngineDependencyRecipe() {}

  @Override
  public String getDisplayName() {
    return "Remove process engine dependency";
  }

  @Override
  public String getDescription() {
    return "Removes process engine dependency. Tries to remove import.";
  }

  String PROCESS_ENGINE = "org.camunda.bpm.engine.ProcessEngine";
  String RUNTIME_SERVICE = "org.camunda.bpm.engine.RuntimeService";
  String TASK_SERVICE = "org.camunda.bpm.engine.TaskService";
  String REPOSITORY_SERVICE = "org.camunda.bpm.engine.RepositoryService";

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        Preconditions.or(
            new UsesType<>(PROCESS_ENGINE, true),
            new UsesType<>(RUNTIME_SERVICE, true),
            new UsesType<>(TASK_SERVICE, true),
            new UsesType<>(REPOSITORY_SERVICE, true));

    return Preconditions.check(
        check,
        new JavaIsoVisitor<>() {

          /**
           * Removing an LST element cannot be done by visiting it directly. Visiting
           * J.ClassDeclaration targets the smallest LST element to filter out the dependencies to
           * be removed. The body of the class contains statements, which can be any type that is
           * allowed to be in a class body. All statements apart from the ones to be removed are
           * collected. If the statement is instanceof J.VariableDeclarations and defines variables
           * of the type to remove, it is not collected. The class declaration is returned with new
           * statements in its body.
           */
          @Override
          public J.ClassDeclaration visitClassDeclaration(
              J.ClassDeclaration classDeclaration, ExecutionContext ctx) {

            List<Statement> newStatements = new ArrayList<>();
            Set<String> removedVariableNames = new HashSet<>();

            for (Statement statement : classDeclaration.getBody().getStatements()) {
              if (statement instanceof J.VariableDeclarations varDecls
                  && (TypeUtils.isOfClassType(varDecls.getType(), PROCESS_ENGINE)
                      || TypeUtils.isOfClassType(varDecls.getType(), RUNTIME_SERVICE)
                      || TypeUtils.isOfClassType(varDecls.getType(), TASK_SERVICE)
                      || TypeUtils.isOfClassType(varDecls.getType(), REPOSITORY_SERVICE))) {
                // Check if the variable is still used in the class before removing
                String varName = varDecls.getVariables().get(0).getSimpleName();
                if (isVariableUsedInMethods(classDeclaration, varName)) {
                  // Variable is still in use, keep it
                  newStatements.add(statement);
                } else {
                  // Variable is not used, mark for removal
                  removedVariableNames.add(varName);
                }
                continue;
              }
              newStatements.add(statement);
            }

            // Only remove imports if the corresponding fields were actually removed
            if (!removedVariableNames.isEmpty()) {
              maybeRemoveImport(PROCESS_ENGINE);
              maybeRemoveImport(RUNTIME_SERVICE);
              maybeRemoveImport(TASK_SERVICE);
              maybeRemoveImport(REPOSITORY_SERVICE);
            }

            return classDeclaration.withBody(
                classDeclaration.getBody().withStatements(newStatements));
          }

          /**
           * Checks if a variable with the given name is used in any method body within the class.
           */
          private boolean isVariableUsedInMethods(J.ClassDeclaration classDeclaration, String varName) {
            AtomicBoolean isUsed = new AtomicBoolean(false);

            new JavaIsoVisitor<AtomicBoolean>() {
              @Override
              public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean used) {
                if (identifier.getSimpleName().equals(varName)) {
                  used.set(true);
                }
                return identifier;
              }
            }.visit(classDeclaration.getBody(), isUsed);

            // The visitor will find the variable declaration itself, so we need to check
            // if it's used more than just in the declaration. We do this by counting
            // occurrences in method declarations only.
            AtomicBoolean usedInMethods = new AtomicBoolean(false);

            for (Statement statement : classDeclaration.getBody().getStatements()) {
              if (statement instanceof J.MethodDeclaration methodDecl) {
                new JavaIsoVisitor<AtomicBoolean>() {
                  @Override
                  public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean used) {
                    if (identifier.getSimpleName().equals(varName)) {
                      used.set(true);
                    }
                    return identifier;
                  }
                }.visit(methodDecl, usedInMethods);

                if (usedInMethods.get()) {
                  return true;
                }
              }
            }

            return false;
          }
        });
  }
}
