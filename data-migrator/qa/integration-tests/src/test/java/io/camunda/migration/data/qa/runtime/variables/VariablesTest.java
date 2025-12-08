/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime.variables;

import static io.camunda.migration.data.impl.logging.RuntimeMigratorLogs.SKIPPING_PROCESS_INSTANCE_VARIABLE_ERROR;
import static io.camunda.migration.data.impl.logging.VariableServiceLogs.BYTE_ARRAY_UNSUPPORTED_ERROR;
import static io.camunda.migration.data.impl.logging.VariableServiceLogs.FILE_TYPE_UNSUPPORTED_ERROR;
import static io.camunda.migration.data.impl.logging.VariableServiceLogs.JAVA_SERIALIZED_UNSUPPORTED_ERROR;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.SerializationDataFormats.JSON;
import static org.camunda.bpm.engine.variable.Variables.SerializationDataFormats.XML;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.data.qa.runtime.RuntimeMigrationAbstractTest;
import io.camunda.process.test.api.CamundaAssert;

import io.github.netmikey.logunit.api.LogCapturer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class VariablesTest extends RuntimeMigrationAbstractTest {

  @RegisterExtension
  protected final LogCapturer LOGS = LogCapturer.create().captureForType(RuntimeMigrator.class);

  public static final String SUB_PROCESS = "subProcess";
  public static final String PARALLEL = "parallel";

  @Autowired
  protected ObjectMapper objectMapper;

  @Test
  public void shouldSetVariableWithNullValue() {
    // given
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(simpleProcessInstance.getId(), "myVariable", null);

    // when
    runtimeMigrator.start();

    // then
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("myVariable", null);
  }

  @Test
  public void shouldSetPrimitiveVariables() {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    // given process state in c7
    VariableMap variables = Variables.createVariables();
    variables.putValue("stringVar", "myStringVar");
    variables.putValue("booleanVar", true);
    variables.putValue("integerVar", 1234);
    variables.putValue("doubleVar", 1.5d);
    variables.putValue("shortVar", (short) 1);
    variables.putValue("longVar", 2_147_483_648L);

    runtimeService.startProcessInstanceByKey("simpleProcess", variables);

    // when running runtime migration
    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("stringVar", "myStringVar")
        .hasVariable("booleanVar", true)
        .hasVariable("integerVar", 1234)
        .hasVariable("doubleVar", 1.5d)
        .hasVariable("shortVar", (short) 1)
        .hasVariable("longVar", 2_147_483_648L);
  }

  @Test
  public void shouldNotSetUnsupportedObjectTypes() {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    String piIdFloat = runtimeService.startProcessInstanceByKey("simpleProcess", Collections.singletonMap("floatVar", 1.5f)).getId();
    String piIdByte = runtimeService.startProcessInstanceByKey("simpleProcess", Collections.singletonMap("byteVar", (byte) 1)).getId();
    String piIdChar = runtimeService.startProcessInstanceByKey("simpleProcess", Collections.singletonMap("charVar", (char) 1)).getId();
    String piIdList = runtimeService.startProcessInstanceByKey("simpleProcess", Collections.singletonMap("stringList", Arrays.asList("one", "two", "three"))).getId();
    String piIdMap = runtimeService.startProcessInstanceByKey("simpleProcess", Collections.singletonMap("map", Collections.singletonMap(1, "one"))).getId();

    // when running runtime migration
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);

    Arrays.asList(piIdFloat, piIdByte, piIdChar, piIdList, piIdMap)
        .forEach(piId -> LOGS.assertContains(
            formatMessage(SKIPPING_PROCESS_INSTANCE_VARIABLE_ERROR, piId,
                JAVA_SERIALIZED_UNSUPPORTED_ERROR)));
  }

  @Test
  public void shouldNotSetUnsupportedBytesType() {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    String c7Id = runtimeService.startProcessInstanceByKey("simpleProcess", Collections.singletonMap("bytesVar", "foo".getBytes())).getId();

    // when running runtime migration
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);
    LOGS.assertContains(
        formatMessage(SKIPPING_PROCESS_INSTANCE_VARIABLE_ERROR, c7Id,
            BYTE_ARRAY_UNSUPPORTED_ERROR));
  }

  @Test
  public void shouldSetInvalidVariableNameInFeel() {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    // given process state in c7
    VariableMap variables = Variables.createVariables();
    variables.putValue("1stC", "value");
    variables.putValue("st C", "value");
    variables.putValue("st/C", "value");
    variables.putValue("st-C", "value");
    variables.putValue("null", "value");

    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess", variables);

    // when running runtime migration
    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("1stC", "value")
        .hasVariable("st C", "value")
        .hasVariable("st/C", "value")
        .hasVariable("st-C", "value")
        .hasVariable("null", "value");
  }

  @Test
  public void shouldSetDateVariable() {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    // given process state in c7
    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    Date date = new Date();
    runtimeService.setVariable(simpleProcessInstance.getId(), "dateVar", date);

    // when running runtime migration
    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("dateVar", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(date));
  }

  @Test
  public void shouldSetXmlObjectVariable() {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    // given process state in c7
    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
        "<xmlSerializable>" +
          "<booleanProperty>true</booleanProperty>" +
          "<intProperty>42</intProperty>" +
          "<stringProperty>a String</stringProperty>" +
        "</xmlSerializable>";

    ObjectValue objectValue = Variables.serializedObjectValue(xml)
        .serializationDataFormat(XML)
        .objectTypeName("io.camunda.migration.data.qa.runtime.variables.XmlSerializable")
        .create();

    runtimeService.setVariable(simpleProcessInstance.getId(), "var", objectValue);

    // when running runtime migration
    runtimeMigrator.start();

    // then
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("var", xml);
  }

  @Test
  public void shouldSetGlobalJsonObjectVariable() throws JsonProcessingException {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    // given process state in c7
    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    String json = "{\"stringProperty\":\"a String\",\"intProperty\":42,\"booleanProperty\":true}";
    ObjectValue objectValue = Variables.serializedObjectValue(json)
        .serializationDataFormat(JSON)
        .objectTypeName("io.camunda.migration.data.qa.runtime.variables.JsonSerializable")
        .create();

    runtimeService.setVariable(simpleProcessInstance.getId(), "var", objectValue);

    // when running runtime migration
    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("var", objectMapper.readValue(json, JsonNode.class));
  }

  @Test
  public void shouldNotSetJsonObjectDueToSyntaxError() {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    // given process state in c7
    String c7Id = runtimeService.startProcessInstanceByKey("simpleProcess").getId();

    String json = "{ broken syntax!";
    ObjectValue objectValue = Variables.serializedObjectValue(json)
        .serializationDataFormat(JSON)
        .objectTypeName("io.camunda.migration.data.qa.runtime.variables.JsonSerializable")
        .create();

    runtimeService.setVariable(c7Id, "var", objectValue);

    // when running runtime migration
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);
    LOGS.assertContains(formatMessage(SKIPPING_PROCESS_INSTANCE_VARIABLE_ERROR, c7Id,
        "Error while deserializing JSON into Map type."));
  }

  @Test
  public void shouldSetLocalJsonObjectVariable() throws JsonProcessingException {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    // given process state in c7
    runtimeService.startProcessInstanceByKey("simpleProcess");

    String json = "{\"stringProperty\":\"a String\",\"intProperty\":42,\"booleanProperty\":true}";
    ObjectValue objectValue = Variables.serializedObjectValue(json)
        .serializationDataFormat(JSON)
        .objectTypeName("io.camunda.migration.data.qa.runtime.variables.JsonSerializable")
        .create();

    String activityInstanceId = runtimeService.createExecutionQuery().activityId("userTask1").singleResult().getId();
    runtimeService.setVariable(activityInstanceId, "var", objectValue);

    // when running runtime migration
    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("var", objectMapper.readValue(json, JsonNode.class));
  }

  @Test
  public void shouldNotSetFileVariable() {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    // given process state in c7
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
    String c7Id = runtimeService.startProcessInstanceByKey("simpleProcess", fileVar).getId();

    // when running runtime migration
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);
    LOGS.assertContains(
        formatMessage(SKIPPING_PROCESS_INSTANCE_VARIABLE_ERROR, c7Id,
            FILE_TYPE_UNSUPPORTED_ERROR));
  }

  @Test
  public void shouldSetGlobalVariable() {
    // deploy processes
    deploySubprocessModels();
    Map<String, Object> vars = new HashMap<>();
    vars.put("variable1", "value1");
    vars.put("variable2", "value2");

    // given
    runtimeService.startProcessInstanceByKey(SUB_PROCESS, vars);
    Task currentTask = taskService.createTaskQuery().singleResult();
    runtimeService.setVariable(currentTask.getExecutionId(), "variable3", "value3");

    // when running runtime migration
    runtimeMigrator.start();

    // then
    CamundaAssert.assertThat(byProcessId(SUB_PROCESS))
        .hasVariableNames("variable1", "variable2", "variable3");
  }

  @Test
  public void shouldSetLocalVariable() {
    // deploy processes
    deployParallelModels();
    Map<String, Object> vars = new HashMap<>();
    vars.put("variable1", "value1");

    // given
    runtimeService.startProcessInstanceByKey(PARALLEL, vars);
    Task currentTask = taskService.createTaskQuery().taskDefinitionKey("userTask_1").singleResult();
    runtimeService.setVariableLocal(currentTask.getExecutionId(), "localVariable", "local value");

    // when running runtime migration
    runtimeMigrator.start();

    // then
    CamundaAssert.assertThat(byProcessId(PARALLEL)).hasVariable("variable1", "value1");
    CamundaAssert.assertThat(byProcessId(PARALLEL)).hasLocalVariable(byId("userTask_1"), "localVariable", "local value");
  }

  @Test
  public void shouldSetTaskVariable() {
    // deploy processes
    deployParallelModels();
    Map<String, Object> vars = new HashMap<>();
    vars.put("variable1", "value1");

    // given
    runtimeService.startProcessInstanceByKey(PARALLEL, vars);
    Task currentTask = taskService.createTaskQuery().taskDefinitionKey("userTask_1").singleResult();
    taskService.setVariableLocal(currentTask.getId(), "localVariable", "local value");

    // when running runtime migration
    runtimeMigrator.start();

    // then
    CamundaAssert.assertThat(byProcessId(PARALLEL)).hasVariable("variable1", "value1");
    CamundaAssert.assertThat(byProcessId(PARALLEL)).hasLocalVariable(byId("userTask_1"), "localVariable", "local value");
  }

  @Test
  @Disabled("https://github.com/camunda/camunda-bpm-platform/issues/5235")
  public void shouldSetVariableIntoSubprocess() {
    // deploy processes
    deploySubprocessModels();
    Map<String, Object> vars = new HashMap<>();
    vars.put("variable1", "value1");
    vars.put("variable2", "value2");

    // given
    runtimeService.startProcessInstanceByKey(SUB_PROCESS, vars);
    Task currentTask = taskService.createTaskQuery().singleResult();
    runtimeService.setVariableLocal(currentTask.getExecutionId(), "localVariable", "local value");

    // when running runtime migration
    runtimeMigrator.start();

    // then
    CamundaAssert.assertThat(byProcessId(SUB_PROCESS)).hasVariableNames("variable1", "variable2");
    CamundaAssert.assertThat(byProcessId(SUB_PROCESS)).hasLocalVariable(byId("userTask_1"), "localVariable", "local value");
  }

  protected void deploySubprocessModels() {
    String process = SUB_PROCESS;
    // C7
    var c7Model = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(process)
        .startEvent("start_1")
        .subProcess("sub_1")
        .embeddedSubProcess()
        .startEvent("start_2")
        .userTask("userTask_1")
        .endEvent("end_2")
        .subProcessDone()
        .endEvent("end_1")
        .done();

    // C8
    var c8Model = io.camunda.zeebe.model.bpmn.Bpmn.createExecutableProcess(process)
        .startEvent("start_1")
        .zeebeEndExecutionListener("migrator")
        .subProcess("sub_1")
        .embeddedSubProcess()
        .startEvent("start_2")
        .userTask("userTask_1")
        .endEvent("end_2")
        .subProcessDone()
        .endEvent("end_1")
        .done();

    deployer.deployModelInstance(process, c7Model, c8Model);
  }

  protected void deployParallelModels() {
    String process = PARALLEL;
    // C7
    var c7Model = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(process)
        .startEvent("start_1")
        .parallelGateway("fork")
        .userTask("userTask_1")
        .parallelGateway("join")
        .endEvent("end_1")
        .moveToNode("fork")
        .userTask("userTask_2")
        .connectTo("join")
        .done();

    // C8
    var c8Model = io.camunda.zeebe.model.bpmn.Bpmn.createExecutableProcess(process)
        .startEvent("start_1")
        .zeebeEndExecutionListener("migrator")
        .parallelGateway("fork")
        .userTask("userTask_1")
        .parallelGateway("join")
        .endEvent("end_1")
        .moveToNode("fork")
        .userTask("userTask_2")
        .connectTo("join")
        .done();

    deployer.deployModelInstance(process, c7Model, c8Model);
  }
}
