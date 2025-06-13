package org.camunda.migration.rewrite.recipes.client.prepare;

import java.util.ArrayList;
import java.util.List;
import org.camunda.migration.rewrite.recipes.client.utils.ClientConstants;
import org.camunda.migration.rewrite.recipes.client.utils.ClientUtils;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

public class ReplaceTypedValueAPIRecipe extends Recipe {

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
            new UsesType<>(ClientConstants.Type.VARIABLES, true),
            new UsesType<>(ClientConstants.Type.VARIABLE_MAP, true),
            new UsesType<>(ClientConstants.Type.TYPED_VALUE, true),
            new UsesType<>(ClientConstants.Type.OBJECT_VALUE, true),
            new UsesType<>(ClientConstants.Type.STRING_VALUE, true),
            new UsesType<>(ClientConstants.Type.INTEGER_VALUE, true),
            new UsesType<>(ClientConstants.Type.LONG_VALUE, true),
            new UsesType<>(ClientConstants.Type.SHORT_VALUE, true),
            new UsesType<>(ClientConstants.Type.DOUBLE_VALUE, true),
            new UsesType<>(ClientConstants.Type.FLOAT_VALUE, true),
            new UsesType<>(ClientConstants.Type.BYTES_VALUE, true));

    return Preconditions.check(
        check,
        new JavaVisitor<ExecutionContext>() {

          final JavaTemplate newMapAndHashMap =
              JavaTemplate.builder("Map<String, Object> variableMap = new HashMap<>();")
                  .imports("java.util.Map", "java.util.HashMap")
                  .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                  .build();

          final JavaTemplate newMapWithAnyInitializer =
              JavaTemplate.builder("Map<String, Object> variableMap = #{any()}")
                  .imports("java.util.Map")
                  .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                  .build();

          // Insert map.put(...) after the declaration
          final JavaTemplate putElementInMap =
              JavaTemplate.builder("#{any(java.util.Map)}.put(#{any(String)}, #{any()});")
                  .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                  .build();

          /** Visit variable declarations to replace all typedValue types */
          @Override
          public J visitVariableDeclarations(J.VariableDeclarations decls, ExecutionContext ctx) {

            maybeRemoveImport(ClientConstants.Type.BOOLEAN_VALUE);
            maybeRemoveImport(ClientConstants.Type.BYTES_VALUE);
            maybeRemoveImport(ClientConstants.Type.FLOAT_VALUE);
            maybeRemoveImport(ClientConstants.Type.DOUBLE_VALUE);
            maybeRemoveImport(ClientConstants.Type.SHORT_VALUE);
            maybeRemoveImport(ClientConstants.Type.LONG_VALUE);
            maybeRemoveImport(ClientConstants.Type.INTEGER_VALUE);
            maybeRemoveImport(ClientConstants.Type.STRING_VALUE);
            maybeRemoveImport(ClientConstants.Type.OBJECT_VALUE);
            maybeRemoveImport(ClientConstants.Type.TYPED_VALUE);
            maybeRemoveImport(ClientConstants.Type.VARIABLE_MAP);
            maybeRemoveImport(ClientConstants.Type.VARIABLES);

            // Analyze first variable
            J.VariableDeclarations.NamedVariable firstVar = decls.getVariables().get(0);
            J.Identifier originalName = firstVar.getName();
            JavaType.Variable originalVariableType = firstVar.getVariableType();

            // fromMap VariableMap replacement - one variable assumed
            if (TypeUtils.isOfType(
                    decls.getType(), JavaType.ShallowClass.build(ClientConstants.Type.VARIABLE_MAP))
                && firstVar.getInitializer() instanceof J.MethodInvocation oneMi
                && oneMi.getSimpleName().equals("fromMap")) {

              // apply java template to create Map with name variableMap and initializer being the
              // first argument of fromMap()
              J.VariableDeclarations newMapDecl =
                  newMapWithAnyInitializer.apply(
                      getCursor(), decls.getCoordinates().replace(), oneMi.getArguments().get(0));

              maybeAddImport("java.util.Map");

              // return new declaration with old name and variable type
              return newMapDecl.withVariables(
                  List.of(
                      newMapDecl
                          .getVariables()
                          .get(0)
                          .withName(originalName)
                          .withVariableType(originalVariableType))); // TODO: check this
            }

            // createVariables replacement - one variable assumed
            // this case requires a non-iso visitor to replace one statement with a block
            // the unneeded block is subsequently removed
            if (TypeUtils.isOfType(
                decls.getType(), JavaType.ShallowClass.build(ClientConstants.Type.VARIABLE_MAP))) {

              List<J.MethodInvocation> putValues = new ArrayList<>();

              // collect information on entries directly put into the map on creation
              Expression current = firstVar.getInitializer();
              while (current instanceof J.MethodInvocation mi) {
                if (mi.getSimpleName().equals("putValueTyped")
                    || mi.getSimpleName().equals("putValue")) {
                  putValues.add(mi);
                }
                current = mi.getSelect();
              }

              // Add necessary imports
              maybeAddImport("java.util.Map");
              maybeAddImport("java.util.HashMap");

              // collect statement to be put in the block
              List<Statement> statements = new ArrayList<>();
              List<JRightPadded<Statement>> jRightStatements = new ArrayList<>();

              J.VariableDeclarations newMapAndHashMapDecl =
                  newMapAndHashMap.apply(getCursor(), decls.getCoordinates().replace());

              // added map as hashmap initialization to statements
              jRightStatements.add(
                  new JRightPadded<>(
                      newMapAndHashMapDecl.withVariables(
                          List.of(
                              newMapAndHashMapDecl
                                  .getVariables()
                                  .get(0)
                                  .withName(originalName)
                                  .withVariableType(originalVariableType))),
                      Space.EMPTY,
                      Markers.EMPTY));

              // add each map.put() as statement
              for (J.MethodInvocation mi : putValues) {
                J.Identifier mapIdent =
                    ClientUtils.createSimpleIdentifier(
                        originalName.getSimpleName(), "java.util.Map");

                J.MethodInvocation newMethodInvoc =
                    putElementInMap
                        .apply(
                            getCursor(),
                            decls.getCoordinates().replace(),
                            mapIdent,
                            mi.getArguments().get(0),
                            mi.getArguments().get(1))
                        .withPrefix(Space.EMPTY);

                jRightStatements.add(
                    new JRightPadded<>(newMethodInvoc, Space.EMPTY, Markers.EMPTY));
              }

              // create and return block
              return new J.Block(
                  Tree.randomId(),
                  Space.EMPTY,
                  Markers.EMPTY,
                  new JRightPadded<>(false, Space.EMPTY, Markers.EMPTY),
                  jRightStatements,
                  Space.EMPTY);
            }

            // Typed Variable replacement - multiple variables can exist

            // collect all variables, changed or not
            List<J.VariableDeclarations.NamedVariable> updatedVars = new ArrayList<>();

            // new type for names variables and variable declaration
            JavaType newType = null;

            for (J.VariableDeclarations.NamedVariable var : decls.getVariables()) {
              if (var.getInitializer() instanceof J.MethodInvocation method
                  && method.getSelect() instanceof J.Identifier sel
                  && sel.getSimpleName().equals("Variables")) {
                // the initializer needs to be a method invocation which first select method is
                // the Variables identifier

                // depending on the method name, newType is set. objectValue is handled below
                switch (method.getSimpleName()) {
                  case "booleanValue" -> newType = JavaType.Primitive.Boolean;
                  case "stringValue" -> newType = JavaType.ShallowClass.build("java.lang.String");
                  case "integerValue" -> newType = JavaType.Primitive.Int;
                  case "longValue" -> newType = JavaType.Primitive.Long;
                  case "shortValue" -> newType = JavaType.Primitive.Short;
                  case "doubleValue" -> newType = JavaType.Primitive.Double;
                  case "floatValue" -> newType = JavaType.Primitive.Float;
                  case "byteArrayValue" -> newType = JavaType.buildType("byte[]");
                  default -> {}
                }

                // var has typed adjusted and initializer is the first method argument: parameter
                // of booleanValue() for example
                if (newType != null) {
                  var =
                      var.withType(newType)
                          .withInitializer(
                              method.getArguments().get(0).withPrefix(Space.SINGLE_SPACE));
                }
              } else if (var.getInitializer() instanceof J.MethodInvocation method
                  && method.getSimpleName().equals("create")
                  && method.getSelect() instanceof J.MethodInvocation method2
                  && method2.getSelect() instanceof J.Identifier sel
                  && sel.getSimpleName().equals("Variables")) {
                // for objectValue, the first method is called create, plus, there are two select
                // methods until the identifier Variables is reached

                // set type to the type of the parameter inside objectValue()
                newType = method2.getArguments().get(0).getType();

                // var has type adjusted and initializer is the select method's first argument:
                // parameter of objectValue
                if (newType != null) {
                  var =
                      var.withType(newType)
                          .withInitializer(
                              method2.getArguments().get(0).withPrefix(Space.SINGLE_SPACE));
                }
              }
              updatedVars.add(var);
            }

            // if new type was set, update type expression of declaration and return declaration
            // with updated variables
            if (newType != null) {
              String typeString = newType.toString();
              if (typeString.equals("java.lang.String")) {
                typeString = "String";
              }
              if (newType instanceof JavaType.Class newClass) {
                typeString = newClass.getClassName();
              }

              J.Identifier newTypeExpression =
                  ClientUtils.createSimpleIdentifier(typeString, newType);

              return decls
                  .withType(newType)
                  .withTypeExpression(newTypeExpression)
                  .withVariables(updatedVars);
            }

            // this replaces standalone declarations, like method parameters
            if (decls.getTypeExpression() instanceof J.Identifier typeExpr) {

              // depending on the type expression, newType is set
              switch (typeExpr.getSimpleName()) {
                case "BooleanValue" -> newType = JavaType.Primitive.Boolean;
                case "StringValue" -> newType = JavaType.ShallowClass.build("java.lang.String");
                case "IntegerValue" -> newType = JavaType.Primitive.Int;
                case "LongValue" -> newType = JavaType.Primitive.Long;
                case "ShortValue" -> newType = JavaType.Primitive.Short;
                case "DoubleValue" -> newType = JavaType.Primitive.Double;
                case "FloatValue" -> newType = JavaType.Primitive.Float;
                case "ByteArrayValue" -> newType = JavaType.buildType("byte[]");
                case "ObjectValue" -> newType = JavaType.buildType("java.lang.Object");
                default -> {}
              }

              // if new type was set, update type expression of declaration and return declaration
              if (newType != null) {
                String typeString = newType.toString();
                if (typeString.equals("java.lang.String")) {
                  typeString = "String";
                }
                if (typeString.equals("java.lang.Object")) {
                  typeString = "Object";
                }

                J.Identifier newTypeExpression =
                    ClientUtils.createSimpleIdentifier(typeString, newType);

                return decls.withType(newType).withTypeExpression(newTypeExpression);
              }
            }
            return super.visitVariableDeclarations(decls, ctx);
          }

          /** Replace variableMap.put() or variableMap.putValue() method invocations */
          @Override
          public J visitMethodInvocation(J.MethodInvocation invoc, ExecutionContext ctx) {

            if (invoc.getMethodType() != null
                && TypeUtils.isOfType(
                    invoc.getMethodType().getDeclaringType(),
                    JavaType.ShallowClass.build(ClientConstants.Type.VARIABLE_MAP))
                && (invoc.getSimpleName().equals("putValueTyped")
                    || invoc.getSimpleName().equals("putValue"))) {

              // rename method invocation identifier to put
              J.Identifier ident = ClientUtils.createSimpleIdentifier("put", "java.lang.String");
              return invoc.withName(ident);
            }
            return super.visitMethodInvocation(invoc, ctx);
          }

          /** Replace initializers of assignments */
          @Override
          public J visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
            Expression assignmentExpr = assignment.getAssignment();

            if (assignmentExpr instanceof J.MethodInvocation method
                && method.getSelect() instanceof J.Identifier sel
                && sel.getSimpleName().equals("Variables")) {
              // the initializer needs to be a method invocation which first or second select method
              // is the Variables identifier

              return assignment.withAssignment(method.getArguments().get(0).withPrefix(Space.SINGLE_SPACE));
            } else if (assignmentExpr instanceof J.MethodInvocation method
                && method.getSelect() instanceof J.MethodInvocation method2
                && method2.getSelect() instanceof J.Identifier sel2
                && sel2.getSimpleName().equals("Variables")) {
              return assignment.withAssignment(method2.getArguments().get(0).withPrefix(Space.SINGLE_SPACE));
            }

            return super.visitAssignment(assignment, executionContext);
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
