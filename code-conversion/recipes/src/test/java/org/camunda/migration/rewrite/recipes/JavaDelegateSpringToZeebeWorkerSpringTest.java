package org.camunda.migration.rewrite.recipes;

import org.camunda.migration.rewrite.recipes.glue.JavaDelegateSpringToZeebeWorkerSpring;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JavaDelegateSpringToZeebeWorkerSpringTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JavaDelegateSpringToZeebeWorkerSpring())
        	.parser(JavaParser.fromJavaVersion()
    			.classpath(JavaParser.runtimeClasspath()));
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

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobWorker;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RetrievePaymentAdapter {
    @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
    public Map<String,Object> execute(ActivatedJob ctx) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();
        return resultMap;
  }
}"""                
            )
        );
    }    
    
    @Test
    void rewriteExecurteMethodWithVariables() {
        // The import for the DelegateExecution should disappear ... yet I failed to achive this so far
        rewriteRun(
            java(
                """
package org.camunda.community.migration.example;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
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

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobWorker;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class RetrievePaymentAdapter {

  @Autowired
  private RestTemplate rest;

    @JobWorker(type = "retrievePaymentAdapter", autoComplete = true)
    public Map<String,Object> execute(ActivatedJob ctx) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();
    Integer amount = (Integer) ctx.getVariablesAsMap().getVariable("AMOUNT");

    String response = rest.postForObject("endpoint", amount, String.class);

        resultMap.put("paymentTransactionId", response);
        return resultMap;
  }

}"""                
            )
        );
    }
}
