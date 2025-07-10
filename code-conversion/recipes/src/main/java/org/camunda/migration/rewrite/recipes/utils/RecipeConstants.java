package org.camunda.migration.rewrite.recipes.utils;

public class RecipeConstants {

  public static class Type {
    public static final String CAMUNDA_CLIENT = "io.camunda.client.CamundaClient";
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
    public static final String CORRELATION_MESSAGE_RESPONSE =
        "io.camunda.client.api.response.CorrelateMessageResponse";
    public static final String PROCESS_INSTANCE_EVENT =
        "io.camunda.client.api.response.ProcessInstanceEvent";

    public static final String ACTIVATED_JOB = "io.camunda.client.api.response.ActivatedJob";
    public static final String JOB_WORKER = "io.camunda.spring.client.annotation.JobWorker";
    public static final String VARIABLE_SCOPE = "org.camunda.bpm.engine.delegate.VariableScope";
    public static final String DELEGATE_EXECUTION =
        "org.camunda.bpm.engine.delegate.DelegateExecution";
    public static final String JAVA_DELEGATE = "org.camunda.bpm.engine.delegate.JavaDelegate";
    public static final String ENGINE_BPMN_ERROR = "org.camunda.bpm.engine.delegate.BpmnError";
    public static final String ENGINE_EXCEPTION = "org.camunda.bpm.engine.ProcessEngineException";

    public static final String CLIENT_CAMUNDA_ERROR =
        "io.camunda.spring.client.exception.CamundaError";
  }

  public static class Method {
    public static final String GET_RUNTIME_SERVICE =
        RecipeConstants.Type.PROCESS_ENGINE_SERVICES + " getRuntimeService()";
    public static final String DELETE_PROCESS_INSTANCE =
        RecipeConstants.Type.RUNTIME_SERVICE + " deleteProcessInstance(String, String)";
    public static final String SIGNAL_EVENT_RECEIVED =
        RecipeConstants.Type.RUNTIME_SERVICE + " signalEventReceived";
    public static final String CREATE_SIGNAL_EVENT =
        RecipeConstants.Type.RUNTIME_SERVICE + " createSignalEvent(String)";
    public static final String SIGNAL_BUILDER_SET_VARIABLES =
        RecipeConstants.Type.SIGNAL_BUILDER + " setVariables(java.util.Map)";
    public static final String SIGNAL_BUILDER_TENANT_ID =
        RecipeConstants.Type.SIGNAL_BUILDER + " tenantId(String)";
    public static final String SIGNAL_BUILDER_EXECUTION_ID =
        RecipeConstants.Type.SIGNAL_BUILDER + " executionId(String)";
    public static final String SIGNAL_BUILDER_SEND =
        RecipeConstants.Type.SIGNAL_BUILDER + " send()";

    public static final String START_PROCESS_INSTANCE_BY_KEY =
        RecipeConstants.Type.RUNTIME_SERVICE + " startProcessInstanceByKey";
    public static final String START_PROCESS_INSTANCE_BY_ID =
        RecipeConstants.Type.RUNTIME_SERVICE + " startProcessInstanceById";
    public static final String START_PROCESS_INSTANCE_BY_MESSAGE =
        RecipeConstants.Type.RUNTIME_SERVICE + " startProcessInstanceByMessage";
    public static final String START_PROCESS_INSTANCE_BY_MESSAGE_AND_PROCESS_DEFINITION_ID =
        RecipeConstants.Type.RUNTIME_SERVICE
            + " startProcessInstanceByMessageAndProcessDefinitionId";
    public static final String CREATE_PROCESS_INSTANCE_BY_KEY =
        RecipeConstants.Type.RUNTIME_SERVICE + " createProcessInstanceByKey(String)";
    public static final String CREATE_PROCESS_INSTANCE_BY_ID =
        RecipeConstants.Type.RUNTIME_SERVICE + " createProcessInstanceById(String)";
    public static final String PROCESS_INSTANCE_BUILDER_BUSINESS_KEY =
        Type.PROCESS_INSTANTIATION_BUILDER + " businessKey(String)";
    public static final String PROCESS_INSTANCE_BUILDER_TENANT_ID =
        Type.PROCESS_INSTANTIATION_BUILDER + " processDefinitionTenantId(String)";
    public static final String PROCESS_INSTANCE_BUILDER_SET_VARIABLES =
        Type.ACTIVITY_INSTANTIATION_BUILDER + " setVariables(java.util.Map)";
    public static final String PROCESS_INSTANCE_BUILDER_EXECUTE =
        Type.PROCESS_INSTANTIATION_BUILDER + " execute()";

    public static final String GET_VARIABLE = Type.VARIABLE_SCOPE + " getVariable(String)";
    public static final String GET_VARIABLE_LOCAL = Type.VARIABLE_SCOPE + " getVariableLocal(String)";
    public static final String SET_VARIABLE =
        Type.VARIABLE_SCOPE + " setVariable(String, java.lang.Object)";
    public static final String SET_VARIABLE_LOCAL =
            Type.VARIABLE_SCOPE + " setVariableLocal(String, java.lang.Object)";

    public static final String CREATE_INCIDENT = Type.DELEGATE_EXECUTION + " createIncident";
  }

  public static class Parameters {
    public static final String ANY = "(..)";

    public static String build(String... params) {
      return "(" + String.join(", ", params) + ")";
    }
  }
}
