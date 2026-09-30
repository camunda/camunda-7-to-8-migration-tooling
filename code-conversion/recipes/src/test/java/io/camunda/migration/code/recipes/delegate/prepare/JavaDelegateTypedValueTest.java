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
  void typedByteGetterDoesNotImportPrimitiveArray() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.BytesValue;

            class ByteReads {
                void read(DelegateExecution execution) {
                    BytesValue bytes = execution.getVariableTyped("bytes");
                    byte[] value = bytes.getValue();
                }
            }
            """,
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;

            class ByteReads {
                void read(DelegateExecution execution) {
                    // please check type
                    byte[] bytes = (byte[]) execution.getVariable("bytes");
                    byte[] value = bytes;
                }
            }
            """));
  }

  @Test
  void groupedTypedGetterDeclarationsKeepEveryVariable() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.IntegerValue;

            class GroupedReads {
                void read(DelegateExecution execution) {
                    final IntegerValue first = execution.getVariableTyped("first"), second = execution.getVariableTyped("second");
                    BytesValue bytes = execution.getVariableTyped("bytes"), otherBytes = execution.getVariableTyped("otherBytes");
                }
            }
            """,
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;

            class GroupedReads {
                void read(DelegateExecution execution) {
                    // please check type
                    final Integer first = (Integer) execution.getVariable("first"), second = (Integer) execution.getVariable("second");
                    // please check type
                    byte[] bytes = (byte[]) execution.getVariable("bytes"), otherBytes = (byte[]) execution.getVariable("otherBytes");
                }
            }
            """));
  }

  @Test
  void groupedTypedGetterFieldsConvertBeforeReads() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class GetterFields {
                private DelegateExecution execution;
                Date readDate() { return this.secondDate.getValue(); }
                byte[] readBytes() { return this.secondBytes.getValue(); }

                private DateValue firstDate = execution.getVariableTyped("firstDate"), secondDate = execution.getVariableTyped("secondDate");
                private BytesValue firstBytes = execution.getVariableTyped("firstBytes"), secondBytes = execution.getVariableTyped("secondBytes");
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;

            class GetterFields {
                private DelegateExecution execution;
                Date readDate() { return this.secondDate; }
                byte[] readBytes() { return this.secondBytes; }

                // please check type
                private Date firstDate = (Date) execution.getVariable("firstDate"), secondDate = (Date) execution.getVariable("secondDate");
                // please check type
                private byte[] firstBytes = (byte[]) execution.getVariable("firstBytes"), secondBytes = (byte[]) execution.getVariable("secondBytes");
            }
            """));
  }

  @Test
  void qualifiedTypedFieldAssignmentRemainsAssignable() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.IntegerValue;
            import org.camunda.bpm.engine.variable.value.ObjectValue;

            public class TypedFieldDelegate implements JavaDelegate {
                IntegerValue amount;
                DateValue date;
                BytesValue bytes;
                ObjectValue object;

                @Override
                public void execute(DelegateExecution execution) {
                    this.amount = execution.getVariableTyped("amount");
                    this.date = execution.getVariableTyped("date");
                    this.bytes = execution.getVariableTyped("bytes");
                    this.object = execution.getVariableTyped("object");
                }
            }
            """,
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;

            import java.util.Date;

            public class TypedFieldDelegate implements JavaDelegate {
                Integer amount;
                Date date;
                byte[] bytes;
                Object object;

                @Override
                public void execute(DelegateExecution execution) {
                    this.amount = (Integer) execution.getVariable("amount");
                    this.date = (Date) execution.getVariable("date");
                    this.bytes = (byte[]) execution.getVariable("bytes");
                    this.object = execution.getVariable("object");
                }
            }
            """));
  }

  @Test
  void qualifiedAssignmentsHandleSameClassButKeepUnrelatedOwners() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class OwnedFields {
                private DateValue date;
                private BytesValue bytes;

                void assign(OwnedFields other, RetainedFields unrelated, DelegateExecution execution) {
                    other.date = execution.getVariableTyped("date");
                    other.bytes = execution.getVariableTyped("bytes");
                    unrelated.date = execution.getVariableTyped("otherDate");
                }
            }

            abstract class RetainedFields {
                DateValue date = loadDate();
                abstract DateValue loadDate();
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class OwnedFields {
                private Date date;
                private byte[] bytes;

                void assign(OwnedFields other, RetainedFields unrelated, DelegateExecution execution) {
                    other.date = (Date) execution.getVariable("date");
                    other.bytes = (byte[]) execution.getVariable("bytes");
                    unrelated.date = execution.getVariableTyped("otherDate");
                }
            }

            abstract class RetainedFields {
                // TODO: migrate Camunda 7 typed-value initializer manually
                DateValue date = loadDate();
                abstract DateValue loadDate();
            }
            """));
  }

  @Test
  void nonDelegateTypedGettersKeepQualifiedAssignmentsAssignable() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.client.task.ExternalTask;
            import org.camunda.bpm.engine.TaskService;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class TypedFields {
                private DateValue date;
                private BytesValue bytes;

                void read(ExternalTask externalTask, TaskService taskService) {
                    this.date = externalTask.getVariableTyped("date");
                    this.bytes = taskService.getVariableTyped("task", "bytes");
                }
            }
            """,
            """
            import org.camunda.bpm.client.task.ExternalTask;
            import org.camunda.bpm.engine.TaskService;

            import java.util.Date;

            class TypedFields {
                private Date date;
                private byte[] bytes;

                void read(ExternalTask externalTask, TaskService taskService) {
                    this.date = (Date) externalTask.getVariable("date");
                    this.bytes = (byte[]) taskService.getVariable("task", "bytes");
                }
            }
            """));
  }

  @Test
  void typedFactoryInitializersRemainAssignable() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class InitializedValues {
                private DateValue date = Variables.dateValue(new Date(0)), otherDate = Variables.dateValue(new Date(1));
                private BytesValue bytes = Variables.byteArrayValue(new byte[] {1}), otherBytes = Variables.byteArrayValue(new byte[] {2});
            }
            """,
            """
            import java.util.Date;

            class InitializedValues {
                private Date date = new Date(0), otherDate = new Date(1);
                private byte[] bytes = new byte[]{1}, otherBytes = new byte[]{2};
            }
            """));
  }

  @Test
  void transientTypedFactoryInitializersRemainAssignable() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class InitializedValues {
                private boolean transientFlag;
                private DateValue date = Variables.dateValue(new Date(0), true), otherDate = Variables.dateValue(new Date(1), transientFlag);
                private BytesValue bytes = Variables.byteArrayValue(new byte[] {1}, false), otherBytes = Variables.byteArrayValue(new byte[] {2}, true);
            }
            """,
            """
            import java.util.Date;

            class InitializedValues {
                private boolean transientFlag;
                // TODO: review Camunda 7 transient variable semantics for migrated values
                private Date date = new Date(0), otherDate = new Date(1);
                // TODO: review Camunda 7 transient variable semantics for migrated values
                private byte[] bytes = new byte[]{1}, otherBytes = new byte[]{2};
            }
            """));
  }

  @Test
  void nestedTypedFactoryArgumentsAreConverted() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class InitializedValues {
                private DateValue firstDate = Variables.dateValue(new Date(0));
                private DateValue otherDate = Variables.dateValue(firstDate.getValue());
                private BytesValue firstBytes = Variables.byteArrayValue(new byte[] {1});
                private BytesValue otherBytes = Variables.byteArrayValue(firstBytes.getValue(), false);
            }
            """,
            """
            import java.util.Date;

            class InitializedValues {
                private Date firstDate = new Date(0);
                private Date otherDate = firstDate;
                private byte[] firstBytes = new byte[]{1};
                private byte[] otherBytes = firstBytes;
            }
            """));
  }

  @Test
  void unsupportedTypedInitializersRemainForManualMigration() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            abstract class InitializedValues {
                abstract DateValue loadDate();
                abstract BytesValue loadBytes();

                private DateValue date = loadDate();
                private BytesValue bytes = loadBytes();
            }
            """,
            """
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            abstract class InitializedValues {
                abstract DateValue loadDate();
                abstract BytesValue loadBytes();

                // TODO: migrate Camunda 7 typed-value initializer manually
                private DateValue date = loadDate();
                // TODO: migrate Camunda 7 typed-value initializer manually
                private BytesValue bytes = loadBytes();
            }
            """));
  }

  @Test
  void convertedTypedFieldsCanInitializeLaterFields() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class InitializedValues {
                private DateValue date = Variables.dateValue(new Date(0)), otherDate = date;
                private BytesValue bytes = Variables.byteArrayValue(new byte[] {1}), otherBytes = bytes;
            }
            """,
            """
            import java.util.Date;

            class InitializedValues {
                private Date date = new Date(0), otherDate = date;
                private byte[] bytes = new byte[]{1}, otherBytes = bytes;
            }
            """));
  }

  @Test
  void qualifiedFieldInitializersFollowConvertedTypes() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class CopiedValues {
                private DateValue earlyCopy = this.date;
                private DateValue date = Variables.dateValue(new Date(0));
                private DateValue laterCopy = this.date;
                private BytesValue bytes = Variables.byteArrayValue(new byte[] {1});
                private BytesValue byteCopy = this.bytes;
            }
            """,
            """
            import java.util.Date;

            class CopiedValues {
                private Date earlyCopy = this.date;
                private Date date = new Date(0);
                private Date laterCopy = this.date;
                private byte[] bytes = new byte[]{1};
                private byte[] byteCopy = this.bytes;
            }
            """));
  }

  @Test
  void qualifiedTypedFieldsOnlyUnwrapConvertedValues() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            abstract class QualifiedValues {
                private DateValue date = Variables.dateValue(new Date(0));
                private BytesValue bytes = Variables.byteArrayValue(new byte[] {1});
                private DateValue untouched = loadDate();

                abstract DateValue loadDate();

                Date readDate() { return this.date.getValue(); }
                byte[] readBytes() { return this.bytes.getValue(); }
                Date readUntouched(DateValue untouched) { return this.untouched.getValue(); }
                Date readSameType(QualifiedValues other) { return other.date.getValue(); }
                Date readOtherType(RetainedValues other) { return other.date.getValue(); }
            }

            abstract class RetainedValues {
                DateValue date = loadDate();
                abstract DateValue loadDate();
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.value.DateValue;

            abstract class QualifiedValues {
                private Date date = new Date(0);
                private byte[] bytes = new byte[]{1};
                // TODO: migrate Camunda 7 typed-value initializer manually
                private DateValue untouched = loadDate();

                abstract DateValue loadDate();

                Date readDate() { return this.date; }
                byte[] readBytes() { return this.bytes; }
                Date readUntouched(Date untouched) { return this.untouched.getValue(); }
                Date readSameType(QualifiedValues other) { return other.date; }
                Date readOtherType(RetainedValues other) { return other.date.getValue(); }
            }

            abstract class RetainedValues {
                // TODO: migrate Camunda 7 typed-value initializer manually
                DateValue date = loadDate();
                abstract DateValue loadDate();
            }
            """));
  }

  @Test
  void qualifiedReadsBeforeConvertedFieldDeclarationsAreUnwrapped() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class LaterFields {
                Date date() { return this.date.getValue(); }
                byte[] bytes() { return this.bytes.getValue(); }

                private DateValue date = Variables.dateValue(new Date(0));
                private BytesValue bytes = Variables.byteArrayValue(new byte[] {1});
            }
            """,
            """
            import java.util.Date;

            class LaterFields {
                Date date() { return this.date; }
                byte[] bytes() { return this.bytes; }

                private Date date = new Date(0);
                private byte[] bytes = new byte[]{1};
            }
            """));
  }

  @Test
  void retainedLocalValueShadowsConvertedField() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            abstract class ShadowedValues {
                private DateValue date = Variables.dateValue(new Date(0));
                abstract DateValue loadDate();

                Date read() {
                    DateValue date = loadDate();
                    return date.getValue();
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.value.DateValue;

            abstract class ShadowedValues {
                private Date date = new Date(0);
                abstract DateValue loadDate();

                Date read() {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue date = loadDate();
                    return date.getValue();
                }
            }
            """));
  }

  @Test
  void laterTypedFactoryAssignmentsBecomeRawValues() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class AssignedValues {
                private DateValue date;
                private BytesValue bytes;
                private boolean transientFlag;

                void assign() {
                    this.date = Variables.dateValue(new Date(0));
                    this.bytes = Variables.byteArrayValue(new byte[] {1}, false);
                    date = Variables.dateValue(new Date(2));
                    bytes = Variables.byteArrayValue(new byte[] {3});
                    this.date = Variables.dateValue(new Date(1), true);
                    this.bytes = Variables.byteArrayValue(new byte[] {2}, transientFlag);
                }

                void assignLocal() {
                    DateValue localDate = null;
                    localDate = Variables.dateValue(new Date(3));
                    BytesValue localBytes = null;
                    localBytes = Variables.byteArrayValue(new byte[] {4}, false);
                }
            }
            """,
            """
            import java.util.Date;

            class AssignedValues {
                private Date date;
                private byte[] bytes;
                private boolean transientFlag;

                void assign() {
                    this.date = new Date(0);
                    this.bytes = new byte[]{1};
                    date = new Date(2);
                    bytes = new byte[]{3};
                    // TODO: review Camunda 7 transient variable semantics for migrated values
                    this.date = new Date(1);
                    // TODO: review Camunda 7 transient variable semantics for migrated values
                    this.bytes = new byte[]{2};
                }

                void assignLocal() {
                    Date localDate = null;
                    localDate = new Date(3);
                    byte[] localBytes = null;
                    localBytes = new byte[]{4};
                }
            }
            """));
  }

  @Test
  void retainedTypedFieldsKeepCompatibleAssignments() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            abstract class RetainedFields {
                private DateValue date = loadDate();
                private BytesValue bytes = loadBytes();

                abstract DateValue loadDate();
                abstract BytesValue loadBytes();

                void assign(DelegateExecution execution) {
                    this.date = execution.getVariableTyped("date");
                    this.bytes = execution.getVariableTyped("bytes");
                    this.date = Variables.dateValue(new Date(0));
                    this.bytes = Variables.byteArrayValue(new byte[] {1});
                    DateValue localDate = loadDate();
                    localDate = execution.getVariableTyped("localDate");
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            abstract class RetainedFields {
                // TODO: migrate Camunda 7 typed-value initializer manually
                private DateValue date = loadDate();
                // TODO: migrate Camunda 7 typed-value initializer manually
                private BytesValue bytes = loadBytes();

                abstract DateValue loadDate();
                abstract BytesValue loadBytes();

                void assign(DelegateExecution execution) {
                    this.date = execution.getVariableTyped("date");
                    this.bytes = execution.getVariableTyped("bytes");
                    this.date = Variables.dateValue(new Date(0));
                    this.bytes = Variables.byteArrayValue(new byte[] {1});
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue localDate = loadDate();
                    localDate = execution.getVariableTyped("localDate");
                }
            }
            """));
  }

  @Test
  void standaloneDateValueFieldIsConverted() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.variable.value.DateValue;

            class DateOnly {
                private DateValue date;
            }
            """,
            """
            import java.util.Date;

            class DateOnly {
                private Date date;
            }
            """));
  }

  @Test
  void nestedTypedFactoryCallsAreUnwrapped() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;

            class NestedFactories {
                void publish(DelegateExecution execution, Date date, byte[] bytes, boolean transientFlag) {
                    execution.setVariable("date", Variables.dateValue(date));
                    execution.setVariable("stableDate", Variables.dateValue(date, false));
                    execution.setVariable("transientDate", Variables.dateValue(date, true));
                    execution.setVariable("bytes", Variables.byteArrayValue(bytes, transientFlag));
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;

            class NestedFactories {
                void publish(DelegateExecution execution, Date date, byte[] bytes, boolean transientFlag) {
                    execution.setVariable("date", date);
                    execution.setVariable("stableDate", date);
                    execution.setVariable("transientDate", // TODO: review Camunda 7 transient variable semantics for migrated values
                            date);
                    execution.setVariable("bytes", // TODO: review Camunda 7 transient variable semantics for migrated values
                            bytes);
                }
            }
            """));
  }

  @Test
  void convertedDeclarationsKeepAnnotations() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.IntegerValue;
            import org.camunda.bpm.engine.variable.value.StringValue;

            class AnnotatedValues {
                @Deprecated
                private DateValue date = Variables.dateValue(new Date(0));
                @Deprecated private DateValue first, second;

                void read(DelegateExecution execution) {
                    @SuppressWarnings("unused") StringValue label = Variables.stringValue("label");
                    @SuppressWarnings("unused") final IntegerValue amount = execution.getVariableTyped("amount");
                    @SuppressWarnings("unused") IntegerValue low = execution.getVariableTyped("low"), high = execution.getVariableTyped("high");
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;

            class AnnotatedValues {
                @Deprecated
                private Date date = new Date(0);
                @Deprecated
                private Date first, second;

                void read(DelegateExecution execution) {
                    @SuppressWarnings("unused") String label = "label";
                    // please check type
                    @SuppressWarnings("unused") final Integer amount = (Integer) execution.getVariable("amount");
                    // please check type
                    @SuppressWarnings("unused") Integer low = (Integer) execution.getVariable("low"), high = (Integer) execution.getVariable("high");
                }
            }
            """));
  }

  @Test
  void objectReturningTypedGettersAreCastInDeclarations() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.TaskService;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class TaskValues {
                void read(TaskService taskService) {
                    DateValue taskDate = taskService.getVariableTyped("task", "date");
                    BytesValue first = taskService.getVariableTyped("task", "first"), second = taskService.getVariableTyped("task", "second");
                }
            }
            """,
            """
            import org.camunda.bpm.engine.TaskService;

            import java.util.Date;

            class TaskValues {
                void read(TaskService taskService) {
                    // please check type
                    Date taskDate = (Date) taskService.getVariable("task", "date");
                    // please check type
                    byte[] first = (byte[]) taskService.getVariable("task", "first"), second = (byte[]) taskService.getVariable("task", "second");
                }
            }
            """));
  }

  @Test
  void typedFactoryReceiversKeepValidJava() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;

            class FactoryReceivers {
                void read(Date date, String a, String b) {
                    Date raw = Variables.dateValue(date).getValue();
                    String joined = Variables.stringValue(a + b).getValue().trim();
                    int size = (Variables.integerValue(5)).getValue();
                    boolean transientDate = Variables.dateValue(date, true).isTransient();
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;

            class FactoryReceivers {
                void read(Date date, String a, String b) {
                    Date raw = date;
                    String joined = (a + b).trim();
                    int size = 5;
                    boolean transientDate = // TODO: migrate Camunda 7 typed-value method call manually
                            Variables.dateValue(date, true).isTransient();
                }
            }
            """));
  }

  @Test
  void typedDeclarationsWithUnprovenInitializersStayTyped() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.IntegerValue;
            import org.camunda.bpm.engine.variable.value.StringValue;

            abstract class LoadedValues {
                abstract IntegerValue loadInteger();

                abstract StringValue loadString();

                void read() {
                    IntegerValue a = loadInteger(), b = loadInteger();
                    StringValue single = loadString();
                    IntegerValue first = Variables.integerValue(1), second = Variables.integerValue(2);
                }
            }
            """,
            """
            import org.camunda.bpm.engine.variable.value.IntegerValue;
            import org.camunda.bpm.engine.variable.value.StringValue;

            abstract class LoadedValues {
                abstract IntegerValue loadInteger();

                abstract StringValue loadString();

                void read() {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    IntegerValue a = loadInteger(), b = loadInteger();
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    StringValue single = loadString();
                    Integer first = 1, second = 2;
                }
            }
            """));
  }

  @Test
  void typedValueArraysStayTyped() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.IntegerValue;

            class ArrayValues {
                DateValue dates[];
                DateValue[] javaDates = {Variables.dateValue(new Date())};

                void read(IntegerValue... values) {
                    IntegerValue local[] = null, other = null;
                    IntegerValue[] counts = new IntegerValue[] {Variables.integerValue(1)};
                    dates[0] = Variables.dateValue(null);
                    Object first = dates[0].getValue();
                    Integer second = values[0].getValue();
                    put(new DateValue[] {Variables.dateValue(null)});
                }

                void write(DelegateExecution execution, DateValue[] retained) {
                    dates[0] = execution.getVariableTyped("date");
                    retained[0] = Variables.dateValue(null);
                    read(Variables.integerValue(2));
                }

                void put(DateValue[] values) {
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.IntegerValue;

            class ArrayValues {
                // TODO: migrate Camunda 7 typed-value initializer manually
                DateValue dates[];
                // TODO: migrate Camunda 7 typed-value initializer manually
                DateValue[] javaDates = {Variables.dateValue(new Date())};

                // TODO: migrate Camunda 7 typed-value parameter manually
                void read(IntegerValue... values) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    IntegerValue local[] = null, other = null;
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    IntegerValue[] counts = new IntegerValue[] {Variables.integerValue(1)};
                    dates[0] = Variables.dateValue(null);
                    Object first = dates[0].getValue();
                    Integer second = values[0].getValue();
                    put(new DateValue[] {// TODO: migrate Camunda 7 typed-value method call manually
                            Variables.dateValue(null)});
                }

                // TODO: migrate Camunda 7 typed-value parameter manually
                void write(DelegateExecution execution, DateValue[] retained) {
                    dates[0] = execution.getVariableTyped("date");
                    retained[0] = Variables.dateValue(null);
                    read(// TODO: migrate Camunda 7 typed-value method call manually
                            Variables.integerValue(2));
                }

                // TODO: migrate Camunda 7 typed-value parameter manually
                void put(DateValue[] values) {
                }
            }
            """));
  }

  @Test
  void retainedTypedTargetsKeepLaterTypedWrites() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.IntegerValue;

            abstract class RetainedValues {
                abstract IntegerValue loadInteger();

                void read(DelegateExecution execution) {
                    IntegerValue value = loadInteger();
                    value = Variables.integerValue(1);
                    value = execution.getVariableTyped("value");
                }
            }
            """,
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.IntegerValue;

            abstract class RetainedValues {
                abstract IntegerValue loadInteger();

                void read(DelegateExecution execution) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    IntegerValue value = loadInteger();
                    value = Variables.integerValue(1);
                    value = execution.getVariableTyped("value");
                }
            }
            """));
  }

  @Test
  void typedFactoryValueReadsKeepBoxedTypes() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.variable.Variables;

            class BoxedValues {
                String read() {
                    Integer missing = Variables.integerValue(null).getValue();
                    int direct = Variables.integerValue(5).getValue();
                    System.out.println(Variables.integerValue(5).getValue());
                    return Variables.integerValue(5).getValue().toString()
                        + Variables.longValue(7L).getValue().hashCode();
                }
            }
            """,
            """
            class BoxedValues {
                String read() {
                    Integer missing = (Integer) null;
                    int direct = 5;
                    System.out.println((Integer) 5);
                    return ((Integer) 5).toString()
                        + ((Long) 7L).hashCode();
                }
            }
            """));
  }

  @Test
  void retainedDeclarationsStillRewriteNestedConvertedReads() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            abstract class NestedReads {
                abstract DateValue load(Date date);

                void read(Date date) {
                    DateValue raw = Variables.dateValue(date);
                    DateValue wrapped = load(raw.getValue());
                    wrapped = load(raw.getValue());
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.value.DateValue;

            abstract class NestedReads {
                abstract DateValue load(Date date);

                void read(Date date) {
                    Date raw = date;
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue wrapped = load(raw);
                    wrapped = load(raw);
                }
            }
            """));
  }

  @Test
  void typedDeclarationsWithUnprovenWritesStayTyped() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.variable.value.IntegerValue;

            abstract class LaterWrites {
                IntegerValue a, b;

                abstract IntegerValue loadInteger();

                void write() {
                    IntegerValue local = null;
                    local = loadInteger();
                    a = loadInteger();
                    b = null;
                }
            }
            """,
            """
            import org.camunda.bpm.engine.variable.value.IntegerValue;

            abstract class LaterWrites {
                // TODO: migrate Camunda 7 typed-value initializer manually
                IntegerValue a, b;

                abstract IntegerValue loadInteger();

                void write() {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    IntegerValue local = null;
                    local = loadInteger();
                    a = loadInteger();
                    b = null;
                }
            }
            """));
  }

  @Test
  void typedFactoriesStayTypedWhereTypedValuesAreExpected() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.VariableMap;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.IntegerValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            abstract class ExpectedTypes {
                abstract void accept(DateValue value);

                DateValue copy(Date date) {
                    return Variables.dateValue(date);
                }

                IntegerValue count() {
                    return (Variables.integerValue(1));
                }

                TypedValue pick(boolean flag, Date date) {
                    return flag ? Variables.dateValue(date) : null;
                }

                Object raw(Date date) {
                    return Variables.dateValue(date);
                }

                void write(DelegateExecution execution, Date date, byte[] bytes) {
                    accept(Variables.dateValue(date));
                    execution.setVariable("date", Variables.dateValue(date));
                    execution.setVariable("bytes", Variables.byteArrayValue(bytes));
                    VariableMap map =
                        Variables.createVariables().putValueTyped("date", Variables.dateValue(date));
                }
            }
            """,
            """
            import java.util.Date;
            import java.util.HashMap;
            import java.util.Map;

            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.IntegerValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            abstract class ExpectedTypes {
                abstract void accept(Date value);

                DateValue copy(Date date) {
                    // TODO: migrate Camunda 7 typed-value method call manually
                    return Variables.dateValue(date);
                }

                IntegerValue count() {
                    // TODO: migrate Camunda 7 typed-value method call manually
                    return (Variables.integerValue(1));
                }

                TypedValue pick(boolean flag, Date date) {
                    // TODO: migrate Camunda 7 typed-value method call manually
                    return flag ? Variables.dateValue(date) : null;
                }

                Object raw(Date date) {
                    return date;
                }

                void write(DelegateExecution execution, Date date, byte[] bytes) {
                    accept(date);
                    execution.setVariable("date", date);
                    execution.setVariable("bytes", bytes);
                    Map<String, Object> map = new HashMap<>();
                    map.put("date", date);
                }
            }
            """));
  }

  @Test
  void typedFactoriesStayTypedInSwitchAndLambdaReturns() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import java.util.function.Function;
            import java.util.function.Supplier;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class SwitchAndLambdaReturns {
                interface DateSupplier extends Supplier<DateValue> {}

                DateValue pick(int kind, Date date) {
                    return switch (kind) {
                        case 1 -> Variables.dateValue(date);
                        case 2 -> {
                            yield Variables.dateValue(date);
                        }
                        default -> null;
                    };
                }

                Object raw(int kind, Date date) {
                    return switch (kind) {
                        case 1 -> Variables.dateValue(date);
                        default -> null;
                    };
                }

                void lambdas(Date date) {
                    Supplier<DateValue> typed = () -> Variables.dateValue(date);
                    Function<Date, ? extends TypedValue> block = value -> {
                        return Variables.dateValue(value);
                    };
                    DateSupplier inherited = () -> Variables.dateValue(date);
                    Supplier<Object> untyped = () -> Variables.dateValue(date);
                }
            }
            """,
            """
            import java.util.Date;
            import java.util.function.Function;
            import java.util.function.Supplier;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class SwitchAndLambdaReturns {
                interface DateSupplier extends Supplier<DateValue> {}

                DateValue pick(int kind, Date date) {
                    // TODO: migrate Camunda 7 typed-value method call manually
                    return switch (kind) {
                        case 1 -> Variables.dateValue(date);
                        case 2 -> {
                            yield Variables.dateValue(date);
                        }
                        default -> null;
                    };
                }

                Object raw(int kind, Date date) {
                    return switch (kind) {
                        case 1 -> date;
                        default -> null;
                    };
                }

                void lambdas(Date date) {
                    Supplier<DateValue> typed = () -> // TODO: migrate Camunda 7 typed-value method call manually
                            Variables.dateValue(date);
                    Function<Date, ? extends TypedValue> block = value -> {
                        // TODO: migrate Camunda 7 typed-value method call manually
                        return Variables.dateValue(value);
                    };
                    DateSupplier inherited = () -> // TODO: migrate Camunda 7 typed-value method call manually
                            Variables.dateValue(date);
                    Supplier<Object> untyped = () -> date;
                }
            }
            """));
  }

  @Test
  void typedCastsKeepTypedFactories() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class CastValues {
                DateValue cast(Date date) {
                    return (DateValue) Variables.dateValue(date);
                }

                Object object(Date date) {
                    return (DateValue) (Variables.dateValue(date));
                }

                Object raw(Date date) {
                    return (Object) Variables.dateValue(date);
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class CastValues {
                DateValue cast(Date date) {
                    // TODO: migrate Camunda 7 typed-value method call manually
                    return (DateValue) Variables.dateValue(date);
                }

                Object object(Date date) {
                    // TODO: migrate Camunda 7 typed-value method call manually
                    return (DateValue) (Variables.dateValue(date));
                }

                Object raw(Date date) {
                    return (Object) date;
                }
            }
            """));
  }

  @Test
  void typedOnlyReadsKeepTypedDeclarations() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class TypedOnlyReads {
                private BytesValue content = Variables.byteArrayValue(new byte[0]);
                private BytesValue converted = Variables.byteArrayValue(new byte[0]);

                DateValue returned(Date date) {
                    DateValue value = Variables.dateValue(date);
                    return value;
                }

                boolean transientCheck(DelegateExecution execution) {
                    DateValue value = execution.getVariableTyped("date");
                    return value.isTransient();
                }

                Object typeCheck() {
                    return this.content.getType();
                }

                TypedValue widened(Date date) {
                    DateValue value = Variables.dateValue(date);
                    TypedValue typed = value;
                    return typed;
                }

                Object cast(Date date) {
                    DateValue value = Variables.dateValue(date);
                    return (DateValue) value;
                }

                Object raw(Date date) {
                    DateValue value = Variables.dateValue(date);
                    Object object = value;
                    return converted.getValue();
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class TypedOnlyReads {
                // TODO: migrate Camunda 7 typed-value initializer manually
                private BytesValue content = Variables.byteArrayValue(new byte[0]);
                private byte[] converted = new byte[0];

                DateValue returned(Date date) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue value = Variables.dateValue(date);
                    return value;
                }

                boolean transientCheck(DelegateExecution execution) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue value = execution.getVariableTyped("date");
                    return value.isTransient();
                }

                Object typeCheck() {
                    return this.content.getType();
                }

                TypedValue widened(Date date) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue value = Variables.dateValue(date);
                    TypedValue typed = value;
                    return typed;
                }

                Object cast(Date date) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue value = Variables.dateValue(date);
                    return (DateValue) value;
                }

                Object raw(Date date) {
                    Date value = date;
                    Object object = value;
                    return converted;
                }
            }
            """));
  }

  @Test
  void retainedAssignmentsKeepNestedTypedExpressions() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class RetainedAssignments {
                DateValue loadDate() {
                    return null;
                }

                Object reassigned(DelegateExecution execution, Date date, boolean flag) {
                    DateValue value = loadDate();
                    value = flag ? Variables.dateValue(date) : null;
                    value = flag ? execution.getVariableTyped("date") : null;
                    value = (DateValue) execution.getVariableTyped("other");
                    return value;
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class RetainedAssignments {
                DateValue loadDate() {
                    return null;
                }

                Object reassigned(DelegateExecution execution, Date date, boolean flag) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue value = loadDate();
                    value = flag ? Variables.dateValue(date) : null;
                    value = flag ? execution.getVariableTyped("date") : null;
                    value = (DateValue) execution.getVariableTyped("other");
                    return value;
                }
            }
            """));
  }

  @Test
  void genericTypedArgumentsStayTyped() {
    rewriteRun(
        java(
            """
            import java.util.ArrayList;
            import java.util.Date;
            import java.util.List;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class GenericArguments {
                List<DateValue> copy(Date date) {
                    return List.of(Variables.dateValue(date));
                }

                List<DateValue> collect(Date date) {
                    List<DateValue> values = new ArrayList<>();
                    DateValue value = Variables.dateValue(date);
                    values.add(value);
                    return values;
                }

                List<Object> raw(Date date) {
                    List<Object> values = new ArrayList<>();
                    DateValue value = Variables.dateValue(date);
                    values.add(value);
                    return values;
                }
            }
            """,
            """
            import java.util.ArrayList;
            import java.util.Date;
            import java.util.List;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class GenericArguments {
                List<DateValue> copy(Date date) {
                    return List.of(// TODO: migrate Camunda 7 typed-value method call manually
                            Variables.dateValue(date));
                }

                List<DateValue> collect(Date date) {
                    List<DateValue> values = new ArrayList<>();
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue value = Variables.dateValue(date);
                    values.add(value);
                    return values;
                }

                List<Object> raw(Date date) {
                    List<Object> values = new ArrayList<>();
                    Date value = date;
                    values.add(value);
                    return values;
                }
            }
            """));
  }

  @Test
  void valueExchangesShareTypedDecisions() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class ValueExchanges {
                private DateValue cached;

                DateValue loadDate() {
                    return null;
                }

                void cache() {
                    DateValue value = loadDate();
                    cached = value;
                }

                Object retainedSource() {
                    DateValue source = loadDate();
                    DateValue target;
                    target = source;
                    return target;
                }

                Object retainedTarget(Date date) {
                    DateValue target = loadDate();
                    DateValue source = Variables.dateValue(date);
                    target = source;
                    return target;
                }

                Object converted(Date date) {
                    DateValue first = Variables.dateValue(date);
                    DateValue second = first;
                    return second.getValue();
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class ValueExchanges {
                // TODO: migrate Camunda 7 typed-value initializer manually
                private DateValue cached;

                DateValue loadDate() {
                    return null;
                }

                void cache() {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue value = loadDate();
                    cached = value;
                }

                Object retainedSource() {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue source = loadDate();
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue target;
                    target = source;
                    return target;
                }

                Object retainedTarget(Date date) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue target = loadDate();
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue source = Variables.dateValue(date);
                    target = source;
                    return target;
                }

                Object converted(Date date) {
                    Date first = date;
                    Date second = first;
                    return second;
                }
            }
            """));
  }

  @Test
  void typedValueTargetsKeepTypedInitializers() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class TypedTargets {
                TypedValue returned(Date date) {
                    TypedValue value = Variables.dateValue(date);
                    return value;
                }

                TypedValue returnedGetter(DelegateExecution execution) {
                    TypedValue value = execution.getVariableTyped("date");
                    return value;
                }

                void reassigned(DelegateExecution execution, Date date) {
                    TypedValue value = Variables.dateValue(date);
                    value = Variables.stringValue("text");
                    execution.setVariable("value", value);
                }

                void raw(DelegateExecution execution, Date date) {
                    TypedValue value = Variables.dateValue(date);
                    execution.setVariable("date", value);
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class TypedTargets {
                TypedValue returned(Date date) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    TypedValue value = Variables.dateValue(date);
                    return value;
                }

                TypedValue returnedGetter(DelegateExecution execution) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    TypedValue value = execution.getVariableTyped("date");
                    return value;
                }

                void reassigned(DelegateExecution execution, Date date) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    TypedValue value = Variables.dateValue(date);
                    value = Variables.stringValue("text");
                    execution.setVariable("value", value);
                }

                void raw(DelegateExecution execution, Date date) {
                    Date value = date;
                    execution.setVariable("date", value);
                }
            }
            """));
  }

  @Test
  void typedGettersStayTypedInTypedContexts() {
    rewriteRun(
        java(
            """
            import java.util.function.Supplier;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class TypedGetterReturns {
                DateValue copy(DelegateExecution execution) {
                    return execution.getVariableTyped("date");
                }

                Supplier<DateValue> supplier(DelegateExecution execution) {
                    return () -> execution.getVariableLocalTyped("date");
                }

                Object raw(DelegateExecution execution) {
                    return execution.getVariableTyped("date");
                }
            }
            """,
            """
            import java.util.function.Supplier;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class TypedGetterReturns {
                DateValue copy(DelegateExecution execution) {
                    // TODO: migrate Camunda 7 typed-value method call manually
                    return execution.getVariableTyped("date");
                }

                Supplier<DateValue> supplier(DelegateExecution execution) {
                    return () -> // TODO: migrate Camunda 7 typed-value method call manually
                            execution.getVariableLocalTyped("date");
                }

                Object raw(DelegateExecution execution) {
                    return execution.getVariable("date");
                }
            }
            """));
  }

  @Test
  void typedValueMethodReferencesStayTyped() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import java.util.function.Supplier;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class TypedReferences {
                Supplier<Date> getter(Date date) {
                    DateValue value = Variables.dateValue(date);
                    return value::getValue;
                }

                Supplier<Date> factory(Date date) {
                    return Variables.dateValue(date)::getValue;
                }

                Object converted(Date date) {
                    DateValue value = Variables.dateValue(date);
                    return value.getValue();
                }
            }
            """,
            """
            import java.util.Date;
            import java.util.function.Supplier;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class TypedReferences {
                Supplier<Date> getter(Date date) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue value = Variables.dateValue(date);
                    return value::getValue;
                }

                Supplier<Date> factory(Date date) {
                    return // TODO: migrate Camunda 7 typed-value method call manually
                            Variables.dateValue(date)::getValue;
                }

                Object converted(Date date) {
                    Date value = date;
                    return value;
                }
            }
            """));
  }

  @Test
  void fieldsAccessedFromOtherClassesStayTyped() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class Holder {
                DateValue shared = Variables.dateValue(new Date());
                DateValue own = Variables.dateValue(new Date());

                Date read(Holder other) {
                    return other.own.getValue();
                }
            }

            class Reader {
                Date read(Holder holder) {
                    return holder.shared.getValue();
                }
            }

            class Child extends Holder {
                Date inherited() {
                    return shared.getValue();
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class Holder {
                // TODO: migrate Camunda 7 typed-value initializer manually
                DateValue shared = Variables.dateValue(new Date());
                Date own = new Date();

                Date read(Holder other) {
                    return other.own;
                }
            }

            class Reader {
                Date read(Holder holder) {
                    return holder.shared.getValue();
                }
            }

            class Child extends Holder {
                Date inherited() {
                    return shared.getValue();
                }
            }
            """));
  }

  @Test
  void typedInstanceofChecksKeepTypedValues() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.IntegerValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class TypedChecks {
                boolean typed() {
                    IntegerValue value = Variables.integerValue(1);
                    return value instanceof TypedValue;
                }

                boolean raw() {
                    IntegerValue value = Variables.integerValue(1);
                    return value.getValue() instanceof Integer;
                }
            }
            """,
            """
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.IntegerValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class TypedChecks {
                boolean typed() {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    IntegerValue value = Variables.integerValue(1);
                    return value instanceof TypedValue;
                }

                boolean raw() {
                    Integer value = 1;
                    return value instanceof Integer;
                }
            }
            """));
  }

  @Test
  void retypedTypedValueFieldsConvertAllReads() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class RetypedFields {
                Object early() {
                    return value.getValue();
                }

                private TypedValue value = Variables.dateValue(new Date());

                Object qualified() {
                    return this.value.getValue();
                }
            }
            """,
            """
            import java.util.Date;

            class RetypedFields {
                Object early() {
                    return value;
                }

                private Date value = new Date();

                Object qualified() {
                    return this.value;
                }
            }
            """));
  }

  @Test
  void groupedTypedValueDeclarationsStayTyped() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class GroupedTypedValues {
                void store(Date first, Date second) {
                    TypedValue a = Variables.dateValue(first), b = Variables.dateValue(second);
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class GroupedTypedValues {
                void store(Date first, Date second) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    TypedValue a = Variables.dateValue(first), b = Variables.dateValue(second);
                }
            }
            """));
  }

  @Test
  void lowerBoundedTypedConsumersStayTyped() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import java.util.List;
            import java.util.function.Consumer;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class LowerBounded {
                void accept(Consumer<? super DateValue> sink, Date date) {
                    sink.accept(Variables.dateValue(date));
                }

                void add(List<? super DateValue> values, Date date) {
                    DateValue value = Variables.dateValue(date);
                    values.add(value);
                }
            }
            """,
            """
            import java.util.Date;
            import java.util.List;
            import java.util.function.Consumer;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class LowerBounded {
                void accept(Consumer<? super DateValue> sink, Date date) {
                    sink.accept(// TODO: migrate Camunda 7 typed-value method call manually
                            Variables.dateValue(date));
                }

                void add(List<? super DateValue> values, Date date) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue value = Variables.dateValue(date);
                    values.add(value);
                }
            }
            """));
  }

  @Test
  void typedValueTargetsWithoutTypedInitializersKeepTypedWrites() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class UninitializedTargets {
                TypedValue field;

                void store(DelegateExecution execution, Date date) {
                    TypedValue local;
                    local = Variables.dateValue(date);
                    TypedValue empty = null;
                    empty = execution.getVariableTyped("date");
                    field = Variables.dateValue(date);
                }

                void update(TypedValue parameter, Date date) {
                    parameter = Variables.dateValue(date);
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class UninitializedTargets {
                // TODO: migrate Camunda 7 typed-value initializer manually
                TypedValue field;

                void store(DelegateExecution execution, Date date) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    TypedValue local;
                    local = Variables.dateValue(date);
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    TypedValue empty = null;
                    empty = execution.getVariableTyped("date");
                    field = Variables.dateValue(date);
                }

                // TODO: migrate Camunda 7 typed-value parameter manually
                void update(TypedValue parameter, Date date) {
                    parameter = Variables.dateValue(date);
                }
            }
            """));
  }

  @Test
  void typedReceiversThroughTernariesStayTyped() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class TernaryReceivers {
                Object read(DelegateExecution e, boolean f, Date a, Date b) {
                    Object one = (f ? Variables.dateValue(a) : Variables.dateValue(b)).getValue();
                    Object two = (f ? e.getVariableTyped("a") : e.getVariableTyped("b")).getValue();
                    DateValue left = Variables.dateValue(a);
                    DateValue right = Variables.dateValue(b);
                    Object three = (f ? left : right).getValue();
                    return one;
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class TernaryReceivers {
                Object read(DelegateExecution e, boolean f, Date a, Date b) {
                    Object one = // TODO: migrate Camunda 7 typed-value method call manually
                            (f ? Variables.dateValue(a) : Variables.dateValue(b)).getValue();
                    Object two = // TODO: migrate Camunda 7 typed-value method call manually
                            (f ? e.getVariableTyped("a") : e.getVariableTyped("b")).getValue();
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue left = Variables.dateValue(a);
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue right = Variables.dateValue(b);
                    Object three = (f ? left : right).getValue();
                    return one;
                }
            }
            """));
  }

  @Test
  void boundedTypeVariablesStayTyped() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class BoundedTypes {
                <T extends TypedValue> T copy(DelegateExecution execution) {
                    return execution.getVariableTyped("x");
                }

                <T extends DateValue> T date(DelegateExecution execution) {
                    T value = execution.getVariableTyped("date");
                    return value;
                }

                <T extends TypedValue> T assign(DelegateExecution execution, T target) {
                    target = execution.getVariableTyped("x");
                    return target;
                }

                <T extends DateValue> T cast(Date date) {
                    DateValue value = Variables.dateValue(date);
                    return (T) value;
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class BoundedTypes {
                <T extends TypedValue> T copy(DelegateExecution execution) {
                    // TODO: migrate Camunda 7 typed-value method call manually
                    return execution.getVariableTyped("x");
                }

                <T extends DateValue> T date(DelegateExecution execution) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    T value = execution.getVariableTyped("date");
                    return value;
                }

                // TODO: migrate Camunda 7 typed-value parameter manually
                <T extends TypedValue> T assign(DelegateExecution execution, T target) {
                    target = execution.getVariableTyped("x");
                    return target;
                }

                <T extends DateValue> T cast(Date date) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    DateValue value = Variables.dateValue(date);
                    return (T) value;
                }
            }
            """));
  }

  @Test
  void switchTypedValueInitializersStayTyped() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class SwitchTargets {
                void declared(DelegateExecution execution, int kind, Date date) {
                    TypedValue value = switch (kind) {
                        case 1 -> Variables.dateValue(date);
                        default -> null;
                    };
                    execution.setVariable("value", value);
                }

                void yielded(DelegateExecution execution, int kind, Date date) {
                    TypedValue value = switch (kind) {
                        case 1 -> {
                            yield Variables.dateValue(date);
                        }
                        default -> null;
                    };
                    execution.setVariable("value", value);
                }

                void assigned(DelegateExecution execution, int kind, Date date) {
                    TypedValue value = null;
                    value = switch (kind) {
                        case 1 -> Variables.dateValue(date);
                        default -> null;
                    };
                    execution.setVariable("value", value);
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class SwitchTargets {
                void declared(DelegateExecution execution, int kind, Date date) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    TypedValue value = switch (kind) {
                        case 1 -> Variables.dateValue(date);
                        default -> null;
                    };
                    execution.setVariable("value", value);
                }

                void yielded(DelegateExecution execution, int kind, Date date) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    TypedValue value = switch (kind) {
                        case 1 -> {
                            yield Variables.dateValue(date);
                        }
                        default -> null;
                    };
                    execution.setVariable("value", value);
                }

                void assigned(DelegateExecution execution, int kind, Date date) {
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    TypedValue value = null;
                    value = switch (kind) {
                        case 1 -> Variables.dateValue(date);
                        default -> null;
                    };
                    execution.setVariable("value", value);
                }
            }
            """));
  }

  @Test
  void typedValueByteArrayFactoriesUsePrimitiveArrays() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class ByteTargets {
                void declared(DelegateExecution execution, byte[] bytes) {
                    TypedValue value = Variables.byteArrayValue(bytes);
                    execution.setVariable("bytes", value);
                }

                void inline(DelegateExecution execution, byte[] bytes) {
                    execution.setVariable("bytes", Variables.byteArrayValue(bytes));
                }
            }
            """,
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;

            class ByteTargets {
                void declared(DelegateExecution execution, byte[] bytes) {
                    byte[] value = bytes;
                    execution.setVariable("bytes", value);
                }

                void inline(DelegateExecution execution, byte[] bytes) {
                    execution.setVariable("bytes", bytes);
                }
            }
            """));
  }

  @Test
  void typedFieldsReadFromOtherFilesStayTyped() {
    rewriteRun(
        java(
            """
            package sample;

            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            public class Holder {
                public DateValue value = Variables.dateValue(new Date());
                protected DateValue inherited = Variables.dateValue(new Date());
                DateValue written = Variables.dateValue(new Date());
                private DateValue own = Variables.dateValue(new Date());

                Date ownDate() {
                    return own.getValue();
                }
            }
            """,
            """
            package sample;

            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            public class Holder {
                // TODO: migrate Camunda 7 typed-value initializer manually
                public DateValue value = Variables.dateValue(new Date());
                // TODO: migrate Camunda 7 typed-value initializer manually
                protected DateValue inherited = Variables.dateValue(new Date());
                // TODO: migrate Camunda 7 typed-value initializer manually
                DateValue written = Variables.dateValue(new Date());
                private Date own = new Date();

                Date ownDate() {
                    return own;
                }
            }
            """),
        java(
            """
            package sample;

            import java.util.Date;

            class Reader {
                Date read(Holder holder) {
                    return holder.value.getValue();
                }
            }
            """),
        java(
            """
            package sample;

            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;

            class Writer {
                void write(Holder holder, Date date) {
                    holder.written = Variables.dateValue(date);
                }
            }
            """),
        java(
            """
            package sample;

            import java.util.Date;

            class Child extends Holder {
                Date inherited() {
                    return inherited.getValue();
                }
            }
            """));
  }

  @Test
  void typeAnnotatedDeclarationsStayValid() {
    rewriteRun(
        java(
            """
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            @Target(ElementType.TYPE_USE)
            @interface Tag {}
            """),
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class Annotated {
                @Tag DateValue field = Variables.dateValue(new Date());
                private @Tag DateValue other = Variables.dateValue(new Date());

                Date read(DelegateExecution execution, Date date) {
                    @Tag DateValue local = Variables.dateValue(date);
                    final @Tag DateValue fetched = execution.getVariableTyped("date");
                    org.camunda.bpm.engine.variable.value.DateValue qualified =
                            Variables.dateValue(date);
                    return field.getValue();
                }

                Date other() {
                    return other.getValue();
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class Annotated {
                @Tag
                Date field = new Date();
                // TODO: migrate Camunda 7 typed-value initializer manually
                private @Tag DateValue other = Variables.dateValue(new Date());

                Date read(DelegateExecution execution, Date date) {
                    @Tag Date local = date;
                    // TODO: migrate Camunda 7 typed-value initializer manually
                    final @Tag DateValue fetched = execution.getVariableTyped("date");
                    Date qualified = date;
                    return field;
                }

                Date other() {
                    return other.getValue();
                }
            }
            """));
  }

  @Test
  void typedLambdaAndLoopParametersShadowConvertedFields() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import java.util.List;
            import java.util.function.Consumer;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.BytesValue;

            class TypedCallbacks {
                DateValue date = Variables.dateValue(new Date(0));
                BytesValue bytes = Variables.byteArrayValue(new byte[]{1});

                void process(List<DateValue> values, List<BytesValue> buffers) {
                    Consumer<DateValue> callback = (DateValue date) -> { Date raw = date.getValue(); };
                    Consumer<BytesValue> bytesCallback = (BytesValue bytes) -> { byte[] raw = bytes.getValue(); };
                    for (DateValue date : values) { Date raw = date.getValue(); }
                    for (BytesValue bytes : buffers) { byte[] raw = bytes.getValue(); }
                }
            }
            """,
            """
            import java.util.Date;
            import java.util.List;
            import java.util.function.Consumer;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.BytesValue;

            class TypedCallbacks {
                Date date = new Date(0);
                byte[] bytes = new byte[]{1};

                void process(List<DateValue> values, List<BytesValue> buffers) {
                    Consumer<DateValue> callback = (DateValue date) -> { Date raw = date.getValue(); };
                    Consumer<BytesValue> bytesCallback = (BytesValue bytes) -> { byte[] raw = bytes.getValue(); };
                    for (DateValue date : values) { Date raw = date.getValue(); }
                    for (BytesValue bytes : buffers) { byte[] raw = bytes.getValue(); }
                }
            }
            """));
  }

  @Test
  void anonymousAndQualifiedTypedFieldsKeepTheirUsesAssignable() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class ScopedFields {
                Runnable task(DelegateExecution execution) {
                    return new Runnable() {
                        private DateValue date = Variables.dateValue(new Date(0));
                        private org.camunda.bpm.engine.variable.value.BytesValue bytes = Variables.byteArrayValue(new byte[]{1});

                        public void run() {
                            this.date = execution.getVariableTyped("date");
                            this.bytes = execution.getVariableTyped("bytes");
                            Date readDate = this.date.getValue();
                            byte[] readBytes = this.bytes.getValue();
                        }
                    };
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;

            class ScopedFields {
                Runnable task(DelegateExecution execution) {
                    return new Runnable() {
                        private Date date = new Date(0);
                        private byte[] bytes = new byte[]{1};

                        public void run() {
                            this.date = (Date) execution.getVariable("date");
                            this.bytes = (byte[]) execution.getVariable("bytes");
                            Date readDate = this.date;
                            byte[] readBytes = this.bytes;
                        }
                    };
                }
            }
            """));
  }

  @Test
  void typedStringNullReadsKeepStringType() {
    rewriteRun(
        java(
            """
            import org.camunda.bpm.engine.variable.Variables;

            class StringNulls {
                void print(String value) {}

                void print(Object value) {}

                String read() {
                    String missing = Variables.stringValue(null).getValue();
                    print(Variables.stringValue(null).getValue());
                    String name = Variables.stringValue("name").getValue().trim();
                    return Variables.stringValue(null).getValue().trim();
                }
            }
            """,
            """
            class StringNulls {
                void print(String value) {}

                void print(Object value) {}

                String read() {
                    String missing = (String) null;
                    print((String) null);
                    String name = "name".trim();
                    return ((String) null).trim();
                }
            }
            """));
  }

  @Test
  void mixedGroupedTypedGettersConvertInAnyOrder() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class MixedGroups {
                DelegateExecution execution;
                Date now;
                DateValue first = execution.getVariableTyped("f"), last = Variables.dateValue(now);

                void read(Date date) {
                    DateValue a = execution.getVariableTyped("a"), b = Variables.dateValue(date);
                    DateValue c = Variables.dateValue(date), d = execution.getVariableTyped("d");
                    Date values = a.getValue();
                    values = b.getValue();
                    values = c.getValue();
                    values = d.getValue();
                    values = first.getValue();
                    values = last.getValue();
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;

            class MixedGroups {
                DelegateExecution execution;
                Date now;
                Date first = (Date) execution.getVariable("f"), last = now;

                void read(Date date) {
                    Date a = (Date) execution.getVariable("a"), b = date;
                    Date c = date, d = (Date) execution.getVariable("d");
                    Date values = a;
                    values = b;
                    values = c;
                    values = d;
                    values = first;
                    values = last;
                }
            }
            """));
  }
}
