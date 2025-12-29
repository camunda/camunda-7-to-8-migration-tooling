/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.migrate;

import static org.openrewrite.java.Assertions.java;

import io.camunda.migration.code.recipes.client.MigrateProcessInstanceQueryMethodsRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

public class ReplaceProcessInstanceQueryMethodsTest implements RewriteTest {

  @Test
  void replaceUserTaskMethodsTest() {
    rewriteRun(spec -> spec.recipe(new MigrateProcessInstanceQueryMethodsRecipe()),
        java(
"""
package org.camunda.community.migration.example;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import io.camunda.client.CamundaClient;
import org.camunda.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.List;

@Component
public class HandleProcessInstanceQueryMethodsTestClass {

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private CamundaClient camundaClient;
    
    public void processInstanceQueryMethods(String activityIdIn, String businessKey, String processDefinitionKey) {
    
        engine.getRuntimeService().createProcessInstanceQuery()
                .activityIdIn(activityIdIn)
                .active()
                .list();
                
        engine.getRuntimeService().createProcessInstanceQuery()
               .processInstanceBusinessKey(businessKey)
               .active()
               .list();
               
        engine.getRuntimeService().createProcessInstanceQuery()
               .processInstanceBusinessKey(businessKey)
               .processDefinitionKey(processDefinitionKey);
    }
}
""",
"""
package org.camunda.community.migration.example;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import io.camunda.client.CamundaClient;
import org.camunda.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.List;

@Component
public class HandleProcessInstanceQueryMethodsTestClass {

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private CamundaClient camundaClient;
    
    public void processInstanceQueryMethods(String activityIdIn, String businessKey, String processDefinitionKey) {
    
        camundaClient
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                        .elementId(activityIdIn)
                        .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .items();
                
        // processInstanceBusinessKey was removed - business key concept changed in Camunda 8
        camundaClient
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter
                        .state(ProcessInstanceState.ACTIVE))
                .send()
                .join()
                .items();
                
        // processInstanceBusinessKey was removed - business key concept changed in Camunda 8
        camundaClient
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter.processDefinitionKey(Long.valueOf(processDefinitionKey)));
    }
}
"""
        ));
  }
}
