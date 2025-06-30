package org.camunda.migration.rewrite.recipes.utils;

public class CamundaClientCodes {

  public static final String BROADCAST_SIGNAL =
      """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newBroadcastSignalCommand()
                .signalName(#{signalName:any(String)})
                .send()
                .join();
            """;

  public static final String BROADCAST_SIGNAL_WITH_VARIABLES =
      """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newBroadcastSignalCommand()
                .signalName(#{signalName:any(String)})
                .variables(#{variableMap:any(java.util.Map)})
                .send()
                .join();
            """;

  public static final String BROADCAST_SIGNAL_WITH_TENANT_ID =
      """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newBroadcastSignalCommand()
                .signalName(#{signalName:any(String)})
                .tenantId(#{tenantId:any(String)})
                .send()
                .join();
            """;

  public static final String BROADCAST_SIGNAL_WITH_TENANT_ID_WITH_VARIABLES =
      """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newBroadcastSignalCommand()
                .signalName(#{signalName:any(String)})
                .tenantId(#{tenantId:any(String)})
                .variables(#{variableMap:any(java.util.Map)})
                .send()
                .join();
            """;

  public static final String CANCEL_PROCESS_INSTANCE =
      """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newCancelInstanceCommand(Long.valueOf(#{processInstanceKey:any(String)}))
                .send()
                .join();
            """;

  public static final String CREATE_PROCESS_BY_BPMN_MODEL_IDENTIFIER =
      """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newCreateInstanceCommand()
                .bpmnProcessId(#{processDefinitionId:any(String)})
                .latestVersion()
                .send()
                .join();
            """;

  public static final String CREATE_PROCESS_BY_BPMN_MODEL_IDENTIFIER_WITH_TENANT_ID =
      """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newCreateInstanceCommand()
                .bpmnProcessId(#{processDefinitionId:any(String)})
                .latestVersion()
                .tenantId(#{tenantId:any(String)})
                .send()
                .join();
            """;

  public static final String CREATE_PROCESS_BY_BPMN_MODEL_IDENTIFIER_WITH_VARIABLES =
      """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newCreateInstanceCommand()
                .bpmnProcessId(#{processDefinitionId:any(String)})
                .latestVersion()
                .variables(#{variableMap:any(java.util.Map)})
                .send()
                .join();
            """;

  public static final String CREATE_PROCESS_BY_BPMN_MODEL_IDENTIFIER_WITH_TENANT_ID_WITH_VARIABLES =
      """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newCreateInstanceCommand()
                .bpmnProcessId(#{processDefinitionId:any(String)})
                .latestVersion()
                .tenantId(#{tenantId:any(String)})
                .variables(#{variableMap:any(java.util.Map)})
                .send()
                .join();
            """;

  public static final String CREATE_PROCESS_BY_KEY_ASSIGNED_ON_DEPLOYMENT =
      """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newCreateInstanceCommand()
                .processDefinitionKey(Long.valueOf(#{tenantId:any(String)}))
                .send()
                .join();
            """;

  public static final String CREATE_PROCESS_BY_KEY_ASSIGNED_ON_DEPLOYMENT_WITH_TENANT_ID =
      """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newCreateInstanceCommand()
                .processDefinitionKey(Long.valueOf(#{tenantId:any(String)}))
                .tenantId(#{tenantId:any(String)})
                .send()
                .join();
            """;

  public static final String CREATE_PROCESS_BY_KEY_ASSIGNED_ON_DEPLOYMENT_WITH_VARIABLES =
      """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newCreateInstanceCommand()
                .processDefinitionKey(Long.valueOf(#{tenantId:any(String)}))
                .variables(#{variableMap:any(java.util.Map)})
                .send()
                .join();
            """;

  public static final String
      CREATE_PROCESS_BY_KEY_ASSIGNED_ON_DEPLOYMENT_WITH_TENANT_ID_WITH_VARIABLES =
          """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newCreateInstanceCommand()
                .processDefinitionKey(Long.valueOf(#{tenantId:any(String)}))
                .tenantId(#{tenantId:any(String)})
                .variables(#{variableMap:any(java.util.Map)})
                .send()
                .join();
            """;

  public static final String CORRELATE_MESSAGE =
      """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newCorrelateMessageCommand()
                .messageName(#{messageName:any(String)})
                .correlationKey("add correlationKey here")
                .send()
                .join();
            """;

  public static final String CORRELATE_MESSAGE_WITH_TENANT_ID =
      """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newCorrelateMessageCommand()
                .messageName(#{messageName:any(String)})
                .correlationKey("add correlationKey here")
                .tenantId(#{tenantId:any(String)})
                .send()
                .join();
            """;

  public static final String CORRELATE_MESSAGE_WITH_VARIABLES =
      """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newCorrelateMessageCommand()
                .messageName(#{messageName:any(String)})
                .correlationKey("add correlationKey here")
                .variables(#{variableMap:any(java.util.Map)})
                .send()
                .join();
            """;

  public static final String CORRELATE_MESSAGE_WITH_TENANT_ID_WITH_VARIABLES =
      """
            #{camundaClient:any(io.camunda.client.CamundaClient)}
                .newCorrelateMessageCommand()
                .messageName(#{messageName:any(String)})
                .correlationKey("add correlationKey here")
                .tenantId(#{tenantId:any(String)})
                .variables(#{variableMap:any(java.util.Map)})
                .send()
                .join();
            """;
}
