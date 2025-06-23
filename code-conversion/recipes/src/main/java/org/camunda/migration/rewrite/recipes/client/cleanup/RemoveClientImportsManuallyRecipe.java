package org.camunda.migration.rewrite.recipes.client.cleanup;

import java.util.List;
import java.util.stream.Collectors;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

public class RemoveClientImportsManuallyRecipe extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove some imports manually";
    }

    @Override
    public String getDescription() {
        return "The remove unneeded imports recipe does not work properly in some cases. This recipe removes specified imports by force.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

	        @Override
	        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
	             // Not sure why "maybeRemoveImport()" is not working - forcing removal here:
                List<J.Import> filteredImports = compilationUnit.getImports().stream()

                        .filter(i -> {try { return (!i.getTypeName().equals("org.camunda.bpm.engine.ProcessEngine"));} catch (Exception ex) {return true;}})
                        .filter(i -> {try { return (!i.getTypeName().equals("org.camunda.bpm.engine.runtime.ProcessInstance"));} catch (Exception ex) {return true;}})
                    .collect(Collectors.toList());

                compilationUnit = compilationUnit.withImports(filteredImports);  
                return super.visitCompilationUnit(compilationUnit, ctx); 
	        }
        };
    }
}
