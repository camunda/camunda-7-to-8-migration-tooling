/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.process_instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@CamundaSpringProcessTest
@TestPropertySource(
    properties = "camunda.process-test.camunda-docker-image-version=8.9.0")
class ProcessInstanceVariableSearchTest {

    private static final String PROCESS_DEFINITION_ID = "ProjectInstanceSearch";
    private static final String PROJECT_VARIABLE = "projectId";

    @Autowired
    private CamundaClient camundaClient;
    @Autowired
    private SearchProcessInstances searchProcessInstances;

    @Test
    void searchesByVariableValueForConcurrentBusinessIds() {
        ProcessInstanceEvent first = start("business-1", "business-1");
        ProcessInstanceEvent second = start("business-2", "business-2");

        assertThat(first.getProcessInstanceKey()).isNotEqualTo(second.getProcessInstanceKey());

        await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () -> {
                            assertOnlyMatches("business-1", first);
                            assertOnlyMatches("business-2", second);

                            var firstPage =
                                    camundaClient
                                            .newProcessInstanceSearchRequest()
                                            .page(page -> page.limit(1))
                                            .filter(
                                                    filter ->
                                                            filter
                                                                    .processDefinitionId(
                                                                            PROCESS_DEFINITION_ID)
                                                                    .state(
                                                                            ProcessInstanceState
                                                                                    .ACTIVE))
                                            .send()
                                            .join();

                            assertThat(firstPage.items()).hasSize(1);
                            assertThat(firstPage.page().totalItems()).isEqualTo(2L);
                        });

        ProcessInstanceEvent duplicate = start("business-1-duplicate", "business-1");
        await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertThatThrownBy(
                                                () ->
                                                        searchProcessInstances
                                                                .findSingleActiveByVariable(
                                                                        PROCESS_DEFINITION_ID,
                                                                        PROJECT_VARIABLE,
                                                                        "business-1"))
                                        .isInstanceOf(IllegalStateException.class));

        var pagedMatches =
                camundaClient
                        .newProcessInstanceSearchRequest()
                        .page(page -> page.limit(1))
                        .filter(
                                filter ->
                                        filter
                                                .processDefinitionId(PROCESS_DEFINITION_ID)
                                                .state(ProcessInstanceState.ACTIVE))
                        .send()
                        .join();

        assertThat(pagedMatches.items()).hasSize(1);
        assertThat(pagedMatches.page().totalItems()).isEqualTo(3L);
        assertThat(duplicate.getProcessInstanceKey())
                .isNotIn(first.getProcessInstanceKey(), second.getProcessInstanceKey());
    }

    private ProcessInstanceEvent start(String businessId, String projectId) {
        return camundaClient
                .newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_DEFINITION_ID)
                .latestVersion()
                .businessId(businessId)
                .variables(Map.of(PROJECT_VARIABLE, projectId))
                .send()
                .join();
    }

    private void assertOnlyMatches(String businessId, ProcessInstanceEvent expected) {
        ProcessInstance result =
                searchProcessInstances.findSingleActiveByVariable(
                        PROCESS_DEFINITION_ID, PROJECT_VARIABLE, businessId);

        assertThat(result).isNotNull();
        assertThat(result.getProcessInstanceKey()).isEqualTo(expected.getProcessInstanceKey());
        assertThat(result.getBusinessId()).isEqualTo(businessId);
        assertThat(result.getState()).isEqualTo(ProcessInstanceState.ACTIVE);
    }
}
