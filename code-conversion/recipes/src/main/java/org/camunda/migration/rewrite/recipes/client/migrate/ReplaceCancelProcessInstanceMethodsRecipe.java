package org.camunda.migration.rewrite.recipes.client.migrate;

import org.camunda.migration.rewrite.recipes.client.utils.ClientConstants;
import org.camunda.migration.rewrite.recipes.client.utils.ClientUtils;
import org.openrewrite.*;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class ReplaceCancelProcessInstanceMethodsRecipe extends Recipe {

  /** Instantiates a new instance. */
  public ReplaceCancelProcessInstanceMethodsRecipe(String CLIENT_WRAPPER_PACKAGE) {
    this.CLIENT_WRAPPER_PACKAGE = CLIENT_WRAPPER_PACKAGE;
  }

  @Override
  public String getDisplayName() {
    return "Convert cancel process instance methods";
  }

  @Override
  public String getDescription() {
    return "Replaces Camunda 7 cancel process instance methods with Camunda 8 client wrapper.";
  }

  String CLIENT_WRAPPER_PACKAGE;

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        Preconditions.and(
            new UsesType<>(ClientConstants.Type.PROCESS_ENGINE, true),
            new UsesMethod<>(ClientConstants.EngineMethod.GET_RUNTIME_SERVICE, true),
            new UsesMethod<>(ClientConstants.RuntimeServiceMethod.DELETE_PROCESS_INSTANCE, true));

    return Preconditions.check(
        check,
        new JavaVisitor<ExecutionContext>() {

          // method to be replaced
          final MethodMatcher engineDeleteProcessInstance =
              new MethodMatcher(ClientConstants.RuntimeServiceMethod.DELETE_PROCESS_INSTANCE);

          /*
           * This java template uses the camunda client wrapper to replace the above method. The
           * processInstanceId is expected to be a string. If any method returns a processInstanceId
           * elsewhere, it will also be cast to a string.
           */
          final JavaTemplate wrapperCancelProcessInstance =
              ClientUtils.createSimpleJavaTemplate(
                  "#{camundaClientWrapper:any("
                      + CLIENT_WRAPPER_PACKAGE
                      + ")}.cancelProcessInstance(Long.valueOf(#{processInstanceId:any(java.lang.String)}));");

          /**
           * One method is replaced with another method, thus visiting J.MethodInvocations works.
           * The base identifier of the method invocation changes from engine.getRuntimeService to
           * the camundaClientWrapper. This new identifier needs to be constructed manually,
           * providing simple name and java type.
           */
          @Override
          public J visitMethodInvocation(J.MethodInvocation methodInv, ExecutionContext ctx) {

            // create new identifier for first java template argument
            J.Identifier camundaClientWrapper =
                ClientUtils.createSimpleIdentifier(
                    "camundaClientWrapper", CLIENT_WRAPPER_PACKAGE);

            /*
             * if methodInv matches the defined method, replace it with the applied java template,
             * else resume tree traversal
             */
            if (engineDeleteProcessInstance.matches(methodInv)) {
              return wrapperCancelProcessInstance.apply(
                  getCursor(),
                  methodInv.getCoordinates().replace(),
                  camundaClientWrapper,
                  methodInv.getArguments().get(0));
            }
            return super.visitMethodInvocation(methodInv, ctx);
          }
        });
  }
}
