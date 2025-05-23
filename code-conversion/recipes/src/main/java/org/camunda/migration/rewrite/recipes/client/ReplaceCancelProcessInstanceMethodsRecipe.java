package org.camunda.migration.rewrite.recipes.client;

import org.openrewrite.*;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.template.internal.AbstractRefasterJavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import static org.openrewrite.java.template.internal.AbstractRefasterJavaVisitor.EmbeddingOption.SHORTEN_NAMES;

public class ReplaceCancelProcessInstanceMethodsRecipe extends Recipe {

    /**
     * Instantiates a new instance.
     */
    public ReplaceCancelProcessInstanceMethodsRecipe() {
    }

    @Override
    public String getDisplayName() {
        return "Convert cancel process instance methods";
    }

    @Override
    public String getDescription() {
        return "Replaces Camunda 7 cancel process instance methods with Camunda 8 client wrapper.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaVisitor<ExecutionContext> javaVisitor = new AbstractRefasterJavaVisitor() {

            final MethodMatcher engineCancelProcessInstance = new MethodMatcher("org.camunda.bpm.engine.RuntimeService deleteProcessInstance(..)");

            final JavaTemplate cancelProcessInstanceWrapper = JavaTemplate
                    .builder("#{camundaClientWrapper:any(org.camunda.migration.rewrite.recipes.glue.CamundaClientWrapper)}.cancelProcessInstance(Long.valueOf(#{processInstanceId:any(java.lang.String)}));")
                    .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .build();


            @Override
            public J visitMethodInvocation(J.MethodInvocation elem, ExecutionContext ctx) {
                J.Identifier camundaClientWrapper = new J.Identifier(
                        Tree.randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        null,
                        "camundaClientWrapper",
                        JavaType.ShallowClass.build("org.camunda.migration.rewrite.recipes.glue.CamundaClientWrapper"),
                        null
                );
                if (engineCancelProcessInstance.matches(elem)) {
                    return embed(
                            cancelProcessInstanceWrapper.apply(getCursor(), elem.getCoordinates().replace(), camundaClientWrapper, elem.getArguments().get(0)),
                            getCursor(),
                            ctx,
                            SHORTEN_NAMES
                    );
                }
                return super.visitMethodInvocation(elem, ctx);
            }

        };
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>("org.camunda.bpm.engine.ProcessEngine", true),
                        new UsesMethod<>("org.camunda.bpm.engine.ProcessEngineServices getRuntimeService(..)", true),
                        new UsesMethod<>("org.camunda.bpm.engine.RuntimeService deleteProcessInstance(..)", true)
                ),
                javaVisitor
        );
    }
}