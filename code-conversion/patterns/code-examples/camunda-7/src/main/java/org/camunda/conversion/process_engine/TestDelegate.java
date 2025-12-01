/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.conversion.process_engine;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.variable.value.StringValue;
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
        final StringValue stringVariableTyped = execution.getVariableTyped("stringVariable");
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
        execution.setVariable("newJsonVariable", JSON("{\"key1\" : \"value1\"}"));
        execution.setVariableLocal("newStringVariableLocal", "Wieso");
        execution.setVariableLocal("newIntegerVariableLocal", 4711);
        execution.setVariableLocal("newDoubleVariableLocal", 6.626);
        execution.setVariableLocal("newBoolVariableLocal", false);
        execution.setVariableLocal("newJsonVariableLocal", JSON("{\"key2\" : \"value2\"}"));

        execution.setVariable("newObjectVariable",
                              new DummyClass(215, 9.81, "Ein Beispielstring zum testen"));
        System.out.println("C7 finished");
    }
    record DummyClass(Integer zahl, Double nochneZahl, String einString){}
}