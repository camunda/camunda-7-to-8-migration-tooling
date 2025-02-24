package org.camunda.migration.rewrite.recipes.glue;

import static org.openrewrite.Tree.randomId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Annotation;
import org.openrewrite.java.tree.J.ClassDeclaration;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobWorker;

public class JavaDelegateSpringToZeebeWorkerSpring extends Recipe {
	
    private static Logger LOG = LoggerFactory.getLogger(JavaDelegateSpringToZeebeWorkerSpring.class);
	
	@Override
	public String getDisplayName() {
		return "Convert JavaDelegate to Zeebe Job Worker";
	}

	@Override
	public String getDescription() {
		return "Transforms JavaDelegate implementations into Zeebe Workers.";
	}

	@Override
	public JavaIsoVisitor<ExecutionContext> getVisitor() {

		return new JavaIsoVisitor<ExecutionContext>() {

	        @Override
	        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
	             // Remove old imports
                List<J.Import> filteredImports = compilationUnit.getImports().stream()

                    .filter(i -> {try { return (!i.getTypeName().equals("org.camunda.bpm.engine.delegate.JavaDelegate"));} catch (Exception ex) {return true;}})
                    .filter(i -> {try { return (!i.getTypeName().equals("org.camunda.bpm.engine.delegate.DelegateExecution"));} catch (Exception ex) {return true;}})
                    .collect(Collectors.toList());

                // Add new imports
                addImport(filteredImports, "io.camunda.zeebe.client.api.worker.JobWorker");
                addImport(filteredImports, "io.camunda.zeebe.client.api.response.ActivatedJob");
                
                compilationUnit = compilationUnit.withImports(filteredImports);  
                return super.visitCompilationUnit(compilationUnit, ctx); 
	        }

			private void addImport(List<J.Import> filteredImports, String fullyQualifiedName) {
				if (filteredImports.stream().noneMatch(i -> i.getTypeName().equals(fullyQualifiedName))) {
					filteredImports.add(
                		new J.Import(randomId(), 
                				Space.build("\n", new ArrayList<Comment>()),
                                Markers.EMPTY,
                                new JLeftPadded<>(Space.EMPTY, false, Markers.EMPTY),
                                TypeTree.build(fullyQualifiedName).withPrefix(Space.SINGLE_SPACE),
                                null));
				}
			}
	        
			@Override
			public ClassDeclaration visitClassDeclaration(ClassDeclaration classDecl, ExecutionContext ctx) {
				System.out.println(classDecl);
				if (isJavaDelegate(classDecl)) {
					System.out.println("Adjusting JavaDelegate: " + classDecl.getType().getFullyQualifiedName());
					LOG.info("Adjusting JavaDelegate: " + classDecl.getType().getFullyQualifiedName());
					
	                // Filter out the interface to remove
	                List<TypeTree> updatedImplements = classDecl.getImplements().stream()
	                    .filter(id -> !TypeUtils.isOfClassType(id.getType(), JavaDelegate.class.getTypeName()))
	                    .collect(Collectors.toList());

	                // If no interfaces remain, set `implements` to null
					classDecl = super.visitClassDeclaration(classDecl.withImplements(updatedImplements.isEmpty() ? null : updatedImplements), ctx);
					
					// not sure why this is not working - that's why I added it separately above
					maybeAddImport("org.camunda.bpm.engine.delegate.JavaDelegate", false);
					maybeAddImport("org.camunda.bpm.engine.delegate.DelegateExecution", false);

					// not sure why this is not working - that's why I added it separately above
					maybeRemoveImport("io.camunda.zeebe.client.api.worker.JobWorker");
					maybeRemoveImport("io.camunda.zeebe.client.api.response.ActivatedJob");

//					return classDecl;
				} else {
					LOG.info("Ignoring: " + classDecl.getType().getFullyQualifiedName());					
				}
				return super.visitClassDeclaration(classDecl, ctx);
			}
			

			private final JavaTemplate workerMethodTemplate = JavaTemplate.builder("@JobWorker(type = \"#{}\", autoComplete=true)")
					.imports(JobWorker.class.getTypeName()).build();
			
	        private final JavaTemplate jobWorkerMethod = JavaTemplate.builder("ActivatedJob #{}")
	                .imports(ActivatedJob.class.getTypeName())
	                .build();
	       			

			@Override
			public MethodDeclaration visitMethodDeclaration(MethodDeclaration methodDeclaration, ExecutionContext ctx) {
				J.ClassDeclaration classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class);
				if (!isJavaDelegate(classDecl)) {
					return super.visitMethodDeclaration(methodDeclaration, ctx);
				}
				
				if ("execute".equals(methodDeclaration.getSimpleName())) {					
					// Remove @Override (if present)
					methodDeclaration = removeAnnotation(methodDeclaration, "@java.lang.Override");
	                					
					// Add @JobWorker 
	                String workerName = Character.toLowerCase(classDecl.getSimpleName().charAt(0)) + classDecl.getSimpleName().substring(1);
					methodDeclaration = workerMethodTemplate.apply(
							updateCursor(methodDeclaration),
							methodDeclaration.getCoordinates().addAnnotation(new DefaultAnnotationComparator()),
							workerName);
				
	                // Update return type to `Map`
					methodDeclaration = changeMethodReturnType(methodDeclaration, "java.util.Map");
	               
	               // Add HashMap that can take any results
					methodDeclaration = addHashMapForResults(methodDeclaration);

                    // Remember the parameter name of type DelegateExecution:;
	               String delegateExecutionAssignmentName = getAssignmentName(methodDeclaration);
                    
	                // Replace parameters by ActivatedJob only (removes DelegateExecution) - but keep the name 
	    			methodDeclaration = jobWorkerMethod.apply(
	    					updateCursor(methodDeclaration),
							methodDeclaration.getCoordinates().replaceParameters(),
							delegateExecutionAssignmentName);
				}				
				
				return super.visitMethodDeclaration(methodDeclaration, ctx); // make sure to do this so that the assignments within that method are checked			
			 }
			
		    private final JavaTemplate createMapTemplate = JavaTemplate.builder("java.util.Map<String, Object> resultMap = new java.util.HashMap<>();")
		    		.imports("java.util.HashMap", "java.util.Map").build();

	        private final JavaTemplate returnMapTemplate = JavaTemplate.builder("return resultMap;").build();
	            
            private MethodDeclaration addHashMapForResults(MethodDeclaration methodDeclaration) {
                // Add HashMap initialization and dynamic key-value setting

            	methodDeclaration = createMapTemplate.apply(
                    		updateCursor(methodDeclaration), 
                    		methodDeclaration.getBody().getCoordinates().firstStatement());                    		

            	methodDeclaration = returnMapTemplate.apply(
                		updateCursor(methodDeclaration), 
                		methodDeclaration.getBody().getCoordinates().lastStatement());                    		

                return methodDeclaration;
			}

			private final JavaTemplate getVariablesAsMap = JavaTemplate.builder("#{any()}.getVariablesAsMap().getVariable(#{any(java.lang.String)})").build();
            
            
            private MethodInvocation transformGetVariableCall(Cursor cursor, J.MethodInvocation methodInvocation) {
                Expression select = methodInvocation.getSelect();  // The variable (ctx, execution, etc.)
                Expression argument = methodInvocation.getArguments().get(0); // The key ("AMOUNT")

                // Apply the transformation using JavaTemplate
                return getVariablesAsMap.apply(
                	cursor,
                    methodInvocation.getCoordinates().replace(),
                    select,  // The original variable (ctx, execution, etc.)
                    argument // The original argument ("AMOUNT")
                );
             }

            private final JavaTemplate putEntryTemplate = JavaTemplate.builder("resultMap.put(#{any(java.lang.String)}, #{any()});").build();

            private J.MethodInvocation transformSetVariableCall(Cursor cursor, J.MethodInvocation methodInvocation) {
                //Expression select = methodInvocation.getSelect();  // The variable (ctx, execution, etc.)
                Expression argumentKey = methodInvocation.getArguments().get(0); // The key ("AMOUNT")
                Expression argumentValue = methodInvocation.getArguments().get(1); // The value
                
                return putEntryTemplate.apply(
                	cursor,
                    methodInvocation.getCoordinates().replace(),
                    argumentKey, 
                    argumentValue
                );
             }

			 @Override
	            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {
	                if (isGetVariableCall(methodInvocation)) {
	                    return transformGetVariableCall(getCursor(), methodInvocation);
	                }
	                if (isSetVariableCall(methodInvocation)) {
	                    return transformSetVariableCall(getCursor(), methodInvocation);
	                }
	                return super.visitMethodInvocation(methodInvocation, ctx);
	            }

	            @Override
	            public J.TypeCast visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {
	                Expression innerExpression = typeCast.getExpression();
	                if (innerExpression instanceof J.MethodInvocation) {
	                    J.MethodInvocation methodInvocation = (J.MethodInvocation) innerExpression;
	                    
	                    if (isGetVariableCall(methodInvocation)) {
	                        J.MethodInvocation newMethodInvocation = transformGetVariableCall(
	                        		new Cursor(getCursor(), methodInvocation), // make sure a proper cursor is created for the inner method
	                        		methodInvocation);
	                        return typeCast.withExpression(newMethodInvocation);
	                    }
	                    else if (isSetVariableCall(methodInvocation)) {
	                        J.MethodInvocation newMethodInvocation = transformSetVariableCall(
	                        		new Cursor(getCursor(), methodInvocation), // make sure a proper cursor is created for the inner method
	                        		methodInvocation);
	                        return typeCast.withExpression(newMethodInvocation);
	                    }
	                }
	                return super.visitTypeCast(typeCast, ctx);
	            }

	            @Override
	            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
	                Expression assignmentExpr = assignment.getAssignment();

	                if (assignmentExpr instanceof J.MethodInvocation) {
	                    J.MethodInvocation methodInvocation = (J.MethodInvocation) assignmentExpr;

	                    if (isGetVariableCall(methodInvocation)) {
	                        return assignment.withAssignment(transformGetVariableCall(getCursor(), methodInvocation));
	                    }
	                    else if (isSetVariableCall(methodInvocation)) {
	                        return assignment.withAssignment(transformSetVariableCall(getCursor(), methodInvocation));
	                    }
	                }

	                return super.visitAssignment(assignment, ctx);
	            }

	            private boolean isGetVariableCall(J.MethodInvocation methodInvocation) {
	                if (!methodInvocation.getSimpleName().equals("getVariable")) {
	                    return false;
	                }

	                Expression select = methodInvocation.getSelect();
	                if (!(select instanceof J.Identifier)) {
	                    return false;
	                }

	                J.Identifier selectIdentifier = (J.Identifier) select;
	                JavaType type = selectIdentifier.getType();

	                return type instanceof JavaType.Class &&
	                       ((JavaType.Class) type).getFullyQualifiedName().equals(DelegateExecution.class.getTypeName());
	            }
	            private boolean isSetVariableCall(J.MethodInvocation methodInvocation) {
	                if (!methodInvocation.getSimpleName().equals("setVariable")) {
	                    return false;
	                }

	                Expression select = methodInvocation.getSelect();
	                if (!(select instanceof J.Identifier)) {
	                    return false;
	                }

	                J.Identifier selectIdentifier = (J.Identifier) select;
	                JavaType type = selectIdentifier.getType();

	                return type instanceof JavaType.Class &&
	                       ((JavaType.Class) type).getFullyQualifiedName().equals(DelegateExecution.class.getTypeName());
	            }

	            
	            public MethodDeclaration removeAnnotation(MethodDeclaration methodDeclaration, String annotationName) {
					final AnnotationMatcher overrideMatcher = new AnnotationMatcher(annotationName);
					methodDeclaration = methodDeclaration.withLeadingAnnotations(
	                        ListUtils.map(methodDeclaration.getLeadingAnnotations(), anno ->
	                            overrideMatcher.matches(anno) ? null : anno
	                        ));		
					return methodDeclaration;
				}
				public MethodDeclaration changeMethodReturnType(MethodDeclaration methodDeclaration, String type) {
					 JavaType.Method newReturnType = methodDeclaration.getMethodType();
		                newReturnType = newReturnType.withReturnType( JavaType.buildType(type) );
		                methodDeclaration = methodDeclaration.withMethodType(newReturnType);
	                 if (methodDeclaration.getName().getType() != null) {
	                 	methodDeclaration = methodDeclaration.withName(methodDeclaration.getName().withType(newReturnType));
	                 }
					return methodDeclaration;
				}
				public String getAssignmentName(MethodDeclaration methodDeclaration) {
					 J.VariableDeclarations firstParam = (J.VariableDeclarations) methodDeclaration.getParameters().get(0);
	                 return firstParam.getVariables().get(0).getName().getSimpleName();				 
				}

				private boolean isJavaDelegate(ClassDeclaration classDecl) {
					if (classDecl == null || classDecl.getImplements() == null) {
						return false;
					}
					for (TypeTree implementedInterface : classDecl.getImplements()) {
						if (TypeUtils.isOfClassType(implementedInterface.getType(), JavaDelegate.class.getName())) {
							return true;
						}
					}
					return false;			
				}
		};

	}
	
	public static class DefaultAnnotationComparator implements Comparator<J.Annotation> {								
		@Override
		public int compare(Annotation o1, Annotation o2) {							
			return o1.getSimpleName().compareTo(o2.getSimpleName());
		}
	}
}