package de.ilume.jens.delegates;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.spin.json.SpinJsonNode;

import static org.camunda.spin.Spin.JSON;

public class SampleSpinJsonDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        // the following 2 lines should be transformed to
        // final Bar bar = (Bar.class) execution.getVariable("jsonVar");
        final SpinJsonNode jsonVarInput = (SpinJsonNode) execution.getVariable("jsonVar");
        final Bar bar = jsonVarInput.mapTo(Bar.class);
        
        Foo foo = new Foo(bar.bar1(), bar.bar2(), bar.bar3(), bar.bar4(), bar);
        
        // the following two lines should be transformed to
        // execution.setVariable("jsonVar", foo);
        final SpinJsonNode jsonVarOutput = JSON(foo);
        execution.setVariable("jsonVar", jsonVarOutput);
    }

    public record Foo(String foo1, int foo2, boolean foo3, double foo4, Bar foo5) { }

    public record Bar(String bar1, int bar2, boolean bar3, double bar4) { }
}
