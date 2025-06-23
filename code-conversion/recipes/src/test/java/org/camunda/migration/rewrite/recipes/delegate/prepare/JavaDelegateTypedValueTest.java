package org.camunda.migration.rewrite.recipes.delegate.prepare;

import static org.openrewrite.java.Assertions.java;

import org.camunda.migration.rewrite.recipes.sharedRecipes.ReplaceTypedValueAPIRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class JavaDelegateTypedValueTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipes(new ReplaceTypedValueAPIRecipe(), new DelegateReplaceTypedValueAPIRecipe())
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
import org.springframework.stereotype.Component;

@Component
public class RetrievePaymentAdapterProcessVariablesTypedValueAPI implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        IntegerValue typedAmount = execution.getVariableTyped("amount");
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
import org.camunda.bpm.engine.variable.value.IntegerValue;
import org.camunda.bpm.engine.variable.value.StringValue;
import org.springframework.stereotype.Component;

@Component
public class RetrievePaymentAdapterProcessVariablesTypedValueAPI implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        int typedAmount = execution.getVariable("amount");
        int amount = typedAmount;
        // do something...
        String typedTransactionId = "TX12345";
        execution.setVariable("transactionId", typedTransactionId);
    }
}
"""));
  }
}
