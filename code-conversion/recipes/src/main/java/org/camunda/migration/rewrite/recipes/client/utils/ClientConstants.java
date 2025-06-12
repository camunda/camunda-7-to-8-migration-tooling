package org.camunda.migration.rewrite.recipes.client.utils;

public class ClientConstants {

  public static class Type {
    public static final String PROCESS_ENGINE = "org.camunda.bpm.engine.ProcessEngine";
    public static final String PROCESS_ENGINE_SERVICES =
        "org.camunda.bpm.engine.ProcessEngineServices";
    public static final String RUNTIME_SERVICE = "org.camunda.bpm.engine.RuntimeService";
    public static final String TASK_SERVICE = "org.camunda.bpm.engine.TaskService";
    public static final String REPOSITORY_SERVICE = "org.camunda.bpm.engine.RepositoryService";
    public static final String SIGNAL_BUILDER =
        "org.camunda.bpm.engine.runtime.SignalEventReceivedBuilder";

    public static final String PROCESS_INSTANTIATION_BUILDER =
        "org.camunda.bpm.engine.runtime.ProcessInstantiationBuilder";
    public static final String ACTIVITY_INSTANTIATION_BUILDER =
        "org.camunda.bpm.engine.runtime.ActivityInstantiationBuilder";

    public static final String VARIABLES = "org.camunda.bpm.engine.variable.Variables";
    public static final String VARIABLE_MAP = "org.camunda.bpm.engine.variable.VariableMap";
    public static final String OBJECT_VALUE = "org.camunda.bpm.engine.variable.value.ObjectValue";
    public static final String STRING_VALUE = "org.camunda.bpm.engine.variable.value.StringValue";
    public static final String TYPED_VALUE = "org.camunda.bpm.engine.variable.value.TypedValue";
    public static final String BOOLEAN_VALUE = "org.camunda.bpm.engine.variable.value.BooleanValue";
    public static final String INTEGER_VALUE = "org.camunda.bpm.engine.variable.value.IntegerValue";
    public static final String LONG_VALUE = "org.camunda.bpm.engine.variable.value.LongValue";
    public static final String SHORT_VALUE = "org.camunda.bpm.engine.variable.value.ShortValue";
    public static final String DOUBLE_VALUE = "org.camunda.bpm.engine.variable.value.DoubleValue";
    public static final String FLOAT_VALUE = "org.camunda.bpm.engine.variable.value.FloatValue";
    public static final String BYTES_VALUE = "org.camunda.bpm.engine.variable.value.BytesValue";

    public static final String ENGINE_PROCESS_INSTANCE =
        "org.camunda.bpm.engine.runtime.ProcessInstance";
    public static final String CORRELATION_MESSAGE_RESPONSE = "io.camunda.client.api.response.CorrelateMessageResponse";
    public static final String PROCESS_INSTANCE_EVENT = "io.camunda.client.api.response.ProcessInstanceEvent";
  }

  public static class EngineMethod {
    public static final String GET_RUNTIME_SERVICE =
        ClientConstants.Type.PROCESS_ENGINE_SERVICES + " getRuntimeService()";
  }

  public static class RuntimeServiceMethod {
    public static final String DELETE_PROCESS_INSTANCE =
        ClientConstants.Type.RUNTIME_SERVICE + " deleteProcessInstance(String, String)";
    public static final String SIGNAL_EVENT_RECEIVED =
        ClientConstants.Type.RUNTIME_SERVICE + " signalEventReceived";
    public static final String CREATE_SIGNAL_EVENT =
        ClientConstants.Type.RUNTIME_SERVICE + " createSignalEvent(String)";
    public static final String SIGNAL_BUILDER_SET_VARIABLES =
        ClientConstants.Type.SIGNAL_BUILDER + " setVariables(java.util.Map)";
    public static final String SIGNAL_BUILDER_TENANT_ID =
        ClientConstants.Type.SIGNAL_BUILDER + " tenantId(String)";
    public static final String SIGNAL_BUILDER_EXECUTION_ID =
        ClientConstants.Type.SIGNAL_BUILDER + " executionId(String)";
    public static final String SIGNAL_BUILDER_SEND =
        ClientConstants.Type.SIGNAL_BUILDER + " send()";

    public static final String START_PROCESS_INSTANCE_BY_KEY =
        ClientConstants.Type.RUNTIME_SERVICE + " startProcessInstanceByKey";
    public static final String START_PROCESS_INSTANCE_BY_ID =
        ClientConstants.Type.RUNTIME_SERVICE + " startProcessInstanceById";
    public static final String START_PROCESS_INSTANCE_BY_MESSAGE =
        ClientConstants.Type.RUNTIME_SERVICE + " startProcessInstanceByMessage";
    public static final String START_PROCESS_INSTANCE_BY_MESSAGE_AND_PROCESS_DEFINITION_ID =
        ClientConstants.Type.RUNTIME_SERVICE
            + " startProcessInstanceByMessageAndProcessDefinitionId";
    public static final String CREATE_PROCESS_INSTANCE_BY_KEY =
        ClientConstants.Type.RUNTIME_SERVICE + " createProcessInstanceByKey(String)";
    public static final String CREATE_PROCESS_INSTANCE_BY_ID =
        ClientConstants.Type.RUNTIME_SERVICE + " createProcessInstanceById(String)";
    public static final String PROCESS_INSTANCE_BUILDER_BUSINESS_KEY =
        Type.PROCESS_INSTANTIATION_BUILDER + " businessKey(String)";
    public static final String PROCESS_INSTANCE_BUILDER_TENANT_ID =
        Type.PROCESS_INSTANTIATION_BUILDER + " processDefinitionTenantId(String)";
    public static final String PROCESS_INSTANCE_BUILDER_SET_VARIABLES =
        Type.ACTIVITY_INSTANTIATION_BUILDER + " setVariables(java.util.Map)";
    public static final String PROCESS_INSTANCE_BUILDER_EXECUTE =
        Type.PROCESS_INSTANTIATION_BUILDER + " execute()";
  }

  public static class Parameters {
    public static final String ANY = "(..)";

    public static String build(String... params) {
      return "(" + String.join(", ", params) + ")";
    }
  }
}
