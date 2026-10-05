/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.testing;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import io.camunda.migration.code.recipes.sharedRecipes.AbstractMigrationRecipe;
import io.camunda.migration.code.recipes.utils.RecipeUtils;
import io.camunda.migration.code.recipes.utils.ReplacementUtils;
import io.camunda.migration.code.recipes.utils.ReplacementUtils.BuilderReplacementSpec;
import io.camunda.migration.code.recipes.utils.ReplacementUtils.ReturnReplacementSpec;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeUtils;

public class ReplaceAssertionsRecipe extends AbstractMigrationRecipe {

  private static final String BPMN_AWARE_TESTS =
      "org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests";
  private static final String CMMN_AWARE_TESTS =
      "org.camunda.bpm.engine.test.assertions.cmmn.CmmnAwareTests";
  private static final String PROCESS_ENGINE_TESTS =
      "org.camunda.bpm.engine.test.assertions.ProcessEngineTests";
  private static final List<String> ASSERTION_IMPORTS_TO_REMOVE =
      List.of(
          PROCESS_ENGINE_TESTS + ".assertThat",
          CMMN_AWARE_TESTS + ".assertThat",
          BPMN_AWARE_TESTS + ".assertThat",
          PROCESS_ENGINE_TESTS,
          CMMN_AWARE_TESTS,
          BPMN_AWARE_TESTS);

  private static final MethodMatcher VARIABLES_METHOD =
      new MethodMatcher(
          "org.camunda.bpm.engine.test.assertions.bpmn.ProcessInstanceAssert variables()");
  private static final MethodMatcher HAS_VARIABLES_METHOD =
      new MethodMatcher(
          "org.camunda.bpm.engine.test.assertions.bpmn.ProcessInstanceAssert hasVariables(..)");
  private static final String VARIABLE_MAP_TODO =
      " TODO: CPT has no assertion on the variable map. Use hasVariable, hasVariableNames, "
          + "hasVariables(Map), or hasVariableSatisfies.";
  private static final String HAS_VARIABLES_TODO =
      " TODO: CPT has no assertion for 'at least one variable'. Assert the expected names with "
          + "hasVariableNames(..).";

  @Override
  public String getDisplayName() {
    return "Convert test assertions";
  }

  @Override
  public String getDescription() {
    return "Replaces Camunda 7 test assertions with Camunda 8 CPT assertions.";
  }

  @Override
  protected TreeVisitor<?, ExecutionContext> preconditions() {
    return new UsesMethod<>(BPMN_AWARE_TESTS + " assertThat(..)", true);
  }

  @Override
  protected List<ReplacementUtils.SimpleReplacementSpec> simpleMethodInvocations() {
    return List.of(
        new ReplacementUtils.SimpleReplacementSpec(
            new MethodMatcher(
                BPMN_AWARE_TESTS
                    + " assertThat(org.camunda.bpm.engine.runtime.ProcessInstance)",
                true),
            RecipeUtils.createSimpleJavaTemplate(
                "CamundaAssert.assertThat(#{processInstance:any(io.camunda.client.api.response.ProcessInstanceEvent)})",
                "io.camunda.process.test.api.CamundaAssert"),
            null,
            "io.camunda.process.test.api.assertions.ProcessInstanceAssert",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(
                new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                    "processInstance", 0, "io.camunda.client.api.response.ProcessInstanceEvent")),
            Collections.emptyList(),
            ASSERTION_IMPORTS_TO_REMOVE,
            List.of("io.camunda.process.test.api.CamundaAssert")),
        new ReplacementUtils.SimpleReplacementSpec(
            new MethodMatcher(
                BPMN_AWARE_TESTS + " assertThat(org.camunda.bpm.engine.task.Task)", true),
            RecipeUtils.createSimpleJavaTemplate(
                "CamundaAssert.assertThat(io.camunda.process.test.api.assertions.UserTaskSelectors.byTaskName(#{task:any(io.camunda.client.api.search.response.UserTask)}.getName()))",
                "io.camunda.process.test.api.CamundaAssert",
                "io.camunda.process.test.api.assertions.UserTaskSelectors"),
            null,
            "io.camunda.process.test.api.assertions.UserTaskAssert",
            ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
            List.of(
                new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                    "task", 0, "io.camunda.client.api.search.response.UserTask")),
            Collections.emptyList(),
            ASSERTION_IMPORTS_TO_REMOVE,
            List.of(
                "io.camunda.process.test.api.CamundaAssert",
                "io.camunda.process.test.api.assertions.UserTaskSelectors.byTaskName")));
  }

  @Override
  protected List<BuilderReplacementSpec> builderMethodInvocations() {
    return Collections.emptyList(); // not used
  }

  @Override
  protected List<ReturnReplacementSpec> returnMethodInvocations() {
    return Collections.emptyList();
  }

  @Override
  protected List<ReplacementUtils.RenameReplacementSpec> renameMethodInvocations() {
    return List.of(
        rename("isWaitingAt(..)", "hasActiveElements"),
        rename("isNotWaitingAt(..)", "hasNoActiveElements"),
        rename("isWaitingAtExactly(..)", "hasActiveElementsExactly"),
        rename("isEnded()", "isCompleted"),
        rename("hasPassed(..)", "hasCompletedElements"),
        rename("hasPassedInOrder(..)", "hasCompletedElementsInOrder"),
        rename("isStarted()", "isCreated"),
        rename("isActive()", "isActive"),
        new ReplacementUtils.RenameReplacementSpec(
            new MethodMatcher("org.camunda.bpm.engine.test.assertions.bpmn.TaskAssert isAssignedTo(..)"),
            "hasAssignee"));
  }

  @Override
  public @NonNull TreeVisitor<?, ExecutionContext> getVisitor() {
    TreeVisitor<?, ExecutionContext> base = ReplaceAssertionsRecipe.super.getVisitor();
    TreeVisitor<?, ExecutionContext> variableAssertions =
        Preconditions.check(
            preconditions(),
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.MethodInvocation visitMethodInvocation(
                  J.MethodInvocation invocation, ExecutionContext ctx) {
                if (isOutermostMethodInvocation()
                    && hasMultiOperationVariableMapAssertion(invocation)) {
                  return addCommentIfMissing(invocation, VARIABLE_MAP_TODO);
                }

                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);

                if (HAS_VARIABLES_METHOD.matches(visited)) {
                  if (hasStringArguments(visited)) {
                    return renamed(visited, "hasVariableNames");
                  }
                }

                Expression select = visited.getSelect();
                if (select != null
                    && unwrapParentheses(select) instanceof J.MethodInvocation variables
                    && VARIABLES_METHOD.matches(variables)) {
                  if (visited.getSimpleName().equals("containsEntry")) {
                    return renamed(visited, "hasVariable")
                        .withSelect(renamed(variables, "isCreated"));
                  }
                  if (visited.getSimpleName().equals("containsKey")
                      || visited.getSimpleName().equals("containsKeys")) {
                    return renamed(visited, "hasVariableNames").withSelect(variables.getSelect());
                  }
                }

                if (isOutermostMethodInvocation()) {
                  if (containsUnsupportedHasVariables(visited)) {
                    return addCommentIfMissing(visited, HAS_VARIABLES_TODO);
                  }
                  if (containsUnsupportedVariableMapAssertion(visited)) {
                    return addCommentIfMissing(visited, VARIABLE_MAP_TODO);
                  }
                }
                return visited;
              }

              private boolean isOutermostMethodInvocation() {
                Cursor current = getCursor();
                Cursor parent = getCursor().getParentTreeCursor();
                while (parent.getValue() instanceof J.Parentheses<?>) {
                  current = parent;
                  parent = parent.getParentTreeCursor();
                }
                return !(parent.getValue() instanceof J.MethodInvocation methodInvocation
                    && methodInvocation.getSelect() == current.getValue());
              }
            });

    return new TreeVisitor<Tree, ExecutionContext>() {
      @Override
      public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
        Tree afterVariableAssertions = (Tree) variableAssertions.visit(tree, ctx);
        return (Tree) base.visit(afterVariableAssertions, ctx);
      }
    };
  }

  private static boolean hasStringArguments(J.MethodInvocation invocation) {
    return !invocation.getArguments().isEmpty()
        && invocation.getArguments().stream()
            .allMatch(
                argument ->
                    !(argument.getType() instanceof JavaType.Array)
                        && (argument.getType() == JavaType.Primitive.String
                            || TypeUtils.isOfClassType(
                                argument.getType(), "java.lang.String")));
  }

  private static boolean containsUnsupportedHasVariables(J tree) {
    AtomicBoolean found = new AtomicBoolean();
    new JavaIsoVisitor<AtomicBoolean>() {
      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation invocation, AtomicBoolean result) {
        if (HAS_VARIABLES_METHOD.matches(invocation) && !hasStringArguments(invocation)) {
          result.set(true);
          return invocation;
        }
        return super.visitMethodInvocation(invocation, result);
      }
    }.visit(tree, found);
    return found.get();
  }

  private static boolean hasMultiOperationVariableMapAssertion(J.MethodInvocation invocation) {
    int callsAfterVariables = 0;
    Expression current = invocation;
    while (current instanceof J.MethodInvocation methodInvocation) {
      if (VARIABLES_METHOD.matches(methodInvocation)) {
        return callsAfterVariables > 1;
      }
      callsAfterVariables++;
      current = unwrapParentheses(methodInvocation.getSelect());
    }
    return false;
  }

  private static Expression unwrapParentheses(Expression expression) {
    while (expression instanceof J.Parentheses<?> parentheses
        && parentheses.getTree() instanceof Expression nested) {
      expression = nested;
    }
    return expression;
  }

  private static boolean containsUnsupportedVariableMapAssertion(J tree) {
    AtomicBoolean found = new AtomicBoolean();
    new JavaIsoVisitor<AtomicBoolean>() {
      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation invocation, AtomicBoolean result) {
        Expression select = invocation.getSelect();
        if (select != null
            && unwrapParentheses(select) instanceof J.MethodInvocation variables
            && VARIABLES_METHOD.matches(variables)
            && !List.of("containsEntry", "containsKey", "containsKeys")
                .contains(invocation.getSimpleName())) {
          result.set(true);
          return invocation;
        }
        return super.visitMethodInvocation(invocation, result);
      }
    }.visit(tree, found);
    return found.get();
  }

  private static J.MethodInvocation renamed(J.MethodInvocation invocation, String newName) {
    return invocation.withName(RecipeUtils.createSimpleIdentifier(newName, "java.lang.String"));
  }

  private static J.MethodInvocation addCommentIfMissing(
      J.MethodInvocation invocation, String text) {
    if (invocation.getComments().stream()
        .anyMatch(
            comment ->
                comment instanceof TextComment textComment
                    && textComment.getText().contains(text.trim()))) {
      return invocation;
    }
    return invocation.withComments(
        Stream.concat(
                invocation.getComments().stream(),
                Stream.of(RecipeUtils.createSimpleComment(invocation, text)))
            .toList());
  }

  private ReplacementUtils.RenameReplacementSpec rename(String methodC7, String methodC8) {
    return new ReplacementUtils.RenameReplacementSpec(
        new MethodMatcher(
            "org.camunda.bpm.engine.test.assertions.bpmn.ProcessInstanceAssert " + methodC7),
        methodC8);
  }
}
