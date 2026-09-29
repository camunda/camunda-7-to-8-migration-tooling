/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.prepare;

import static org.openrewrite.java.Assertions.java;

import io.camunda.migration.code.recipes.sharedRecipes.ReplaceTypedValueAPIRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

class ReplaceTypedValueAPITest implements RewriteTest {

  @Test
  void replaceTypedValueAPITest() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        // language=java
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
        nameTyped = Variables.stringValue("3");
        String bla = "blub";

        double someDouble = doubleTyped.getValue();
        someDouble = doubleTyped.getValue();

        VariableMap map1 = Variables.createVariables().putValueTyped("name", nameTyped).putValueTyped("amount", amountTyped);
        map1.putValue("bla", bla);
        map1.putValueTyped("double", doubleTyped);

        Variables.createVariables().putValue("blub", "blub").putValueTyped("name", nameTyped);

        VariableMap map2 = Variables.fromMap(Collections.singletonMap("amount", amountTyped));

        ObjectValue objectValue = Variables.objectValue(customObject).create();
    }
}
""",
"""
package org.camunda.community.migration.example;

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

    public void someMethod(CustomObject customObject, Double doubleTyped) {
        Integer amountTyped = 2;
        String nameTyped = "2";
        nameTyped = "3";
        String bla = "blub";

        double someDouble = doubleTyped;
        someDouble = doubleTyped;
        Map<String, Object> map1 = new HashMap<>();
        map1.put("amount", amountTyped);
        map1.put("name", nameTyped);
        map1.put("bla", bla);
        map1.put("double", doubleTyped);

        Map.ofEntries(Map.entry("blub", "blub"), Map.entry("name", nameTyped));

        Map<String, Object> map2 = Collections.singletonMap("amount", amountTyped);

        // type set to java.lang.Object
        Object objectValue = customObject;
    }
}
"""));
  }

  @Test
  void replacesJsonObjectValueBuilderWithPojo() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        // language=java
        java(
            """
                package org.camunda.community.migration.example;

                import java.util.Map;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class SpinPayloadTest {
                    static class SerializationDataFormats {
                        static final String JSON = "application/xml";
                        static final String JSON_XML = "application/json_xml";
                    }

                    record Payload(Map<String, Object> variables) {}

                    void submit(Payload payload) {
                        ObjectValue serialized = Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON)
                            .create();
                        ObjectValue literalSerialized = Variables.objectValue(payload)
                            .serializationDataFormat("application/json")
                            .create();
                        ObjectValue customSerialized = Variables.objectValue(payload)
                            .serializationDataFormat(SerializationDataFormats.JSON)
                            .create();
                        ObjectValue jsonXmlSerialized = Variables.objectValue(payload)
                            .serializationDataFormat(SerializationDataFormats.JSON_XML)
                            .create();
                        consume(Variables.objectValue(payload)
                            .serializationDataFormat(SerializationDataFormats.JSON_XML)
                            .create());
                    }

                    void consume(Object value) {
                    }
                }
                """,
            """
                package org.camunda.community.migration.example;

                import java.util.Map;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class SpinPayloadTest {
                    static class SerializationDataFormats {
                        static final String JSON = "application/xml";
                        static final String JSON_XML = "application/json_xml";
                    }

                    record Payload(Map<String, Object> variables) {}

                    void submit(Payload payload) {
                        // type set to java.lang.Object
                        Object serialized = payload;
                        // type set to java.lang.Object
                        Object literalSerialized = payload;
                        ObjectValue customSerialized = Variables.objectValue(payload)
                            .serializationDataFormat(SerializationDataFormats.JSON)
                            .create();
                        ObjectValue jsonXmlSerialized = Variables.objectValue(payload)
                            .serializationDataFormat(SerializationDataFormats.JSON_XML)
                            .create();
                        consume(Variables.objectValue(payload)
                            .serializationDataFormat(SerializationDataFormats.JSON_XML)
                            .create());
                    }

                    void consume(Object value) {
                    }
                }
                """));
  }

  @Test
  void preservesUnsupportedObjectValueFormatAssignments() {
    rewriteRun(
        spec ->
            spec.recipe(new ReplaceTypedValueAPIRecipe()).expectedCyclesThatMakeChanges(0),
        // language=java
        java(
            """
                package org.camunda.community.migration.example;

                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class SpinPayloadTest {
                    record Payload(String value) {}

                    void submit(Payload payload) {
                        ObjectValue serialized;
                        serialized = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml")
                            .create();
                        Object value = serialized.getValue();

                        ObjectValue initialized = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml")
                            .create();
                        Object initializedValue = initialized.getValue();

                        ObjectValue parenthesizedInitialized = (Variables.objectValue(payload)
                            .serializationDataFormat("application/xml")
                            .create());
                        Object parenthesizedInitializedValue = parenthesizedInitialized.getValue();

                        ObjectValue parenthesized;
                        parenthesized = (Variables.objectValue(payload)
                            .serializationDataFormat("application/xml")
                            .create());
                        Object parenthesizedValue = parenthesized.getValue();
                        consume(value);
                        consume(initializedValue);
                        consume(parenthesizedInitializedValue);
                        consume(parenthesizedValue);
                    }

                    void consume(Object value) {
                    }
                }
                """));
  }

  @Test
  void preservesUnsupportedObjectValueAssignmentsOnlyForMatchingVariable() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        // language=java
        java(
            """
                package org.camunda.community.migration.example;

                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class SpinPayloadTest {
                    record Payload(String value) {}

                    ObjectValue fieldValue;

                    void keepUnsupportedFormat(Payload payload) {
                        ObjectValue serialized;
                        serialized = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml")
                            .create();
                        Object value = serialized.getValue();
                        consume(value);
                    }

                    void keepUnsupportedQualifiedField(Payload payload) {
                        this.fieldValue = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml")
                            .create();
                        Object value = fieldValue.getValue();
                        consume(value);
                    }

                    void migrateSameNameInAnotherMethod(Payload payload) {
                        ObjectValue serialized;
                        serialized = Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON)
                            .create();
                        Object value = serialized.getValue();
                        consume(value);
                    }

                    void migrateSameNameInSiblingScopes(Payload payload) {
                        if (payload != null) {
                            ObjectValue sibling;
                            sibling = Variables.objectValue(payload)
                                .serializationDataFormat("application/xml")
                                .create();
                            Object value = sibling.getValue();
                            consume(value);
                        }
                        if (payload != null) {
                            ObjectValue sibling;
                            sibling = Variables.objectValue(payload)
                                .serializationDataFormat(Variables.SerializationDataFormats.JSON)
                                .create();
                            Object value = sibling.getValue();
                            consume(value);
                        }
                    }

                    void consume(Object value) {
                    }
                }
                """,
            """
                package org.camunda.community.migration.example;

                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class SpinPayloadTest {
                    record Payload(String value) {}

                    ObjectValue fieldValue;

                    void keepUnsupportedFormat(Payload payload) {
                        ObjectValue serialized;
                        serialized = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml")
                            .create();
                        Object value = serialized.getValue();
                        consume(value);
                    }

                    void keepUnsupportedQualifiedField(Payload payload) {
                        this.fieldValue = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml")
                            .create();
                        Object value = fieldValue.getValue();
                        consume(value);
                    }

                    void migrateSameNameInAnotherMethod(Payload payload) {
                        Object serialized;
                        // type set to java.lang.Object
                        serialized = payload;
                        Object value = serialized;
                        consume(value);
                    }

                    void migrateSameNameInSiblingScopes(Payload payload) {
                        if (payload != null) {
                            ObjectValue sibling;
                            sibling = Variables.objectValue(payload)
                                .serializationDataFormat("application/xml")
                                .create();
                            Object value = sibling.getValue();
                            consume(value);
                        }
                        if (payload != null) {
                            Object sibling;
                            // type set to java.lang.Object
                            sibling = payload;
                            Object value = sibling;
                            consume(value);
                        }
                    }

                    void consume(Object value) {
                    }
                }
                """));
  }

  @Test
  void preservesUnsupportedObjectValueOnlyForMatchingForLoopVariable() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        // language=java
        java(
            """
                package org.camunda.community.migration.example;

                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class SpinPayloadTest {
                    record Payload(String value) {}

                    void submit(Payload payload) {
                        for (ObjectValue serialized = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml")
                            .create(); serialized != null; ) {
                            consume(serialized.getValue());
                            break;
                        }
                        for (ObjectValue serialized = Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON)
                            .create(); serialized != null; ) {
                            consume(serialized.getValue());
                            break;
                        }
                    }

                    void consume(Object value) {
                    }
                }
                """,
            """
                package org.camunda.community.migration.example;

                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class SpinPayloadTest {
                    record Payload(String value) {}

                    void submit(Payload payload) {
                        for (ObjectValue serialized = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml")
                            .create(); serialized != null; ) {
                            consume(serialized.getValue());
                            break;
                        }
                        for (// type set to java.lang.Object
                                Object serialized = payload; serialized != null; ) {
                            consume(serialized);
                            break;
                        }
                    }

                    void consume(Object value) {
                    }
                }
                """));
  }

  @Test
  void replacesJsonObjectValueBuilderAssignmentWithPojo() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        // language=java
        java(
            """
                package org.camunda.community.migration.example;

                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class SpinPayloadTest {
                    record Payload(String value) {}

                    void submit(Payload payload) {
                        ObjectValue serialized;
                        serialized = Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON)
                            .create();
                        Object value = serialized.getValue();
                        consume(value);
                    }

                    void consume(Object value) {
                    }
                }
                """,
            """
                package org.camunda.community.migration.example;

                class SpinPayloadTest {
                    record Payload(String value) {}

                    void submit(Payload payload) {
                        Object serialized;
                        // type set to java.lang.Object
                        serialized = payload;
                        Object value = serialized;
                        consume(value);
                    }

                    void consume(Object value) {
                    }
                }
                """));
  }

  @Test
  void replacesParenthesizedJsonObjectValueBuilderAssignmentWithPojo() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        // language=java
        java(
            """
                package org.camunda.community.migration.example;

                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class SpinPayloadTest {
                    record Payload(String value) {}

                    void submit(Payload payload) {
                        ObjectValue serialized;
                        serialized = (Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON)
                            .create());
                        Object value = serialized.getValue();
                        consume(value);
                    }

                    void consume(Object value) {
                    }
                }
                """,
            """
                package org.camunda.community.migration.example;

                class SpinPayloadTest {
                    record Payload(String value) {}

                    void submit(Payload payload) {
                        Object serialized;
                        // type set to java.lang.Object
                        serialized = (payload);
                        Object value = serialized;
                        consume(value);
                    }

                    void consume(Object value) {
                    }
                }
                """));
  }

  @Test
  void preservesObjectValueForQualifiedFieldAssignments() {
    rewriteRun(
        spec ->
            spec.recipe(new ReplaceTypedValueAPIRecipe()).expectedCyclesThatMakeChanges(0),
        // language=java
        java(
            """
                package org.camunda.community.migration.example;

                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class SpinPayloadTest {
                    record Payload(String value) {}

                    ObjectValue fieldValue;

                    void submit(Payload payload) {
                        this.fieldValue = Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON)
                            .create();
                        consume(this.fieldValue.getValue());
                    }

                    void consume(Object value) {
                    }
                }
                """));
  }
}
