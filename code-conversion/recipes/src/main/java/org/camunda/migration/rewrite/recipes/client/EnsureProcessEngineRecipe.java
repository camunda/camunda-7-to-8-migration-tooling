package org.camunda.migration.rewrite.recipes.client;

import org.openrewrite.*;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.template.internal.AbstractRefasterJavaVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

public class EnsureProcessEngineRecipe extends Recipe {

    /**
     * Instantiates a new instance.
     */
    public EnsureProcessEngineRecipe() {
    }

    @Override
    public String getDisplayName() {
        return "Ensuring process engine";
    }

    @Override
    public String getDescription() {
        return "Replaces specific services with process engine.";
    }

    private boolean isServiceReference(Expression expr, String fqn) {
        if (expr instanceof J.Identifier ident) {
            JavaType type = ident.getType();
            return type instanceof JavaType.FullyQualified fqType &&
                    fqType.getFullyQualifiedName().equals(fqn);
        }
        return false;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaVisitor<ExecutionContext> javaVisitor = new AbstractRefasterJavaVisitor() {

            final JavaTemplate processEngineRuntimeService = JavaTemplate.builder("#{any(org.camunda.bpm.engine.ProcessEngine)}.getRuntimeService()")
                    .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .build();

            final JavaTemplate processEngineTaskService = JavaTemplate.builder("#{any(org.camunda.bpm.engine.ProcessEngine)}.getTaskService()")
                    .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .build();

            final JavaTemplate processEngineRepositoryService = JavaTemplate.builder("#{any(org.camunda.bpm.engine.ProcessEngine)}.getRepositoryService()")
                    .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .build();

            @Override
            public J visitMethodInvocation(J.MethodInvocation elem, ExecutionContext ctx) {
                // This is the replacement identifier for ProcessEngine
                J.Identifier processEngineJ = new J.Identifier(
                        Tree.randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        null,
                        "engine",
                        JavaType.ShallowClass.build("org.camunda.bpm.engine.ProcessEngine"),
                        null
                );

                if (isServiceReference(elem.getSelect(), "org.camunda.bpm.engine.RuntimeService")) {
                    return elem.withSelect(processEngineRuntimeService.apply(getCursor(), elem.getCoordinates().replace(), processEngineJ)).withPrefix(Space.EMPTY);
                }
                if (isServiceReference(elem.getSelect(), "org.camunda.bpm.engine.TaskService")) {
                    return elem.withSelect(processEngineTaskService.apply(getCursor(), elem.getCoordinates().replace(), processEngineJ)).withPrefix(Space.EMPTY);
                }
                if (isServiceReference(elem.getSelect(), "org.camunda.bpm.engine.RepositoryService")) {
                    return elem.withSelect(processEngineRepositoryService.apply(getCursor(), elem.getCoordinates().replace(), processEngineJ)).withPrefix(Space.EMPTY);
                }
                return super.visitMethodInvocation(elem, ctx);
            }

        };
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>("org.camunda.bpm.engine.RuntimeService", true),
                        new UsesType<>("org.camunda.bpm.engine.TaskService", true),
                        new UsesType<>("org.camunda.bpm.engine.RepositoryService", true)
                ),
                javaVisitor
        );
    }
}