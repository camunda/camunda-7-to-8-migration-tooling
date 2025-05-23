package org.camunda.migration.rewrite.recipes.client;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.ShortenFullyQualifiedTypeReferences;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class TryShortenedImportTest implements RewriteTest {

    @Test
    void tryShortenedImportTest() {
        rewriteRun(
                spec -> spec.recipe(new ShortenFullyQualifiedTypeReferences()),
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
                                
                                import org.camunda.migration.rewrite.recipes.glue.CamundaClientWrapper;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.stereotype.Component;
                                
                                import java.util.Map;
                                        
                                @Component
                                public class BroadcastSignalsTestClass {
                                    
                                    @Autowired
                                    private CamundaClientWrapper camundaClientWrapper;
                                    
                                    public void broadcastSignalGlobally(String signalName, Map<String, Object> variableMap) {
                                        camundaClientWrapper.broadcastSignalWithoutTenantId(signalName, variableMap);
                                    }
                                            
                                    public void broadcastSignalGloballyViaBuilder(String signalName, Map<String, Object> variableMap) {
                                        camundaClientWrapper.broadcastSignalWithoutTenantId(signalName, variableMap);
                                    }
                                            
                                    public void broadcastSignalToOneTenantViaBuilder(String signalName, String tenantId, Map<String, Object> variableMap) {
                                        camundaClientWrapper.broadcastSignalWithTenantId(signalName, tenantId, variableMap);
                                    }
                                    
                                    public void broadcastSignalToOneTenantViaBuilder2(String signalName, String tenantId, Map<String, Object> variableMap) {
                                        camundaClientWrapper.broadcastSignalWithTenantId(signalName, tenantId, variableMap);
                                    }
                                } 
                                """
                )
        );
    }

}
