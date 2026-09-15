/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client;

import io.camunda.migration.code.recipes.utils.RecipeUtils;
import java.util.ArrayList;
import java.util.List;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.java.tree.J.VariableDeclarations;

/** Migrates Camunda 7 RepositoryService deployments and flags unsupported queries. */
public class MigrateRepositoryServiceRecipe extends org.openrewrite.Recipe {

  private static final String REPOSITORY_SERVICE = "org.camunda.bpm.engine.RepositoryService";
  private static final String CAMUNDA_CLIENT = "io.camunda.client.CamundaClient";
  private static final String DEPLOYMENT_TODO =
      " TODO: RepositoryService deployment method was not migrated automatically";
  private static final String QUERY_TODO =
      " TODO: RepositoryService queries have no Java client equivalent in C8. "
          + "Use CamundaClient REST: newProcessDefinitionSearchRequest() or direct REST call.";

  private static final MethodMatcher CREATE_DEPLOYMENT =
      new MethodMatcher(REPOSITORY_SERVICE + " createDeployment()");
  private static final MethodMatcher CREATE_PROCESS_DEFINITION_QUERY =
      new MethodMatcher(REPOSITORY_SERVICE + " createProcessDefinitionQuery()");
  private static final MethodMatcher CREATE_DEPLOYMENT_QUERY =
      new MethodMatcher(REPOSITORY_SERVICE + " createDeploymentQuery()");
  private static final MethodMatcher DEPLOY =
      new MethodMatcher("org.camunda.bpm.engine.repository.DeploymentBuilder deploy()");

  @Override
  public String getDisplayName() {
    return "Migrate RepositoryService deployments";
  }

  @Override
  public String getDescription() {
    return "Replaces RepositoryService deployment builders with CamundaClient deploy commands and "
        + "adds migration guidance for RepositoryService queries.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return Preconditions.check(
        Preconditions.or(
            new org.openrewrite.java.search.UsesMethod<>(REPOSITORY_SERVICE + " createDeployment()", true),
            new org.openrewrite.java.search.UsesMethod<>(
                REPOSITORY_SERVICE + " createProcessDefinitionQuery()", true),
            new org.openrewrite.java.search.UsesMethod<>(
                REPOSITORY_SERVICE + " createDeploymentQuery()", true)),
        new JavaIsoVisitor<>() {
          private String clientIdentifier = "camundaClient";

          @Override
          public J.ClassDeclaration visitClassDeclaration(
              J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
            boolean hasCamundaClient =
                classDeclaration.getBody().getStatements().stream()
                    .filter(VariableDeclarations.class::isInstance)
                    .map(VariableDeclarations.class::cast)
                    .anyMatch(
                        declaration ->
                            declaration.getVariables().stream()
                                .anyMatch(variable -> variable.getSimpleName().equals("camundaClient"))
                                || TypeUtils.isOfClassType(declaration.getType(), CAMUNDA_CLIENT));
            if (hasCamundaClient) {
              clientIdentifier = "camundaClient";
            }
            boolean hasRepositoryQuery =
                classDeclaration.getBody().toString().contains("createProcessDefinitionQuery")
                    || classDeclaration.getBody().toString().contains("createDeploymentQuery");

            List<Statement> statements = new ArrayList<>();
            for (Statement statement : classDeclaration.getBody().getStatements()) {
              if (!(statement instanceof VariableDeclarations declaration)
                  || !TypeUtils.isOfClassType(declaration.getType(), REPOSITORY_SERVICE)) {
                statements.add(statement);
                continue;
              }

              if (hasCamundaClient) {
                if (hasRepositoryQuery) {
                  if (declaration.getTypeExpression() instanceof J.Identifier type) {
                    declaration =
                        declaration.withTypeExpression(
                            type
                                .withSimpleName("CamundaClient")
                                .withType(JavaType.ShallowClass.build(CAMUNDA_CLIENT)));
                  }
                  statements.add(declaration.withType(JavaType.ShallowClass.build(CAMUNDA_CLIENT)));
                }
                continue;
              }

              if (declaration.getTypeExpression() instanceof J.Identifier type) {
                declaration =
                    declaration.withTypeExpression(
                        type
                            .withSimpleName("CamundaClient")
                            .withType(JavaType.ShallowClass.build(CAMUNDA_CLIENT)));
              }
              declaration = declaration.withType(JavaType.ShallowClass.build(CAMUNDA_CLIENT));
              clientIdentifier = declaration.getVariables().get(0).getSimpleName();
              statements.add(declaration);
              hasCamundaClient = true;
            }

            maybeAddImport(CAMUNDA_CLIENT);
            J.ClassDeclaration visited =
                super.visitClassDeclaration(
                    classDeclaration.withBody(classDeclaration.getBody().withStatements(statements)),
                    ctx);
            maybeRemoveImport(REPOSITORY_SERVICE);
            return visited;
          }

          @Override
          public J.Import visitImport(J.Import import_, ExecutionContext ctx) {
            if (import_.getQualid().toString().equals(REPOSITORY_SERVICE)) {
              return null;
            }
            return super.visitImport(import_, ctx);
          }

          @Override
          public J.MethodInvocation visitMethodInvocation(
              J.MethodInvocation invocation, ExecutionContext ctx) {
            J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);

            if (DEPLOY.matches(visited) && containsMethod(visited, CREATE_DEPLOYMENT)) {
              return migrateDeployment(visited, ctx);
            }

            return visited;
          }

          @Override
          public J.Return visitReturn(J.Return returnStatement, ExecutionContext ctx) {
            J.Return visited = super.visitReturn(returnStatement, ctx);
            if (visited.getExpression() instanceof J.MethodInvocation invocation
                && containsRepositoryQuery(invocation)) {
              return addCommentIfMissing(visited, QUERY_TODO);
            }
            return visited;
          }

          private J.MethodInvocation migrateDeployment(
              J.MethodInvocation deployInvocation, ExecutionContext ctx) {
            List<J.MethodInvocation> chain = invocationChain(deployInvocation);
            List<J.MethodInvocation> sourceMethods = new ArrayList<>(chain);
            sourceMethods.remove(0);
            java.util.Collections.reverse(sourceMethods);

            if (sourceMethods.isEmpty() || !CREATE_DEPLOYMENT.matches(sourceMethods.get(0))) {
              return addCommentIfMissing(deployInvocation, DEPLOYMENT_TODO);
            }

            StringBuilder templateCode =
                new StringBuilder(
                    "#{client:any(io.camunda.client.CamundaClient)}"
                        + "\n    .newDeployResourceCommand()");
            List<Expression> arguments = new ArrayList<>();
            List<String> comments = new ArrayList<>();

            for (J.MethodInvocation method : sourceMethods.subList(1, sourceMethods.size())) {
              switch (method.getSimpleName()) {
                case "addClasspathResource" -> {
                  if (method.getArguments().size() != 1) {
                    return addCommentIfMissing(deployInvocation, DEPLOYMENT_TODO);
                  }
                  templateCode.append("\n    .addResourceFromClasspath(#{any(java.lang.String)})");
                  arguments.add(method.getArguments().get(0));
                }
                case "addInputStream" -> {
                  if (method.getArguments().size() != 2) {
                    return addCommentIfMissing(deployInvocation, DEPLOYMENT_TODO);
                  }
                  templateCode.append(
                      "\n    .addResourceStream(#{any(java.io.InputStream)}, #{any(java.lang.String)})");
                  arguments.add(method.getArguments().get(1));
                  arguments.add(method.getArguments().get(0));
                }
                case "addString" -> {
                  if (method.getArguments().size() != 2) {
                    return addCommentIfMissing(deployInvocation, DEPLOYMENT_TODO);
                  }
                  templateCode.append(
                      "\n    .addResourceStringUtf8(#{any(java.lang.String)}, #{any(java.lang.String)})");
                  arguments.add(method.getArguments().get(0));
                  arguments.add(method.getArguments().get(1));
                }
                case "tenantId" -> {
                  if (method.getArguments().size() != 1) {
                    return addCommentIfMissing(deployInvocation, DEPLOYMENT_TODO);
                  }
                  templateCode.append("\n    .tenantId(#{any(java.lang.String)})");
                  arguments.add(method.getArguments().get(0));
                }
                case "name", "source" -> {
                  // Deployment names and sources have no direct equivalent in the C8 command.
                }
                default -> {
                  return addCommentIfMissing(deployInvocation, DEPLOYMENT_TODO);
                }
              }
            }

            templateCode.append("\n    .send()\n    .join()");
            JavaTemplate template =
                RecipeUtils.createSimpleJavaTemplate(
                    templateCode.toString(),
                    CAMUNDA_CLIENT,
                    "io.camunda.client.api.command.DeployResourceCommandStep1");
            Object[] templateArguments = new Object[arguments.size() + 1];
            templateArguments[0] =
                RecipeUtils.createSimpleIdentifier(clientIdentifier, CAMUNDA_CLIENT);
            System.arraycopy(arguments.toArray(), 0, templateArguments, 1, arguments.size());
            J.MethodInvocation replacement =
                (J.MethodInvocation)
                    RecipeUtils.applyTemplate(
                        template, deployInvocation, getCursor(), templateArguments, comments);
            return maybeAutoFormat(deployInvocation, replacement, ctx);
          }

          private boolean containsRepositoryQuery(J.MethodInvocation invocation) {
            return containsMethod(invocation, CREATE_PROCESS_DEFINITION_QUERY)
                || containsMethod(invocation, CREATE_DEPLOYMENT_QUERY);
          }

          private boolean containsMethod(J.MethodInvocation invocation, MethodMatcher matcher) {
            for (J.MethodInvocation method : invocationChain(invocation)) {
              if (matcher.matches(method)) {
                return true;
              }
            }
            return false;
          }

          private List<J.MethodInvocation> invocationChain(J.MethodInvocation invocation) {
            List<J.MethodInvocation> chain = new ArrayList<>();
            Expression current = invocation;
            while (current instanceof J.MethodInvocation method) {
              chain.add(method);
              current = method.getSelect();
            }
            return chain;
          }

          private J.Return addCommentIfMissing(J.Return statement, String text) {
            if (statement.getComments().stream()
                  .anyMatch(
                      comment ->
                          comment instanceof TextComment textComment
                              && textComment.getText().contains(text.trim()))) {
              return statement;
            }
            return statement.withComments(
                  java.util.stream.Stream.concat(
                          statement.getComments().stream(),
                          java.util.stream.Stream.of(RecipeUtils.createSimpleComment(statement, text)))
                      .toList());
          }

          private J.MethodInvocation addCommentIfMissing(
              J.MethodInvocation invocation, String text) {
            if (invocation.getComments().stream()
                .anyMatch(
                      comment ->
                          comment instanceof TextComment textComment
                              && textComment.getText().contains(text.trim()))) {
              return invocation;
            }
            return invocation.withComments(
                java.util.stream.Stream.concat(
                          invocation.getComments().stream(),
                          java.util.stream.Stream.of(RecipeUtils.createSimpleComment(invocation, text)))
                      .toList());
          }

        });
  }
}
