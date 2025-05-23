package org.camunda.migration.rewrite.recipes.client;

import org.openrewrite.*;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.template.internal.AbstractRefasterJavaVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.List;

import static org.openrewrite.java.template.internal.AbstractRefasterJavaVisitor.EmbeddingOption.SHORTEN_NAMES;

public class ReplaceSignalMethodsRecipe extends Recipe {

    /**
     * Instantiates a new instance.
     */
    public ReplaceSignalMethodsRecipe() {
    }

    @Override
    public String getDisplayName() {
        return "Convert signal broadcasting methods";
    }

    @Override
    public String getDescription() {
        return "Replaces Camunda 7 signal broadcasting methods with Camunda 8 client wrapper.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaVisitor<ExecutionContext> javaVisitor = new AbstractRefasterJavaVisitor() {

            // engine - simple method
            final MethodMatcher engineBroadcastSignalGlobally = new MethodMatcher("org.camunda.bpm.engine.RuntimeService signalEventReceived(String)");
            final MethodMatcher engineBroadcastSignalToOneExecution = new MethodMatcher("org.camunda.bpm.engine.RuntimeService signalEventReceived(String, String)");
            final MethodMatcher engineBroadcastSignalGloballyWithVariables = new MethodMatcher("org.camunda.bpm.engine.RuntimeService signalEventReceived(String, java.util.Map)");
            final MethodMatcher engineBroadcastSignalToOneExecutionWithVariables = new MethodMatcher("org.camunda.bpm.engine.RuntimeService signalEventReceived(String, String, java.util.Map)");

            // engine - builder pattern
            final MethodMatcher engineBroadcastSignalGloballyViaBuilderSend = new MethodMatcher("org.camunda.bpm.engine.runtime.SignalEventReceivedBuilder send(..)");
            final MethodMatcher engineBroadcastSignalGloballyViaBuilderSetVariables = new MethodMatcher("org.camunda.bpm.engine.runtime.SignalEventReceivedBuilder setVariables(..)");
            final MethodMatcher engineBroadcastSignalGloballyViaBuilderTenantId = new MethodMatcher("org.camunda.bpm.engine.runtime.SignalEventReceivedBuilder tenantId(..)");
            final MethodMatcher engineBroadcastSignalGloballyViaBuilderExecutionId = new MethodMatcher("org.camunda.bpm.engine.runtime.SignalEventReceivedBuilder executionId(..)");
            final MethodMatcher engineBroadcastSignalGloballyViaBuilderCreateSignalEvent = new MethodMatcher("org.camunda.bpm.engine.RuntimeService createSignalEvent(..)");

            // wrapper - simple methods
            final JavaTemplate wrapperBroadcastSignal = JavaTemplate
                    .builder("#{camundaClientWrapper:any(org.camunda.migration.rewrite.recipes.glue.CamundaClientWrapper)}.broadcastSignal(#{signalName:any(java.lang.String)});")
                    .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .build();

            final JavaTemplate wrapperBroadcastSignalWithVariables = JavaTemplate
                    .builder("#{camundaClientWrapper:any(org.camunda.migration.rewrite.recipes.glue.CamundaClientWrapper)}.broadcastSignalWithVariables(#{signalName:any(java.lang.String)}, #{variableMap:any(java.util.Map<java.lang.String,java.lang.Object>)});")
                    .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .build();

            final JavaTemplate wrapperBroadcastSignalWithTenantId = JavaTemplate
                    .builder("#{camundaClientWrapper:any(org.camunda.migration.rewrite.recipes.glue.CamundaClientWrapper)}.broadcastSignalWithTenantId(#{signalName:any(java.lang.String)}, #{tenantId:any(java.lang.String)});")
                    .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .build();

            final JavaTemplate wrapperBroadcastSignalWithTenantIdWithVariables = JavaTemplate
                    .builder("#{camundaClientWrapper:any(org.camunda.migration.rewrite.recipes.glue.CamundaClientWrapper)}.broadcastSignalWithTenantIdWithVariables(#{signalName:any(java.lang.String)}, #{tenantId:any(java.lang.String)}, #{variableMap:any(java.util.Map<java.lang.String,java.lang.Object>)});")
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

                TextComment removedExecutionIdComment = new TextComment(false, " executionId was removed", "\n" + elem.getPrefix().getIndent(), Markers.EMPTY);

                // replace simple methods
                if (engineBroadcastSignalGlobally.matches(elem)) {
                    return embed(
                            wrapperBroadcastSignal.apply(getCursor(), elem.getCoordinates().replace(), camundaClientWrapper, elem.getArguments().get(0)),
                            getCursor(),
                            ctx,
                            SHORTEN_NAMES
                    );
                }

                if (engineBroadcastSignalToOneExecution.matches(elem)) {
                    return embed(
                            wrapperBroadcastSignal.apply(getCursor(), elem.getCoordinates().replace(), camundaClientWrapper, elem.getArguments().get(0)).withComments(List.of(removedExecutionIdComment)),
                            getCursor(),
                            ctx,
                            SHORTEN_NAMES
                    );
                }

                if (engineBroadcastSignalGloballyWithVariables.matches(elem)) {
                    return embed(
                            wrapperBroadcastSignalWithVariables.apply(getCursor(), elem.getCoordinates().replace(), camundaClientWrapper, elem.getArguments().get(0), elem.getArguments().get(1)),
                            getCursor(),
                            ctx,
                            SHORTEN_NAMES
                    );
                }

                if (engineBroadcastSignalToOneExecutionWithVariables.matches(elem)) {
                    return embed(
                            wrapperBroadcastSignalWithVariables.apply(getCursor(), elem.getCoordinates().replace(), camundaClientWrapper, elem.getArguments().get(0), elem.getArguments().get(2)).withComments(List.of(removedExecutionIdComment)),
                            getCursor(),
                            ctx,
                            SHORTEN_NAMES
                    );
                }

                // replace builder pattern
                if (engineBroadcastSignalGloballyViaBuilderSend.matches(elem)) {
                    Expression signalName = null;
                    Expression variableMap = null;
                    Expression tenantId = null;
                    Expression executionId = null;

                    Expression current = elem;
                    while (current instanceof J.MethodInvocation mi) {
                        if (engineBroadcastSignalGloballyViaBuilderSetVariables.matches(mi)) {
                            variableMap = mi.getArguments().get(0);
                        } else if (engineBroadcastSignalGloballyViaBuilderTenantId.matches(mi)) {
                            tenantId = mi.getArguments().get(0);
                        } else if (engineBroadcastSignalGloballyViaBuilderExecutionId.matches(mi)) {
                            executionId = mi.getArguments().get(0);
                        } else if (engineBroadcastSignalGloballyViaBuilderCreateSignalEvent.matches(mi)) {
                            signalName = mi.getArguments().get(0);
                        }
                        current = mi.getSelect();
                    }
                    if(executionId != null) {
                        if (tenantId != null && variableMap != null) {
                            return embed(
                                    wrapperBroadcastSignalWithTenantIdWithVariables.apply(getCursor(), elem.getCoordinates().replace(), camundaClientWrapper, signalName, tenantId, variableMap).withComments(List.of(removedExecutionIdComment)),
                                    getCursor(),
                                    ctx,
                                    SHORTEN_NAMES
                            );
                        } else if (tenantId != null) {
                            return embed(
                                    wrapperBroadcastSignalWithTenantId.apply(getCursor(), elem.getCoordinates().replace(), camundaClientWrapper, signalName, tenantId).withComments(List.of(removedExecutionIdComment)),
                                    getCursor(),
                                    ctx,
                                    SHORTEN_NAMES
                            );
                        } else if (variableMap != null) {
                            return embed(
                                    wrapperBroadcastSignalWithVariables.apply(getCursor(), elem.getCoordinates().replace(), camundaClientWrapper, signalName, variableMap).withComments(List.of(removedExecutionIdComment)),
                                    getCursor(),
                                    ctx,
                                    SHORTEN_NAMES
                            );
                        } else {
                            return embed(
                                    wrapperBroadcastSignal.apply(getCursor(), elem.getCoordinates().replace(), camundaClientWrapper, signalName).withComments(List.of(removedExecutionIdComment)),
                                    getCursor(),
                                    ctx,
                                    SHORTEN_NAMES
                            );
                        }
                    } else {
                        if (tenantId != null && variableMap != null) {
                            return embed(
                                    wrapperBroadcastSignalWithTenantIdWithVariables.apply(getCursor(), elem.getCoordinates().replace(), camundaClientWrapper, signalName, tenantId, variableMap),
                                    getCursor(),
                                    ctx,
                                    SHORTEN_NAMES
                            );
                        } else if (tenantId != null) {
                            return embed(
                                    wrapperBroadcastSignalWithTenantId.apply(getCursor(), elem.getCoordinates().replace(), camundaClientWrapper, signalName, tenantId),
                                    getCursor(),
                                    ctx,
                                    SHORTEN_NAMES
                            );
                        } else if (variableMap != null) {
                            return embed(
                                    wrapperBroadcastSignalWithVariables.apply(getCursor(), elem.getCoordinates().replace(), camundaClientWrapper, signalName, variableMap),
                                    getCursor(),
                                    ctx,
                                    SHORTEN_NAMES
                            );
                        } else {
                            return embed(
                                    wrapperBroadcastSignal.apply(getCursor(), elem.getCoordinates().replace(), camundaClientWrapper, signalName),
                                    getCursor(),
                                    ctx,
                                    SHORTEN_NAMES
                            );
                        }
                    }
                }
                return super.visitMethodInvocation(elem, ctx);
            }

        };
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>("org.camunda.bpm.engine.ProcessEngine", true),
                        new UsesMethod<>("org.camunda.bpm.engine.ProcessEngineServices getRuntimeService(..)", true),
                        Preconditions.or(
                                new UsesMethod<>("org.camunda.bpm.engine.RuntimeService signalEventReceived(..)", true),
                                Preconditions.and(
                                        new UsesMethod<>("org.camunda.bpm.engine.runtime.SignalEventReceivedBuilder send(..)", true),
                                        new UsesMethod<>("org.camunda.bpm.engine.runtime.SignalEventReceivedBuilder setVariables(..)", true),
                                        new UsesMethod<>("org.camunda.bpm.engine.RuntimeService createSignalEvent(..)", true)
                                )
                        )
                ),
                javaVisitor
        );
    }
}