/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client;

import java.util.*;
import java.util.stream.Collectors;
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
  Set<String> REPOSITORY_SERVICE_TODOS =
      Set.of(
          "TODO: RepositoryService deployment method was not migrated automatically",
          "TODO: RepositoryService query was not migrated automatically");

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
            if (containsDeferredRepositoryServiceMigration(classDeclaration, ctx)) {
              return classDeclaration;
            }

            Set<UUID> fieldDeclarationIds =
                classDeclaration.getBody().getStatements().stream()
                    .filter(J.VariableDeclarations.class::isInstance)
                    .map(J.VariableDeclarations.class::cast)
                    .map(J.VariableDeclarations::getId)
                    .collect(Collectors.toSet());
            boolean hasRepositoryServiceTypeOutsideFields =
                hasRepositoryServiceTypeOutsideFields(
                    classDeclaration, fieldDeclarationIds, ctx);

            List<Statement> newStatements = new ArrayList<>();
            for (Statement statement : classDeclaration.getBody().getStatements()) {
              if (statement instanceof J.VariableDeclarations varDecls
                  && (TypeUtils.isOfClassType(varDecls.getType(), PROCESS_ENGINE)
                      || TypeUtils.isOfClassType(varDecls.getType(), RUNTIME_SERVICE)
                      || TypeUtils.isOfClassType(varDecls.getType(), TASK_SERVICE)
                      || isDirectRepositoryServiceType(varDecls))) {
                // This is the statement we want to remove, so skip adding it
                continue;
              }
              newStatements.add(statement);
            }

            maybeRemoveImport(PROCESS_ENGINE);
            maybeRemoveImport(RUNTIME_SERVICE);
            maybeRemoveImport(TASK_SERVICE);
            if (!hasRepositoryServiceTypeOutsideFields) {
              maybeRemoveImport(REPOSITORY_SERVICE);
            }

            return classDeclaration.withBody(
                classDeclaration.getBody().withStatements(newStatements));
          }

          private boolean containsDeferredRepositoryServiceMigration(
              J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
            boolean[] found = {false};
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J preVisit(J tree, ExecutionContext nestedCtx) {
                if (tree.getComments().stream()
                    .anyMatch(
                        comment ->
                            comment instanceof TextComment textComment
                                && REPOSITORY_SERVICE_TODOS.stream()
                                    .anyMatch(textComment.getText()::contains))) {
                  found[0] = true;
                }
                return super.preVisit(tree, nestedCtx);
              }
            }.visit(classDeclaration, ctx);
            return found[0];
          }

          private boolean hasRepositoryServiceTypeOutsideFields(
              J.ClassDeclaration classDeclaration,
              Set<UUID> fieldDeclarationIds,
              ExecutionContext ctx) {
            boolean[] found = {false};
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.VariableDeclarations visitVariableDeclarations(
                  J.VariableDeclarations declarations, ExecutionContext nestedCtx) {
                if (fieldDeclarationIds.contains(declarations.getId())
                    && isDirectRepositoryServiceType(declarations)) {
                  return declarations;
                }
                if (containsRepositoryServiceType(
                    declarations.getTypeExpression(), nestedCtx)) {
                  found[0] = true;
                }
                return super.visitVariableDeclarations(declarations, nestedCtx);
              }

              @Override
              public J.MethodDeclaration visitMethodDeclaration(
                  J.MethodDeclaration method, ExecutionContext nestedCtx) {
                if (containsRepositoryServiceType(
                    method.getReturnTypeExpression(), nestedCtx)) {
                  found[0] = true;
                }
                return super.visitMethodDeclaration(method, nestedCtx);
              }
            }.visit(classDeclaration, ctx);
            return found[0];
          }

          private boolean containsRepositoryServiceType(
              TypeTree typeExpression, ExecutionContext ctx) {
            if (typeExpression == null) {
              return false;
            }
            boolean[] found = {false};
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.Identifier visitIdentifier(
                  J.Identifier identifier, ExecutionContext nestedCtx) {
                if (identifier.getSimpleName().equals("RepositoryService")
                    || TypeUtils.isOfClassType(identifier.getType(), REPOSITORY_SERVICE)) {
                  found[0] = true;
                }
                return super.visitIdentifier(identifier, nestedCtx);
              }
            }.visit(typeExpression, ctx);
            return found[0];
          }

          private boolean isDirectRepositoryServiceType(
              J.VariableDeclarations declarations) {
            TypeTree typeExpression = declarations.getTypeExpression();
            boolean hasRepositoryServiceName =
                (typeExpression instanceof J.Identifier identifier
                        && identifier.getSimpleName().equals("RepositoryService"))
                    || (typeExpression instanceof J.FieldAccess fieldAccess
                        && fieldAccess.getName().getSimpleName().equals("RepositoryService"));
            return hasRepositoryServiceName
                && TypeUtils.isOfClassType(declarations.getType(), REPOSITORY_SERVICE);
          }
        });
  }
}
