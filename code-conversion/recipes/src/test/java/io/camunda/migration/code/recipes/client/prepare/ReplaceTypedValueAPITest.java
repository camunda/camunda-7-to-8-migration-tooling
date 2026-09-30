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
  void convertsOnlyFieldsAssignedTypedGetters() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        java(
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    private ObjectValue bareGetter;
                    private ObjectValue qualifiedGetter;
                    private ObjectValue bareBuilder;
                    private ObjectValue qualifiedBuilder;
                    private ObjectValue mixedField;
                    private ObjectValue mixedQualifiedGetter;
                    private ObjectValue shadowedGetter;

                    void assign(DelegateExecution execution, Object payload) {
                        bareGetter = execution.getVariableTyped("bare");
                        (this.qualifiedGetter) = execution.getVariableTyped("qualified");
                        bareBuilder = Variables.objectValue(payload).create();
                        this.qualifiedBuilder = Variables.objectValue(payload).create();
                        mixedField = execution.getVariableTyped("mixed");
                        this.mixedField = Variables.objectValue(payload).create();
                        this.mixedQualifiedGetter = execution.getVariableTyped("mixedQualified");
                        mixedQualifiedGetter = Variables.objectValue(payload).create();
                    }

                    void shadow(DelegateExecution execution, Object payload) {
                        ObjectValue bareGetter;
                        bareGetter = Variables.objectValue(payload).create();
                        ObjectValue shadowedGetter;
                        shadowedGetter = Variables.objectValue(payload).create();
                        this.shadowedGetter = execution.getVariableTyped("shadowed");
                    }
                }
                """,
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    private Object bareGetter;
                    private Object qualifiedGetter;
                    private ObjectValue bareBuilder;
                    private ObjectValue qualifiedBuilder;
                    private ObjectValue mixedField;
                    private ObjectValue mixedQualifiedGetter;
                    private Object shadowedGetter;

                    void assign(DelegateExecution execution, Object payload) {
                        bareGetter = execution.getVariable("bare");
                        (this.qualifiedGetter) = execution.getVariable("qualified");
                        bareBuilder = Variables.objectValue(payload).create();
                        this.qualifiedBuilder = Variables.objectValue(payload).create();
                        mixedField = execution.getVariableTyped("mixed");
                        this.mixedField = Variables.objectValue(payload).create();
                        this.mixedQualifiedGetter = execution.getVariableTyped("mixedQualified");
                        mixedQualifiedGetter = Variables.objectValue(payload).create();
                    }

                    void shadow(DelegateExecution execution, Object payload) {
                        ObjectValue bareGetter;
                        bareGetter = Variables.objectValue(payload).create();
                        ObjectValue shadowedGetter;
                        shadowedGetter = Variables.objectValue(payload).create();
                        this.shadowedGetter = execution.getVariable("shadowed");
                    }
                }
                """));
  }

  @Test
  void convertsFieldsUsedBeforeTheirDeclarations() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        java(
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    void read(DelegateExecution execution) {
                        bare = execution.getVariableTyped("bare");
                        Object first = bare.getValue();
                        this.qualified = execution.getVariableTyped("qualified");
                        Object second = (this.qualified).getValue();
                    }

                    private ObjectValue bare;
                    private ObjectValue qualified;
                }
                """,
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    void read(DelegateExecution execution) {
                        bare = execution.getVariable("bare");
                        Object first = bare;
                        this.qualified = execution.getVariable("qualified");
                        Object second = this.qualified;
                    }

                    private Object bare;
                    private Object qualified;
                }
                """));
  }

  @Test
  void convertsFieldsInNestedAndAnonymousClassesBeforeTheirDeclarations() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        java(
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    class Nested {
                        void read(DelegateExecution execution) {
                            value = execution.getVariableTyped("nested");
                            Object nested = value.getValue();
                            PayloadTest.this.outer = execution.getVariableTyped("outer");
                            Object outside = PayloadTest.this.outer.getValue();
                        }

                        private ObjectValue value;
                    }

                    Runnable anonymous(DelegateExecution execution) {
                        return new Runnable() {
                            @Override public void run() {
                                field = execution.getVariableTyped("anonymous");
                                Object result = field.getValue();
                            }

                            private ObjectValue field;
                        };
                    }

                    private ObjectValue outer;
                }
                """,
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    class Nested {
                        void read(DelegateExecution execution) {
                            value = execution.getVariable("nested");
                            Object nested = value;
                            PayloadTest.this.outer = execution.getVariable("outer");
                            Object outside = PayloadTest.this.outer;
                        }

                        private Object value;
                    }

                    Runnable anonymous(DelegateExecution execution) {
                        return new Runnable() {
                            @Override public void run() {
                                field = execution.getVariable("anonymous");
                                Object result = field;
                            }

                            private Object field;
                        };
                    }

                    private Object outer;
                }
                """));
  }

  @Test
  void convertsNestedFieldUsedFromEnclosingClassBeforeDeclaration() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        java(
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    void read(DelegateExecution execution, Nested nested) {
                        nested.value = execution.getVariableTyped("nested");
                        Object result = nested.value.getValue();
                    }

                    class Nested {
                        private ObjectValue value;
                    }
                }
                """,
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    void read(DelegateExecution execution, Nested nested) {
                        nested.value = execution.getVariable("nested");
                        Object result = nested.value;
                    }

                    class Nested {
                        private Object value;
                    }
                }
                """));
  }

  @Test
  void preservesParenthesizedMixedFieldAssignments() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()).expectedCyclesThatMakeChanges(0),
        java(
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    private ObjectValue mixed;

                    void assign(DelegateExecution execution, Object payload) {
                        (this.mixed) = execution.getVariableTyped("mixed");
                        mixed = Variables.objectValue(payload).create();
                    }
                }
                """));
  }

  @Test
  void preservesFieldsWrittenByTheirEnclosingClass() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()).expectedCyclesThatMakeChanges(0),
        java(
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    void write(Nested nested, Object payload) {
                        nested.value = Variables.objectValue(payload).create();
                        consume(nested.value.getValue());
                    }

                    class Nested {
                        void read(DelegateExecution execution) {
                            value = execution.getVariableTyped("value");
                        }

                        private ObjectValue value;
                    }

                    void consume(Object payload) {}
                }
                """));
  }

  @Test
  void preservesFieldsWritableFromAnotherCompilationUnit() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()).expectedCyclesThatMakeChanges(0),
        java(
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    ObjectValue packageVisible;
                    protected ObjectValue protectedValue;
                    public ObjectValue publicValue;

                    void read(DelegateExecution execution) {
                        packageVisible = execution.getVariableTyped("package");
                        this.protectedValue = execution.getVariableTyped("protected");
                        this.publicValue = execution.getVariableTyped("public");
                    }
                }
                """));
  }

  @Test
  void preservesGetterOverloadsWithoutAnEquivalentUntypedCall() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()).expectedCyclesThatMakeChanges(0),
        java(
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    private ObjectValue deferred;
                    private ObjectValue qualified;

                    void read(DelegateExecution execution) {
                        deferred = execution.getVariableTyped("payload", false);
                        this.qualified = execution.getVariableTyped("qualified", true);
                    }
                }
                """));
  }

  @Test
  void convertsTwoArgumentTaskServiceGetterField() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        java(
            """
                import org.camunda.bpm.engine.TaskService;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    private ObjectValue taskValue;

                    void read(TaskService taskService) {
                        taskValue = taskService.getVariableTyped("task", "payload");
                    }
                }
                """,
            """
                import org.camunda.bpm.engine.TaskService;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    private Object taskValue;

                    void read(TaskService taskService) {
                        taskValue = taskService.getVariable("task", "payload");
                    }
                }
                """));
  }

  @Test
  void preservesLocalTypedGettersWithoutEquivalentMigration() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()).expectedCyclesThatMakeChanges(0),
        java(
            """
                import org.camunda.bpm.engine.TaskService;
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    private ObjectValue executionLocal;
                    private ObjectValue taskLocal;

                    void read(DelegateExecution execution, TaskService taskService) {
                        executionLocal = execution.getVariableLocalTyped("execution");
                        this.taskLocal = taskService.getVariableLocalTyped("task", "local");
                    }
                }
                """));
  }

  @Test
  void preservesGetterFieldsUsedAsTypedValues() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()).expectedCyclesThatMakeChanges(0),
        java(
            """
                import java.util.ArrayList;
                import java.util.List;
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    private ObjectValue formatOnly;
                    private ObjectValue typedArgument;
                    private List<ObjectValue> values = new ArrayList<>();

                    void read(DelegateExecution execution) {
                        formatOnly = execution.getVariableTyped("format");
                        this.typedArgument = execution.getVariableTyped("argument");
                    }

                    String format() {
                        return formatOnly.getSerializationDataFormat();
                    }

                    void collect() {
                        values.add(this.typedArgument);
                    }

                    ObjectValue current() {
                        return typedArgument;
                    }
                }
                """));
  }

  @Test
  void visitsConvertedFieldReadsInsidePreservedTypedValues() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        java(
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    void update(DelegateExecution execution) {
                        source = execution.getVariableTyped("source");
                        this.builder = Variables.objectValue(source.getValue())
                            .serializationDataFormat("application/xml").create();
                        ObjectValue local = Variables.objectValue(this.source.getValue())
                            .serializationDataFormat("application/xml").create();
                        ObjectValue delayed;
                        delayed = Variables.objectValue(this.source.getValue())
                            .serializationDataFormat("application/xml").create();
                        ObjectValue reassigned = execution.getVariableTyped(
                            String.valueOf(source.getValue()));
                        reassigned = Variables.objectValue("later")
                            .serializationDataFormat("application/xml").create();
                    }

                    private ObjectValue source;
                    private ObjectValue builder;
                }
                """,
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    void update(DelegateExecution execution) {
                        source = execution.getVariable("source");
                        this.builder = Variables.objectValue(source)
                            .serializationDataFormat("application/xml").create();
                        ObjectValue local = Variables.objectValue(this.source)
                            .serializationDataFormat("application/xml").create();
                        ObjectValue delayed;
                        delayed = Variables.objectValue(this.source)
                            .serializationDataFormat("application/xml").create();
                        ObjectValue reassigned = execution.getVariableTyped(
                            String.valueOf(source));
                        reassigned = Variables.objectValue("later")
                            .serializationDataFormat("application/xml").create();
                    }

                    private Object source;
                    private ObjectValue builder;
                }
                """));
  }

  @Test
  void preservesNestedTypedGettersWhenUnwrappingBuilders() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        java(
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;

                class PayloadTest {
                    void submit(DelegateExecution execution) {
                        consume(Variables.objectValue(execution.getVariableTyped("json").getValue())
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create());
                        consume(Variables.objectValue(execution.getVariableTyped("xml").getValue())
                            .serializationDataFormat("application/xml").create());
                        consume(execution.getVariableTyped("direct").getValue());
                        consume(execution.getVariableLocalTyped("local").getValue());
                    }

                    void consume(Object value) {}
                }
                """,
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;

                class PayloadTest {
                    void submit(DelegateExecution execution) {
                        consume(// type set to java.lang.Object
                                execution.getVariableTyped("json").getValue());
                        consume(Variables.objectValue(execution.getVariableTyped("xml").getValue())
                            .serializationDataFormat("application/xml").create());
                        consume(execution.getVariableTyped("direct").getValue());
                        consume(execution.getVariableLocalTyped("local").getValue());
                    }

                    void consume(Object value) {}
                }
                """));
  }

  @Test
  void preservesTypedGettersInsideMixedFieldExpressions() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()).expectedCyclesThatMakeChanges(0),
        java(
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    private ObjectValue mixed;

                    void update(DelegateExecution execution, boolean condition, Object payload) {
                        this.mixed = execution.getVariableTyped("direct");
                        mixed = condition ? execution.getVariableTyped("first") : null;
                        this.mixed = (ObjectValue) execution.getVariableTyped("second");
                        mixed = Variables.objectValue(payload)
                            .serializationDataFormat("application/xml").create();
                        this.mixed = Variables.objectValue(
                            execution.getVariableTyped("nested").getValue())
                            .serializationDataFormat("application/xml").create();
                    }

                    Object current() {
                        return this.mixed.getValue();
                    }
                }
                """));
  }

  @Test
  void visitsConvertedFieldReadsInsideMigratedTypedGetters() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        java(
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    void update(DelegateExecution execution) {
                        source = execution.getVariableTyped("source");
                        source = execution.getVariableTyped(String.valueOf(this.source.getValue()));
                        ObjectValue payload = execution.getVariableTyped(
                            String.valueOf(source.getValue()));
                        consume(payload.getValue());
                    }

                    private ObjectValue source;
                    private java.util.List<ObjectValue> typedValues;

                    void consume(Object value) {}
                }
                """,
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    void update(DelegateExecution execution) {
                        source = execution.getVariable("source");
                        source = execution.getVariable(String.valueOf(this.source));
                        // please check type
                        Object payload = execution.getVariable(
                                String.valueOf(source));
                        consume(payload);
                    }

                    private Object source;
                    private java.util.List<ObjectValue> typedValues;

                    void consume(Object value) {}
                }
                """));
  }

  @Test
  void preservesFieldsUsedAsTypedChainedAssignmentResults() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()).expectedCyclesThatMakeChanges(0),
        java(
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    private ObjectValue retained;
                    private ObjectValue nested;

                    void update(DelegateExecution execution) {
                        retained = nested = execution.getVariableTyped("payload");
                        consume(nested.getValue());
                    }

                    void consume(Object value) {}
                }
                """));
  }

  @Test
  void rewritesConvertedAssignmentsInsideRetainedAndUnwrappedBuilders() {
    rewriteRun(
        spec -> spec.recipe(new ReplaceTypedValueAPIRecipe()),
        java(
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    private ObjectValue retained;
                    private ObjectValue nestedXml;
                    private ObjectValue nestedJson;

                    void update(DelegateExecution execution) {
                        retained = Variables.objectValue(this.nestedXml = execution.getVariableTyped("xml"))
                            .serializationDataFormat("application/xml").create();
                        consume(Variables.objectValue(String.valueOf(
                            nestedJson = execution.getVariableTyped("json")))
                            .serializationDataFormat(Variables.SerializationDataFormats.JSON).create());
                        consume(nestedXml.getValue());
                        consume(nestedJson.getValue());
                    }

                    void consume(Object value) {}
                }
                """,
            """
                import org.camunda.bpm.engine.delegate.DelegateExecution;
                import org.camunda.bpm.engine.variable.Variables;
                import org.camunda.bpm.engine.variable.value.ObjectValue;

                class PayloadTest {
                    private ObjectValue retained;
                    private Object nestedXml;
                    private Object nestedJson;

                    void update(DelegateExecution execution) {
                        retained = Variables.objectValue(this.nestedXml = execution.getVariable("xml"))
                            .serializationDataFormat("application/xml").create();
                        consume(// type set to java.lang.Object
                                String.valueOf(
                                        nestedJson = execution.getVariable("json")));
                        consume(nestedXml);
                        consume(nestedJson);
                    }

                    void consume(Object value) {}
                }
                """));
  }
}
