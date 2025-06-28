package org.camunda.migration.rewrite.recipes.delegate;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JavaDelegateSpringToZeebeWorkerSpringTest implements RewriteTest {

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
                System.out.println("SampleJavaDelegate " + job.getVariablesAsMap().get("x"));
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
        Integer amount = (Integer) job.getVariablesAsMap().get("AMOUNT");
    
        String response = rest.postForObject("endpoint", amount, String.class);

        resultMap.put("paymentTransactionId", response);
        return resultMap;
    }
}"""));
    }
}
