package org.camunda.migration.rewrite.recipes.client.prepare;

import org.camunda.migration.rewrite.recipes.client.utils.ClientConstants;
import org.camunda.migration.rewrite.recipes.client.utils.ClientUtils;
import org.openrewrite.*;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

public class EnsureProcessEngineRecipe extends Recipe {

  /** Instantiates a new instance. */
  public EnsureProcessEngineRecipe() {}

  @Override
  public String getDisplayName() {
    return "Ensuring process engine";
  }

  @Override
  public String getDescription() {
    return "Replaces specific services with process engine.";
  }

  private String getServiceReference(Expression expr) {
    return expr instanceof J.Identifier ident
            && ident.getType() instanceof JavaType.FullyQualified fqType
        ? fqType.getFullyQualifiedName()
        : null;
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        Preconditions.or(
            new UsesType<>(ClientConstants.Type.RUNTIME_SERVICE, true),
            new UsesType<>(ClientConstants.Type.TASK_SERVICE, true),
            new UsesType<>(ClientConstants.Type.REPOSITORY_SERVICE, true));

    return Preconditions.check(
        check,
        new JavaVisitor<ExecutionContext>() {

          final JavaTemplate processEngineRuntimeService =
              ClientUtils.createSimpleJavaTemplate(
                  "#{any(" + ClientConstants.Type.PROCESS_ENGINE + ")}.getRuntimeService()");

          final JavaTemplate processEngineTaskService =
              ClientUtils.createSimpleJavaTemplate(
                  "#{any(" + ClientConstants.Type.PROCESS_ENGINE + ")}.getTaskService()");

          final JavaTemplate processEngineRepositoryService =
              ClientUtils.createSimpleJavaTemplate(
                  "#{any(" + ClientConstants.Type.PROCESS_ENGINE + ")}.getRepositoryService()");

          /**
           * One method is replaced with another method, thus visiting J.MethodInvocations works.
           * The base identifier of the method invocation changes from runtimeService to engine.
           * This new identifier needs to be constructed manually, providing simple name and java
           * type.
           */
          @Override
          public J visitMethodInvocation(J.MethodInvocation elem, ExecutionContext ctx) {

            if (getServiceReference(elem.getSelect()) == null) {
              return super.visitMethodInvocation(elem, ctx);
            }

            // This is the replacement identifier for ProcessEngine
            J.Identifier processEngineJ =
                ClientUtils.createSimpleIdentifier("engine", ClientConstants.Type.PROCESS_ENGINE);

            return switch (getServiceReference(elem.getSelect())) {
              case ClientConstants.Type.RUNTIME_SERVICE ->
                  elem.withSelect(
                          processEngineRuntimeService.apply(
                              getCursor(), elem.getCoordinates().replace(), processEngineJ))
                      .withPrefix(Space.EMPTY);
              case ClientConstants.Type.TASK_SERVICE ->
                  elem.withSelect(
                          processEngineTaskService.apply(
                              getCursor(), elem.getCoordinates().replace(), processEngineJ))
                      .withPrefix(Space.EMPTY);
              case ClientConstants.Type.REPOSITORY_SERVICE ->
                  elem.withSelect(
                          processEngineRepositoryService.apply(
                              getCursor(), elem.getCoordinates().replace(), processEngineJ))
                      .withPrefix(Space.EMPTY);
              default -> super.visitMethodInvocation(elem, ctx);
            };
          }
        });
  }
}
