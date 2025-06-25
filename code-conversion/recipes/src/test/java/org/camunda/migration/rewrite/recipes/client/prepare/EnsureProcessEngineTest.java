package org.camunda.migration.rewrite.recipes.client.prepare;

import org.camunda.migration.rewrite.recipes.client.prepare.EnsureProcessEngineRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class EnsureProcessEngineTest implements RewriteTest {

    @Test
    void ensureProcessEngineTest() {
        rewriteRun(
                spec -> spec.recipe(new EnsureProcessEngineRecipe()),
                //language=java
                java(
                        """
                                package org.camunda.community.migration.example;
                                        
                                import org.camunda.bpm.engine.RuntimeService;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.stereotype.Component;
                                                                
                                import java.util.Map;
                                        
                                @Component
                                public class BroadcastSignalsTestClass {
                                    
                                    @Autowired
                                    private RuntimeService runtimeService;
                                                                                                 
                                    public void broadcastSignalGlobally(String signalName, Map<String, Object> variableMap) {
                                        runtimeService.signalEventReceived(signalName, variableMap);
                                    }
                                            
                                    public void broadcastSignalGloballyViaBuilder(String signalName, String tenantId, Map<String, Object> variableMap) {
                                        runtimeService.createSignalEvent(signalName)
                                                .tenantId(tenantId)
                                                .setVariables(variableMap)
                                                .send();
                                    }
                                }                                                                                                    
                                """,
                        """
                                package org.camunda.community.migration.example;
                                                                
                                import org.camunda.bpm.engine.ProcessEngine;
                                import org.camunda.bpm.engine.RuntimeService;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.stereotype.Component;
                                                                
                                import java.util.Map;
                                        
                                @Component
                                public class BroadcastSignalsTestClass {
                                        
                                    @Autowired
                                    private ProcessEngine engine;
                                    
                                    @Autowired
                                    private RuntimeService runtimeService;
                                                                                                 
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
