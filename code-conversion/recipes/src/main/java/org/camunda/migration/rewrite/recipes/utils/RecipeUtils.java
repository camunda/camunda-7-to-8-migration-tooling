package org.camunda.migration.rewrite.recipes.utils;

import java.util.*;
import java.util.stream.Stream;
import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
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

  public static J.Identifier createSimpleIdentifier(String simpleName, JavaType javaType) {
    return new J.Identifier(
        Tree.randomId(), Space.EMPTY, Markers.EMPTY, null, simpleName, javaType, null);
  }

  public static Comment createSimpleComment(Statement statement, String text) {
    return (Comment)
        new TextComment(false, text, "\n" + statement.getPrefix().getIndent(), Markers.EMPTY);
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

  public interface MethodInvocationReplacementSpec {
    MethodMatcher matcher();

    JavaTemplate template();

    J.Identifier baseIdentifier();

    String returnTypeFqn();

    ReturnTypeStrategy returnTypeStrategy();

    List<String> textComments();
  }

  public record MethodInvocationSimpleReplacementSpec(
      MethodMatcher matcher,
      JavaTemplate template,
      J.Identifier baseIdentifier,
      String returnTypeFqn,
      ReturnTypeStrategy returnTypeStrategy,
      List<NamedArg> argumentIndexes,
      List<String> textComments)
      implements MethodInvocationReplacementSpec {
    public record NamedArg(String name, int index) {}
  }

  public record MethodInvocationBuilderReplacementSpec(
      MethodMatcher matcher,
      Set<String> methodNamesToExtractParameters,
      List<String> extractedParametersToApply,
      JavaTemplate template,
      J.Identifier baseIdentifier,
      String returnTypeFqn,
      ReturnTypeStrategy returnTypeStrategy,
      List<String> textComments)
      implements MethodInvocationReplacementSpec {}

  public record MethodInvocationReturnReplacementSpec(
      MethodMatcher matcher, JavaTemplate template) {}

  public enum ReturnTypeStrategy {
    /** Use a specific, provided fully qualified name. */
    USE_SPECIFIED_TYPE,
    /** Infer the type from the original code (declaration or assignment). */
    INFER_FROM_CONTEXT,
    /** No return type needed (e.g. void method or expression statement). */
    VOID
  }

  public static Object[] createArgs(
      J tree,
      J.Identifier baseIdentifier,
      List<MethodInvocationSimpleReplacementSpec.NamedArg> argumentIndexes) {
    List<Expression> args;

    if (tree instanceof J.MethodInvocation methodInvocation) {
      args = methodInvocation.getArguments();
    } else if (tree instanceof J.NewClass newClass) {
      args = newClass.getArguments();
    } else {
      throw new IllegalArgumentException("Unsupported type: " + tree.getClass());
    }

    Object[] selectedArgs = argumentIndexes.stream().map(i -> args.get(i.index())).toArray();

    if (baseIdentifier != null) {

      return prependBaseIdentifier(baseIdentifier, selectedArgs);
    } else {
      return selectedArgs;
    }
  }

  public static Object[] prependBaseIdentifier(J.Identifier baseIdentifier, Object[] rest) {
    Object[] all = new Object[rest.length + 1];
    all[0] = baseIdentifier;
    System.arraycopy(rest, 0, all, 1, rest.length);
    return all;
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
    // .withPrefix(expression.getPrefix());
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
