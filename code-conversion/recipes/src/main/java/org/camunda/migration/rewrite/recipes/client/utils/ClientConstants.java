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
    public static final String SIGNAL_BUILDER_SEND =
        ClientConstants.Type.SIGNAL_BUILDER + " send()";
    public static final String SIGNAL_BUILDER_SET_VARIABLES =
        ClientConstants.Type.SIGNAL_BUILDER + " setVariables(java.util.Map)";
    public static final String SIGNAL_BUILDER_TENANT_ID =
            ClientConstants.Type.SIGNAL_BUILDER + " tenantId(String)";
    public static final String SIGNAL_BUILDER_EXECUTION_ID =
            ClientConstants.Type.SIGNAL_BUILDER + " executionId(String)";
  }

  public static class Parameters {
    public static final String ANY = "(..)";

    public static String build(String... params) {
      return "(" + String.join(", ", params) + ")";
    }
  }
}
