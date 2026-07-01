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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;

public class DetectHistoryServiceUsageRecipe extends Recipe {

  private static final String HISTORY_SERVICE_FQN = "org.camunda.bpm.engine.HistoryService";
  private static final String DOCS_URL =
      "https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/";
  static final String MARKER = "HistoryService has no direct equivalent";

  private record MethodEndpoint(MethodMatcher matcher, String endpoint) {}

  private static final List<MethodEndpoint> HISTORY_METHODS =
      List.of(
          new MethodEndpoint(
              new MethodMatcher(HISTORY_SERVICE_FQN + " createHistoricProcessInstanceQuery()"),
              "POST /v2/process-instances/search"),
          new MethodEndpoint(
              new MethodMatcher(HISTORY_SERVICE_FQN + " createHistoricActivityInstanceQuery()"),
              "POST /v2/flow-node-instances/search"),
          new MethodEndpoint(
              new MethodMatcher(HISTORY_SERVICE_FQN + " createHistoricVariableInstanceQuery()"),
              "POST /v2/variables/search"),
          new MethodEndpoint(
              new MethodMatcher(HISTORY_SERVICE_FQN + " createHistoricTaskInstanceQuery()"),
              "POST /v2/user-tasks/search"),
          new MethodEndpoint(
              new MethodMatcher(HISTORY_SERVICE_FQN + " createHistoricDecisionInstanceQuery()"),
              "POST /v2/decision-instances/search"),
          new MethodEndpoint(
              new MethodMatcher(HISTORY_SERVICE_FQN + " createUserOperationLogQuery()"),
              "POST /v2/audit-logs/search (8.9+)"),
          new MethodEndpoint(
              new MethodMatcher(HISTORY_SERVICE_FQN + " createHistoricDetailQuery()"),
              "POST /v2/variables/search"));

  @Override
  public @NonNull String getDisplayName() {
    return "Detect HistoryService usage and add migration TODO hints";
  }

  @Override
  public @NonNull String getDescription() {
    return "Adds TODO comments where HistoryService is declared or used, explaining that it has no"
        + " direct equivalent in Camunda 8 and pointing to the Orchestration Cluster REST API.";
  }

  @Override
  public @NonNull TreeVisitor<?, ExecutionContext> getVisitor() {
    return Preconditions.check(
        new UsesType<>(HISTORY_SERVICE_FQN, true),
        new JavaIsoVisitor<>() {

          /**
           * Annotates field/variable declarations typed as HistoryService.
           * Comment is placed before any annotations on the declaration.
           */
          @Override
          public J.VariableDeclarations visitVariableDeclarations(
              J.VariableDeclarations declarations, ExecutionContext ctx) {
            J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, ctx);
            JavaType.FullyQualified type = visited.getTypeAsFullyQualified();
            if (type == null || !HISTORY_SERVICE_FQN.equals(type.getFullyQualifiedName())) {
              return visited;
            }
            if (alreadyAnnotated(visited.getComments())) {
              return visited;
            }
            return visited.withComments(
                Stream.concat(
                        visited.getComments().stream(),
                        declarationComments(visited).stream())
                    .toList());
          }

          /**
           * Annotates statements that contain HistoryService method calls.
           * Comments are placed at statement level for correct indentation.
           */
          @Override
          public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block visited = super.visitBlock(block, ctx);
            List<Statement> newStatements = new ArrayList<>();
            boolean changed = false;
            for (Statement stmt : visited.getStatements()) {
              Statement annotated = maybeAnnotateStatement(stmt);
              if (annotated != stmt) {
                changed = true;
              }
              newStatements.add(annotated);
            }
            return changed ? visited.withStatements(newStatements) : visited;
          }

          private Statement maybeAnnotateStatement(Statement stmt) {
            if (alreadyAnnotated(stmt.getComments())) {
              return stmt;
            }
            for (MethodEndpoint entry : HISTORY_METHODS) {
              if (containsHistoryServiceCall(stmt, entry.matcher())) {
                return addCommentToStatement(stmt, entry.endpoint());
              }
            }
            return stmt;
          }

          private boolean containsHistoryServiceCall(Statement stmt, MethodMatcher matcher) {
            AtomicReference<Boolean> found = new AtomicReference<>(false);
            new JavaIsoVisitor<AtomicReference<Boolean>>() {
              @Override
              public J.MethodInvocation visitMethodInvocation(
                  J.MethodInvocation inv, AtomicReference<Boolean> ref) {
                if (matcher.matches(inv)) {
                  ref.set(true);
                  return inv;
                }
                return Boolean.TRUE.equals(ref.get()) ? inv : super.visitMethodInvocation(inv, ref);
              }

              @Override
              public J.Block visitBlock(J.Block blk, AtomicReference<Boolean> ref) {
                return blk;
              }
            }.visit(stmt, found);
            return Boolean.TRUE.equals(found.get());
          }

          @SuppressWarnings("unchecked")
          private Statement addCommentToStatement(Statement stmt, String endpoint) {
            return (Statement)
                stmt.withComments(
                    Stream.concat(
                            stmt.getComments().stream(),
                            methodComments(stmt, endpoint).stream())
                        .toList());
          }

          private boolean alreadyAnnotated(List<Comment> comments) {
            return comments.stream()
                .anyMatch(c -> c instanceof TextComment tc && tc.getText().contains(MARKER));
          }

          private List<Comment> declarationComments(J.VariableDeclarations declaration) {
            return List.of(
                RecipeUtils.createSimpleComment(
                    declaration, " TODO: " + MARKER + " in Camunda 8."),
                RecipeUtils.createSimpleComment(
                    declaration, " Use the Orchestration Cluster REST API instead."),
                RecipeUtils.createSimpleComment(declaration, " See: " + DOCS_URL));
          }

          private List<Comment> methodComments(Statement stmt, String endpoint) {
            return List.of(
                RecipeUtils.createSimpleComment(
                    stmt, " TODO: " + MARKER + " in Camunda 8."),
                RecipeUtils.createSimpleComment(
                    stmt, " Use the Orchestration Cluster REST API: " + endpoint),
                RecipeUtils.createSimpleComment(stmt, " See: " + DOCS_URL));
          }
        });
  }
}
