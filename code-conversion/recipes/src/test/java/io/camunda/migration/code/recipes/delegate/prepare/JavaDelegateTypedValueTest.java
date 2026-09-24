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
                private DateValue date = Variables.dateValue(new Date(0)), anotherDate = Variables.dateValue(new Date(1));
                private BytesValue bytes = Variables.byteArrayValue(new byte[] {1}), anotherBytes = Variables.byteArrayValue(new byte[] {2});
            }
            """,
            """
            import java.util.Date;

            class InitializedValues {
                private Date date = new Date(0), anotherDate = new Date(1);
                private byte[] bytes = new byte[]{1}, anotherBytes = new byte[]{2};
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
}
