package org.camunda.migration.rewrite.recipes.sharedRecipes;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.camunda.migration.rewrite.recipes.utils.ReplacementUtils;
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

  protected Predicate<Cursor> visitorSkipCondition() {
    return cursor -> false;
  }

  protected abstract List<ReplacementUtils.SimpleReplacementSpec> simpleMethodInvocations();

  protected abstract List<ReplacementUtils.BuilderReplacementSpec> builderMethodInvocations();

  protected abstract List<ReplacementUtils.ReturnReplacementSpec> returnMethodInvocations();

  protected abstract List<ReplacementUtils.RenameReplacementSpec> renameMethodInvocations();

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    return Preconditions.check(
        preconditions(),
        new JavaIsoVisitor<>() {

          // join specs - possible because we don't touch the method invocations
          final List<ReplacementUtils.ReplacementSpec> commonSpecs =
              Stream.concat(
                      simpleMethodInvocations().stream()
                          .map(spec -> (ReplacementUtils.ReplacementSpec) spec),
                      builderMethodInvocations().stream()
                          .map(spec -> (ReplacementUtils.ReplacementSpec) spec))
                  .toList();

          /**
           * Variable declarations are visited. Types are adjusted appropriately. Initializers are
           * replaced by wrapper methods + class methods.
           */
          @Override
          public J.VariableDeclarations visitVariableDeclarations(
              J.VariableDeclarations declarations, ExecutionContext ctx) {

            // test to skip visitor
            if (visitorSkipCondition().test(getCursor())) {
              return declarations;
            }

            // Analyze first variable
            J.VariableDeclarations.NamedVariable firstVar = declarations.getVariables().get(0);
            J.Identifier originalName = firstVar.getName();
            Expression originalInitializer = firstVar.getInitializer();

            // work with initializer that is a method invocation
            if (originalInitializer instanceof J.MethodInvocation invocation) {

              // run through prepared migration rules
              for (ReplacementUtils.ReplacementSpec spec : commonSpecs) {

                // if match is found for the invocation, check returnTypeFqn to adjust variable
                // declaration type
                if (spec.matcher().matches(invocation)) {

                  // nothing to do if type stays the same
                  if (spec.returnTypeStrategy()
                      == ReplacementUtils.ReturnTypeStrategy.INFER_FROM_CONTEXT) {
                    return super.visitVariableDeclarations(declarations, ctx);
                  }

                  String resolvedFqn = null;
                  switch (spec.returnTypeStrategy()) {
                    case USE_SPECIFIED_TYPE -> resolvedFqn = spec.returnTypeFqn();
                    case VOID ->
                        throw new IllegalStateException(
                            "Should not visit declarations for void strategy.");
                  }

                  // get modifiers
                  List<J.Modifier> modifiers = declarations.getModifiers();

                  // Create simple java template to adjust variable declaration type, but keep
                  // invocation as is
                  assert resolvedFqn != null;

                  String shortName = RecipeUtils.getShortName(resolvedFqn);
                  String genericLongName = RecipeUtils.getGenericLongName(resolvedFqn);

                  J.VariableDeclarations modifiedDeclarations =
                      RecipeUtils.createSimpleJavaTemplate(
                              (modifiers == null || modifiers.isEmpty()
                                      ? ""
                                      : modifiers.stream()
                                          .map(J.Modifier::toString)
                                          .collect(Collectors.joining(" ", "", " ")))
                                  + shortName
                                  + " "
                                  + originalName.getSimpleName()
                                  + " = #{any(java.lang.Object)}",
                              genericLongName)
                          .apply(getCursor(), declarations.getCoordinates().replace(), invocation);

                  maybeAddImport(genericLongName);

                  // ensure comments are added here, not on method invocation
                  getCursor().putMessage(invocation.getId().toString(), "comments added");

                  // record fqn of identifier for later uses
                  getCursor()
                      .dropParentUntil(parent -> parent instanceof J.Block)
                      .putMessage(originalName.toString(), resolvedFqn);

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

                  maybeRemoveImport(
                      RecipeUtils.getGenericLongName(
                          declarations.getTypeAsFullyQualified().toString()));

                  return maybeAutoFormat(declarations, modifiedDeclarations, ctx);
                }
              }

              // transform access to lists
              if (invocation.getSelect() instanceof J.Identifier ident
                  && invocation.getSimpleName().equals("get")
                  && getCursor().getNearestMessage(ident.getSimpleName()) != null) {

                String listMessage = getCursor().getNearestMessage(ident.getSimpleName());

                String shortName = RecipeUtils.getGenericShortName(listMessage);
                String longName = RecipeUtils.getGenericLongName(listMessage);

                J.VariableDeclarations modifiedDeclarations =
                    RecipeUtils.createSimpleJavaTemplate(
                            shortName + " " + originalName.getSimpleName() + " = #{any()}",
                            longName)
                        .apply(
                            getCursor(),
                            declarations.getCoordinates().replace(),
                            originalInitializer);

                maybeAddImport(longName);

                // record fqn of identifier for later uses
                getCursor()
                    .dropParentUntil(parent -> parent instanceof J.Block)
                    .putMessage(
                        originalName.getSimpleName(), RecipeUtils.getGenericLongName(listMessage));

                // visit method invocations
                modifiedDeclarations = super.visitVariableDeclarations(modifiedDeclarations, ctx);

                return maybeAutoFormat(declarations, modifiedDeclarations, ctx);
              }
            }

            maybeRemoveImport(declarations.getTypeAsFullyQualified());

            return super.visitVariableDeclarations(declarations, ctx);
          }

          /** Replace initializers of assignments */
          @Override
          public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {

            // test to skip visitor
            if (visitorSkipCondition().test(getCursor())) {
              return assignment;
            }

            if (!(assignment.getVariable() instanceof J.Identifier originalName)) {
              return super.visitAssignment(assignment, ctx);
            }

            if (!(assignment.getAssignment() instanceof J.MethodInvocation invocation)) {
              return super.visitAssignment(assignment, ctx);
            }

            // run through prepared migration rules
            for (ReplacementUtils.ReplacementSpec spec : commonSpecs) {

              // if match is found for the invocation, check returnTypeFqn to adjust variable
              // declaration type
              if (spec.matcher().matches(invocation)) {

                // nothing to do if type stays the same
                if (spec.returnTypeStrategy()
                    == ReplacementUtils.ReturnTypeStrategy.INFER_FROM_CONTEXT) {
                  return super.visitAssignment(assignment, ctx);
                }

                String resolvedFqn = null;
                switch (spec.returnTypeStrategy()) {
                  case USE_SPECIFIED_TYPE -> resolvedFqn = spec.returnTypeFqn();
                  case VOID ->
                      throw new IllegalStateException(
                          "Should not visit assignment for void strategy.");
                }

                // Create simple java template to adjust variable declaration type, but keep
                // invocation as is
                J.Assignment modifiedAssignment =
                    RecipeUtils.createSimpleJavaTemplate(
                            originalName.getSimpleName() + " = #{any()}", resolvedFqn)
                        .apply(getCursor(), assignment.getCoordinates().replace(), invocation);

                assert resolvedFqn != null;
                modifiedAssignment =
                    modifiedAssignment.withVariable(
                        modifiedAssignment.getVariable().withType(JavaType.buildType(resolvedFqn)));
                modifiedAssignment = modifiedAssignment.withType(JavaType.buildType(resolvedFqn));

                maybeAddImport(resolvedFqn);

                // ensure comments are added here, not on method invocation
                getCursor().putMessage(invocation.getId().toString(), "comments added");

                // record fqn of identifier for later uses
                getCursor()
                    .dropParentUntil(parent -> parent instanceof J.Block)
                    .putMessage(originalName.toString(), resolvedFqn);

                // merge comments
                modifiedAssignment =
                    modifiedAssignment.withComments(
                        Stream.concat(
                                assignment.getComments().stream(),
                                spec.textComments().stream()
                                    .map(text -> RecipeUtils.createSimpleComment(assignment, text)))
                            .toList());

                // visit method invocations
                modifiedAssignment = (J.Assignment) super.visitAssignment(modifiedAssignment, ctx);

                if (originalName.getType() instanceof JavaType.FullyQualified fqn) {
                  maybeRemoveImport(fqn);
                }

                return maybeAutoFormat(assignment, modifiedAssignment, ctx);
              }
            }
            return super.visitAssignment(assignment, ctx);
          }

          final List<ReplacementUtils.SimpleReplacementSpec> simpleMethodInvocations =
              simpleMethodInvocations();

          final Map<MethodMatcher, List<ReplacementUtils.BuilderReplacementSpec>> builderSpecMap =
              builderMethodInvocations().stream()
                  .collect(Collectors.groupingBy(ReplacementUtils.BuilderReplacementSpec::matcher));

          final List<ReplacementUtils.ReturnReplacementSpec> returnMethodInvocations =
              returnMethodInvocations();

          /** Method invocations are visited and replaced */
          @Override
          public J.MethodInvocation visitMethodInvocation(
              J.MethodInvocation invocation, ExecutionContext ctx) {

            // test to skip visitor
            if (visitorSkipCondition().test(getCursor())) {
              return invocation;
            }

            // visit simple method invocations
            for (ReplacementUtils.SimpleReplacementSpec spec : simpleMethodInvocations) {

              if (spec.matcher().matches(invocation)) {

                spec.maybeRemoveImports().forEach(this::maybeRemoveImport);
                spec.maybeAddImports().forEach(this::maybeAddImport);

                J.MethodInvocation modifiedInvocation =
                    (J.MethodInvocation)
                        RecipeUtils.applyTemplate(
                            spec.template(),
                            invocation,
                            getCursor(),
                            ReplacementUtils.createArgs(
                                invocation, spec.baseIdentifier(), spec.argumentIndexes()),
                            getCursor().getNearestMessage(invocation.getId().toString()) != null
                                ? Collections.emptyList()
                                : spec.textComments());

                return maybeAutoFormat(
                    invocation, super.visitMethodInvocation(modifiedInvocation, ctx), ctx);
              }
            }

            // loop through builder pattern groups
            for (Map.Entry<MethodMatcher, List<ReplacementUtils.BuilderReplacementSpec>> entry :
                builderSpecMap.entrySet()) {
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
                for (ReplacementUtils.BuilderReplacementSpec spec : entry.getValue()) {
                  if (collectedArgs.keySet().equals(spec.methodNamesToExtractParameters())) {

                    spec.maybeRemoveImports().forEach(this::maybeRemoveImport);
                    spec.maybeAddImports().forEach(this::maybeAddImport);

                    Object[] args =
                        ReplacementUtils.prependBaseIdentifier(
                            spec.baseIdentifier(),
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
              for (ReplacementUtils.ReturnReplacementSpec spec : returnMethodInvocations) {

                // matching old identifier and method invocation
                if (spec.matcher().matches(invocation)) {

                  // create new identifier from new returnTypeFqn
                  J.Identifier newSelect =
                      RecipeUtils.createSimpleIdentifier(
                          currentSelect.getSimpleName(), returnTypeFqn);

                  maybeRemoveImport(currentFQN);

                  spec.maybeAddImports().forEach(this::maybeAddImport);
                  spec.maybeRemoveImports().forEach(this::maybeRemoveImport);

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

            for (ReplacementUtils.RenameReplacementSpec spec : renameMethodInvocations()) {
              if (spec.matcher().matches(invocation)) {
                return super.visitMethodInvocation(
                    invocation.withName(
                        RecipeUtils.createSimpleIdentifier(
                            spec.newSimpleName(), "java.lang.String")),
                    ctx);
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
