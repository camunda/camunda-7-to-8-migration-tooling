/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.call_activity_scope;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class CallActivityInputScopeModelTest {

    private static final String ZEEBE_NS = "http://camunda.org/schema/zeebe/1.0";

    @Test
    void callActivityMapsOnlySelectedParentInputs() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        try (InputStream model = getClass().getResourceAsStream(
                "/call-activity-input-scope-parent.bpmn")) {
            final Document document = factory.newDocumentBuilder().parse(model);
            final NodeList calledElements = document.getElementsByTagNameNS(ZEEBE_NS, "calledElement");
            final NodeList inputs = document.getElementsByTagNameNS(ZEEBE_NS, "input");

            assertThat(calledElements.getLength()).isEqualTo(1);
            assertThat(((Element) calledElements.item(0)).getAttribute("propagateAllParentVariables"))
                    .isEqualTo("false");
            assertThat(inputs.getLength()).isEqualTo(1);

            final Element input = (Element) inputs.item(0);
            assertThat(input.getAttribute("source")).isEqualTo("=selectedInput");
            assertThat(input.getAttribute("target")).isEqualTo("selectedInput");
        }
    }
}
