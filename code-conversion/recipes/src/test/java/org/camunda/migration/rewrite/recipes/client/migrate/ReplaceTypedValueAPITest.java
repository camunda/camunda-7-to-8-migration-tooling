package org.camunda.migration.rewrite.recipes.client.migrate;

import org.camunda.migration.rewrite.recipes.client.prepare.ReplaceTypedValueAPIRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceTypedValueAPITest implements RewriteTest {

    @Test
    void replaceTypedValueAPITest() {
        rewriteRun(
                spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
                //language=java
                java(
                        """
                                package org.camunda.community.migration.example;
                                        
                                import org.camunda.bpm.engine.variable.Variables;
                                import org.camunda.bpm.engine.variable.VariableMap;
                                import org.camunda.bpm.engine.variable.value.DoubleValue;
                                import org.camunda.bpm.engine.variable.value.IntegerValue;
                                import org.camunda.bpm.engine.variable.value.StringValue;
                                import org.camunda.bpm.engine.variable.value.ObjectValue;
                                import org.springframework.stereotype.Component;
                                                                
                                import java.util.Collections;
                                                                
                                @Component
                                public class TypeValueTestClass {
                                    
                                    private class CustomObject {
                                        private String someString;
                                
                                        private Long someLong;
                                
                                        public CustomObject(String someString, Long someLong) {
                                            this.someString = someString;
                                            this.someLong = someLong;
                                        }
                                    }
                                        
                                    public void someMethod(CustomObject customObject, DoubleValue doubleTyped) {
                                        IntegerValue amountTyped = Variables.integerValue(2);
                                        StringValue nameTyped = Variables.stringValue("2");
                                        String bla = "blub";
                                        
                                        VariableMap map1 = Variables.createVariables().putValueTyped("name", nameTyped).putValueTyped("amount", amountTyped);
                                        map1.putValue("bla", bla);
                                        map1.putValueTyped("double", doubleTyped);
                                        
                                        VariableMap map2 = Variables.fromMap(Collections.singletonMap("amount", amountTyped));
                                        
                                        ObjectValue objectValue = Variables.objectValue(customObject).create();
                                    }
                                }                                                                                                         
                                """,
                        """
                                package org.camunda.community.migration.example;
                                                                
                                import org.camunda.bpm.engine.variable.VariableMap;
                                import org.camunda.bpm.engine.variable.value.DoubleValue;
                                import org.camunda.bpm.engine.variable.value.IntegerValue;
                                import org.camunda.bpm.engine.variable.value.StringValue;
                                import org.camunda.bpm.engine.variable.value.ObjectValue;
                                import org.springframework.stereotype.Component;
                                                                
                                import java.util.Collections;
                                import java.util.HashMap;
                                import java.util.Map;
                                                                
                                @Component
                                public class TypeValueTestClass {
                                    
                                    private class CustomObject {
                                        private String someString;
                                
                                        private Long someLong;
                                
                                        public CustomObject(String someString, Long someLong) {
                                            this.someString = someString;
                                            this.someLong = someLong;
                                        }
                                    }
                                    
                                    public void someMethod(CustomObject customObject, double doubleTyped) {
                                        int amountTyped = 2;
                                        String nameTyped = "2";
                                        String bla = "blub";
                                        
                                        Map<String, Object> map1 = new HashMap<>();
                                        map1.put("amount", amountTyped);
                                        map1.put("name", nameTyped);
                                        map1.put("bla", bla);
                                        map1.put("double", doubleTyped);
                                        
                                        Map<String, Object> map2 = Collections.singletonMap("amount", amountTyped);
                                        
                                        TypeValueTestClass.CustomObject objectValue = customObject;
                                    }
                                } 
                                """
                )
        );
    }
}
