package org.camunda.community.migration.example;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class SampleProcessStarter {
	
	@Autowired
	private ProcessEngine processEngine;
	
	@PostConstruct
	public void startOneProcess() {
		System.out.println( "Demo usage of Camunda API during startup, typically wired into your own code somewhere..." );
		
		VariableMap variables = Variables.createVariables().putValue("x", 7);
		
		ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceByKey("sample-process-solution-process", variables);
		
		System.out.println( "Started " + processInstance.getId() );
	}

}
