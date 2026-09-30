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
                ObjectValue object = execution.getVariableTyped("object");
                TypedValue typed = execution.getVariableTyped("typed");

                Object readObject() { return object.getValue(); }
                Object readTyped() { return typed.getValue(); }
            }
            """,
            """
            import org.camunda.bpm.engine.delegate.DelegateExecution;

            class OtherValues {
                DelegateExecution execution;
                // please check type
                Object object = execution.getVariable("object");
                // please check type
                Object typed = execution.getVariable("typed");

                Object readObject() { return object; }
                Object readTyped() { return typed; }
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
}
