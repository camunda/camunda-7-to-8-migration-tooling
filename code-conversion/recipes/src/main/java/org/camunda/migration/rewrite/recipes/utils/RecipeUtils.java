package org.camunda.migration.rewrite.recipes.utils;

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
}
