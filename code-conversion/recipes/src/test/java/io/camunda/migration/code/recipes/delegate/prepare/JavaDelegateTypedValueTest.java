/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.delegate.prepare;

import static org.openrewrite.java.Assertions.java;

import io.camunda.migration.code.recipes.sharedRecipes.ReplaceTypedValueAPIRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class JavaDelegateTypedValueTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipes(new ReplaceTypedValueAPIRecipe())
        .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
  }

  @Test
  void ReplaceTypedValueTest() {
    rewriteRun(
        java(
"""
package org.camunda.conversion.java_delegates.handling_process_variables;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.IntegerValue;
import org.camunda.bpm.engine.variable.value.StringValue;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.springframework.stereotype.Component;

@Component
public class RetrievePaymentAdapterProcessVariablesTypedValueAPI implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        IntegerValue typedAmount = execution.getVariableTyped("amount");
        TypedValue stringVariableTyped = execution.getVariableTyped("stringVariable");
        int amount = typedAmount.getValue();
        // do something...
        StringValue typedTransactionId = Variables.stringValue("TX12345");
        execution.setVariable("transactionId", typedTransactionId);
    }
}
                """,
"""
package org.camunda.conversion.java_delegates.handling_process_variables;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
public class RetrievePaymentAdapterProcessVariablesTypedValueAPI implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        // please check type
        Integer typedAmount = (Integer) execution.getVariable("amount");
        // please check type
        Object stringVariableTyped = execution.getVariable("stringVariable");
        int amount = typedAmount;
        // do something...
        String typedTransactionId = "TX12345";
        execution.setVariable("transactionId", typedTransactionId);
    }
}
"""));
  }

  @Test
  void ReplaceTypedValueTest2() {
    rewriteRun(
            java(
                    """
                    package org.camunda.conversion.java_delegates.handling_process_variables;
                    
                    import org.camunda.bpm.engine.delegate.DelegateExecution;
                    import org.camunda.bpm.engine.delegate.JavaDelegate;
                    import org.camunda.bpm.engine.variable.value.IntegerValue;
                    import org.springframework.stereotype.Component;
                    
                    @Component
                    public class RetrievePaymentAdapterProcessVariablesTypedValueAPI implements JavaDelegate {
                    
                        @Override
                        public void execute(DelegateExecution execution) {
                            IntegerValue typedAmount = execution.getVariableTyped("amount");
                        }
                    }
                                    """,
                    """
                    package org.camunda.conversion.java_delegates.handling_process_variables;
                    
                    import org.camunda.bpm.engine.delegate.DelegateExecution;
                    import org.camunda.bpm.engine.delegate.JavaDelegate;
                    import org.springframework.stereotype.Component;
                    
                    @Component
                    public class RetrievePaymentAdapterProcessVariablesTypedValueAPI implements JavaDelegate {
                    
                        @Override
                        public void execute(DelegateExecution execution) {
                            // please check type
                            Integer typedAmount = (Integer) execution.getVariable("amount");
                        }
                    }
                    """));
  }

  @Test
  void dateAndBytesDeclarationsKeepEveryInitializerAndTransientWarning() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class Values {
                private DateValue emptyDate;
                @Deprecated private final DateValue first = Variables.dateValue(new Date(0)), second = Variables.dateValue(new Date(1), false);
                private BytesValue emptyBytes, bytes = Variables.byteArrayValue(new byte[]{1}), transientBytes = Variables.byteArrayValue(new byte[]{2}, true);

                void read(DelegateExecution execution, boolean transientFlag) {
                    final DateValue localDate = Variables.dateValue(new Date(2), true);
                    Date date = localDate.getValue();
                    BytesValue localBytes = Variables.byteArrayValue(new byte[]{3});
                    byte[] contents = localBytes.getValue();
                    DateValue computed = Variables.dateValue(new Date(4), transientFlag);
                    execution.setVariable("first", first);
                    execution.setVariable("localDate", localDate);
                    this.emptyDate = Variables.dateValue(new Date(5), transientFlag);
                    this.emptyBytes = Variables.byteArrayValue(new byte[]{4});
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;

            class Values {
                private Date emptyDate;
                @Deprecated
                private final Date first = new Date(0), second = new Date(1);
                // TODO: review Camunda 7 transient variable semantics for migrated values
                private byte[] emptyBytes, bytes = new byte[]{1}, transientBytes = new byte[]{2};

                void read(DelegateExecution execution, boolean transientFlag) {
                    // TODO: review Camunda 7 transient variable semantics for migrated values
                    final Date localDate = new Date(2);
                    Date date = localDate;
                    byte[] localBytes = new byte[]{3};
                    byte[] contents = localBytes;
                    // TODO: review Camunda 7 transient variable semantics for migrated values
                    Date computed = new Date(4);
                    execution.setVariable("first", first);
                    execution.setVariable("localDate", localDate);
                    // TODO: review Camunda 7 transient variable semantics for migrated values
                    this.emptyDate = new Date(5);
                    this.emptyBytes = new byte[]{4};
                }
            }
            """));
  }

  @Test
  void dateAndBytesTypedReadInitializersKeepGroupedFieldsAndLocalCasts() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class TypedReads {
                DelegateExecution execution;
                DateValue firstDate = execution.getVariableTyped("firstDate"), secondDate = execution.getVariableLocalTyped("secondDate");
                BytesValue firstBytes = execution.getVariableTyped("firstBytes"), secondBytes = execution.getVariableLocalTyped("secondBytes");

                void read(DelegateExecution execution) {
                    DateValue date = execution.getVariableTyped("date");
                    BytesValue bytes = execution.getVariableLocalTyped("bytes");
                    Date dateValue = date.getValue();
                    byte[] contents = bytes.getValue();
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;

            class TypedReads {
                DelegateExecution execution;
                // please check type
                Date firstDate = (Date) execution.getVariable("firstDate"), secondDate = (Date) execution.getVariableLocal("secondDate");
                // please check type
                byte[] firstBytes = (byte[]) execution.getVariable("firstBytes"), secondBytes = (byte[]) execution.getVariableLocal("secondBytes");

                void read(DelegateExecution execution) {
                    // please check type
                    Date date = (Date) execution.getVariable("date");
                    // please check type
                    byte[] bytes = (byte[]) execution.getVariableLocal("bytes");
                    Date dateValue = date;
                    byte[] contents = bytes;
                }
            }
            """));
  }

  @Test
  void nonGenericTypedGettersRequireCasts() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.client.task.ExternalTask;
            import org.camunda.bpm.engine.TaskService;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.VariableMap;
            import org.camunda.bpm.engine.variable.value.IntegerValue;

            class GetterCasts {
                void read(DelegateExecution execution, TaskService service, ExternalTask external) {
                    IntegerValue local = execution.getVariableLocalTyped("local");
                    IntegerValue task = service.getVariableTyped("taskId", "value");
                    IntegerValue taskLocal = service.getVariableLocalTyped("taskId", "local");
                    IntegerValue externalValue = external.getVariableTyped("external");
                    VariableMap values = external.getAllVariablesTyped();
                }
            }
            """,
            """
            import org.camunda.bpm.client.task.ExternalTask;
            import org.camunda.bpm.engine.TaskService;
            import org.camunda.bpm.engine.delegate.DelegateExecution;

            import java.util.Map;

            class GetterCasts {
                void read(DelegateExecution execution, TaskService service, ExternalTask external) {
                    // please check type
                    Integer local = (Integer) execution.getVariableLocal("local");
                    // please check type
                    Integer task = (Integer) service.getVariable("taskId", "value");
                    // please check type
                    Integer taskLocal = (Integer) service.getVariableLocal("taskId", "local");
                    // please check type
                    Integer externalValue = external.getVariable("external");
                    // please check type
                    Map<String, Object> values = external.getAllVariables();
                }
            }
            """));
  }

  @Test
  void groupedTypedLocalsShadowOuterConversions() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.IntegerValue;

            class GroupedValues {
                void use(DelegateExecution execution) {
                    IntegerValue amount = execution.getVariableTyped("amount");
                    DateValue date = execution.getVariableTyped("date");
                    class Nested {
                        IntegerValue load() { return null; }
                        int read() {
                            IntegerValue amount = load(), other = load();
                            return amount.getValue();
                        }
                        Date readParameter(Holder date) { return date.getValue(); }
                        Date readLocal() {
                            Holder date = new Holder();
                            return date.getValue();
                        }
                    }
                }
            }

            class Holder {
                Date getValue() { return new Date(0); }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.IntegerValue;

            class GroupedValues {
                void use(DelegateExecution execution) {
                    // please check type
                    Integer amount = (Integer) execution.getVariable("amount");
                    // please check type
                    Date date = (Date) execution.getVariable("date");
                    class Nested {
                        IntegerValue load() { return null; }
                        int read() {
                            IntegerValue amount = load(), other = load();
                            return amount.getValue();
                        }
                        Date readParameter(Holder date) { return date.getValue(); }
                        Date readLocal() {
                            Holder date = new Holder();
                            return date.getValue();
                        }
                    }
                }
            }

            class Holder {
                Date getValue() { return new Date(0); }
            }
            """));
  }

  @Test
  void convertedFieldsKeepQualifiedReadCastsWithoutChangingOtherOwners() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.IntegerValue;

            class Fields {
                Date readDate() { return this.date.getValue(); }
                byte[] readBytes() { return bytes.getValue(); }

                IntegerValue amount;
                DateValue date;
                BytesValue bytes;

                void update(DelegateExecution execution, Other other) {
                    this.amount = execution.getVariableTyped("amount");
                    this.date = execution.getVariableTyped("date");
                    bytes = execution.getVariableTyped("bytes");
                    other.date = execution.getVariableTyped("other");
                }
            }

            class Other {
                DateValue date = loadDate();
                DateValue loadDate() { return null; }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class Fields {
                Date readDate() { return this.date; }
                byte[] readBytes() { return bytes; }

                Integer amount;
                Date date;
                byte[] bytes;

                void update(DelegateExecution execution, Other other) {
                    this.amount = (Integer) execution.getVariable("amount");
                    this.date = (Date) execution.getVariable("date");
                    bytes = (byte[]) execution.getVariable("bytes");
                    other.date = execution.getVariableTyped("other");
                }
            }

            class Other {
                // TODO: migrate Camunda 7 typed-value declaration manually
                DateValue date = loadDate();
                DateValue loadDate() { return null; }
            }
            """));
  }

  @Test
  void otherConvertedTypedFieldsKeepTheirExistingValueReads() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.ObjectValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class OtherValues {
                DelegateExecution execution;
                Other other;
                Object beforeObject() { return this.object.getValue(); }
                Object beforeTyped() { return typed.getValue(); }
                Object beforeWrapped() { return this.wrapped.getValue(); }
                Object readReassigned() { return reassigned.getValue(); }
                Object readQualifiedReassigned() { return this.qualifiedReassigned.getValue(); }
                Object readReassignedTyped() { return reassignedTyped.getValue(); }

                ObjectValue object = execution.getVariableTyped("object");
                TypedValue typed = execution.getVariableTyped("typed");
                ObjectValue wrapped = (execution.getVariableTyped("wrapped"));
                ObjectValue reassigned = execution.getVariableTyped("reassigned");
                ObjectValue qualifiedReassigned = execution.getVariableTyped("qualifiedReassigned");
                TypedValue reassignedTyped = execution.getVariableTyped("reassignedTyped");
                ObjectValue retained = load();
                ObjectValue load() { return null; }
                TypedValue loadTyped() { return null; }
                void update() {
                    reassigned = load();
                    this.qualifiedReassigned = load();
                    reassignedTyped = loadTyped();
                    other.object = load();
                }

                Object readObject() { return object.getValue(); }
                Object readTyped() { return typed.getValue(); }
                Object qualifiedTyped() { return this.typed.getValue(); }
                Object readRetained() { return retained.getValue(); }
            }

            class Other {
                ObjectValue object;
            }
            """,
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.ObjectValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class OtherValues {
                DelegateExecution execution;
                Other other;
                Object beforeObject() { return this.object; }
                Object beforeTyped() { return typed; }
                Object beforeWrapped() { return this.wrapped; }
                Object readReassigned() { return reassigned.getValue(); }
                Object readQualifiedReassigned() { return this.qualifiedReassigned.getValue(); }
                Object readReassignedTyped() { return reassignedTyped.getValue(); }

                // please check type
                Object object = execution.getVariable("object");
                // please check type
                Object typed = execution.getVariable("typed");
                // please check type
                Object wrapped = execution.getVariable("wrapped");
                ObjectValue reassigned = execution.getVariableTyped("reassigned");
                ObjectValue qualifiedReassigned = execution.getVariableTyped("qualifiedReassigned");
                TypedValue reassignedTyped = execution.getVariableTyped("reassignedTyped");
                // TODO: migrate Camunda 7 typed-value declaration manually
                ObjectValue retained = load();
                ObjectValue load() { return null; }
                TypedValue loadTyped() { return null; }
                void update() {
                    reassigned = load();
                    this.qualifiedReassigned = load();
                    reassignedTyped = loadTyped();
                    other.object = load();
                }

                Object readObject() { return object; }
                Object readTyped() { return typed; }
                Object qualifiedTyped() { return this.typed; }
                Object readRetained() { return retained.getValue(); }
            }

            class Other {
                ObjectValue object;
            }
            """));
  }

  @Test
  void typedGettersPassedToVariableSettersUseRawReads() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;

            class SetterReads {
                void copy(DelegateExecution execution) {
                    execution.setVariable("copy", execution.getVariableTyped("source"));
                }
            }
            """,
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;

            class SetterReads {
                void copy(DelegateExecution execution) {
                    execution.setVariable("copy", execution.getVariable("source"));
                }
            }
            """));
  }

  @Test
  void unsupportedTypedConsumersStayTypedForManualMigration() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class TypedConsumers {
                DateValue retainedDate = loadDate();
                DateValue typedDate = Variables.dateValue(new Date(0));

                DateValue loadDate() {
                    return Variables.dateValue(new Date(1));
                }
                void acceptDate(DateValue value) {}
                void accept(Object value) {}

                void use(DelegateExecution execution) {
                    retainedDate = execution.getVariableTyped("date");
                    acceptDate(typedDate);
                    acceptDate(execution.getVariableTyped("argument"));
                    accept(Variables.dateValue(new Date(2)));
                    accept(Variables.byteArrayValue(new byte[]{1}, true));
                }

                DateValue[] readArray(DelegateExecution execution) {
                    return new DateValue[]{execution.getVariableTyped("array")};
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class TypedConsumers {
                // TODO: migrate Camunda 7 typed-value declaration manually
                DateValue retainedDate = loadDate();
                // TODO: migrate Camunda 7 typed-value declaration manually
                DateValue typedDate = Variables.dateValue(new Date(0));

                DateValue loadDate() {
                    return // TODO: migrate Camunda 7 typed-value factory call manually
                            Variables.dateValue(new Date(1));
                }
                void acceptDate(DateValue value) {}
                void accept(Object value) {}

                void use(DelegateExecution execution) {
                    retainedDate = execution.getVariableTyped("date");
                    acceptDate(typedDate);
                    acceptDate(execution.getVariableTyped("argument"));
                    accept(// TODO: migrate Camunda 7 typed-value factory call manually
                            Variables.dateValue(new Date(2)));
                    accept(// TODO: migrate Camunda 7 typed-value factory call manually
                            Variables.byteArrayValue(new byte[]{1}, true));
                }

                DateValue[] readArray(DelegateExecution execution) {
                    return new DateValue[]{execution.getVariableTyped("array")};
                }
            }
            """));
  }

  @Test
  void retainedAssignmentsKeepTypedGetters() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.IntegerValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class RetainedAssignments {
                IntegerValue amount;

                void assign(DelegateExecution execution, TypedValue parameter, TypedValue[] values) {
                    TypedValue local;
                    local = execution.getVariableTyped("local");
                    parameter = execution.getVariableTyped("parameter");
                    values[0] = execution.getVariableTyped("array");
                    TypedValue amount;
                    amount = execution.getVariableTyped("shadow");
                    IntegerValue number = execution.getVariableTyped("number");
                }
            }
            """,
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class RetainedAssignments {
                Integer amount;

                void assign(DelegateExecution execution, TypedValue parameter, TypedValue[] values) {
                    TypedValue local;
                    local = execution.getVariableTyped("local");
                    parameter = execution.getVariableTyped("parameter");
                    values[0] = execution.getVariableTyped("array");
                    TypedValue amount;
                    amount = execution.getVariableTyped("shadow");
                    // please check type
                    Integer number = (Integer) execution.getVariable("number");
                }
            }
            """));
  }

  @Test
  void nestedRetainedContextsKeepTypedSourceValues() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.ObjectValue;

            class NestedUses {
                DateValue raw = Variables.dateValue(new Date(0));
                ObjectValue object;
                DateValue retained = choose(raw.getValue());

                DateValue choose(Date value) {
                    return Variables.dateValue(value);
                }
                void accept(Object value) {}

                void use() {
                    accept(Variables.dateValue(raw.getValue()));
                    this.object = Variables.objectValue(raw.getValue()).create();
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.ObjectValue;

            class NestedUses {
                // TODO: migrate Camunda 7 typed-value declaration manually
                DateValue raw = Variables.dateValue(new Date(0));
                ObjectValue object;
                // TODO: migrate Camunda 7 typed-value declaration manually
                DateValue retained = choose(raw.getValue());

                DateValue choose(Date value) {
                    return // TODO: migrate Camunda 7 typed-value factory call manually
                            Variables.dateValue(value);
                }
                void accept(Object value) {}

                void use() {
                    accept(// TODO: migrate Camunda 7 typed-value factory call manually
                            Variables.dateValue(raw.getValue()));
                    this.object = Variables.objectValue(raw.getValue()).create();
                }
            }
            """));
  }

  @Test
  void typedParametersShadowConvertedOuterLocal() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import java.util.function.Function;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class ScopedReads {
                void read(DateValue[] values) {
                    DateValue date = Variables.dateValue(new Date(0));
                    class Local {
                        Date fromParameter(DateValue date) { return date.getValue(); }
                        Date fromLambda(DateValue[] values) {
                            Function<DateValue, Date> fn = (DateValue date) -> date.getValue();
                            for (DateValue date : values) {
                                Date value = date.getValue();
                            }
                            return fn.apply(values[0]);
                        }
                    }
                    Date result = date.getValue();
                }
            }
            """,
            """
            import java.util.Date;
            import java.util.function.Function;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class ScopedReads {
                void read(DateValue[] values) {
                    Date date = new Date(0);
                    class Local {
                        Date fromParameter(DateValue date) { return date.getValue(); }
                        Date fromLambda(DateValue[] values) {
                            Function<DateValue, Date> fn = (DateValue date) -> date.getValue();
                            for (DateValue date : values) {
                                Date value = date.getValue();
                            }
                            return fn.apply(values[0]);
                        }
                    }
                    Date result = date;
                }
            }
            """));
  }

  @Test
  void recordComponentsStayTypedForManualMigration() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.IntegerValue;

            record Payload(DateValue date, BytesValue bytes, IntegerValue amount) {
                Date readDate() { return date.getValue(); }
                byte[] readBytes() { return bytes.getValue(); }
                int readAmount() { return amount.getValue(); }
            }
            """));
  }

  @Test
  void typedArrayInitializersRetainTheirSources() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class TypedArrays {
                DateValue date = Variables.dateValue(new Date(0));
                BytesValue bytes = Variables.byteArrayValue(new byte[]{1});
                DateValue[] dates = {date};
                BytesValue[] buffers = new BytesValue[]{this.bytes};
                TypedValue[] mixed = {date, bytes};

                void use() {
                    DateValue local = Variables.dateValue(new Date(2));
                    DateValue[] locals = {local};
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class TypedArrays {
                // TODO: migrate Camunda 7 typed-value declaration manually
                DateValue date = Variables.dateValue(new Date(0));
                // TODO: migrate Camunda 7 typed-value declaration manually
                BytesValue bytes = Variables.byteArrayValue(new byte[]{1});
                // TODO: migrate Camunda 7 typed-value declaration manually
                DateValue[] dates = {date};
                // TODO: migrate Camunda 7 typed-value declaration manually
                BytesValue[] buffers = new BytesValue[]{this.bytes};
                TypedValue[] mixed = {date, bytes};

                void use() {
                    // TODO: migrate Camunda 7 typed-value declaration manually
                    DateValue local = Variables.dateValue(new Date(2));
                    // TODO: migrate Camunda 7 typed-value declaration manually
                    DateValue[] locals = {local};
                }
            }
            """));
  }

  @Test
  void typedMemberReferencesRetainTheirReceivers() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import java.util.function.Supplier;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class TypedReferences {
                DateValue date = Variables.dateValue(new Date(0));
                BytesValue bytes = Variables.byteArrayValue(new byte[]{1});
                Supplier<Date> readDate = date::getValue;
                Supplier<byte[]> readBytes = this.bytes::getValue;
            }
            """,
            """
            import java.util.Date;
            import java.util.function.Supplier;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class TypedReferences {
                // TODO: migrate Camunda 7 typed-value declaration manually
                DateValue date = Variables.dateValue(new Date(0));
                // TODO: migrate Camunda 7 typed-value declaration manually
                BytesValue bytes = Variables.byteArrayValue(new byte[]{1});
                Supplier<Date> readDate = date::getValue;
                Supplier<byte[]> readBytes = this.bytes::getValue;
            }
            """));
  }

  @Test
  void typedSupertypeAssignmentsRetainTheirSources() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class SupertypeAssignments {
                DateValue date = Variables.dateValue(new Date(0));
                BytesValue bytes = Variables.byteArrayValue(new byte[]{1});
                TypedValue alias = date;
                TypedValue assigned;

                void copy() {
                    assigned = this.bytes;
                    DateValue local = Variables.dateValue(new Date(2));
                    TypedValue localAlias;
                    localAlias = local;
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class SupertypeAssignments {
                // TODO: migrate Camunda 7 typed-value declaration manually
                DateValue date = Variables.dateValue(new Date(0));
                // TODO: migrate Camunda 7 typed-value declaration manually
                BytesValue bytes = Variables.byteArrayValue(new byte[]{1});
                TypedValue alias = date;
                TypedValue assigned;

                void copy() {
                    assigned = this.bytes;
                    // TODO: migrate Camunda 7 typed-value declaration manually
                    DateValue local = Variables.dateValue(new Date(2));
                    TypedValue localAlias;
                    localAlias = local;
                }
            }
            """));
  }

  @Test
  void instanceofChecksKeepTypedSourcesAndPatternVariables() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class TypedChecks {
                DateValue date = Variables.dateValue(new Date(0));
                BytesValue bytes = Variables.byteArrayValue(new byte[]{1});

                boolean matches(Object candidate) {
                    boolean dateMatches = date instanceof DateValue;
                    boolean bytesMatch = this.bytes instanceof BytesValue;
                    if (candidate instanceof DateValue matchedDate) {
                        return dateMatches && matchedDate.getValue() != null;
                    }
                    if (candidate instanceof BytesValue matchedBytes) {
                        return bytesMatch && matchedBytes.getValue() != null;
                    }
                    return false;
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class TypedChecks {
                // TODO: migrate Camunda 7 typed-value declaration manually
                DateValue date = Variables.dateValue(new Date(0));
                // TODO: migrate Camunda 7 typed-value declaration manually
                BytesValue bytes = Variables.byteArrayValue(new byte[]{1});

                boolean matches(Object candidate) {
                    boolean dateMatches = date instanceof DateValue;
                    boolean bytesMatch = this.bytes instanceof BytesValue;
                    if (candidate instanceof DateValue matchedDate) {
                        return dateMatches && matchedDate.getValue() != null;
                    }
                    if (candidate instanceof BytesValue matchedBytes) {
                        return bytesMatch && matchedBytes.getValue() != null;
                    }
                    return false;
                }
            }
            """));
  }

  @Test
  void genericTypedDeclarationsPreserveModifiersAndInitializers() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.variable.value.IntegerValue;

            class GenericValues {
                @Deprecated private final IntegerValue empty = null;
                private IntegerValue loaded = load();
                @Deprecated private IntegerValue pending;

                IntegerValue load() { return null; }
            }
            """,
            """
            import org.camunda.bpm.engine.variable.value.IntegerValue;

            class GenericValues {
                // TODO: migrate Camunda 7 typed-value declaration manually
                @Deprecated private final IntegerValue empty = null;
                // TODO: migrate Camunda 7 typed-value declaration manually
                private IntegerValue loaded = load();
                @Deprecated
                private Integer pending;

                IntegerValue load() { return null; }
            }
            """));
  }

  @Test
  void annotatedTypedGetterAndFactoryDeclarationsKeepAnnotations() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.IntegerValue;
            import org.camunda.bpm.engine.variable.value.StringValue;

            class AnnotatedValues {
                DelegateExecution execution;
                @Deprecated private IntegerValue amount = execution.getVariableTyped("amount");
                @Deprecated private StringValue label = Variables.stringValue("ready");
            }
            """,
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;

            class AnnotatedValues {
                DelegateExecution execution;
                // please check type
                @Deprecated
                private Integer amount = (Integer) execution.getVariable("amount");
                @Deprecated
                private String label = "ready";
            }
            """));
  }

  @Test
  void nestedAndLocalRecordComponentsStayTyped() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.IntegerValue;

            class NestedRecords {
                record Member(DateValue date, BytesValue bytes, IntegerValue amount) {
                    Date readDate() { return date.getValue(); }
                }

                void use(DelegateExecution execution) {
                    IntegerValue amount = execution.getVariableTyped("amount");
                    record Local(DateValue date, BytesValue bytes, IntegerValue amount) {
                        byte[] readBytes() { return bytes.getValue(); }
                        int readAmount() { return amount.getValue(); }
                    }
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.IntegerValue;

            class NestedRecords {
                record Member(DateValue date, BytesValue bytes, IntegerValue amount) {
                    Date readDate() { return date.getValue(); }
                }

                void use(DelegateExecution execution) {
                    // please check type
                    Integer amount = (Integer) execution.getVariable("amount");
                    record Local(DateValue date, BytesValue bytes, IntegerValue amount) {
                        byte[] readBytes() { return bytes.getValue(); }
                        int readAmount() { return amount.getValue(); }
                    }
                }
            }
            """));
  }
}
