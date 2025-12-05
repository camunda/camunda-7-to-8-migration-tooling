/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.date.runtime.variables;

import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.migration.date.runtime.RuntimeMigrationAbstractTest;
import io.camunda.process.test.api.CamundaAssert;
import org.camunda.spin.json.SpinJsonNode;
import org.camunda.spin.plugin.variable.SpinValues;
import org.camunda.spin.plugin.variable.value.JsonValue;
import org.camunda.spin.plugin.variable.value.XmlValue;
import org.camunda.spin.xml.SpinXmlElement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SpinVariablesTest extends RuntimeMigrationAbstractTest {

  @Autowired
  protected ObjectMapper objectMapper;

  @Test
  public void shouldSetSpinJsonVariable() throws JsonProcessingException {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    // given process state in c7
    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    String json = "{\"name\" : \"jonny\","
        + "\"address\" : {"
        + "\"street\" : \"12 High Street\","
        + "\"post code\" : 1234"
        + "}"
        + "}";
    JsonValue jsonValue = SpinValues.jsonValue(json).create();

    runtimeService.setVariable(simpleProcessInstance.getId(), "var", jsonValue);
    SpinJsonNode c7value = (SpinJsonNode) runtimeService.getVariable(simpleProcessInstance.getId(), "var");

    // when running runtime migration
    runtimeMigrator.start();

    // then
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("var", objectMapper.readValue(c7value.toString(), JsonNode.class));
  }

  @Test
  public void shouldSetXmlVariable() {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    // given process state in c7
    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

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
    runtimeService.setVariable(simpleProcessInstance.getId(), "var", xmlValue);
    SpinXmlElement c7value = (SpinXmlElement) runtimeService.getVariable(simpleProcessInstance.getId(), "var");

    // when running runtime migration
    runtimeMigrator.start();

    // then
    CamundaAssert.assertThat(byProcessId("simpleProcess")).hasVariable("var", c7value.toString());
  }
}