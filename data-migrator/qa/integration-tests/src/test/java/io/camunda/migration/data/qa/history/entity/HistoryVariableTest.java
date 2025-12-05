/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.SerializationDataFormats.JSON;
import static org.camunda.bpm.engine.variable.Variables.SerializationDataFormats.XML;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.VariableEntity;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class HistoryVariableTest extends HistoryMigrationAbstractTest {

  @Autowired
  protected HistoryService historyService;

  @Autowired
  protected ObjectMapper objectMapper;

  @Test
  public void shouldMigrateVariableWithNullValue() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "myVariable", null);

    HistoricVariableInstance c7Variable = historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("myVariable")
        .singleResult();

    // when
    historyMigrator.migrate();

    // then
    List<VariableEntity> variables = searchHistoricVariables("myVariable");
    assertThat(variables).hasSize(1);
    VariableEntity variable = variables.get(0);
    assertC8VariableFields(variable, c7Variable, null, processInstance.getProcessDefinitionKey(), C8_DEFAULT_TENANT);
  }

  @Test
  @Disabled("https://github.com/camunda/camunda-bpm-platform/issues/5329")
  public void shouldMigratePrimitiveVariables() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    VariableMap variables = Variables.createVariables();
    variables.putValue("stringVar", "myStringVar");
    variables.putValue("booleanVar", true);
    variables.putValue("integerVar", 1234);
    variables.putValue("doubleVar", 1.5d);
    variables.putValue("shortVar", (short) 1);
    variables.putValue("longVar", 2_147_483_648L);

    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess", variables);

    // when
    historyMigrator.migrate();

    // then
    assertVariableExists("stringVar", "myStringVar", processInstance.getProcessDefinitionKey());
    assertVariableExists("booleanVar", true, processInstance.getProcessDefinitionKey());
    assertVariableExists("integerVar", 1234, processInstance.getProcessDefinitionKey());
    assertVariableExists("doubleVar", 1.5d, processInstance.getProcessDefinitionKey());
    assertVariableExists("shortVar", (short) 1, processInstance.getProcessDefinitionKey());
    assertVariableExists("longVar", 2_147_483_648L, processInstance.getProcessDefinitionKey());
  }

  @Test
  @Disabled("https://github.com/camunda/camunda-bpm-platform/issues/5329")
  public void shouldMigrateDateVariable() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    Date date = new Date();
    runtimeService.setVariable(processInstance.getId(), "dateVar", date);

    HistoricVariableInstance c7Variable = historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("dateVar")
        .singleResult();

    // when
    historyMigrator.migrate();

    // then
    List<VariableEntity> variables = searchHistoricVariables("dateVar");
    assertThat(variables).hasSize(1);
    VariableEntity variable = variables.get(0);
    String expectedDateValue = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(date);
    assertC8VariableFields(variable, c7Variable, expectedDateValue, processInstance.getProcessDefinitionKey(), C8_DEFAULT_TENANT);
  }

  @Test
  @Disabled("https://github.com/camunda/camunda-bpm-platform/issues/5329")
  public void shouldMigrateXmlObjectVariable() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" + "<xmlSerializable>"
        + "<booleanProperty>true</booleanProperty>" + "<intProperty>42</intProperty>"
        + "<stringProperty>a String</stringProperty>" + "</xmlSerializable>";

    ObjectValue objectValue = Variables.serializedObjectValue(xml)
        .serializationDataFormat(XML)
        .objectTypeName("io.camunda.migrator.qa.runtime.variables.XmlSerializable")
        .create();

    runtimeService.setVariable(processInstance.getId(), "xmlVar", objectValue);

    HistoricVariableInstance c7Variable = historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("xmlVar")
        .singleResult();

    // when
    historyMigrator.migrate();

    // then
    List<VariableEntity> variables = searchHistoricVariables("xmlVar");
    assertThat(variables).hasSize(1);
    VariableEntity variable = variables.get(0);
    assertC8VariableFields(variable, c7Variable, xml, processInstance.getProcessDefinitionKey(), C8_DEFAULT_TENANT);
  }

  @Test
  @Disabled("https://github.com/camunda/camunda-bpm-platform/issues/5329")
  public void shouldMigrateJsonObjectVariable() throws JsonProcessingException {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    String json = "{\"stringProperty\":\"a String\",\"intProperty\":42,\"booleanProperty\":true}";
    ObjectValue objectValue = Variables.serializedObjectValue(json)
        .serializationDataFormat(JSON)
        .objectTypeName("io.camunda.migrator.qa.runtime.variables.JsonSerializable")
        .create();

    runtimeService.setVariable(processInstance.getId(), "jsonVar", objectValue);

    HistoricVariableInstance c7Variable = historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("jsonVar")
        .singleResult();

    // when
    historyMigrator.migrate();

    // then
    List<VariableEntity> variables = searchHistoricVariables("jsonVar");
    assertThat(variables).hasSize(1);
    VariableEntity variable = variables.get(0);
    // JSON objects should be converted to JSON string representation
    JsonNode expectedJson = objectMapper.readValue(json, JsonNode.class);
    String expectedValue = objectMapper.writeValueAsString(expectedJson);
    assertC8VariableFields(variable, c7Variable, expectedValue, processInstance.getProcessDefinitionKey(), C8_DEFAULT_TENANT);
  }

  @Test
  public void shouldMigrateVariablesWithTenant() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn", "my-tenant1");

    VariableMap variables = Variables.createVariables();
    variables.putValue("stringVar", "myStringVar");

    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess", variables);

    HistoricVariableInstance c7StringVar = historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("stringVar")
        .singleResult();

    // when
    historyMigrator.migrate();

    // then
    List<VariableEntity> stringVars = searchHistoricVariables("stringVar");

    assertThat(stringVars).hasSize(1);

    assertC8VariableFields(stringVars.get(0), c7StringVar, "myStringVar", processInstance.getProcessDefinitionKey(),
        "my-tenant1");
  }

  @Test
  public void shouldMigrateGlobalVariable() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    Map<String, Object> vars = new HashMap<>();
    vars.put("variable1", "value1");
    vars.put("variable2", "value2");

    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId", vars);
    Task currentTask = taskService.createTaskQuery().singleResult();
    runtimeService.setVariable(currentTask.getExecutionId(), "variable3", "value3");

    HistoricVariableInstance c7Variable1 = historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("variable1")
        .singleResult();

    HistoricVariableInstance c7Variable3 = historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("variable3")
        .singleResult();

    // when
    historyMigrator.migrate();

    // then
    assertVariableExists("variable1", "value1", "userTaskProcessId");
    assertVariableExists("variable2", "value2", "userTaskProcessId");
    assertVariableExists("variable3", "value3", "userTaskProcessId");

    // Verify that global variables have correct scope (process instance scope)
    List<VariableEntity> variable1List = searchHistoricVariables("variable1");
    List<VariableEntity> variable3List = searchHistoricVariables("variable3");

    assertThat(variable1List).hasSize(1);
    assertThat(variable3List).hasSize(1);

    assertC8VariableFields(variable1List.get(0), c7Variable1, "value1", "userTaskProcessId", C8_DEFAULT_TENANT);
    assertC8VariableFields(variable3List.get(0), c7Variable3, "value3", "userTaskProcessId", C8_DEFAULT_TENANT);
  }

  @Test
  public void shouldMigrateLocalVariable() {
    // given
    deployer.deployCamunda7Process("parallelGateway.bpmn");

    Map<String, Object> vars = new HashMap<>();
    vars.put("globalVariable", "globalValue");

    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("ParallelGatewayProcess", vars);
    Task currentTask = taskService.createTaskQuery().taskDefinitionKey("usertaskActivity").singleResult();
    runtimeService.setVariableLocal(currentTask.getExecutionId(), "localVariable", "localValue");

    HistoricVariableInstance c7GlobalVar = historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("globalVariable")
        .singleResult();

    HistoricVariableInstance c7LocalVar = historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("localVariable")
        .singleResult();

    // when
    historyMigrator.migrate();

    // then
    List<VariableEntity> globalVars = searchHistoricVariables("globalVariable");
    List<VariableEntity> localVars = searchHistoricVariables("localVariable");

    assertThat(globalVars).hasSize(1);
    assertThat(localVars).hasSize(1);

    VariableEntity globalVar = globalVars.get(0);
    VariableEntity localVar = localVars.get(0);

    assertC8VariableFields(globalVar, c7GlobalVar, "globalValue", "ParallelGatewayProcess", C8_DEFAULT_TENANT);
    assertC8VariableFields(localVar, c7LocalVar, "localValue", "ParallelGatewayProcess", C8_DEFAULT_TENANT);

    // Local variable should have different scope than global variable
    assertThat(localVar.scopeKey()).isNotEqualTo(globalVar.scopeKey());
  }

  @Test
  public void shouldMigrateTaskVariable() {
    // given
    deployer.deployCamunda7Process("parallelGateway.bpmn");

    Map<String, Object> vars = new HashMap<>();
    vars.put("processVariable", "processValue");

    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("ParallelGatewayProcess", vars);
    Task currentTask = taskService.createTaskQuery().taskDefinitionKey("usertaskActivity").singleResult();
    taskService.setVariableLocal(currentTask.getId(), "taskVariable", "taskValue");

    HistoricVariableInstance c7ProcessVar = historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("processVariable")
        .singleResult();

    HistoricVariableInstance c7TaskVar = historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("taskVariable")
        .singleResult();

    // when
    historyMigrator.migrate();

    // then
    List<VariableEntity> processVars = searchHistoricVariables("processVariable");
    List<VariableEntity> taskVars = searchHistoricVariables("taskVariable");

    assertThat(processVars).hasSize(1);
    assertThat(taskVars).hasSize(1);

    VariableEntity processVar = processVars.get(0);
    VariableEntity taskVar = taskVars.get(0);

    assertC8VariableFields(processVar, c7ProcessVar, "processValue", "ParallelGatewayProcess", C8_DEFAULT_TENANT);
    assertC8VariableFields(taskVar, c7TaskVar, "taskValue", "ParallelGatewayProcess", C8_DEFAULT_TENANT);

    // Task variable should have different scope than process variable
    assertThat(taskVar.scopeKey()).isNotEqualTo(processVar.scopeKey());
  }

  @Test
  public void shouldMigrateVariableWithInvalidFeelName() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    VariableMap variables = Variables.createVariables();
    variables.putValue("1stC", "value");
    variables.putValue("st C", "value");
    variables.putValue("st/C", "value");
    variables.putValue("st-C", "value");
    variables.putValue("null", "value");

    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess", variables);

    // when
    historyMigrator.migrate();

    // then - all variables should be migrated regardless of FEEL validity
    assertVariableExists("1stC", "value", "simpleProcess");
    assertVariableExists("st C", "value", "simpleProcess");
    assertVariableExists("st/C", "value", "simpleProcess");
    assertVariableExists("st-C", "value", "simpleProcess");
    assertVariableExists("null", "value", "simpleProcess");
  }

  @Test
  @Disabled("https://github.com/camunda/camunda-bpm-platform/issues/5329")
  public void shouldNotSetFileVariable() {
    // deploy processes
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    // given process state in c7
    String fileName = "text.txt";
    String encoding = "crazy-encoding";
    String mimeType = "martini/dry";
    FileValue fileValue = Variables.fileValue(fileName)
        .file("ABC".getBytes())
        .encoding(encoding)
        .mimeType(mimeType)
        .create();

    VariableMap fileVar = Variables.createVariables().putValueTyped("fileVar", fileValue);
    runtimeService.startProcessInstanceByKey("simpleProcess", fileVar);

    // when
    historyMigrator.migrate();

    // then
    // instance is skipped, because file variables are not supported in C8
  }

  @Test
  @Disabled("https://github.com/camunda/camunda-bpm-platform/issues/5329")
  public void shouldNotSetUnsupportedBytesType() {
    // deploy processes
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    runtimeService.startProcessInstanceByKey("simpleProcess",
        Collections.singletonMap("bytesVar", "foo".getBytes()));

    // when
    historyMigrator.migrate();

    // then
    // instance is skipped, because byte arrays are not supported in C8
  }

  protected void assertVariableExists(String varName, Object expectedValue, String processDefinitionKey) {
    List<VariableEntity> variables = searchHistoricVariables(varName);
    assertThat(variables).hasSize(1);
    VariableEntity variable = variables.get(0);
    assertThat(variable.name()).isEqualTo(varName);
    assertThat(variable.value()).isEqualTo(expectedValue);
    assertThat(variable.processDefinitionId()).isEqualTo(processDefinitionKey);
  }

  protected void assertC8VariableFields(VariableEntity variable,
                                        HistoricVariableInstance c7Variable,
                                        Object expectedValue,
                                        String processDefinitionKey,
                                        String tenantId) {
    assertThat(variable.variableKey()).isNotNull();
    assertThat(variable.name()).isEqualTo(c7Variable.getName());
    assertThat(variable.value()).isEqualTo(expectedValue);
    assertThat(variable.processInstanceKey()).isNotNull();
    assertThat(variable.scopeKey()).isNotNull();
    assertThat(variable.processDefinitionId()).isEqualTo(processDefinitionKey);
    assertThat(variable.tenantId()).isEqualTo(tenantId);
    assertThat(variable.isPreview()).isFalse();
  }
}
