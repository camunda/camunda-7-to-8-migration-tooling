package org.camunda.migration.rewrite.recipes.delegate.migrate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.camunda.migration.rewrite.recipes.sharedRecipes.AbstractMigrationRecipe;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

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
        new MigrateDelegateExecutionMethodsInJobWorker(),
        new MigrateDelegateBPMNErrorAndExceptionInJobWorker());
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
              new UsesType<>("io.camunda.spring.client.annotation.JobWorker", true),
              new UsesType<>("org.camunda.bpm.engine.delegate.JavaDelegate", true));

      return Preconditions.check(
          check,
          new JavaIsoVisitor<>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(
                J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
              // Skip interfaces
              if (classDeclaration.getKind() != J.ClassDeclaration.Kind.Type.Class) {
                return super.visitClassDeclaration(classDeclaration, ctx);
              }

              List<Statement> currentStatements = classDeclaration.getBody().getStatements();
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
                return classDeclaration.withBody(
                    classDeclaration.getBody().withStatements(updatedStatements));
              }
              return super.visitClassDeclaration(classDeclaration, ctx);
            }
          });
    }
  }

  private static class MigrateDelegateExecutionMethodsInJobWorker extends AbstractMigrationRecipe {

    @Override
    public String getDisplayName() {
      return "Migrate variable handling code in job worker recipe";
    }

    @Override
    public String getDescription() {
      return "During a previous step, delegate code was copied into the job worker. This recipe migrates variable handling code.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> preconditions() {
      return Preconditions.and(
          new UsesType<>("io.camunda.spring.client.annotation.JobWorker", true),
          new UsesType<>("org.camunda.bpm.engine.delegate.JavaDelegate", true));
    }

    @Override
    protected Predicate<Cursor> visitorSkipCondition() {
      return cursor -> {
        J.MethodDeclaration m = cursor.firstEnclosing(J.MethodDeclaration.class);
        return m != null && "execute".equals(m.getSimpleName());
      };
    }

    @Override
    protected List<RecipeUtils.MethodInvocationSimpleReplacementSpec> simpleMethodInvocations() {
      return List.of(
          new RecipeUtils.MethodInvocationSimpleReplacementSpec(
              new MethodMatcher(
                  // "getVariable(String variableName)"
                  "org.camunda.bpm.engine.delegate.VariableScope getVariable(java.lang.String)"),
              RecipeUtils.createSimpleJavaTemplate(
                  "#{job:any(io.camunda.client.api.response.ActivatedJob)}.getVariable(#{any(java.lang.String)})"),
              RecipeUtils.createSimpleIdentifier(
                  "job", "io.camunda.client.api.response.ActivatedJob"),
              null,
              RecipeUtils.ReturnTypeStrategy.INFER_FROM_CONTEXT,
              List.of(
                  new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg(
                      "variableName", 0)),
              Collections.emptyList()),
          new RecipeUtils.MethodInvocationSimpleReplacementSpec(
              new MethodMatcher(
                  // "getVariableLocal(String variableName)"
                  "org.camunda.bpm.engine.delegate.VariableScope getVariableLocal(java.lang.String)"),
              RecipeUtils.createSimpleJavaTemplate(
                  "#{job:any(io.camunda.client.api.response.ActivatedJob)}.getVariable(#{any(java.lang.String)})"),
              RecipeUtils.createSimpleIdentifier(
                  "job", "io.camunda.client.api.response.ActivatedJob"),
              null,
              RecipeUtils.ReturnTypeStrategy.INFER_FROM_CONTEXT,
              List.of(
                  new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg(
                      "variableName", 0)),
              Collections.emptyList()),
          new RecipeUtils.MethodInvocationSimpleReplacementSpec(
              new MethodMatcher(
                  // "setVariable(String variableName, Object value)"
                  "org.camunda.bpm.engine.delegate.VariableScope setVariable(java.lang.String, java.lang.Object)"),
              RecipeUtils.createSimpleJavaTemplate(
                  "#{resultMap:any(java.util.Map)}.put(#{any(java.lang.String)}, #{any(java.lang.Object)})"),
              RecipeUtils.createSimpleIdentifier("resultMap", "java.util.Map"),
              null,
              RecipeUtils.ReturnTypeStrategy.VOID,
              List.of(
                  new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("variableName", 0),
                  new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("value", 1)),
              Collections.emptyList()),
          new RecipeUtils.MethodInvocationSimpleReplacementSpec(
              new MethodMatcher(
                  // "setVariableLocal(String variableName, Object value)"
                  "org.camunda.bpm.engine.delegate.VariableScope setVariableLocal(java.lang.String, java.lang.Object)"),
              RecipeUtils.createSimpleJavaTemplate(
                  "#{resultMap:any(java.util.Map)}.put(#{any(java.lang.String)}, #{any(java.lang.Object)})"),
              RecipeUtils.createSimpleIdentifier("resultMap", "java.util.Map"),
              null,
              RecipeUtils.ReturnTypeStrategy.VOID,
              List.of(
                  new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("variableName", 0),
                  new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("value", 1)),
              Collections.emptyList()),
          new RecipeUtils.MethodInvocationSimpleReplacementSpec(
              new MethodMatcher(
                  // "getProcessInstanceId()"
                  "org.camunda.bpm.engine.delegate.DelegateExecution getProcessInstanceId()"),
              RecipeUtils.createSimpleJavaTemplate(
                  "String.valueOf(#{any(io.camunda.client.api.response.ActivatedJob)}.getProcessInstanceKey())"),
              RecipeUtils.createSimpleIdentifier(
                  "job", "io.camunda.client.api.response.ActivatedJob"),
              "java.lang.String",
              RecipeUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              Collections.emptyList(),
              Collections.emptyList()),
          new RecipeUtils.MethodInvocationSimpleReplacementSpec(
              new MethodMatcher(
                  // "getProcessDefinitionId()"
                  "org.camunda.bpm.engine.delegate.DelegateExecution getProcessDefinitionId()"),
              RecipeUtils.createSimpleJavaTemplate(
                  "String.valueOf(#{any(io.camunda.client.api.response.ActivatedJob)}.getProcessDefinitionKey())"),
              RecipeUtils.createSimpleIdentifier(
                  "job", "io.camunda.client.api.response.ActivatedJob"),
              "java.lang.String",
              RecipeUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              Collections.emptyList(),
              Collections.emptyList()),
          new RecipeUtils.MethodInvocationSimpleReplacementSpec(
              new MethodMatcher(
                  // "getCurrentActivityId()"
                  "org.camunda.bpm.engine.delegate.DelegateExecution getCurrentActivityId()"),
              RecipeUtils.createSimpleJavaTemplate(
                  "#{any(io.camunda.client.api.response.ActivatedJob)}.getElementId()"),
              RecipeUtils.createSimpleIdentifier(
                  "job", "io.camunda.client.api.response.ActivatedJob"),
              "java.lang.String",
              RecipeUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              Collections.emptyList(),
              Collections.emptyList()),
          new RecipeUtils.MethodInvocationSimpleReplacementSpec(
              new MethodMatcher(
                  // "getActivityInstanceId()"
                  "org.camunda.bpm.engine.delegate.DelegateExecution getActivityInstanceId()"),
              RecipeUtils.createSimpleJavaTemplate(
                  "String.valueOf(#{any(io.camunda.client.api.response.ActivatedJob)}.getElementInstanceKey())"),
              RecipeUtils.createSimpleIdentifier(
                  "job", "io.camunda.client.api.response.ActivatedJob"),
              "java.lang.String",
              RecipeUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              Collections.emptyList(),
              Collections.emptyList()));
    }

    @Override
    protected List<RecipeUtils.MethodInvocationBuilderReplacementSpec> builderMethodInvocations() {
      return Collections.emptyList();
    }

    @Override
    protected List<RecipeUtils.MethodInvocationReturnReplacementSpec> returnMethodInvocations() {
      return Collections.emptyList();
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

    List<RecipeUtils.MethodInvocationSimpleReplacementSpec> errorSpecs =
        List.of(
            new RecipeUtils.MethodInvocationSimpleReplacementSpec(
                // BpmnError(java.lang.String errorCode)
                new MethodMatcher(
                    "org.camunda.bpm.engine.delegate.BpmnError <constructor>(java.lang.String)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.bpmnError(#{any(java.lang.String)}, \"Add an error message here\")",
                    "io.camunda.spring.client.exception.CamundaError"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                RecipeUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("errorCode", 0)),
                Collections.emptyList()),
            new RecipeUtils.MethodInvocationSimpleReplacementSpec(
                // BpmnError(java.lang.String errorCode, java.lang.String errorMessage)
                new MethodMatcher(
                    "org.camunda.bpm.engine.delegate.BpmnError <constructor>(java.lang.String, java.lang.String)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.bpmnError(#{any(java.lang.String)}, #{any(java.lang.String)})",
                    "io.camunda.spring.client.exception.CamundaError"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                RecipeUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("errorCode", 0),
                    new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg(
                        "errorMessage", 1)),
                Collections.emptyList()),
            new RecipeUtils.MethodInvocationSimpleReplacementSpec(
                // BpmnError(java.lang.String errorCode, java.lang.String errorMessage,
                // java.lang.Throwable throwable)
                new MethodMatcher(
                    "org.camunda.bpm.engine.delegate.BpmnError <constructor>(java.lang.String, java.lang.String, java.lang.Throwable)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.bpmnError(#{any(java.lang.String)}, #{any(java.lang.String)}, Collections.emptyMap(), #{any(java.lang.Throwable)})",
                    "io.camunda.spring.client.exception.CamundaError",
                    "java.util.Collections"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                RecipeUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("errorCode", 0),
                    new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg(
                        "errorMessage", 1),
                    new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("throwable", 2)),
                Collections.emptyList()),
            new RecipeUtils.MethodInvocationSimpleReplacementSpec(
                // BpmnError(java.lang.String errorCode, java.lang.Throwable cause)
                new MethodMatcher(
                    "org.camunda.bpm.engine.delegate.BpmnError <constructor>(java.lang.String, java.lang.Throwable)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.bpmnError(#{any(java.lang.String)}, \"Add an error message here\", Collections.emptyMap(), #{any(java.lang.Throwable)})",
                    "io.camunda.spring.client.exception.CamundaError",
                    "java.util.Collections"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                RecipeUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("errorCode", 0),
                    new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("throwable", 1)),
                Collections.emptyList()),
            new RecipeUtils.MethodInvocationSimpleReplacementSpec(
                // ProcessEngineException()
                new MethodMatcher("org.camunda.bpm.engine.ProcessEngineException <constructor>()"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.jobError(\"Add an error message here\")",
                    "io.camunda.spring.client.exception.CamundaError"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                RecipeUtils.ReturnTypeStrategy.VOID,
                Collections.emptyList(),
                Collections.emptyList()),
            new RecipeUtils.MethodInvocationSimpleReplacementSpec(
                // ProcessEngineException(java.lang.String message)
                new MethodMatcher(
                    "org.camunda.bpm.engine.ProcessEngineException <constructor>(java.lang.String)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.jobError(#{any(java.lang.String)})",
                    "io.camunda.spring.client.exception.CamundaError"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                RecipeUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("message", 0)),
                Collections.emptyList()),
            new RecipeUtils.MethodInvocationSimpleReplacementSpec(
                // ProcessEngineException(java.lang.String message, java.lang.Throwable throwable)
                new MethodMatcher(
                    "org.camunda.bpm.engine.ProcessEngineException <constructor>(java.lang.String, java.lang.Throwable)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.jobError(#{any(String)}, Collections.emptyMap(), 3, Duration.ofSeconds(30), #{any(java.lang.Throwable)})",
                    "io.camunda.spring.client.exception.CamundaError",
                    "java.util.Collections",
                    "java.time.Duration"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                RecipeUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("message", 0),
                    new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("throwable", 1)),
                List.of(" set retries with job.getRetries() - 1")),
            new RecipeUtils.MethodInvocationSimpleReplacementSpec(
                // ProcessEngineException(java.lang.String message, int code)
                new MethodMatcher(
                    "org.camunda.bpm.engine.ProcessEngineException <constructor>(java.lang.String, int)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.jobError(#{any(String)})",
                    "io.camunda.spring.client.exception.CamundaError"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                RecipeUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("message", 0)),
                List.of(" error code was removed")),
            new RecipeUtils.MethodInvocationSimpleReplacementSpec(
                // ProcessEngineException(java.lang.Throwable throwable)
                new MethodMatcher(
                    "org.camunda.bpm.engine.ProcessEngineException <constructor>(java.lang.Throwable)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.jobError(\"Add an error message here\", Collections.emptyMap(), 3, Duration.ofSeconds(30), #{any(java.lang.Throwable)})",
                    "io.camunda.spring.client.exception.CamundaError",
                    "java.util.Collections",
                    "java.time.Duration"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                RecipeUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("throwable", 0)),
                Collections.emptyList()));

    List<RecipeUtils.MethodInvocationSimpleReplacementSpec> incidentSpecs =
        List.of(
            new RecipeUtils.MethodInvocationSimpleReplacementSpec(
                // createIncident(java.lang.String incidentType, java.lang.String configuration)
                new MethodMatcher(
                    "org.camunda.bpm.engine.delegate.DelegateExecution createIncident(java.lang.String, java.lang.String)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.jobError(\"Add an error message here\", Collections.emptyMap(), 0)",
                    "io.camunda.spring.client.exception.CamundaError",
                    "java.util.Collections"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                RecipeUtils.ReturnTypeStrategy.VOID,
                Collections.emptyList(),
                List.of(
                    " incidentType was removed",
                    " configuration was removed",
                    " incident created by retries being 0")),
            new RecipeUtils.MethodInvocationSimpleReplacementSpec(
                // createIncident(java.lang.String incidentType, java.lang.String configuration,
                // java.lang.String message)
                new MethodMatcher(
                    "org.camunda.bpm.engine.delegate.DelegateExecution createIncident(java.lang.String, java.lang.String, java.lang.String)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.jobError(#{any(java.lang.String)}, Collections.emptyMap(), 0)",
                    "io.camunda.spring.client.exception.CamundaError",
                    "java.util.Collections"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                RecipeUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new RecipeUtils.MethodInvocationSimpleReplacementSpec.NamedArg("message", 0)),
                List.of(
                    " incidentType was removed",
                    " configuration was removed",
                    " incident created by retries being 0")));

    List<RecipeUtils.MethodInvocationReplacementSpec> commonSpecs =
        Stream.concat(
                errorSpecs.stream().map(spec -> (RecipeUtils.MethodInvocationReplacementSpec) spec),
                incidentSpecs.stream()
                    .map(spec -> (RecipeUtils.MethodInvocationReplacementSpec) spec))
            .toList();

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

      // define preconditions
      TreeVisitor<?, ExecutionContext> check =
          Preconditions.and(
              new UsesType<>("io.camunda.spring.client.annotation.JobWorker", true),
              new UsesType<>("org.camunda.bpm.engine.delegate.JavaDelegate", true));

      return Preconditions.check(
          check,
          new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitThrow(J.Throw throwStmt, ExecutionContext ctx) {
              if (isInsideDelegateMethod()) {
                return super.visitThrow(throwStmt, ctx);
              }

              Expression exception = throwStmt.getException();
              if (exception instanceof J.NewClass newClass) {

                for (RecipeUtils.MethodInvocationSimpleReplacementSpec spec : errorSpecs) {
                  if (spec.matcher().matches(newClass)) {

                    maybeAddImport("io.camunda.spring.client.exception.CamundaError");

                    return maybeAutoFormat(
                        throwStmt,
                        spec.template()
                            .apply(
                                getCursor(),
                                throwStmt.getCoordinates().replace(),
                                RecipeUtils.createArgs(
                                    newClass, spec.baseIdentifier(), spec.argumentIndexes())),
                        ctx);
                  }
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
                  for (RecipeUtils.MethodInvocationReplacementSpec spec : commonSpecs) {
                    if (spec.matcher().matches(methodInvocation)) {
                      Statement newStatement =
                          (Statement) replaceIncidentCreation(methodInvocation, ctx);
                      if (newStatement != null) {

                        newStatement =
                            newStatement.withComments(
                                Stream.concat(
                                        stmt.getComments().stream(),
                                        spec.textComments().stream()
                                            .map(
                                                text ->
                                                    RecipeUtils.createSimpleComment(stmt, text)))
                                    .toList());

                        return newStatement;
                      }
                    }
                  }
                }
              }

              if (stmt instanceof J.MethodInvocation methodInvocation) {
                for (RecipeUtils.MethodInvocationReplacementSpec spec : commonSpecs) {
                  if (spec.matcher().matches(methodInvocation)) {

                    Statement newStatement =
                        (Statement) replaceIncidentCreation(methodInvocation, ctx);
                    if (newStatement != null) {

                      newStatement =
                          newStatement.withComments(
                              Stream.concat(
                                      stmt.getComments().stream(),
                                      spec.textComments().stream()
                                          .map(text -> RecipeUtils.createSimpleComment(stmt, text)))
                                  .toList());

                      return newStatement;
                    }
                  }
                }
              }

              return super.visitStatement(stmt, ctx);
            }

            public J replaceIncidentCreation(
                J.MethodInvocation methodInvocation, ExecutionContext ctx) {

              Cursor statementCursor =
                  (getCursor().getValue() instanceof Statement)
                      ? getCursor()
                      : getCursor().dropParentUntil(Statement.class::isInstance);

              for (RecipeUtils.MethodInvocationSimpleReplacementSpec specs : incidentSpecs) {
                if (specs.matcher().matches(methodInvocation)) {

                  Statement statement =
                      specs
                          .template()
                          .apply(
                              statementCursor,
                              ((Statement) statementCursor.getValue()).getCoordinates().replace(),
                              RecipeUtils.createArgs(
                                  methodInvocation,
                                  specs.baseIdentifier(),
                                  specs.argumentIndexes()));

                  maybeAddImport("io.camunda.spring.client.exception.CamundaError");

                  return maybeAutoFormat(methodInvocation, statement, ctx);
                }
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
