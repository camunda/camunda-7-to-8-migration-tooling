package org.camunda.migration.rewrite.recipes.client;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.RemoveUnusedImports;
import org.openrewrite.staticanalysis.RemoveUnneededBlock;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceTypedValueAPISimpleTest implements RewriteTest {

    @Test
    void replaceTypedValueAPITest() {
        rewriteRun(
                spec -> spec.recipes(new RemoveUnneededBlock()),
                //language=java
                java(
                        """
                                package org.camunda.community.migration.example;
                                        
                                import org.camunda.bpm.engine.variable.Variables;
                                import org.camunda.bpm.engine.variable.value.StringValue;
                                import org.springframework.stereotype.Component;
                                               
                                import java.util.HashMap;
                                import java.util.Map;
                                                 
                                @Component
                                public class TypeValueTestClass {
                                    
                                    public void someMethod() {
                                        StringValue nameTyped = Variables.stringValue("2");
                                        String bla = "blub";{
                                         
                                        Map<String, Object> variableMap = new HashMap<>();
                                         
                                        variableMap.put("name", nameTyped);}
                                    }
                                }                                                                                                         
                                """,
                        """
                                package org.camunda.community.migration.example;
                                
                                import org.camunda.bpm.engine.variable.Variables;
                                import org.camunda.bpm.engine.variable.value.StringValue;
                                import org.springframework.stereotype.Component;
                                
                                import java.util.HashMap;
                                import java.util.Map;
                                
                                @Component
                                public class TypeValueTestClass {
                                    
                                    public void someMethod() {
                                        StringValue nameTyped = Variables.stringValue("2");
                                        String bla = "blub";
                                         
                                        Map<String, Object> variableMap = new HashMap<>();
                                         
                                        variableMap.put("name", nameTyped);
                                    }
                                } 
                                """
                )
        );
    }
}
