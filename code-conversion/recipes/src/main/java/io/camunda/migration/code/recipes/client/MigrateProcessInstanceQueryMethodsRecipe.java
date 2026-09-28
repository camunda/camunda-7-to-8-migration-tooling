/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client;

import io.camunda.migration.code.recipes.utils.RecipeUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeUtils;

public class MigrateProcessInstanceQueryMethodsRecipe extends Recipe {

  private static final String PROCESS_INSTANCE_QUERY =
      "org.camunda.bpm.engine.runtime.ProcessInstanceQuery";
  private static final String PROCESS_INSTANCE_STATE =
      "io.camunda.client.api.search.enums.ProcessInstanceState";
  private static final String CREATE_QUERY_PATTERN =
      "org.camunda.bpm.engine.RuntimeService createProcessInstanceQuery()";
  private static final MethodMatcher CREATE_QUERY = new MethodMatcher(CREATE_QUERY_PATTERN);
  private static final String MANUAL_HINT =
      " TODO: Migrate this Camunda 7 process-instance query manually; preserve its filters,"
          + " runtime state and complete results (including pagination).";

  private record Filters(Expression processDefinition) {}

  @Override
  public @NonNull String getDisplayName() {
    return "Migrates process instance query methods";
  }

  @Override
  public @NonNull String getDescription() {
    return "Converts complete inline active process-instance counts and marks all other"
        + " process-instance queries for manual migration.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return Preconditions.check(
        Preconditions.or(
            new UsesMethod<>(CREATE_QUERY_PATTERN, true),
            new UsesType<>(PROCESS_INSTANCE_QUERY, true)),
        new JavaIsoVisitor<ExecutionContext>() {
          @Override
          public J.MethodInvocation visitMethodInvocation(
              J.MethodInvocation invocation, ExecutionContext ctx) {
            J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
            J.MethodInvocation terminal = countedQuery(visited);
            Filters filters = terminal == null ? null : filters(terminal);
            if (filters == null) {
              return visited;
            }

            String filter =
                filters.processDefinition() == null
                    ? ".state(ProcessInstanceState.ACTIVE)"
                    : "\n        .processDefinitionId(#{any(java.lang.String)})"
                        + "\n        .state(ProcessInstanceState.ACTIVE)";
            String accessor = visited.getSimpleName().equals("size") ? "intValue" : "longValue";
            JavaTemplate template =
                RecipeUtils.createSimpleJavaTemplate(
                    """
                    java.util.Optional.of(#{any(io.camunda.client.CamundaClient)}
                        .newProcessInstanceSearchRequest()
                        .filter(filter -> filter%s)
                        .send()
                        .join()
                        .page())
                        .filter(page -> Boolean.FALSE.equals(page.hasMoreTotalItems()))
                        .orElseThrow(() -> new IllegalStateException("Process-instance count exceeds search limit; paginate to count exactly"))
                        .totalItems().%s()
                    """
                        .formatted(filter, accessor),
                    PROCESS_INSTANCE_STATE,
                    "io.camunda.client.api.search.filter.ProcessInstanceFilter");
            maybeAddImport(PROCESS_INSTANCE_STATE);
            J.Identifier client =
                RecipeUtils.createSimpleIdentifier(
                    "camundaClient", "io.camunda.client.CamundaClient");
            return template.apply(
                getCursor(),
                visited.getCoordinates().replace(),
                filters.processDefinition() == null
                    ? new Object[] {client}
                    : new Object[] {client, filters.processDefinition()});
          }

          @Override
          public J.MethodDeclaration visitMethodDeclaration(
              J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
            if (visited.getBody() == null
                || hasManualHint(visited)
                || !containsUnconvertedQuery(visited.getBody())) {
              return visited;
            }
            return visited.withComments(
                Stream.concat(
                        visited.getComments().stream(),
                        Stream.of(RecipeUtils.createSimpleComment(visited, MANUAL_HINT)))
                    .toList());
          }

          @Override
          public J.VariableDeclarations visitVariableDeclarations(
              J.VariableDeclarations declarations, ExecutionContext ctx) {
            J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, ctx);
            if (getCursor().firstEnclosing(J.MethodDeclaration.class) != null
                || hasManualHint(visited)
                || !containsUnconvertedQuery(visited)) {
              return visited;
            }
            return visited.withComments(
                Stream.concat(
                        visited.getComments().stream(),
                        Stream.of(RecipeUtils.createSimpleComment(visited, MANUAL_HINT)))
                    .toList());
          }
        });
  }

  private static boolean hasManualHint(J tree) {
    return tree.getComments().stream()
        .anyMatch(
            comment ->
                comment instanceof TextComment text && text.getText().contains(MANUAL_HINT.trim()));
  }

  private static boolean containsUnconvertedQuery(J tree) {
    AtomicBoolean found = new AtomicBoolean();
    new JavaIsoVisitor<AtomicBoolean>() {
      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation invocation, AtomicBoolean result) {
        if (CREATE_QUERY.matches(invocation)
            || (invocation.getSelect() != null
                && TypeUtils.isOfClassType(
                    invocation.getSelect().getType(), PROCESS_INSTANCE_QUERY))) {
          result.set(true);
          return invocation;
        }
        return result.get() ? invocation : super.visitMethodInvocation(invocation, result);
      }
    }.visit(tree, found);
    return found.get();
  }

  private static J.MethodInvocation countedQuery(J.MethodInvocation invocation) {
    if (!hasNoArguments(invocation)) {
      return null;
    }
    Expression receiver = unwrap(invocation.getSelect());
    if (invocation.getSimpleName().equals("count")
        && receiver != null
        && TypeUtils.isOfClassType(receiver.getType(), PROCESS_INSTANCE_QUERY)) {
      return invocation;
    }
    if (invocation.getSimpleName().equals("count")
        && receiver instanceof J.MethodInvocation stream
        && stream.getSimpleName().equals("stream")
        && hasNoArguments(stream)) {
      receiver = unwrap(stream.getSelect());
    }
    if ((invocation.getSimpleName().equals("size")
            || invocation.getSimpleName().equals("count"))
        && receiver instanceof J.MethodInvocation list
        && list.getSimpleName().equals("list")
        && hasNoArguments(list)
        && list.getSelect() != null
        && TypeUtils.isOfClassType(list.getSelect().getType(), PROCESS_INSTANCE_QUERY)) {
      return list;
    }
    return null;
  }

  private static Filters filters(J.MethodInvocation terminal) {
    Expression receiver = unwrap(terminal.getSelect());
    Expression processDefinition = null;
    boolean active = false;
    while (receiver instanceof J.MethodInvocation method) {
      if (CREATE_QUERY.matches(method)) {
        return active ? new Filters(processDefinition) : null;
      }
      if (!TypeUtils.isOfClassType(method.getType(), PROCESS_INSTANCE_QUERY)) {
        return null;
      }
      switch (method.getSimpleName()) {
        case "active" -> {
          if (active || !hasNoArguments(method)) {
            return null;
          }
          active = true;
        }
        case "processDefinitionKey" -> {
          if (processDefinition != null || !hasSingleStringArgument(method)) {
            return null;
          }
          processDefinition = method.getArguments().get(0);
        }
        default -> {
          return null;
        }
      }
      receiver = unwrap(method.getSelect());
    }
    return null;
  }

  private static boolean hasNoArguments(J.MethodInvocation invocation) {
    return invocation.getArguments().isEmpty()
        || invocation.getArguments().size() == 1
            && invocation.getArguments().get(0) instanceof J.Empty;
  }

  private static boolean hasSingleStringArgument(J.MethodInvocation invocation) {
    return invocation.getArguments().size() == 1
        && (invocation.getArguments().get(0).getType() == JavaType.Primitive.String
            || TypeUtils.isOfClassType(
                invocation.getArguments().get(0).getType(), "java.lang.String"));
  }

  private static Expression unwrap(Expression expression) {
    while (expression instanceof J.Parentheses<?> parentheses
        && parentheses.getTree() instanceof Expression nested) {
      expression = nested;
    }
    return expression;
  }
}
