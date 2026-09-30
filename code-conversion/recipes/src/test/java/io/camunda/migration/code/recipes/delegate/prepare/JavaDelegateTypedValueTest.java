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
  void nonDelegateTypedGetterDeclarationsKeepEveryCast() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.client.task.ExternalTask;
            import org.camunda.bpm.engine.TaskService;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class OtherGetterReads {
                void read(ExternalTask externalTask, TaskService taskService) {
                    DateValue fromExternal = externalTask.getVariableTyped("date"), fromTask = taskService.getVariableTyped("task", "date");
                    BytesValue bytes = taskService.getVariableTyped("task", "bytes");
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.client.task.ExternalTask;
            import org.camunda.bpm.engine.TaskService;

            class OtherGetterReads {
                void read(ExternalTask externalTask, TaskService taskService) {
                    // please check type
                    Date fromExternal = (Date) externalTask.getVariable("date"), fromTask = (Date) taskService.getVariable("task", "date");
                    // please check type
                    byte[] bytes = (byte[]) taskService.getVariable("task", "bytes");
                }
            }
            """));
  }

  @Test
  void mixedTypedGetterDeclarationsRetainEachInitializer() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;

            class MixedValues {
                void read(DelegateExecution execution) {
                    DateValue first = Variables.dateValue(new Date(0)), second = execution.getVariableTyped("second");
                    DateValue empty = null, later = execution.getVariableTyped("later");
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;

            class MixedValues {
                void read(DelegateExecution execution) {
                    // please check type
                    Date first = new Date(0), second = (Date) execution.getVariable("second");
                    // please check type
                    Date empty = null, later = (Date) execution.getVariable("later");
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
                    (this.date) = execution.getVariableTyped("wrappedDate");
                    (bytes) = execution.getVariableTyped("wrappedBytes");
                }
            }
            """,
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.delegate.JavaDelegate;
            import org.camunda.bpm.engine.variable.value.ObjectValue;

            import java.util.Date;

            public class TypedFieldDelegate implements JavaDelegate {
                Integer amount;
                Date date;
                byte[] bytes;
                ObjectValue object;

                @Override
                public void execute(DelegateExecution execution) {
                    this.amount = (Integer) execution.getVariable("amount");
                    this.date = (Date) execution.getVariable("date");
                    this.bytes = (byte[]) execution.getVariable("bytes");
                    this.object = execution.getVariableTyped("object");
                    this.date = (Date) execution.getVariable("wrappedDate");
                    bytes = (byte[]) execution.getVariable("wrappedBytes");
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
                // TODO: migrate Camunda 7 typed-value declaration manually
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
                @Deprecated
                private DateValue date = Variables.dateValue(new Date(0)), otherDate = Variables.dateValue(new Date(1));
                private BytesValue bytes = Variables.byteArrayValue(new byte[] {1}), otherBytes = Variables.byteArrayValue(new byte[] {2});
                private DateValue wrappedDate = (Variables.dateValue(new Date(2)));
                private BytesValue wrappedBytes = ((Variables.byteArrayValue(new byte[] {3}, false)));
            }
            """,
            """
            import java.util.Date;

            class InitializedValues {
                @Deprecated
                private Date date = new Date(0), otherDate = new Date(1);
                private byte[] bytes = new byte[]{1}, otherBytes = new byte[]{2};
                private Date wrappedDate = new Date(2);
                private byte[] wrappedBytes = new byte[]{3};
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

            @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE)
            @interface Tag {}

            abstract class InitializedValues {
                abstract DateValue loadDate();
                abstract BytesValue loadBytes();

                private DateValue date = loadDate();
                private BytesValue bytes = loadBytes();
                private @Tag DateValue annotated;
            }
            """,
            """
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE)
            @interface Tag {}

            abstract class InitializedValues {
                abstract DateValue loadDate();
                abstract BytesValue loadBytes();

                // TODO: migrate Camunda 7 typed-value declaration manually
                private DateValue date = loadDate();
                // TODO: migrate Camunda 7 typed-value declaration manually
                private BytesValue bytes = loadBytes();
                // TODO: migrate Camunda 7 typed-value declaration manually
                private @Tag DateValue annotated;
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
                // TODO: migrate Camunda 7 typed-value declaration manually
                private DateValue untouched = loadDate();

                abstract DateValue loadDate();

                Date readDate() { return this.date; }
                byte[] readBytes() { return this.bytes; }
                Date readUntouched(DateValue untouched) { return this.untouched.getValue(); }
                Date readSameType(QualifiedValues other) { return other.date; }
                Date readOtherType(RetainedValues other) { return other.date.getValue(); }
            }

            abstract class RetainedValues {
                // TODO: migrate Camunda 7 typed-value declaration manually
                DateValue date = loadDate();
                abstract DateValue loadDate();
            }
            """));
  }

  @Test
  void inheritedTypedFieldsFollowConvertedSuperclassDeclarations() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            abstract class EarlyChild extends BaseValues {
                Date readDate() { return this.date.getValue(); }
                byte[] readBytes() { return this.bytes.getValue(); }
                Date readRetained() { return this.untouched.getValue(); }

                void update(DelegateExecution execution) {
                    this.date = execution.getVariableTyped("date");
                    this.bytes = execution.getVariableTyped("bytes");
                    this.untouched = execution.getVariableTyped("untouched");
                }
            }

            abstract class BaseValues {
                protected DateValue date = Variables.dateValue(new Date(0));
                protected BytesValue bytes = Variables.byteArrayValue(new byte[] {1});
                protected DateValue untouched = loadDate();
                abstract DateValue loadDate();
            }

            abstract class LateChild extends BaseValues {
                Date readDate() { return this.date.getValue(); }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.value.DateValue;

            abstract class EarlyChild extends BaseValues {
                Date readDate() { return this.date; }
                byte[] readBytes() { return this.bytes; }
                Date readRetained() { return this.untouched.getValue(); }

                void update(DelegateExecution execution) {
                    this.date = (Date) execution.getVariable("date");
                    this.bytes = (byte[]) execution.getVariable("bytes");
                    this.untouched = execution.getVariableTyped("untouched");
                }
            }

            abstract class BaseValues {
                protected Date date = new Date(0);
                protected byte[] bytes = new byte[]{1};
                // TODO: migrate Camunda 7 typed-value declaration manually
                protected DateValue untouched = loadDate();
                abstract DateValue loadDate();
            }

            abstract class LateChild extends BaseValues {
                Date readDate() { return this.date; }
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
                    // TODO: migrate Camunda 7 typed-value declaration manually
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

                void assign(DelegateExecution execution, boolean condition) {
                    this.date = execution.getVariableTyped("date");
                    this.bytes = execution.getVariableTyped("bytes");
                    this.date = Variables.dateValue(new Date(0));
                    this.bytes = Variables.byteArrayValue(new byte[] {1});
                    this.date = (DateValue) Variables.dateValue(new Date(1));
                    this.bytes = (BytesValue) execution.getVariableTyped("bytes");
                    this.date = condition ? Variables.dateValue(new Date(2)) : Variables.dateValue(new Date(3));
                    this.date = choose(Variables.dateValue(new Date(4)));
                    DateValue localDate = loadDate();
                    localDate = execution.getVariableTyped("localDate");
                }

                abstract DateValue choose(DateValue value);
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.DateValue;

            abstract class RetainedFields {
                // TODO: migrate Camunda 7 typed-value declaration manually
                private DateValue date = loadDate();
                // TODO: migrate Camunda 7 typed-value declaration manually
                private BytesValue bytes = loadBytes();

                abstract DateValue loadDate();
                abstract BytesValue loadBytes();

                void assign(DelegateExecution execution, boolean condition) {
                    this.date = execution.getVariableTyped("date");
                    this.bytes = execution.getVariableTyped("bytes");
                    this.date = Variables.dateValue(new Date(0));
                    this.bytes = Variables.byteArrayValue(new byte[] {1});
                    this.date = (DateValue) Variables.dateValue(new Date(1));
                    this.bytes = (BytesValue) execution.getVariableTyped("bytes");
                    this.date = condition ? Variables.dateValue(new Date(2)) : Variables.dateValue(new Date(3));
                    this.date = choose(Variables.dateValue(new Date(4)));
                    // TODO: migrate Camunda 7 typed-value declaration manually
                    DateValue localDate = loadDate();
                    localDate = execution.getVariableTyped("localDate");
                }

                abstract DateValue choose(DateValue value);
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
  void unknownAndCastedTypedAssignmentsRemainAssignable() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.BytesValue;

            abstract class AssignedFields {
                private DateValue date;
                private BytesValue bytes;

                abstract DateValue loadDate();
                abstract BytesValue loadBytes();

                void assign(DelegateExecution execution) {
                    this.date = loadDate();
                    this.bytes = loadBytes();
                    DateValue fromDate = loadDate();
                    this.date = fromDate;
                    this.date = (DateValue) Variables.dateValue(new Date(1));
                    this.date = (DateValue) Variables.dateValue(new Date(2), true);
                    this.bytes = (BytesValue) execution.getVariableTyped("bytes");
                    Date readDate = this.date.getValue();
                    byte[] readBytes = this.bytes.getValue();
                }

                void assignLocal() {
                    DateValue local = null, alias = Variables.dateValue(new Date(3));
                    local = alias;
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.BytesValue;

            abstract class AssignedFields {
                // TODO: migrate Camunda 7 typed-value declaration manually
                private DateValue date;
                // TODO: migrate Camunda 7 typed-value declaration manually
                private BytesValue bytes;

                abstract DateValue loadDate();
                abstract BytesValue loadBytes();

                void assign(DelegateExecution execution) {
                    this.date = loadDate();
                    this.bytes = loadBytes();
                    // TODO: migrate Camunda 7 typed-value declaration manually
                    DateValue fromDate = loadDate();
                    this.date = fromDate;
                    this.date = (DateValue) Variables.dateValue(new Date(1));
                    this.date = (DateValue) Variables.dateValue(new Date(2), true);
                    this.bytes = (BytesValue) execution.getVariableTyped("bytes");
                    Date readDate = this.date.getValue();
                    byte[] readBytes = this.bytes.getValue();
                }

                void assignLocal() {
                    // TODO: migrate Camunda 7 typed-value declaration manually
                    DateValue local = null, alias = Variables.dateValue(new Date(3));
                    local = alias;
                }
            }
            """));
  }

  @Test
  void nestedTypedFactoriesKeepRawValues() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;
            import org.camunda.bpm.engine.variable.Variables;

            class TypedVariables {
                void publish(DelegateExecution execution, boolean transientFlag) {
                    execution.setVariable("date", Variables.dateValue(new Date(0)));
                    execution.setVariable("dateFalse", Variables.dateValue(new Date(1), false));
                    execution.setVariable("dateTransient", Variables.dateValue(new Date(2), true));
                    execution.setVariable("dateComputed", Variables.dateValue(new Date(3), transientFlag));
                    execution.setVariable("one", Variables.byteArrayValue(new byte[]{1}));
                    execution.setVariable("two", Variables.byteArrayValue(new byte[]{2}, false));
                    execution.setVariable("transient", Variables.byteArrayValue(new byte[]{3}, true));
                    execution.setVariable("computed", Variables.byteArrayValue(new byte[]{4}, transientFlag));
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.delegate.DelegateExecution;

            class TypedVariables {
                void publish(DelegateExecution execution, boolean transientFlag) {
                    execution.setVariable("date", new Date(0));
                    execution.setVariable("dateFalse", new Date(1));
                    execution.setVariable("dateTransient", // TODO: review Camunda 7 transient variable semantics for migrated values
                            new Date(2));
                    execution.setVariable("dateComputed", // TODO: review Camunda 7 transient variable semantics for migrated values
                            new Date(3));
                    execution.setVariable("one", new byte[]{1});
                    execution.setVariable("two", new byte[]{2});
                    execution.setVariable("transient", // TODO: review Camunda 7 transient variable semantics for migrated values
                            new byte[]{3});
                    execution.setVariable("computed", // TODO: review Camunda 7 transient variable semantics for migrated values
                            new byte[]{4});
                }
            }
            """));
  }

  @Test
  void typedFactoryConsumersStayTypedForManualMigration() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class TypedConsumers {
                private DateValue stored = Variables.dateValue(new Date(3));
                private BytesValue saved = Variables.byteArrayValue(new byte[]{3});

                DateValue date() {
                    return Variables.dateValue(new Date(0));
                }

                BytesValue bytes() {
                    return Variables.byteArrayValue(new byte[]{1});
                }

                void consume(TypedValue value) {}
                void consumeDate(DateValue value) {}
                void update(DateValue stored, BytesValue saved) {
                    stored = Variables.dateValue(new Date(4));
                    saved = Variables.byteArrayValue(new byte[]{4});
                }

                void call() {
                    consume(Variables.dateValue(new Date(1), true));
                    consumeDate(Variables.dateValue(new Date(2)));
                }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.BytesValue;
            import org.camunda.bpm.engine.variable.value.TypedValue;

            class TypedConsumers {
                private Date stored = new Date(3);
                private byte[] saved = new byte[]{3};

                DateValue date() {
                    return // TODO: migrate Camunda 7 typed-value factory call manually
                            Variables.dateValue(new Date(0));
                }

                BytesValue bytes() {
                    return // TODO: migrate Camunda 7 typed-value factory call manually
                            Variables.byteArrayValue(new byte[]{1});
                }

                void consume(TypedValue value) {}
                void consumeDate(DateValue value) {}
                void update(DateValue stored, BytesValue saved) {
                    stored = Variables.dateValue(new Date(4));
                    saved = Variables.byteArrayValue(new byte[]{4});
                }

                void call() {
                    consume(// TODO: migrate Camunda 7 typed-value factory call manually
                            Variables.dateValue(new Date(1), true));
                    consumeDate(// TODO: migrate Camunda 7 typed-value factory call manually
                            Variables.dateValue(new Date(2)));
                }
            }
            """));
  }

  @Test
  void typedFieldConsumersStayTypedForManualMigration() {
    rewriteRun(
        java(
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.BytesValue;

            class ExposedValues {
                private DateValue date = Variables.dateValue(new Date(0));
                private BytesValue bytes = Variables.byteArrayValue(new byte[]{1});

                DateValue getDate() { return date; }
                BytesValue getBytes() { return (this.bytes); }
                void consumeDate(DateValue value) {}
                void passDate() { consumeDate(this.date); }
            }
            """,
            """
            import java.util.Date;
            import org.camunda.bpm.engine.variable.Variables;
            import org.camunda.bpm.engine.variable.value.DateValue;
            import org.camunda.bpm.engine.variable.value.BytesValue;

            class ExposedValues {
                // TODO: migrate Camunda 7 typed-value declaration manually
                private DateValue date = Variables.dateValue(new Date(0));
                // TODO: migrate Camunda 7 typed-value declaration manually
                private BytesValue bytes = Variables.byteArrayValue(new byte[]{1});

                DateValue getDate() { return date; }
                BytesValue getBytes() { return (this.bytes); }
                void consumeDate(DateValue value) {}
                void passDate() { consumeDate(this.date); }
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
