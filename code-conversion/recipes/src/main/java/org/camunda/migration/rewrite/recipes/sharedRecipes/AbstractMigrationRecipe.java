package org.camunda.migration.rewrite.recipes.sharedRecipes;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;

public abstract class AbstractMigrationRecipe extends Recipe {

  /** Instantiates a new instance. */
  public AbstractMigrationRecipe() {}

  @Override
  public String getDisplayName() {
    return "Migrates variable declarations and method invocations based on rules";
  }

  @Override
  public String getDescription() {
    return "This recipe can be used to migrate variable declarations, stand-along method invocations, and method invocations of returned variables based on rule sets. The rules are provided to the recipe by extension.";
  }

  protected abstract TreeVisitor<?, ExecutionContext> preconditions();

  protected abstract List<RecipeUtils.MethodInvocationSimpleReplacementSpec>
      simpleMethodInvocations();

  protected abstract List<RecipeUtils.MethodInvocationBuilderReplacementSpec>
      builderMethodInvocations();

  protected abstract List<RecipeUtils.MethodInvocationReturnReplacementSpec>
      returnMethodInvocations();

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    return Preconditions.check(
        preconditions(),
        new JavaIsoVisitor<ExecutionContext>() {

          // join specs - possible because we don't touch the method invocations
          final List<RecipeUtils.MethodInvocationReplacementSpec> commonSpecs =
              Stream.concat(
                      simpleMethodInvocations().stream()
                          .map(spec -> (RecipeUtils.MethodInvocationReplacementSpec) spec),
                      builderMethodInvocations().stream()
                          .map(spec -> (RecipeUtils.MethodInvocationReplacementSpec) spec))
                  .toList();

          /**
           * Variable declarations are visited. Types are adjusted appropriately. Initializers are
           * replaced by wrapper methods + class methods.
           */
          @Override
          public J.VariableDeclarations visitVariableDeclarations(
              J.VariableDeclarations declarations, ExecutionContext ctx) {

            // Analyze first variable
            J.VariableDeclarations.NamedVariable firstVar = declarations.getVariables().get(0);
            J.Identifier originalName = firstVar.getName();
            Expression originalInitializer = firstVar.getInitializer();

            // work with initializer that is a method invocation
            if (originalInitializer instanceof J.MethodInvocation invocation) {

              // run through prepared migration rules
              for (RecipeUtils.MethodInvocationReplacementSpec spec : commonSpecs) {

                // if match is found for the invocation, check returnTypeFqn to adjust variable
                // declaration type
                if (spec.matcher().matches(invocation)) {

                  // get modifiers
                  List<J.Modifier> modifiers = declarations.getModifiers();

                  // Create simple java template to adjust variable declaration type, but keep
                  // invocation as is
                  J.VariableDeclarations modifiedDeclarations =
                      RecipeUtils.createSimpleJavaTemplate(
                              (modifiers == null || modifiers.isEmpty()
                                      ? ""
                                      : modifiers.stream()
                                          .map(J.Modifier::toString)
                                          .collect(Collectors.joining(" ", "", " ")))
                                  + spec.returnTypeFqn()
                                      .substring(spec.returnTypeFqn().lastIndexOf('.') + 1)
                                  + " "
                                  + originalName.getSimpleName()
                                  + " = #{any()}",
                              spec.returnTypeFqn())
                          .apply(getCursor(), declarations.getCoordinates().replace(), invocation);

                  maybeAddImport(spec.returnTypeFqn());

                  // ensure comments are added here, not on method invocation
                  getCursor().putMessage(invocation.getId().toString(), "comments added");

                  // record fqn of identifier for later uses
                  getCursor()
                      .dropParentUntil(parent -> parent instanceof J.Block)
                      .putMessage(originalName.toString(), spec.returnTypeFqn());

                  // merge comments
                  modifiedDeclarations =
                      modifiedDeclarations.withComments(
                          Stream.concat(
                                  declarations.getComments().stream(),
                                  spec.textComments().stream()
                                      .map(
                                          text ->
                                              RecipeUtils.createSimpleComment(declarations, text)))
                              .toList());

                  // visit method invocations
                  modifiedDeclarations = super.visitVariableDeclarations(modifiedDeclarations, ctx);

                  maybeRemoveImport(declarations.getTypeAsFullyQualified());

                  return maybeAutoFormat(declarations, modifiedDeclarations, ctx);
                }
              }
            }

            maybeRemoveImport(declarations.getTypeAsFullyQualified());

            return super.visitVariableDeclarations(declarations, ctx);
          }

          final List<RecipeUtils.MethodInvocationSimpleReplacementSpec> simpleMethodInvocations =
              simpleMethodInvocations();

          final Map<MethodMatcher, List<RecipeUtils.MethodInvocationBuilderReplacementSpec>>
              builderSpecMap =
                  builderMethodInvocations().stream()
                      .collect(
                          Collectors.groupingBy(
                              RecipeUtils.MethodInvocationBuilderReplacementSpec::matcher));

          final List<RecipeUtils.MethodInvocationReturnReplacementSpec> returnMethodInvocations =
              returnMethodInvocations();

          /** Method invocations are visited and replaced */
          @Override
          public J.MethodInvocation visitMethodInvocation(
              J.MethodInvocation invocation, ExecutionContext ctx) {

            // visit simple method invocations
            for (RecipeUtils.MethodInvocationSimpleReplacementSpec spec : simpleMethodInvocations) {
              if (spec.matcher().matches(invocation)) {

                return maybeAutoFormat(
                    invocation,
                    (J.MethodInvocation)
                        RecipeUtils.applyTemplate(
                            spec.template(),
                            invocation,
                            getCursor(),
                            RecipeUtils.createArgs(invocation, spec.argumentIndexes()),
                            getCursor().getNearestMessage(invocation.getId().toString()) != null
                                ? Collections.emptyList()
                                : spec.textComments()),
                    ctx);
              }
            }

            // loop through builder pattern groups
            for (Map.Entry<MethodMatcher, List<RecipeUtils.MethodInvocationBuilderReplacementSpec>>
                entry : builderSpecMap.entrySet()) {
              MethodMatcher matcher = entry.getKey();
              if (matcher.matches(invocation)) {
                Map<String, Expression> collectedArgs = new HashMap<>();
                Expression current = invocation.getSelect();

                // extract arguments
                while (current instanceof J.MethodInvocation mi) {
                  String name = mi.getSimpleName();
                  if (!mi.getArguments().isEmpty()
                      && !(mi.getArguments().get(0) instanceof J.Empty)) {
                    collectedArgs.put(name, mi.getArguments().get(0));
                  }
                  current = mi.getSelect();
                }

                // loop through pattern options
                for (RecipeUtils.MethodInvocationBuilderReplacementSpec spec : entry.getValue()) {
                  if (collectedArgs.keySet().equals(spec.methodNamesToExtractParameters())) {
                    Object[] args =
                        RecipeUtils.prependCamundaClient(
                            spec.extractedParametersToApply().stream()
                                .map(collectedArgs::get)
                                .toArray());

                    return maybeAutoFormat(
                        invocation,
                        (J.MethodInvocation)
                            RecipeUtils.applyTemplate(
                                spec.template(),
                                invocation,
                                getCursor(),
                                args,
                                getCursor().getNearestMessage(invocation.getId().toString()) != null
                                    ? Collections.emptyList()
                                    : spec.textComments()),
                        ctx);
                  }
                }
              }
            }

            // migrate methods based on returned variable declaration identifier
            if (invocation.getSelect() != null
                && invocation.getSelect() instanceof J.Identifier currentSelect
                && currentSelect.getType() instanceof JavaType.FullyQualified currentFQN) {

              // get returnTypeFqn from cursor message
              String returnTypeFqn = getCursor().getNearestMessage(currentSelect.getSimpleName());

              // loop through return replacement specs
              for (RecipeUtils.MethodInvocationReturnReplacementSpec spec :
                  returnMethodInvocations) {

                // matching old identifier and method invocation
                if (spec.matcher().matches(invocation)) {

                  // create new identifier from new returnTypeFqn
                  J.Identifier newSelect =
                      RecipeUtils.createSimpleIdentifier(
                          currentSelect.getSimpleName(), returnTypeFqn);

                  maybeRemoveImport(currentFQN);

                  return maybeAutoFormat(
                      invocation,
                      (J.MethodInvocation)
                          RecipeUtils.applyTemplate(
                              spec.template(),
                              invocation,
                              getCursor(),
                              new Object[] {newSelect},
                              Collections.emptyList()),
                      ctx);
                }
              }
            }

            // no match, continue tree traversal
            return super.visitMethodInvocation(invocation, ctx);
          }

          @Override
          public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {

            return (J.Identifier) RecipeUtils.updateType(getCursor(), identifier);
          }
        });
  }
}
