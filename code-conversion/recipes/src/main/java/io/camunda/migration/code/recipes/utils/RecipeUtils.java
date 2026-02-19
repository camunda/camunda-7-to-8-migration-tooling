/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.utils;

import java.util.*;
import java.util.stream.Stream;
import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

public class RecipeUtils {

  public static J.Identifier createSimpleIdentifier(String simpleName, String javaType) {
    return new J.Identifier(
        Tree.randomId(),
        Space.EMPTY,
        Markers.EMPTY,
        null,
        simpleName,
        JavaType.ShallowClass.build(javaType),
        null);
  }

  public static Comment createSimpleComment(Statement statement, String text) {
    return new TextComment(false, text, "\n" + statement.getPrefix().getIndent(), Markers.EMPTY);
  }

  public static JavaTemplate createSimpleJavaTemplate(String code) {
    return JavaTemplate.builder(code)
        .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
        .build();
  }

  public static JavaTemplate createSimpleJavaTemplate(String code, String... imports) {
    return JavaTemplate.builder(code)
        .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
        .imports(imports)
        .build();
  }

  public static Expression applyTemplate(
      JavaTemplate template,
      Expression expression,
      Cursor cursor,
      Object[] args,
      List<String> textComments) {
    return template
        .apply(cursor, expression.getCoordinates().replace(), args)
        .withComments(
            Stream.concat(
                    expression.getComments().stream(),
                    textComments.stream()
                        .map(
                            text ->
                                (Comment)
                                    new TextComment(
                                        false,
                                        text,
                                        "\n"
                                            + (expression.getPrefix() != null
                                                ? expression.getPrefix().getIndent()
                                                : ""),
                                        Markers.EMPTY)))
                .toList());
  }

  public static Expression updateType(Cursor cursor, Expression input) {

    if (!(input instanceof J.Identifier identifier)) {
      return input;
    }

    String newFqn = cursor.getNearestMessage(identifier.getSimpleName());

    if (newFqn != null) {
      return identifier.withType(JavaType.buildType(newFqn));
    }
    return input;
  }

  public static String getShortName(String fqn) {
    if (fqn == null || fqn.isEmpty()) {
      return fqn;
    }

    int genericStart = fqn.indexOf('<');
    if (genericStart == -1) {
      // No generics, return the simple class name
      return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    String rawType = fqn.substring(0, genericStart);
    String genericPart = fqn.substring(genericStart + 1, fqn.length() - 1); // remove < and >

    String rawShort = rawType.substring(rawType.lastIndexOf('.') + 1);
    String[] genericTypes = genericPart.split("\\s*,\\s*");
    StringJoiner joiner = new StringJoiner(", ");
    for (String g : genericTypes) {
      joiner.add(g.substring(g.lastIndexOf('.') + 1));
    }

    return rawShort + "<" + joiner + ">";
  }

  public static String getGenericShortName(String fqn) {
    if (fqn == null || fqn.isEmpty()) {
      return fqn;
    }

    int genericStart = fqn.indexOf('<');
    if (genericStart == -1) {
      // No generics, return the simple class name
      return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    String genericPart = fqn.substring(genericStart + 1, fqn.length() - 1); // remove < and >

    String[] genericTypes = genericPart.split("\\s*,\\s*");
    StringJoiner joiner = new StringJoiner(", ");
    for (String g : genericTypes) {
      joiner.add(g.substring(g.lastIndexOf('.') + 1));
    }

    return joiner + "";
  }

  public static String getGenericLongName(String fqn) {
    if (fqn == null || fqn.isEmpty()) {
      return fqn;
    }

    int genericStart = fqn.indexOf('<');
    if (genericStart == -1) {
      // No generics, return the simple class name
      return fqn;
    }

    String genericPart = fqn.substring(genericStart + 1, fqn.length() - 1); // remove < and >

    String[] genericTypes = genericPart.split("\\s*,\\s*");
    StringJoiner joiner = new StringJoiner(", ");
    for (String g : genericTypes) {
      joiner.add(g);
    }

    return joiner + "";
  }

  /**
   * Extracts the outer type from a generic type string.
   * For example, "List&lt;ProcessInstance&gt;" returns "java.util.List",
   * and "java.util.List&lt;ProcessInstance&gt;" returns "java.util.List".
   * If no generic part exists, returns null.
   *
   * @param fqn the fully qualified name potentially containing generics
   * @return the outer type without generics (fully qualified), or null if not a generic type
   */
  public static String getOuterType(String fqn) {
    if (fqn == null || fqn.isEmpty()) {
      return null;
    }

    int genericStart = fqn.indexOf('<');
    if (genericStart == -1) {
      // No generics
      return null;
    }

    String outerType = fqn.substring(0, genericStart);

    // Handle common types that may not be fully qualified
    if (outerType.equals("List")) {
      return "java.util.List";
    } else if (outerType.equals("Set")) {
      return "java.util.Set";
    } else if (outerType.equals("Map")) {
      return "java.util.Map";
    } else if (outerType.equals("Collection")) {
      return "java.util.Collection";
    } else if (outerType.equals("Optional")) {
      return "java.util.Optional";
    }

    return outerType;
  }
}
