package org.camunda.migration.rewrite.recipes.client;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddCamundaClientWrapperDependencyTest implements RewriteTest {

    @Test
    void addCamundaClientWrapperDependencyTest() {
        rewriteRun(
                spec -> spec.recipe(new AddCamundaClientWrapperDependencyRecipe()),
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
                                        
                                import org.camunda.bpm.engine.ProcessEngine;
                                import org.camunda.migration.rewrite.recipes.client.CamundaClientWrapper;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.stereotype.Component;
                                
                                import java.util.Map;
                                        
                                @Component
                                public class BroadcastSignalsTestClass {
                                        
                                    @Autowired
                                    private CamundaClientWrapper camundaClientWrapper;
                                    
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
