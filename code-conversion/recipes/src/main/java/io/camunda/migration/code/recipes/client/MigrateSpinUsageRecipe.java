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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;

public class MigrateSpinUsageRecipe extends Recipe {

  private static final String FILE_TODO_MARKER = "This file uses Camunda Spin";
  private static final String FILE_TODO =
      " TODO: This file uses Camunda Spin, which is not provided by the Camunda 8 process engine."
          + " Replace Spin JSON/XML handling with Jackson or standard Java XML APIs.";
  private static final String JSON_TODO =
      " TODO: Replace Spin JSON(...): parse existing JSON text or streams with"
          + " objectMapper.readValue(input, Map.class); pass POJOs/Maps directly (or use"
          + " objectMapper.writeValueAsString(pojo) when a JSON string is needed). Do not parse"
          + " String.valueOf(pojo) as JSON.";
  private static final String XML_TODO =
      " TODO: Camunda 8 does not provide native XML process variables. Keep XML as a String"
          + " and parse it with Java XML APIs, or convert the data model to JSON.";
  private static final String PROPERTY_TODO =
      " TODO: Replace SpinJsonNode.prop(...).stringValue() with Map access or Jackson mapping;"
          + " handle missing or null properties.";
  private static final String SPIN_JSON_FQN = "org.camunda.spin.json.SpinJsonNode";
  private static final MethodMatcher JSON_METHOD =
      new MethodMatcher("org.camunda.spin.Spin JSON(..)");
  private static final MethodMatcher XML_METHOD =
      new MethodMatcher("org.camunda.spin.Spin XML(..)");
  private static final MethodMatcher SPIN_JSON_PROPERTY =
      new MethodMatcher(SPIN_JSON_FQN + " prop(java.lang.String)");
  private static final MethodMatcher SPIN_JSON_STRING_VALUE =
      new MethodMatcher(SPIN_JSON_FQN + " stringValue()");

  @Override
  public String getDisplayName() {
    return "Detect Camunda Spin usage and add migration guidance";
  }

  @Override
  public String getDescription() {
    return "Adds migration guidance for Camunda Spin JSON and XML APIs that are not part of the"
        + " Camunda 8 process-variable API.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {

      @Override
      public J.CompilationUnit visitCompilationUnit(
          J.CompilationUnit compilationUnit, ExecutionContext ctx) {
        J.CompilationUnit visited = super.visitCompilationUnit(compilationUnit, ctx);
        if (!hasSpinImport(visited)
            || visited.getClasses().isEmpty()
            || hasComment(visited.getClasses().get(0).getComments(), FILE_TODO_MARKER)) {
          return visited;
        }

        List<J.ClassDeclaration> classes = new ArrayList<>(visited.getClasses());
        J.ClassDeclaration firstClass = classes.get(0);
        List<Comment> comments = new ArrayList<>();
        comments.add(RecipeUtils.createSimpleComment(firstClass, FILE_TODO));
        comments.addAll(firstClass.getComments());
        classes.set(0, firstClass.withComments(comments));
        return visited.withClasses(classes);
      }

      @Override
      public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
        J.Block visited = super.visitBlock(block, ctx);
        List<Statement> statements = new ArrayList<>(visited.getStatements());
        boolean changed = false;

        for (int i = 0; i < statements.size(); i++) {
          Statement statement = statements.get(i);
          Statement annotated = statement;
          for (String hint : hintsFor(statement)) {
            annotated = addCommentIfMissing(annotated, hint);
          }
          if (annotated != statement) {
            statements.set(i, annotated);
            changed = true;
          }
        }

        return changed ? maybeAutoFormat(block, visited.withStatements(statements), ctx) : visited;
      }

      private List<String> hintsFor(Statement statement) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        new JavaIsoVisitor<LinkedHashSet<String>>() {
          @Override
          public J.MethodInvocation visitMethodInvocation(
              J.MethodInvocation invocation, LinkedHashSet<String> collectedHints) {
            if (JSON_METHOD.matches(invocation)) {
              collectedHints.add(JSON_TODO);
            }
            if (XML_METHOD.matches(invocation)) {
              collectedHints.add(XML_TODO);
            }
            if (isSpinJsonStringValue(invocation)) {
              collectedHints.add(PROPERTY_TODO);
            }
            return super.visitMethodInvocation(invocation, collectedHints);
          }

          @Override
          public J.Block visitBlock(J.Block nestedBlock, LinkedHashSet<String> collectedHints) {
            return nestedBlock;
          }
        }.visit(statement, hints);
        return List.copyOf(hints);
      }

      private boolean isSpinJsonStringValue(J.MethodInvocation invocation) {
        return SPIN_JSON_STRING_VALUE.matches(invocation)
            && invocation.getSelect() instanceof J.MethodInvocation property
            && SPIN_JSON_PROPERTY.matches(property)
            && !property.getArguments().isEmpty();
      }

      private Statement addCommentIfMissing(Statement statement, String text) {
        if (hasComment(statement.getComments(), text.trim())) {
          return statement;
        }
        return statement.withComments(
            Stream.concat(
                    statement.getComments().stream(),
                    Stream.of(RecipeUtils.createSimpleComment(statement, text)))
                .toList());
      }

      private boolean hasSpinImport(J.CompilationUnit compilationUnit) {
        return compilationUnit.getImports().stream()
            .anyMatch(
                anImport ->
                    anImport.getQualid().toString().startsWith("org.camunda.spin."));
      }

      private boolean hasComment(List<Comment> comments, String marker) {
        return comments.stream()
            .anyMatch(
                comment ->
                    comment instanceof TextComment textComment
                        && textComment.getText().contains(marker));
      }
    };
  }
}
