/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.sharedRecipes;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import io.camunda.migration.code.recipes.utils.MigrationMessages;
import io.camunda.migration.code.recipes.utils.RecipeUtils;
import io.camunda.migration.code.recipes.utils.ReplacementUtils;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaPrinter;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

public abstract class AbstractMigrationRecipe extends Recipe {

  private static final String QUERY_RESULT_VARIABLE_STATES_MESSAGE =
      "migration.query-result-variable-states";

  private static final class QueryResultVariable {
    private final String name;
    private final String fieldSymbol;
    private final String receiver;

    private QueryResultVariable(
        String name, String fieldSymbol, String receiver) {
      this.name = name;
      this.fieldSymbol = fieldSymbol;
      this.receiver = receiver;
    }

    private static QueryResultVariable local(String name) {
      return new QueryResultVariable(name, null, null);
    }

    private static QueryResultVariable fromDeclaration(
        J.Identifier identifier, Cursor declarationScope) {
      if (declarationScope == null
          || !(declarationScope.getValue() instanceof J.ClassDeclaration)) {
        return local(identifier.getSimpleName());
      }

      QueryResultVariable field = from(identifier);
      return field.isField()
          ? field
          : new QueryResultVariable(identifier.getSimpleName(), null, "this");
    }

    private static QueryResultVariable from(Expression expression) {
      if (expression instanceof J.Identifier identifier) {
        JavaType.Variable fieldType = identifier.getFieldType();
        return new QueryResultVariable(
            identifier.getSimpleName(),
            fieldType == null ? null : fieldType.toString(),
            null);
      }
      if (expression instanceof J.FieldAccess fieldAccess) {
        JavaType.Variable fieldType = fieldAccess.getName().getFieldType();
        return new QueryResultVariable(
            fieldAccess.getName().getSimpleName(),
            fieldType == null ? null : fieldType.toString(),
            fieldType == null
                ? fieldAccess.getTarget().printTrimmed(new JavaPrinter<>())
                : null);
      }
      return null;
    }

    private boolean isField() {
      return fieldSymbol != null || receiver != null;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof QueryResultVariable variable)
          || isField() != variable.isField()) {
        return false;
      }
      if (!isField()) {
        return name.equals(variable.name);
      }
      if (fieldSymbol != null || variable.fieldSymbol != null) {
        return Objects.equals(fieldSymbol, variable.fieldSymbol);
      }
      return name.equals(variable.name) && Objects.equals(receiver, variable.receiver);
    }

    @Override
    public int hashCode() {
      if (!isField()) {
        return name.hashCode();
      }
      return fieldSymbol != null
          ? fieldSymbol.hashCode()
          : Objects.hash(name, receiver);
    }
  }

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

  protected List<ReplacementUtils.BuilderReplacementSpec> countBuilderMethodInvocations() {
    return List.of();
  }

  protected boolean isBuilderReplacementApplicable(
      ReplacementUtils.BuilderReplacementSpec spec,
      J.MethodInvocation queryTerminal,
      Map<String, Expression> collectedArgs) {
    return true;
  }

  protected boolean shouldStopBuilderTraversal(J.MethodInvocation invocation) {
    return false;
  }

  protected J.MethodInvocation adjustBuilderReplacement(
      J.MethodInvocation replacement, J.MethodInvocation replacementTarget, Cursor cursor) {
    return replacement;
  }

  protected J.MethodInvocation afterBuilderReplacementFormat(
      J.MethodInvocation replacement, J.MethodInvocation replacementTarget, Cursor cursor) {
    return replacement;
  }

  protected String methodMigrationComment(J.MethodDeclaration method, Cursor cursor) {
    return null;
  }

  protected String classMigrationComment(J.ClassDeclaration classDeclaration, Cursor cursor) {
    return null;
  }

  protected boolean preserveVariableDeclarationType(
      J.VariableDeclarations declarations,
      J.MethodInvocation invocation,
      ReplacementUtils.ReplacementSpec spec) {
    return false;
  }

  protected boolean preserveAssignmentType(
      J.Assignment assignment,
      J.MethodInvocation invocation,
      ReplacementUtils.ReplacementSpec spec) {
    return false;
  }

  protected boolean shouldTrackQueryResultVariable(
      J.MethodInvocation invocation, ReplacementUtils.ReplacementSpec spec) {
    return false;
  }

  protected String manualMigrationComment(J.MethodInvocation invocation, Cursor cursor) {
    return null;
  }

  private void updateQueryResultVariableState(
      Cursor cursor, QueryResultVariable variable, boolean tracked) {
    if (variable == null) {
      return;
    }

    Cursor scope = findQueryResultVariableScope(cursor, variable);
    if (scope == null) {
      return;
    }

    putQueryResultVariableState(scope, variable, tracked, cursor);
  }

  private void putQueryResultVariableState(
      Cursor scope, QueryResultVariable variable, boolean tracked) {
    putQueryResultVariableState(scope, variable, tracked, null);
  }

  private void putQueryResultVariableState(
      Cursor scope, QueryResultVariable variable, boolean tracked, Cursor updateCursor) {
    Map<QueryResultVariable, Boolean> states =
        scope.getMessage(QUERY_RESULT_VARIABLE_STATES_MESSAGE);
    states = states == null ? new HashMap<>() : new HashMap<>(states);
    if (!tracked
        && Boolean.TRUE.equals(states.get(variable))
        && (variable.isField() || isConditionalAssignment(updateCursor, scope))) {
      return;
    }
    states.put(variable, tracked);
    scope.putMessage(QUERY_RESULT_VARIABLE_STATES_MESSAGE, states);
  }

  private boolean isConditionalAssignment(Cursor updateCursor, Cursor stateScope) {
    if (updateCursor == null) {
      return false;
    }

    Cursor current = updateCursor;
    while (current != null && current != stateScope) {
      Object value = current.getValue();
      if (value instanceof J.If
          || value instanceof J.ForLoop
          || value instanceof J.ForEachLoop
          || value instanceof J.WhileLoop
          || value instanceof J.DoWhileLoop
          || value instanceof J.Switch
          || value instanceof J.SwitchExpression
          || value instanceof J.Synchronized
          || value instanceof J.Ternary
          || value instanceof J.Try
          || value instanceof J.Lambda) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  private Cursor findQueryResultVariableScope(Cursor cursor, QueryResultVariable variable) {
    Cursor current = cursor;
    Cursor nearestBlock = null;
    while (current != null) {
      Object value = current.getValue();
      if (value instanceof J.Block
          || value instanceof J.MethodDeclaration
          || value instanceof J.ClassDeclaration) {
        if (nearestBlock == null) {
          if (value instanceof J.Block) {
            nearestBlock = current;
          }
        }
        Map<QueryResultVariable, Boolean> states =
            current.getMessage(QUERY_RESULT_VARIABLE_STATES_MESSAGE);
        if (states != null && states.containsKey(variable)) {
          return current;
        }
      }
      current = current.getParent();
    }

    if (variable.isField()) {
      current = cursor;
      while (current != null) {
        if (current.getValue() instanceof J.ClassDeclaration) {
          return current;
        }
        current = current.getParent();
      }
      return null;
    }

    return nearestBlock;
  }

  private Cursor findDeclarationScope(Cursor cursor) {
    Cursor current = cursor;
    while (current != null) {
      Object value = current.getValue();
      if (value instanceof J.Block) {
        Cursor parent = current.getParent();
        return parent != null && parent.getValue() instanceof J.ClassDeclaration
            ? parent
            : current;
      }
      if (value instanceof J.MethodDeclaration || value instanceof J.ClassDeclaration) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }

  protected boolean isTrackedQueryResultVariable(Expression variable, Cursor cursor) {
    QueryResultVariable queryResultVariable = resolveQueryResultVariable(variable, cursor);
    return queryResultVariable != null
        && isTrackedQueryResultVariable(queryResultVariable, cursor);
  }

  private QueryResultVariable resolveQueryResultVariable(
      Expression variable, Cursor cursor) {
    QueryResultVariable localVariable =
        variable instanceof J.Identifier identifier
            ? QueryResultVariable.local(identifier.getSimpleName())
            : null;
    if (localVariable != null && hasQueryResultVariableState(localVariable, cursor)) {
      return localVariable;
    }
    if (variable instanceof J.Identifier identifier) {
      QueryResultVariable resolvedField = QueryResultVariable.from(identifier);
      if (resolvedField != null
          && resolvedField.isField()
          && hasQueryResultVariableState(resolvedField, cursor)) {
        return resolvedField;
      }
      QueryResultVariable fieldVariable =
          findFieldVariableState(cursor, identifier.getSimpleName());
      if (fieldVariable != null) {
        return fieldVariable;
      }
    }
    return QueryResultVariable.from(variable);
  }

  private QueryResultVariable findFieldVariableState(Cursor cursor, String name) {
    Cursor current = cursor;
    QueryResultVariable untrackedField = null;
    while (current != null) {
      Map<QueryResultVariable, Boolean> states =
          current.getMessage(QUERY_RESULT_VARIABLE_STATES_MESSAGE);
      if (states != null) {
        for (QueryResultVariable variable : states.keySet()) {
          if (variable.isField() && variable.name.equals(name)) {
            if (Boolean.TRUE.equals(states.get(variable))) {
              return variable;
            }
            if (untrackedField == null) {
              untrackedField = variable;
            }
          }
        }
      }
      current = current.getParent();
    }
    return untrackedField;
  }

  private boolean hasQueryResultVariableState(
      QueryResultVariable variable, Cursor cursor) {
    Cursor current = cursor;
    while (current != null) {
      Map<QueryResultVariable, Boolean> states =
          current.getMessage(QUERY_RESULT_VARIABLE_STATES_MESSAGE);
      if (states != null && states.containsKey(variable)) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  private boolean isTrackedQueryResultVariable(
      QueryResultVariable variable, Cursor cursor) {
    Cursor current = cursor;
    while (current != null) {
      Map<QueryResultVariable, Boolean> states =
          current.getMessage(QUERY_RESULT_VARIABLE_STATES_MESSAGE);
      if (states != null && states.containsKey(variable)) {
        return Boolean.TRUE.equals(states.get(variable));
      }
      current = current.getParent();
    }
    return false;
  }

  protected abstract List<ReplacementUtils.ReturnReplacementSpec> returnMethodInvocations();

  protected abstract List<ReplacementUtils.RenameReplacementSpec> renameMethodInvocations();

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    return Preconditions.check(
        preconditions(),
        new JavaIsoVisitor<>() {

          @Override
          public J.ClassDeclaration visitClassDeclaration(
              J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
            Set<QueryResultVariable> fieldQueryResults = new HashSet<>();

            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.ClassDeclaration visitClassDeclaration(
                  J.ClassDeclaration nestedClass, ExecutionContext nestedCtx) {
                return nestedClass == classDeclaration
                    ? super.visitClassDeclaration(nestedClass, nestedCtx)
                    : nestedClass;
              }

              @Override
              public J.VariableDeclarations visitVariableDeclarations(
                  J.VariableDeclarations declarations, ExecutionContext nestedCtx) {
                Cursor declarationScope = findDeclarationScope(getCursor());
                if (declarationScope != null
                    && declarationScope.getValue() instanceof J.ClassDeclaration) {
                  for (J.VariableDeclarations.NamedVariable variable :
                      declarations.getVariables()) {
                    Expression initializer = unwrapParentheses(variable.getInitializer());
                    if (isTrackedQueryResultInitializer(initializer)) {
                      QueryResultVariable queryResultVariable =
                          QueryResultVariable.fromDeclaration(
                              variable.getName(), declarationScope);
                      if (queryResultVariable != null && queryResultVariable.isField()) {
                        fieldQueryResults.add(queryResultVariable);
                      }
                    }
                  }
                }
                return super.visitVariableDeclarations(declarations, nestedCtx);
              }

              @Override
              public J.Assignment visitAssignment(
                  J.Assignment assignment, ExecutionContext nestedCtx) {
                QueryResultVariable queryResultVariable =
                    QueryResultVariable.from(assignment.getVariable());
                Expression assignedValue = unwrapParentheses(assignment.getAssignment());
                if (queryResultVariable != null
                    && queryResultVariable.isField()
                    && isTrackedQueryResultInitializer(assignedValue)) {
                  fieldQueryResults.add(queryResultVariable);
                }
                return super.visitAssignment(assignment, nestedCtx);
              }

              private boolean isTrackedQueryResultInitializer(Expression expression) {
                return expression instanceof J.MethodInvocation invocation
                    && findReplacementSpec(invocation)
                        .filter(spec -> shouldTrackQueryResultVariable(invocation, spec))
                        .isPresent();
              }
            }.visit(classDeclaration, ctx);

            fieldQueryResults.forEach(
                field -> putQueryResultVariableState(getCursor(), field, true));
            J.ClassDeclaration modifiedClass = super.visitClassDeclaration(classDeclaration, ctx);
            String migrationComment = classMigrationComment(modifiedClass, getCursor());
            if (migrationComment == null
                || modifiedClass.getComments().stream()
                    .anyMatch(
                        comment ->
                            comment instanceof TextComment textComment
                                && textComment.getText().contains(migrationComment))) {
              return modifiedClass;
            }
            return modifiedClass.withComments(
                Stream.concat(
                        modifiedClass.getComments().stream(),
                        Stream.of(
                            new TextComment(
                                true,
                                " " + migrationComment + " ",
                                "\n" + modifiedClass.getPrefix().getIndent(),
                                Markers.EMPTY)))
                    .toList());
          }

          @Override
          public J.MethodDeclaration visitMethodDeclaration(
              J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration modifiedMethod = super.visitMethodDeclaration(method, ctx);
            String migrationComment = methodMigrationComment(modifiedMethod, getCursor());
            if (migrationComment == null
                || modifiedMethod.getComments().stream()
                    .anyMatch(
                        comment ->
                            comment instanceof TextComment textComment
                                && textComment.getText().contains(migrationComment))) {
              return modifiedMethod;
            }
            return modifiedMethod.withComments(
                Stream.concat(
                        modifiedMethod.getComments().stream(),
                        Stream.of(
                            new TextComment(
                                true,
                                " " + migrationComment + " ",
                                "\n" + modifiedMethod.getPrefix().getIndent(),
                                Markers.EMPTY)))
                    .toList());
          }

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

            Cursor declarationScope = findDeclarationScope(getCursor());
            for (J.VariableDeclarations.NamedVariable variable : declarations.getVariables()) {
              Expression initializer = unwrapParentheses(variable.getInitializer());
              boolean tracksQueryResult =
                  initializer instanceof J.MethodInvocation invocation
                      && findReplacementSpec(invocation)
                          .filter(spec -> shouldTrackQueryResultVariable(invocation, spec))
                          .isPresent();
              if (declarationScope != null) {
                putQueryResultVariableState(
                    declarationScope,
                    QueryResultVariable.fromDeclaration(variable.getName(), declarationScope),
                    tracksQueryResult);
              }
            }

            // Analyze first variable
            J.VariableDeclarations.NamedVariable firstVar = declarations.getVariables().get(0);
            J.Identifier originalName = firstVar.getName();
            Expression originalInitializer = unwrapParentheses(firstVar.getInitializer());

            // work with initializer that is a method invocation
            if (originalInitializer instanceof J.MethodInvocation invocation) {

              // run through prepared migration rules
              ReplacementUtils.ReplacementSpec matchingSpec =
                  findReplacementSpec(invocation).orElse(null);

              if (matchingSpec != null) {
                ReplacementUtils.ReplacementSpec spec = matchingSpec;

                // nothing to do if type stays the same
                if (spec.returnTypeStrategy()
                    == ReplacementUtils.ReturnTypeStrategy.INFER_FROM_CONTEXT) {
                  return super.visitVariableDeclarations(declarations, ctx);
                }

                if (preserveVariableDeclarationType(declarations, invocation, spec)) {
                  getCursor().putMessage(invocation.getId().toString(), "comments added");
                  J.VariableDeclarations modifiedDeclarations =
                      declarations.withComments(
                          Stream.concat(
                                  declarations.getComments().stream(),
                                  spec.textComments().stream()
                                      .map(
                                          text ->
                                              RecipeUtils.createSimpleComment(declarations, text)))
                              .toList());
                  modifiedDeclarations = super.visitVariableDeclarations(modifiedDeclarations, ctx);
                  return maybeAutoFormat(declarations, modifiedDeclarations, ctx);
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

                JavaType.FullyQualified originalType = declarations.getTypeAsFullyQualified();
                if (originalType != null) {
                  maybeRemoveImport(RecipeUtils.getGenericLongName(originalType.toString()));
                }

                return maybeAutoFormat(declarations, modifiedDeclarations, ctx);
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

            JavaType.FullyQualified declaredType = declarations.getTypeAsFullyQualified();
            if (declaredType != null) {
              maybeRemoveImport(declaredType);
            }

            return super.visitVariableDeclarations(declarations, ctx);
          }

          /** Replace initializers of assignments */
          @Override
          public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {

            // test to skip visitor
            if (visitorSkipCondition().test(getCursor())) {
              return assignment;
            }

            Expression originalAssignment = unwrapParentheses(assignment.getAssignment());
            QueryResultVariable assignedVariable =
                resolveQueryResultVariable(assignment.getVariable(), getCursor());
            if (!(originalAssignment instanceof J.MethodInvocation invocation)) {
              updateQueryResultVariableState(getCursor(), assignedVariable, false);
              return super.visitAssignment(assignment, ctx);
            }

            // run through prepared migration rules
            ReplacementUtils.ReplacementSpec matchingSpec =
                findReplacementSpec(invocation).orElse(null);

            updateQueryResultVariableState(
                getCursor(),
                assignedVariable,
                matchingSpec != null && shouldTrackQueryResultVariable(invocation, matchingSpec));

            if (matchingSpec != null) {
              ReplacementUtils.ReplacementSpec spec = matchingSpec;

              if (!(assignment.getVariable() instanceof J.Identifier originalName)) {
                return super.visitAssignment(assignment, ctx);
              }

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
              boolean preserveType = preserveAssignmentType(assignment, invocation, spec);
              if (preserveType) {
                modifiedAssignment =
                    modifiedAssignment.withVariable(
                        modifiedAssignment.getVariable().withType(originalName.getType()));
                modifiedAssignment = modifiedAssignment.withType(assignment.getType());
              } else {
                modifiedAssignment =
                    modifiedAssignment.withVariable(
                        modifiedAssignment.getVariable().withType(JavaType.buildType(resolvedFqn)));
                modifiedAssignment = modifiedAssignment.withType(JavaType.buildType(resolvedFqn));
              }

              maybeAddImport(resolvedFqn);

              // ensure comments are added here, not on method invocation
              getCursor().putMessage(invocation.getId().toString(), "comments added");

              // record fqn of identifier for later uses
              getCursor()
                  .dropParentUntil(parent -> parent instanceof J.Block)
                  .putMessage(
                      originalName.toString(),
                      preserveType && originalName.getType() != null
                          ? originalName.getType().toString()
                          : resolvedFqn);

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
            return super.visitAssignment(assignment, ctx);
          }

          final List<ReplacementUtils.SimpleReplacementSpec> simpleMethodInvocations =
              simpleMethodInvocations();

          final Map<MethodMatcher, List<ReplacementUtils.BuilderReplacementSpec>> builderSpecMap =
              builderMethodInvocations().stream()
                  .collect(Collectors.groupingBy(ReplacementUtils.BuilderReplacementSpec::matcher));

          final Map<MethodMatcher, List<ReplacementUtils.BuilderReplacementSpec>>
              countBuilderSpecMap =
                  countBuilderMethodInvocations().stream()
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

            String manualMigrationComment = manualMigrationComment(invocation, getCursor());
            if (manualMigrationComment != null) {
              boolean alreadyHasComment =
                  invocation.getComments().stream()
                      .anyMatch(
                          comment ->
                              comment instanceof TextComment textComment
                                  && textComment.getText().contains(manualMigrationComment));
              if (alreadyHasComment) {
                return super.visitMethodInvocation(invocation, ctx);
              }
              J.MethodInvocation modifiedInvocation = invocation.withComments(
                  Stream.concat(
                          invocation.getComments().stream(),
                          Stream.of(
                              RecipeUtils.createSimpleBlockComment(manualMigrationComment)))
                      .toList());
              return super.visitMethodInvocation(modifiedInvocation, ctx);
            }

            J.MethodInvocation countedQuery = findCountedQuery(invocation);
            if (countedQuery != null) {
              boolean countMatcherMatched = false;
              for (Map.Entry<MethodMatcher, List<ReplacementUtils.BuilderReplacementSpec>> entry :
                  countBuilderSpecMap.entrySet()) {
                if (entry.getKey().matches(countedQuery)) {
                  countMatcherMatched = true;
                  J.MethodInvocation replacement =
                      replaceBuilderInvocation(
                          invocation, countedQuery, entry.getValue(), ctx);
                  if (replacement != null) {
                    return replacement;
                  }
                }
              }
              if (countMatcherMatched) {
                return invocation;
              }
            }

            // visit simple method invocations
            for (ReplacementUtils.SimpleReplacementSpec spec : simpleMethodInvocations) {

              if (spec.matcher().matches(invocation)
                  && (spec.requiredReceiverMethodNames().isEmpty()
                      || hasAnyMethodInReceiverChain(invocation, spec.requiredReceiverMethodNames()))) {

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
            Map<MethodMatcher, List<ReplacementUtils.BuilderReplacementSpec>> specs =
                invocation.getSimpleName().equals("count")
                    ? countBuilderSpecMap
                    : builderSpecMap;
            boolean terminalMatcherMatched = false;
            for (Map.Entry<MethodMatcher, List<ReplacementUtils.BuilderReplacementSpec>> entry :
                specs.entrySet()) {
              MethodMatcher matcher = entry.getKey();
              if (matcher.matches(invocation)) {
                terminalMatcherMatched = true;
                J.MethodInvocation replacement =
                    replaceBuilderInvocation(invocation, invocation, entry.getValue(), ctx);
                if (replacement != null) {
                  return replacement;
                }
              }
            }
            if (terminalMatcherMatched
                && (invocation.getSimpleName().equals("count")
                    || shouldStopBuilderTraversal(invocation))) {
              return invocation;
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

                  // skip transformation if return type cannot be resolved - requires manual migration
                  if (returnTypeFqn == null) {
                    String variableName = currentSelect.getSimpleName();
                    String todoComment = MigrationMessages.formatUnresolvedReturnType(variableName);
                    // check if comment was already added to avoid duplicates on multiple visits
                    boolean alreadyHasComment =
                        invocation.getComments().stream()
                            .anyMatch(
                                c ->
                                    c instanceof TextComment tc
                                        && MigrationMessages.containsUnresolvedTypeMessage(
                                            tc.getText(), variableName));
                    if (alreadyHasComment) {
                      return invocation;
                    }
                    return invocation.withComments(
                        Stream.concat(
                                invocation.getComments().stream(),
                                Stream.of(RecipeUtils.createSimpleComment(invocation, todoComment)))
                            .toList());
                  }

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

          private J.MethodInvocation replaceBuilderInvocation(
              J.MethodInvocation replacementTarget,
              J.MethodInvocation queryTerminal,
              List<ReplacementUtils.BuilderReplacementSpec> specs,
              ExecutionContext ctx) {
            Map<String, Expression> collectedArgs = collectArguments(queryTerminal);

            for (ReplacementUtils.BuilderReplacementSpec spec : specs) {
              if (isBuilderReplacementApplicable(spec, queryTerminal, collectedArgs)
                  && collectedArgs.keySet().equals(spec.methodNamesToExtractParameters())
                  && matchesReceiverType(spec, queryTerminal)) {
                spec.maybeRemoveImports().forEach(this::maybeRemoveImport);
                spec.maybeAddImports().forEach(this::maybeAddImport);

                Object[] args =
                    ReplacementUtils.prependBaseIdentifier(
                        spec.baseIdentifier(),
                        spec.extractedParametersToApply().stream()
                            .map(collectedArgs::get)
                            .toArray());

                J.MethodInvocation templatedReplacement =
                    (J.MethodInvocation)
                        RecipeUtils.applyTemplate(
                            spec.template(),
                            replacementTarget,
                            getCursor(),
                            args,
                            getCursor().getNearestMessage(replacementTarget.getId().toString())
                                    != null
                                ? Collections.emptyList()
                                : spec.textComments());

                templatedReplacement =
                    adjustBuilderReplacement(templatedReplacement, replacementTarget, getCursor());

                templatedReplacement =
                    (J.MethodInvocation)
                        new JavaIsoVisitor<ExecutionContext>() {
                          @Override
                          public J.Identifier visitIdentifier(
                              J.Identifier identifier, ExecutionContext ctx) {
                            return spec.maybeAddImports().stream()
                                    .filter(
                                        importedType ->
                                            RecipeUtils.getShortName(importedType)
                                                .equals(identifier.getSimpleName()))
                                    .findFirst()
                                    .map(
                                        importedType ->
                                            identifier.withType(JavaType.buildType(importedType)))
                                    .orElse(identifier);
                          }
                        }.visit(templatedReplacement, ctx);

                return afterBuilderReplacementFormat(
                    maybeAutoFormat(replacementTarget, templatedReplacement, ctx),
                    replacementTarget,
                    getCursor());
              }
            }
            return null;
          }

          private Optional<ReplacementUtils.ReplacementSpec> findReplacementSpec(
              J.MethodInvocation invocation) {
            J.MethodInvocation countedQuery = findCountedQuery(invocation);
            if (countedQuery != null) {
              return findBuilderReplacementSpec(countedQuery, countBuilderSpecMap)
                  .map(spec -> (ReplacementUtils.ReplacementSpec) spec);
            }

            if (invocation.getSimpleName().equals("count")) {
              if (!countBuilderSpecMap.isEmpty()) {
                return findBuilderReplacementSpec(invocation, countBuilderSpecMap)
                    .map(spec -> (ReplacementUtils.ReplacementSpec) spec);
              }
            }

            return findBuilderReplacementSpec(invocation, builderSpecMap)
                .map(spec -> (ReplacementUtils.ReplacementSpec) spec)
                .or(() -> findBuilderReplacementSpecForType(invocation, builderSpecMap))
                .or(
                    () ->
                        simpleMethodInvocations.stream()
                            .filter(spec -> spec.matcher().matches(invocation))
                            .map(spec -> (ReplacementUtils.ReplacementSpec) spec)
                            .findFirst());
          }

          private Optional<ReplacementUtils.BuilderReplacementSpec> findBuilderReplacementSpec(
              J.MethodInvocation queryTerminal,
              Map<MethodMatcher, List<ReplacementUtils.BuilderReplacementSpec>> specMap) {
            Map<String, Expression> collectedArgs = collectArguments(queryTerminal);
            return specMap.entrySet().stream()
                .filter(entry -> entry.getKey().matches(queryTerminal))
                .flatMap(entry -> entry.getValue().stream())
                .filter(spec -> matchesBuilderSpec(spec, queryTerminal, collectedArgs))
                .findFirst();
          }

          private Optional<ReplacementUtils.ReplacementSpec> findBuilderReplacementSpecForType(
              J.MethodInvocation invocation,
              Map<MethodMatcher, List<ReplacementUtils.BuilderReplacementSpec>> specMap) {
            Map<String, Expression> collectedArgs = collectArguments(invocation);
            return specMap.entrySet().stream()
                .filter(entry -> entry.getKey().matches(invocation))
                .flatMap(entry -> entry.getValue().stream())
                .filter(spec -> isBuilderReplacementApplicable(spec, invocation, collectedArgs))
                .filter(spec -> collectedArgs.keySet().equals(spec.methodNamesToExtractParameters()))
                .filter(spec -> matchesReceiverType(spec, invocation))
                .sorted(Comparator.comparing(spec -> !spec.textComments().isEmpty()))
                .map(spec -> (ReplacementUtils.ReplacementSpec) spec)
                .findFirst();
          }

          private boolean matchesBuilderSpec(
              ReplacementUtils.BuilderReplacementSpec spec, J.MethodInvocation queryTerminal) {
            return matchesBuilderSpec(spec, queryTerminal, collectArguments(queryTerminal));
          }

          private boolean matchesBuilderSpec(
              ReplacementUtils.BuilderReplacementSpec spec,
              J.MethodInvocation queryTerminal,
              Map<String, Expression> collectedArgs) {
            return isBuilderReplacementApplicable(spec, queryTerminal, collectedArgs)
                && collectedArgs.keySet().equals(spec.methodNamesToExtractParameters())
                && matchesReceiverType(spec, queryTerminal);
          }

          private boolean matchesReceiverType(
              ReplacementUtils.BuilderReplacementSpec spec, J.MethodInvocation queryTerminal) {
            return spec.receiverTypeFqn()
                .map(
                    fqn ->
                        queryTerminal.getSelect() != null
                            && TypeUtils.isOfClassType(queryTerminal.getSelect().getType(), fqn))
                .orElse(true);
          }

          private Map<String, Expression> collectArguments(J.MethodInvocation invocation) {
            Map<String, Expression> collectedArgs = new HashMap<>();
            List<J.MethodInvocation> methodInvocations = new ArrayList<>();
            Expression current = unwrapParentheses(invocation.getSelect());

            while (current instanceof J.MethodInvocation mi) {
              methodInvocations.add(mi);
              current = unwrapParentheses(mi.getSelect());
            }

            Collections.reverse(methodInvocations);
            for (J.MethodInvocation mi : methodInvocations) {
              String name = mi.getSimpleName();
              if (!mi.getArguments().isEmpty()
                  && !(mi.getArguments().get(0) instanceof J.Empty)) {
                collectedArgs.put(name, mi.getArguments().get(0));
              }
            }
            return collectedArgs;
          }

          private J.MethodInvocation findCountedQuery(J.MethodInvocation invocation) {
            Expression select = unwrapParentheses(invocation.getSelect());
            if (invocation.getSimpleName().equals("size")
                && select instanceof J.MethodInvocation query) {
              return query;
            }

            if (invocation.getSimpleName().equals("count")
                && select instanceof J.MethodInvocation stream
                && stream.getSimpleName().equals("stream")
                && unwrapParentheses(stream.getSelect()) instanceof J.MethodInvocation query) {
              return query;
            }
            return null;
          }

          private Expression unwrapParentheses(Expression expression) {
            while (expression instanceof J.Parentheses<?> parentheses
                && parentheses.getTree() instanceof Expression nested) {
              expression = nested;
            }
            return expression;
          }

          private boolean hasAnyMethodInReceiverChain(
              J.MethodInvocation invocation, Set<String> methodNames) {
            Expression current = invocation.getSelect();
            while (current instanceof J.MethodInvocation mi) {
              if (methodNames.contains(mi.getSimpleName())) {
                return true;
              }
              current = mi.getSelect();
            }
            return false;
          }

          @Override
          public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {

            return (J.Identifier) RecipeUtils.updateType(getCursor(), identifier);
          }
        });
  }
}
