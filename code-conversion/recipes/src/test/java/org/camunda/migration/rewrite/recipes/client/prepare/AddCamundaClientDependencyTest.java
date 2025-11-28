package org.camunda.migration.rewrite.recipes.client.prepare;

import org.camunda.migration.rewrite.recipes.client.PrepareCamundaClientDependencyRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddCamundaClientDependencyTest implements RewriteTest {

    @Test
    void addCamundaClientDependencyTest() {
        rewriteRun(
                spec -> spec.recipe(new PrepareCamundaClientDependencyRecipe()),
                //language=java
                java(
                        """
                                package org.camunda.community.migration.example;
                                        
                                import org.camunda.bpm.engine.ProcessEngine;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.stereotype.Component;
                                
                                import java.util.Map;
                                        
                                @Component
                                public class BroadcastSignalsTestClass {
                                        
                                    @Autowired
                                    private ProcessEngine engine;
                                                                                                 
                                    public void broadcastSignalGlobally(String signalName, Map<String, Object> variableMap) {
                                        engine.getRuntimeService().signalEventReceived(signalName, variableMap);
                                    }
                                            
                                    public void broadcastSignalGloballyViaBuilder(String signalName, String tenantId, Map<String, Object> variableMap) {
                                        engine.getRuntimeService().createSignalEvent(signalName)
                                                .tenantId(tenantId)
                                                .setVariables(variableMap)
                                                .send();
                                    }
                                }                                                                                                         
                                """,
                        """
                                package org.camunda.community.migration.example;
                                  
                                import io.camunda.client.CamundaClient;        
                                import org.camunda.bpm.engine.ProcessEngine;
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
                                        engine.getRuntimeService().signalEventReceived(signalName, variableMap);
                                    }
                                            
                                    public void broadcastSignalGloballyViaBuilder(String signalName, String tenantId, Map<String, Object> variableMap) {
                                        engine.getRuntimeService().createSignalEvent(signalName)
                                                .tenantId(tenantId)
                                                .setVariables(variableMap)
                                                .send();
                                    }
                                }           
                                """
                )
        );
    }

}
