package org.camunda.migration.rewrite.recipes.glue;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.*;
import org.openrewrite.java.tree.J.Annotation;
import org.openrewrite.java.tree.J.ClassDeclaration;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.JavaType.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
				final J.CompilationUnit newCompilationUnit = super.visitCompilationUnit(compilationUnit, ctx);
				maybeRemoveImport("org.camunda.bpm.engine.delegate.JavaDelegate");
				maybeRemoveImport("org.camunda.bpm.engine.delegate.DelegateExecution");
				return newCompilationUnit;
			}

//          @Override
//          public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
//               // Remove old imports
//              List<J.Import> filteredImports = compilationUnit.getImports().stream()

//                  .filter(i -> {try { return (!i.getTypeName().equals("org.camunda.bpm.engine.delegate.JavaDelegate"));} catch (Exception ex) {return true;}})
//                  .filter(i -> {try { return (!i.getTypeName().equals("org.camunda.bpm.engine.delegate.DelegateExecution"));} catch (Exception ex) {return true;}})
//                  .collect(Collectors.toList());

//              // Add new imports
//              addImport(filteredImports, "io.camunda.zeebe.client.api.worker.JobWorker");
//              addImport(filteredImports, "io.camunda.zeebe.client.api.response.ActivatedJob");

//              compilationUnit = compilationUnit.withImports(filteredImports);
//  			return super.visitCompilationUnit(compilationUnit, ctx);
//          }
//
//			private void addImport(List<J.Import> filteredImports, String fullyQualifiedName) {
//				if (filteredImports.stream().noneMatch(i -> i.getTypeName().equals(fullyQualifiedName))) {
//					filteredImports.add(
//                		new J.Import(randomId(), 
//                				Space.build("\n", new ArrayList<Comment>()),
//                                Markers.EMPTY,
//                                new JLeftPadded<>(Space.EMPTY, false, Markers.EMPTY),
//                                TypeTree.build(fullyQualifiedName).withPrefix(Space.SINGLE_SPACE),
//                                null));
//				}
//			}
	        
			@Override
			public ClassDeclaration visitClassDeclaration(ClassDeclaration classDecl, ExecutionContext ctx) {
				if (isJavaDelegate(classDecl)) {
					System.out.println("Adjusting JavaDelegate: " + classDecl.getType().getFullyQualifiedName());
					LOG.info("Adjusting JavaDelegate: " + classDecl.getType().getFullyQualifiedName());
					
	                // Filter out the interface to remove
	                List<TypeTree> updatedImplements = classDecl.getImplements().stream()
	                    .filter(id -> !TypeUtils.isOfClassType(id.getType(), JavaDelegate.class.getTypeName()))
	                    .collect(Collectors.toList());

	                // If no interfaces remain, set `implements` to null
					classDecl = super.visitClassDeclaration(classDecl.withImplements(updatedImplements.isEmpty() ? null : updatedImplements), ctx);
					
					maybeAddImport("io.camunda.zeebe.client.api.worker.JobWorker", false);
					maybeAddImport("io.camunda.zeebe.client.api.response.ActivatedJob", false);
				}
				return super.visitClassDeclaration(classDecl, ctx);
			}
			

			private final JavaTemplate workerMethodTemplate = JavaTemplate.builder("@JobWorker(type = \"#{}\", autoComplete=true)")
					.javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
					.imports("io.camunda.zeebe.client.api.worker.JobWorker")
					.build();
			
	        private final JavaTemplate jobWorkerMethod = JavaTemplate.builder("ActivatedJob #{}")
	        		.javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
	        		.imports(ActivatedJob.class.getTypeName())
	                .build();
	       			
			@Override
			public MethodDeclaration visitMethodDeclaration(MethodDeclaration methodDeclaration, ExecutionContext ctx) {
				J.ClassDeclaration classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class);
				
				if (isJavaDelegate(classDecl) // only JavaDelegate classes
					&& "execute".equals(methodDeclaration.getSimpleName()) // execute method
				    && !hasAnnotation(methodDeclaration, "@io.camunda.zeebe.client.api.worker.JobWorker")) { // if they don't have the @JobWorker yet

					// Remove @Override (if present)
					methodDeclaration = removeAnnotation(methodDeclaration, "@java.lang.Override");
	                					
					// Add @JobWorker 
	                String workerName = Character.toLowerCase(classDecl.getSimpleName().charAt(0)) + classDecl.getSimpleName().substring(1);
					methodDeclaration = workerMethodTemplate.apply(
							updateCursor(methodDeclaration),
							methodDeclaration.getCoordinates().addAnnotation(new DefaultAnnotationComparator()),
							workerName);
				
	               // Add HashMap that can take any results
					methodDeclaration = addHashMapForResults(methodDeclaration);

                    // Remember the parameter name of type DelegateExecution:;
	               String delegateExecutionAssignmentName = getAssignmentName(methodDeclaration);
                    
	                // Replace parameters by ActivatedJob only (removes DelegateExecution) - but keep the name 
	    			methodDeclaration = jobWorkerMethod.apply(
	    					updateCursor(methodDeclaration),
							methodDeclaration.getCoordinates().replaceParameters(),
							delegateExecutionAssignmentName);

	                // Update return type to `Map`
					methodDeclaration = changeMethodReturnType(methodDeclaration, " Map<String, Object>", "java.util.Map<String, Object>");
					maybeAddImport("java.util.Map");
	               
					System.out.println("Adjusted JobWorker: " + methodDeclaration);
					return methodDeclaration;
				}				
				
				return super.visitMethodDeclaration(methodDeclaration, ctx); // make sure to do this so that the assignments within that method are checked			
			 }
			
			public MethodDeclaration changeMethodReturnType(MethodDeclaration methodDeclaration, String expression, String type) {
				 Method methodType = methodDeclaration.getMethodType().withReturnType( JavaType.buildType(type) );
				 methodDeclaration = methodDeclaration.withReturnTypeExpression( TypeTree.build(expression, ' ') );		 
	             methodDeclaration = methodDeclaration.withMethodType(methodType)
	            		 .withName(methodDeclaration.getName().withType(methodType));
				return methodDeclaration;
			}
			
		    private final JavaTemplate createMapTemplate = JavaTemplate.builder("Map<String, Object> resultMap = new HashMap<>();")
		    		.javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
		    		.imports("java.util.HashMap", "java.util.Map")
		    		.build();

	        private final JavaTemplate returnMapTemplate = JavaTemplate.builder("return resultMap;")
		    		.javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
		    		.imports("java.util.HashMap", "java.util.Map")
	        		.build();
	            
            private MethodDeclaration addHashMapForResults(MethodDeclaration methodDeclaration) {
				maybeAddImport("java.util.Map", false);
				maybeAddImport("java.util.HashMap", false);

            	// Add HashMap initialization and dynamic key-value setting
            	methodDeclaration = createMapTemplate.apply(
                    		updateCursor(methodDeclaration), 
                    		methodDeclaration.getBody().getCoordinates().firstStatement());                    		

            	methodDeclaration = returnMapTemplate.apply(
                		updateCursor(methodDeclaration), 
                		methodDeclaration.getBody().getCoordinates().lastStatement());  
            	
                // Manually set the type of the `resultMap` variable
                methodDeclaration = methodDeclaration.withBody(
                        methodDeclaration.getBody().withStatements(
                                methodDeclaration.getBody().getStatements().stream()
                                        .map(statement -> {
                                            if (statement instanceof J.Return) {
                                                J.Return returnStatement = (J.Return) statement;
                                                return returnStatement.withExpression(
                                                		returnStatement.getExpression().withType(
                                                		JavaType.buildType("java.util.Map<java.lang.String, java.lang.Object>")));
                                            }
                                            return statement;
                                        }).toList()
                        )
                );


                return methodDeclaration;
			}

			private final JavaTemplate getVariableTemplate = JavaTemplate.builder("#{any(io.camunda.zeebe.client.api.response.ActivatedJob)}.getVariable(#{any(java.lang.String)})")
					.javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
					.imports("io.camunda.zeebe.client.api.response.ActivatedJob")
					.build();
            
            
            private MethodInvocation transformGetVariableCall(Cursor cursor, J.MethodInvocation methodInvocation) {
                Expression select = methodInvocation.getSelect();  // The variable (ctx, execution, etc.)
                Expression argument = methodInvocation.getArguments().get(0); // The key ("AMOUNT")

                // Apply the transformation using JavaTemplate
                return getVariableTemplate.apply(
                	cursor,
                    methodInvocation.getCoordinates().replace(),
                    select,  // The original variable (ctx, execution, etc.)
                    argument // The original argument ("AMOUNT")
                );
             }

            private final JavaTemplate putEntryTemplate = JavaTemplate.builder("resultMap.put(#{any(java.lang.String)}, #{any()});")
					.javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
            		.build();

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
					System.out.println("Transform " + methodInvocation);
                    return transformGetVariableCall(getCursor(), methodInvocation);
                }
                if (isSetVariableCall(methodInvocation)) {
					System.out.println("Transform " + methodInvocation);
                    return transformSetVariableCall(getCursor(), methodInvocation);
                }
                return super.visitMethodInvocation(methodInvocation, ctx);
            }

            @Override
            public J.TypeCast visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {
                return super.visitTypeCast(typeCast, ctx);
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                return super.visitAssignment(assignment, ctx);
            }

            private boolean isGetVariableCall(J.MethodInvocation methodInvocation) {
                if (!methodInvocation.getSimpleName().equals("getVariable")) {
                    return false;
                }

				final JavaType methodType = methodInvocation.getMethodType();
				if (methodType == null || !methodInvocation.getMethodType().equals(JAVATYPE_VariableScope)) {
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
			private static final JavaType JAVATYPE_VariableScope = JavaType.buildType("org.camunda.bpm.engine.delegate.VariableScope");

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
            
            public boolean hasAnnotation(MethodDeclaration methodDeclaration, String annotationName) {
            	AnnotationMatcher annotationMatcher = new AnnotationMatcher(annotationName);
            	for (Annotation a : methodDeclaration.getLeadingAnnotations()) {
					if (annotationMatcher.matches(a))
						return true;
				}
            	return false;
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
