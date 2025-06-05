package org.camunda.migration.rewrite.recipes.client.prepare;

import org.openrewrite.*;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.template.internal.AbstractRefasterJavaVisitor;
import org.openrewrite.java.tree.J;

public class AddCamundaClientWrapperDependencyRecipe extends Recipe {

    /**
     * Instantiates a new instance.
     */
    public AddCamundaClientWrapperDependencyRecipe(String CLIENT_WRAPPER_PACKAGE) {
        this.CLIENT_WRAPPER_PACKAGE = CLIENT_WRAPPER_PACKAGE;
    }

    @Override
    public String getDisplayName() {
        return "Ensure camunda 8 wrapper";
    }

    @Override
    public String getDescription() {
        return "Adds camunda 8 wrapper dependency.";
    }

    String CLIENT_WRAPPER_PACKAGE;

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaVisitor<ExecutionContext> javaVisitor = new AbstractRefasterJavaVisitor() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {

                final String FIELD_NAME = "camundaClientWrapper";



                // Build the new field with JavaTemplate
                JavaTemplate template = JavaTemplate.builder("""
                        @Autowired
                        private CamundaClientWrapper camundaClientWrapper;
                    """)
                        .imports("org.springframework.beans.factory.annotation.Autowired",
                                CLIENT_WRAPPER_PACKAGE)
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
                                .anyMatch(v -> v.getSimpleName().equals(CLIENT_WRAPPER_PACKAGE)));

                if (hasField) {
                    return classDecl; // Already present
                }

                // Insert the new field at the top of the class body
                maybeAddImport(CLIENT_WRAPPER_PACKAGE);
                maybeAddImport("org.springframework.beans.factory.annotation.Autowired");

                return template.apply(
                        updateCursor(classDecl),
                        classDecl.getBody().getCoordinates().firstStatement()
                );
            }
        };
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>("org.camunda.bpm.engine.ProcessEngine", true),
                        Preconditions.not(new UsesType<>("org.camunda.migration.rewrite.recipes.client.CamundaClientWrapper", true))
                ),
                javaVisitor
        );
    }
}
