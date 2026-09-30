/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.sharedRecipes;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import io.camunda.migration.code.recipes.utils.RecipeUtils;
import io.camunda.migration.code.recipes.utils.ReplacementUtils;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

public class ReplaceTypedValueAPIRecipe extends Recipe {
  private static final String OBJECT_VALUE_FQN =
      "org.camunda.bpm.engine.variable.value.ObjectValue";
  private static final String TYPED_VALUE_PACKAGE = "org.camunda.bpm.engine.variable.value.";
  private static final String TRANSIENT_REVIEW =
      " TODO: review Camunda 7 transient variable semantics for migrated values";
  private static final String MANUAL_REVIEW =
      " TODO: migrate Camunda 7 typed-value declaration manually";
  private static final String FACTORY_REVIEW =
      " TODO: migrate Camunda 7 typed-value factory call manually";

  /** Instantiates a new instance. */
  public ReplaceTypedValueAPIRecipe() {}

  @Override
  public String getDisplayName() {
    return "Convert typed value api to java object api";
  }

  @Override
  public String getDescription() {
    return "Replaces typed value api to java object api.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        Preconditions.or(
            new UsesType<>("org.camunda.bpm.engine.variable.Variables", true),
            new UsesType<>("org.camunda.bpm.engine.variable.VariableMap", true),
            new UsesType<>("org.camunda.bpm.engine.variable.value.TypedValue", true),
            new UsesType<>("org.camunda.bpm.engine.variable.value.BooleanValue", true),
            new UsesType<>("org.camunda.bpm.engine.variable.value.ObjectValue", true),
            new UsesType<>("org.camunda.bpm.engine.variable.value.StringValue", true),
            new UsesType<>("org.camunda.bpm.engine.variable.value.IntegerValue", true),
            new UsesType<>("org.camunda.bpm.engine.variable.value.LongValue", true),
            new UsesType<>("org.camunda.bpm.engine.variable.value.ShortValue", true),
            new UsesType<>("org.camunda.bpm.engine.variable.value.DoubleValue", true),
            new UsesType<>("org.camunda.bpm.engine.variable.value.FloatValue", true),
            new UsesType<>("org.camunda.bpm.engine.variable.value.BytesValue", true),
            new UsesType<>("org.camunda.bpm.engine.variable.value.DateValue", true),
            new UsesMethod<>("org.camunda.bpm.engine.delegate.VariableScope getVariableTyped(..)"),
            new UsesMethod<>(
                "org.camunda.bpm.engine.delegate.VariableScope getVariableLocalTyped(..)"),
            new UsesMethod<>("org.camunda.bpm.client.task.ExternalTask getVariableTyped(..)"),
            new UsesMethod<>("org.camunda.bpm.client.task.ExternalTask getAllVariablesTyped(..)"),
            new UsesMethod<>("org.camunda.bpm.engine.TaskService getVariableLocalTyped(..)"),
            new UsesMethod<>("org.camunda.bpm.engine.TaskService getVariableTyped(..)"));

    return Preconditions.check(
        check,
        new JavaVisitor<ExecutionContext>() {

          private final MethodMatcher dateValueFactory =
              new MethodMatcher("org.camunda.bpm.engine.variable.Variables dateValue(..)");
          private final MethodMatcher byteArrayValueFactory =
              new MethodMatcher("org.camunda.bpm.engine.variable.Variables byteArrayValue(..)");
          private final MethodMatcher variableSetter =
              new MethodMatcher("org.camunda.bpm.engine.delegate.VariableScope setVariable(..)");
          private final List<MethodMatcher> typedVariableGetters =
              List.of(
                  new MethodMatcher(
                      "org.camunda.bpm.engine.delegate.VariableScope getVariableTyped(..)"),
                  new MethodMatcher(
                      "org.camunda.bpm.engine.delegate.VariableScope getVariableLocalTyped(..)"),
                  new MethodMatcher("org.camunda.bpm.client.task.ExternalTask getVariableTyped(..)"),
                  new MethodMatcher("org.camunda.bpm.engine.TaskService getVariableLocalTyped(..)"),
                  new MethodMatcher("org.camunda.bpm.engine.TaskService getVariableTyped(..)"));
          private final Map<JavaType.Variable, String> convertedFields = new HashMap<>();
          private final Set<JavaType.Variable> retainedValues = new HashSet<>();

          @Override
          public J visitCompilationUnit(J.CompilationUnit unit, ExecutionContext ctx) {
            convertedFields.clear();
            retainedValues.clear();
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext innerCtx) {
                if (identifier.getFieldType() != null
                    && isDateOrBytesValue(identifier.getType())
                    && !isSupportedTypedUse(getCursor(), identifier)) {
                  retainedValues.add(identifier.getFieldType());
                }
                return super.visitIdentifier(identifier, innerCtx);
              }
            }.visit(unit, ctx);
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.VariableDeclarations visitVariableDeclarations(
                  J.VariableDeclarations declarations, ExecutionContext innerCtx) {
                Object owner = getCursor().getParentTreeCursor().getParentTreeCursor().getValue();
                if (owner instanceof J.ClassDeclaration || owner instanceof J.NewClass) {
                  String mapped = mapTypedValueToNewFqn(declarations.getType());
                  boolean converted = false;
                  if (isDateOrBytesValue(declarations.getType())) {
                    converted = canConvertRawDeclarations(declarations);
                  } else if (declarations.getVariables().size() == 1) {
                    J.VariableDeclarations.NamedVariable variable =
                        declarations.getVariables().get(0);
                    Expression initializer = unwrapParentheses(variable.getInitializer());
                    boolean reassignedObjectValue =
                        TypeUtils.isOfClassType(declarations.getType(), OBJECT_VALUE_FQN)
                            && isReassigned(
                                variable.getName(), getCursor().firstEnclosing(J.Block.class));
                    converted =
                        !reassignedObjectValue
                            && (initializer instanceof J.MethodInvocation getter
                                    && matchesTypedGetter(getter)
                                || !"java.lang.Object".equals(mapped)
                                    && (initializer == null
                                        || initializer instanceof J.MethodInvocation factory
                                            && simpleMethodInvocations.stream()
                                                .anyMatch(
                                                    spec -> spec.matcher().matches(factory))));
                  }
                  if (converted) {
                    for (J.VariableDeclarations.NamedVariable variable :
                        declarations.getVariables()) {
                      if (variable.getName().getFieldType() != null) {
                        convertedFields.put(variable.getName().getFieldType(), mapped);
                      }
                    }
                  }
                }
                return super.visitVariableDeclarations(declarations, innerCtx);
              }
            }.visit(unit, ctx);
            return super.visitCompilationUnit(unit, ctx);
          }

          private final List<ReplacementUtils.SimpleReplacementSpec> simpleMethodInvocations =
              List.of(
                  new ReplacementUtils.SimpleReplacementSpec(
                      new MethodMatcher(
                          // "booleanValue(Boolean bool)"
                          "org.camunda.bpm.engine.variable.Variables booleanValue(..)"),
                      RecipeUtils.createSimpleJavaTemplate("#{any(java.lang.Boolean)}"),
                      null,
                      "java.lang.Boolean",
                      ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
                      List.of(
                          new ReplacementUtils.SimpleReplacementSpec.NamedArg("booleanValue", 0)),
                      Collections.emptyList()),
                  new ReplacementUtils.SimpleReplacementSpec(
                      new MethodMatcher(
                          // "stringValue(String string)"
                          "org.camunda.bpm.engine.variable.Variables stringValue(..)"),
                      RecipeUtils.createSimpleJavaTemplate("#{any(java.lang.Boolean)}"),
                      null,
                      "java.lang.String",
                      ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
                      List.of(
                          new ReplacementUtils.SimpleReplacementSpec.NamedArg("stringValue", 0)),
                      Collections.emptyList()),
                  new ReplacementUtils.SimpleReplacementSpec(
                      new MethodMatcher(
                          // "integerValue(Integer integer)"
                          "org.camunda.bpm.engine.variable.Variables integerValue(..)"),
                      RecipeUtils.createSimpleJavaTemplate("#{any(java.lang.Integer)}"),
                      null,
                      "java.lang.Integer",
                      ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
                      List.of(
                          new ReplacementUtils.SimpleReplacementSpec.NamedArg("integerValue", 0)),
                      Collections.emptyList()),
                  new ReplacementUtils.SimpleReplacementSpec(
                      new MethodMatcher(
                          // "longValue(Long long)"
                          "org.camunda.bpm.engine.variable.Variables longValue(..)"),
                      RecipeUtils.createSimpleJavaTemplate("#{any(java.lang.Long)}"),
                      null,
                      "java.lang.Long",
                      ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
                      List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("longValue", 0)),
                      Collections.emptyList()),
                  new ReplacementUtils.SimpleReplacementSpec(
                      new MethodMatcher(
                          // "shortValue(Short short)"
                          "org.camunda.bpm.engine.variable.Variables shortValue(..)"),
                      RecipeUtils.createSimpleJavaTemplate("#{any(java.lang.Short)}"),
                      null,
                      "java.lang.Short",
                      ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
                      List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("shortValue", 0)),
                      Collections.emptyList()),
                  new ReplacementUtils.SimpleReplacementSpec(
                      new MethodMatcher(
                          // "doubleValue(Double double)"
                          "org.camunda.bpm.engine.variable.Variables doubleValue(..)"),
                      RecipeUtils.createSimpleJavaTemplate("#{any(java.lang.Double)}"),
                      null,
                      "java.lang.Double",
                      ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
                      List.of(
                          new ReplacementUtils.SimpleReplacementSpec.NamedArg("doubleValue", 0)),
                      Collections.emptyList()),
                  new ReplacementUtils.SimpleReplacementSpec(
                      new MethodMatcher(
                          // "floatValue(Float float)"
                          "org.camunda.bpm.engine.variable.Variables floatValue(..)"),
                      RecipeUtils.createSimpleJavaTemplate("#{any(java.lang.Float)}"),
                      null,
                      "java.lang.Float",
                      ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
                      List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("floatValue", 0)),
                      Collections.emptyList()),
                  new ReplacementUtils.SimpleReplacementSpec(
                      new MethodMatcher(
                          // "fromMap(java.util.Map map)"
                          "org.camunda.bpm.engine.variable.Variables fromMap(..)"),
                      RecipeUtils.createSimpleJavaTemplate("#{any(java.lang.Map)}"),
                      null,
                      "java.util.Map<String, Object>",
                      ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
                      List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("fromMap", 0)),
                      Collections.emptyList()));

          // "org.camunda.bpm.client.task.ExternalTask getAllVariablesTyped(..)"

          private final List<ReplacementUtils.BuilderReplacementSpec> builderMethodInvocations =
              List.of(
                  new ReplacementUtils.BuilderReplacementSpec(
                      new MethodMatcher(
                          "org.camunda.bpm.engine.variable.value.builder.TypedValueBuilder create()"),
                      Set.of("objectValue"),
                      List.of("objectValue"),
                      RecipeUtils.createSimpleJavaTemplate("#{any()}"),
                      null,
                      "java.lang.Object",
                      ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
                      List.of(" type set to java.lang.Object")));

          // join specs - possible because we don't touch the method invocations
          final List<ReplacementUtils.ReplacementSpec> commonSpecs =
              Stream.concat(
                      simpleMethodInvocations.stream()
                          .map(spec -> (ReplacementUtils.ReplacementSpec) spec),
                      builderMethodInvocations.stream()
                          .map(spec -> (ReplacementUtils.ReplacementSpec) spec))
                  .toList();

          final Map<MethodMatcher, List<ReplacementUtils.BuilderReplacementSpec>> builderSpecMap =
              builderMethodInvocations.stream()
                  .collect(Collectors.groupingBy(ReplacementUtils.BuilderReplacementSpec::matcher));

          public static String mapTypedValueToNewFqn(JavaType type) {
            if (!(type instanceof JavaType.FullyQualified fqType)) {
              return "java.lang.Object"; // Default fallback
            }

            String fqn = fqType.getFullyQualifiedName();

            if (fqn.equals("org.camunda.bpm.engine.variable.value.TypedValue")) {
              return "java.lang.Object";
            }

            // Handle known subclasses like IntegerValue, StringValue, etc.
            if (fqn.startsWith(TYPED_VALUE_PACKAGE)) {
              String simpleName = fqn.substring(fqn.lastIndexOf('.') + 1);

              return switch (simpleName) {
                case "IntegerValue" -> "java.lang.Integer";
                case "StringValue" -> "java.lang.String";
                case "BooleanValue" -> "java.lang.Boolean";
                case "DoubleValue" -> "java.lang.Double";
                case "LongValue" -> "java.lang.Long";
                case "ShortValue" -> "java.lang.Short";
                case "DateValue" -> "java.util.Date";
                case "BytesValue" -> "byte[]";
                default -> "java.lang.Object"; // Fallback for unknown types
              };
            }

            if (fqn.equals("org.camunda.bpm.engine.variable.VariableMap")) {
              return "java.util.Map<String, Object>";
            }

            return "java.lang.Object";
          }

          private boolean isDateOrBytesValue(JavaType type) {
            return TypeUtils.isOfClassType(
                    type, "org.camunda.bpm.engine.variable.value.DateValue")
                || TypeUtils.isOfClassType(
                    type, "org.camunda.bpm.engine.variable.value.BytesValue");
          }

          private boolean matchesTypedGetter(J.MethodInvocation invocation) {
            return typedVariableGetters.stream().anyMatch(matcher -> matcher.matches(invocation));
          }

          private boolean matchesFactory(J.MethodInvocation invocation, JavaType type) {
            return (invocation.getArguments().size() == 1
                    || invocation.getArguments().size() == 2)
                && (TypeUtils.isOfClassType(
                            type, "org.camunda.bpm.engine.variable.value.DateValue")
                        && dateValueFactory.matches(invocation)
                    || TypeUtils.isOfClassType(
                            type, "org.camunda.bpm.engine.variable.value.BytesValue")
                        && byteArrayValueFactory.matches(invocation));
          }

          private boolean canConvertRawValue(Expression value, JavaType type) {
            value = unwrapParentheses(value);
            return value == null
                || value instanceof J.Literal literal && literal.getValue() == null
                || value instanceof J.MethodInvocation invocation
                    && (matchesFactory(invocation, type) || matchesTypedGetter(invocation));
          }

          private boolean canConvertRawDeclarations(J.VariableDeclarations declarations) {
            if (declarations.getTypeExpression() instanceof J.AnnotatedType) {
              return false;
            }
            return declarations.getVariables().stream()
                .allMatch(
                    variable ->
                        !retainedValues.contains(variable.getName().getFieldType())
                            && isDateOrBytesValue(variable.getType())
                            && canConvertRawValue(variable.getInitializer(), declarations.getType()));
          }

          private boolean isSupportedValueRead(Cursor callCursor, J.MethodInvocation getValue) {
            Cursor consumer = callCursor.getParentTreeCursor();
            while (consumer.getValue() instanceof J.Parentheses<?>) {
              consumer = consumer.getParentTreeCursor();
            }
            if (consumer.getValue() instanceof J.Return) {
              return true;
            }
            if (consumer.getValue() instanceof J.VariableDeclarations.NamedVariable) {
              J.VariableDeclarations declarations =
                  callCursor.firstEnclosing(J.VariableDeclarations.class);
              return declarations != null
                  && !(declarations.getType() instanceof JavaType.FullyQualified type
                      && type.getFullyQualifiedName().startsWith(TYPED_VALUE_PACKAGE));
            }
            if (consumer.getValue() instanceof J.Assignment assignment) {
              JavaType targetType = unwrapParentheses(assignment.getVariable()).getType();
              return !(targetType instanceof JavaType.FullyQualified type
                  && type.getFullyQualifiedName().startsWith(TYPED_VALUE_PACKAGE));
            }
            return consumer.getValue() instanceof J.MethodInvocation call
                && variableSetter.matches(call)
                && call.getArguments().size() == 2
                && call.getArguments().get(1) == getValue;
          }

          private boolean isSupportedTypedUse(Cursor cursor, J.Identifier identifier) {
            Cursor parent = cursor.getParentTreeCursor();
            if (parent.getValue() instanceof J.VariableDeclarations.NamedVariable variable
                && variable.getName() == identifier) {
              return true;
            }
            Expression reference = identifier;
            if (parent.getValue() instanceof J.FieldAccess field
                && field.getName() == identifier) {
              reference = field;
              parent = parent.getParentTreeCursor();
            }
            while (parent.getValue() instanceof J.Parentheses<?>) {
              reference = (Expression) parent.getValue();
              parent = parent.getParentTreeCursor();
            }
            if (parent.getValue() instanceof J.MethodInvocation call) {
              return call.getSelect() == reference
                      && call.getSimpleName().equals("getValue")
                      && isSupportedValueRead(parent, call)
                  || variableSetter.matches(call)
                      && call.getArguments().size() == 2
                      && call.getArguments().get(1) == reference;
            }
            return parent.getValue() instanceof J.Assignment assignment
                && assignment.getVariable() == reference
                && canConvertRawValue(assignment.getAssignment(), identifier.getType());
          }

          private boolean requiresTransientReview(J.MethodInvocation factory) {
            return factory.getArguments().size() == 2
                && !(factory.getArguments().get(1) instanceof J.Literal literal
                    && Boolean.FALSE.equals(literal.getValue()));
          }

          private void retainTypeInScope(J.VariableDeclarations declarations) {
            if (declarations.getTypeAsFullyQualified() instanceof JavaType.FullyQualified type) {
              Cursor scope = declarationScope();
              for (J.VariableDeclarations.NamedVariable variable : declarations.getVariables()) {
                scope.putMessage(variable.getSimpleName(), type.getFullyQualifiedName());
              }
            }
          }

          private J.VariableDeclarations markForManualMigration(
              J.VariableDeclarations declarations) {
            retainTypeInScope(declarations);
            if (declarations.getComments().stream()
                .anyMatch(
                    comment ->
                        comment instanceof TextComment textComment
                            && textComment.getText().contains(MANUAL_REVIEW.trim()))) {
              return declarations;
            }
            return declarations.withComments(
                Stream.concat(
                        declarations.getComments().stream(),
                        Stream.of(
                            RecipeUtils.createSimpleComment(declarations, MANUAL_REVIEW)))
                    .toList());
          }

          private J.VariableDeclarations rewriteRawDeclarations(
              J.VariableDeclarations declarations, ExecutionContext ctx) {
            String mapped = mapTypedValueToNewFqn(declarations.getType());
            StringBuilder code = new StringBuilder();
            for (J.Modifier modifier : declarations.getModifiers()) {
              code.append(modifier).append(" ");
            }
            code.append(RecipeUtils.getShortName(mapped)).append(" ");

            List<Expression> initializers = new ArrayList<>();
            boolean transientReview = false;
            boolean getterReview = false;
            for (int i = 0; i < declarations.getVariables().size(); i++) {
              J.VariableDeclarations.NamedVariable variable = declarations.getVariables().get(i);
              if (i > 0) {
                code.append(", ");
              }
              code.append(variable.getSimpleName());
              if (!isFieldDeclaration()) {
                declarationScope().putMessage(variable.getSimpleName(), mapped);
              }
              Expression initializer = variable.getInitializer();
              Expression value = unwrapParentheses(initializer);
              if (value instanceof J.MethodInvocation factory
                  && matchesFactory(factory, declarations.getType())) {
                initializer = factory.getArguments().get(0);
                transientReview |= requiresTransientReview(factory);
                maybeRemoveImport("org.camunda.bpm.engine.variable.Variables");
              } else if (value instanceof J.MethodInvocation getter
                  && matchesTypedGetter(getter)) {
                getterReview = true;
                code.append(" = (").append(RecipeUtils.getShortName(mapped)).append(") #{any()}");
                initializers.add(getter);
                continue;
              }
              if (initializer != null) {
                code.append(" = #{any()}");
                initializers.add(initializer);
              }
            }
            String[] imports = "java.util.Date".equals(mapped) ? new String[] {mapped} : new String[0];
            J.VariableDeclarations modified =
                RecipeUtils.createSimpleJavaTemplate(code.toString(), imports)
                    .apply(
                        getCursor(),
                        declarations.getCoordinates().replace(),
                        initializers.toArray());
            List<Comment> comments = new ArrayList<>(declarations.getComments());
            if (getterReview) {
              comments.add(RecipeUtils.createSimpleComment(declarations, " please check type"));
            }
            if (transientReview) {
              comments.add(RecipeUtils.createSimpleComment(declarations, TRANSIENT_REVIEW));
            }
            modified =
                modified.withLeadingAnnotations(declarations.getLeadingAnnotations())
                    .withComments(comments);
            if (imports.length > 0) {
              maybeAddImport(mapped);
            }
            maybeRemoveImport(declarations.getTypeAsFullyQualified());
            modified = (J.VariableDeclarations) super.visitVariableDeclarations(modified, ctx);
            return maybeAutoFormat(declarations, modified, ctx);
          }

          /** Visit variable declarations to replace all typedValue types */
          @Override
          public J visitVariableDeclarations(
              J.VariableDeclarations declarations, ExecutionContext ctx) {

            Object declarationParent = getCursor().getParentTreeCursor().getValue();
            if (declarationParent instanceof J.ClassDeclaration
                || getCursor().firstEnclosing(J.Block.class) == null) {
              return declarations;
            }
            if (isDateOrBytesValue(declarations.getType())) {
              if (declarationParent instanceof J.MethodDeclaration
                  || declarationParent instanceof J.Lambda.Parameters
                  || declarationParent instanceof J.ForEachLoop.Control) {
                retainTypeInScope(declarations);
                return declarations;
              }
              return canConvertRawDeclarations(declarations)
                  ? rewriteRawDeclarations(declarations, ctx)
                  : markForManualMigration(declarations);
            }
            if (declarations.getVariables().size() != 1) {
              return preserveLegacyValues(declarations);
            }

            // Analyze first variable
            J.VariableDeclarations.NamedVariable firstVar = declarations.getVariables().get(0);
            J.Identifier originalName = firstVar.getName();
            Expression originalInitializer = unwrapParentheses(firstVar.getInitializer());
            // A declaration without a value might later receive a builder we cannot unwrap.
            if (TypeUtils.isOfClassType(declarations.getType(), OBJECT_VALUE_FQN)
                && ((originalInitializer == null
                        && (declarationParent instanceof J.Block
                            || declarationParent instanceof J.ForLoop.Control))
                    || isReassigned(originalName))) {
              return preserveLegacyValues(declarations);
            }

            // work with initializer that is a method invocation
            if (originalInitializer instanceof J.MethodInvocation invocation) {

              // run through prepared migration rules
              for (ReplacementUtils.ReplacementSpec spec : commonSpecs) {

                // if match is found for the invocation, check returnTypeFqn to adjust variable
                // declaration type
                if (spec.matcher().matches(invocation)) {
                  if (spec instanceof ReplacementUtils.BuilderReplacementSpec
                      && (hasUnsupportedSerializationDataFormat(invocation)
                          || isFieldDeclaration()
                          || isReassigned(originalName))) {
                    return preserveLegacyValues(declarations);
                  }

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
                  modifiedDeclarations =
                      modifiedDeclarations.withLeadingAnnotations(
                          declarations.getLeadingAnnotations());

                  // visit method invocations
                  modifiedDeclarations =
                      (J.VariableDeclarations)
                          super.visitVariableDeclarations(modifiedDeclarations, ctx);

                  maybeRemoveImport(declarations.getTypeAsFullyQualified());

                  return maybeAutoFormat(declarations, modifiedDeclarations, ctx);
                }
              }

              boolean delegateTypedVariable =
                  new MethodMatcher(
                          "org.camunda.bpm.engine.delegate.VariableScope getVariableTyped(..)")
                      .matches(invocation);
              if (delegateTypedVariable
                  || new MethodMatcher(
                          "org.camunda.bpm.engine.delegate.VariableScope getVariableLocalTyped(..)")
                      .matches(invocation)
                  || new MethodMatcher(
                          "org.camunda.bpm.client.task.ExternalTask getVariableTyped(..)")
                      .matches(invocation)
                  || new MethodMatcher(
                          "org.camunda.bpm.client.task.ExternalTask getAllVariablesTyped(..)")
                      .matches(invocation)
                  || new MethodMatcher(
                      "org.camunda.bpm.engine.TaskService getVariableLocalTyped(..)")
                          .matches(invocation)
                  || new MethodMatcher(
                      "org.camunda.bpm.engine.TaskService getVariableTyped(..)")
                          .matches(invocation)) {

                // get modifiers
                List<J.Modifier> modifiers = declarations.getModifiers();

                String newFqn = mapTypedValueToNewFqn(originalName.getType());

                // Create simple java template to adjust variable declaration type, but keep
                // invocation as is
                J.VariableDeclarations modifiedDeclarations =
                    RecipeUtils.createSimpleJavaTemplate(
                            (modifiers == null || modifiers.isEmpty()
                                    ? ""
                                    : modifiers.stream()
                                        .map(J.Modifier::toString)
                                        .collect(Collectors.joining(" ", "", " ")))
                                + newFqn.substring(newFqn.lastIndexOf('.') + 1)
                                + " "
                                + originalName.getSimpleName()
                                + " = "
                                + (delegateTypedVariable && !newFqn.equals("java.lang.Object")
                                    ? "(" + RecipeUtils.getShortName(newFqn) + ") "
                                    : "")
                                + "#{any()}",
                            "java.lang.Object")
                        .apply(getCursor(), declarations.getCoordinates().replace(), invocation);

                maybeAddImport(newFqn);

                // record fqn of identifier for later uses
                getCursor()
                    .dropParentUntil(parent -> parent instanceof J.Block)
                    .putMessage(originalName.toString(), newFqn);

                // merge comments
                modifiedDeclarations =
                    modifiedDeclarations.withComments(
                        Stream.concat(
                                declarations.getComments().stream(),
                                Stream.of(
                                    RecipeUtils.createSimpleComment(
                                        declarations, " please check type")))
                            .toList());
                modifiedDeclarations =
                    modifiedDeclarations.withLeadingAnnotations(
                        declarations.getLeadingAnnotations());

                // visit method invocations
                modifiedDeclarations =
                    (J.VariableDeclarations)
                        super.visitVariableDeclarations(modifiedDeclarations, ctx);

                if (originalName.getType() instanceof JavaType.FullyQualified oldFqn) {
                  maybeRemoveImport(oldFqn);
                }

                return maybeAutoFormat(declarations, modifiedDeclarations, ctx);
              }
            }

            maybeRemoveImport("org.camunda.bpm.engine.variable.value.TypedValue");
            maybeRemoveImport("org.camunda.bpm.engine.variable.VariableMap");
            maybeRemoveImport("org.camunda.bpm.engine.variable.Variables");

            // createVariables replacement - one variable assumed
            // this case requires a non-iso visitor to replace one statement with a block
            // the unneeded block is subsequently removed
            if (TypeUtils.isOfType(
                declarations.getType(),
                JavaType.ShallowClass.build("org.camunda.bpm.engine.variable.VariableMap"))) {

              List<Object> putValues = new ArrayList<>();

              // collect information on entries directly put into the map on creation
              Expression current = firstVar.getInitializer();
              while (current instanceof J.MethodInvocation mi) {
                if (mi.getSimpleName().equals("putValueTyped")
                    || mi.getSimpleName().equals("putValue")) {
                  putValues.addAll(mi.getArguments());
                }
                current = mi.getSelect();
              }

              // Add necessary imports
              maybeAddImport("java.util.Map");
              maybeAddImport("java.util.HashMap");
              maybeRemoveImport("org.camunda.bpm.engine.variable.VariableMap");

              // Dynamically build the put(...) lines using the variable name
              StringBuilder mapPutLines = new StringBuilder();
              for (int i = 0; i < putValues.size(); i += 2) {
                mapPutLines.append(
                    String.format(
                        "%s.put(#{any(java.lang.String)}, #{any(java.lang.Object)});\n",
                        originalName.getSimpleName()));
              }

              // Inject everything into the final block
              String blockCode =
                  String.format(
                      """
                      {
                          Map<String, Object> %s = new HashMap<>();
                          %s
                      }
                      """,
                      originalName.getSimpleName(), mapPutLines.toString().stripTrailing());

              // record fqn of identifier for later uses
              getCursor()
                  .dropParentUntil(parent -> parent instanceof J.Block)
                  .putMessage(originalName.toString(), "java.util.Map");

              J.Block newBlock =
                  RecipeUtils.createSimpleJavaTemplate(
                          blockCode, "java.util.Map", "java.util.HashMap")
                      .apply(
                          getCursor(),
                          declarations.getCoordinates().replace(),
                          putValues.toArray(new Object[0]));

              return super.visit(newBlock, ctx);
            }

            // this replaces standalone declarations, like method parameters
            if (declarations.getTypeExpression() instanceof J.Identifier typeExpr) {

              String newFqn = null;

              // depending on the type expression, newType is set
              switch (typeExpr.getSimpleName()) {
                case "BooleanValue" -> newFqn = "java.lang.Boolean";
                case "StringValue" -> newFqn = "java.lang.String";
                case "IntegerValue" -> newFqn = "java.lang.Integer";
                case "LongValue" -> newFqn = "java.lang.Long";
                case "ShortValue" -> newFqn = "java.lang.Short";
                case "DoubleValue" -> newFqn = "java.lang.Double";
                case "FloatValue" -> newFqn = "java.lang.Float";
                case "ByteArrayValue" -> newFqn = "java.lang.Byte[]";
                case "ObjectValue" -> newFqn = "java.lang.Object";
                default -> {}
              }

              // if new fqn was set, update type expression of declaration and return declaration
              if (newFqn != null) {

                if (firstVar.getInitializer() != null) {
                  return markForManualMigration(declarations);
                }
                // record fqn of identifier for later uses
                declarationScope().putMessage(originalName.toString(), newFqn);

                maybeRemoveImport(declarations.getTypeAsFullyQualified());
                J.VariableDeclarations modifiedDeclarations =
                    RecipeUtils.createSimpleJavaTemplate(
                            newFqn.substring(newFqn.lastIndexOf('.') + 1)
                                + " "
                                + firstVar.getSimpleName(),
                            newFqn)
                        .apply(getCursor(), declarations.getCoordinates().replace());
                return maybeAutoFormat(
                    declarations,
                    modifiedDeclarations
                        .withModifiers(declarations.getModifiers())
                        .withLeadingAnnotations(declarations.getLeadingAnnotations())
                        .withComments(declarations.getComments()),
                    ctx);
              }
            }
            return isLegacyTypedValue(declarations.getType())
                ? preserveLegacyValues(declarations)
                : super.visitVariableDeclarations(declarations, ctx);
          }

          private J.Assignment retypeAssignment(J.Assignment assignment, String mapped) {
            JavaType type = JavaType.buildType(mapped);
            Expression target = assignment.getVariable().withType(type);
            if (target instanceof J.FieldAccess field) {
              target = field.withName(field.getName().withType(type));
            }
            return assignment.withVariable(target).withType(type);
          }

          private String convertedType(Expression target) {
            target = unwrapParentheses(target);
            if (target instanceof J.FieldAccess field) {
              return convertedFields.get(field.getName().getFieldType());
            }
            if (target instanceof J.Identifier identifier) {
              JavaType.Variable symbol = identifier.getFieldType();
              return symbol != null && symbol.getOwner() instanceof JavaType.FullyQualified
                  ? convertedFields.get(symbol)
                  : getCursor().getNearestMessage(identifier.getSimpleName());
            }
            return null;
          }

          private boolean isDirectConvertedRead() {
            Cursor parent = getCursor().getParentTreeCursor();
            while (parent.getValue() instanceof J.Parentheses<?>
                || parent.getValue() instanceof J.TypeCast) {
              parent = parent.getParentTreeCursor();
            }
            if (parent.getValue() instanceof J.VariableDeclarations.NamedVariable) {
              return true;
            }
            if (parent.getValue() instanceof J.Assignment assignment) {
              Expression target = unwrapParentheses(assignment.getVariable());
              if (target.getType() instanceof JavaType.FullyQualified type
                  && type.getFullyQualifiedName().startsWith(TYPED_VALUE_PACKAGE)) {
                String mapped = convertedType(target);
                return mapped != null && !mapped.startsWith(TYPED_VALUE_PACKAGE);
              }
              return true;
            }
            return false;
          }

          /** Replace initializers of assignments */
          @Override
          public J visitAssignment(J.Assignment assignment, ExecutionContext ctx) {

            Expression target = unwrapParentheses(assignment.getVariable());
            String mapped = convertedType(target);
            if (mapped != null && mapped.startsWith(TYPED_VALUE_PACKAGE)) {
              return assignment;
            }
            if (isDateOrBytesValue(target.getType())
                && !mapTypedValueToNewFqn(target.getType()).equals(mapped)) {
              return assignment;
            }

            Expression assignmentValue = assignment.getAssignment();
            Expression unwrappedAssignmentValue = unwrapParentheses(assignmentValue);
            if (!(unwrappedAssignmentValue instanceof J.MethodInvocation invocation)) {
              return super.visitAssignment(assignment, ctx);
            }

            if (mapped != null && matchesFactory(invocation, target.getType())) {
              J.Assignment modified =
                  retypeAssignment(assignment.withAssignment(invocation.getArguments().get(0)), mapped);
              if (requiresTransientReview(invocation)) {
                modified =
                    modified.withComments(
                        Stream.concat(
                                assignment.getComments().stream(),
                                Stream.of(RecipeUtils.createSimpleComment(assignment, TRANSIENT_REVIEW)))
                            .toList());
              }
              maybeRemoveImport("org.camunda.bpm.engine.variable.Variables");
              return maybeAutoFormat(
                  assignment, (J.Assignment) super.visitAssignment(modified, ctx), ctx);
            }
            if (mapped != null
                && !"java.lang.Object".equals(mapped)
                && matchesTypedGetter(invocation)) {
              String[] imports =
                  "java.util.Date".equals(mapped) ? new String[] {mapped} : new String[0];
              J.Assignment modified =
                  RecipeUtils.createSimpleJavaTemplate(
                          "#{any()} = (" + RecipeUtils.getShortName(mapped) + ") #{any()}",
                          imports)
                      .apply(
                          getCursor(), assignment.getCoordinates().replace(), target, invocation);
              return maybeAutoFormat(
                  assignment,
                  (J.Assignment) super.visitAssignment(retypeAssignment(modified, mapped), ctx),
                  ctx);
            }

            if (!(assignment.getVariable() instanceof J.Identifier originalName)) {
              if (isBuilderInvocation(invocation)) {
                return assignment;
              }
              return super.visitAssignment(assignment, ctx);
            }

            String rewrittenType = getCursor().getNearestMessage(originalName.getSimpleName());
            if (TypeUtils.isOfClassType(originalName.getType(), OBJECT_VALUE_FQN)
                && (rewrittenType == null || OBJECT_VALUE_FQN.equals(rewrittenType))) {
              return assignment;
            }

            if (isBuilderInvocation(invocation)
                && hasUnsupportedSerializationDataFormat(invocation)) {
              return assignment;
            }

            if (new MethodMatcher(
                    "org.camunda.bpm.engine.delegate.VariableScope getVariableTyped(..)")
                .matches(invocation)) {
              String newFqn = mapTypedValueToNewFqn(originalName.getType());
              if (!"java.lang.Object".equals(newFqn)) {
                J.Assignment modifiedAssignment =
                    RecipeUtils.createSimpleJavaTemplate(
                            originalName.getSimpleName()
                                + " = ("
                                + RecipeUtils.getShortName(newFqn)
                                + ") #{any()}",
                            newFqn)
                        .apply(getCursor(), assignment.getCoordinates().replace(), invocation);
                return super.visitAssignment(modifiedAssignment, ctx);
              }
            }

            // run through prepared migration rules
            for (ReplacementUtils.ReplacementSpec spec : commonSpecs) {

              // if match is found for the invocation, check returnTypeFqn to adjust variable
              // declaration type
              if (spec.matcher().matches(invocation)) {

                // Create simple java template to adjust variable declaration type, but keep
                // invocation as is
                J.Assignment modifiedAssignment =
                    RecipeUtils.createSimpleJavaTemplate(
                            originalName.getSimpleName() + " = #{any()}", spec.returnTypeFqn())
                        .apply(getCursor(), assignment.getCoordinates().replace(), assignmentValue);

                modifiedAssignment =
                    modifiedAssignment.withVariable(
                        modifiedAssignment
                            .getVariable()
                            .withType(JavaType.buildType(spec.returnTypeFqn())));
                modifiedAssignment =
                    modifiedAssignment.withType(JavaType.buildType(spec.returnTypeFqn()));

                maybeAddImport(spec.returnTypeFqn());

                // ensure comments are added here, not on method invocation
                getCursor().putMessage(invocation.getId().toString(), "comments added");

                // record fqn of identifier for later uses
                getCursor()
                    .dropParentUntil(parent -> parent instanceof J.Block)
                    .putMessage(originalName.toString(), spec.returnTypeFqn());

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

          /** Replace variableMap.put() or variableMap.putValue() method invocations */
          @Override
          public J visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {

            if (dateValueFactory.matches(invocation) || byteArrayValueFactory.matches(invocation)) {
              if (invocation.getComments().stream()
                  .anyMatch(
                      comment ->
                          comment instanceof TextComment textComment
                              && textComment.getText().contains(FACTORY_REVIEW.trim()))) {
                return invocation;
              }
              return maybeAutoFormat(
                  invocation,
                  invocation.withComments(
                      Stream.concat(
                              invocation.getComments().stream(),
                              Stream.of(RecipeUtils.createSimpleComment(invocation, FACTORY_REVIEW)))
                          .toList()),
                  ctx);
            }

            // visit simple method invocations
            for (ReplacementUtils.SimpleReplacementSpec spec : simpleMethodInvocations) {
              if (spec.matcher().matches(invocation)) {

                if (invocation.getType() instanceof JavaType.FullyQualified fqn) {
                  maybeRemoveImport(fqn);
                }

                Expression modifiedInvocation =
                    RecipeUtils.applyTemplate(
                        spec.template(),
                        invocation,
                        getCursor(),
                        spec.argumentIndexes().stream()
                            .map(i -> invocation.getArguments().get(i.index()))
                            .toArray(),
                        getCursor().getNearestMessage(invocation.getId().toString()) != null
                            ? Collections.emptyList()
                            : spec.textComments());

                if (modifiedInvocation instanceof J.MethodInvocation) {
                  modifiedInvocation =
                      (Expression)
                          super.visitMethodInvocation((J.MethodInvocation) modifiedInvocation, ctx);
                }
                return maybeAutoFormat(invocation, modifiedInvocation, ctx);
              }
            }

            // loop through builder pattern groups
            for (Map.Entry<MethodMatcher, List<ReplacementUtils.BuilderReplacementSpec>> entry :
                builderSpecMap.entrySet()) {
              MethodMatcher matcher = entry.getKey();
              if (matcher.matches(invocation)) {
                if (!canUnwrapBuilder(invocation)) {
                  return super.visitMethodInvocation(invocation, ctx);
                }
                Map<String, Expression> collectedArgs = new HashMap<>();
                Expression current = invocation.getSelect();

                // extract arguments
                while (current instanceof J.MethodInvocation mi) {
                  String name = mi.getSimpleName();
                  if (isJsonSerializationDataFormat(mi)) {
                    current = mi.getSelect();
                    continue;
                  }
                  if (!mi.getArguments().isEmpty()
                      && !(mi.getArguments().get(0) instanceof J.Empty)) {
                    collectedArgs.put(name, mi.getArguments().get(0));
                  }
                  current = mi.getSelect();
                }

                // loop through pattern options
                for (ReplacementUtils.BuilderReplacementSpec spec : entry.getValue()) {
                  if (collectedArgs.keySet().equals(spec.methodNamesToExtractParameters())) {
                    Object[] args =
                        spec.extractedParametersToApply().stream()
                            .map(collectedArgs::get)
                            .toArray();

                    if (invocation.getType() instanceof JavaType.FullyQualified fqn) {
                      maybeRemoveImport(fqn);
                    }

                    Expression modifiedInvocation =
                        RecipeUtils.applyTemplate(
                            spec.template(),
                            invocation,
                            getCursor(),
                            args,
                            getCursor().getNearestMessage(invocation.getId().toString()) != null
                                ? Collections.emptyList()
                                : spec.textComments());

                    if (modifiedInvocation instanceof J.MethodInvocation) {
                      modifiedInvocation =
                          (Expression)
                              super.visitMethodInvocation(
                                  (J.MethodInvocation) modifiedInvocation, ctx);
                    }
                    return maybeAutoFormat(invocation, modifiedInvocation, ctx);
                  }
                }
              }
            }

            maybeRemoveImport("org.camunda.bpm.engine.variable.VariableMap");

            if (invocation.getMethodType() != null
                && TypeUtils.isOfType(
                    invocation.getMethodType().getDeclaringType(),
                    JavaType.ShallowClass.build("org.camunda.bpm.engine.variable.VariableMap"))
                && (invocation.getSimpleName().equals("putValueTyped")
                    || invocation.getSimpleName().equals("putValue"))) {

              List<J.MethodInvocation> putValues = new ArrayList<>();

              Expression current = invocation;

              while (current instanceof J.MethodInvocation mi) {
                current = mi.getSelect();
                if (mi.getSimpleName().equals("putValueTyped")
                    || mi.getSimpleName().equals("putValue")) {
                  putValues.add(mi);
                }
              }

              if (current instanceof J.Identifier ident
                  && ident.getSimpleName().equals("Variables")) {
                // replace inline map creation with Map.ofEntries()

                String mapOfEntriesCode =
                    "Map.ofEntries("
                        + putValues.stream()
                            .map((put) -> "Map.entry(#{any(String)}, #{any(java.lang.Object)})")
                            .collect(Collectors.joining(", "))
                        + ")";

                JavaTemplate mapOfEntries =
                    JavaTemplate.builder(mapOfEntriesCode)
                        .javaParser(
                            JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                        .imports("java.util.Map")
                        .build();

                Collections.reverse(putValues);

                List<Expression> args = new ArrayList<>();
                for (J.MethodInvocation put : putValues) {
                  args.add(RecipeUtils.updateType(getCursor(), put.getArguments().get(0)));
                  args.add(RecipeUtils.updateType(getCursor(), put.getArguments().get(1)));
                }

                maybeAddImport("java.util.Map");

                return mapOfEntries.apply(
                    getCursor(),
                    invocation.getCoordinates().replace(),
                    args.toArray(new Object[0]));
              }

              if (!(invocation.getSelect() instanceof J.Identifier select)) {
                return super.visitMethodInvocation(invocation, ctx);
              }

              J.Identifier newIdent =
                  RecipeUtils.createSimpleIdentifier(select.getSimpleName(), "java.util.Map");

              return RecipeUtils.createSimpleJavaTemplate("#{any()}.put(#{any()}, #{any()})")
                  .apply(
                      getCursor(),
                      invocation.getCoordinates().replace(),
                      newIdent,
                      RecipeUtils.updateType(getCursor(), invocation.getArguments().get(0)),
                      RecipeUtils.updateType(getCursor(), invocation.getArguments().get(1)));
            }

            if (invocation.getSimpleName().equals("getValue")) {
              Expression select = unwrapParentheses(invocation.getSelect());
              String mapped = convertedType(select);
              if (mapped == null
                  && select instanceof J.Identifier identifier
                  && !isDateOrBytesValue(identifier.getType())) {
                mapped = getCursor().getNearestMessage(identifier.getSimpleName());
              }
              if (mapped != null
                  && !OBJECT_VALUE_FQN.equals(mapped)
                  && !mapped.startsWith(TYPED_VALUE_PACKAGE)) {
                JavaType type = JavaType.buildType(mapped);
                if (select instanceof J.FieldAccess field) {
                  select = field.withName(field.getName().withType(type)).withType(type);
                } else {
                  select = select.withType(type);
                }
                return RecipeUtils.createSimpleJavaTemplate("#{any()}")
                    .apply(getCursor(), invocation.getCoordinates().replace(), select);
              }
            }

            if (invocation.getSimpleName().equals("getVariableTyped")
                || invocation.getSimpleName().equals("getVariableLocalTyped")) {
              if (!isDirectConvertedRead()) {
                return invocation;
              }
              J.Identifier newIdent =
                  RecipeUtils.createSimpleIdentifier("getVariable", "java.lang.String");
              return invocation.withName(newIdent);
            }

            if (invocation.getSimpleName().equals("getAllVariablesTyped")) {
              J.Identifier newIdent =
                  RecipeUtils.createSimpleIdentifier("getAllVariables", "java.lang.String");
              return invocation.withName(newIdent);
            }

            return super.visitMethodInvocation(invocation, ctx);
          }

          private boolean isJsonSerializationDataFormat(J.MethodInvocation invocation) {
            if (!invocation.getSimpleName().equals("serializationDataFormat")
                || invocation.getArguments().size() != 1) {
              return false;
            }

            Expression format = invocation.getArguments().get(0);
            if (format instanceof J.Literal literal
                && literal.getValue() instanceof String value) {
              return value.equalsIgnoreCase("application/json");
            }

            if (format instanceof J.FieldAccess fieldAccess) {
              return isSerializationDataFormatsJson(fieldAccess.getName());
            }

            return format instanceof J.Identifier identifier
                && isSerializationDataFormatsJson(identifier);
          }

          private boolean hasUnsupportedSerializationDataFormat(
              J.MethodInvocation invocation) {
            Expression current = invocation;
            while (current instanceof J.MethodInvocation methodInvocation) {
              if (methodInvocation.getSimpleName().equals("serializationDataFormat")
                  && !isJsonSerializationDataFormat(methodInvocation)) {
                return true;
              }
              current = methodInvocation.getSelect();
            }
            return false;
          }

          private boolean isBuilderInvocation(J.MethodInvocation invocation) {
            return builderMethodInvocations.stream()
                .anyMatch(spec -> spec.matcher().matches(invocation));
          }

          private boolean canUnwrapBuilder(J.MethodInvocation invocation) {
            if (getCursor().firstEnclosing(J.Ternary.class) != null
                || getCursor().firstEnclosing(J.SwitchExpression.class) != null) {
              return false;
            }
            Cursor parent = getCursor().getParentTreeCursor();
            while (parent.getValue() instanceof J.Parentheses<?>) {
              parent = parent.getParentTreeCursor();
            }
            if (parent.getValue() instanceof J.VariableDeclarations.NamedVariable
                || parent.getValue() instanceof J.Assignment) {
              return getCursor().getNearestMessage(invocation.getId().toString()) != null;
            }
            if (!(parent.getValue() instanceof J.MethodInvocation call)
                || call.getSelect() == invocation
                || call.getMethodType() == null
                || (call.getSelect() != null
                    && call.getSelect().getType() instanceof JavaType.Parameterized receiver
                    && receiver.getTypeParameters().stream().anyMatch(this::isLegacyTypedValue))) {
              return false;
            }
            List<JavaType> parameterTypes = call.getMethodType().getParameterTypes();
            for (int i = 0; i < call.getArguments().size() && i < parameterTypes.size(); i++) {
              if (unwrapParentheses(call.getArguments().get(i)) == invocation) {
                return TypeUtils.isOfClassType(parameterTypes.get(i), "java.lang.Object");
              }
            }
            return false;
          }

          private boolean isLegacyTypedValue(JavaType type) {
            return TypeUtils.isOfClassType(type, OBJECT_VALUE_FQN)
                || TypeUtils.isOfClassType(
                    type, "org.camunda.bpm.engine.variable.value.TypedValue");
          }

          private boolean isFieldDeclaration() {
            Object parent = getCursor().getParentTreeCursor().getParentTreeCursor().getValue();
            return parent instanceof J.ClassDeclaration || parent instanceof J.NewClass;
          }

          private J.VariableDeclarations preserveLegacyValues(J.VariableDeclarations declarations) {
            if (isLegacyTypedValue(declarations.getType())) {
              retainTypeInScope(declarations);
            }
            return declarations;
          }

          private Cursor declarationScope() {
            Object parent = getCursor().getParentTreeCursor().getValue();
            if (parent instanceof J.MethodDeclaration) {
              return getCursor().dropParentUntil(tree -> tree instanceof J.MethodDeclaration);
            }
            if (parent instanceof J.Lambda.Parameters || parent instanceof J.Lambda) {
              return getCursor().dropParentUntil(tree -> tree instanceof J.Lambda);
            }
            if (parent instanceof J.ForEachLoop.Control) {
              return getCursor().dropParentUntil(tree -> tree instanceof J.ForEachLoop);
            }
            return getCursor().dropParentUntil(tree -> tree instanceof J.Block);
          }

          private boolean isReassigned(J.Identifier variable) {
            J.Block block =
                getCursor().getParentTreeCursor().getValue() instanceof J.MethodDeclaration method
                    ? method.getBody()
                    : getCursor().firstEnclosing(J.Block.class);
            return isReassigned(variable, block);
          }

          private boolean isReassigned(J.Identifier variable, J.Block block) {
            if (block == null) {
              return false;
            }
            Set<String> assigned = new HashSet<>();
            new JavaIsoVisitor<Set<String>>() {
              @Override
              public J.Assignment visitAssignment(J.Assignment assignment, Set<String> names) {
                Expression target = unwrapParentheses(assignment.getVariable());
                if (target instanceof J.Identifier identifier) {
                  names.add(identifier.getSimpleName());
                }
                return super.visitAssignment(assignment, names);
              }
            }.visit(block, assigned);
            return assigned.contains(variable.getSimpleName());
          }

          private boolean isSerializationDataFormatsJson(J.Identifier identifier) {
            if (!identifier.getSimpleName().equals("JSON")) {
              return false;
            }

            JavaType.Variable fieldType = identifier.getFieldType();
            return fieldType != null
                && fieldType.getOwner() instanceof JavaType.FullyQualified owner
                && owner
                    .getFullyQualifiedName()
                    .equals(
                        "org.camunda.bpm.engine.variable.Variables$SerializationDataFormats");
          }

          @Override
          public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
            JavaType.Variable symbol = identifier.getFieldType();
            if (symbol != null
                && symbol.getOwner() instanceof JavaType.FullyQualified
                && (isDateOrBytesValue(identifier.getType())
                    || convertedFields.containsKey(symbol))) {
              String mapped = convertedFields.get(symbol);
              return mapped == null
                  ? identifier
                  : identifier.withType(JavaType.buildType(mapped));
            }
            String mapped = getCursor().getNearestMessage(identifier.getSimpleName());
            if (identifier.getType() instanceof JavaType.FullyQualified type
                && type.getFullyQualifiedName().equals(mapped)) {
              return identifier;
            }
            if (OBJECT_VALUE_FQN.equals(mapped)) {
              return identifier;
            }
            return (J.Identifier) RecipeUtils.updateType(getCursor(), identifier);
          }

          /**
           * Copied from unneeded block removal recipe. Adjusted to delete block with variable
           * declaration
           */
          @Override
          public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block bl = (J.Block) super.visitBlock(block, ctx);
            J directParent = getCursor().getParentTreeCursor().getValue();
            if (directParent instanceof J.NewClass || directParent instanceof J.ClassDeclaration) {
              // If the direct parent is an initializer block or a static block, skip it
              return bl;
            }

            return maybeInlineBlock(bl, ctx);
          }

          private Expression unwrapParentheses(Expression expression) {
            while (expression instanceof J.Parentheses<?> parentheses
                && parentheses.getTree() instanceof Expression nested) {
              expression = nested;
            }
            return expression;
          }

          private J.Block maybeInlineBlock(J.Block block, ExecutionContext ctx) {
            List<Statement> statements = block.getStatements();
            if (statements.isEmpty()) {
              // Removal handled by `EmptyBlock`
              return block;
            }

            // Else perform the flattening on this block.
            Statement lastStatement = statements.get(statements.size() - 1);
            J.Block flattened =
                block.withStatements(
                    ListUtils.flatMap(
                        statements,
                        (i, stmt) -> {
                          J.Block nested;
                          if (stmt instanceof J.Try) {
                            J.Try _try = (J.Try) stmt;
                            if (_try.getResources() != null
                                || !_try.getCatches().isEmpty()
                                || _try.getFinally() == null
                                || !_try.getFinally().getStatements().isEmpty()) {
                              return stmt;
                            }
                            nested = _try.getBody();
                          } else if (stmt instanceof J.Block) {
                            nested = (J.Block) stmt;
                          } else {
                            return stmt;
                          }

                          return ListUtils.map(
                              nested.getStatements(),
                              (j, inlinedStmt) -> {
                                if (j == 0) {
                                  inlinedStmt =
                                      inlinedStmt.withPrefix(
                                          inlinedStmt
                                              .getPrefix()
                                              .withComments(
                                                  ListUtils.concatAll(
                                                      nested.getComments(),
                                                      inlinedStmt.getComments())));
                                }
                                return autoFormat(inlinedStmt, ctx, getCursor());
                              });
                        }));

            if (flattened == block) {
              return block;
            } else if (lastStatement instanceof J.Block) {
              flattened =
                  flattened.withEnd(
                      flattened
                          .getEnd()
                          .withComments(
                              ListUtils.concatAll(
                                  ((J.Block) lastStatement).getEnd().getComments(),
                                  flattened.getEnd().getComments())));
            }
            return flattened;
          }
        });
  }
}
