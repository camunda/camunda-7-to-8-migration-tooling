/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.spin.Spin.JSON;
import static org.camunda.spin.Spin.XML;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpinDataFormatsTest {

  @Test
  void shouldUseManagedJacksonForSpinJson() {
    assertThat(JSON("{\"name\":\"Jonny\"}").unwrap()).isInstanceOf(JsonNode.class);
  }

  @Test
  void shouldRoundTripSpinJson() {
    var value = Map.of("customer", Map.of("name", "Jonny"), "items", List.of(1, 2));

    var json = JSON(value);
    var restored = JSON(json.toString());
    Map<?, ?> restoredValue = restored.mapTo(Map.class);

    assertThat(restoredValue).isEqualTo(value);
    assertThat(restored.prop("customer").prop("name").stringValue()).isEqualTo("Jonny");
    assertThat(restored.jsonPath("$.items[1]").numberValue()).isEqualTo(2);
  }

  @Test
  void shouldRoundTripSpinXml() {
    var xml = XML("<customer name=\"Jonny\"><address><street>High Street</street></address></customer>");

    xml.childElement("address").childElement("street").textContent("New Street");
    var restored = XML(xml.toString());

    assertThat(restored.attr("name").value()).isEqualTo("Jonny");
    assertThat(restored.childElement("address").childElement("street").textContent()).isEqualTo("New Street");
  }
}
