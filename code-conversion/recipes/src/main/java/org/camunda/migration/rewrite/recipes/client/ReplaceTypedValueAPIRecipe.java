package org.camunda.migration.rewrite.recipes.client;

import fj.data.Java;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.internal.ListUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.openrewrite.staticanalysis.RemoveUnneededBlock;

public class ReplaceTypedValueAPIRecipe extends Recipe {

    /**
     * Instantiates a new instance.
     */
    public ReplaceTypedValueAPIRecipe() {
    }

    @Override
    public String getDisplayName() {
        return "Convert typed value api to java object api";
    }

    @Override
    public String getDescription() {
        return "Replaces typed value api to java object api.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<>() {

            // Build new variable declaration: Map<String, Object> variableMap = new HashMap<>();
            final JavaTemplate mapTemplate = JavaTemplate.builder("Map<String, Object> variableMap = new HashMap<>();")
                    .imports("java.util.Map", "java.util.HashMap")
                    .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .build();

            final JavaTemplate mapTemplate2 = JavaTemplate.builder("Map<String, Object> variableMap = #{any()}")
                    .imports("java.util.Map", "java.util.HashMap")
                    .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .build();

            // Insert map.put(...) after the declaration
            final JavaTemplate putTemplate = JavaTemplate.builder("variableMap.put(#{any(String)}, #{any()});")
                    .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .build();

            @Override
            public J visitVariableDeclarations(J.VariableDeclarations decls, ExecutionContext ctx) {
                boolean modified = false;

                if (TypeUtils.isOfType(decls.getType(), JavaType.ShallowClass.build("org.camunda.bpm.engine.variable.VariableMap"))) {
                    // VariableMap replacement

                    // Assume one named variable
                    J.VariableDeclarations.NamedVariable var = decls.getVariables().get(0);

                    if (var.getInitializer() instanceof J.MethodInvocation oneMi
                        && oneMi.getSimpleName().equals("fromMap")) {
                        // from map replacement
                        maybeAddImport("java.util.Map");

                        J.VariableDeclarations newVarDecl = mapTemplate2.apply(getCursor(), decls.getCoordinates().replace(), oneMi.getArguments().get(0));

                        J.VariableDeclarations.NamedVariable newVar = newVarDecl.getVariables().get(0);

                        return decls.withVariables(List.of(newVar.withName(var.getName())));
                    } else {
                        // createVariables replacement

                        List<J.MethodInvocation> putValues = new ArrayList<>();

                        Expression current = var.getInitializer();
                        while (current instanceof J.MethodInvocation mi) {
                            System.out.println(mi.getSimpleName());
                            if (mi.getSimpleName().equals("putValueTyped") || mi.getSimpleName().equals("putValue")) {
                                putValues.add(mi);
                            }
                            current = mi.getSelect();
                        }

                        // Add necessary imports
                        maybeAddImport("java.util.Map");
                        maybeAddImport("java.util.HashMap");

                        List<Statement> statements = new ArrayList<>();

                        J.VariableDeclarations newVarDecl = mapTemplate.apply(getCursor(), decls.getCoordinates().replace());

                        J.VariableDeclarations.NamedVariable newVar = newVarDecl.getVariables().get(0);

                        J.Identifier newName = var.getName();

                        J.VariableDeclarations.NamedVariable renamedVar = newVar.withName(newName);

                        statements.add(newVarDecl.withVariables(List.of(renamedVar)));

                        for (J.MethodInvocation mi : putValues) {
                            J.MethodInvocation newMethodInvoc = putTemplate.apply(getCursor(), decls.getCoordinates().replace(), mi.getArguments().get(0), mi.getArguments().get(1)).withPrefix(Space.EMPTY);

                            J.Identifier newSelect = (J.Identifier) newMethodInvoc.getSelect();

                            J.MethodInvocation renamedMethodInvoc = newMethodInvoc.withSelect(newSelect.withSimpleName(newName.getSimpleName()));

                            statements.add(renamedMethodInvoc);
                        }

                        List<JRightPadded<Statement>> jRightStatements = new ArrayList<>();

                        for (Statement statement : statements) {
                            jRightStatements.add(new JRightPadded<>(statement, Space.EMPTY, Markers.EMPTY));
                        }

                        maybeRemoveImport("org.camunda.bpm.engine.variable.Variables");
                        maybeRemoveImport("org.camunda.bpm.engine.variable.VariableMap");
                        maybeRemoveImport("org.camunda.bpm.engine.variable.value.StringValue");

                        return new J.Block(
                                Tree.randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                new JRightPadded<>(false, Space.EMPTY, Markers.EMPTY),
                                jRightStatements,
                                Space.EMPTY
                        );
                    }
                } else {
                    // Typed Variable replacement

                    List<J.VariableDeclarations.NamedVariable> updatedVars = new ArrayList<>();

                    JavaType newType = null;

                    for (J.VariableDeclarations.NamedVariable var : decls.getVariables()) {
                        if (var.getInitializer() instanceof J.MethodInvocation method &&
                            method.getSelect() instanceof J.Identifier sel &&
                            sel.getSimpleName().equals("Variables")) {

                            switch (method.getSimpleName()) {
                                case "booleanValue" -> newType = JavaType.Primitive.Boolean;
                                case "stringValue" -> newType = JavaType.ShallowClass.build("java.lang.String");
                                case "integerValue" -> newType = JavaType.Primitive.Int;
                                case "longValue" -> newType = JavaType.Primitive.Long;
                                case "shortValue" -> newType = JavaType.Primitive.Short;
                                case "doubleValue" -> newType = JavaType.Primitive.Double;
                                case "floatValue" -> newType = JavaType.Primitive.Float;
                                case "byteArrayValue" -> newType = JavaType.buildType("byte[]");
                                default -> System.out.println("Type not yet implemented!");
                            }

                            if (newType != null) {
                                var = var.withType(newType).withInitializer(method.getArguments().get(0).withPrefix(Space.SINGLE_SPACE));
                                modified = true;
                            }
                        }
                        updatedVars.add(var);
                    }

                    if (modified) {
                        J.Identifier newTypeExpression = new J.Identifier(
                                Tree.randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                null,
                                Objects.equals(newType.toString(), "java.lang.String") ? "String" : newType.toString(),
                                newType,
                                null
                        );
                        maybeRemoveImport("org.camunda.bpm.engine.variable.value.BooleanValue");
                        maybeRemoveImport("org.camunda.bpm.engine.variable.value.BytesValue");
                        maybeRemoveImport("org.camunda.bpm.engine.variable.value.FloatValue");
                        maybeRemoveImport("org.camunda.bpm.engine.variable.value.DoubleValue");
                        maybeRemoveImport("org.camunda.bpm.engine.variable.value.ShortValue");
                        maybeRemoveImport("org.camunda.bpm.engine.variable.value.LongValue");
                        maybeRemoveImport("org.camunda.bpm.engine.variable.value.IntegerValue");
                        maybeRemoveImport("org.camunda.bpm.engine.variable.value.StringValue");
                        maybeRemoveImport("org.camunda.bpm.engine.variable.Variables");
                        return decls.withType(newType).withTypeExpression(newTypeExpression).withVariables(updatedVars);
                    }
                }
                return super.visitVariableDeclarations(decls, ctx);
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation invoc, ExecutionContext ctx) {

                if (invoc.getMethodType() != null && TypeUtils.isOfType(invoc.getMethodType().getDeclaringType(), JavaType.ShallowClass.build("org.camunda.bpm.engine.variable.VariableMap"))
                    && (invoc.getSimpleName().equals("putValueTyped") || invoc.getSimpleName().equals("putValue"))) {
                    J.Identifier ident = new J.Identifier(
                            Tree.randomId(),
                            Space.EMPTY,
                            Markers.EMPTY,
                            null,
                            "put",
                            JavaType.ShallowClass.build("java.lang.String"),
                            null
                    );

                    return invoc.withName(ident);
                }
                return super.visitMethodInvocation(invoc, ctx);
            }

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
                J.Block flattened = block.withStatements(ListUtils.flatMap(statements, (i, stmt) -> {
                    J.Block nested;
                    if (stmt instanceof J.Try) {
                        J.Try _try = (J.Try) stmt;
                        if (_try.getResources() != null || !_try.getCatches().isEmpty() || _try.getFinally() == null || !_try.getFinally().getStatements().isEmpty()) {
                            return stmt;
                        }
                        nested = _try.getBody();
                    } else if (stmt instanceof J.Block) {
                        nested = (J.Block) stmt;
                    } else {
                        return stmt;
                    }

                    return ListUtils.map(nested.getStatements(), (j, inlinedStmt) -> {
                        if (j == 0) {
                            inlinedStmt = inlinedStmt.withPrefix(inlinedStmt.getPrefix()
                                    .withComments(ListUtils.concatAll(nested.getComments(), inlinedStmt.getComments())));
                        }
                        return autoFormat(inlinedStmt, ctx, getCursor());
                    });
                }));

                if (flattened == block) {
                    return block;
                } else if (lastStatement instanceof J.Block) {
                    flattened = flattened.withEnd(flattened.getEnd()
                            .withComments(ListUtils.concatAll(((J.Block) lastStatement).getEnd().getComments(), flattened.getEnd().getComments())));
                }
                return flattened;
            }
        };
    }
}