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
