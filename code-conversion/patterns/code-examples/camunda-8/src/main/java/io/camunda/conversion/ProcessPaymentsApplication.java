package io.camunda.conversion;

import io.camunda.spring.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Deployment(resources = "classpath*:/bpmn/**/*.bpmn")
public class ProcessPaymentsApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProcessPaymentsApplication.class, args);
	}

}
