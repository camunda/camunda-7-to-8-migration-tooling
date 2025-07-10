package org.camunda.migration.rewrite.recipes.delegate.migrate;

import java.util.ArrayList;
import java.util.List;
import org.camunda.migration.rewrite.recipes.utils.RecipeConstants;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

public class ReplaceExecutionRecipe extends Recipe {

  /** Instantiates a new instance. */
  public ReplaceExecutionRecipe() {}

  @Override
  public String getDisplayName() {
    return "Replaces all delegate execution methods";
  }

  @Override
  public String getDescription() {
    return "During preparation, a job worker was added to the class. This recipe copies and adjusts the delegate code to the job worker.";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(
        new CopyDelegateToJobWorkerRecipe(),
        new MigrateDelegateVariableHandlingInJobWorker(),
        new MigrateDelegateBPMNErrorAndExceptionInJobWorker()
        //    new MigrateDelegateIncidentInJobWorker()
        );
  }

  private static class CopyDelegateToJobWorkerRecipe extends Recipe {

    /** Instantiates a new instance. */
    public CopyDelegateToJobWorkerRecipe() {}

    @Override
    public String getDisplayName() {
      return "Copy delegate code to job worker recipe";
    }

    @Override
    public String getDescription() {
      return "During preparation, a job worker was added to the class. This recipe copies the delegate code to the job worker.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

      // define preconditions
      TreeVisitor<?, ExecutionContext> check =
          Preconditions.and(
              new UsesType<>(RecipeConstants.Type.JOB_WORKER, true),
              new UsesType<>(RecipeConstants.Type.VARIABLE_SCOPE, true));

      return Preconditions.check(
          check,
          new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
              // Skip interfaces
              if (classDecl.getKind() != J.ClassDeclaration.Kind.Type.Class) {
                return super.visitClassDeclaration(classDecl, ctx);
              }

              List<Statement> currentStatements = classDecl.getBody().getStatements();
              List<Statement> updatedStatements = new ArrayList<>();

              // find delegate method
              J.Block delegateBody = null;
              for (Statement stmt : currentStatements) {
                if (stmt instanceof J.MethodDeclaration methDecl
                    && methDecl.getSimpleName().equals("execute")) {
                  delegateBody = methDecl.getBody();
                }
              }

              // find and change job worker method
              if (delegateBody != null) {
                for (Statement stmt : currentStatements) {
                  if (stmt instanceof J.MethodDeclaration methDecl
                      && methDecl.getSimpleName().equals("executeJob")) {
                    J.Block jobWorkerBody = methDecl.getBody();

                    // all current statments (result map and return)
                    List<Statement> jobWorkerStatements = jobWorkerBody.getStatements();

                    // delegate body
                    List<Statement> delegateStatements =
                        new ArrayList<>(delegateBody.getStatements());

                    // combine statements
                    delegateStatements.add(0, jobWorkerStatements.get(0));
                    delegateStatements.add(jobWorkerStatements.get(jobWorkerStatements.size() - 1));

                    // put together and rename job worker so recipe does not run twice
                    updatedStatements.add(
                        methDecl
                            .withBody(methDecl.getBody().withStatements(delegateStatements))
                            .withName(methDecl.getName().withSimpleName("executeJobMigrated"))
                            .withMethodType(
                                methDecl.getMethodType().withName("executeJobMigrated")));
                  } else {
                    updatedStatements.add(stmt);
                  }
                }
                return classDecl.withBody(classDecl.getBody().withStatements(updatedStatements));
              }
              return super.visitClassDeclaration(classDecl, ctx);
            }
          });
    }
  }

  private static class MigrateDelegateVariableHandlingInJobWorker extends Recipe {

    /** Instantiates a new instance. */
    public MigrateDelegateVariableHandlingInJobWorker() {}

    @Override
    public String getDisplayName() {
      return "Migrate variable handling code in job worker recipe";
    }

    @Override
    public String getDescription() {
      return "During a previous step, delegate code was copied into the job worker. This recipe migrates variable handling code.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

      // define preconditions
      TreeVisitor<?, ExecutionContext> check =
          Preconditions.and(
              new UsesType<>(RecipeConstants.Type.JOB_WORKER, true),
              new UsesType<>(RecipeConstants.Type.DELEGATE_EXECUTION, true));

      return Preconditions.check(
          check,
          new JavaVisitor<ExecutionContext>() {

            final MethodMatcher engineGetVariable =
                new MethodMatcher(RecipeConstants.Method.GET_VARIABLE);

            final MethodMatcher engineGetVariableLocal =
                new MethodMatcher(RecipeConstants.Method.GET_VARIABLE_LOCAL);

            final MethodMatcher engineSetVariable =
                new MethodMatcher(RecipeConstants.Method.SET_VARIABLE);

            final MethodMatcher engineSetVariableLocal =
                new MethodMatcher(RecipeConstants.Method.SET_VARIABLE_LOCAL);

            @Override
            public J visitMethodInvocation(
                J.MethodInvocation methodInvocation, ExecutionContext ctx) {

              // ensure we are not inside the delegate method
              if (isInsideDelegateMethod()) {
                return methodInvocation;
              }

              if (engineGetVariable.matches(methodInvocation)
                  || engineGetVariableLocal.matches(methodInvocation)) {
                return transformGetVariableCall(getCursor(), methodInvocation);
              }
              if (engineSetVariable.matches(methodInvocation)
                  || engineSetVariableLocal.matches(methodInvocation)) {
                return transformSetVariableCall(getCursor(), methodInvocation);
              }
              return super.visitMethodInvocation(methodInvocation, ctx);
            }

            @Override
            public J visitAssignment(J.Assignment assignment, ExecutionContext ctx) {

              // ensure we are not inside the delegate method
              if (isInsideDelegateMethod()) {
                return assignment;
              }
              return super.visitAssignment(assignment, ctx);
            }

            @Override
            public J visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {

              // ensure we are not inside the delegate method
              if (isInsideDelegateMethod()) {
                return typeCast;
              }
              return super.visitTypeCast(typeCast, ctx);
            }

            @Override
            public J visitBinary(J.Binary binary, ExecutionContext ctx) {

              // ensure we are not inside the delegate method
              if (isInsideDelegateMethod()) {
                return binary;
              }
              return super.visitBinary(binary, ctx);
            }

            private boolean isInsideDelegateMethod() {
              J.MethodDeclaration enclosingMethod =
                  getCursor().firstEnclosing(J.MethodDeclaration.class);
              return enclosingMethod != null && "execute".equals(enclosingMethod.getSimpleName());
            }

            private final JavaTemplate getVariablesAsMap =
                JavaTemplate.builder(
                        "#{job:any("
                            + RecipeConstants.Type.ACTIVATED_JOB
                            + ")}.getVariable(#{any(java.lang.String)})")
                    .javaParser(
                        JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .imports(RecipeConstants.Type.ACTIVATED_JOB)
                    .build();

            private J.MethodInvocation transformGetVariableCall(
                Cursor cursor, J.MethodInvocation methodInvocation) {
              J.Identifier jobIdent =
                  RecipeUtils.createSimpleIdentifier("job", RecipeConstants.Type.ACTIVATED_JOB);

              Expression argument = methodInvocation.getArguments().get(0); // The key ("AMOUNT")

              // Apply the transformation using JavaTemplate
              return getVariablesAsMap.apply(
                  cursor,
                  methodInvocation.getCoordinates().replace(),
                  jobIdent, // job ident
                  argument // The original argument ("AMOUNT")
                  );
            }

            private final JavaTemplate putEntryTemplate =
                JavaTemplate.builder(
                        "#{resultMap:any(java.util.Map)}.put(#{any(java.lang.String)}, #{any()});")
                    .javaParser(
                        JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .imports(RecipeConstants.Type.ACTIVATED_JOB)
                    .build();

            private J.MethodInvocation transformSetVariableCall(
                Cursor cursor, J.MethodInvocation methodInvocation) {
              // Expression select = methodInvocation.getSelect();  // The variable (ctx, execution,
              // etc.)
              J.Identifier mapIdent =
                  RecipeUtils.createSimpleIdentifier("resultMap", "java.util.Map");
              Expression argumentKey = methodInvocation.getArguments().get(0); // The key ("AMOUNT")
              Expression argumentValue = methodInvocation.getArguments().get(1); // The value

              return putEntryTemplate
                  .apply(
                      cursor,
                      methodInvocation.getCoordinates().replace(),
                      mapIdent,
                      argumentKey,
                      argumentValue)
                  .withPrefix(methodInvocation.getPrefix());
            }
          });
    }
  }

  private static class MigrateDelegateBPMNErrorAndExceptionInJobWorker extends Recipe {

    /** Instantiates a new instance. */
    public MigrateDelegateBPMNErrorAndExceptionInJobWorker() {}

    @Override
    public String getDisplayName() {
      return "Migrate BPMN error throwing code in job worker recipe";
    }

    @Override
    public String getDescription() {
      return "During a previous step, delegate code was copied into the job worker. This recipe migrates BPMN error throwing code.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

      // define preconditions
      TreeVisitor<?, ExecutionContext> check =
          Preconditions.and(
              new UsesType<>(RecipeConstants.Type.JOB_WORKER, true),
              new UsesType<>(RecipeConstants.Type.VARIABLE_SCOPE, true));

      return Preconditions.check(
          check,
          new JavaVisitor<ExecutionContext>() {

            // engine
            // BpmnError(java.lang.String errorCode)
            final MethodMatcher engineErrorWithCode =
                new MethodMatcher(
                    RecipeConstants.Type.ENGINE_BPMN_ERROR
                        + " <constructor>"
                        + RecipeConstants.Parameters.build("String"));
            // BpmnError(java.lang.String errorCode, java.lang.String message)
            final MethodMatcher engineErrorWithCodeWithMessage =
                new MethodMatcher(
                    RecipeConstants.Type.ENGINE_BPMN_ERROR
                        + " <constructor>"
                        + RecipeConstants.Parameters.build("String", "String"));
            // BpmnError(java.lang.String errorCode, java.lang.String message,
            // java.lang.Throwable cause)
            final MethodMatcher engineErrorWithCodeWithMessageWithThrowable =
                new MethodMatcher(
                    RecipeConstants.Type.ENGINE_BPMN_ERROR
                        + " <constructor>"
                        + RecipeConstants.Parameters.build(
                            "String", "String", "java.lang.Throwable"));
            // BpmnError(java.lang.String errorCode, java.lang.Throwable cause)
            final MethodMatcher engineErrorWithCodeWithThrowable =
                new MethodMatcher(
                    RecipeConstants.Type.ENGINE_BPMN_ERROR
                        + " <constructor>"
                        + RecipeConstants.Parameters.build("String", "java.lang.Throwable"));

            // client errors
            // bpmnError(java.lang.String errorCode, java.lang.String errorMessage)
            final JavaTemplate clientBPMNErrorWithCodeWithMessage =
                JavaTemplate.builder("throw CamundaError.bpmnError(#{any(String)}, #{any(String)})")
                    .javaParser(
                        JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .imports(RecipeConstants.Type.CLIENT_CAMUNDA_ERROR) // adjust imports
                    .build();
            // bpmnError(java.lang.String errorCode, java.lang.String errorMessage,java.lang.Object
            // variables, java.lang.Throwable cause)
            final JavaTemplate clientBPMNErrorWithCodeWithMessageWithVariablesWithThrowable =
                JavaTemplate.builder(
                        "throw CamundaError.bpmnError(#{any(String)}, #{any(String)}, #{any(java.util.Map)}, #{any(java.lang.Throwable)})")
                    .javaParser(
                        JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .imports(RecipeConstants.Type.CLIENT_CAMUNDA_ERROR) // adjust imports
                    .build();

            // engine
            // public ProcessEngineException()
            final MethodMatcher engineException =
                new MethodMatcher(RecipeConstants.Type.ENGINE_EXCEPTION + " <constructor>()");
            // public ProcessEngineException(java.lang.String message, java.lang.Throwable cause)
            final MethodMatcher engineExceptionWithMessageWithThrowable =
                new MethodMatcher(
                    RecipeConstants.Type.ENGINE_EXCEPTION
                        + " <constructor>"
                        + RecipeConstants.Parameters.build("String", "java.lang.Throwable"));
            // public ProcessEngineException(java.lang.String message) { /* compiled code */ }
            final MethodMatcher engineExceptionWithMessage =
                new MethodMatcher(
                    RecipeConstants.Type.ENGINE_EXCEPTION
                        + " <constructor>"
                        + RecipeConstants.Parameters.build("String"));
            // public ProcessEngineException(java.lang.String message, int code) { /* compiled code
            // */ }
            final MethodMatcher engineExceptionWithMessageAndCode =
                new MethodMatcher(
                    RecipeConstants.Type.ENGINE_EXCEPTION
                        + " <constructor>"
                        + RecipeConstants.Parameters.build("String", "int"));
            // public ProcessEngineException(java.lang.Throwable cause) { /* compiled code */ }
            final MethodMatcher engineExceptionWithThrowable =
                new MethodMatcher(
                    RecipeConstants.Type.ENGINE_EXCEPTION
                        + " <constructor>"
                        + RecipeConstants.Parameters.build("java.lang.Throwable"));

            // jobError(java.lang.String errorMessage)
            final JavaTemplate clientExceptionWithMessage =
                JavaTemplate.builder("throw CamundaError.jobError(#{any(String)})")
                    .javaParser(
                        JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .imports(RecipeConstants.Type.CLIENT_CAMUNDA_ERROR) // adjust imports
                    .build();

            // jobError(java.lang.String errorMessage, java.lang.Object variables)
            final JavaTemplate clientExceptionWithMessageWithVariables =
                JavaTemplate.builder(
                        "throw CamundaError.jobError(#{any(String)}, #{any(java.util.Map)})")
                    .javaParser(
                        JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .imports(RecipeConstants.Type.CLIENT_CAMUNDA_ERROR) // adjust imports
                    .build();

            // jobError(java.lang.String errorMessage, java.lang.Object variables, java.lang.Integer
            // retries)
            final JavaTemplate clientExceptionWithMessageWithVariablesWithRetries =
                JavaTemplate.builder(
                        "throw CamundaError.jobError(#{any(String)}, #{any(java.util.Map)}, #{any(Integer)})")
                    .javaParser(
                        JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .imports(RecipeConstants.Type.CLIENT_CAMUNDA_ERROR) // adjust imports
                    .build();

            final JavaTemplate clientExceptionWithMessageWithVariablesWithRetriesFixedMapIncident =
                JavaTemplate.builder(
                        "throw CamundaError.jobError(#{any(String)}, new HashMap<>(), 0)")
                    .javaParser(
                        JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .imports(
                        RecipeConstants.Type.CLIENT_CAMUNDA_ERROR,
                        "java.util.HashMap") // adjust imports
                    .build();
            final JavaTemplate
                clientExceptionWithMessageWithVariablesWithRetriesFixedMessageFixedMapIncident =
                    JavaTemplate.builder(
                            "throw CamundaError.jobError(\"Add an error message here\", new HashMap<>(), 0)")
                        .javaParser(
                            JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                        .imports(
                            RecipeConstants.Type.CLIENT_CAMUNDA_ERROR,
                            "java.util.HashMap") // adjust imports
                        .build();

            // jobError(java.lang.String errorMessage, java.lang.Object variables, java.lang.Integer
            // retries, java.time.Duration retryBackoff)
            final JavaTemplate clientExceptionWithMessageWithVariablesWithRetriesWithBackoff =
                JavaTemplate.builder(
                        "throw CamundaError.jobError(#{any(String)}, #{any(java.util.Map)}, #{any(Integer)}, #{any(java.time.Duration)})")
                    .javaParser(
                        JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .imports(RecipeConstants.Type.CLIENT_CAMUNDA_ERROR) // adjust imports
                    .build();

            // jobError(java.lang.String errorMessage, java.lang.Object variables, java.lang.Integer
            // retries, java.time.Duration retryBackoff, java.lang.Throwable cause)
            final JavaTemplate
                clientExceptionWithMessageWithVariablesWithRetriesWithBackoffWithThrowable =
                    JavaTemplate.builder(
                            "throw CamundaError.jobError(#{any(String)}, #{any(java.util.Map)}, #{any(Integer)}, #{any(java.time.Duration)}, #{any(java.lang.Throwable)})")
                        .javaParser(
                            JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                        .imports(RecipeConstants.Type.CLIENT_CAMUNDA_ERROR) // adjust imports
                        .build();

            @Override
            public J visitThrow(J.Throw throwStmt, ExecutionContext ctx) {
              if (isInsideDelegateMethod()) {
                return super.visitThrow(throwStmt, ctx);
              }

              Expression exception = throwStmt.getException();
              if (exception instanceof J.NewClass newClass) {

                TextComment variableComment =
                    new TextComment(
                        false,
                        " error variables can be added to the BPMN error event",
                        "\n" + throwStmt.getPrefix().getIndent(),
                        Markers.EMPTY);

                TextComment addErrorMessage =
                    new TextComment(
                        false,
                        " throwing a BPMN error event requires an error message in Camunda 8",
                        "\n" + throwStmt.getPrefix().getIndent(),
                        Markers.EMPTY);

                TextComment noErrorCodeComment =
                    new TextComment(
                        false,
                        " no error code when throwing job error in Camunda 8",
                        "\n" + throwStmt.getPrefix().getIndent(),
                        Markers.EMPTY);

                TextComment retriesDurationComment =
                    new TextComment(
                        false,
                        " you can specify retries and backoff when failing a job",
                        "\n" + throwStmt.getPrefix().getIndent(),
                        Markers.EMPTY);

                J.Identifier addAnErrorMessage =
                    RecipeUtils.createSimpleIdentifier("\"Add an error message here\"", "String");

                J.Identifier newHashMap =
                    RecipeUtils.createSimpleIdentifier("new HashMap<>()", "java.util.Map");

                J.Identifier retriesIdent =
                    RecipeUtils.createSimpleIdentifier("job.getRetries() - 1", "java.lang.Integer");

                J.Identifier durationIdent =
                    RecipeUtils.createSimpleIdentifier(
                        "Duration.ofSeconds(30)", "java.time.Duration");

                if (engineErrorWithCode.matches(throwStmt.getException())) {
                  maybeAddImport(RecipeConstants.Type.CLIENT_CAMUNDA_ERROR);

                  return clientBPMNErrorWithCodeWithMessage
                      .apply(
                          getCursor(),
                          throwStmt.getCoordinates().replace(),
                          newClass.getArguments().get(0),
                          addAnErrorMessage)
                      .withComments(List.of(variableComment, addErrorMessage));
                }

                if (engineErrorWithCodeWithMessage.matches(throwStmt.getException())) {
                  maybeAddImport(RecipeConstants.Type.CLIENT_CAMUNDA_ERROR);

                  return clientBPMNErrorWithCodeWithMessage
                      .apply(
                          getCursor(),
                          throwStmt.getCoordinates().replace(),
                          newClass.getArguments().get(0),
                          newClass.getArguments().get(1))
                      .withComments(List.of(variableComment));
                }

                if (engineErrorWithCodeWithMessageWithThrowable.matches(throwStmt.getException())) {
                  maybeAddImport(RecipeConstants.Type.CLIENT_CAMUNDA_ERROR);
                  maybeAddImport("java.util.HashMap");

                  return clientBPMNErrorWithCodeWithMessageWithVariablesWithThrowable
                      .apply(
                          getCursor(),
                          throwStmt.getCoordinates().replace(),
                          newClass.getArguments().get(0),
                          newClass.getArguments().get(1),
                          newHashMap,
                          newClass.getArguments().get(2))
                      .withComments(List.of(variableComment));
                }

                if (engineErrorWithCodeWithThrowable.matches(throwStmt.getException())) {
                  maybeAddImport(RecipeConstants.Type.CLIENT_CAMUNDA_ERROR);
                  maybeAddImport("java.util.HashMap");

                  return clientBPMNErrorWithCodeWithMessageWithVariablesWithThrowable
                      .apply(
                          getCursor(),
                          throwStmt.getCoordinates().replace(),
                          newClass.getArguments().get(0),
                          addAnErrorMessage,
                          newHashMap,
                          newClass.getArguments().get(1))
                      .withComments(List.of(variableComment, addErrorMessage));
                }

                if (engineException.matches(throwStmt.getException())) {
                  maybeAddImport(RecipeConstants.Type.CLIENT_CAMUNDA_ERROR);

                  return clientExceptionWithMessage.apply(
                      getCursor(), throwStmt.getCoordinates().replace(), addAnErrorMessage);
                }
                if (engineExceptionWithMessage.matches(throwStmt.getException())) {
                  maybeAddImport(RecipeConstants.Type.CLIENT_CAMUNDA_ERROR);

                  return clientExceptionWithMessage.apply(
                      getCursor(),
                      throwStmt.getCoordinates().replace(),
                      newClass.getArguments().get(0));
                }
                if (engineExceptionWithThrowable.matches(throwStmt.getException())) {
                  maybeAddImport(RecipeConstants.Type.CLIENT_CAMUNDA_ERROR);
                  maybeAddImport("java.util.HashMap");
                  maybeAddImport("java.time.Duration");

                  return clientExceptionWithMessageWithVariablesWithRetriesWithBackoffWithThrowable
                      .apply(
                          getCursor(),
                          throwStmt.getCoordinates().replace(),
                          addAnErrorMessage,
                          newHashMap,
                          retriesIdent,
                          durationIdent,
                          newClass.getArguments().get(0))
                      .withComments(List.of(retriesDurationComment));
                }
                if (engineExceptionWithMessageAndCode.matches(throwStmt.getException())) {
                  maybeAddImport(RecipeConstants.Type.CLIENT_CAMUNDA_ERROR);

                  return clientExceptionWithMessage
                      .apply(
                          getCursor(),
                          throwStmt.getCoordinates().replace(),
                          newClass.getArguments().get(0))
                      .withComments(List.of(noErrorCodeComment));
                }
                if (engineExceptionWithMessageWithThrowable.matches(throwStmt.getException())) {
                  maybeAddImport(RecipeConstants.Type.CLIENT_CAMUNDA_ERROR);
                  maybeAddImport("java.util.HashMap");
                  maybeAddImport("java.time.Duration");

                  return clientExceptionWithMessageWithVariablesWithRetriesWithBackoffWithThrowable
                      .apply(
                          getCursor(),
                          throwStmt.getCoordinates().replace(),
                          newClass.getArguments().get(0),
                          newHashMap,
                          retriesIdent,
                          durationIdent,
                          newClass.getArguments().get(1))
                      .withComments(List.of(retriesDurationComment));
                }
              }

              return super.visitThrow(throwStmt, ctx);
            }

            @Override
            public J visitStatement(Statement stmt, ExecutionContext ctx) {
              if (isInsideDelegateMethod()) {
                return super.visitStatement(stmt, ctx);
              }

              if (stmt instanceof J.VariableDeclarations variableDeclarations) {
                // assume one var
                J.VariableDeclarations.NamedVariable var =
                    variableDeclarations.getVariables().get(0);
                if (var.getInitializer() instanceof J.MethodInvocation methodInvocation) {
                  Statement newStatement =
                      (Statement) replaceIncidentCreation(methodInvocation, ctx);
                  if (newStatement != null) {
                    return newStatement;
                  }
                }
              }

              if (stmt instanceof J.MethodInvocation methodInvocation) {
                Statement newStatement = (Statement) replaceIncidentCreation(methodInvocation, ctx);
                if (newStatement != null) {
                  return newStatement;
                }
              }

              return super.visitStatement(stmt, ctx);
            }

            // createIncident(java.lang.String incidentType, java.lang.String configuration)
            final MethodMatcher engineCreateIncident =
                new MethodMatcher(
                    RecipeConstants.Method.CREATE_INCIDENT
                        + RecipeConstants.Parameters.build("String", "String"));

            // createIncident(java.lang.String incidentType, java.lang.String configuration,
            // java.lang.String message)
            final MethodMatcher engineCreateIncidentWithMessage =
                new MethodMatcher(
                    RecipeConstants.Method.CREATE_INCIDENT
                        + RecipeConstants.Parameters.build("String", "String", "String"));

            public J replaceIncidentCreation(
                J.MethodInvocation methodInvocation, ExecutionContext ctx) {

              Cursor statementCursor =
                  (getCursor().getValue() instanceof Statement)
                      ? getCursor()
                      : getCursor().dropParentUntil(Statement.class::isInstance);

              TextComment incidentNoRetries =
                  new TextComment(
                      false,
                      " incident is raised by throwing jobError with no retries",
                      "\n" + ((Statement) statementCursor.getValue()).getPrefix().getIndent(),
                      Markers.EMPTY);

              if (engineCreateIncident.matches(methodInvocation)) {
                maybeAddImport(RecipeConstants.Type.CLIENT_CAMUNDA_ERROR);

                Statement stmt =
                    clientExceptionWithMessageWithVariablesWithRetriesFixedMessageFixedMapIncident
                        .apply(
                            statementCursor,
                            ((Statement) statementCursor.getValue()).getCoordinates().replace())
                        .withPrefix(((Statement) statementCursor.getValue()).getPrefix());

                return stmt.withComments(List.of(incidentNoRetries));
                // return stmt;
              }

              if (engineCreateIncidentWithMessage.matches(methodInvocation)) {
                maybeAddImport(RecipeConstants.Type.CLIENT_CAMUNDA_ERROR);

                Statement stmt =
                    clientExceptionWithMessageWithVariablesWithRetriesFixedMapIncident
                        .apply(
                            statementCursor,
                            ((Statement) statementCursor.getValue()).getCoordinates().replace(),
                            methodInvocation.getArguments().get(2))
                        .withPrefix(((Statement) statementCursor.getValue()).getPrefix());

                return stmt.withComments(List.of(incidentNoRetries));
                // return stmt;
              }

              return null;
            }

            private boolean isInsideDelegateMethod() {
              J.MethodDeclaration enclosingMethod =
                  getCursor().firstEnclosing(J.MethodDeclaration.class);
              return enclosingMethod != null && "execute".equals(enclosingMethod.getSimpleName());
            }
          });
    }
  }
}
