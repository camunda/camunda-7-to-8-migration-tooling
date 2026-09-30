/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.timer;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@CamundaProcessTest
class LiveTimerAcceptanceTest {

  private static final String CASE_1_PROCESS_ID = "Sample";
  private static final String ACTIVE_TIMER_PROCESS_ID = "active-timer-rescheduling";
  private static final String PROJECT_ID = "project-2927";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String BPMN_NAMESPACE =
      "http://www.omg.org/spec/BPMN/20100524/MODEL";
  private CamundaProcessTestContext processTestContext;

  private record TimerStart(String id, String cycle) {}

  @Test
  void shouldVerifyDeploymentSetAndRearmAnActiveTimerTwice() throws IOException {
    CamundaClient camundaClient = processTestContext.createClient();
    TimerStart moduleATimer = timerStart("module-a/sample.bpmn");
    TimerStart moduleBTimer = timerStart("module-b/sample.bpmn");
    assertEquals(new TimerStart("RecurringStart", "R/PT5S"), moduleATimer);
    assertNull(moduleBTimer);
    var firstDeployment =
        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath("module-a/sample.bpmn")
            .send()
            .join();
    assertTrue(
        firstDeployment.getProcesses().stream()
            .anyMatch(p -> CASE_1_PROCESS_ID.equals(p.getBpmnProcessId())));

    processTestContext.increaseTime(Duration.ofSeconds(6));
    await().atMost(Duration.ofSeconds(20)).until(() -> activeSampleCount(camundaClient) > 0);
    int timerStartedInstancesBeforeReplacement = activeSampleCount(camundaClient);

    var secondDeployment =
        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath("module-b/sample.bpmn")
            .send()
            .join();
    assertTrue(
        secondDeployment.getProcesses().stream()
            .anyMatch(p -> CASE_1_PROCESS_ID.equals(p.getBpmnProcessId())));

    processTestContext.increaseTime(Duration.ofSeconds(16));
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                assertEquals(
                    timerStartedInstancesBeforeReplacement, activeSampleCount(camundaClient)));
    int timerStartedInstancesAfterReplacement = activeSampleCount(camundaClient);

    ProcessInstanceEvent latestVersionInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(CASE_1_PROCESS_ID)
            .latestVersion()
            .send()
            .join();
    assertThat(latestVersionInstance).isActive().hasActiveElements("Hold_B");

    OffsetDateTime baseTime =
        OffsetDateTime.ofInstant(
            processTestContext.getCurrentTime().plusSeconds(30).truncatedTo(ChronoUnit.SECONDS),
            ZoneOffset.UTC);
    processTestContext.setTime(baseTime.toInstant());
    OffsetDateTime originalDeadline = baseTime.plusMinutes(5);
    OffsetDateTime firstUpdatedDeadline = baseTime.plusMinutes(15);
    OffsetDateTime finalDeadline = baseTime.plusMinutes(25);
    assertTrue(
        processTestContext.getCurrentTime().isBefore(originalDeadline.toInstant()),
        "The original deadline must remain in the future while the timer is activated");
    var timerExpressionResult =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=date and time(terminationDate)")
            .variables(Map.of("terminationDate", originalDeadline.toString()))
            .send()
            .join();
    assertEquals(
        originalDeadline.toInstant(),
        Instant.parse(timerExpressionResult.getResult().toString()));
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("active-timer-rescheduling.bpmn")
        .send()
        .join();
    ProcessInstanceEvent timerInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(ACTIVE_TIMER_PROCESS_ID)
            .latestVersion()
            .variables(
                Map.of(
                    "projectId", PROJECT_ID,
                    "terminationDate", originalDeadline.toString()))
            .send()
            .join();
    assertWaitingForDateOrUpdate(timerInstance);

    TerminationDateUpdater updater = new TerminationDateUpdater(camundaClient);

    assertEquals(
        timerInstance.getProcessInstanceKey(),
        updater.update(PROJECT_ID, firstUpdatedDeadline.toString()).getProcessInstanceKey());
    assertWaitingForDateOrUpdate(timerInstance);

    processTestContext.increaseTime(
        Duration.between(processTestContext.getCurrentTime(), originalDeadline.plusSeconds(1)));
    assertWaitingForDateOrUpdate(timerInstance);

    assertEquals(
        timerInstance.getProcessInstanceKey(),
        updater.update(PROJECT_ID, finalDeadline.toString()).getProcessInstanceKey());
    assertWaitingForDateOrUpdate(timerInstance);

    processTestContext.increaseTime(
        Duration.between(processTestContext.getCurrentTime(), firstUpdatedDeadline.plusSeconds(1)));
    assertWaitingForDateOrUpdate(timerInstance);
    processTestContext.increaseTime(
        Duration.between(processTestContext.getCurrentTime(), finalDeadline.minusSeconds(1)));
    assertWaitingForDateOrUpdate(timerInstance);

    processTestContext.increaseTime(Duration.ofSeconds(2));
    assertThat(timerInstance)
        .isCompleted()
        .hasCompletedElement("DeadlineReached", 1);

    writeObservation(
        processTestContext,
        timerStartedInstancesBeforeReplacement,
        timerStartedInstancesAfterReplacement,
        moduleATimer,
        moduleBTimer,
        originalDeadline,
        firstUpdatedDeadline,
        finalDeadline);
  }

  private static int activeSampleCount(CamundaClient camundaClient) {
    return camundaClient
        .newProcessInstanceSearchRequest()
        .filter(
            filter ->
                filter
                    .processDefinitionId(CASE_1_PROCESS_ID)
                    .state(ProcessInstanceState.ACTIVE))
        .send()
        .join()
        .items()
        .size();
  }

  private static void assertWaitingForDateOrUpdate(ProcessInstanceEvent instance) {
    assertThat(instance).isActive().hasActiveElements("WaitForDateOrUpdate");
  }

  private static void writeObservation(
      CamundaProcessTestContext processTestContext,
      int timerStartsBeforeReplacement,
      int timerStartsAfterReplacement,
      TimerStart moduleATimer,
      TimerStart moduleBTimer,
      OffsetDateTime originalDeadline,
      OffsetDateTime firstUpdatedDeadline,
      OffsetDateTime finalDeadline)
      throws IOException {
    Map<String, Object> case1 =
        Map.of(
            "module_a_model", "module-a/src/main/resources/module-a/sample.bpmn",
            "module_b_model", "module-b/src/main/resources/module-b/sample.bpmn",
            "process_id", CASE_1_PROCESS_ID,
            "module_a_timer_start_id", moduleATimer.id(),
            "module_a_cycle", moduleATimer.cycle(),
            "module_b_has_timer_start", moduleBTimer != null,
            "timer_started_instances_before_replacement", timerStartsBeforeReplacement,
            "timer_started_instances_after_replacement", timerStartsAfterReplacement,
            "new_instances_after_replacement",
                timerStartsAfterReplacement - timerStartsBeforeReplacement,
            "latest_by_id_start_element", "Hold_B");
    Map<String, Object> activeTimer =
        Map.ofEntries(
            Map.entry(
                "model_path", "process-test/src/test/resources/active-timer-rescheduling.bpmn"),
            Map.entry("process_id", ACTIVE_TIMER_PROCESS_ID),
            Map.entry("timer_id", "TerminationTimer"),
            Map.entry("strategy", "message_rearm"),
            Map.entry("message_name", "TerminationDateChanged"),
            Map.entry("correlation_key_variable", "projectId"),
            Map.entry("date_variable", "terminationDate"),
            Map.entry("timer_was_active_before_first_update", true),
            Map.entry("updates",
                List.of(
                    Map.of(
                        "old_deadline", originalDeadline.toString(),
                        "new_deadline", firstUpdatedDeadline.toString(),
                        "timer_active_before_update", true,
                        "correlated", true),
                    Map.of(
                        "old_deadline", firstUpdatedDeadline.toString(),
                        "new_deadline", finalDeadline.toString(),
                        "timer_active_before_update", true,
                        "correlated", true))),
            Map.entry("obsolete_deadlines",
                List.of(
                    Map.of("deadline", originalDeadline.toString(), "fire_count", 0),
                    Map.of("deadline", firstUpdatedDeadline.toString(), "fire_count", 0))),
            Map.entry("advanced_past_obsolete_deadlines", true),
            Map.entry("final_deadline", finalDeadline.toString()),
            Map.entry("final_deadline_fire_count", 1),
            Map.entry("final_deadline_fired_at", processTestContext.getCurrentTime().toString()));

    Path observation =
        Path.of(
            System.getProperty(
                "timer.fixture.observation", "target/acceptance-observation.json"));
    Files.createDirectories(observation.getParent());
    OBJECT_MAPPER.writeValue(
        observation.toFile(), Map.of("case1", case1, "active_timer", activeTimer));
  }

  private static TimerStart timerStart(String resource) throws IOException {
    try (InputStream input =
        LiveTimerAcceptanceTest.class.getClassLoader().getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("Missing BPMN fixture resource: " + resource);
      }
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      NodeList starts =
          factory
              .newDocumentBuilder()
              .parse(input)
              .getElementsByTagNameNS(BPMN_NAMESPACE, "startEvent");
      TimerStart found = null;
      for (int index = 0; index < starts.getLength(); index++) {
        Element start = (Element) starts.item(index);
        NodeList definitions =
            start.getElementsByTagNameNS(BPMN_NAMESPACE, "timerEventDefinition");
        if (definitions.getLength() == 0) {
          continue;
        }
        if (definitions.getLength() != 1 || found != null) {
          throw new IOException("Expected one repeating timer start in " + resource);
        }
        NodeList cycles =
            ((Element) definitions.item(0)).getElementsByTagNameNS(BPMN_NAMESPACE, "timeCycle");
        if (cycles.getLength() != 1) {
          throw new IOException("Expected one repeating timer cycle in " + resource);
        }
        found = new TimerStart(
            start.getAttribute("id"), cycles.item(0).getTextContent().trim());
      }
      return found;
    } catch (ParserConfigurationException | SAXException exception) {
      throw new IOException("Could not read BPMN fixture resource: " + resource, exception);
    }
  }
}
