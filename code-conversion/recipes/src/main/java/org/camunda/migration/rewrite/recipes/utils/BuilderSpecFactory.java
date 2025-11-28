package org.camunda.migration.rewrite.recipes.utils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openrewrite.java.MethodMatcher;

public class BuilderSpecFactory {

  public static List<ReplacementUtils.BuilderReplacementSpec> createBuilderSpecs(
      String methodPattern,
      String baseMethodName,
      List<String> matchableMethodNames,
      Map<String, String> extractableMethodNames,
      String prefix,
      String infix,
      String suffix,
      String returnTypeFqn,
      List<String> additionalTextComments) {
    List<List<String>> allOrderedCombinations =
        generateAllOrderedCombinations(baseMethodName, matchableMethodNames);

    return allOrderedCombinations.stream()
        .map(
            matches -> {
              List<String> intersection =
                  matches.stream().filter(extractableMethodNames::containsKey).toList();

              List<String> nonExtractables =
                  matches.stream()
                      .filter(name -> !extractableMethodNames.containsKey(name))
                      .toList();

              return new ReplacementUtils.BuilderReplacementSpec(
                  new MethodMatcher(methodPattern),
                  new HashSet<>(matches),
                  intersection,
                  RecipeUtils.createSimpleJavaTemplate(
                      prefix
                          + extractableMethodNames.get(intersection.get(0))
                          + (infix.isEmpty() ? "" : "\n\t")
                          + infix
                          + (intersection.size() > 1
                              ? intersection.stream()
                                  .skip(1)
                                  .map(extractableMethodNames::get) // get value from map
                                  .collect(Collectors.joining("\n\t", "\n\t", "\n\t"))
                              : "\n\t")
                          + suffix),
                  RecipeUtils.createSimpleIdentifier(
                      "camundaClient", "io.camunda.client.CamundaClient"),
                  returnTypeFqn,
                  ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
                  Stream.concat(
                          nonExtractables.stream()
                              .map(methodName -> " " + methodName + " was removed"),
                          additionalTextComments.stream())
                      .toList());
            })
        .toList();
  }

  public static List<ReplacementUtils.BuilderReplacementSpec> createBuilderFilterSpecs(
      String methodPattern,
      String baseMethodName,
      List<String> matchableMethodNames,
      Map<String, String> extractableMethodNames,
      String prefix,
      String suffix,
      String returnTypeFqn,
      List<String> additionalTextComments,
      Map<String, String> maybeImport) {
    List<List<String>> allOrderedCombinations =
        generateAllOrderedCombinations(baseMethodName, matchableMethodNames);

    return allOrderedCombinations.stream()
        .map(
            matches -> {
              List<String> intersection =
                  matches.stream().filter(extractableMethodNames::containsKey).toList();

              List<String> nonExtractables =
                  matches.stream()
                      .filter(name -> !extractableMethodNames.containsKey(name))
                      .toList();

              List<String> imports =
                  intersection.stream()
                      .map(maybeImport::get) // lookup each key in the map
                      .filter(Objects::nonNull) // optional: skip keys that aren't present
                      .toList();

              String javaTemplateCode =
                  prefix
                      + (!intersection.isEmpty()
                          ? ".filter(filter -> filter"
                              + intersection.stream()
                                  .map(extractableMethodNames::get) // get value from map
                                  .collect(Collectors.joining("\n\t"))
                              + ")\n\t"
                          : "")
                      + suffix;

              return new ReplacementUtils.BuilderReplacementSpec(
                  new MethodMatcher(methodPattern),
                  new HashSet<>(matches),
                  intersection,
                  imports.isEmpty()
                      ? RecipeUtils.createSimpleJavaTemplate(javaTemplateCode)
                      : RecipeUtils.createSimpleJavaTemplate(
                          javaTemplateCode, imports.toArray(new String[0])),
                  RecipeUtils.createSimpleIdentifier(
                      "camundaClient", "io.camunda.client.CamundaClient"),
                  returnTypeFqn,
                  ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
                  Stream.concat(
                          nonExtractables.stream()
                              .map(methodName -> " " + methodName + " was removed"),
                          additionalTextComments.stream())
                      .toList(),
                  Collections.emptyList(),
                  imports);
            })
        .toList();
  }

  public static List<List<String>> generateAllOrderedCombinations(
      String baseMethodName, List<String> matchableMethodNames) {

    List<List<String>> result = new ArrayList<>();
    int n = matchableMethodNames.size();

    // Iterate over all subsets (including empty subset)
    for (int mask = 0; mask < (1 << n); mask++) {
      List<String> combo = new ArrayList<>();
      if (baseMethodName != null) {
        combo.add(baseMethodName);
      }
      for (int i = 0; i < n; i++) {
        if ((mask & (1 << i)) != 0) {
          combo.add(matchableMethodNames.get(i));
        }
      }
      result.add(combo);
    }

    return result;
  }
}
