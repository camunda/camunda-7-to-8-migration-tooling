package org.camunda.migration.rewrite.recipes;

import org.camunda.migration.rewrite.recipes.client.ProcessEngineToZeebeClient;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ProcessEngineToZeebeClientTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ProcessEngineToZeebeClient())
        	.parser(JavaParser.fromJavaVersion()
        			.classpath(JavaParser.runtimeClasspath()));
    }
    

    @Test
    void rewriteRuntimeServiceStartProcessInstanceByKey() {
        rewriteRun(
            java(
                """
package org.camunda.community.migration.example;
import java.util.Map;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SampleProcessStarter {
	
	@Autowired
	private ProcessEngine processEngine;
	
	public void startOneProcess() {
		Map<String, Object> variables = Map.of("x", 7);	
		ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceByKey("sample-process-solution-process", variables);		
		System.out.println("Started " + processInstance.getId() );
	}
}
                """,
                """
package org.camunda.community.migration.example;
import java.util.Map;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SampleProcessStarter {
	
	@Autowired
	private ZeebeClient processEngine;
	
	public void startOneProcess() {
		Map<String, Object> variables = Map.of("x", 7);	
		ProcessInstanceEvent processInstance = processEngine.newCreateInstanceCommand().bpmnProcessId("sample-process-solution-process").latestVersion().variables(variables).send().join(); 
		System.out.println("Started " + processInstance.getProcessInstanceKey() );
	}
}"""
            )
        );
    }    
    
}
