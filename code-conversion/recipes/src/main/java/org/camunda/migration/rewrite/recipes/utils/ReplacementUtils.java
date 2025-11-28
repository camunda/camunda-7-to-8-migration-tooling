package org.camunda.migration.rewrite.recipes.utils;

import java.util.*;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;

public class ReplacementUtils {

  public interface ReplacementSpec {
    MethodMatcher matcher();

    JavaTemplate template();

    J.Identifier baseIdentifier();

    String returnTypeFqn();

    ReturnTypeStrategy returnTypeStrategy();

    List<String> textComments();

    List<String> maybeRemoveImports();

    List<String> maybeAddImports();
  }

  public record SimpleReplacementSpec(
      MethodMatcher matcher,
      JavaTemplate template,
      J.Identifier baseIdentifier,
      String returnTypeFqn,
      ReturnTypeStrategy returnTypeStrategy,
      List<NamedArg> argumentIndexes,
      List<String> textComments,
      List<String> maybeRemoveImports,
      List<String> maybeAddImports)
      implements ReplacementSpec {
    public SimpleReplacementSpec(
        MethodMatcher matcher,
        JavaTemplate template,
        J.Identifier baseIdentifier,
        String returnTypeFqn,
        ReturnTypeStrategy returnTypeStrategy,
        List<NamedArg> argumentIndexes,
        List<String> textComments) {
      this(
          matcher,
          template,
          baseIdentifier,
          returnTypeFqn,
          returnTypeStrategy,
          argumentIndexes,
          textComments,
          Collections.emptyList(), // default maybeRemoveImports
          Collections.emptyList() // default maybeAddImports
          );
    }

    public SimpleReplacementSpec(
        MethodMatcher matcher,
        JavaTemplate template,
        J.Identifier baseIdentifier,
        String returnTypeFqn,
        ReturnTypeStrategy returnTypeStrategy,
        List<NamedArg> argumentIndexes) {
      this(
          matcher,
          template,
          baseIdentifier,
          returnTypeFqn,
          returnTypeStrategy,
          argumentIndexes,
          Collections.emptyList(), // default textComments
          Collections.emptyList(), // default maybeRemoveImports
          Collections.emptyList() // default maybeAddImports
          );
    }

    public record NamedArg(String name, int index, String fqn) {
      public NamedArg(String name, int index) {
        this(name, index, null);
      }
    }
  }

  public record BuilderReplacementSpec(
      MethodMatcher matcher,
      Set<String> methodNamesToExtractParameters,
      List<String> extractedParametersToApply,
      JavaTemplate template,
      J.Identifier baseIdentifier,
      String returnTypeFqn,
      ReturnTypeStrategy returnTypeStrategy,
      List<String> textComments,
      List<String> maybeRemoveImports,
      List<String> maybeAddImports)
      implements ReplacementSpec {
    public BuilderReplacementSpec(
        MethodMatcher matcher,
        Set<String> methodNamesToExtractParameters,
        List<String> extractedParametersToApply,
        JavaTemplate template,
        J.Identifier baseIdentifier,
        String returnTypeFqn,
        ReturnTypeStrategy returnTypeStrategy,
        List<String> textComments) {
      this(
          matcher,
          methodNamesToExtractParameters,
          extractedParametersToApply,
          template,
          baseIdentifier,
          returnTypeFqn,
          returnTypeStrategy,
          textComments,
          Collections.emptyList(), // default maybeRemoveImports
          Collections.emptyList() // default maybeAddImports
          );
    }

    public BuilderReplacementSpec(
        MethodMatcher matcher,
        Set<String> methodNamesToExtractParameters,
        List<String> extractedParametersToApply,
        JavaTemplate template,
        J.Identifier baseIdentifier,
        String returnTypeFqn,
        ReturnTypeStrategy returnTypeStrategy) {
      this(
          matcher,
          methodNamesToExtractParameters,
          extractedParametersToApply,
          template,
          baseIdentifier,
          returnTypeFqn,
          returnTypeStrategy,
          Collections.emptyList(), // default textComments
          Collections.emptyList(), // default maybeRemoveImports
          Collections.emptyList() // default maybeAddImports
          );
    }
  }

  public record ReturnReplacementSpec(
          MethodMatcher matcher,
          JavaTemplate template,
          List<String> maybeRemoveImports,
          List<String> maybeAddImports) {
    public ReturnReplacementSpec(MethodMatcher matcher, JavaTemplate template) {
      this(matcher, template, Collections.emptyList(), Collections.emptyList());
    }
  }

  public record RenameReplacementSpec(MethodMatcher matcher, String newSimpleName) {}

  public enum ReturnTypeStrategy {
    /** Use a specific, provided fully qualified name. */
    USE_SPECIFIED_TYPE,
    /** Infer the type from the original code (declaration or assignment). */
    INFER_FROM_CONTEXT,
    /** No return type needed (e.g. void method or expression statement). */
    VOID
  }

  public static Object[] createArgs(
      J tree, J.Identifier baseIdentifier, List<SimpleReplacementSpec.NamedArg> argumentIndexes) {
    List<Expression> args;

    if (tree instanceof J.MethodInvocation methodInvocation) {
      args = methodInvocation.getArguments();
    } else if (tree instanceof J.NewClass newClass) {
      args = newClass.getArguments();
    } else {
      throw new IllegalArgumentException("Unsupported type: " + tree.getClass());
    }

    Object[] selectedArgs =
        argumentIndexes.stream()
            .map(
                i -> {
                  Expression expression = args.get(i.index());
                  if (i.fqn() != null) {
                    return expression.withType(JavaType.buildType(i.fqn()));
                  } else {
                    return expression;
                  }
                })
            .toArray();

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
}
