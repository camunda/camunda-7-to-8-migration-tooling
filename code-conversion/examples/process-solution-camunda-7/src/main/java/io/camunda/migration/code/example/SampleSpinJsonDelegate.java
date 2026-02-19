/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.example;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.spring.client.annotation.JobWorker;
import org.camunda.spin.json.SpinJsonNode;

import java.util.HashMap;
import java.util.Map;

import static org.camunda.spin.Spin.JSON;

public class SampleSpinJsonDelegate {

    public record Foo(String foo1, int foo2, boolean foo3, double foo4, Bar foo5) { }

    public record Bar(String bar1, int bar2, boolean bar3, double bar4) { }

    @JobWorker(type = "sampleSpinJsonDelegate", autoComplete = true)
    public Map<String, Object> executeJobMigrated(ActivatedJob job) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();
        // the following 2 lines should be transformed to
        // final Bar bar = (Bar.class) execution.getVariable("jsonVar");
        final SpinJsonNode jsonVarInput = (SpinJsonNode) job.getVariable("jsonVar");
        final Bar bar = jsonVarInput.mapTo(Bar.class);
        
        Foo foo = new Foo(bar.bar1(), bar.bar2(), bar.bar3(), bar.bar4(), bar);
        
        // the following two lines should be transformed to
        // execution.setVariable("jsonVar", foo);
        final SpinJsonNode jsonVarOutput = JSON(foo);
        resultMap.put("jsonVar", jsonVarOutput);
        return resultMap;
    }
}
