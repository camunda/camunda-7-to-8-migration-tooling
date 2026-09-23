/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.delegate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import io.camunda.migration.code.recipes.sharedRecipes.AbstractMigrationRecipe;
import io.camunda.migration.code.recipes.utils.RecipeUtils;
import io.camunda.migration.code.recipes.utils.ReplacementUtils;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

public class MigrateExecutionRecipe extends Recipe {

  /**
   * Sentinel shared with cleanup recipes so that warning comments about lost delegate business
   * logic can be detected reliably.
   */
  public static final String DELEGATE_BODY_COPY_WARNING_SENTINEL =
      "The delegate body could not be copied automatically";

  /** Instantiates a new instance. */
  public MigrateExecutionRecipe() {}

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
        new CopyExecutionListenerToJobWorkerRecipe(),
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

      // Only run on classes that implement JavaDelegate. Requiring the @JobWorker annotation here
      // is fragile because the annotation is injected by AllDelegatePrepareRecipes, and newer
      // OpenRewrite versions may evaluate this precondition against an outdated LST.
      TreeVisitor<?, ExecutionContext> check =
          new UsesType<>("org.camunda.bpm.engine.delegate.JavaDelegate", true);

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

              if (!isJavaDelegateAssignable(classDeclaration)) {
                return super.visitClassDeclaration(classDeclaration, ctx);
              }

              List<Statement> currentStatements = classDeclaration.getBody().getStatements();
              J.MethodDeclaration delegateMethod = null;
              J.MethodDeclaration jobWorkerMethod = null;
              J.MethodDeclaration alreadyMigratedMethod = null;

              for (Statement stmt : currentStatements) {
                if (stmt instanceof J.MethodDeclaration methDecl) {
                  if (methDecl.getSimpleName().equals("execute")) {
                    delegateMethod = methDecl;
                  } else if (methDecl.getSimpleName().equals("executeJob")) {
                    jobWorkerMethod = methDecl;
                  } else if (methDecl.getSimpleName().equals("executeJobMigrated")) {
                    alreadyMigratedMethod = methDecl;
                  }
                }
              }

              if (alreadyMigratedMethod != null) {
                // The copy has already happened in a previous cycle; keep traversing in case
                // there are nested delegate/listener classes that still need to be processed.
                return super.visitClassDeclaration(classDeclaration, ctx);
              }

              String warning = null;

              if (delegateMethod != null && jobWorkerMethod != null) {
                J.Block delegateBody = delegateMethod.getBody();
                J.Block jobWorkerBody = jobWorkerMethod.getBody();

                boolean canCopy =
                    delegateBody != null
                        && jobWorkerBody != null
                        && jobWorkerBody.getStatements().size() == 2;

                if (canCopy) {
                  // all current statements (result map and return)
                  List<Statement> jobWorkerStatements = jobWorkerBody.getStatements();

                  // delegate body
                  List<Statement> delegateStatements =
                      new ArrayList<>(delegateBody.getStatements());

                  // combine statements
                  delegateStatements.add(0, jobWorkerStatements.get(0));
                  delegateStatements.add(jobWorkerStatements.get(jobWorkerStatements.size() - 1));

                  J.MethodDeclaration migratedJobWorker =
                      jobWorkerMethod
                          .withBody(jobWorkerMethod.getBody().withStatements(delegateStatements))
                          .withName(jobWorkerMethod.getName().withSimpleName("executeJobMigrated"))
                          .withMethodType(
                              jobWorkerMethod.getMethodType().withName("executeJobMigrated"));

                  List<Statement> updatedStatements = new ArrayList<>();
                  for (Statement stmt : currentStatements) {
                    if (stmt == jobWorkerMethod) {
                      updatedStatements.add(migratedJobWorker);
                    } else {
                      updatedStatements.add(stmt);
                    }
                  }

                  return super.visitClassDeclaration(
                      classDeclaration.withBody(
                          classDeclaration.getBody().withStatements(updatedStatements)),
                      ctx);
                }

                warning =
                    "Could not copy delegate body: execute(DelegateExecution) or executeJob(ActivatedJob)"
                        + " is not in the expected shape. "
                        + DELEGATE_BODY_COPY_WARNING_SENTINEL
                        + "; migrate the logic manually.";
              }

              if (warning == null) {
                if (delegateMethod != null && jobWorkerMethod == null) {
                  warning =
                      "The delegate execute(DelegateExecution) method exists, but no generated"
                          + " executeJob(ActivatedJob) stub was found. The delegate body could not"
                          + " be copied automatically; migrate it manually.";
                } else if (delegateMethod == null && jobWorkerMethod != null) {
                  warning =
                      "No execute(DelegateExecution) method was found directly in this class. If it"
                          + " lives in a superclass, "
                          + DELEGATE_BODY_COPY_WARNING_SENTINEL
                          + "; migrate it manually.";
                } else {
                  warning =
                      "Neither execute(DelegateExecution) nor executeJob(ActivatedJob) was found."
                          + " The delegate body could not be copied automatically; migrate it"
                          + " manually.";
                }
              }

              List<Comment> existingComments =
                  classDeclaration.getComments() == null
                      ? Collections.emptyList()
                      : classDeclaration.getComments();
              boolean alreadyWarned =
                  existingComments.stream()
                      .filter(c -> c instanceof TextComment)
                      .map(c -> (TextComment) c)
                      .anyMatch(
                          c -> c.getText().contains(DELEGATE_BODY_COPY_WARNING_SENTINEL));
              if (alreadyWarned) {
                // Keep traversing so any nested delegate/listener classes are still processed.
                return super.visitClassDeclaration(classDeclaration, ctx);
              }

              List<Comment> updatedComments = new ArrayList<>(existingComments);
              updatedComments.add(
                  new TextComment(true, " " + warning, "\n", Markers.EMPTY));
              return super.visitClassDeclaration(
                  classDeclaration.withComments(updatedComments), ctx);
            }
          });
    }
  }

  private static boolean isJavaDelegateAssignable(J.ClassDeclaration classDeclaration) {
    return RecipeUtils.isAssignableTo(
        classDeclaration.getType(), "org.camunda.bpm.engine.delegate.JavaDelegate");
  }

  private static class CopyExecutionListenerToJobWorkerRecipe extends Recipe {

    public CopyExecutionListenerToJobWorkerRecipe() {}

    @Override
    public String getDisplayName() {
      return "Copy ExecutionListener code to job worker recipe";
    }

    @Override
    public String getDescription() {
      return "Copies ExecutionListener notify() logic into the generated job worker method.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

      TreeVisitor<?, ExecutionContext> precondition =
          Preconditions.and(
              new UsesType<>("io.camunda.client.annotation.JobWorker", true),
              new UsesType<>("org.camunda.bpm.engine.delegate.ExecutionListener", true));

      return Preconditions.check(
          precondition,
          new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(
                J.ClassDeclaration classDecl, ExecutionContext ctx) {

              if (classDecl.getKind() != J.ClassDeclaration.Kind.Type.Class) {
                return super.visitClassDeclaration(classDecl, ctx);
              }

              List<Statement> current = classDecl.getBody().getStatements();
              List<Statement> updated = new ArrayList<>();

              // 1) find notify(...) body
              J.Block notifyBody = null;
              for (Statement stmt : current) {
                if (stmt instanceof J.MethodDeclaration m
                    && "notify".equals(m.getSimpleName())) {
                  notifyBody = m.getBody();
                }
              }

              if (notifyBody != null) {
                for (Statement stmt : current) {
                  if (stmt instanceof J.MethodDeclaration m
                      && "executeJob".equals(m.getSimpleName())) {

                    J.Block jobBody = m.getBody();
                    List<Statement> jobStmts = jobBody.getStatements();
                    List<Statement> listenerStmts =
                        new ArrayList<>(notifyBody.getStatements());

                    // keep resultMap init + return, wrap listener logic in between
                    listenerStmts.add(0, jobStmts.get(0));
                    listenerStmts.add(jobStmts.get(jobStmts.size() - 1));

                    updated.add(
                        m.withBody(jobBody.withStatements(listenerStmts))
                            .withName(m.getName().withSimpleName("executeJobMigrated"))
                            .withMethodType(
                                m.getMethodType().withName("executeJobMigrated")));
                  } else {
                    updated.add(stmt);
                  }
                }
                return classDecl.withBody(classDecl.getBody().withStatements(updated));
              }

              return super.visitClassDeclaration(classDecl, ctx);
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
          new UsesType<>("io.camunda.client.annotation.JobWorker", true),
          Preconditions.or(
              new UsesType<>("org.camunda.bpm.engine.delegate.JavaDelegate", true),
              new UsesType<>("org.camunda.bpm.engine.delegate.ExecutionListener", true)));
    }

    @Override
    protected Predicate<Cursor> visitorSkipCondition() {
      return cursor -> {
        J.MethodDeclaration m = cursor.firstEnclosing(J.MethodDeclaration.class);
        return m != null
            && ("execute".equals(m.getSimpleName())
                || "notify".equals(m.getSimpleName()));
      };
    }

    @Override
    protected List<ReplacementUtils.SimpleReplacementSpec> simpleMethodInvocations() {
      return List.of(
          new ReplacementUtils.SimpleReplacementSpec(
              new MethodMatcher(
                  // "getVariable(String variableName)"
                  "org.camunda.bpm.engine.delegate.VariableScope getVariable(java.lang.String)"),
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
                  // "getVariableLocal(String variableName)"
                  "org.camunda.bpm.engine.delegate.VariableScope getVariableLocal(java.lang.String)"),
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
                  // "setVariable(String variableName, Object value)"
                  "org.camunda.bpm.engine.delegate.VariableScope setVariable(java.lang.String, java.lang.Object)"),
              RecipeUtils.createSimpleJavaTemplate(
                  "#{resultMap:any(java.util.Map)}.put(#{any(java.lang.String)}, #{any(java.lang.Object)})"),
              RecipeUtils.createSimpleIdentifier("resultMap", "java.util.Map"),
              null,
              ReplacementUtils.ReturnTypeStrategy.VOID,
              List.of(
                  new ReplacementUtils.SimpleReplacementSpec.NamedArg("variableName", 0),
                  new ReplacementUtils.SimpleReplacementSpec.NamedArg("value", 1)),
              Collections.emptyList()),
          new ReplacementUtils.SimpleReplacementSpec(
              new MethodMatcher(
                  // "setVariableLocal(String variableName, Object value)"
                  "org.camunda.bpm.engine.delegate.VariableScope setVariableLocal(java.lang.String, java.lang.Object)"),
              RecipeUtils.createSimpleJavaTemplate(
                  "#{resultMap:any(java.util.Map)}.put(#{any(java.lang.String)}, #{any(java.lang.Object)})"),
              RecipeUtils.createSimpleIdentifier("resultMap", "java.util.Map"),
              null,
              ReplacementUtils.ReturnTypeStrategy.VOID,
              List.of(
                  new ReplacementUtils.SimpleReplacementSpec.NamedArg("variableName", 0),
                  new ReplacementUtils.SimpleReplacementSpec.NamedArg("value", 1)),
              Collections.emptyList()),
          new ReplacementUtils.SimpleReplacementSpec(
              new MethodMatcher(
                  // "getProcessInstanceId()"
                  "org.camunda.bpm.engine.delegate.DelegateExecution getProcessInstanceId()"),
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
                  "org.camunda.bpm.engine.delegate.DelegateExecution getProcessDefinitionId()"),
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
                  "org.camunda.bpm.engine.delegate.DelegateExecution getCurrentActivityId()"),
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
                  "org.camunda.bpm.engine.delegate.DelegateExecution getActivityInstanceId()"),
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

    List<ReplacementUtils.SimpleReplacementSpec> errorSpecs =
        List.of(
            new ReplacementUtils.SimpleReplacementSpec(
                // BpmnError(java.lang.String errorCode)
                new MethodMatcher(
                    "org.camunda.bpm.engine.delegate.BpmnError <constructor>(java.lang.String)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.client.exception.CamundaError)}.bpmnError(#{any(java.lang.String)}, \"Add an error message here\")",
                    "io.camunda.client.exception.CamundaError"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("errorCode", 0)),
                Collections.emptyList()),
            new ReplacementUtils.SimpleReplacementSpec(
                // BpmnError(java.lang.String errorCode, java.lang.String errorMessage)
                new MethodMatcher(
                    "org.camunda.bpm.engine.delegate.BpmnError <constructor>(java.lang.String, java.lang.String)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.client.exception.CamundaError)}.bpmnError(#{any(java.lang.String)}, #{any(java.lang.String)})",
                    "io.camunda.client.exception.CamundaError"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("errorCode", 0),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                        "errorMessage", 1)),
                Collections.emptyList()),
            new ReplacementUtils.SimpleReplacementSpec(
                // BpmnError(java.lang.String errorCode, java.lang.String errorMessage,
                // java.lang.Throwable throwable)
                new MethodMatcher(
                    "org.camunda.bpm.engine.delegate.BpmnError <constructor>(java.lang.String, java.lang.String, java.lang.Throwable)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.client.exception.CamundaError)}.bpmnError(#{any(java.lang.String)}, #{any(java.lang.String)}, Collections.emptyMap(), #{any(java.lang.Throwable)})",
                    "io.camunda.client.exception.CamundaError",
                    "java.util.Collections"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("errorCode", 0),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg(
                        "errorMessage", 1),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("throwable", 2)),
                Collections.emptyList()),
            new ReplacementUtils.SimpleReplacementSpec(
                // BpmnError(java.lang.String errorCode, java.lang.Throwable cause)
                new MethodMatcher(
                    "org.camunda.bpm.engine.delegate.BpmnError <constructor>(java.lang.String, java.lang.Throwable)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.client.exception.CamundaError)}.bpmnError(#{any(java.lang.String)}, \"Add an error message here\", Collections.emptyMap(), #{any(java.lang.Throwable)})",
                    "io.camunda.client.exception.CamundaError",
                    "java.util.Collections"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("errorCode", 0),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("throwable", 1)),
                Collections.emptyList()),
            new ReplacementUtils.SimpleReplacementSpec(
                // ProcessEngineException()
                new MethodMatcher("org.camunda.bpm.engine.ProcessEngineException <constructor>()"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.client.exception.CamundaError)}.jobError(\"Add an error message here\")",
                    "io.camunda.client.exception.CamundaError"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                Collections.emptyList(),
                Collections.emptyList()),
            new ReplacementUtils.SimpleReplacementSpec(
                // ProcessEngineException(java.lang.String message)
                new MethodMatcher(
                    "org.camunda.bpm.engine.ProcessEngineException <constructor>(java.lang.String)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.client.exception.CamundaError)}.jobError(#{any(java.lang.String)})",
                    "io.camunda.client.exception.CamundaError"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("message", 0)),
                Collections.emptyList()),
            new ReplacementUtils.SimpleReplacementSpec(
                // ProcessEngineException(java.lang.String message, java.lang.Throwable throwable)
                new MethodMatcher(
                    "org.camunda.bpm.engine.ProcessEngineException <constructor>(java.lang.String, java.lang.Throwable)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.client.exception.CamundaError)}.jobError(#{any(String)}, Collections.emptyMap(), 3, Duration.ofSeconds(30), #{any(java.lang.Throwable)})",
                    "io.camunda.client.exception.CamundaError",
                    "java.util.Collections",
                    "java.time.Duration"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("message", 0),
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("throwable", 1)),
                List.of(" set retries with job.getRetries() - 1")),
            new ReplacementUtils.SimpleReplacementSpec(
                // ProcessEngineException(java.lang.String message, int code)
                new MethodMatcher(
                    "org.camunda.bpm.engine.ProcessEngineException <constructor>(java.lang.String, int)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.client.exception.CamundaError)}.jobError(#{any(String)})",
                    "io.camunda.client.exception.CamundaError"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("message", 0)),
                List.of(" error code was removed")),
            new ReplacementUtils.SimpleReplacementSpec(
                // ProcessEngineException(java.lang.Throwable throwable)
                new MethodMatcher(
                    "org.camunda.bpm.engine.ProcessEngineException <constructor>(java.lang.Throwable)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.client.exception.CamundaError)}.jobError(\"Add an error message here\", Collections.emptyMap(), 3, Duration.ofSeconds(30), #{any(java.lang.Throwable)})",
                    "io.camunda.client.exception.CamundaError",
                    "java.util.Collections",
                    "java.time.Duration"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("throwable", 0)),
                Collections.emptyList()));

    List<ReplacementUtils.SimpleReplacementSpec> incidentSpecs =
        List.of(
            new ReplacementUtils.SimpleReplacementSpec(
                // createIncident(java.lang.String incidentType, java.lang.String configuration)
                new MethodMatcher(
                    "org.camunda.bpm.engine.delegate.DelegateExecution createIncident(java.lang.String, java.lang.String)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.client.exception.CamundaError)}.jobError(\"Add an error message here\", Collections.emptyMap(), 0)",
                    "io.camunda.client.exception.CamundaError",
                    "java.util.Collections"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                Collections.emptyList(),
                List.of(
                    " incidentType was removed",
                    " configuration was removed",
                    " incident created by retries being 0")),
            new ReplacementUtils.SimpleReplacementSpec(
                // createIncident(java.lang.String incidentType, java.lang.String configuration,
                // java.lang.String message)
                new MethodMatcher(
                    "org.camunda.bpm.engine.delegate.DelegateExecution createIncident(java.lang.String, java.lang.String, java.lang.String)"),
                RecipeUtils.createSimpleJavaTemplate(
                    "throw #{any(io.camunda.client.exception.CamundaError)}.jobError(#{any(java.lang.String)}, Collections.emptyMap(), 0)",
                    "io.camunda.client.exception.CamundaError",
                    "java.util.Collections"),
                RecipeUtils.createSimpleIdentifier(
                    "CamundaError", "io.camunda.client.exception.CamundaError"),
                null,
                ReplacementUtils.ReturnTypeStrategy.VOID,
                List.of(
                    new ReplacementUtils.SimpleReplacementSpec.NamedArg("message", 0)),
                List.of(
                    " incidentType was removed",
                    " configuration was removed",
                    " incident created by retries being 0")));

    List<ReplacementUtils.ReplacementSpec> commonSpecs =
        Stream.concat(
                errorSpecs.stream().map(spec -> (ReplacementUtils.ReplacementSpec) spec),
                incidentSpecs.stream()
                    .map(spec -> (ReplacementUtils.ReplacementSpec) spec))
            .toList();

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

      // define preconditions
      TreeVisitor<?, ExecutionContext> check =
          Preconditions.and(
              new UsesType<>("io.camunda.client.annotation.JobWorker", true),
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

                for (ReplacementUtils.SimpleReplacementSpec spec : errorSpecs) {
                  if (spec.matcher().matches(newClass)) {

                    maybeAddImport("io.camunda.client.exception.CamundaError");

                    return maybeAutoFormat(
                        throwStmt,
                        spec.template()
                            .apply(
                                getCursor(),
                                throwStmt.getCoordinates().replace(),
                                ReplacementUtils.createArgs(
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
                  for (ReplacementUtils.ReplacementSpec spec : commonSpecs) {
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
                for (ReplacementUtils.ReplacementSpec spec : commonSpecs) {
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

              for (ReplacementUtils.SimpleReplacementSpec specs : incidentSpecs) {
                if (specs.matcher().matches(methodInvocation)) {

                  Statement statement =
                      specs
                          .template()
                          .apply(
                              statementCursor,
                              ((Statement) statementCursor.getValue()).getCoordinates().replace(),
                              ReplacementUtils.createArgs(
                                  methodInvocation,
                                  specs.baseIdentifier(),
                                  specs.argumentIndexes()));

                  maybeAddImport("io.camunda.client.exception.CamundaError");

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
