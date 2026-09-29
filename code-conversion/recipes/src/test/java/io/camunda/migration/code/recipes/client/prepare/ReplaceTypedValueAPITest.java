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
  void replacesKnownJsonBuildersButLeavesOtherFormats() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        java(
            """
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    ObjectValue json;

                    static class Formats {
                        static final String JSON = "application/xml";
                    }

                    void submit(Object payload) {
                        ObjectValue json = Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();
                        ObjectValue literal = Variables.objectValue(payload)
                            .serializationDataFormat("application/json").create();
                        ObjectValue unknown = Variables.objectValue(payload)
                            .serializationDataFormat(Formats.JSON).create();
                        this.json = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml").create();
                        consume(Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create());
                    }

                    void consume(Object value) {}
                }
                """,
            """
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    ObjectValue json;

                    static class Formats {
                        static final String JSON = "application/xml";
                    }

                    void submit(Object payload) {
                        // type set to java.lang.Object
                        Object json = payload;
                        // type set to java.lang.Object
                        Object literal = payload;
                        ObjectValue unknown = Variables.objectValue(payload)
                            .serializationDataFormat(Formats.JSON).create();
                        this.json = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml").create();
                        consume(// type set to java.lang.Object
                                payload);
                    }

                    void consume(Object value) {}
                }
                """));
  }

  @Test
  void replacesParenthesizedJsonBuilderInitializersWithoutDroppingPayload() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        java(
            """
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    void submit(Object payload) {
                        ObjectValue constant = ((Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON)
                            .create()));
                        ObjectValue literal = (Variables.objectValue(payload)
                            .serializationDataFormat("application/json")
                            .create());
                        consume(constant.getValue());
                        consume(literal.getValue());
                    }

                    void consume(Object value) {}
                }
                """,
            """
                class PayloadTest {
                    void submit(Object payload) {
                        // type set to java.lang.Object
                        Object constant = payload;
                        // type set to java.lang.Object
                        Object literal = payload;
                        consume(constant);
                        consume(literal);
                    }

                    void consume(Object value) {}
                }
                """));
  }

  @Test
  void leavesUnknownFormatAndLaterAssignmentsForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()).expectedCyclesThatMakeChanges(0),
        java(
            """
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    ObjectValue field;

                    void submit(Object payload) {
                        ObjectValue unknown = (Variables.objectValue(payload)
                            .serializationDataFormat("application/xml").create());
                        ObjectValue assignedLater;
                        assignedLater = Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();
                        ObjectValue xmlAssignedLater;
                        xmlAssignedLater = (Variables.objectValue(payload)
                            .serializationDataFormat("application/xml").create());
                        ObjectValue initiallyNull = null;
                        initiallyNull = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml").create();
                        ObjectValue reassigned = Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();
                        if (payload != null) {
                            (reassigned) = Variables.objectValue(payload)
                                .serializationDataFormat("application/xml").create();
                        }
                        this.field = Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();
                        consume(unknown.getValue());
                        consume(assignedLater.getValue());
                        consume(xmlAssignedLater.getValue());
                        consume(initiallyNull.getValue());
                        consume(reassigned.getValue());
                        consume(field.getValue());
                    }

                    void consume(Object value) {}
                }
                """));
  }

  @Test
  void leavesMultipleDeclarationsTogetherForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()).expectedCyclesThatMakeChanges(0),
        java(
            """
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    void submit(Object payload) {
                        ObjectValue json = Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create(),
                            xml = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml").create();
                        consume(json.getValue());
                        consume(xml.getValue());
                    }

                    void consume(Object value) {}
                }
                """));
  }

  @Test
  void leavesObjectValueFieldsForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()).expectedCyclesThatMakeChanges(0),
        java(
            """
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    ObjectValue field = Variables.objectValue(new Object())
                        .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();

                    Object read() {
                        return this.field.getValue();
                    }
                }
                """));
  }

  @Test
  void stillMigratesObjectValueParameters() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        java(
            """
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;
                import org.camunda.bpm.engine.variable.value.StringValue;

                class PayloadTest {
                    StringValue value;

                    void handle(ObjectValue value) {
                        consume(value.getValue());
                    }

                    void handleReassigned(ObjectValue value, Object payload) {
                        value = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml").create();
                        consume(value.getValue());
                    }

                    void handleLocal(Object payload) {
                        ObjectValue value = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml").create();
                        consume(value.getValue());
                    }

                    void consume(Object value) {}
                }
                """,
            """
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    String value;

                    void handle(Object value) {
                        consume(value);
                    }

                    void handleReassigned(ObjectValue value, Object payload) {
                        value = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml").create();
                        consume(value.getValue());
                    }

                    void handleLocal(Object payload) {
                        ObjectValue value = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml").create();
                        consume(value.getValue());
                    }

                    void consume(Object value) {}
                }
                """));
  }

  @Test
  void keepsJsonBuildersInTypedValueContexts() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()).expectedCyclesThatMakeChanges(0),
        java(
            """
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;
                import java.util.concurrent.atomic.AtomicReference;
                import java.util.function.Supplier;

                class PayloadTest {
                    ObjectValue build(Object payload) {
                        return Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();
                    }

                    ObjectValue[] buildArray(Object payload) {
                        return new ObjectValue[] { Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create() };
                    }

                    ObjectValue cast(Object payload) {
                        return (ObjectValue) Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();
                    }

                    ObjectValue conditional(boolean flag, Object payload) {
                        return flag ? (Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create()) : null;
                    }

                    ObjectValue choose(int choice, Object payload) {
                        return switch (choice) {
                            case 1 -> Variables.objectValue(payload)
                                .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();
                            default -> null;
                        };
                    }

                    void store(AtomicReference<ObjectValue> target, Object payload) {
                        target.set(Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create());
                    }

                    AtomicReference<ObjectValue> constructed(Object payload) {
                        return new AtomicReference<>(Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create());
                    }

                    Supplier<ObjectValue> supplier(Object payload) {
                        return () -> Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();
                    }
                }
                """));
  }

  @Test
  void preservesFieldBuildersAndRewritesTypedGetterAssignments() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        java(
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.IntegerValue;
                import org.camunda.bpm.engine.variable.value.ObjectValue;
                import org.camunda.bpm.engine.variable.value.StringValue;

                class PayloadTest {
                    IntegerValue amount;
                    StringValue label;
                    ObjectValue qualified;
                    ObjectValue plain;
                    ObjectValue deferredBuilder;
                    ObjectValue json = Variables.objectValue("before")
                        .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();

                    void submit(DelegateExecution execution, Object payload) {
                        this.amount = execution.getVariableTyped("amount");
                        this.label = execution.getVariableTyped("label");
                        this.qualified = execution.getVariableTyped("qualified");
                        plain = execution.getVariableTyped("plain");
                        this.deferredBuilder = Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();
                        this.json = Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();
                    }
                }
                """,
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    Integer amount;
                    String label;
                    Object qualified;
                    Object plain;
                    ObjectValue deferredBuilder;
                    ObjectValue json = Variables.objectValue("before")
                        .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();

                    void submit(DelegateExecution execution, Object payload) {
                        this.amount = (Integer) execution.getVariable("amount");
                        this.label = (String) execution.getVariable("label");
                        this.qualified = execution.getVariable("qualified");
                        plain = execution.getVariable("plain");
                        this.deferredBuilder = Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();
                        this.json = Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();
                    }
                }
                """));
  }

  @Test
  void convertsGetterBackedFieldsDeclaredAfterTheirMethods() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        java(
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    void update(DelegateExecution execution, Object payload) {
                        getter = execution.getVariableTyped("getter");
                        this.qualified = execution.getVariableTyped("qualified");
                        builder = Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();
                    }

                    Object read() {
                        return getter.getValue();
                    }

                    Object readQualified() {
                        return this.qualified.getValue();
                    }

                    Object readBuilder() {
                        return builder.getValue();
                    }

                    ObjectValue getter;
                    ObjectValue qualified;
                    ObjectValue builder;
                }
                """,
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    void update(DelegateExecution execution, Object payload) {
                        getter = execution.getVariable("getter");
                        this.qualified = execution.getVariable("qualified");
                        builder = Variables.objectValue(payload)
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();
                    }

                    Object read() {
                        return getter;
                    }

                    Object readQualified() {
                        return this.qualified;
                    }

                    Object readBuilder() {
                        return builder.getValue();
                    }

                    Object getter;
                    Object qualified;
                    ObjectValue builder;
                }
                """));
  }

  @Test
  void distinguishesGetterBackedFieldsFromShadowingBuilderLocals() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        java(
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    void update(DelegateExecution execution, Object payload) {
                        ObjectValue getter = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml").create();
                        this.getter = execution.getVariableTyped("getter");
                        consume(getter.getValue());
                        consume(this.getter.getValue());
                    }

                    void updateUnqualified(DelegateExecution execution) {
                        getter = execution.getVariableTyped("getter");
                        consume(getter.getValue());
                    }

                    void consume(Object value) {}

                    ObjectValue getter;
                }
                """,
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    void update(DelegateExecution execution, Object payload) {
                        ObjectValue getter = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml").create();
                        this.getter = execution.getVariable("getter");
                        consume(getter.getValue());
                        consume(this.getter);
                    }

                    void updateUnqualified(DelegateExecution execution) {
                        getter = execution.getVariable("getter");
                        consume(getter);
                    }

                    void consume(Object value) {}

                    Object getter;
                }
                """));
  }

  @Test
  void convertsGetterBackedAnonymousClassFieldsWithoutConvertingBuilders() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        java(
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    void submit(DelegateExecution execution, Object payload) {
                        Runnable task = new Runnable() {
                            public void run() {
                                getter = execution.getVariableTyped("getter");
                                builder = Variables.objectValue(payload)
                                    .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();
                                consume(this.getter.getValue());
                                consume(builder.getValue());
                            }

                            ObjectValue getter;
                            ObjectValue builder;
                        };
                    }

                    void consume(Object value) {}
                }
                """,
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    void submit(DelegateExecution execution, Object payload) {
                        Runnable task = new Runnable() {
                            public void run() {
                                getter = execution.getVariable("getter");
                                builder = Variables.objectValue(payload)
                                    .serializationDataFormat(Variables.SerializationDataFormats.JSON).create();
                                consume(this.getter);
                                consume(builder.getValue());
                            }

                            Object getter;
                            ObjectValue builder;
                        };
                    }

                    void consume(Object value) {}
                }
                """));
  }
}
