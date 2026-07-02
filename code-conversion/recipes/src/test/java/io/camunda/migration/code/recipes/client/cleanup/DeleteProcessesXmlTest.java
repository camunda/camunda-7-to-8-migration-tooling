/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.cleanup;

import static org.openrewrite.xml.Assertions.xml;

import io.camunda.migration.code.recipes.client.DeleteProcessesXmlRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

class DeleteProcessesXmlTest implements RewriteTest {

  private static final String C7_PROCESSES_XML =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <process-application
        xmlns="http://www.camunda.org/schema/1.0/ProcessApplication"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <process-archive name="default">
          <properties>
            <property name="isScanForProcessDefinitions">false</property>
            <property name="isDeleteUponUndeploy">true</property>
          </properties>
        </process-archive>
      </process-application>
      """;

  @Test
  void deletesC7ProcessesXmlWithProcessApplicationSchema() {
    rewriteRun(
        spec -> spec.recipe(new DeleteProcessesXmlRecipe()),
        xml(
            C7_PROCESSES_XML,
            spec ->
                spec.path("src/main/resources/META-INF/processes.xml")
                    // null return signals that the recipe should delete this file
                    .after(s -> null)));
  }

  @Test
  void doesNotDeleteXmlWithoutC7ProcessApplicationSchema() {
    rewriteRun(
        spec -> spec.recipe(new DeleteProcessesXmlRecipe()),
        xml(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <processes xmlns="http://example.com/some-other-schema">
              <process id="myProcess"/>
            </processes>
            """,
            spec -> spec.path("src/main/resources/META-INF/processes.xml")));
  }

  @Test
  void doesNotDeleteC7ProcessesXmlOutsideMetaInf() {
    rewriteRun(
        spec -> spec.recipe(new DeleteProcessesXmlRecipe()),
        xml(
            C7_PROCESSES_XML,
            spec -> spec.path("src/main/resources/processes.xml")));
  }
}
