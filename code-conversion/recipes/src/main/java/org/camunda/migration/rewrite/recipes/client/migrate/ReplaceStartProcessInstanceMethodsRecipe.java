package org.camunda.migration.rewrite.recipes.client.migrate;

import static java.util.Collections.emptyList;

import java.util.List;
import org.camunda.migration.rewrite.recipes.utils.RecipeConstants;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.openrewrite.*;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

public class ReplaceStartProcessInstanceMethodsRecipe extends Recipe {

  /** Instantiates a new instance. */
  public ReplaceStartProcessInstanceMethodsRecipe(String CLIENT_WRAPPER_PACKAGE) {
    this.CLIENT_WRAPPER_PACKAGE = CLIENT_WRAPPER_PACKAGE;
  }

  @Override
  public String getDisplayName() {
    return "Convert create process instance methods";
  }

  @Override
  public String getDescription() {
    return "Replaces Camunda 7 create process instance methods with Camunda 8 client wrapper.";
  }

  String CLIENT_WRAPPER_PACKAGE;

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        Preconditions.and(
            new UsesType<>(RecipeConstants.Type.PROCESS_ENGINE, true),
            new UsesMethod<>(RecipeConstants.Method.GET_RUNTIME_SERVICE, true),
            Preconditions.or(
                new UsesMethod<>(
                        RecipeConstants.Method.START_PROCESS_INSTANCE_BY_KEY
                        + RecipeConstants.Parameters.ANY,
                        true),
                new UsesMethod<>(
                        RecipeConstants.Method.CREATE_PROCESS_INSTANCE_BY_KEY, true),
                new UsesMethod<>(
                        RecipeConstants.Method.START_PROCESS_INSTANCE_BY_ID
                        + RecipeConstants.Parameters.ANY,
                        true),
                new UsesMethod<>(
                        RecipeConstants.Method.CREATE_PROCESS_INSTANCE_BY_ID, true),
                new UsesMethod<>(
                        RecipeConstants.Method.START_PROCESS_INSTANCE_BY_MESSAGE
                        + RecipeConstants.Parameters.ANY,
                        true),
                new UsesMethod<>(
                        RecipeConstants.Method
                            .START_PROCESS_INSTANCE_BY_MESSAGE_AND_PROCESS_DEFINITION_ID
                        + RecipeConstants.Parameters.ANY,
                        true)));

    return Preconditions.check(
        check,
        new JavaVisitor<ExecutionContext>() {

          // engine - start by key methods
          final MethodMatcher engineStartByKey =
              new MethodMatcher(
                      RecipeConstants.Method.START_PROCESS_INSTANCE_BY_KEY
                      + RecipeConstants.Parameters.build("String"));
          final MethodMatcher engineStartByKeyAndBusinessKey =
              new MethodMatcher(
                      RecipeConstants.Method.START_PROCESS_INSTANCE_BY_KEY
                      + RecipeConstants.Parameters.build("String", "String"));
          final MethodMatcher engineStartByKeyAndVariables =
              new MethodMatcher(
                      RecipeConstants.Method.START_PROCESS_INSTANCE_BY_KEY
                      + RecipeConstants.Parameters.build("String", "java.util.Map"));
          final MethodMatcher engineStartByKeyAndBusinessKeyAndVariables =
              new MethodMatcher(
                      RecipeConstants.Method.START_PROCESS_INSTANCE_BY_KEY
                      + RecipeConstants.Parameters.build("String", "String", "java.util.Map"));

          // engine - start by id methods
          final MethodMatcher engineStartById =
              new MethodMatcher(
                      RecipeConstants.Method.START_PROCESS_INSTANCE_BY_ID
                      + RecipeConstants.Parameters.build("String"));
          final MethodMatcher engineStartByIdAndBusinessKey =
              new MethodMatcher(
                      RecipeConstants.Method.START_PROCESS_INSTANCE_BY_ID
                      + RecipeConstants.Parameters.build("String", "String"));
          final MethodMatcher engineStartByIdAndVariables =
              new MethodMatcher(
                      RecipeConstants.Method.START_PROCESS_INSTANCE_BY_ID
                      + RecipeConstants.Parameters.build("String", "java.util.Map"));
          final MethodMatcher engineStartByIdAndBusinessKeyAndVariables =
              new MethodMatcher(
                      RecipeConstants.Method.START_PROCESS_INSTANCE_BY_ID
                      + RecipeConstants.Parameters.build("String", "String", "java.util.Map"));

          // engine - start by message
          final MethodMatcher engineStartByMessage =
              new MethodMatcher(
                      RecipeConstants.Method.START_PROCESS_INSTANCE_BY_MESSAGE
                      + RecipeConstants.Parameters.build("String"));
          final MethodMatcher engineStartByMessageAndBusinessKey =
              new MethodMatcher(
                      RecipeConstants.Method.START_PROCESS_INSTANCE_BY_MESSAGE
                      + RecipeConstants.Parameters.build("String", "String"));
          final MethodMatcher engineStartByMessageAndVariables =
              new MethodMatcher(
                      RecipeConstants.Method.START_PROCESS_INSTANCE_BY_MESSAGE
                      + RecipeConstants.Parameters.build("String", "java.util.Map"));
          final MethodMatcher engineStartByMessageAndBusinessKeyAndVariables =
              new MethodMatcher(
                      RecipeConstants.Method.START_PROCESS_INSTANCE_BY_MESSAGE
                      + RecipeConstants.Parameters.build("String", "String", "java.util.Map"));

          // engine - start by message and processDefinitionId
          final MethodMatcher engineStartByMessageAndProcessDefinitionId =
              new MethodMatcher(
                      RecipeConstants.Method
                          .START_PROCESS_INSTANCE_BY_MESSAGE_AND_PROCESS_DEFINITION_ID
                      + RecipeConstants.Parameters.build("String", "String"));
          final MethodMatcher engineStartByMessageAndProcessDefinitionIdAndBusinessKey =
              new MethodMatcher(
                      RecipeConstants.Method
                          .START_PROCESS_INSTANCE_BY_MESSAGE_AND_PROCESS_DEFINITION_ID
                      + RecipeConstants.Parameters.build("String", "String", "String"));
          final MethodMatcher engineStartByMessageAndProcessDefinitionIdAndVariables =
              new MethodMatcher(
                      RecipeConstants.Method
                          .START_PROCESS_INSTANCE_BY_MESSAGE_AND_PROCESS_DEFINITION_ID
                      + RecipeConstants.Parameters.build("String", "String", "java.util.Map"));
          final MethodMatcher engineStartByMessageAndProcessDefinitionIdAndBusinessKeyAndVariables =
              new MethodMatcher(
                      RecipeConstants.Method
                          .START_PROCESS_INSTANCE_BY_MESSAGE_AND_PROCESS_DEFINITION_ID
                      + RecipeConstants.Parameters.build(
                          "String", "String", "String", "java.util.Map"));

          // engine - create by key builder pattern
          final MethodMatcher engineCreateByKey =
              new MethodMatcher(
                      RecipeConstants.Method.CREATE_PROCESS_INSTANCE_BY_KEY);
          final MethodMatcher engineCreateById =
              new MethodMatcher(RecipeConstants.Method.CREATE_PROCESS_INSTANCE_BY_ID);
          final MethodMatcher engineCreateBusinessKey =
              new MethodMatcher(
                      RecipeConstants.Method.PROCESS_INSTANCE_BUILDER_BUSINESS_KEY);
          final MethodMatcher engineCreateTenantId =
              new MethodMatcher(
                      RecipeConstants.Method.PROCESS_INSTANCE_BUILDER_TENANT_ID);
          final MethodMatcher engineCreateSetVariables =
              new MethodMatcher(
                      RecipeConstants.Method.PROCESS_INSTANCE_BUILDER_SET_VARIABLES);
          final MethodMatcher engineCreateExecute =
              new MethodMatcher(
                      RecipeConstants.Method.PROCESS_INSTANCE_BUILDER_EXECUTE);

          // wrapper - create by BPMN Model Identifier methods
          final JavaTemplate wrapperCreateByBPMNModelIdentifier =
              RecipeUtils.createSimpleJavaTemplate(
                  "#{camundaClientWrapper:any("
                      + CLIENT_WRAPPER_PACKAGE
                      + ")}.createProcessByBPMNModelIdentifier(#{processDefinitionId:any(java.lang.String)});");

          final JavaTemplate wrapperCreateByBPMNModelIdentifierWithTenantId =
              RecipeUtils.createSimpleJavaTemplate(
                  "#{camundaClientWrapper:any("
                      + CLIENT_WRAPPER_PACKAGE
                      + ")}.createProcessByBPMNModelIdentifierWithTenantId(#{processDefinitionId:any(java.lang.String)}, #{tenantId:any(java.lang.String)});");

          final JavaTemplate wrapperCreateByBPMNModelIdentifierWithVariables =
              RecipeUtils.createSimpleJavaTemplate(
                  "#{camundaClientWrapper:any("
                      + CLIENT_WRAPPER_PACKAGE
                      + ")}.createProcessByBPMNModelIdentifierWithVariables(#{processDefinitionId:any(java.lang.String)}, #{variableMap:any(java.util.Map<java.lang.String,java.lang.Object>)});");

          final JavaTemplate wrapperCreateByBPMNModelIdentifierWithTenantIdWithVariables =
              RecipeUtils.createSimpleJavaTemplate(
                  "#{camundaClientWrapper:any("
                      + CLIENT_WRAPPER_PACKAGE
                      + ")}.createProcessByBPMNModelIdentifierWithTenantIdWithVariables(#{processDefinitionId:any(java.lang.String)}, #{tenantId:any(java.lang.String)}, #{variableMap:any(java.util.Map<java.lang.String,java.lang.Object>)});");

          // wrapper - create by key assigned on deployment methods
          final JavaTemplate wrapperCreateByKeyAssignedOnDeployment =
              RecipeUtils.createSimpleJavaTemplate(
                  "#{camundaClientWrapper:any("
                      + CLIENT_WRAPPER_PACKAGE
                      + ")}.createProcessByKeyAssignedOnDeployment(#{processDefinitionKey:any(java.lang.String)});");

          final JavaTemplate wrapperCreateByKeyAssignedOnDeploymentWithTenantId =
              RecipeUtils.createSimpleJavaTemplate(
                  "#{camundaClientWrapper:any("
                      + CLIENT_WRAPPER_PACKAGE
                      + ")}.createProcessByKeyAssignedOnDeploymentWithTenantId(#{processDefinitionKey:any(java.lang.String)}, #{tenantId:any(java.lang.String)});");

          final JavaTemplate wrapperCreateByKeyAssignedOnDeploymentWithVariables =
              RecipeUtils.createSimpleJavaTemplate(
                  "#{camundaClientWrapper:any("
                      + CLIENT_WRAPPER_PACKAGE
                      + ")}.createProcessByKeyAssignedOnDeploymentWithVariables(#{processDefinitionKey:any(java.lang.String)}, #{variableMap:any(java.util.Map<java.lang.String,java.lang.Object>)});");

          final JavaTemplate wrapperCreateByKeyAssignedOnDeploymentWithTenantIdWithVariables =
              RecipeUtils.createSimpleJavaTemplate(
                  "#{camundaClientWrapper:any("
                      + CLIENT_WRAPPER_PACKAGE
                      + ")}.createProcessByKeyAssignedOnDeploymentWithTenantIdWithVariables(#{processDefinitionKey:any(java.lang.String)}, #{tenantId:any(java.lang.String)}, #{variableMap:any(java.util.Map<java.lang.String,java.lang.Object>)});");

          // wrapper - correlate message
          final JavaTemplate wrapperCorrelateMessage =
              RecipeUtils.createSimpleJavaTemplate(
                  "#{camundaClientWrapper:any("
                      + CLIENT_WRAPPER_PACKAGE
                      + ")}.correlateMessage(#{messageName:any(java.lang.String)}, \"someCorrelationKey\");");

          final JavaTemplate wrapperCorrelateMessageWithTenantId =
              RecipeUtils.createSimpleJavaTemplate(
                  "#{camundaClientWrapper:any("
                      + CLIENT_WRAPPER_PACKAGE
                      + ")}.correlateMessageWithTenantId(#{messageName:any(java.lang.String)}, \"someCorrelationKey\", #{tenantId:any(java.lang.String)});");

          final JavaTemplate wrapperCorrelateMessageWithVariables =
              RecipeUtils.createSimpleJavaTemplate(
                  "#{camundaClientWrapper:any("
                      + CLIENT_WRAPPER_PACKAGE
                      + ")}.correlateMessageWithVariables(#{messageName:any(java.lang.String)}, \"someCorrelationKey\", #{variableMap:any(java.util.Map<java.lang.String,java.lang.Object>)});");

          final JavaTemplate wrapperCorrelateMessageWithTenantIdWithVariables =
              RecipeUtils.createSimpleJavaTemplate(
                  "#{camundaClientWrapper:any("
                      + CLIENT_WRAPPER_PACKAGE
                      + ")}.correlateMessageWithTenantIdWithVariables(#{messageName:any(java.lang.String)}, \"someCorrelationKey\", #{tenantId:any(java.lang.String)}, #{variableMap:any(java.util.Map<java.lang.String,java.lang.Object>)});");

          /**
           * Variable declarations are visited. Types are adjusted appropriately. Initializers are
           * replaced by wrapper methods + class methods.
           */
          @Override
          public J visitVariableDeclarations(J.VariableDeclarations decls, ExecutionContext ctx) {

            // Analyze first variable
            J.VariableDeclarations.NamedVariable firstVar = decls.getVariables().get(0);
            J.Identifier originalName = firstVar.getName();
            JavaType.Variable originalVariableType = firstVar.getVariableType();
            Expression originalInitializer = firstVar.getInitializer();

            if (originalInitializer instanceof J.MethodInvocation meth) {
              if (engineStartByMessage.matches(meth)
                  || engineStartByMessageAndBusinessKey.matches(meth)
                  || engineStartByMessageAndVariables.matches(meth)
                  || engineStartByMessageAndBusinessKeyAndVariables.matches(meth)
                  || engineStartByMessageAndProcessDefinitionId.matches(meth)
                  || engineStartByMessageAndProcessDefinitionIdAndBusinessKey.matches(meth)
                  || engineStartByMessageAndProcessDefinitionIdAndVariables.matches(meth)
                  || engineStartByMessageAndProcessDefinitionIdAndBusinessKeyAndVariables.matches(
                      meth)) {
                decls =
                    changeVariableTypeIfApplicable(
                            decls,
                            RecipeConstants.Type.ENGINE_PROCESS_INSTANCE,
                            RecipeConstants.Type.CORRELATION_MESSAGE_RESPONSE);
              } else if (engineStartByKey.matches(meth)
                  || engineStartByKeyAndVariables.matches(meth)
                  || engineStartByKeyAndBusinessKey.matches(meth)
                  || engineStartByKeyAndBusinessKeyAndVariables.matches(meth)
                  || engineStartById.matches(meth)
                  || engineStartByIdAndVariables.matches(meth)
                  || engineStartByIdAndBusinessKey.matches(meth)
                  || engineStartByIdAndBusinessKeyAndVariables.matches(meth)
                  || engineCreateExecute.matches(meth)) {
                decls =
                    changeVariableTypeIfApplicable(
                            decls,
                            RecipeConstants.Type.ENGINE_PROCESS_INSTANCE,
                            RecipeConstants.Type.PROCESS_INSTANCE_EVENT);
              }
            }

            maybeRemoveImport(RecipeConstants.Type.ENGINE_PROCESS_INSTANCE);
            return super.visitVariableDeclarations(decls, ctx);
          }

          /** Method invocations are visited and replaced */
          @Override
          public J visitMethodInvocation(J.MethodInvocation elem, ExecutionContext ctx) {
            J.Identifier camundaClientWrapper =
                RecipeUtils.createSimpleIdentifier("camundaClientWrapper", CLIENT_WRAPPER_PACKAGE);

            // a comment is added in case the businessKey was removed
            TextComment removedBusinessKeyComment =
                new TextComment(
                    false,
                    " businessKey was removed",
                    "\n" + elem.getPrefix().getIndent(),
                    Markers.EMPTY);

            // a comment is added in case the processDefinitionId was removed
            TextComment removedProcessDefinitionIdComment =
                new TextComment(
                    false,
                    " processDefinitionId was removed",
                    "\n" + elem.getPrefix().getIndent(),
                    Markers.EMPTY);

            // replace by key methods
            if (engineStartByKey.matches(elem)) {
              return wrapperCreateByBPMNModelIdentifier.apply(
                  getCursor(),
                  elem.getCoordinates().replace(),
                  camundaClientWrapper,
                  elem.getArguments().get(0));
            }

            if (engineStartByKeyAndBusinessKey.matches(elem)) {
              return wrapperCreateByBPMNModelIdentifier
                  .apply(
                      getCursor(),
                      elem.getCoordinates().replace(),
                      camundaClientWrapper,
                      elem.getArguments().get(0))
                  .withComments(List.of(removedBusinessKeyComment));
            }

            if (engineStartByKeyAndVariables.matches(elem)) {
              return wrapperCreateByBPMNModelIdentifierWithVariables.apply(
                  getCursor(),
                  elem.getCoordinates().replace(),
                  camundaClientWrapper,
                  elem.getArguments().get(0),
                  elem.getArguments().get(1));
            }

            if (engineStartByKeyAndBusinessKeyAndVariables.matches(elem)) {
              return wrapperCreateByBPMNModelIdentifierWithVariables
                  .apply(
                      getCursor(),
                      elem.getCoordinates().replace(),
                      camundaClientWrapper,
                      elem.getArguments().get(0),
                      elem.getArguments().get(2))
                  .withComments(List.of(removedBusinessKeyComment));
            }

            // replace by id methods
            if (engineStartById.matches(elem)) {
              return wrapperCreateByKeyAssignedOnDeployment.apply(
                  getCursor(),
                  elem.getCoordinates().replace(),
                  camundaClientWrapper,
                  elem.getArguments().get(0));
            }

            if (engineStartByIdAndBusinessKey.matches(elem)) {
              return wrapperCreateByKeyAssignedOnDeployment
                  .apply(
                      getCursor(),
                      elem.getCoordinates().replace(),
                      camundaClientWrapper,
                      elem.getArguments().get(0))
                  .withComments(List.of(removedBusinessKeyComment));
            }

            if (engineStartByIdAndVariables.matches(elem)) {
              return wrapperCreateByKeyAssignedOnDeploymentWithVariables.apply(
                  getCursor(),
                  elem.getCoordinates().replace(),
                  camundaClientWrapper,
                  elem.getArguments().get(0),
                  elem.getArguments().get(1));
            }

            if (engineStartByIdAndBusinessKeyAndVariables.matches(elem)) {
              return wrapperCreateByKeyAssignedOnDeploymentWithVariables
                  .apply(
                      getCursor(),
                      elem.getCoordinates().replace(),
                      camundaClientWrapper,
                      elem.getArguments().get(0),
                      elem.getArguments().get(2))
                  .withComments(List.of(removedBusinessKeyComment));
            }

            // replace by message
            if (engineStartByMessage.matches(elem)) {
              return wrapperCorrelateMessage.apply(
                  getCursor(),
                  elem.getCoordinates().replace(),
                  camundaClientWrapper,
                  elem.getArguments().get(0));
            }

            if (engineStartByMessageAndBusinessKey.matches(elem)) {
              return wrapperCorrelateMessage
                  .apply(
                      getCursor(),
                      elem.getCoordinates().replace(),
                      camundaClientWrapper,
                      elem.getArguments().get(0))
                  .withComments(List.of(removedBusinessKeyComment));
            }

            if (engineStartByMessageAndVariables.matches(elem)) {
              return wrapperCorrelateMessageWithVariables.apply(
                  getCursor(),
                  elem.getCoordinates().replace(),
                  camundaClientWrapper,
                  elem.getArguments().get(0),
                  elem.getArguments().get(1));
            }

            if (engineStartByMessageAndBusinessKeyAndVariables.matches(elem)) {
              return wrapperCorrelateMessageWithVariables
                  .apply(
                      getCursor(),
                      elem.getCoordinates().replace(),
                      camundaClientWrapper,
                      elem.getArguments().get(0),
                      elem.getArguments().get(2))
                  .withComments(List.of(removedBusinessKeyComment));
            }

            // replace by message and processDefinitionId
            if (engineStartByMessageAndProcessDefinitionId.matches(elem)) {
              return wrapperCorrelateMessage
                  .apply(
                      getCursor(),
                      elem.getCoordinates().replace(),
                      camundaClientWrapper,
                      elem.getArguments().get(0))
                  .withComments(List.of(removedProcessDefinitionIdComment));
            }

            if (engineStartByMessageAndProcessDefinitionIdAndBusinessKey.matches(elem)) {
              return wrapperCorrelateMessage
                  .apply(
                      getCursor(),
                      elem.getCoordinates().replace(),
                      camundaClientWrapper,
                      elem.getArguments().get(0))
                  .withComments(
                      List.of(removedBusinessKeyComment, removedProcessDefinitionIdComment));
            }

            if (engineStartByMessageAndProcessDefinitionIdAndVariables.matches(elem)) {
              return wrapperCorrelateMessageWithVariables
                  .apply(
                      getCursor(),
                      elem.getCoordinates().replace(),
                      camundaClientWrapper,
                      elem.getArguments().get(0),
                      elem.getArguments().get(2))
                  .withComments(List.of(removedProcessDefinitionIdComment));
            }

            if (engineStartByMessageAndProcessDefinitionIdAndBusinessKeyAndVariables.matches(
                elem)) {
              return wrapperCorrelateMessageWithVariables
                  .apply(
                      getCursor(),
                      elem.getCoordinates().replace(),
                      camundaClientWrapper,
                      elem.getArguments().get(0),
                      elem.getArguments().get(3))
                  .withComments(
                      List.of(removedBusinessKeyComment, removedProcessDefinitionIdComment));
            }

            // replace builder patterns by key and id
            if (engineCreateExecute.matches(elem)) {
              Expression processDefinitionKey = null;
              Expression processDefinitionId = null;
              Expression variableMap = null;
              Expression tenantId = null;
              Expression businessKey = null;

              // loop through builder pattern select methods to extract information
              Expression current = elem;
              while (current instanceof J.MethodInvocation mi) {
                if (engineCreateSetVariables.matches(mi)) {
                  variableMap = mi.getArguments().get(0);
                } else if (engineCreateTenantId.matches(mi)) {
                  tenantId = mi.getArguments().get(0);
                } else if (engineCreateBusinessKey.matches(mi)) {
                  businessKey = mi.getArguments().get(0);
                } else if (engineCreateById.matches(mi)) {
                  processDefinitionId = mi.getArguments().get(0);
                } else if (engineCreateByKey.matches(mi)) {
                  processDefinitionKey = mi.getArguments().get(0);
                }
                current = mi.getSelect();
              }

              if (processDefinitionKey != null) {
                if (businessKey != null) {
                  if (tenantId != null && variableMap != null) {
                    return wrapperCreateByBPMNModelIdentifierWithTenantIdWithVariables
                        .apply(
                            getCursor(),
                            elem.getCoordinates().replace(),
                            camundaClientWrapper,
                            processDefinitionKey,
                            tenantId,
                            variableMap)
                        .withComments(List.of(removedBusinessKeyComment));
                  } else if (variableMap != null) {
                    return wrapperCreateByBPMNModelIdentifierWithVariables
                        .apply(
                            getCursor(),
                            elem.getCoordinates().replace(),
                            camundaClientWrapper,
                            processDefinitionKey,
                            variableMap)
                        .withComments(List.of(removedBusinessKeyComment));
                  } else if (tenantId != null) {
                    return wrapperCreateByBPMNModelIdentifierWithTenantId
                        .apply(
                            getCursor(),
                            elem.getCoordinates().replace(),
                            camundaClientWrapper,
                            processDefinitionKey,
                            tenantId)
                        .withComments(List.of(removedBusinessKeyComment));
                  } else {
                    return wrapperCreateByBPMNModelIdentifier
                        .apply(
                            getCursor(),
                            elem.getCoordinates().replace(),
                            camundaClientWrapper,
                            processDefinitionKey)
                        .withComments(List.of(removedBusinessKeyComment));
                  }
                } else {
                  if (tenantId != null && variableMap != null) {
                    return wrapperCreateByBPMNModelIdentifierWithTenantIdWithVariables.apply(
                        getCursor(),
                        elem.getCoordinates().replace(),
                        camundaClientWrapper,
                        processDefinitionKey,
                        tenantId,
                        variableMap);
                  } else if (variableMap != null) {
                    return wrapperCreateByBPMNModelIdentifierWithVariables.apply(
                        getCursor(),
                        elem.getCoordinates().replace(),
                        camundaClientWrapper,
                        processDefinitionKey,
                        variableMap);
                  } else if (tenantId != null) {
                    return wrapperCreateByBPMNModelIdentifierWithTenantId.apply(
                        getCursor(),
                        elem.getCoordinates().replace(),
                        camundaClientWrapper,
                        processDefinitionKey,
                        tenantId);
                  } else {
                    return wrapperCreateByBPMNModelIdentifier.apply(
                        getCursor(),
                        elem.getCoordinates().replace(),
                        camundaClientWrapper,
                        processDefinitionKey);
                  }
                }
              } else if (processDefinitionId != null) {
                if (businessKey != null) {
                  if (tenantId != null && variableMap != null) {
                    return wrapperCreateByKeyAssignedOnDeploymentWithTenantIdWithVariables
                        .apply(
                            getCursor(),
                            elem.getCoordinates().replace(),
                            camundaClientWrapper,
                            processDefinitionId,
                            tenantId,
                            variableMap)
                        .withComments(List.of(removedBusinessKeyComment));
                  } else if (variableMap != null) {
                    return wrapperCreateByKeyAssignedOnDeploymentWithVariables
                        .apply(
                            getCursor(),
                            elem.getCoordinates().replace(),
                            camundaClientWrapper,
                            processDefinitionId,
                            variableMap)
                        .withComments(List.of(removedBusinessKeyComment));
                  } else if (tenantId != null) {
                    return wrapperCreateByKeyAssignedOnDeploymentWithTenantId
                        .apply(
                            getCursor(),
                            elem.getCoordinates().replace(),
                            camundaClientWrapper,
                            processDefinitionId,
                            tenantId)
                        .withComments(List.of(removedBusinessKeyComment));
                  } else {
                    return wrapperCreateByKeyAssignedOnDeployment
                        .apply(
                            getCursor(),
                            elem.getCoordinates().replace(),
                            camundaClientWrapper,
                            processDefinitionId)
                        .withComments(List.of(removedBusinessKeyComment));
                  }
                } else {
                  if (tenantId != null && variableMap != null) {
                    return wrapperCreateByKeyAssignedOnDeploymentWithTenantIdWithVariables.apply(
                        getCursor(),
                        elem.getCoordinates().replace(),
                        camundaClientWrapper,
                        processDefinitionId,
                        tenantId,
                        variableMap);
                  } else if (variableMap != null) {
                    return wrapperCreateByKeyAssignedOnDeploymentWithVariables.apply(
                        getCursor(),
                        elem.getCoordinates().replace(),
                        camundaClientWrapper,
                        processDefinitionId,
                        variableMap);
                  } else if (tenantId != null) {
                    return wrapperCreateByKeyAssignedOnDeploymentWithTenantId.apply(
                        getCursor(),
                        elem.getCoordinates().replace(),
                        camundaClientWrapper,
                        processDefinitionId,
                        tenantId);
                  } else {
                    return wrapperCreateByKeyAssignedOnDeployment.apply(
                        getCursor(),
                        elem.getCoordinates().replace(),
                        camundaClientWrapper,
                        processDefinitionId);
                  }
                }
              }
            }

            if (elem.getSelect() != null
                && TypeUtils.isOfType(
                    elem.getSelect().getType(),
                    JavaType.ShallowClass.build(RecipeConstants.Type.ENGINE_PROCESS_INSTANCE))
                && (elem.getSimpleName().equals("getProcessInstanceId")
                    || elem.getSimpleName().equals("getId"))) {
              JavaTemplate getProcessInstanceKeyToString =
                  RecipeUtils.createSimpleJavaTemplate(
                          "#{any("
                          + RecipeConstants.Type.CORRELATION_MESSAGE_RESPONSE
                          + ")}.getProcessInstanceKey().toString()");

              return getProcessInstanceKeyToString.apply(
                  getCursor(), elem.getCoordinates().replace(), elem.getSelect());
            }

            maybeRemoveImport(RecipeConstants.Type.ENGINE_PROCESS_INSTANCE);

            // no match, continue tree traversal
            return super.visitMethodInvocation(elem, ctx);
          }

          /**
           * See
           * https://github.com/openrewrite/rewrite/blob/main/rewrite-java/src/main/java/org/openrewrite/java/ChangeMethodInvocationReturnType.java
           */
          private J.VariableDeclarations changeVariableTypeIfApplicable(
              J.VariableDeclarations multiVariable,
              String fromFullyQualifiedTypeName,
              String toFullyQualifiedTypeName) {
            if (!fromFullyQualifiedTypeName.equals(
                multiVariable.getTypeAsFullyQualified().getFullyQualifiedName())) {
              return multiVariable;
            } else {
              multiVariable =
                  multiVariable
                      .withType(JavaType.buildType(toFullyQualifiedTypeName))
                      .withTypeExpression(
                          new J.Identifier(
                              multiVariable.getTypeExpression().getId(),
                              multiVariable.getTypeExpression().getPrefix(),
                              Markers.EMPTY,
                              emptyList(),
                              toFullyQualifiedTypeName.substring(
                                  toFullyQualifiedTypeName.lastIndexOf('.') + 1),
                              JavaType.buildType(toFullyQualifiedTypeName),
                              null));

              maybeRemoveImport(fromFullyQualifiedTypeName);
              maybeAddImport(toFullyQualifiedTypeName, false);

              return multiVariable;
            }
          }
        });
  }
}
