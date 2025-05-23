package org.camunda.migration.rewrite.recipes.client;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceMessagesMethodsTest implements RewriteTest {

    @Test
    void replaceMessageMethodsTest() {
        rewriteRun(
                spec -> spec.recipe(new ReplaceSignalMethodsRecipe()),
                //language=java
                java(
                        """
                                package org.camunda.community.migration.example;
                                        
                                import org.camunda.bpm.engine.ProcessEngine;
                                import org.camunda.migration.rewrite.recipes.glue.CamundaClientWrapper;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.stereotype.Component;
                                                                
                                import java.util.Map;
                                        
                                @Component
                                public class BroadcastSignalsTestClass {
                                        
                                    @Autowired
                                    private CamundaClientWrapper camundaClientWrapper;
                                    
                                    @Autowired
                                    private ProcessEngine engine;
                                                                                                 
                                    public void correlateMessageMethods(String messageName, String businessKey, String tenantId, VariableMap variableMap) {
                                        engine.getRuntimeService().correlateMessage(messageName);
                                        
                                        engine.getRuntimeService().correlateMessage(messageName, businessKey);
                                        
                                        engine.getRuntimeService().correlateMessage(messageName, variableMap);
                                        
                                        engine.getRuntimeService().correlateMessage(messageName, businessKey, variableMap);
                                
                                        engine.getRuntimeService().messageEventReceived(messageName, executionId);
                                        
                                        engine.getRuntimeService().messageEventReceived(messageName, executionId, variableMap);
                                        
                                        engine.getRuntimeService().createMessageCorrelation(messageName)
                                                        .correlate();
                                                        
                                        engine.getRuntimeService().createMessageCorrelation(messageName)
                                                        .processInstanceBusinessKey(businessKey)
                                                        .correlate();
                                                        
                                        engine.getRuntimeService().createMessageCorrelation(messageName)
                                                        .tenantId(tenantId)
                                                        .correlate();
                                                        
                                        engine.getRuntimeService().createMessageCorrelation(messageName)
                                                        .setVariables(variableMap)
                                                        .correlate();
                                                        
                                        engine.getRuntimeService().createMessageCorrelation(messageName)
                                                        .processInstanceBusinessKey(businessKey)
                                                        .tenantId(tenantId)
                                                        .correlate();
                                                        
                                        engine.getRuntimeService().createMessageCorrelation(messageName)
                                                        .processInstanceBusinessKey(businessKey)
                                                        .setVariables(variableMap)
                                                        .correlate();
                                                        
                                        engine.getRuntimeService().createMessageCorrelation(messageName)
                                                        .tenantId(tenantId)
                                                        .setVariables(variableMap)
                                                        .correlate();
                                                        
                                        engine.getRuntimeService().createMessageCorrelation(messageName)
                                                        .processInstanceBusinessKey(businessKey)
                                                        .tenantId(tenantId)
                                                        .setVariables(variableMap)
                                                        .correlate();
                                    }
                                }                                                                                                         
                                """,
                        """
                                package org.camunda.community.migration.example;
                                                                
                                import org.camunda.bpm.engine.ProcessEngine;
                                import org.camunda.migration.rewrite.recipes.glue.CamundaClientWrapper;
                                import org.springframework.beans.factory.annotation.Autowired;
                                import org.springframework.stereotype.Component;
                                                                
                                import java.util.Map;
                                        
                                @Component
                                public class BroadcastSignalsTestClass {
                                    
                                    @Autowired
                                    private CamundaClientWrapper camundaClientWrapper;
                                    
                                    @Autowired
                                    private ProcessEngine engine;
                                                                                                 
                                    public void correlateMessageMethods(String messageName, String businessKey, String tenantId, VariableMap variableMap) {
                                        camundaClientWrapper.correlateMessage(messageName, "please configure a correlationKey");
                                                                
                                        camundaClientWrapper.correlateMessage(messageName, "please configure a correlationKey");
                                                                
                                        camundaClientWrapper.correlateMessageWithVariables(messageName, "please configure a correlationKey", variableMap);
                                                                
                                        camundaClientWrapper.correlateMessageWithVariables(messageName, "please configure a correlationKey", variableMap);
                                                                      
                                        camundaClientWrapper.correlateMessage(messageName, "please configure a correlationKey");
                                                        
                                        camundaClientWrapper.correlateMessageWithVariables(messageName, "please configure a correlationKey", variableMap);
                                        
                                        camundaClientWrapper.correlateMessage(messageName, "please configure a correlationKey");
                                
                                        camundaClientWrapper.correlateMessage(messageName, "please configure a correlationKey");
                                
                                        camundaClientWrapper.correlateMessageWithTenantId(messageName, "please configure a correlationKey", tenantId);
                                        
                                        camundaClientWrapper.correlateMessageWithVariables(messageName, "please configure a correlationKey", variableMap);
                                
                                        camundaClientWrapper.correlateMessageWithTenantId(messageName, "please configure a correlationKey", tenantId);
                                
                                        camundaClientWrapper.correlateMessageWithTenantIdWithVariables(messageName, "please configure a correlationKey", tenantId, variableMap);
                                
                                        camundaClientWrapper.correlateMessageWithTenantIdWithVariables(messageName, "please configure a correlationKey", tenantId, variableMap);
                                    }
                                } 
                                """
                )
        );
    }

}
