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
import java.util.Collections;
import java.util.List;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;

/** Migrates complete RepositoryService deployment chains to CamundaClient commands. */
public class MigrateRepositoryServiceRecipe extends org.openrewrite.Recipe {

  private static final String CAMUNDA_CLIENT = "io.camunda.client.CamundaClient";
  private static final String REPOSITORY_SERVICE = "org.camunda.bpm.engine.RepositoryService";
  private static final String DEPLOYMENT_TODO =
      " TODO: RepositoryService deployment method was not migrated automatically";
  private static final String QUERY_TODO =
      " TODO: RepositoryService query was not migrated automatically. "
          + "Migrate it manually with the corresponding Camunda 8 Java client search request "
          + "or REST endpoint.";

  private static final MethodMatcher CREATE_DEPLOYMENT =
      new MethodMatcher(REPOSITORY_SERVICE + " createDeployment()");
  private static final MethodMatcher DEPLOY =
      new MethodMatcher("org.camunda.bpm.engine.repository.DeploymentBuilder deploy()");
  private static final MethodMatcher REPOSITORY_QUERY =
      new MethodMatcher(REPOSITORY_SERVICE + " create*Query(..)");

  @Override
  public String getDisplayName() {
    return "Migrate RepositoryService deployments";
  }

  @Override
  public String getDescription() {
    return "Converts complete RepositoryService deployment chains and flags unsupported "
        + "deployments and queries for manual migration.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return Preconditions.check(
        Preconditions.or(
            new UsesMethod<>(REPOSITORY_SERVICE + " createDeployment()", true),
            new UsesMethod<>(REPOSITORY_SERVICE + " create*Query(..)", true)),
        new JavaIsoVisitor<>() {

          @Override
          public J.VariableDeclarations visitVariableDeclarations(
              J.VariableDeclarations declarations, ExecutionContext ctx) {
            J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, ctx);
            return addTodoForUnsupportedUsage(visited, ctx);
          }

          @Override
          public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
            J.Assignment visited = super.visitAssignment(assignment, ctx);
            return addTodoForUnsupportedUsage(visited, ctx);
          }

          @Override
          public J.Return visitReturn(J.Return returnStatement, ExecutionContext ctx) {
            J.Return visited = super.visitReturn(returnStatement, ctx);
            return addTodoForUnsupportedUsage(visited, ctx);
          }

          @Override
          public J.MethodInvocation visitMethodInvocation(
              J.MethodInvocation invocation, ExecutionContext ctx) {
            J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);

            if (DEPLOY.matches(visited) && containsMethod(visited, CREATE_DEPLOYMENT)) {
              return migrateDeployment(visited, ctx);
            }
            if (isStandaloneStatement() && containsMethod(visited, REPOSITORY_QUERY)) {
              return addCommentIfMissing(visited, QUERY_TODO);
            }
            return visited;
          }

          private J.VariableDeclarations addTodoForUnsupportedUsage(
              J.VariableDeclarations declaration, ExecutionContext ctx) {
            return (J.VariableDeclarations) addTodoForUnsupportedUsage((Statement) declaration, ctx);
          }

          private J.Assignment addTodoForUnsupportedUsage(
              J.Assignment assignment, ExecutionContext ctx) {
            return (J.Assignment) addTodoForUnsupportedUsage((Statement) assignment, ctx);
          }

          private J.Return addTodoForUnsupportedUsage(
              J.Return returnStatement, ExecutionContext ctx) {
            return (J.Return) addTodoForUnsupportedUsage((Statement) returnStatement, ctx);
          }

          private Statement addTodoForUnsupportedUsage(Statement statement, ExecutionContext ctx) {
            if (containsMethod(statement, REPOSITORY_QUERY, ctx)) {
              return addCommentIfMissing(statement, QUERY_TODO);
            }
            if (containsMethod(statement, CREATE_DEPLOYMENT, ctx)) {
              return addCommentIfMissing(statement, DEPLOYMENT_TODO);
            }
            return statement;
          }

          private J.MethodInvocation migrateDeployment(
              J.MethodInvocation deployment, ExecutionContext ctx) {
            if (!isStandaloneStatement()) {
              return deployment;
            }

            List<J.MethodInvocation> sourceMethods = sourceMethods(deployment);
            if (sourceMethods.isEmpty() || !CREATE_DEPLOYMENT.matches(sourceMethods.getFirst())) {
              return addCommentIfMissing(deployment, DEPLOYMENT_TODO);
            }

            StringBuilder templateCode =
                new StringBuilder(
                    "#{client:any(io.camunda.client.CamundaClient)}\n.newDeployResourceCommand()");
            List<Expression> arguments = new ArrayList<>();
            boolean hasResource = false;
            boolean hasTenantId = false;

            for (J.MethodInvocation method : sourceMethods.subList(1, sourceMethods.size())) {
              switch (method.getSimpleName()) {
                case "addClasspathResource" -> {
                  if (method.getArguments().size() != 1 || hasTenantId) {
                    return addCommentIfMissing(deployment, DEPLOYMENT_TODO);
                  }
                  templateCode.append("\n.addResourceFromClasspath(#{any(java.lang.String)})");
                  arguments.add(method.getArguments().getFirst());
                  hasResource = true;
                }
                case "addInputStream" -> {
                  if (method.getArguments().size() != 2
                      || hasTenantId
                      || !canReorder(method.getArguments())) {
                    return addCommentIfMissing(deployment, DEPLOYMENT_TODO);
                  }
                  templateCode.append(
                      "\n.addResourceStream(#{any(java.io.InputStream)}, "
                          + "#{any(java.lang.String)})");
                  arguments.add(method.getArguments().get(1));
                  arguments.add(method.getArguments().getFirst());
                  hasResource = true;
                }
                case "addString" -> {
                  if (method.getArguments().size() != 2
                      || hasTenantId
                      || !canReorder(method.getArguments())) {
                    return addCommentIfMissing(deployment, DEPLOYMENT_TODO);
                  }
                  templateCode.append(
                      "\n.addResourceStringUtf8(#{any(java.lang.String)}, "
                          + "#{any(java.lang.String)})");
                  arguments.add(method.getArguments().get(1));
                  arguments.add(method.getArguments().getFirst());
                  hasResource = true;
                }
                case "tenantId" -> {
                  if (method.getArguments().size() != 1 || !hasResource || hasTenantId) {
                    return addCommentIfMissing(deployment, DEPLOYMENT_TODO);
                  }
                  templateCode.append("\n.tenantId(#{any(java.lang.String)})");
                  arguments.add(method.getArguments().getFirst());
                  hasTenantId = true;
                }
                case "name" -> {
                  if (method.getArguments().size() != 1
                      || !canDiscard(method.getArguments().getFirst())) {
                    return addCommentIfMissing(deployment, DEPLOYMENT_TODO);
                  }
                }
                default -> {
                  return addCommentIfMissing(deployment, DEPLOYMENT_TODO);
                }
              }
            }

            if (!hasResource) {
              return addCommentIfMissing(deployment, DEPLOYMENT_TODO);
            }

            templateCode.append("\n.send()\n.join()");
            JavaTemplate template =
                RecipeUtils.createSimpleJavaTemplate(
                    templateCode.toString(),
                    CAMUNDA_CLIENT,
                    "io.camunda.client.api.command.DeployResourceCommandStep1");
            Object[] templateArguments = new Object[arguments.size() + 1];
            templateArguments[0] =
                RecipeUtils.createSimpleIdentifier("camundaClient", CAMUNDA_CLIENT);
            System.arraycopy(arguments.toArray(), 0, templateArguments, 1, arguments.size());

            maybeAddImport(CAMUNDA_CLIENT);
            J.MethodInvocation replacement =
                (J.MethodInvocation)
                    RecipeUtils.applyTemplate(
                        template,
                        deployment,
                        getCursor(),
                        templateArguments,
                        Collections.emptyList());
            return maybeAutoFormat(deployment, replacement, ctx);
          }

          private boolean canReorder(List<Expression> arguments) {
            return arguments.stream().allMatch(this::canDiscard);
          }

          private boolean canDiscard(Expression expression) {
            return expression instanceof J.Identifier || expression instanceof J.Literal;
          }

          private boolean isStandaloneStatement() {
            Object parent = getCursor().getParentTreeCursor().getValue();
            return parent instanceof J.Block
                || parent instanceof J.Case
                || parent instanceof J.If
                || parent instanceof J.ForLoop
                || parent instanceof J.ForEachLoop
                || parent instanceof J.WhileLoop
                || parent instanceof J.DoWhileLoop;
          }

          private boolean containsMethod(J.MethodInvocation invocation, MethodMatcher matcher) {
            Expression current = invocation;
            while (current instanceof J.MethodInvocation method) {
              if (matcher.matches(method)) {
                return true;
              }
              current = method.getSelect();
            }
            return false;
          }

          private boolean containsMethod(J tree, MethodMatcher matcher, ExecutionContext ctx) {
            boolean[] found = {false};
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.MethodInvocation visitMethodInvocation(
                  J.MethodInvocation invocation, ExecutionContext nestedCtx) {
                if (matcher.matches(invocation)) {
                  found[0] = true;
                }
                return super.visitMethodInvocation(invocation, nestedCtx);
              }
            }.visit(tree, ctx);
            return found[0];
          }

          private List<J.MethodInvocation> sourceMethods(J.MethodInvocation deployment) {
            List<J.MethodInvocation> methods = new ArrayList<>();
            Expression current = deployment.getSelect();
            while (current instanceof J.MethodInvocation method) {
              methods.add(method);
              current = method.getSelect();
            }
            Collections.reverse(methods);
            return methods;
          }

          private Statement addCommentIfMissing(Statement statement, String text) {
            if (hasComment(statement.getComments(), text)) {
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
            if (hasComment(invocation.getComments(), text)) {
              return invocation;
            }
            return invocation.withComments(
                java.util.stream.Stream.concat(
                        invocation.getComments().stream(),
                        java.util.stream.Stream.of(RecipeUtils.createSimpleComment(invocation, text)))
                    .toList());
          }

          private boolean hasComment(List<org.openrewrite.java.tree.Comment> comments, String text) {
            return comments.stream()
                .anyMatch(
                    comment ->
                        comment instanceof TextComment textComment
                            && textComment.getText().contains(text.trim()));
          }
        });
  }
}
