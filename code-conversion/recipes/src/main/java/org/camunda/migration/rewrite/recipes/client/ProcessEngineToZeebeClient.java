package org.camunda.migration.rewrite.recipes.client;

import static java.util.Collections.emptyList;

import java.util.List;
import java.util.stream.Collectors;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Identifier;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.Markers;

public class ProcessEngineToZeebeClient extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate Camunda 7 ProcessEngine to Camunda 8 ZeebeClient";
    }

    @Override
    public String getDescription() {
        return "Refactors Camunda 7's ProcessEngine usage to Camunda 8's ZeebeClient usage.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            private final MethodMatcher methodMatcher = new MethodMatcher("org.camunda.bpm.engine.ProcessEngine getRuntimeService().startProcessInstanceByKey(..)");


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
                        
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext executionContext) {
            	multiVariable = changeVariableTypeIfApplicable(multiVariable, 
            			"org.camunda.bpm.engine.ProcessEngine", // from 
            			"io.camunda.zeebe.client.ZeebeClient"); // to           		
            	
            	multiVariable = changeVariableTypeIfApplicable(multiVariable, 
            			"org.camunda.bpm.engine.runtime.ProcessInstance", //from
            			"io.camunda.zeebe.client.api.response.ProcessInstanceEvent"); // to
            	
                return super.visitVariableDeclarations(multiVariable, executionContext);
            }

            /**
             * See https://github.com/openrewrite/rewrite/blob/main/rewrite-java/src/main/java/org/openrewrite/java/ChangeMethodInvocationReturnType.java
             */
			private J.VariableDeclarations changeVariableTypeIfApplicable(J.VariableDeclarations multiVariable, String fromFullyQualifiedTypeName, String toFullyQualifiedTypeName) {
            	if (!fromFullyQualifiedTypeName.equals(multiVariable.getTypeAsFullyQualified().getFullyQualifiedName())) {
            		return multiVariable;
            	} else {			
					multiVariable = multiVariable
							.withType( JavaType.buildType(toFullyQualifiedTypeName) )
							.withTypeExpression( new J.Identifier(
								 multiVariable.getTypeExpression().getId(),
								 multiVariable.getTypeExpression().getPrefix(),
		                         Markers.EMPTY,
		                         emptyList(),
		                         toFullyQualifiedTypeName.substring(toFullyQualifiedTypeName.lastIndexOf('.') + 1),
						 		 JavaType.buildType(toFullyQualifiedTypeName),
						 		 null));

					maybeRemoveImport(fromFullyQualifiedTypeName);
					maybeAddImport(toFullyQualifiedTypeName, false);

					return multiVariable;
            	}
			}

            private final JavaTemplate createInstanceCommand = JavaTemplate.builder(
            		"#{any(io.camunda.zeebe.client.ZeebeClient)}.newCreateInstanceCommand().bpmnProcessId(#{any(String)}).latestVersion().variables(#{any(java.util.Map)}).send().join()")
            		.javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
            		.imports("io.camunda.zeebe.client.ZeebeClient", "io.camunda.zeebe.client.api.response.ProcessInstanceEvent")
            		.build();

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (!methodMatcher.matches(method.getMethodType())) {
                	// This is still a bit wired to me why the matcher is not working as expected in this context - matching manually below
                    //return method;
                	//System.out.println("NOT MATCHED??: " + method + " of type: " + method.getMethodType());
                }            	            	

                // ADJUST processEngine.getRuntimeService().startProcessInstanceByKey(..)
                if (method.getSelect()!=null 
                		&& method.getSelect().getType() !=null 
                		&& method.getSelect().getType().getClass().isAssignableFrom( JavaType.Class.class) 
                		&& ((JavaType.Class)method.getSelect().getType()).getFullyQualifiedName().equals("org.camunda.bpm.engine.RuntimeService")
                	    && method.getSimpleName().equals("startProcessInstanceByKey")) {                	
                	
                	Identifier variableName = (Identifier) ((MethodInvocation)method.getSelect()).getSelect();            		
            		method = createInstanceCommand.apply(
            			getCursor(), 
            			method.getCoordinates().replace(),
            			variableName,
            			method.getArguments().get(0),
            			method.getArguments().get(1)
                		);
            		
//            		maybeAddImport("io.camunda.zeebe.client.ZeebeClient");
//            		maybeAddImport("io.camunda.zeebe.client.api.response.ProcessInstanceEvent");
//            		
//            		maybeRemoveImport("org.camunda.bpm.engine.RuntimeService");
//            		maybeRemoveImport("org.camunda.bpm.engine.runtime.ProcessInstance");
                }

                // ADJUST processInstance.getId()
                if (method.getSelect()!=null 
                		&& method.getSelect().getType() !=null 
                		&& method.getSelect().getType().getClass().isAssignableFrom( JavaType.Class.class) 
                		&& ((JavaType.Class)method.getSelect().getType()).getFullyQualifiedName().equals("org.camunda.bpm.engine.runtime.ProcessInstance")
                	    && method.getSimpleName().equals("getId")) {
                	
                	// change name                	
                	method = method.withName( method.getName().withSimpleName("getProcessInstanceKey") );                	
                }

                return super.visitMethodInvocation(method, executionContext);
            }
            
        };
    }
}
