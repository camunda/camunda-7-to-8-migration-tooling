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
  private static final String GETTER_ONLY_OBJECT_VALUE_FIELDS = "getterOnlyObjectValueFields";
  private static final List<MethodMatcher> TYPED_VALUE_GETTERS =
      List.of(
          new MethodMatcher("org.camunda.bpm.engine.delegate.VariableScope getVariableTyped(..)"),
          new MethodMatcher(
              "org.camunda.bpm.engine.delegate.VariableScope getVariableLocalTyped(..)"),
          new MethodMatcher("org.camunda.bpm.client.task.ExternalTask getVariableTyped(..)"),
          new MethodMatcher("org.camunda.bpm.engine.TaskService getVariableTyped(..)"),
          new MethodMatcher("org.camunda.bpm.engine.TaskService getVariableLocalTyped(..)"));

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
                          // "byteArrayValue(java.lang.Byte[] bytes)"
                          "org.camunda.bpm.engine.variable.Variables byteArrayValue(..)"),
                      RecipeUtils.createSimpleJavaTemplate("#{any(java.lang.Byte[])}"),
                      null,
                      "java.lang.Byte[]",
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

          @Override
          public J.ClassDeclaration visitClassDeclaration(
              J.ClassDeclaration declaration, ExecutionContext ctx) {
            // Fields may be declared after methods that read or assign them.
            getCursor()
                .putMessage(
                    GETTER_ONLY_OBJECT_VALUE_FIELDS, getterOnlyObjectValueFields(declaration.getBody()));
            return (J.ClassDeclaration) super.visitClassDeclaration(declaration, ctx);
          }

          @Override
          public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
            if (newClass.getBody() != null) {
              getCursor()
                  .putMessage(
                      GETTER_ONLY_OBJECT_VALUE_FIELDS,
                      getterOnlyObjectValueFields(newClass.getBody()));
            }
            return (J.NewClass) super.visitNewClass(newClass, ctx);
          }

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

          /** Visit variable declarations to replace all typedValue types */
          @Override
          public J visitVariableDeclarations(
              J.VariableDeclarations declarations, ExecutionContext ctx) {

            if (declarations.getVariables().size() != 1) {
              return preserveObjectValues(declarations);
            }

            // Analyze first variable
            J.VariableDeclarations.NamedVariable firstVar = declarations.getVariables().get(0);
            J.Identifier originalName = firstVar.getName();
            Expression originalInitializer = unwrapParentheses(firstVar.getInitializer());
            // A declaration without a value might later receive a builder we cannot unwrap.
            Object declarationParent = getCursor().getParentTreeCursor().getValue();
            if (TypeUtils.isOfClassType(declarations.getType(), OBJECT_VALUE_FQN)) {
              boolean onlyTypedGetterWrites =
                  originalInitializer == null
                      && isFieldDeclaration()
                      && isGetterOnlyObjectValueField(originalName);
              if (!onlyTypedGetterWrites
                  && ((originalInitializer == null
                          && (declarationParent instanceof J.Block
                              || declarationParent instanceof J.ForLoop.Control))
                      || isReassigned(originalName))) {
                return preserveObjectValues(declarations);
              }
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
                    return preserveObjectValues(declarations);
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

              String newFqn = mapTypedValueToNewFqn(typeExpr.getType());
              if (!"java.lang.Object".equals(newFqn)
                  || TypeUtils.isOfClassType(
                      typeExpr.getType(), "org.camunda.bpm.engine.variable.value.ObjectValue")) {

                // record fqn of identifier for later uses
                if (!isGetterOnlyObjectValueField(originalName)) {
                  declarationScope().putMessage(originalName.toString(), newFqn);
                }

                maybeRemoveImport(declarations.getTypeAsFullyQualified());

                String[] imports =
                    "java.util.Date".equals(newFqn) ? new String[] {newFqn} : new String[0];
                if (imports.length > 0) {
                  maybeAddImport(newFqn);
                }
                return maybeAutoFormat(
                    declarations,
                    RecipeUtils.createSimpleJavaTemplate(
                            RecipeUtils.getShortName(newFqn) + " " + firstVar.getSimpleName(),
                            imports)
                        .apply(getCursor(), declarations.getCoordinates().replace()),
                    ctx);
              }
            }
            return super.visitVariableDeclarations(declarations, ctx);
          }

          /** Replace initializers of assignments */
          @Override
          public J visitAssignment(J.Assignment assignment, ExecutionContext ctx) {

            Expression target = assignment.getVariable();
            Expression assignmentValue = assignment.getAssignment();
            Expression unwrappedAssignmentValue = unwrapParentheses(assignmentValue);
            if (!(unwrappedAssignmentValue instanceof J.MethodInvocation invocation)) {
              return super.visitAssignment(assignment, ctx);
            }

            if (!(target instanceof J.Identifier) && isBuilderInvocation(invocation)) {
              return assignment;
            }
            if (!(target instanceof J.Identifier)
                && !(target instanceof J.FieldAccess fieldAccess
                    && fieldAccess.getTarget() instanceof J.Identifier owner
                    && "this".equals(owner.getSimpleName()))) {
              return super.visitAssignment(assignment, ctx);
            }

            if (target instanceof J.Identifier identifier) {
              String rewrittenType = getCursor().getNearestMessage(identifier.getSimpleName());
              if (TypeUtils.isOfClassType(identifier.getType(), OBJECT_VALUE_FQN)
                  && !isGetterOnlyObjectValueField(identifier)
                  && (rewrittenType == null || OBJECT_VALUE_FQN.equals(rewrittenType))) {
                return assignment;
              }
            }

            if (isBuilderInvocation(invocation)
                && hasUnsupportedSerializationDataFormat(invocation)) {
              return assignment;
            }

            if (new MethodMatcher(
                    "org.camunda.bpm.engine.delegate.VariableScope getVariableTyped(..)")
                .matches(invocation)) {
              String newFqn = mapTypedValueToNewFqn(target.getType());
              if (!"java.lang.Object".equals(newFqn)) {
                String[] imports =
                    "byte[]".equals(newFqn) ? new String[0] : new String[] {newFqn};
                J.Assignment modifiedAssignment =
                    RecipeUtils.createSimpleJavaTemplate(
                            "#{any()} = ("
                                + RecipeUtils.getShortName(newFqn)
                                + ") #{any()}",
                            imports)
                        .apply(
                            getCursor(), assignment.getCoordinates().replace(), target, invocation);
                return super.visitAssignment(modifiedAssignment, ctx);
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

            if (invocation.getSimpleName().equals("getValue")
                && invocation.getSelect() != null) {
              Expression select = invocation.getSelect();
              String returnTypeFqn = null;
              if (select instanceof J.Identifier identifier) {
                returnTypeFqn =
                    isGetterOnlyObjectValueField(identifier)
                        ? "java.lang.Object"
                        : getCursor().getNearestMessage(identifier.getSimpleName());
              } else if (select instanceof J.FieldAccess access
                  && isGetterOnlyObjectValueField(access.getName())) {
                returnTypeFqn = "java.lang.Object";
              }
              if (returnTypeFqn == null || OBJECT_VALUE_FQN.equals(returnTypeFqn)) {
                return super.visitMethodInvocation(invocation, ctx);
              }

              return RecipeUtils.createSimpleJavaTemplate("#{any()}")
                  .apply(
                      getCursor(),
                      invocation.getCoordinates().replace(),
                      select.withType(JavaType.buildType(returnTypeFqn)));
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

          private Set<JavaType.Variable> getterOnlyObjectValueFields(J.Block body) {
            Set<JavaType.Variable> fields = new HashSet<>();
            for (Statement statement : body.getStatements()) {
              if (statement instanceof J.VariableDeclarations declarations
                  && declarations.getVariables().size() == 1
                  && TypeUtils.isOfClassType(declarations.getType(), OBJECT_VALUE_FQN)) {
                J.VariableDeclarations.NamedVariable variable = declarations.getVariables().get(0);
                JavaType.Variable fieldType = variable.getName().getFieldType();
                if (variable.getInitializer() == null
                    && fieldType != null
                    && hasOnlyTypedValueGetterAssignments(body, fieldType)) {
                  fields.add(fieldType);
                }
              }
            }
            return fields;
          }

          private boolean hasOnlyTypedValueGetterAssignments(
              J.Block body, JavaType.Variable fieldType) {
            List<J.Assignment> assignments = new ArrayList<>();
            new JavaIsoVisitor<List<J.Assignment>>() {
              @Override
              public J.Assignment visitAssignment(
                  J.Assignment assignment, List<J.Assignment> collected) {
                Expression target = unwrapParentheses(assignment.getVariable());
                if (target instanceof J.FieldAccess access) {
                  target = access.getName();
                }
                if (target instanceof J.Identifier identifier
                    && fieldType.equals(identifier.getFieldType())) {
                  collected.add(assignment);
                }
                return super.visitAssignment(assignment, collected);
              }
            }.visit(body, assignments);

            return !assignments.isEmpty()
                && assignments.stream()
                    .allMatch(
                        assignment ->
                            unwrapParentheses(assignment.getAssignment())
                                    instanceof J.MethodInvocation invocation
                                && TYPED_VALUE_GETTERS.stream()
                                    .anyMatch(matcher -> matcher.matches(invocation)));
          }

          private boolean isGetterOnlyObjectValueField(J.Identifier identifier) {
            JavaType.Variable fieldType = identifier.getFieldType();
            if (fieldType == null) {
              return false;
            }
            for (Cursor cursor = getCursor(); cursor != null; cursor = cursor.getParent()) {
              Set<JavaType.Variable> fields = cursor.getMessage(GETTER_ONLY_OBJECT_VALUE_FIELDS);
              if (fields != null && fields.contains(fieldType)) {
                return true;
              }
            }
            return false;
          }

          private J.VariableDeclarations preserveObjectValues(J.VariableDeclarations declarations) {
            if (TypeUtils.isOfClassType(declarations.getType(), OBJECT_VALUE_FQN)) {
              // Shadow messages from converted fields when a local or parameter stays ObjectValue.
              Cursor scope = declarationScope();
              for (J.VariableDeclarations.NamedVariable variable : declarations.getVariables()) {
                scope.putMessage(variable.getSimpleName(), OBJECT_VALUE_FQN);
              }
            }
            return declarations;
          }

          private Cursor declarationScope() {
            return getCursor().getParentTreeCursor().getValue() instanceof J.MethodDeclaration
                ? getCursor().dropParentUntil(parent -> parent instanceof J.MethodDeclaration)
                : getCursor().dropParentUntil(parent -> parent instanceof J.Block);
          }

          private boolean isReassigned(J.Identifier variable) {
            J.Block block =
                getCursor().getParentTreeCursor().getValue() instanceof J.MethodDeclaration method
                    ? method.getBody()
                    : getCursor().firstEnclosing(J.Block.class);
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
            if (isGetterOnlyObjectValueField(identifier)) {
              return identifier.withType(JavaType.buildType("java.lang.Object"));
            }
            if (OBJECT_VALUE_FQN.equals(
                getCursor().getNearestMessage(identifier.getSimpleName()))) {
              return identifier;
            }
            return (J.Identifier) RecipeUtils.updateType(getCursor(), identifier);
          }

          @Override
          public J.FieldAccess visitFieldAccess(J.FieldAccess access, ExecutionContext ctx) {
            J.FieldAccess visited = (J.FieldAccess) super.visitFieldAccess(access, ctx);
            return isGetterOnlyObjectValueField(access.getName())
                ? visited.withType(JavaType.buildType("java.lang.Object"))
                : visited;
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
