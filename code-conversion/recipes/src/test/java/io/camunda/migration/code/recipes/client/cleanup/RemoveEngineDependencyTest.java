/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.cleanup;

import io.camunda.migration.code.recipes.client.CleanupEngineDependencyRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveEngineDependencyTest implements RewriteTest {

    @Test
    void removeEngineDependencyTest() {
        rewriteRun(
                spec -> spec.recipe(new CleanupEngineDependencyRecipe()),
                //language=java
                java(
                        """
                                package org.camunda.community.migration.example;
                                
                                import org.camunda.bpm.engine.ProcessEngine;
                                import io.camunda.client.CamundaClient;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.stereotype.Component;
                                
                                import java.util.Map;
                                        
                                @Component
                                public class BroadcastSignalsTestClass {
                                    
                                    @Autowired
                                    private CamundaClient camundaClient;
                                    
                                    @Autowired
                                    private ProcessEngine engine;
                                                                                                 
                                    public void broadcastSignalGlobally(String signalName, Map<String, Object> variableMap) {
                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .variables(variableMap)
                                                .send()
                                                .join();
                                    }
                                }                                                                                                       
                                """,
                        """
                                package org.camunda.community.migration.example;
                                
                                import io.camunda.client.CamundaClient;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.stereotype.Component;
                                
                                import java.util.Map;
                                        
                                @Component
                                public class BroadcastSignalsTestClass {
                                    
                                    @Autowired
                                    private CamundaClient camundaClient;
                                    
                                    public void broadcastSignalGlobally(String signalName, Map<String, Object> variableMap) {
                                        camundaClient
                                                .newBroadcastSignalCommand()
                                                .signalName(signalName)
                                                .variables(variableMap)
                                                .send()
                                                .join();
                                    }
                                } 
                                """
                )
        );
    }

}
