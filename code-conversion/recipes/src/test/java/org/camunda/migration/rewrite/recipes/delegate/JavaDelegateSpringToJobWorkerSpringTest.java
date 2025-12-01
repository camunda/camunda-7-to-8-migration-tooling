package org.camunda.migration.rewrite.recipes.delegate;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JavaDelegateSpringToJobWorkerSpringTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
    spec.recipeFromResources("org.camunda.migration.rewrite.recipes.AllDelegateRecipes")
        .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }
    

    @Test
    void rewriteEmptyExecuteMethod() {
        rewriteRun(
            java(
                """
package org.camunda.community.migration.example;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
public class RetrievePaymentAdapter implements JavaDelegate {

  @Override
  public void execute(DelegateExecution ctx) throws Exception {    
  }
  
}
""",
"""
package org.camunda.community.migration.example;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.spring.client.annotation.JobWorker;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RetrievePaymentAdapter {

    @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
    public Map<String, Object> executeJobMigrated(ActivatedJob job) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();
        return resultMap;        
    }
  
}"""                
            )
        );
    }


    @Test
    void logTest() {
    rewriteRun(
        java(
            """
        package org.camunda.community.migration.example;

        import org.camunda.bpm.engine.delegate.DelegateExecution;
        import org.camunda.bpm.engine.delegate.JavaDelegate;
        import org.springframework.stereotype.Component;

        @Component
        public class RetrievePaymentAdapter implements JavaDelegate {

            @Override
            public void execute(DelegateExecution execution) throws Exception {
                System.out.println("SampleJavaDelegate " + execution.getVariable("x"));
                execution.setVariable("y", "hello world");
            }
        }
        """,
            """
        package org.camunda.community.migration.example;

        import io.camunda.client.api.response.ActivatedJob;
        import io.camunda.spring.client.annotation.JobWorker;
        import org.springframework.stereotype.Component;

        import java.util.HashMap;
        import java.util.Map;

        @Component
        public class RetrievePaymentAdapter {

            @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
            public Map<String, Object> executeJobMigrated(ActivatedJob job) throws Exception {
                Map<String, Object> resultMap = new HashMap<>();
                System.out.println("SampleJavaDelegate " + job.getVariable("x"));
                resultMap.put("y", "hello world");
                return resultMap;
            }
        }"""));
    }

    @Test
    void rewriteExecuteMethodWithVariables() {
    rewriteRun(
        java(
"""
package org.camunda.community.migration.example;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class RetrievePaymentAdapter implements JavaDelegate {

    @Autowired
    private RestTemplate rest;

    @Override
    public void execute(DelegateExecution ctx) throws Exception {
        Integer amount = (Integer) ctx.getVariable("AMOUNT");

        String response = rest.postForObject("endpoint", amount, String.class);

        ctx.setVariable("paymentTransactionId", response);
    }
}
                """,
"""
package org.camunda.community.migration.example;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.spring.client.annotation.JobWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class RetrievePaymentAdapter {

    @Autowired
    private RestTemplate rest;

    @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
    public Map<String, Object> executeJobMigrated(ActivatedJob job) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();
        Integer amount = (Integer) job.getVariable("AMOUNT");
    
        String response = rest.postForObject("endpoint", amount, String.class);

        resultMap.put("paymentTransactionId", response);
        return resultMap;
    }
}"""));
    }

    @Test
    void executionAccessTest() {
    rewriteRun(
        java(
"""
package org.camunda.community.migration.example;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.springframework.stereotype.Component;

import static org.camunda.spin.Spin.JSON;

@Component("TestDelegate")
public class TestDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {

        System.out.println("C7 delegate called");
        final var stringVariable = execution.getVariable("stringVariable");
        final var integerVariable = execution.getVariable("integerVariable");
        final var doubleVariable = execution.getVariable("doubleVariable");
        final var boolVariable = execution.getVariable("boolVariable");
        final var jsonVariable = execution.getVariable("jsonVariable");
        final var fileVariable = execution.getVariable("fileVariable");
        final TypedValue stringVariableTyped = execution.getVariableTyped("stringVariable");
        final TypedValue integerVariableTyped = execution.getVariableTyped("integerVariable");
        final TypedValue doubleVariableTyped = execution.getVariableTyped("doubleVariable");
        final TypedValue boolVariableTyped = execution.getVariableTyped("boolVariable");
        final TypedValue jsonVariableTyped = execution.getVariableTyped("jsonVariable");
        final TypedValue fileVariableTyped = execution.getVariableTyped("fileVariable");
        final var stringVariableLocal = execution.getVariableLocal("stringVariableLocal");
        final var integerVariableLocal = execution.getVariableLocal("integerVariableLocal");
        final var doubleVariableLocal = execution.getVariableLocal("doubleVariableLocal");
        final var boolVariableLocal = execution.getVariableLocal("boolVariableLocal");
        final var jsonVariableLocal = execution.getVariableLocal("jsonVariableLocal");

        final String procInstanceId = execution.getProcessInstanceId();
        final String procDefId = execution.getProcessDefinitionId();
        final String curActId = execution.getCurrentActivityId();
        final String actInstanceId = execution.getActivityInstanceId();

        execution.setVariable("newStringVariable", "Warum");
        execution.setVariable("newIntegerVariable", 42);
        execution.setVariable("newDoubleVariable", 2.71828);
        execution.setVariable("newBoolVariable", true);
        execution.setVariable("newJsonVariable", JSON("{\\"key1\\" : \\"value1\\"}"));
        execution.setVariableLocal("newStringVariableLocal", "Wieso");
        execution.setVariableLocal("newIntegerVariableLocal", 4711);
        execution.setVariableLocal("newDoubleVariableLocal", 6.626);
        execution.setVariableLocal("newBoolVariableLocal", false);
        execution.setVariableLocal("newJsonVariableLocal", JSON("{\\"key2\\" : \\"value2\\"}"));

        execution.setVariable("newObjectVariable",
                new DummyClass(215, 9.81, "Ein Beispielstring zum testen"));
        System.out.println("C7 finished");
    }
    record DummyClass(Integer zahl, Double nochneZahl, String einString){}
}
                """,
"""
package org.camunda.community.migration.example;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.spring.client.annotation.JobWorker;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static org.camunda.spin.Spin.JSON;

@Component("TestDelegate")
public class TestDelegate {
    record DummyClass(Integer zahl, Double nochneZahl, String einString){}

    @JobWorker(type = "testDelegate", autoComplete = true)
    public Map<String, Object> executeJobMigrated(ActivatedJob job) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();

        System.out.println("C7 delegate called");
        final var stringVariable = job.getVariable("stringVariable");
        final var integerVariable = job.getVariable("integerVariable");
        final var doubleVariable = job.getVariable("doubleVariable");
        final var boolVariable = job.getVariable("boolVariable");
        final var jsonVariable = job.getVariable("jsonVariable");
        final var fileVariable = job.getVariable("fileVariable");
        // please check type
        final Object stringVariableTyped = job.getVariable("stringVariable");
        // please check type
        final Object integerVariableTyped = job.getVariable("integerVariable");
        // please check type
        final Object doubleVariableTyped = job.getVariable("doubleVariable");
        // please check type
        final Object boolVariableTyped = job.getVariable("boolVariable");
        // please check type
        final Object jsonVariableTyped = job.getVariable("jsonVariable");
        // please check type
        final Object fileVariableTyped = job.getVariable("fileVariable");
        final var stringVariableLocal = job.getVariable("stringVariableLocal");
        final var integerVariableLocal = job.getVariable("integerVariableLocal");
        final var doubleVariableLocal = job.getVariable("doubleVariableLocal");
        final var boolVariableLocal = job.getVariable("boolVariableLocal");
        final var jsonVariableLocal = job.getVariable("jsonVariableLocal");

        final String procInstanceId = String.valueOf(job.getProcessInstanceKey());
        final String procDefId = String.valueOf(job.getProcessDefinitionKey());
        final String curActId = job.getElementId();
        final String actInstanceId = String.valueOf(job.getElementInstanceKey());

        resultMap.put("newStringVariable", "Warum");
        resultMap.put("newIntegerVariable", 42);
        resultMap.put("newDoubleVariable", 2.71828);
        resultMap.put("newBoolVariable", true);
        resultMap.put("newJsonVariable", JSON("{\\"key1\\" : \\"value1\\"}"));
        resultMap.put("newStringVariableLocal", "Wieso");
        resultMap.put("newIntegerVariableLocal", 4711);
        resultMap.put("newDoubleVariableLocal", 6.626);
        resultMap.put("newBoolVariableLocal", false);
        resultMap.put("newJsonVariableLocal", JSON("{\\"key2\\" : \\"value2\\"}"));

        resultMap.put("newObjectVariable", new DummyClass(215, 9.81, "Ein Beispielstring zum testen"));
        System.out.println("C7 finished");
        return resultMap;
    }
}
"""));
    }
}