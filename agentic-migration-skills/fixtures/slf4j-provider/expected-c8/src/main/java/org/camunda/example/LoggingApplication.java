package org.camunda.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LoggingApplication {

  private static final Logger LOG = LoggerFactory.getLogger(LoggingApplication.class);

  public static void main(String[] args) {
    System.out.println(
        "SLF4J factory: " + LoggerFactory.getILoggerFactory().getClass().getName());
    SpringApplication.run(LoggingApplication.class, args);
    LOG.info("Application started");
  }
}
