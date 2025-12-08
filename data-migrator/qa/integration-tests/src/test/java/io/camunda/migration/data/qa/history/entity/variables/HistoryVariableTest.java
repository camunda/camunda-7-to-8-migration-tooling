/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.variables;

import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.SerializationDataFormats.JSON;
import static org.camunda.bpm.engine.variable.Variables.SerializationDataFormats.XML;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import io.camunda.search.entities.VariableEntity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.camunda.spin.json.SpinJsonNode;
import org.camunda.spin.plugin.variable.SpinValues;
import org.camunda.spin.plugin.variable.value.JsonValue;
import org.camunda.spin.plugin.variable.value.XmlValue;
import org.camunda.spin.xml.SpinXmlElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;

public class HistoryVariableTest extends AbstractMigratorTest {

  @RegisterExtension
  HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  @Autowired
  protected ObjectMapper objectMapper;

  @Autowired
  protected ProcessEngineConfigurationImpl processEngineConfiguration;

  @AfterEach
  public void tearDown() {
    processEngineConfiguration.setJavaSerializationFormatEnabled(false);
  }

  @Test
  public void shouldMigrateVariableWithNullValue() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "myVariable", null);

    HistoricVariableInstance c7Variable = historyMigration.getHistoryService().createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("myVariable")
        .singleResult();

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<VariableEntity> variables = historyMigration.searchHistoricVariables("myVariable");
    assertThat(variables).hasSize(1);
    VariableEntity variable = variables.getFirst();
    assertVariableFields(variable, c7Variable, "\"null\"");
  }

  @Test
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
    historyMigration.getMigrator().migrate();

    // then
    assertVariableExists("stringVar", "\"myStringVar\"");
    assertVariableExists("booleanVar", "\"true\"");
    assertVariableExists("integerVar", "\"1234\"");
    assertVariableExists("doubleVar", "\"1.5\"");
    assertVariableExists("shortVar", "\"1\"");
    assertVariableExists("longVar", "\"2147483648\"");
  }

  @Test
  public void shouldMigrateVariablesWithoutTenant() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    VariableMap variables = Variables.createVariables();
    variables.putValue("stringVar", "myStringVar");

    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess", variables);

    HistoricVariableInstance c7StringVar = historyMigration.getHistoryService().createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("stringVar")
        .singleResult();

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<VariableEntity> stringVars = historyMigration.searchHistoricVariables("stringVar");

    assertThat(stringVars).hasSize(1);

    VariableEntity variable = stringVars.getFirst();
    assertVariableFields(variable, c7StringVar, "\"myStringVar\"");
    assertThat(variable.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
  }

  @Test
  public void shouldMigrateVariablesWithTenant() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn", "my-tenant1");

    VariableMap variables = Variables.createVariables();
    variables.putValue("stringVar", "myStringVar");

    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess", variables);

    HistoricVariableInstance c7StringVar = historyMigration.getHistoryService().createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("stringVar")
        .singleResult();

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<VariableEntity> stringVars = historyMigration.searchHistoricVariables("stringVar");

    assertThat(stringVars).hasSize(1);

    VariableEntity variable = stringVars.getFirst();
    assertVariableFields(variable, c7StringVar, "\"myStringVar\"");
    assertThat(variable.tenantId()).isEqualTo("my-tenant1");
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

    runtimeService.startProcessInstanceByKey("simpleProcess", variables);

    // when
    historyMigration.getMigrator().migrate();

    // then - all variables should be migrated regardless of FEEL validity
    assertVariableExists("1stC", "\"value\"");
    assertVariableExists("st C", "\"value\"");
    assertVariableExists("st/C", "\"value\"");
    assertVariableExists("st-C", "\"value\"");
    assertVariableExists("null", "\"value\"");
  }

  @Test
  public void shouldMigrateDateVariable() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    Date date = new Date();
    runtimeService.setVariable(processInstance.getId(), "dateVar", date);

    HistoricVariableInstance c7Variable = historyMigration.getHistoryService().createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("dateVar")
        .singleResult();

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<VariableEntity> variables = historyMigration.searchHistoricVariables("dateVar");
    assertThat(variables).hasSize(1);
    VariableEntity variable = variables.getFirst();
    String expectedDateValue = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(date);
    assertVariableFields(variable, c7Variable, String.format("\"%s\"", expectedDateValue));
  }

  @Test
  public void shouldMigrateXmlObjectVariable() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
        "<xmlSerializable>" +
          "<booleanProperty>true</booleanProperty>" +
          "<intProperty>42</intProperty>" +
          "<stringProperty>a String</stringProperty>" +
        "</xmlSerializable>";

    ObjectValue objectValue = Variables.serializedObjectValue(xml)
        .serializationDataFormat(XML)
        .objectTypeName("io.camunda.migrator.qa.runtime.variables.XmlSerializable")
        .create();

    runtimeService.setVariable(processInstance.getId(), "var", objectValue);

    HistoricVariableInstance c7Variable = historyMigration.getHistoryService().createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("var")
        .singleResult();

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<VariableEntity> variables = historyMigration.searchHistoricVariables("var");
    assertThat(variables).hasSize(1);
    VariableEntity variable = variables.getFirst();
    assertVariableFields(variable, c7Variable, String.format("\"%s\"", xml));
  }

  @Test
  public void shouldMigrateJsonObjectVariable() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    String json = "{\"stringProperty\":\"a String\",\"intProperty\":42,\"booleanProperty\":true}";
    ObjectValue objectValue = Variables.serializedObjectValue(json)
        .serializationDataFormat(JSON)
        .objectTypeName("io.camunda.migrator.qa.runtime.variables.JsonSerializable")
        .create();

    runtimeService.setVariable(processInstance.getId(), "var", objectValue);

    HistoricVariableInstance c7Variable = historyMigration.getHistoryService().createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("var")
        .singleResult();

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<VariableEntity> variables = historyMigration.searchHistoricVariables("var");
    assertThat(variables).hasSize(1);
    VariableEntity variable = variables.getFirst();
    assertVariableFields(variable, c7Variable, String.format("\"%s\"", json));
  }

  @Test
  public void shouldNotSetFileVariable() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    String fileName = "text.txt";
    String encoding = "crazy-encoding";
    String mimeType = "martini/dry";
    FileValue fileValue = Variables
        .fileValue(fileName)
        .file("ABC".getBytes())
        .encoding(encoding)
        .mimeType(mimeType)
        .create();

    VariableMap fileVar = Variables.createVariables().putValueTyped("fileVar", fileValue);
    runtimeService.startProcessInstanceByKey("simpleProcess", fileVar);

    // when
    historyMigration.getMigrator().migrate();

    // then - file variables are not supported
    assertThat(historyMigration.searchHistoricVariables("fileVar")).isEmpty();
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

    HistoricVariableInstance c7Variable1 = historyMigration.getHistoryService().createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("variable1")
        .singleResult();

    HistoricVariableInstance c7Variable3 = historyMigration.getHistoryService().createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("variable3")
        .singleResult();

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertVariableExists("variable1", "\"value1\"");
    assertVariableExists("variable2", "\"value2\"");
    assertVariableExists("variable3", "\"value3\"");

    // Verify that global variables have correct scope (process instance scope)
    List<VariableEntity> variable1List = historyMigration.searchHistoricVariables("variable1");
    List<VariableEntity> variable3List = historyMigration.searchHistoricVariables("variable3");

    assertThat(variable1List).hasSize(1);
    assertThat(variable3List).hasSize(1);

    assertVariableFields(variable1List.getFirst(), c7Variable1, "\"value1\"");
    assertVariableFields(variable3List.getFirst(), c7Variable3, "\"value3\"");
    assertThat(variable1List.getFirst().scopeKey()).isEqualTo(variable1List.getFirst().processInstanceKey());
    assertThat(variable3List.getFirst().scopeKey()).isEqualTo(variable1List.getFirst().processInstanceKey());
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

    HistoricVariableInstance c7GlobalVar = historyMigration.getHistoryService().createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("globalVariable")
        .singleResult();

    HistoricVariableInstance c7LocalVar = historyMigration.getHistoryService().createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("localVariable")
        .singleResult();

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<VariableEntity> globalVars = historyMigration.searchHistoricVariables("globalVariable");
    List<VariableEntity> localVars = historyMigration.searchHistoricVariables("localVariable");

    assertThat(globalVars).hasSize(1);
    assertThat(localVars).hasSize(1);

    VariableEntity globalVar = globalVars.getFirst();
    VariableEntity localVar = localVars.getFirst();

    assertVariableFields(globalVar, c7GlobalVar, "\"globalValue\"");
    assertVariableFields(localVar, c7LocalVar, "\"localValue\"");

    // Local variable should have different scope than global variable
    assertThat(localVar.scopeKey()).isNotEqualTo(globalVar.scopeKey());
    var flowNode = historyMigration.searchHistoricFlowNodesById("usertaskActivity").getFirst();
    assertThat(localVar.scopeKey()).isEqualTo(flowNode.flowNodeInstanceKey());
  }


  @Test
  public void shouldMigrateSpinJsonVariable() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    String json = "{\"name\" : \"jonny\","
        + "\"address\" : {"
        + "\"street\" : \"12 High Street\","
        + "\"post code\" : 1234"
        + "}"
        + "}";
    JsonValue jsonValue = SpinValues.jsonValue(json).create();

    runtimeService.setVariable(processInstance.getId(), "var", jsonValue);

    HistoricVariableInstance c7Variable = historyMigration.getHistoryService().createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("var")
        .singleResult();

    SpinJsonNode c7Value = (SpinJsonNode) c7Variable.getValue();

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<VariableEntity> variables = historyMigration.searchHistoricVariables("var");
    assertThat(variables).hasSize(1);
    VariableEntity variable = variables.getFirst();
    String expectedValue = c7Value.toString();
    assertVariableFields(variable, c7Variable, String.format("\"%s\"", expectedValue));
  }

  @Test
  public void shouldMigrateSpinXmlVariable() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    String street = "<street>12 High Street</street>";
    String postcode = "<postCode>1234</postCode>";
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<customer xmlns=\"http:\\/\\/camunda.org/example\" name=\"Jonny\">"
        + "<address>"
        + street
        + postcode
        + "</address>"
        + "</customer>";
    XmlValue xmlValue = SpinValues.xmlValue(xml).create();

    runtimeService.setVariable(processInstance.getId(), "var", xmlValue);

    HistoricVariableInstance c7Variable = historyMigration.getHistoryService().createHistoricVariableInstanceQuery()
        .processInstanceId(processInstance.getId())
        .variableName("var")
        .singleResult();

    SpinXmlElement c7Value = (SpinXmlElement) c7Variable.getValue();

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<VariableEntity> variables = historyMigration.searchHistoricVariables("var");
    assertThat(variables).hasSize(1);
    VariableEntity variable = variables.getFirst();
    String expectedValue = c7Value.toString();
    assertVariableFields(variable, c7Variable, String.format("\"%s\"", expectedValue));
  }

  @Test
  public void shouldSkipUnsupportedByteArrayVariable() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "bytesVar", "foo".getBytes());

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<VariableEntity> variables = historyMigration.searchHistoricVariables("bytesVar");
    assertThat(variables).isEmpty();
  }

  @Test
  public void shouldSkipUnsupportedFileVariable() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    FileValue fileValue = Variables.fileValue("file.txt").file("ABC".getBytes()).mimeType("text/plain").create();
    runtimeService.setVariable(processInstance.getId(), "fileVar", fileValue);

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<VariableEntity> variables = historyMigration.searchHistoricVariables("fileVar");
    assertThat(variables).isEmpty();
  }

  @Test
  public void shouldSkipUnsupportedJavaSerializedVariable() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    ObjectValue objectValue = Variables.serializedObjectValue("serialized-data")
        .serializationDataFormat("application/x-java-serialized-object")
        .objectTypeName("java.lang.Object")
        .create();

    processEngineConfiguration.setJavaSerializationFormatEnabled(true);
    runtimeService.setVariable(processInstance.getId(), "javaSerializedVar", objectValue);

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<VariableEntity> variables = historyMigration.searchHistoricVariables("javaSerializedVar");
    assertThat(variables).isEmpty();
  }

  protected void assertVariableExists(String varName, Object expectedValue) {
    List<VariableEntity> variables = historyMigration.searchHistoricVariables(varName);
    assertThat(variables).hasSize(1);
    VariableEntity variable = variables.getFirst();
    assertThat(variable.name()).isEqualTo(varName);
    assertThat(variable.value()).isEqualTo(expectedValue);
  }

  protected void assertVariableFields(VariableEntity variable,
                                      HistoricVariableInstance c7Variable,
                                      Object expectedValue) {
    assertThat(variable.variableKey()).isNotNull();
    assertThat(variable.name()).isEqualTo(c7Variable.getName());
    assertThat(variable.value()).isEqualTo(expectedValue);
    assertThat(variable.processInstanceKey()).isNotNull();
    assertThat(variable.scopeKey()).isNotNull();
    assertThat(variable.isPreview()).isFalse();
  }

}
