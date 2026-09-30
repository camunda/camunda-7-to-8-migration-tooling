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

  private static final String TRANSIENT_REVIEW =
      " TODO: review Camunda 7 transient variable semantics for migrated values";

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
          private final MethodMatcher delegateTypedGetter =
              new MethodMatcher("org.camunda.bpm.engine.delegate.VariableScope getVariableTyped(..)");
          private final MethodMatcher allVariablesTypedGetter =
              new MethodMatcher("org.camunda.bpm.client.task.ExternalTask getAllVariablesTyped(..)");
          private final List<MethodMatcher> typedVariableGetters =
              List.of(
                  delegateTypedGetter,
                  new MethodMatcher(
                      "org.camunda.bpm.engine.delegate.VariableScope getVariableLocalTyped(..)"),
                  new MethodMatcher("org.camunda.bpm.client.task.ExternalTask getVariableTyped(..)"),
                  new MethodMatcher("org.camunda.bpm.engine.TaskService getVariableLocalTyped(..)"),
                  new MethodMatcher("org.camunda.bpm.engine.TaskService getVariableTyped(..)"));
          private final Map<JavaType.Variable, String> convertedFields = new HashMap<>();
          private final Set<JavaType.Variable> retainedTypedValues = new HashSet<>();

          @Override
          public J visitCompilationUnit(J.CompilationUnit unit, ExecutionContext ctx) {
            convertedFields.clear();
            retainedTypedValues.clear();
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext innerCtx) {
                Expression target = unwrapParentheses(assignment.getVariable());
                J.Identifier name =
                    target instanceof J.FieldAccess fieldAccess
                        ? fieldAccess.getName()
                        : target instanceof J.Identifier identifier ? identifier : null;
                if (name != null
                    && name.getFieldType() != null
                    && isDateOrBytesValue(target.getType())
                    && !canRewriteTypedAssignment(assignment.getAssignment(), target.getType())) {
                  retainedTypedValues.add(name.getFieldType());
                  retainTypedReferences(assignment.getAssignment(), innerCtx);
                }
                return super.visitAssignment(assignment, innerCtx);
              }

              @Override
              public J.Return visitReturn(J.Return statement, ExecutionContext innerCtx) {
                J.MethodDeclaration method = getCursor().firstEnclosing(J.MethodDeclaration.class);
                if (method != null
                    && method.getReturnTypeExpression() != null
                    && isLegacyTypedValue(method.getReturnTypeExpression().getType())
                    && statement.getExpression() != null) {
                  retainTypedReferences(statement.getExpression(), innerCtx);
                }
                return super.visitReturn(statement, innerCtx);
              }

              @Override
              public J.MethodInvocation visitMethodInvocation(
                  J.MethodInvocation call, ExecutionContext innerCtx) {
                Expression receiver = unwrapParentheses(call.getSelect());
                if (receiver != null
                    && isDateOrBytesValue(receiver.getType())
                    && !"getValue".equals(call.getSimpleName())) {
                  retainTypedReferences(receiver, innerCtx);
                }
                retainTypedArguments(call.getArguments(), call.getMethodType(), innerCtx);
                return super.visitMethodInvocation(call, innerCtx);
              }

              @Override
              public J.MemberReference visitMemberReference(
                  J.MemberReference reference, ExecutionContext innerCtx) {
                Expression receiver = unwrapParentheses(reference.getContaining());
                if (receiver != null && isDateOrBytesValue(receiver.getType())) {
                  retainTypedReferences(receiver, innerCtx);
                }
                return super.visitMemberReference(reference, innerCtx);
              }

              @Override
              public J.TypeCast visitTypeCast(J.TypeCast cast, ExecutionContext innerCtx) {
                if (isLegacyTypedValue(cast.getType())) {
                  retainTypedReferences(cast.getExpression(), innerCtx);
                }
                return super.visitTypeCast(cast, innerCtx);
              }

              @Override
              public J.InstanceOf visitInstanceOf(J.InstanceOf test, ExecutionContext innerCtx) {
                if (test.getClazz() instanceof TypedTree type
                    && isLegacyTypedValue(type.getType())) {
                  retainTypedReferences(test.getExpression(), innerCtx);
                }
                return super.visitInstanceOf(test, innerCtx);
              }

              @Override
              public J.Ternary visitTernary(J.Ternary ternary, ExecutionContext innerCtx) {
                if (isDateOrBytesValue(ternary.getType())) {
                  retainTypedReferences(ternary, innerCtx);
                }
                return super.visitTernary(ternary, innerCtx);
              }

              @Override
              public J.SwitchExpression visitSwitchExpression(
                  J.SwitchExpression expression, ExecutionContext innerCtx) {
                if (isDateOrBytesValue(expression.getType())) {
                  retainTypedReferences(expression, innerCtx);
                }
                return super.visitSwitchExpression(expression, innerCtx);
              }

              @Override
              public J.Lambda visitLambda(J.Lambda lambda, ExecutionContext innerCtx) {
                if (lambda.getType() instanceof JavaType.Parameterized functional
                    && functional.getTypeParameters().stream()
                        .anyMatch(type -> isLegacyTypedValue(type))) {
                  retainTypedReferences(lambda.getBody(), innerCtx);
                }
                return super.visitLambda(lambda, innerCtx);
              }

              @Override
              public J.NewClass visitNewClass(J.NewClass constructor, ExecutionContext innerCtx) {
                retainTypedArguments(
                    constructor.getArguments(), constructor.getConstructorType(), innerCtx);
                return super.visitNewClass(constructor, innerCtx);
              }

              @Override
              public J.NewArray visitNewArray(J.NewArray array, ExecutionContext innerCtx) {
                if (array.getType() instanceof JavaType.Array type
                    && isLegacyTypedValue(type.getElemType())
                    && array.getInitializer() != null) {
                  for (Expression element : array.getInitializer()) {
                    retainTypedReferences(element, innerCtx);
                  }
                }
                return super.visitNewArray(array, innerCtx);
              }
            }.visit(unit, ctx);
            int previousSize;
            do {
              previousSize = convertedFields.size();
              new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.Block visitBlock(J.Block block, ExecutionContext innerCtx) {
                  if (getCursor().getParentTreeCursor().getValue()
                          instanceof J.ClassDeclaration
                      || getCursor().getParentTreeCursor().getValue() instanceof J.NewClass) {
                    for (Statement statement : block.getStatements()) {
                      if (statement instanceof J.VariableDeclarations fields
                          && canConvertField(fields, block)) {
                        String newFqn = mapTypedValueToNewFqn(fields.getType());
                        for (J.VariableDeclarations.NamedVariable field : fields.getVariables()) {
                          JavaType.Variable symbol = field.getName().getFieldType();
                          if (symbol != null) {
                            convertedFields.put(symbol, newFqn);
                          }
                        }
                      }
                    }
                  }
                  return super.visitBlock(block, innerCtx);
                }
              }.visit(unit, ctx);
            } while (convertedFields.size() != previousSize);
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
                      dateValueFactory,
                      RecipeUtils.createSimpleJavaTemplate("#{any(java.util.Date)}"),
                      null,
                      "java.util.Date",
                      ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
                      List.of(new ReplacementUtils.SimpleReplacementSpec.NamedArg("dateValue", 0)),
                      Collections.emptyList()),
                  new ReplacementUtils.SimpleReplacementSpec(
                      byteArrayValueFactory,
                      RecipeUtils.createSimpleJavaTemplate("#{any(byte[])}"),
                      null,
                      "byte[]",
                      ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
                      List.of(
                          new ReplacementUtils.SimpleReplacementSpec.NamedArg("byteArrayValue", 0)),
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

          // Typed factories need their declared type or consumer checked before replacement.
          final List<ReplacementUtils.ReplacementSpec> commonSpecs =
              Stream.concat(
                      simpleMethodInvocations.stream()
                          .filter(
                              spec ->
                                  spec.matcher() != dateValueFactory
                                      && spec.matcher() != byteArrayValueFactory)
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
            if (fqn.startsWith("org.camunda.bpm.engine.variable.value.")) {
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

          private boolean matchesTypedVariableGetter(J.MethodInvocation invocation) {
            return typedVariableGetters.stream().anyMatch(matcher -> matcher.matches(invocation));
          }

          private boolean canConvertField(J.VariableDeclarations fields, J.Block block) {
            if (fields.getTypeExpression() instanceof J.AnnotatedType) {
              return false;
            }
            String newFqn = mapTypedValueToNewFqn(fields.getType());
            if ("java.lang.Object".equals(newFqn)
                && !TypeUtils.isOfClassType(fields.getType(), OBJECT_VALUE_FQN)) {
              return false;
            }
            if (TypeUtils.isOfClassType(fields.getType(), OBJECT_VALUE_FQN)) {
              if (fields.getVariables().size() != 1) {
                return false;
              }
              J.VariableDeclarations.NamedVariable field = fields.getVariables().get(0);
              Expression initializer = unwrapParentheses(field.getInitializer());
              if (initializer == null
                  || initializer instanceof J.MethodInvocation invocation
                      && isBuilderInvocation(invocation)
                  || isReassigned(block, field.getSimpleName())) {
                return false;
              }
            }
            return (isDateOrBytesValue(fields.getType())
                    || !hasMixedTypedGetterGroup(fields))
                && !requiresManualTypedConversion(fields, fields.getType(), newFqn);
          }

          private String convertedFieldType(J.Identifier field) {
            return convertedFields.get(field.getFieldType());
          }

          private String convertedIdentifierType(J.Identifier identifier) {
            JavaType.Variable symbol = identifier.getFieldType();
            String mappedType =
                symbol != null && symbol.getOwner() instanceof JavaType.FullyQualified
                    ? convertedFields.get(symbol)
                    : getCursor().getNearestMessage(identifier.getSimpleName());
            return mappedType != null
                    && mappedType.startsWith("org.camunda.bpm.engine.variable.value.")
                ? null
                : mappedType;
          }

          private String convertedTargetType(Expression target) {
            if (target instanceof J.FieldAccess fieldAccess) {
              return convertedFieldType(fieldAccess.getName());
            }
            if (target instanceof J.Identifier identifier) {
              return convertedIdentifierType(identifier);
            }
            return null;
          }

          private boolean isDateOrBytesValue(JavaType type) {
            return TypeUtils.isOfClassType(
                    type, "org.camunda.bpm.engine.variable.value.DateValue")
                || TypeUtils.isOfClassType(
                    type, "org.camunda.bpm.engine.variable.value.BytesValue");
          }

          private void retainTypedReferences(J expression, ExecutionContext ctx) {
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext innerCtx) {
                if (identifier.getFieldType() != null
                    && isDateOrBytesValue(identifier.getType())) {
                  retainedTypedValues.add(identifier.getFieldType());
                }
                return super.visitIdentifier(identifier, innerCtx);
              }
            }.visit(expression, ctx);
          }

          private void retainTypedArguments(
              List<Expression> arguments, JavaType.Method method, ExecutionContext ctx) {
            if (arguments == null || method == null) {
              return;
            }
            List<JavaType> parameters = method.getParameterTypes();
            for (int i = 0; i < arguments.size() && i < parameters.size(); i++) {
              if (isLegacyTypedValue(parameters.get(i))) {
                retainTypedReferences(arguments.get(i), ctx);
              }
            }
          }

          private boolean requiresTransientReview(J.MethodInvocation factory) {
            return factory.getArguments().size() == 2
                && !(factory.getArguments().get(1) instanceof J.Literal literal
                    && Boolean.FALSE.equals(literal.getValue()));
          }

          private boolean isRawFactoryArgument(J.MethodInvocation factory) {
            Cursor parent = getCursor().getParentTreeCursor();
            while (parent.getValue() instanceof J.Parentheses<?>) {
              parent = parent.getParentTreeCursor();
            }
            return isRawMethodArgument(factory, parent);
          }

          private boolean isRawMethodArgument(J.MethodInvocation argument, Cursor parent) {
            if (!(parent.getValue() instanceof J.MethodInvocation call)
                || call.getSelect() == argument
                || call.getMethodType() == null
                || (call.getSelect() != null
                    && call.getSelect().getType() instanceof JavaType.Parameterized receiver
                    && receiver.getTypeParameters().stream().anyMatch(this::isLegacyTypedValue))) {
              return false;
            }
            List<JavaType> parameterTypes = call.getMethodType().getParameterTypes();
            for (int i = 0; i < call.getArguments().size() && i < parameterTypes.size(); i++) {
              if (unwrapParentheses(call.getArguments().get(i)) == argument) {
                return TypeUtils.isOfClassType(parameterTypes.get(i), "java.lang.Object");
              }
            }
            return false;
          }

          private J.MethodInvocation retainTypedFactory(J.MethodInvocation factory) {
            String hint = " TODO: migrate Camunda 7 typed-value factory call manually";
            if (factory.getComments().stream()
                .anyMatch(
                    comment ->
                        comment instanceof TextComment textComment
                            && textComment.getText().contains(hint.trim()))) {
              return factory;
            }
            return factory.withComments(
                Stream.concat(
                        factory.getComments().stream(),
                        Stream.of(RecipeUtils.createSimpleComment(factory, hint)))
                    .toList());
          }

          private Expression unwrapTypedValueFactory(Expression initializer, JavaType declaredType) {
            Expression value = unwrapParentheses(initializer);
            if (value instanceof J.MethodInvocation factory
                && (factory.getArguments().size() == 1 || factory.getArguments().size() == 2)
                && ((TypeUtils.isOfClassType(
                            declaredType, "org.camunda.bpm.engine.variable.value.DateValue")
                        && dateValueFactory.matches(factory))
                    || (TypeUtils.isOfClassType(
                            declaredType, "org.camunda.bpm.engine.variable.value.BytesValue")
                        && byteArrayValueFactory.matches(factory)))) {
              return factory.getArguments().get(0);
            }
            return initializer;
          }

          private boolean canRewriteTypedAssignment(Expression expression, JavaType declaredType) {
            Expression value = unwrapParentheses(expression);
            return value instanceof J.Literal literal && literal.getValue() == null
                || value instanceof J.MethodInvocation invocation
                    && (matchesTypedVariableGetter(invocation)
                        || unwrapTypedValueFactory(invocation, declaredType) != invocation);
          }

          private boolean canRewriteTypedInitializers(
              J.VariableDeclarations declarations, String newFqn) {
            Set<String> convertedNames = new HashSet<>();
            for (J.VariableDeclarations.NamedVariable variable : declarations.getVariables()) {
              Expression initializer = unwrapParentheses(variable.getInitializer());
              if (initializer != null
                  && !(initializer instanceof J.Literal literal && literal.getValue() == null)
                  && unwrapTypedValueFactory(
                          initializer, declarations.getTypeAsFullyQualified())
                      == initializer
                  && !(initializer instanceof J.MethodInvocation getter
                      && matchesTypedVariableGetter(getter))
                  && !(initializer instanceof J.Identifier identifier
                      && (convertedNames.contains(identifier.getSimpleName())
                          || newFqn.equals(convertedIdentifierType(identifier))))
                  && !(initializer instanceof J.FieldAccess fieldAccess
                      && newFqn.equals(convertedFieldType(fieldAccess.getName())))) {
                return false;
              }
              convertedNames.add(variable.getSimpleName());
            }
            return true;
          }

          private boolean requiresManualTypedConversion(
              J.VariableDeclarations declarations, JavaType declaredType, String newFqn) {
            return isDateOrBytesValue(declaredType)
                && (!canRewriteTypedInitializers(declarations, newFqn)
                    || declarations.getVariables().stream()
                        .anyMatch(
                            variable ->
                                retainedTypedValues.contains(variable.getName().getFieldType())));
          }

          private boolean allTypedGetterInitializers(J.VariableDeclarations declarations) {
            return declarations.getVariables().stream()
                .allMatch(
                    variable ->
                        variable.getInitializer() instanceof J.MethodInvocation getter
                            && matchesTypedVariableGetter(getter));
          }

          private boolean hasMixedTypedGetterGroup(J.VariableDeclarations declarations) {
            return declarations.getVariables().size() > 1
                && !allTypedGetterInitializers(declarations)
                && declarations.getVariables().stream()
                    .anyMatch(
                        variable ->
                            variable.getInitializer() instanceof J.MethodInvocation getter
                                && matchesTypedVariableGetter(getter));
          }

          private J.VariableDeclarations markForManualMigration(
              J.VariableDeclarations declarations) {
            if (declarations.getTypeAsFullyQualified() instanceof JavaType.FullyQualified type) {
              Cursor block = declarationScope();
              for (J.VariableDeclarations.NamedVariable variable : declarations.getVariables()) {
                block.putMessage(variable.getSimpleName(), type.getFullyQualifiedName());
              }
            }
            String hint = " TODO: migrate Camunda 7 typed-value declaration manually";
            if (declarations.getComments().stream()
                .anyMatch(
                    comment ->
                        comment instanceof TextComment textComment
                            && textComment.getText().contains(hint.trim()))) {
              return declarations;
            }
            return declarations.withComments(
                Stream.concat(
                        declarations.getComments().stream(),
                        Stream.of(RecipeUtils.createSimpleComment(declarations, hint)))
                    .toList());
          }

          private J.VariableDeclarations rewriteTypedDeclarations(
              J.VariableDeclarations declarations, String newFqn, ExecutionContext ctx) {
            StringBuilder code = new StringBuilder();
            for (J.Modifier modifier : declarations.getModifiers()) {
              code.append(modifier).append(" ");
            }
            code.append(RecipeUtils.getShortName(newFqn)).append(" ");

            List<Expression> initializers = new ArrayList<>();
            Cursor block = declarationScope();
            boolean reviewType = false;
            boolean reviewTransient = false;
            for (int i = 0; i < declarations.getVariables().size(); i++) {
              J.VariableDeclarations.NamedVariable variable = declarations.getVariables().get(i);
              if (i > 0) {
                code.append(", ");
              }
              code.append(variable.getSimpleName());
              Expression original = variable.getInitializer();
              Expression initializer =
                  unwrapTypedValueFactory(original, declarations.getTypeAsFullyQualified());
              if (initializer != original) {
                maybeRemoveImport("org.camunda.bpm.engine.variable.Variables");
                reviewTransient |=
                    requiresTransientReview((J.MethodInvocation) unwrapParentheses(original));
              } else if (unwrapParentheses(initializer) instanceof J.MethodInvocation getter
                  && (matchesTypedVariableGetter(getter)
                      || allVariablesTypedGetter.matches(getter))) {
                reviewType = true;
              }
              if (unwrapParentheses(initializer) instanceof J.FieldAccess fieldAccess
                  && newFqn.equals(convertedFieldType(fieldAccess.getName()))) {
                JavaType newType = JavaType.buildType(newFqn);
                initializer =
                    initializer instanceof J.Parentheses<?>
                        ? initializer.withType(newType)
                        : fieldAccess
                            .withName(fieldAccess.getName().withType(newType))
                            .withType(newType);
              }
              if (initializer != null) {
                code.append(" = ");
                if (unwrapParentheses(initializer) instanceof J.MethodInvocation getter
                    && matchesTypedVariableGetter(getter)
                    && !"java.lang.Object".equals(newFqn)) {
                  code.append("(").append(RecipeUtils.getShortName(newFqn)).append(") ");
                }
                code.append("#{any()}");
                initializers.add(initializer);
              }
              block.putMessage(variable.getSimpleName(), newFqn);
            }

            String importedType =
                newFqn.startsWith("java.util.Map<") ? "java.util.Map" : newFqn;
            String[] imports =
                "java.util.Date".equals(importedType) || "java.util.Map".equals(importedType)
                    ? new String[] {importedType}
                    : new String[0];
            J.VariableDeclarations modified =
                RecipeUtils.createSimpleJavaTemplate(code.toString(), imports)
                    .apply(
                        getCursor(),
                        declarations.getCoordinates().replace(),
                        initializers.toArray());
            List<Comment> comments = new ArrayList<>(declarations.getComments());
            if (reviewType) {
              comments.add(RecipeUtils.createSimpleComment(declarations, " please check type"));
            }
            if (reviewTransient) {
              comments.add(
                  RecipeUtils.createSimpleComment(declarations, TRANSIENT_REVIEW));
            }
            modified =
                modified.withLeadingAnnotations(declarations.getLeadingAnnotations())
                    .withComments(comments);
            if (imports.length > 0) {
              maybeAddImport(importedType);
            }
            maybeRemoveImport(declarations.getTypeAsFullyQualified());
            modified = (J.VariableDeclarations) super.visitVariableDeclarations(modified, ctx);
            return maybeAutoFormat(declarations, modified, ctx);
          }

          /** Visit variable declarations to replace all typedValue types */
          @Override
          public J visitVariableDeclarations(
              J.VariableDeclarations declarations, ExecutionContext ctx) {

            if (isDateOrBytesValue(declarations.getType())) {
              Object parent = getCursor().getParentTreeCursor().getValue();
              if (parent instanceof J.MethodDeclaration
                  || parent instanceof J.Lambda.Parameters
                  || parent instanceof J.Lambda
                  || parent instanceof J.ForEachLoop.Control) {
                return preserveTypedValues(declarations);
              }
              if (declarations.getTypeExpression() instanceof J.AnnotatedType) {
                return markForManualMigration(declarations);
              }
              String newFqn = mapTypedValueToNewFqn(declarations.getType());
              return requiresManualTypedConversion(declarations, declarations.getType(), newFqn)
                  ? markForManualMigration(declarations)
                  : rewriteTypedDeclarations(declarations, newFqn, ctx);
            }
            if (declarations.getVariables().size() != 1
                && TypeUtils.isOfClassType(declarations.getType(), OBJECT_VALUE_FQN)) {
              return preserveTypedValues(declarations);
            }
            if (hasMixedTypedGetterGroup(declarations)) {
              return markForManualMigration(declarations);
            }
            if (declarations.getVariables().size() != 1
                && !allTypedGetterInitializers(declarations)) {
              return declarations;
            }

            // Analyze first variable
            J.VariableDeclarations.NamedVariable firstVar = declarations.getVariables().get(0);
            J.Identifier originalName = firstVar.getName();
            Expression originalInitializer = unwrapParentheses(firstVar.getInitializer());
            // A declaration without a value might later receive a builder we cannot unwrap.
            Object declarationParent = getCursor().getParentTreeCursor().getValue();
            if (TypeUtils.isOfClassType(declarations.getType(), OBJECT_VALUE_FQN)
                && ((originalInitializer == null
                        && (declarationParent instanceof J.Block
                            || declarationParent instanceof J.ForLoop.Control))
                    || isReassigned(originalName))) {
              return preserveTypedValues(declarations);
            }

            // work with initializer that is a method invocation
            if (originalInitializer instanceof J.MethodInvocation invocation
                && unwrapTypedValueFactory(
                        originalInitializer, declarations.getTypeAsFullyQualified())
                    == originalInitializer) {

              // run through prepared migration rules
              for (ReplacementUtils.ReplacementSpec spec : commonSpecs) {

                // if match is found for the invocation, check returnTypeFqn to adjust variable
                // declaration type
                if (spec.matcher().matches(invocation)) {
                  if (spec instanceof ReplacementUtils.BuilderReplacementSpec
                      && (hasUnsupportedSerializationDataFormat(invocation)
                          || isFieldDeclaration()
                          || isReassigned(originalName))) {
                    return preserveTypedValues(declarations);
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

                  // visit method invocations
                  modifiedDeclarations =
                      (J.VariableDeclarations)
                          super.visitVariableDeclarations(modifiedDeclarations, ctx);

                  maybeRemoveImport(declarations.getTypeAsFullyQualified());

                  return maybeAutoFormat(declarations, modifiedDeclarations, ctx);
                }
              }

              if (matchesTypedVariableGetter(invocation)
                  || allVariablesTypedGetter.matches(invocation)) {
                String newFqn = mapTypedValueToNewFqn(originalName.getType());
                return declarations.getVariables().size() > 1
                        && !allTypedGetterInitializers(declarations)
                    ? markForManualMigration(declarations)
                    : rewriteTypedDeclarations(declarations, newFqn, ctx);
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

              String newFqn = mapTypedValueToNewFqn(typeExpr.getType());
              if (!"java.lang.Object".equals(newFqn)
                  || TypeUtils.isOfClassType(
                      typeExpr.getType(), OBJECT_VALUE_FQN)) {
                return rewriteTypedDeclarations(declarations, newFqn, ctx);
              }
            }
            return super.visitVariableDeclarations(declarations, ctx);
          }

          private J.Assignment retypeAssignment(J.Assignment assignment, String mappedType) {
            JavaType type = JavaType.buildType(mappedType);
            Expression target = assignment.getVariable().withType(type);
            if (target instanceof J.FieldAccess fieldAccess) {
              target = fieldAccess.withName(fieldAccess.getName().withType(type));
            }
            return assignment.withVariable(target).withType(type);
          }

          /** Replace initializers of assignments */
          @Override
          public J visitAssignment(J.Assignment assignment, ExecutionContext ctx) {

            Expression target = unwrapParentheses(assignment.getVariable());
            Expression assignmentValue = assignment.getAssignment();
            if (isDateOrBytesValue(target.getType())
                && !mapTypedValueToNewFqn(target.getType()).equals(convertedTargetType(target))) {
              return assignment;
            }
            if (isDateOrBytesValue(target.getType())
                && unwrapParentheses(assignmentValue) instanceof J.TypeCast cast
                && TypeUtils.isOfType(cast.getType(), target.getType())) {
              return assignment;
            }

            if (!(unwrapParentheses(assignmentValue) instanceof J.MethodInvocation invocation)) {
              return super.visitAssignment(assignment, ctx);
            }

            if (isBuilderInvocation(invocation)
                && (!(target instanceof J.Identifier)
                    || hasUnsupportedSerializationDataFormat(invocation))) {
              return assignment;
            }

            if (target instanceof J.FieldAccess fieldAccess) {
              if (convertedFieldType(fieldAccess.getName()) == null) {
                if (target.getType() instanceof JavaType.FullyQualified oldType
                    && oldType
                        .getFullyQualifiedName()
                        .startsWith("org.camunda.bpm.engine.variable.value.")
                    && (matchesTypedVariableGetter(invocation)
                        || invocation.getType() instanceof JavaType.FullyQualified resultType
                            && resultType
                                .getFullyQualifiedName()
                                .startsWith("org.camunda.bpm.engine.variable.value."))) {
                  return assignment;
                }
                return super.visitAssignment(assignment, ctx);
              }
            } else if (!(target instanceof J.Identifier)) {
              return super.visitAssignment(assignment, ctx);
            }

            if (TypeUtils.isOfClassType(target.getType(), OBJECT_VALUE_FQN)
                && !"java.lang.Object".equals(convertedTargetType(target))) {
              return assignment;
            }

            if (isDateOrBytesValue(target.getType())) {
              String newFqn = mapTypedValueToNewFqn(target.getType());
              Expression rawValue = unwrapTypedValueFactory(invocation, target.getType());
              if (rawValue != invocation) {
                J.Assignment modifiedAssignment =
                    retypeAssignment(assignment.withAssignment(rawValue), newFqn);
                if (requiresTransientReview(invocation)) {
                  modifiedAssignment =
                      modifiedAssignment.withComments(
                          Stream.concat(
                                  assignment.getComments().stream(),
                                  Stream.of(RecipeUtils.createSimpleComment(assignment, TRANSIENT_REVIEW)))
                              .toList());
                }
                maybeRemoveImport("org.camunda.bpm.engine.variable.Variables");
                if (target.getType() instanceof JavaType.FullyQualified oldType) {
                  maybeRemoveImport(oldType);
                }
                modifiedAssignment = (J.Assignment) super.visitAssignment(modifiedAssignment, ctx);
                return maybeAutoFormat(assignment, modifiedAssignment, ctx);
              }
            }

            if (matchesTypedVariableGetter(invocation)) {
              String newFqn = mapTypedValueToNewFqn(target.getType());
              boolean mappedObjectValue =
                  TypeUtils.isOfClassType(
                      target.getType(), "org.camunda.bpm.engine.variable.value.ObjectValue");
              if (!"java.lang.Object".equals(newFqn) || mappedObjectValue) {
                J.Assignment modifiedAssignment = assignment;
                if (!mappedObjectValue) {
                  String[] imports =
                      "byte[]".equals(newFqn) ? new String[0] : new String[] {newFqn};
                  modifiedAssignment =
                      RecipeUtils.createSimpleJavaTemplate(
                              "#{any()} = ("
                                  + RecipeUtils.getShortName(newFqn)
                                  + ") #{any()}",
                              imports)
                          .apply(
                              getCursor(), assignment.getCoordinates().replace(), target, invocation);
                }
                modifiedAssignment = retypeAssignment(modifiedAssignment, newFqn);
                modifiedAssignment = (J.Assignment) super.visitAssignment(modifiedAssignment, ctx);
                if (target.getType() instanceof JavaType.FullyQualified oldType) {
                  maybeRemoveImport(oldType);
                }
                return modifiedAssignment;
              }
            }

            if (!(target instanceof J.Identifier originalName)) {
              return super.visitAssignment(assignment, ctx);
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

            // visit simple method invocations
            for (ReplacementUtils.SimpleReplacementSpec spec : simpleMethodInvocations) {
              if (spec.matcher().matches(invocation)) {
                if ((dateValueFactory.matches(invocation)
                        || byteArrayValueFactory.matches(invocation))
                    && !isRawFactoryArgument(invocation)) {
                  J.MethodInvocation visited =
                      (J.MethodInvocation) super.visitMethodInvocation(invocation, ctx);
                  return maybeAutoFormat(invocation, retainTypedFactory(visited), ctx);
                }

                if (invocation.getType() instanceof JavaType.FullyQualified fqn) {
                  maybeRemoveImport(fqn);
                }

                List<String> comments =
                    getCursor().getNearestMessage(invocation.getId().toString()) != null
                        ? Collections.emptyList()
                        : spec.textComments();
                if ((dateValueFactory.matches(invocation)
                        || byteArrayValueFactory.matches(invocation))
                    && requiresTransientReview(invocation)) {
                  comments = List.of(TRANSIENT_REVIEW);
                }
                Expression modifiedInvocation =
                    RecipeUtils.applyTemplate(
                        spec.template(),
                        invocation,
                        getCursor(),
                        spec.argumentIndexes().stream()
                            .map(i -> invocation.getArguments().get(i.index()))
                            .toArray(),
                        comments);

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
              String returnTypeFqn = null;
              if (select instanceof J.Identifier identifier) {
                returnTypeFqn = convertedIdentifierType(identifier);
              } else if (select instanceof J.FieldAccess fieldAccess) {
                returnTypeFqn = convertedFieldType(fieldAccess.getName());
              }
              if (returnTypeFqn != null) {
                JavaType newType = JavaType.buildType(returnTypeFqn);
                if (select instanceof J.FieldAccess fieldAccess) {
                  if (fieldAccess.getType() instanceof JavaType.FullyQualified oldType) {
                    maybeRemoveImport(oldType);
                  }
                  select =
                      fieldAccess
                          .withName(fieldAccess.getName().withType(newType))
                          .withType(newType);
                } else {
                  select = select.withType(newType);
                }
                return RecipeUtils.createSimpleJavaTemplate("#{any()}")
                    .apply(
                        getCursor(),
                        invocation.getCoordinates().replace(),
                        select);
              }
            }

            if (invocation.getSimpleName().equals("getVariableTyped")
                || invocation.getSimpleName().equals("getVariableLocalTyped")) {
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
            return isRawMethodArgument(invocation, parent);
          }

          private boolean isLegacyTypedValue(JavaType type) {
            return type instanceof JavaType.FullyQualified qualified
                && qualified
                    .getFullyQualifiedName()
                    .startsWith("org.camunda.bpm.engine.variable.value.");
          }

          private boolean isFieldDeclaration() {
            Object parent = getCursor().getParentTreeCursor().getParentTreeCursor().getValue();
            return parent instanceof J.ClassDeclaration || parent instanceof J.NewClass;
          }

          private J.VariableDeclarations preserveTypedValues(J.VariableDeclarations declarations) {
            if (declarations.getTypeAsFullyQualified() instanceof JavaType.FullyQualified type
                && (isDateOrBytesValue(type)
                    || TypeUtils.isOfClassType(type, OBJECT_VALUE_FQN))) {
              Cursor scope = declarationScope();
              for (J.VariableDeclarations.NamedVariable variable : declarations.getVariables()) {
                scope.putMessage(variable.getSimpleName(), type.getFullyQualifiedName());
              }
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
            return block != null && isReassigned(block, variable.getSimpleName());
          }

          private boolean isReassigned(J.Block block, String name) {
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
            return assigned.contains(name);
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
            String mappedType = convertedIdentifierType(identifier);
            return mappedType == null
                ? identifier
                : identifier.withType(JavaType.buildType(mappedType));
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
