package org.camunda.migration.rewrite.recipes.external;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.camunda.migration.rewrite.recipes.sharedRecipes.AbstractMigrationRecipe;
import org.camunda.migration.rewrite.recipes.utils.RecipeUtils;
import org.camunda.migration.rewrite.recipes.utils.ReplacementUtils;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

public class MigrateExternalWorkerRecipe extends Recipe {

  /** Instantiates a new instance. */
  public MigrateExternalWorkerRecipe() {}

  @Override
  public String getDisplayName() {
    return "Replaces all external worker methods";
  }

  @Override
  public String getDescription() {
    return "During preparation, a job worker was added to the class. This recipe copies and adjusts the external worker's code to the job worker.";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(
        new CopyExternalWorkerToJobWorkerRecipe(),
        new MigrateExternalTaskMethodsInJobWorker(),
        new MigrateExternalWorkerBPMNErrorAndExceptionInJobWorker());
  }

  private static class CopyExternalWorkerToJobWorkerRecipe extends Recipe {

    /** Instantiates a new instance. */
    public CopyExternalWorkerToJobWorkerRecipe() {}

    @Override
    public String getDisplayName() {
      return "Copy external worker code to job worker recipe";
    }

    @Override
    public String getDescription() {
      return "During preparation, a job worker was added to the class. This recipe copies the external worker code to the job worker.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

      // define preconditions
      TreeVisitor<?, ExecutionContext> check =
          Preconditions.and(
              new UsesType<>("io.camunda.spring.client.annotation.JobWorker", true),
              new UsesType<>("org.camunda.bpm.client.task.ExternalTask", true));

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
                    // all current statments (result map and return): ignored

                    // external worker body
                    List<Statement> externalWorkerStatements =
                        new ArrayList<>(delegateBody.getStatements());

                    // do not combine statements, drop dummy resultMap

                    // put together and rename job worker so recipe does not run twice
                    updatedStatements.add(
                        methDecl
                            .withBody(methDecl.getBody().withStatements(externalWorkerStatements))
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

  private static class MigrateExternalTaskMethodsInJobWorker extends AbstractMigrationRecipe {

    @Override
    public String getDisplayName() {
      return "Migrate various methods in job worker recipe";
    }

    @Override
    public String getDescription() {
      return "During a previous step, external worker code was copied into the job worker. This recipe migrates various methods.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> preconditions() {
      return Preconditions.and(
          new UsesType<>("io.camunda.spring.client.annotation.JobWorker", true),
          new UsesType<>("org.camunda.bpm.client.task.ExternalTask", true));
    }

    @Override
    protected Predicate<Cursor> visitorSkipCondition() {
      return cursor -> {
        J.MethodDeclaration m = cursor.firstEnclosing(J.MethodDeclaration.class);
        return m != null && "execute".equals(m.getSimpleName());
      };
    }

    @Override
    protected List<ReplacementUtils.SimpleReplacementSpec> simpleMethodInvocations() {
      return List.of(
          new ReplacementUtils.SimpleReplacementSpec(
                  new MethodMatcher(
                  // "getVariable(String variableName)"
                  "org.camunda.bpm.client.task.ExternalTask getVariable(java.lang.String)"),
                  RecipeUtils.createSimpleJavaTemplate(
                  "#{job:any(io.camunda.client.api.response.ActivatedJob)}.getVariable(#{any(java.lang.String)})"),
                  RecipeUtils.createSimpleIdentifier(
                  "job", "io.camunda.client.api.response.ActivatedJob"),
                  null,
                  ReplacementUtils.ReturnTypeStrategy.INFER_FROM_CONTEXT,
                  List.of(
                  new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                      "variableName", 0)),
                  Collections.emptyList()),
          new ReplacementUtils.SimpleReplacementSpec(
              new MethodMatcher(
                  // "getProcessInstanceId()"
                  "org.camunda.bpm.client.task.ExternalTask getProcessInstanceId()"),
              RecipeUtils.createSimpleJavaTemplate(
                  "String.valueOf(#{any(io.camunda.client.api.response.ActivatedJob)}.getProcessInstanceKey())"),
              RecipeUtils.createSimpleIdentifier(
                  "job", "io.camunda.client.api.response.ActivatedJob"),
              "java.lang.String",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              Collections.emptyList(),
              Collections.emptyList()),
          new ReplacementUtils.SimpleReplacementSpec(
              new MethodMatcher(
                  // "getProcessDefinitionId()"
                  "org.camunda.bpm.client.task.ExternalTask getProcessDefinitionId()"),
              RecipeUtils.createSimpleJavaTemplate(
                  "String.valueOf(#{any(io.camunda.client.api.response.ActivatedJob)}.getProcessDefinitionKey())"),
              RecipeUtils.createSimpleIdentifier(
                  "job", "io.camunda.client.api.response.ActivatedJob"),
              "java.lang.String",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              Collections.emptyList(),
              Collections.emptyList()),
          new ReplacementUtils.SimpleReplacementSpec(
              new MethodMatcher(
                  // "getCurrentActivityId()"
                  "org.camunda.bpm.client.task.ExternalTask getActivityId()"),
              RecipeUtils.createSimpleJavaTemplate(
                  "#{any(io.camunda.client.api.response.ActivatedJob)}.getElementId()"),
              RecipeUtils.createSimpleIdentifier(
                  "job", "io.camunda.client.api.response.ActivatedJob"),
              "java.lang.String",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              Collections.emptyList(),
              Collections.emptyList()),
          new ReplacementUtils.SimpleReplacementSpec(
              new MethodMatcher(
                  // "getActivityInstanceId()"
                  "org.camunda.bpm.client.task.ExternalTask getActivityInstanceId()"),
              RecipeUtils.createSimpleJavaTemplate(
                  "String.valueOf(#{any(io.camunda.client.api.response.ActivatedJob)}.getElementInstanceKey())"),
              RecipeUtils.createSimpleIdentifier(
                  "job", "io.camunda.client.api.response.ActivatedJob"),
              "java.lang.String",
              ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
              Collections.emptyList(),
              Collections.emptyList()));
    }

    @Override
    protected List<ReplacementUtils.BuilderReplacementSpec> builderMethodInvocations() {
      return Collections.emptyList();
    }

    @Override
    protected List<ReplacementUtils.ReturnReplacementSpec> returnMethodInvocations() {
      return Collections.emptyList();
    }


    @Override
    protected List<ReplacementUtils.RenameReplacementSpec> renameMethodInvocations() {
      return Collections.emptyList();
    }
  }

  private static class MigrateExternalWorkerBPMNErrorAndExceptionInJobWorker extends Recipe {

    /** Instantiates a new instance. */
    public MigrateExternalWorkerBPMNErrorAndExceptionInJobWorker() {}

    @Override
    public String getDisplayName() {
      return "Migrate BPMN error throwing code in job worker recipe";
    }

    @Override
    public String getDescription() {
      return "During a previous step, external worker code was copied into the job worker. This recipe migrates BPMN error throwing code.";
    }

    List<ReplacementUtils.SimpleReplacementSpec> errorSpecs =
        List.of(
            new ReplacementUtils.SimpleReplacementSpec(
                // handleBpmnError(ExternalTask externalTask, String errorCode)
                new MethodMatcher(
                    "org.camunda.bpm.client.task.ExternalTaskService handleBpmnError(org.camunda.bpm.client.task.ExternalTask, java.lang.String)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.bpmnError(#{any(java.lang.String)}, \"Add an error message here\")",
                    "io.camunda.spring.client.exception.CamundaError"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("errorCode", 1)),
                Collections.emptyList()),
            new ReplacementUtils.SimpleReplacementSpec(
                // handleBpmnError(ExternalTask externalTask, String errorCode, String errorMessage)
                new MethodMatcher(
                    "org.camunda.bpm.client.task.ExternalTaskService handleBpmnError(org.camunda.bpm.client.task.ExternalTask, java.lang.String, java.lang.String)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.bpmnError(#{any(java.lang.String)}, #{any(java.lang.String)})",
                    "io.camunda.spring.client.exception.CamundaError"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("errorCode", 1),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                        "errorMessage", 2)),
                Collections.emptyList()),
            new ReplacementUtils.SimpleReplacementSpec(
                // handleBpmnError(ExternalTask externalTask, String errorCode, String errorMessage,
                // Map<String, Object> variableMap)
                new MethodMatcher(
                    "org.camunda.bpm.client.task.ExternalTaskService handleBpmnError(org.camunda.bpm.client.task.ExternalTask, java.lang.String, java.lang.String, java.util.Map)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.bpmnError(#{any(java.lang.String)}, #{any(java.lang.String)}, #{any(java.util.Map)})",
                    "io.camunda.spring.client.exception.CamundaError",
                    "java.util.Map"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("errorCode", 1),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                        "errorMessage", 2),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                        "variableMap", 3)),
                Collections.emptyList()),
            new ReplacementUtils.SimpleReplacementSpec(
                // handleBpmnError(String externalTaskId, String errorCode, String errorMessage,
                // Map<String, Object> variableMap)
                new MethodMatcher(
                    "org.camunda.bpm.client.task.ExternalTaskService handleBpmnError(java.lang.String, java.lang.String, java.lang.String, java.util.Map)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.bpmnError(#{any(java.lang.String)}, #{any(java.lang.String)}, #{any(java.util.Map)})",
                    "io.camunda.spring.client.exception.CamundaError",
                    "java.util.Map"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("errorCode", 1),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                        "errorMessage", 2),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                        "variableMap", 3)),
                Collections.emptyList()),
            new ReplacementUtils.SimpleReplacementSpec(
                // handleFailure(ExternalTask externalTask, String errorMessage, String
                // errorDetails, int retries, long duration)
                new MethodMatcher(
                    "org.camunda.bpm.client.task.ExternalTaskService handleFailure(org.camunda.bpm.client.task.ExternalTask, java.lang.String, java.lang.String, int, long)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.jobError(#{any(java.lang.String)}, Collections.emptyMap(), Integer.valueOf(#{any()}), Duration.ofMillis(#{any()}))",
                    "io.camunda.spring.client.exception.CamundaError", "java.util.Collections", "java.time.Duration"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                        "errorMessage", 1),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("retries", 3),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("duration", 4)),
                List.of(" error details were removed")),
            new ReplacementUtils.SimpleReplacementSpec(
                // handleFailure(String externalTaskId, String errorMessage, String
                // errorDetails, int retries, long duration)
                new MethodMatcher(
                    "org.camunda.bpm.client.task.ExternalTaskService handleFailure(java.lang.String, java.lang.String, java.lang.String, int, long)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.jobError(#{any(java.lang.String)}, Collections.emptyMap(), Integer.valueOf(#{any()}), Duration.ofMillis(#{any()}))",
                    "io.camunda.spring.client.exception.CamundaError", "java.util.Collections", "java.time.Duration"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                        "errorMessage", 1),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("retries", 3),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("duration", 4)),
                List.of(" error details were removed")),
            new ReplacementUtils.SimpleReplacementSpec(
                // handleFailure(String externalTaskId, String errorMessage, String
                // errorDetails, int retries, long duration, Map<String, Object> variables,
                // Map<String, Object> localVariables)
                new MethodMatcher(
                    "org.camunda.bpm.client.task.ExternalTaskService handleFailure(java.lang.String, java.lang.String, java.lang.String, int, long, java.util.Map, java.util.Map)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.jobError(#{any(java.lang.String)}, #{any(java.util.Map)}, Integer.valueOf(#{any()}), Duration.ofMillis(#{any()}))",
                    "io.camunda.spring.client.exception.CamundaError", "java.util.Map", "java.time.Duration"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                        "errorMessage", 1),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                        "variableMap", 5),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("retries", 3),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("duration", 4)),
                List.of(" error details were removed", " local variables were removed")),
            new ReplacementUtils.SimpleReplacementSpec(
                // handleFailure(String externalTaskId, String errorMessage, String
                // errorDetails, int retries, long duration, Map<String, Object> variables,
                // Map<String, Object> localVariables)
                new MethodMatcher(
                    "org.camunda.bpm.client.task.ExternalTaskService handleFailure(java.lang.String, java.lang.String, java.lang.String, int, long, java.util.Map, java.util.Map)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.spring.client.exception.CamundaError)}.jobError(#{any(java.lang.String)}, #{any(java.util.Map)}, Integer.valueOf(#{any()}), Duration.ofMillis(#{any()}))",
                    "io.camunda.spring.client.exception.CamundaError", "java.util.Map", "java.time.Duration"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.spring.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                        "errorMessage", 1),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                        "variablsMap", 5),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("retries", 3),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("duration", 4)),
                List.of(" error details were removed", " local variables were removed")));

    List<ReplacementUtils.SimpleReplacementSpec> invocationSpecs =
        List.of(
            new ReplacementUtils.SimpleReplacementSpec(
                // getRetries()
                new MethodMatcher("org.camunda.bpm.client.task.ExternalTask getRetries()"),
                RecipeUtils.createSimpleJavaTemplate(
                    "#{job:any(io.camunda.client.api.response.ActivatedJob)}.getRetries()",
                    "io.camunda.client.api.response.ActivatedJob"),
                RecipeUtils.createSimpleIdentifier(
                    "job", "io.camunda.client.api.response.ActivatedJob"),
                "int",
                ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
                Collections.emptyList(),
                Collections.emptyList()),
            new ReplacementUtils.SimpleReplacementSpec(
                new MethodMatcher(
                    // "complete(ExternalTask externalTask, Map<String, Object> variableMap)"
                    "org.camunda.bpm.client.task.ExternalTaskService complete(org.camunda.bpm.client.task.ExternalTask, java.util.Map)"),
                RecipeUtils.createSimpleJavaTemplate("return #{any(java.util.Map)}"),
                null,
                "java.util.Map",
                ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                        "variableMap", 1)),
                Collections.emptyList()),
            new ReplacementUtils.SimpleReplacementSpec(
                new MethodMatcher(
                    // "complete(ExternalTask externalTask, Map<String, Object> variableMap,
                    // Map<String, Object> localVariables)"
                    "org.camunda.bpm.client.task.ExternalTaskService complete(org.camunda.bpm.client.task.ExternalTask, java.util.Map, java.util.Map)"),
                RecipeUtils.createSimpleJavaTemplate("return #{any(java.util.Map)}"),
                null,
                "java.util.Map",
                ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                        "variableMap", 1)),
                List.of(" local variables were removed")),
            new ReplacementUtils.SimpleReplacementSpec(
                new MethodMatcher(
                    // "complete(String externalTaskId, Map<String, Object> variableMap, Map<String,
                    // Object> localVariables)"
                    "org.camunda.bpm.client.task.ExternalTaskService complete(java.lang.String, java.util.Map, java.util.Map)"),
                RecipeUtils.createSimpleJavaTemplate("return #{any(java.util.Map)}"),
                null,
                "java.util.Map",
                ReplacementUtils.ReturnTypeStrategy.USE_SPECIFIED_TYPE,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                        "variableMap", 1)),
                List.of(" local variables were removed")));

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

      // define preconditions
      TreeVisitor<?, ExecutionContext> check =
          Preconditions.and(
              new UsesType<>("io.camunda.spring.client.annotation.JobWorker", true),
              new UsesType<>("org.camunda.bpm.client.task.ExternalTask", true));

      return Preconditions.check(
          check,
          new JavaVisitor<>() {

            @Override
            public J visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
              if (isInsideExternalWorkerMethod()) {
                return super.visitMethodInvocation(invocation, ctx);
              }

              for (ReplacementUtils.SimpleReplacementSpec spec : invocationSpecs) {
                if (spec.matcher().matches(invocation)) {

                  return spec.template()
                      .apply(
                          getCursor(),
                          invocation.getCoordinates().replace(),
                          ReplacementUtils.createArgs(
                              invocation, spec.baseIdentifier(), spec.argumentIndexes()))
                      .withComments(
                          Stream.concat(
                                  invocation.getComments().stream(),
                                  spec.textComments().stream()
                                      .map(
                                          text ->
                                              RecipeUtils.createSimpleComment(invocation, text)))
                              .toList());
                }
              }

              for (ReplacementUtils.SimpleReplacementSpec spec : errorSpecs) {
                if (spec.matcher().matches(invocation)) {
                  maybeAddImport("io.camunda.spring.client.exception.CamundaError");

                  J.Throw throwStmt =
                      spec.template()
                          .apply(
                              getCursor(),
                              invocation.getCoordinates().replace(),
                              ReplacementUtils.createArgs(
                                  invocation, spec.baseIdentifier(), spec.argumentIndexes()))
                          .withComments(
                              Stream.concat(
                                      invocation.getComments().stream(),
                                      spec.textComments().stream()
                                          .map(
                                              text ->
                                                  RecipeUtils.createSimpleComment(
                                                      invocation, text)))
                                  .toList());

                  return visit(throwStmt, ctx);
                }
              }
              return super.visitMethodInvocation(invocation, ctx);
            }

            private boolean isInsideExternalWorkerMethod() {
              J.MethodDeclaration enclosingMethod =
                  getCursor().firstEnclosing(J.MethodDeclaration.class);
              return enclosingMethod != null && "execute".equals(enclosingMethod.getSimpleName());
            }
          });
    }
  }
}
