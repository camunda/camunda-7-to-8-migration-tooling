package org.camunda.migration.rewrite.recipes.client.prepare;

import org.openrewrite.*;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.template.internal.AbstractRefasterJavaVisitor;
import org.openrewrite.java.tree.J;

public class AddProcessEngineDependencyRecipe extends Recipe {

    /**
     * Instantiates a new instance.
     */
    public AddProcessEngineDependencyRecipe() {
    }

    @Override
    public String getDisplayName() {
        return "Add process engine dependency";
    }

    @Override
    public String getDescription() {
        return "Adds process engine dependency.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaVisitor<ExecutionContext> javaVisitor = new AbstractRefasterJavaVisitor() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {

                final String FIELD_NAME = "engine";
                final String FIELD_TYPE = "org.camunda.bpm.engine.ProcessEngine";

                // Build the new field with JavaTemplate
                JavaTemplate template = JavaTemplate.builder("""
                                    @Autowired
                                    private ProcessEngine engine;
                                """)
                        .imports("org.springframework.beans.factory.annotation.Autowired",
                                FIELD_TYPE)
                        .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                        .build();

                // Skip interfaces
                if (classDecl.getKind() != J.ClassDeclaration.Kind.Type.Class) {
                    return classDecl;
                }

                // Check if field already exists
                boolean hasField = classDecl.getBody().getStatements().stream()
                        .filter(stmt -> stmt instanceof J.VariableDeclarations)
                        .map(stmt -> (J.VariableDeclarations) stmt)
                        .anyMatch(varDecl -> varDecl.getVariables().stream()
                                .anyMatch(v -> v.getSimpleName().equals(FIELD_NAME)));

                if (hasField) {
                    return classDecl; // Already present
                }

                // Insert the new field at the top of the class body
                maybeAddImport(FIELD_TYPE);
                maybeAddImport("org.springframework.beans.factory.annotation.Autowired");

                return template.apply(
                        updateCursor(classDecl),
                        classDecl.getBody().getCoordinates().firstStatement()
                );
            }

        };
        return Preconditions.check(
                Preconditions.and(
                        Preconditions.not(new UsesType<>("org.camunda.bpm.engine.ProcessEngine", true)),
                        Preconditions.or(
                                new UsesType<>("org.camunda.bpm.engine.RuntimeService", true),
                                new UsesType<>("org.camunda.bpm.engine.TaskService", true),
                                new UsesType<>("org.camunda.bpm.engine.RepositoryService", true)
                        )),
                javaVisitor
        );
    }
}
