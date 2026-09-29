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
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeUtils;

public class MigrateSpinUsageRecipe extends Recipe {

  private static final String FILE_TODO_MARKER = "This file uses Camunda Spin";
  private static final String FILE_TODO =
      " TODO: This file uses Camunda Spin, which is not provided by the Camunda 8 process engine."
          + " Replace Spin JSON/XML handling with Jackson or standard Java XML APIs.";
  private static final String SPIN_JSON_FQN = "org.camunda.spin.json.SpinJsonNode";
  private static final MethodMatcher JSON_METHOD =
      new MethodMatcher("org.camunda.spin.Spin JSON(..)");
  private static final MethodMatcher STRING_VALUE_OF_OBJECT_METHOD =
      new MethodMatcher("java.lang.String valueOf(java.lang.Object)");
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
              collectedHints.add(jsonHint(invocation));
            }
            if (XML_METHOD.matches(invocation)) {
              collectedHints.add(xmlHint());
            }
            if (isSpinJsonStringValue(invocation)) {
              collectedHints.add(jsonPropertyHint(invocation));
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

      private String jsonHint(J.MethodInvocation invocation) {
        if (invocation.getArguments().isEmpty()) {
          return " TODO: Camunda Spin JSON(...) is not a Camunda 8 process-variable API. Review"
              + " whether the input is JSON text or a POJO/Map before choosing a Jackson"
              + " conversion.";
        }

        Expression argument = invocation.getArguments().get(0);
        if (argument instanceof J.MethodInvocation stringValueOf
            && STRING_VALUE_OF_OBJECT_METHOD.matches(stringValueOf)
            && stringValueOf.getArguments().size() == 1
            && !TypeUtils.isOfClassType(
                stringValueOf.getArguments().get(0).getType(), "java.lang.String")) {
          String object = flattenLineBreaks(stringValueOf.getArguments().get(0).toString());
          return " TODO: Camunda Spin JSON(...) receives String.valueOf("
              + object
              + "), which does not serialize a Java object as JSON. Pass "
              + object
              + " directly as a POJO/Map variable; use objectMapper.writeValueAsString("
              + object
              + ") only when a JSON string is required.";
        }

        String expression = flattenLineBreaks(argument.toString());
        if (TypeUtils.isOfClassType(argument.getType(), "java.lang.String")) {
          return " TODO: Camunda Spin JSON(...) is not a Camunda 8 process-variable API. If "
              + expression
              + " contains JSON text, use objectMapper.readValue("
              + expression
              + ", Map.class). For a POJO/Map, pass it directly as a JSON variable or use"
              + " objectMapper.writeValueAsString(pojo) when a JSON string is required.";
        }

        if (RecipeUtils.isAssignableTo(argument.getType(), "java.io.Reader")) {
          return " TODO: Camunda Spin JSON(...) is not a Camunda 8 process-variable API. Use"
              + " Jackson's Reader overload objectMapper.readValue("
              + expression
              + ", Map.class) to parse the JSON text before setting a JSON process variable.";
        }

        return " TODO: Camunda Spin JSON(...) is not a Camunda 8 process-variable API. Pass "
            + expression
            + " as a plain POJO/Map JSON variable; use objectMapper.writeValueAsString("
            + expression
            + ") only when a JSON string is required. Do not parse String.valueOf("
            + expression
            + ") as JSON.";
      }

      private String xmlHint() {
        return " TODO: Camunda 8 does not provide native XML process variables. Keep XML as a"
            + " String and parse it with standard Java XML APIs, or convert the data model to JSON.";
      }

      private String jsonPropertyHint(J.MethodInvocation stringValue) {
        J.MethodInvocation property = (J.MethodInvocation) stringValue.getSelect();
        if (property.getArguments().get(0) instanceof J.Literal literal
            && literal.getValue() instanceof String key) {
          String commentKey = escapeLineTerminators(key);
          return " TODO: Replace SpinJsonNode.prop(\""
              + commentKey
              + "\").stringValue() with JSON Map access such as map.get(\""
              + commentKey
              + "\").toString() or Jackson mapping.";
        }
        return " TODO: Replace SpinJsonNode.prop(...).stringValue() with JSON Map access or"
            + " Jackson mapping.";
      }

      private String flattenLineBreaks(String value) {
        return value.replaceAll("[\\t ]*(?:\\r\\n|\\r|\\n)+[\\t ]*", " ").trim();
      }

      private String escapeLineTerminators(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
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
