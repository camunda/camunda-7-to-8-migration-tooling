/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client;

import io.camunda.migration.code.recipes.utils.RecipeUtils;
import java.util.HashSet;
import java.util.Set;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.RenameVariable;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

public class PrepareCamundaClientDependencyRecipe extends Recipe {

  /** Instantiates a new instance. */
  public PrepareCamundaClientDependencyRecipe() {}

  @Override
  public String getDisplayName() {
    return "Ensure camunda 8 client";
  }

  @Override
  public String getDescription() {
    return "Adds camunda 8 client dependency.";
  }

  String CAMUNDA_CLIENT = "io.camunda.client.CamundaClient";
  private static final String CAMUNDA_CLIENT_FIELD = "camundaClient";

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        Preconditions.and(
            Preconditions.or(
                new UsesType<>("org.camunda.bpm.engine.ProcessEngine", true),
                new UsesType<>("org.camunda.bpm.engine.RuntimeService", true),
                new UsesType<>("org.camunda.bpm.engine.TaskService", true),
                new UsesType<>("org.camunda.bpm.engine.RepositoryService", true)),
            Preconditions.not(new UsesType<>(CAMUNDA_CLIENT, true)));

    return Preconditions.check(
        check,
        new JavaIsoVisitor<>() {

          @Override
          public J.ClassDeclaration visitClassDeclaration(
              J.ClassDeclaration classDeclaration, ExecutionContext ctx) {

            // Build the new field with JavaTemplate
            JavaTemplate template =
                RecipeUtils.createSimpleJavaTemplate(
                    """
                        @Autowired
                        private CamundaClient camundaClient;
                    """,
                    "org.springframework.beans.factory.annotation.Autowired",
                    CAMUNDA_CLIENT);

            // Skip interfaces
            if (classDeclaration.getKind() != J.ClassDeclaration.Kind.Type.Class) {
              return classDeclaration;
            }

            J.VariableDeclarations.NamedVariable existingCamundaClientField =
                classDeclaration.getBody().getStatements().stream()
                    .filter(stmt -> stmt instanceof J.VariableDeclarations)
                    .map(stmt -> (J.VariableDeclarations) stmt)
                    .filter(varDecl -> TypeUtils.isOfClassType(varDecl.getType(), CAMUNDA_CLIENT))
                    .flatMap(varDecl -> varDecl.getVariables().stream())
                    .filter(v -> v.getSimpleName().equals(CAMUNDA_CLIENT_FIELD))
                    .findFirst()
                    .orElse(null);

            if (existingCamundaClientField != null) {
              return classDeclaration;
            }

            J.ClassDeclaration preparedClass = classDeclaration;
            J.VariableDeclarations.NamedVariable conflictingField =
                classDeclaration.getBody().getStatements().stream()
                    .filter(stmt -> stmt instanceof J.VariableDeclarations)
                    .map(stmt -> (J.VariableDeclarations) stmt)
                    .filter(varDecl -> !TypeUtils.isOfClassType(varDecl.getType(), CAMUNDA_CLIENT))
                    .flatMap(varDecl -> varDecl.getVariables().stream())
                    .filter(v -> v.getSimpleName().equals(CAMUNDA_CLIENT_FIELD))
                    .findFirst()
                    .orElse(null);
            if (conflictingField != null) {
              String replacementName = findAvailableFieldName(classDeclaration, ctx);
              preparedClass =
                  (J.ClassDeclaration)
                      new RenameVariable<ExecutionContext>(conflictingField, replacementName)
                          .visit(classDeclaration, ctx);
            }

            // Insert the new field at the top of the class body
            maybeAddImport(CAMUNDA_CLIENT);
            maybeAddImport("org.springframework.beans.factory.annotation.Autowired");

            return template.apply(
                updateCursor(preparedClass),
                preparedClass.getBody().getCoordinates().firstStatement());
          }

          private String findAvailableFieldName(
              J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
            Set<String> variableNames = new HashSet<>();
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.VariableDeclarations visitVariableDeclarations(
                  J.VariableDeclarations declarations, ExecutionContext nestedCtx) {
                declarations.getVariables().stream()
                    .map(J.VariableDeclarations.NamedVariable::getSimpleName)
                    .forEach(variableNames::add);
                return super.visitVariableDeclarations(declarations, nestedCtx);
              }
            }.visit(classDeclaration, ctx);

            String baseName = CAMUNDA_CLIENT_FIELD + "Value";
            String candidate = baseName;
            int suffix = 2;
            while (variableNames.contains(candidate)) {
              candidate = baseName + suffix++;
            }
            return candidate;
          }
        });
  }
}
