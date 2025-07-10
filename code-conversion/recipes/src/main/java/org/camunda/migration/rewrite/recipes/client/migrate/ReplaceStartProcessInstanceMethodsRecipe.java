package org.camunda.migration.rewrite.recipes.client.migrate;

import java.util.*;
import org.camunda.migration.rewrite.recipes.sharedRecipes.AbstractMigrationRecipe;
import org.camunda.migration.rewrite.recipes.utils.RecipeConstants;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.openrewrite.*;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;

public class ReplaceStartProcessInstanceMethodsRecipe extends AbstractMigrationRecipe {

  @Override
  public String getDisplayName() {
    return "Migrates start process instance variable declarations and method invocations based on rules";
  }

  @Override
  public String getDescription() {
    return "This recipe extends the abstract migration recipe with rules specific to starting process instances.";
  }

  @Override
  protected TreeVisitor<?, ExecutionContext> preconditions() {
    return Preconditions.and(
        Preconditions.or(
            new UsesType<>(RecipeConstants.Type.PROCESS_ENGINE, true),
            new UsesType<>(RecipeConstants.Type.RUNTIME_SERVICE, true)),
        Preconditions.or(
            new UsesMethod<>(
                RecipeConstants.Method.START_PROCESS_INSTANCE_BY_KEY
                    + RecipeConstants.Parameters.ANY,
                true),
            new UsesMethod<>(RecipeConstants.Method.CREATE_PROCESS_INSTANCE_BY_KEY, true),
            new UsesMethod<>(
                RecipeConstants.Method.START_PROCESS_INSTANCE_BY_ID
                    + RecipeConstants.Parameters.ANY,
                true),
            new UsesMethod<>(RecipeConstants.Method.CREATE_PROCESS_INSTANCE_BY_ID, true),
            new UsesMethod<>(
                RecipeConstants.Method.START_PROCESS_INSTANCE_BY_MESSAGE
                    + RecipeConstants.Parameters.ANY,
                true),
            new UsesMethod<>(
                RecipeConstants.Method.START_PROCESS_INSTANCE_BY_MESSAGE_AND_PROCESS_DEFINITION_ID
                    + RecipeConstants.Parameters.ANY,
                true)));
  }

  @Override
  protected List<RecipeUtils.MethodInvocationSimpleReplacementSpec> simpleMethodInvocations() {
    return List.of(
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "startProcessInstanceByKey(String processDefinitionKey)"
                "org.camunda.bpm.engine.RuntimeService startProcessInstanceByKey(java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                            #{camundaClient:any(io.camunda.client.CamundaClient)}
                                .newCreateInstanceCommand()
                                .bpmnProcessId(#{processDefinitionId:any(String)})
                                .latestVersion()
                                .send()
                                .join();
                            """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg(
                    "processDefinitionKey", 0)),
            Collections.emptyList()),
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "startProcessInstanceByKey(String processDefinitionKey, String
                // businessKey)"
                "org.camunda.bpm.engine.RuntimeService startProcessInstanceByKey(java.lang.String, java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                            #{camundaClient:any(io.camunda.client.CamundaClient)}
                                .newCreateInstanceCommand()
                                .bpmnProcessId(#{processDefinitionId:any(String)})
                                .latestVersion()
                                .send()
                                .join();
                            """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg(
                    "processDefinitionKey", 0)),
            List.of(" businessKey was removed")),
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "startProcessInstanceByKey(String processDefinitionKey, Map<String, Object>
                // variableMap)"
                "org.camunda.bpm.engine.RuntimeService startProcessInstanceByKey(java.lang.String, java.util.Map)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                             #{camundaClient:any(io.camunda.client.CamundaClient)}
                                .newCreateInstanceCommand()
                                .bpmnProcessId(#{processDefinitionId:any(String)})
                                .latestVersion()
                                .variables(#{variableMap:any(java.util.Map)})
                                .send()
                                .join();
                              """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg(
                    "processDefinitionKey", 0),
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("variableMap", 1)),
            Collections.emptyList()),
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "startProcessInstanceByKey(String processDefinitionKey, String businessKey,
                // Map<String, Object> variableMap)"
                "org.camunda.bpm.engine.RuntimeService startProcessInstanceByKey(java.lang.String, java.lang.String, java.util.Map)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                             #{camundaClient:any(io.camunda.client.CamundaClient)}
                                .newCreateInstanceCommand()
                                .bpmnProcessId(#{processDefinitionId:any(String)})
                                .latestVersion()
                                .variables(#{variableMap:any(java.util.Map)})
                                .send()
                                .join();
                              """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg(
                    "processDefinitionKey", 0),
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("variableMap", 2)),
            List.of(" businessKey was removed")),
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "startProcessInstanceById(String processDefinitionId)"
                "org.camunda.bpm.engine.RuntimeService startProcessInstanceById(java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                            #{camundaClient:any(io.camunda.client.CamundaClient)}
                                .newCreateInstanceCommand()
                                .processDefinitionKey(Long.valueOf(#{processDefinitionId:any(String)}))
                                .send()
                                .join();
                            """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg(
                    "processDefinitionId", 0)),
            Collections.emptyList()),
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "startProcessInstanceById(String processDefinitionId, String businessKey)"
                "org.camunda.bpm.engine.RuntimeService startProcessInstanceById(java.lang.String, java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                            #{camundaClient:any(io.camunda.client.CamundaClient)}
                                .newCreateInstanceCommand()
                                .processDefinitionKey(Long.valueOf(#{processDefinitionId:any(String)}))
                                .send()
                                .join();
                            """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg(
                    "processDefinitionId", 0)),
            List.of(" businessKey was removed")),
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "startProcessInstanceById(String processDefinitionId, Map<String, Object>
                // variableMap)"
                "org.camunda.bpm.engine.RuntimeService startProcessInstanceById(java.lang.String, java.util.Map)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                            #{camundaClient:any(io.camunda.client.CamundaClient)}
                                .newCreateInstanceCommand()
                                .processDefinitionKey(Long.valueOf(#{processDefinitionId:any(String)}))
                                .variables(#{variableMap:any(java.util.Map)})
                                .send()
                                .join();
                            """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg(
                    "processDefinitionId", 0),
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("variableMap", 1)),
            Collections.emptyList()),
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "startProcessInstanceById(String processDefinitionId, String businessKey,
                // Map<String, Object> variableMap)"
                "org.camunda.bpm.engine.RuntimeService startProcessInstanceById(java.lang.String, java.lang.String, java.util.Map)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                            #{camundaClient:any(io.camunda.client.CamundaClient)}
                                .newCreateInstanceCommand()
                                .processDefinitionKey(Long.valueOf(#{processDefinitionId:any(String)}))
                                .variables(#{variableMap:any(java.util.Map)})
                                .send()
                                .join();
                            """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg(
                    "processDefinitionId", 0),
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("variableMap", 2)),
            List.of(" businessKey was removed")),
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "startProcessInstanceByMessage(String messageName)"
                "org.camunda.bpm.engine.RuntimeService startProcessInstanceByMessage(java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                            #{camundaClient:any(io.camunda.client.CamundaClient)}
                              .newCorrelateMessageCommand()
                              .messageName(#{messageName:any(String)})
                              .correlationKey("add correlationKey here")
                              .send()
                              .join();
                            """),
            "io.camunda.client.api.response.CorrelateMessageResponse",
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("messageName", 0)),
            List.of(" please configure you correlationKey")),
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "startProcessInstanceByMessage(String messageName, String businessKey)"
                "org.camunda.bpm.engine.RuntimeService startProcessInstanceByMessage(java.lang.String, java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                            #{camundaClient:any(io.camunda.client.CamundaClient)}
                              .newCorrelateMessageCommand()
                              .messageName(#{messageName:any(String)})
                              .correlationKey("add correlationKey here")
                              .send()
                              .join();
                            """),
            "io.camunda.client.api.response.CorrelateMessageResponse",
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("messageName", 0)),
            List.of(" please configure you correlationKey", " businessKey was removed")),
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "startProcessInstanceByMessage(String messageName, Map<String, Object>
                // variableMap)"
                "org.camunda.bpm.engine.RuntimeService startProcessInstanceByMessage(java.lang.String, java.util.Map)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                            #{camundaClient:any(io.camunda.client.CamundaClient)}
                                .newCorrelateMessageCommand()
                                .messageName(#{messageName:any(String)})
                                .correlationKey("add correlationKey here")
                                .variables(#{variableMap:any(java.util.Map)})
                                .send()
                                .join();
                            """),
            "io.camunda.client.api.response.CorrelateMessageResponse",
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("messageName", 0),
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("variableMap", 1)),
            List.of(" please configure you correlationKey")),
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "startProcessInstanceByMessage(String messageName, String businessKey,
                // Map<String, Object> variableMap)"
                "org.camunda.bpm.engine.RuntimeService startProcessInstanceByMessage(java.lang.String, java.lang.String, java.util.Map)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                            #{camundaClient:any(io.camunda.client.CamundaClient)}
                                .newCorrelateMessageCommand()
                                .messageName(#{messageName:any(String)})
                                .correlationKey("add correlationKey here")
                                .variables(#{variableMap:any(java.util.Map)})
                                .send()
                                .join();
                                    """),
            "io.camunda.client.api.response.CorrelateMessageResponse",
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("messageName", 0),
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("variableMap", 2)),
            List.of(" please configure you correlationKey", " businessKey was removed")),
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "startProcessInstanceByMessageAndProcessDefinitionId(String messageName,
                // String
                // processDefinitionId)"
                "org.camunda.bpm.engine.RuntimeService startProcessInstanceByMessageAndProcessDefinitionId(java.lang.String, java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                            #{camundaClient:any(io.camunda.client.CamundaClient)}
                                .newCorrelateMessageCommand()
                                .messageName(#{messageName:any(String)})
                                .correlationKey("add correlationKey here")
                                .send()
                                .join();
                            """),
            "io.camunda.client.api.response.CorrelateMessageResponse",
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("messageName", 0)),
            List.of(" please configure you correlationKey", " processDefinitionId was removed")),
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "startProcessInstanceByMessageAndProcessDefinitionId(String messageName,
                // String
                // processDefinitionId, String businessKey)"
                "org.camunda.bpm.engine.RuntimeService startProcessInstanceByMessageAndProcessDefinitionId(java.lang.String, java.lang.String, java.lang.String)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                            #{camundaClient:any(io.camunda.client.CamundaClient)}
                                .newCorrelateMessageCommand()
                                .messageName(#{messageName:any(String)})
                                .correlationKey("add correlationKey here")
                                .send()
                                .join();
                            """),
            "io.camunda.client.api.response.CorrelateMessageResponse",
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("messageName", 0)),
            List.of(
                " please configure you correlationKey",
                " businessKey was removed",
                " processDefinitionId was removed")),
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "startProcessInstanceByMessageAndProcessDefinitionId(String messageName,
                // String
                // processDefinitionId, Map<String, Object> variableMap)"
                "org.camunda.bpm.engine.RuntimeService startProcessInstanceByMessageAndProcessDefinitionId(java.lang.String, java.lang.String, java.util.Map)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                            #{camundaClient:any(io.camunda.client.CamundaClient)}
                                .newCorrelateMessageCommand()
                                .messageName(#{messageName:any(String)})
                                .correlationKey("add correlationKey here")
                                .variables(#{variableMap:any(java.util.Map)})
                                .send()
                                .join();
                            """),
            "io.camunda.client.api.response.CorrelateMessageResponse",
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("messageName", 0),
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("variableMap", 2)),
            List.of(" please configure you correlationKey", " processDefinitionId was removed")),
        new RecipeUtils.MethodInvocationSimpleReplacementSpec(
            new MethodMatcher(
                // "startProcessInstanceByMessageAndProcessDefinitionId(String messageName,
                // String
                // processDefinitionId, String businessKey, Map<String, Object>
                // variableMap)"
                "org.camunda.bpm.engine.RuntimeService startProcessInstanceByMessageAndProcessDefinitionId(java.lang.String, java.lang.String, java.lang.String, java.util.Map)"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                            #{camundaClient:any(io.camunda.client.CamundaClient)}
                                .newCorrelateMessageCommand()
                                .messageName(#{messageName:any(String)})
                                .correlationKey("add correlationKey here")
                                .variables(#{variableMap:any(java.util.Map)})
                                .send()
                                .join();
                            """),
            "io.camunda.client.api.response.CorrelateMessageResponse",
            List.of(
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("messageName", 0),
                new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("variableMap", 3)),
            List.of(
                " please configure you correlationKey",
                " businessKey was removed",
                " processDefinitionId was removed")));
  }

  static final MethodMatcher executeMethodMatcher =
      new MethodMatcher("org.camunda.bpm.engine.runtime.ProcessInstantiationBuilder execute(..)");

  @Override
  protected List<RecipeUtils.MethodInvocationBuilderReplacementSpec> builderMethodInvocations() {
    return List.of(
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
            executeMethodMatcher,
            Set.of("createProcessInstanceByKey"),
            List.of("createProcessInstanceByKey"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newCreateInstanceCommand()
                                    .bpmnProcessId(#{processDefinitionId:any(String)})
                                    .latestVersion()
                                    .send()
                                    .join();
                                """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            Collections.emptyList()),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
            executeMethodMatcher,
            Set.of("createProcessInstanceByKey", "businessKey"),
            List.of("createProcessInstanceByKey"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newCreateInstanceCommand()
                                    .bpmnProcessId(#{processDefinitionId:any(String)})
                                    .latestVersion()
                                    .send()
                                    .join();
                                """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            List.of(" businessKey was removed")),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
            executeMethodMatcher,
            Set.of("createProcessInstanceByKey", "processDefinitionTenantId"),
            List.of("createProcessInstanceByKey", "processDefinitionTenantId"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newCreateInstanceCommand()
                                    .bpmnProcessId(#{processDefinitionId:any(String)})
                                    .latestVersion()
                                    .tenantId(#{tenantId:any(String)})
                                    .send()
                                    .join();
                                """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            Collections.emptyList()),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
            executeMethodMatcher,
            Set.of("createProcessInstanceByKey", "setVariables"),
            List.of("createProcessInstanceByKey", "setVariables"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newCreateInstanceCommand()
                                    .bpmnProcessId(#{processDefinitionId:any(String)})
                                    .latestVersion()
                                    .variables(#{variableMap:any(java.util.Map)})
                                    .send()
                                    .join();
                                """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            Collections.emptyList()),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
            executeMethodMatcher,
            Set.of("createProcessInstanceByKey", "processDefinitionTenantId", "businessKey"),
            List.of("createProcessInstanceByKey", "processDefinitionTenantId"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newCreateInstanceCommand()
                                    .bpmnProcessId(#{processDefinitionId:any(String)})
                                    .latestVersion()
                                    .tenantId(#{tenantId:any(String)})
                                    .send()
                                    .join();
                                """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            List.of(" businessKey was removed")),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
            executeMethodMatcher,
            Set.of("createProcessInstanceByKey", "setVariables", "businessKey"),
            List.of("createProcessInstanceByKey", "setVariables"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newCreateInstanceCommand()
                                    .bpmnProcessId(#{processDefinitionId:any(String)})
                                    .latestVersion()
                                    .variables(#{variableMap:any(java.util.Map)})
                                    .send()
                                    .join();
                                """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            List.of(" businessKey was removed")),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
            executeMethodMatcher,
            Set.of("createProcessInstanceByKey", "processDefinitionTenantId", "setVariables"),
            List.of("createProcessInstanceByKey", "processDefinitionTenantId", "setVariables"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newCreateInstanceCommand()
                                    .bpmnProcessId(#{processDefinitionId:any(String)})
                                    .latestVersion()
                                    .tenantId(#{tenantId:any(String)})
                                    .variables(#{variableMap:any(java.util.Map)})
                                    .send()
                                    .join();
                                """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            Collections.emptyList()),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
            executeMethodMatcher,
            Set.of(
                "createProcessInstanceByKey",
                "processDefinitionTenantId",
                "setVariables",
                "businessKey"),
            List.of("createProcessInstanceByKey", "processDefinitionTenantId", "setVariables"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newCreateInstanceCommand()
                                    .bpmnProcessId(#{processDefinitionId:any(String)})
                                    .latestVersion()
                                    .tenantId(#{tenantId:any(String)})
                                    .variables(#{variableMap:any(java.util.Map)})
                                    .send()
                                    .join();
                                """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            List.of(" businessKey was removed")),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
            executeMethodMatcher,
            Set.of("createProcessInstanceById"),
            List.of("createProcessInstanceById"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newCreateInstanceCommand()
                                    .processDefinitionKey(Long.valueOf(#{processDefinitionKey:any(String)}))
                                    .send()
                                    .join();
                                """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            Collections.emptyList()),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
            executeMethodMatcher,
            Set.of("createProcessInstanceById", "businessKey"),
            List.of("createProcessInstanceById"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newCreateInstanceCommand()
                                    .processDefinitionKey(Long.valueOf(#{processDefinitionKey:any(String)}))
                                    .send()
                                    .join();
                                """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            List.of(" businessKey was removed")),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
            executeMethodMatcher,
            Set.of("createProcessInstanceById", "processDefinitionTenantId"),
            List.of("createProcessInstanceById", "processDefinitionTenantId"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newCreateInstanceCommand()
                                    .processDefinitionKey(Long.valueOf(#{processDefinitionKey:any(String)}))
                                    .tenantId(#{tenantId:any(String)})
                                    .send()
                                    .join();
                                """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            Collections.emptyList()),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
            executeMethodMatcher,
            Set.of("createProcessInstanceById", "setVariables"),
            List.of("createProcessInstanceById", "setVariables"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newCreateInstanceCommand()
                                    .processDefinitionKey(Long.valueOf(#{processDefinitionKey:any(String)}))
                                    .variables(#{variableMap:any(java.util.Map)})
                                    .send()
                                    .join();
                                """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            Collections.emptyList()),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
            executeMethodMatcher,
            Set.of("createProcessInstanceById", "processDefinitionTenantId", "businessKey"),
            List.of("createProcessInstanceById", "processDefinitionTenantId"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newCreateInstanceCommand()
                                    .processDefinitionKey(Long.valueOf(#{processDefinitionKey:any(String)}))
                                    .tenantId(#{tenantId:any(String)})
                                    .send()
                                    .join();
                                """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            List.of(" businessKey was removed")),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
            executeMethodMatcher,
            Set.of("createProcessInstanceById", "setVariables", "businessKey"),
            List.of("createProcessInstanceById", "setVariables"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newCreateInstanceCommand()
                                    .processDefinitionKey(Long.valueOf(#{processDefinitionKey:any(String)}))
                                    .variables(#{variableMap:any(java.util.Map)})
                                    .send()
                                    .join();
                                """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            List.of(" businessKey was removed")),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
            executeMethodMatcher,
            Set.of("createProcessInstanceById", "processDefinitionTenantId", "setVariables"),
            List.of("createProcessInstanceById", "processDefinitionTenantId", "setVariables"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newCreateInstanceCommand()
                                    .processDefinitionKey(Long.valueOf(#{processDefinitionKey:any(String)}))
                                    .tenantId(#{tenantId:any(String)})
                                    .variables(#{variableMap:any(java.util.Map)})
                                    .send()
                                    .join();
                                """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            Collections.emptyList()),
        new RecipeUtils.MethodInvocationBuilderReplacementSpec(
            executeMethodMatcher,
            Set.of(
                "createProcessInstanceById",
                "processDefinitionTenantId",
                "setVariables",
                "businessKey"),
            List.of("createProcessInstanceById", "processDefinitionTenantId", "setVariables"),
            RecipeUtils.createSimpleJavaTemplate(
                """
                                #{camundaClient:any(io.camunda.client.CamundaClient)}
                                    .newCreateInstanceCommand()
                                    .processDefinitionKey(Long.valueOf(#{processDefinitionKey:any(String)}))
                                    .tenantId(#{tenantId:any(String)})
                                    .variables(#{variableMap:any(java.util.Map)})
                                    .send()
                                    .join();
                                """),
            "io.camunda.client.api.response.ProcessInstanceEvent",
            List.of(" businessKey was removed")));
  }

  @Override
  protected List<RecipeUtils.MethodInvocationReturnReplacementSpec> returnMethodInvocations() {
    return List.of(
        new RecipeUtils.MethodInvocationReturnReplacementSpec(
            new MethodMatcher("org.camunda.bpm.engine.runtime.Execution getProcessInstanceId()"),
            RecipeUtils.createSimpleJavaTemplate(
                "String.valueOf(#{any()}.getProcessInstanceKey())")));
  }
}
