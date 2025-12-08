/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.data.qa.runtime.element;

import static io.camunda.migration.data.constants.MigratorConstants.LEGACY_ID_VAR_NAME;
import static io.camunda.migration.data.impl.logging.RuntimeMigratorLogs.SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byTaskName;
import static io.camunda.migration.data.impl.logging.RuntimeValidatorLogs.CALL_ACTIVITY_LEGACY_ID_ERROR;

import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.RuntimeMigrationExtension;
import io.camunda.client.api.search.response.Variable;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.Optional;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@CamundaSpringProcessTest
public class SubprocessMigrationTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final RuntimeMigrationExtension runtimeMigration = new RuntimeMigrationExtension();

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

  @Test
  public void shouldMigrateCallActivityAndSubprocess() {
    // given
    deployer.deployProcessInC7AndC8("calledActivitySubprocess.bpmn");
    deployer.deployProcessInC7AndC8("callActivityProcess.bpmn");
    ProcessInstance parentInstance = runtimeService.startProcessInstanceByKey("callingProcessId");
    ProcessInstance subProcessInstance = runtimeService
        .createProcessInstanceQuery()
        .superProcessInstanceId(parentInstance.getProcessInstanceId())
        .singleResult();
    // when
    runtimeMigration.getMigrator().start();

    // then
    io.camunda.client.api.search.response.ProcessInstance c8ParentInstance =
        runtimeMigration.getCamundaClient().newProcessInstanceSearchRequest().filter(processInstanceFilter -> {
          processInstanceFilter.processDefinitionId("callingProcessId");
        }).execute().items().getFirst();

    Long c8ParentInstanceKey = c8ParentInstance.getProcessInstanceKey();
    Optional<Variable> variable = runtimeMigration.getVariableByScope(c8ParentInstanceKey, c8ParentInstanceKey, LEGACY_ID_VAR_NAME);
    assert variable.isPresent();
    assert variable.get().getValue().equals("\""+parentInstance.getProcessInstanceId()+"\"");

    assertThat(byProcessId("calledProcessInstanceId")).isActive()
        .hasActiveElements(byId("userTaskId"))
        .hasVariable(LEGACY_ID_VAR_NAME, subProcessInstance.getProcessInstanceId());
    assertThat(byTaskName("userTaskName")).isCreated().hasElementId("userTaskId");
  }

  @Test
  public void shouldSkipMigrationWhenPropagateAllParentVariablesIsFalse() {
    // given
    deployer.deployProcessInC7AndC8("calledActivitySubprocess.bpmn");
    deployer.deployProcessInC7AndC8("callActivityProcessNoPropagation.bpmn");
    ProcessInstance parentInstance = runtimeService.startProcessInstanceByKey("callingProcessIdNoPropagation");

    // when
    runtimeMigration.getMigrator().start();

    // then
    // verify no C8 instance was created
    runtimeMigration.assertThatProcessInstanceCountIsEqualTo(0);

    // verify the correct error message was logged
    logs.assertContains(formatMessage(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR,
        parentInstance.getId(), formatMessage(CALL_ACTIVITY_LEGACY_ID_ERROR, "callActivityId")));
  }

  @Test
  public void shouldNotSkipMigrationWhenPropagateAllParentVariablesIsFalseButMappingIsPresent() {
    // given
    deployer.deployProcessInC7AndC8("calledActivitySubprocess.bpmn");
    deployer.deployProcessInC7AndC8("callActivityProcessNoPropagationWithMapping.bpmn");
    ProcessInstance parentInstance = runtimeService.startProcessInstanceByKey("callingProcessIdNoPropagationWithMapping");
    ProcessInstance subProcessInstance = runtimeService
        .createProcessInstanceQuery()
        .superProcessInstanceId(parentInstance.getProcessInstanceId())
        .singleResult();

    // when
    runtimeMigration.getMigrator().start();

    // then
    assertThat(byProcessId("callingProcessIdNoPropagationWithMapping")).isActive()
        .hasVariable(LEGACY_ID_VAR_NAME, parentInstance.getProcessInstanceId());

    assertThat(byProcessId("calledProcessInstanceId")).isActive()
        .hasActiveElements(byId("userTaskId"))
        .hasVariable(LEGACY_ID_VAR_NAME, subProcessInstance.getProcessInstanceId());
    assertThat(byTaskName("userTaskName")).isCreated().hasElementId("userTaskId");
  }

}
