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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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

  private static final String MIGRATED_WORKER_METHOD = "executeJobMigrated";
  private static final String LOCAL_VARIABLE_LOOKUP_TODO =
      " TODO: getVariableLocal requires manual migration because Camunda 8 job workers do not expose the Camunda 7 execution scope.";
  private static final MethodMatcher GET_VARIABLE_LOCAL =
      new MethodMatcher(
          "org.camunda.bpm.engine.delegate.VariableScope getVariableLocal(java.lang.String)");
  private static final MethodMatcher GET_VARIABLE_LOCAL_TYPED =
      new MethodMatcher(
          "org.camunda.bpm.engine.delegate.VariableScope getVariableLocalTyped(..)");
  private static final MethodMatcher GET_VARIABLE =
      new MethodMatcher(
          "org.camunda.bpm.engine.delegate.VariableScope getVariable(java.lang.String)");
  private static final String MANUAL_MIGRATION_METHOD =
      "getVariableLocalRequiresManualMigration";
  private static final String VARIABLE_SCOPE =
      "org.camunda.bpm.engine.delegate.VariableScope";

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
        new MigrateNestedVariableLookupsInJobWorker(),
        new AddCastsToTypedVariableLookupsInJobWorker(),
        new FlagLocalVariableLookupsInJobWorker(),
        new MigrateDelegateBPMNErrorAndExceptionInJobWorker());
  }

  private static boolean isCopiedJobWorkerMethod(J.MethodDeclaration method) {
    return method != null && MIGRATED_WORKER_METHOD.equals(method.getSimpleName());
  }

  private static boolean isCopiedJobWorkerMethod(Cursor cursor) {
    for (Cursor current = cursor; current != null; current = current.getParent()) {
      if (current.getValue() instanceof J.MethodDeclaration method
          && isCopiedJobWorkerMethod(method)) {
        return true;
      }
    }
    return false;
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

  static J.ClassDeclaration ensureCompilableLocalVariableLookups(
      J.ClassDeclaration classDeclaration, Cursor parentCursor, ExecutionContext ctx) {
    return (J.ClassDeclaration)
        new JavaVisitor<ExecutionContext>() {
          private final Map<UUID, String> methodNamesByClass = new HashMap<>();
          private final Set<UUID> classesWithReplacedLookups = new HashSet<>();
          private final Set<UUID> classesWithTypedReplacedLookups = new HashSet<>();

          @Override
          public J.ClassDeclaration visitClassDeclaration(
              J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
            UUID classId = classDeclaration.getId();
            String methodName =
                methodNamesByClass.computeIfAbsent(
                    classId, ignored -> manualMigrationMethodName(classDeclaration));
            J.ClassDeclaration visited =
                (J.ClassDeclaration) super.visitClassDeclaration(classDeclaration, ctx);
            boolean hasReplacedLookups = classesWithReplacedLookups.remove(classId);
            boolean hasTypedReplacedLookups =
                classesWithTypedReplacedLookups.remove(classId);
            if (hasReplacedLookups && !hasMethodNamed(visited, methodName)) {
              String helperMethods =
                  """
                  private static <T> T %s(String variableName) {
                      throw new UnsupportedOperationException(
                          "Manual migration required for getVariableLocal: " + variableName);
                  }
                  """
                      .formatted(methodName);
              if (hasTypedReplacedLookups) {
                helperMethods +=
                    """

                    private static <T> T %s(String variableName, Object... ignored) {
                        throw new UnsupportedOperationException(
                            "Manual migration required for getVariableLocal: " + variableName);
                    }
                    """
                        .formatted(methodName);
              }
              J.ClassDeclaration withManualMigrationMethod =
                  RecipeUtils.createSimpleJavaTemplate(helperMethods)
                  .apply(
                      getCursor(),
                      visited.getBody().getCoordinates().lastStatement());
              return withManualMigrationMethod;
            }
            if (hasTypedReplacedLookups
                && hasMethodNamed(visited, methodName)
                && !hasTypedManualMigrationMethod(visited, methodName)) {
              J.ClassDeclaration withTypedManualMigrationMethod =
                  RecipeUtils.createSimpleJavaTemplate(
                          """

                          private static <T> T %s(String variableName, Object... ignored) {
                              throw new UnsupportedOperationException(
                                  "Manual migration required for getVariableLocal: " + variableName);
                          }
                          """
                              .formatted(methodName))
                      .apply(
                          getCursor(),
                          visited.getBody().getCoordinates().lastStatement());
              return withTypedManualMigrationMethod;
            }
            return visited;
          }

          @Override
          public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.MethodDeclaration method = getCursor().firstEnclosing(J.MethodDeclaration.class);
            if (method == null
                || method.getBody() == null
                || !method.getBody().getId().equals(block.getId())
                || !isCopiedJobWorkerMethod(method)) {
              return (J.Block) super.visitBlock(block, ctx);
            }

            List<Statement> originalStatements = block.getStatements();
            J.Block visited = (J.Block) super.visitBlock(block, ctx);
            List<Statement> updatedStatements = new ArrayList<>();
            List<Statement> visitedStatements = visited.getStatements();
            for (int i = 0; i < visitedStatements.size(); i++) {
              Statement statement = visitedStatements.get(i);
              if (i < originalStatements.size()
                  && containsLocalVariableLookup(originalStatements.get(i), ctx)) {
                statement = addManualMigrationFinding(statement);
              }
              updatedStatements.add(statement);
            }
            return visited.withStatements(updatedStatements);
          }

          @Override
          public J.MethodInvocation visitMethodInvocation(
              J.MethodInvocation invocation, ExecutionContext ctx) {
            if (!isMigratedMethod()) {
              return (J.MethodInvocation) super.visitMethodInvocation(invocation, ctx);
            }

            J.MethodInvocation visited =
                (J.MethodInvocation) super.visitMethodInvocation(invocation, ctx);
            if (!isLocalVariableLookup(visited)) {
              return visited;
            }

            J.ClassDeclaration classDeclaration =
                getCursor().firstEnclosing(J.ClassDeclaration.class);
            UUID classId = classDeclaration == null ? null : classDeclaration.getId();
            String methodName =
                classId == null
                    ? MANUAL_MIGRATION_METHOD
                    : methodNamesByClass.getOrDefault(
                        classId, manualMigrationMethodName(classDeclaration));
            if (classId != null) {
              classesWithReplacedLookups.add(classId);
              if (visited.getArguments().size() > 1) {
                classesWithTypedReplacedLookups.add(classId);
              }
            }
            if (visited.getArguments().isEmpty()) {
              return visited;
            }
            String arguments =
                String.join(
                    ", ", Collections.nCopies(visited.getArguments().size(), "#{any()}"));
            J.MethodInvocation replacement =
                RecipeUtils.createSimpleJavaTemplate(
                        methodName + "(" + arguments + ")")
                    .apply(
                        getCursor(),
                        visited.getCoordinates().replace(),
                        visited.getArguments().toArray());
            if (visited.getMethodType() == null) {
              return replacement;
            }
            return replacement.withMethodType(
                visited.getMethodType().withName(methodName));
          }

          @Override
          public J visitMemberReference(
              J.MemberReference reference, ExecutionContext ctx) {
            if (!isMigratedMethod()) {
              return super.visitMemberReference(reference, ctx);
            }

            J.MemberReference visited =
                (J.MemberReference) super.visitMemberReference(reference, ctx);
            if (!isLocalVariableLookup(visited)) {
              return visited;
            }

            J.ClassDeclaration classDeclaration =
                getCursor().firstEnclosing(J.ClassDeclaration.class);
            UUID classId = classDeclaration == null ? null : classDeclaration.getId();
            String methodName =
                classId == null
                    ? MANUAL_MIGRATION_METHOD
                    : methodNamesByClass.getOrDefault(
                        classId, manualMigrationMethodName(classDeclaration));
            if (classId != null) {
              classesWithReplacedLookups.add(classId);
            }
            JavaType.Method methodType = visited.getMethodType();
            int parameterCount =
                Math.max(
                    1,
                    methodType == null ? 1 : methodType.getParameterTypes().size());
            if (classId != null && parameterCount > 1) {
              classesWithTypedReplacedLookups.add(classId);
            }
            List<String> parameterNames = new ArrayList<>();
            for (int i = 0; i < parameterCount; i++) {
              parameterNames.add(i == 0 ? "variableName" : "argument" + i);
            }
            String lambdaParameters =
                parameterCount == 1
                    ? parameterNames.get(0)
                    : "(" + String.join(", ", parameterNames) + ")";

            return JavaTemplate.builder(
                    lambdaParameters
                        + " -> "
                        + methodName
                        + "("
                        + String.join(", ", parameterNames)
                        + ")")
                .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                .contextSensitive()
                .build()
                .apply(getCursor(), visited.getCoordinates().replace());
          }

          private Statement addManualMigrationFinding(Statement statement) {
            if (hasManualMigrationFinding(statement)) {
              return statement;
            }

            return statement.withComments(
                Stream.concat(
                        statement.getComments().stream(),
                        Stream.of(
                            RecipeUtils.createSimpleComment(
                                statement, LOCAL_VARIABLE_LOOKUP_TODO)))
                    .toList());
          }

          private boolean containsLocalVariableLookup(
              Statement statement, ExecutionContext ctx) {
            boolean[] found = {false};
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.MethodInvocation visitMethodInvocation(
                  J.MethodInvocation invocation, ExecutionContext nestedCtx) {
                if (isLocalVariableLookup(invocation)) {
                  found[0] = true;
                }
                return found[0] ? invocation : super.visitMethodInvocation(invocation, nestedCtx);
              }

              @Override
              public J.MemberReference visitMemberReference(
                J.MemberReference reference, ExecutionContext nestedCtx) {
              if (isLocalVariableLookup(reference)) {
                found[0] = true;
              }
              return found[0]
                  ? reference
                  : super.visitMemberReference(reference, nestedCtx);
              }
            }.visit(statement, ctx);
            return found[0];
          }

          private boolean isLocalVariableLookup(J.MethodInvocation invocation) {
            if (GET_VARIABLE_LOCAL.matches(invocation)
              || GET_VARIABLE_LOCAL_TYPED.matches(invocation)) {
              return true;
            }

            Expression receiver = invocation.getSelect();
            return ("getVariableLocal".equals(invocation.getSimpleName())
                  || "getVariableLocalTyped".equals(invocation.getSimpleName()))
              && receiver != null
              && RecipeUtils.isAssignableTo(receiver.getType(), VARIABLE_SCOPE);
          }

          private boolean isLocalVariableLookup(J.MemberReference reference) {
            return GET_VARIABLE_LOCAL.matches(reference)
              || GET_VARIABLE_LOCAL_TYPED.matches(reference);
          }

          private String manualMigrationMethodName(J.ClassDeclaration classDeclaration) {
            if (classDeclaration == null) {
              return MANUAL_MIGRATION_METHOD;
            }

            String generatedMethodName =
                classDeclaration.getBody().getStatements().stream()
                    .filter(J.MethodDeclaration.class::isInstance)
                    .map(J.MethodDeclaration.class::cast)
                    .filter(this::isGeneratedManualMigrationMethod)
                    .map(J.MethodDeclaration::getSimpleName)
                    .findFirst()
                    .orElse(null);
            if (generatedMethodName != null) {
              return generatedMethodName;
            }

            String methodName = MANUAL_MIGRATION_METHOD;
            int suffix = 0;
            while (hasMethodNamed(classDeclaration, methodName)) {
              suffix++;
              methodName = MANUAL_MIGRATION_METHOD + suffix;
            }
            return methodName;
          }

          private boolean isGeneratedManualMigrationMethod(J.MethodDeclaration method) {
            return method.getSimpleName().startsWith(MANUAL_MIGRATION_METHOD)
                && method.getBody() != null
                && method.getBody().getStatements().stream()
                    .anyMatch(
                        statement ->
                            statement
                                .toString()
                                .contains("Manual migration required for getVariableLocal:"));
          }

          private boolean hasMethodNamed(J.ClassDeclaration classDeclaration, String methodName) {
            return classDeclaration.getBody().getStatements().stream()
                .filter(J.MethodDeclaration.class::isInstance)
                .map(J.MethodDeclaration.class::cast)
                .anyMatch(method -> methodName.equals(method.getSimpleName()));
          }

          private boolean hasTypedManualMigrationMethod(
              J.ClassDeclaration classDeclaration, String methodName) {
            return classDeclaration.getBody().getStatements().stream()
                .filter(J.MethodDeclaration.class::isInstance)
                .map(J.MethodDeclaration.class::cast)
                .filter(method -> methodName.equals(method.getSimpleName()))
                .filter(this::isGeneratedManualMigrationMethod)
                .anyMatch(method -> method.getParameters().size() > 1);
          }

          private boolean hasManualMigrationFinding(Statement statement) {
            return statement.getComments().stream()
                .anyMatch(
                    comment ->
                        comment instanceof TextComment textComment
                            && textComment
                                .getText()
                                .contains(LOCAL_VARIABLE_LOOKUP_TODO.trim()));
          }

          private boolean isMigratedMethod() {
            return MigrateExecutionRecipe.isCopiedJobWorkerMethod(getCursor());
          }
        }
            .visit(classDeclaration, ctx, parentCursor);
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
      return cursor -> !MigrateExecutionRecipe.isCopiedJobWorkerMethod(cursor);
    }

    @Override
    protected List<ReplacementUtils.SimpleReplacementSpec> simpleMethodInvocations() {
      return List.of(
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

  private static class MigrateNestedVariableLookupsInJobWorker extends Recipe {

    @Override
    public String getDisplayName() {
      return "Migrate nested variable lookups in job worker recipe";
    }

    @Override
    public String getDescription() {
      return "Migrates variable lookups nested inside generic and other expressions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
      TreeVisitor<?, ExecutionContext> precondition =
          Preconditions.and(
              new UsesType<>("io.camunda.client.annotation.JobWorker", true),
              Preconditions.or(
                  new UsesType<>("org.camunda.bpm.engine.delegate.JavaDelegate", true),
                  new UsesType<>("org.camunda.bpm.engine.delegate.ExecutionListener", true)));

      return Preconditions.check(
          precondition,
          new JavaIsoVisitor<>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(
                J.MethodInvocation invocation, ExecutionContext ctx) {
              if (!isCopiedJobWorkerMethod()) {
                return super.visitMethodInvocation(invocation, ctx);
              }

              J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
              if (!isVariableLookup(visited)) {
                return visited;
              }

              return RecipeUtils.createSimpleJavaTemplate(
                      "#{job:any(io.camunda.client.api.response.ActivatedJob)}.getVariablesAsMap().get(#{any(java.lang.String)})")
                  .apply(
                      getCursor(),
                      visited.getCoordinates().replace(),
                      RecipeUtils.createSimpleIdentifier(
                          "job", "io.camunda.client.api.response.ActivatedJob"),
                      visited.getArguments().get(0));
            }

            @Override
            public J.MemberReference visitMemberReference(
                J.MemberReference memberReference, ExecutionContext ctx) {
              if (!isCopiedJobWorkerMethod()) {
                return super.visitMemberReference(memberReference, ctx);
              }

              J.MemberReference visited = super.visitMemberReference(memberReference, ctx);
              if (!isVariableLookup(visited)) {
                return visited;
              }

              return JavaTemplate.builder(
                      "#{job:any(io.camunda.client.api.response.ActivatedJob)}.getVariablesAsMap()::get")
                  .javaParser(
                      JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                  .contextSensitive()
                  .build()
                  .apply(
                      getCursor(),
                      visited.getCoordinates().replace(),
                      RecipeUtils.createSimpleIdentifier(
                          "job", "io.camunda.client.api.response.ActivatedJob"));
            }

            private boolean isVariableLookup(J.MethodInvocation invocation) {
              if (invocation.getArguments().size() != 1
                  || invocation.getArguments().get(0) instanceof J.Empty) {
                return false;
              }

              if (GET_VARIABLE.matches(invocation)) {
                return true;
              }

              Expression receiver = invocation.getSelect();
              return "getVariable".equals(invocation.getSimpleName())
                  && receiver != null
                  && isVariableScopeReceiver(receiver);
            }

            private boolean isVariableLookup(J.MemberReference memberReference) {
              if (!"getVariable".equals(memberReference.getReference().getSimpleName())
                  || !isVariableScopeReceiver(memberReference.getContaining())) {
                return false;
              }

              JavaType.Method methodType = memberReference.getMethodType();
              return methodType == null || methodType.getParameterTypes().size() == 1;
            }

            private boolean isVariableScopeReceiver(Expression receiver) {
              if (RecipeUtils.isAssignableTo(
                  receiver.getType(), "org.camunda.bpm.engine.delegate.VariableScope")) {
                return true;
              }

              if (!(receiver instanceof J.Identifier identifier)) {
                return false;
              }

              J.MethodDeclaration method =
                  getCursor().firstEnclosing(J.MethodDeclaration.class);
              if (method == null) {
                return false;
              }

              return method.getParameters().stream()
                  .filter(J.VariableDeclarations.class::isInstance)
                  .map(J.VariableDeclarations.class::cast)
                  .anyMatch(
                      parameter ->
                          parameter.getVariables().stream()
                              .anyMatch(
                                  variable ->
                                      identifier.getSimpleName()
                                          .equals(variable.getName().getSimpleName())
                                          && (RecipeUtils.isAssignableTo(
                                                  parameter.getType(), VARIABLE_SCOPE)
                                              || isVariableScopeType(parameter))));
            }

            private boolean isVariableScopeType(J.VariableDeclarations parameter) {
              return parameter.getTypeExpression() != null
                  && parameter.getTypeExpression().toString().endsWith("DelegateExecution");
            }

            private boolean isCopiedJobWorkerMethod() {
              return MigrateExecutionRecipe.isCopiedJobWorkerMethod(getCursor());
            }
          });
    }
  }

  private static class FlagLocalVariableLookupsInJobWorker extends Recipe {

    @Override
    public String getDisplayName() {
      return "Flags local variable lookups for manual migration";
    }

    @Override
    public String getDescription() {
      return "Adds a TODO when a copied delegate uses getVariableLocal because job workers do not expose the Camunda 7 execution scope.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
      TreeVisitor<?, ExecutionContext> precondition =
          Preconditions.and(
              new UsesType<>("io.camunda.client.annotation.JobWorker", true),
              Preconditions.or(
                  new UsesType<>("org.camunda.bpm.engine.delegate.JavaDelegate", true),
                  new UsesType<>("org.camunda.bpm.engine.delegate.ExecutionListener", true)));

      return Preconditions.check(
          precondition,
          new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(
                J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
              return ensureCompilableLocalVariableLookups(
                  classDeclaration, getCursor().getParentOrThrow(), ctx);
            }
          });
    }
  }

  private static class AddCastsToTypedVariableLookupsInJobWorker extends Recipe {

    @Override
    public String getDisplayName() {
      return "Preserves typed variable lookup assignments in job workers";
    }

    @Override
    public String getDescription() {
      return "Adds casts when map-based variable lookups are used in typed contexts.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
      TreeVisitor<?, ExecutionContext> preconditions =
          Preconditions.and(
              new UsesType<>("io.camunda.client.annotation.JobWorker", true),
              Preconditions.or(
                  new UsesType<>("org.camunda.bpm.engine.delegate.JavaDelegate", true),
                  new UsesType<>("org.camunda.bpm.engine.delegate.ExecutionListener", true)));

      return Preconditions.check(
          preconditions,
          new JavaIsoVisitor<>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(
                J.VariableDeclarations declarations, ExecutionContext ctx) {
              if (!isCopiedJobWorkerMethod()) {
                return super.visitVariableDeclarations(declarations, ctx);
              }

              J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, ctx);
              String declaredType = declaredType(visited);
              if (declaredType == null) {
                return visited;
              }

              List<J.VariableDeclarations.NamedVariable> updatedVariables =
                  visited.getVariables().stream()
                      .map(
                          variable ->
                              variable.withInitializer(
                                  addCastIfNeeded(variable, declaredType, getCursor())))
                      .toList();

              return maybeAutoFormat(
                  declarations, visited.withVariables(updatedVariables), ctx);
            }

            @Override
            public J.Return visitReturn(J.Return returnStatement, ExecutionContext ctx) {
              J.Return visited = super.visitReturn(returnStatement, ctx);
              if (!isCopiedJobWorkerMethod()) {
                return visited;
              }

              if (isInsideLambdaBody()) {
                return visited;
              }

              String expectedType = methodReturnType();
              if (expectedType == null) {
                return visited;
              }

              Expression expression = visited.getExpression();
              Expression castExpression = addCastIfNeeded(expression, expectedType, getCursor());
              if (castExpression == expression) {
                return visited;
              }

              return visited.withExpression(castExpression);
            }

            @Override
            public J.Assignment visitAssignment(
                J.Assignment assignment, ExecutionContext ctx) {
              J.Assignment visited = super.visitAssignment(assignment, ctx);
              if (!isCopiedJobWorkerMethod()) {
                return visited;
              }

              String expectedType = typeName(visited.getVariable().getType());
              if (expectedType == null) {
                return visited;
              }

              Expression castAssignment =
                  addCastIfNeeded(visited.getAssignment(), expectedType, getCursor());
              if (castAssignment == visited.getAssignment()) {
                return visited;
              }

              return maybeAutoFormat(
                  assignment, visited.withAssignment(castAssignment), ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(
                J.MethodInvocation invocation, ExecutionContext ctx) {
              J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
              if (!isCopiedJobWorkerMethod()) {
                return visited;
              }

              if (visited.getArguments().isEmpty()) {
                return visited;
              }

              List<Expression> arguments = visited.getArguments();
              List<Expression> updatedArguments = new ArrayList<>(arguments);
              boolean changed = false;

              for (int i = 0; i < arguments.size(); i++) {
                Expression castArgument =
                    addCastIfNeeded(
                        arguments.get(i), expectedArgumentType(visited, i), getCursor());
                if (castArgument != arguments.get(i)) {
                  updatedArguments.set(i, castArgument);
                  changed = true;
                }
              }

              return changed
                  ? maybeAutoFormat(invocation, visited.withArguments(updatedArguments), ctx)
                  : visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
              J.NewClass visited = super.visitNewClass(newClass, ctx);
              if (!isCopiedJobWorkerMethod() || visited.getArguments().isEmpty()) {
                return visited;
              }

              List<Expression> arguments = visited.getArguments();
              List<Expression> updatedArguments = new ArrayList<>(arguments);
              boolean changed = false;

              for (int i = 0; i < arguments.size(); i++) {
                Expression castArgument =
                    addCastIfNeeded(
                        arguments.get(i),
                        expectedConstructorArgumentType(visited, i),
                        getCursor());
                if (castArgument != arguments.get(i)) {
                  updatedArguments.set(i, castArgument);
                  changed = true;
                }
              }

              return changed
                  ? maybeAutoFormat(newClass, visited.withArguments(updatedArguments), ctx)
                  : visited;
            }

            private Expression addCastIfNeeded(
                J.VariableDeclarations.NamedVariable variable,
                String declaredType,
                Cursor scope) {
              Expression initializer = variable.getInitializer();
              Expression castInitializer = addCastIfNeeded(initializer, declaredType, scope);
              if (castInitializer == initializer) {
                return initializer;
              }

              return castInitializer;
            }

            private Expression addCastIfNeeded(
                Expression expression, String expectedType, Cursor scope) {
              if (expression == null) {
                return expression;
              }

              if (expression instanceof J.NewClass newClass) {
                return adaptConstructorArguments(newClass, new Cursor(scope, newClass));
              }

              if (expression instanceof J.Ternary ternary && expectedType != null) {
                Cursor ternaryScope = new Cursor(scope, ternary);
                Expression truePart =
                    addCastIfNeeded(ternary.getTruePart(), expectedType, ternaryScope);
                Expression falsePart =
                    addCastIfNeeded(ternary.getFalsePart(), expectedType, ternaryScope);
                if (truePart == ternary.getTruePart() && falsePart == ternary.getFalsePart()) {
                  return expression;
                }
                return ternary.withTruePart(truePart).withFalsePart(falsePart);
              }

              if (expression instanceof J.Lambda lambda && expectedType != null) {
                return adaptLambda(lambda, expectedType, scope);
              }

              if (expression instanceof J.NewArray newArray && expectedType != null) {
                return adaptArrayInitializer(newArray, expectedType, scope);
              }

              if (isEffectiveVariableLookup(expression)) {
                if (expectedType == null
                    || isObjectType(expectedType)
                    || isAlreadyCast(expression)) {
                  return expression;
                }

                return createTypeCast(expression, expectedType);
              }

              if (expression instanceof J.MethodInvocation methodInvocation
                  && expectedType != null) {
                return adaptGenericInvocation(
                    methodInvocation, expectedType, new Cursor(scope, methodInvocation));
              }

              return expression;
            }

            private Expression adaptLambda(
                J.Lambda lambda, String expectedType, Cursor scope) {
              String resultType = functionalResultType(expectedType);
              if (resultType == null) {
                return lambda;
              }

              J body = lambda.getBody();
              if (body instanceof Expression expressionBody) {
                Expression castBody =
                    addCastIfNeeded(expressionBody, resultType, new Cursor(scope, lambda));
                return castBody == expressionBody ? lambda : lambda.withBody(castBody);
              }

              if (body instanceof J.Block block) {
                J.Block castBlock =
                    (J.Block)
                        new JavaIsoVisitor<ExecutionContext>() {
                          @Override
                          public J.Lambda visitLambda(J.Lambda lambda, ExecutionContext ctx) {
                            return lambda;
                          }

                          @Override
                          public J.ClassDeclaration visitClassDeclaration(
                              J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
                            return classDeclaration;
                          }

                          @Override
                          public J.Return visitReturn(
                              J.Return returnStatement, ExecutionContext ctx) {
                            J.Return visited = super.visitReturn(returnStatement, ctx);
                            Expression expression = visited.getExpression();
                            Expression castExpression =
                                addCastIfNeeded(expression, resultType, getCursor());
                            return castExpression == expression
                                ? visited
                                : visited.withExpression(castExpression);
                          }
                        }.visit(block, new InMemoryExecutionContext());
                return castBlock == block ? lambda : lambda.withBody(castBlock);
              }

              return lambda;
            }

            private Expression adaptArrayInitializer(
                J.NewArray newArray, String expectedType, Cursor scope) {
              List<Expression> initializer = newArray.getInitializer();
              String elementType = arrayElementType(expectedType);
              if (initializer == null || elementType == null) {
                return newArray;
              }

              List<Expression> updatedElements = new ArrayList<>(initializer);
              boolean changed = false;
              for (int i = 0; i < initializer.size(); i++) {
                Expression element =
                    addCastIfNeeded(
                        initializer.get(i), elementType, new Cursor(scope, newArray));
                if (element != initializer.get(i)) {
                  updatedElements.set(i, element);
                  changed = true;
                }
              }

              return changed
                  ? newArray.withInitializer(updatedElements)
                  : newArray;
            }

            private String functionalResultType(String type) {
              List<String> typeArguments = genericTypeArguments(type);
              if (typeArguments.isEmpty()) {
                return null;
              }

              String rawType = type.substring(0, type.indexOf('<')).trim();
              String simpleName = rawType.substring(rawType.lastIndexOf('.') + 1);
              return switch (simpleName) {
                case "Function", "BiFunction" -> typeArguments.get(typeArguments.size() - 1);
                case "Supplier", "Callable", "UnaryOperator", "BinaryOperator" ->
                    typeArguments.get(0);
                default -> null;
              };
            }

            private String arrayElementType(String type) {
              return type.endsWith("[]") ? type.substring(0, type.length() - 2).trim() : null;
            }

            private Expression createTypeCast(Expression expression, String expectedType) {
              String source =
                  "class CastTarget { Object value = (" + expectedType + ") null; }";
              SourceFile parsedSource =
                  JavaParser.fromJavaVersion()
                      .classpath(JavaParser.runtimeClasspath())
                      .build()
                      .parse(source)
                      .findFirst()
                      .orElseThrow();
              if (!(parsedSource instanceof J.CompilationUnit compilationUnit)) {
                throw new IllegalStateException(
                    "Could not parse a cast for migrated variable lookup type: "
                        + expectedType
                        + " ("
                        + parsedSource
                        + ")");
              }
              J.TypeCast[] parsedTypeCast = new J.TypeCast[1];
              new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.TypeCast visitTypeCast(
                    J.TypeCast typeCast, ExecutionContext nestedCtx) {
                  parsedTypeCast[0] = typeCast;
                  return typeCast;
                }
              }.visit(compilationUnit, new InMemoryExecutionContext());

              if (parsedTypeCast[0] == null) {
                throw new IllegalStateException(
                    "Could not create a cast for migrated variable lookup type: " + expectedType);
              }

              Expression castExpression =
                  (Expression) expression.withPrefix(parsedTypeCast[0].getExpression().getPrefix());
              return parsedTypeCast[0]
                  .withId(Tree.randomId())
                  .withPrefix(expression.getPrefix())
                  .withExpression(castExpression);
            }

            private Expression adaptConstructorArguments(J.NewClass newClass, Cursor scope) {
              List<Expression> arguments = newClass.getArguments();
              List<Expression> updatedArguments = new ArrayList<>(arguments);
              boolean changed = false;

              for (int i = 0; i < arguments.size(); i++) {
                Expression castArgument =
                    addCastIfNeeded(
                        arguments.get(i),
                        expectedConstructorArgumentType(newClass, i),
                        scope);
                if (castArgument != arguments.get(i)) {
                  updatedArguments.set(i, castArgument);
                  changed = true;
                }
              }

              return changed ? newClass.withArguments(updatedArguments) : newClass;
            }

            private Expression adaptGenericInvocation(
                J.MethodInvocation invocation, String expectedType, Cursor scope) {
              List<String> typeArguments = genericTypeArguments(expectedType);
              if (isMapFactory(invocation, expectedType, typeArguments)) {
                return adaptMapFactory(invocation, typeArguments, scope);
              }
              if (!isElementFactory(invocation, expectedType, typeArguments)
                  || typeArguments.isEmpty()) {
                return invocation;
              }

              List<Expression> arguments = invocation.getArguments();
              List<Expression> updatedArguments = new ArrayList<>(arguments);
              boolean changed = false;
              for (int i = 0; i < arguments.size(); i++) {
                String elementType = typeArguments.get(Math.min(i, typeArguments.size() - 1));
                Expression castArgument =
                    addCastIfNeeded(arguments.get(i), elementType, scope);
                if (castArgument != arguments.get(i)) {
                  updatedArguments.set(i, castArgument);
                  changed = true;
                }
              }
              return changed ? invocation.withArguments(updatedArguments) : invocation;
            }

            private Expression adaptMapFactory(
                J.MethodInvocation invocation, List<String> typeArguments, Cursor scope) {
              List<Expression> arguments = invocation.getArguments();
              List<Expression> updatedArguments = new ArrayList<>(arguments);
              boolean changed = false;

              for (int i = 0; i < arguments.size(); i++) {
                Expression argument = arguments.get(i);
                Expression adaptedArgument;
                if ("ofEntries".equals(invocation.getSimpleName())
                    && argument instanceof J.MethodInvocation entry
                    && "entry".equals(entry.getSimpleName())) {
                  adaptedArgument =
                      adaptMapEntry(entry, typeArguments, new Cursor(scope, entry));
                } else if ("of".equals(invocation.getSimpleName())) {
                  adaptedArgument =
                      addCastIfNeeded(
                          argument,
                          typeArguments.get(i % typeArguments.size()),
                          scope);
                } else {
                  adaptedArgument = argument;
                }

                if (adaptedArgument != argument) {
                  updatedArguments.set(i, adaptedArgument);
                  changed = true;
                }
              }

              return changed ? invocation.withArguments(updatedArguments) : invocation;
            }

            private Expression adaptMapEntry(
                J.MethodInvocation entry, List<String> typeArguments, Cursor scope) {
              List<Expression> arguments = entry.getArguments();
              List<Expression> updatedArguments = new ArrayList<>(arguments);
              boolean changed = false;
              for (int i = 0; i < arguments.size(); i++) {
                Expression adaptedArgument =
                    addCastIfNeeded(
                        arguments.get(i),
                        typeArguments.get(i % typeArguments.size()),
                        scope);
                if (adaptedArgument != arguments.get(i)) {
                  updatedArguments.set(i, adaptedArgument);
                  changed = true;
                }
              }
              return changed ? entry.withArguments(updatedArguments) : entry;
            }

            private boolean isMapFactory(
                J.MethodInvocation invocation,
                String expectedType,
                List<String> typeArguments) {
              if (typeArguments.size() < 2) {
                return false;
              }

              int genericStart = expectedType.indexOf('<');
              if (genericStart < 0) {
                return false;
              }

              String rawType = expectedType.substring(0, genericStart).trim();
              return rawType.endsWith("Map")
                  && ("of".equals(invocation.getSimpleName())
                      || "ofEntries".equals(invocation.getSimpleName()));
            }

            private boolean isElementFactory(
                J.MethodInvocation invocation,
                String expectedType,
                List<String> typeArguments) {
              if (typeArguments.isEmpty()) {
                return false;
              }

              String rawType = expectedType.substring(0, expectedType.indexOf('<')).trim();
              return switch (invocation.getSimpleName()) {
                case "of", "ofNullable", "singleton", "singletonList", "singletonSet" ->
                    rawType.endsWith("List")
                        || rawType.endsWith("Set")
                        || rawType.endsWith("Collection")
                        || rawType.endsWith("Iterable")
                        || rawType.endsWith("Optional")
                        || rawType.endsWith("Stream");
                default -> false;
              };
            }

            private List<String> genericTypeArguments(String type) {
              int start = type.indexOf('<');
              int end = type.lastIndexOf('>');
              if (start < 0 || end <= start) {
                return Collections.emptyList();
              }

              String arguments = type.substring(start + 1, end);
              List<String> result = new ArrayList<>();
              int depth = 0;
              int argumentStart = 0;
              for (int i = 0; i < arguments.length(); i++) {
                char current = arguments.charAt(i);
                if (current == '<') {
                  depth++;
                } else if (current == '>') {
                  depth--;
                } else if (current == ',' && depth == 0) {
                  result.add(arguments.substring(argumentStart, i).trim());
                  argumentStart = i + 1;
                }
              }
              result.add(arguments.substring(argumentStart).trim());
              return result;
            }

            private String expectedConstructorArgumentType(J.NewClass newClass, int index) {
              JavaType.Method constructorType = newClass.getConstructorType();
              if (constructorType != null
                  && index < constructorType.getParameterTypes().size()) {
                String type = typeName(constructorType.getParameterTypes().get(index));
                if (type != null) {
                  return type;
                }
              }

              J.ClassDeclaration enclosingClass =
                  getCursor().firstEnclosing(J.ClassDeclaration.class);
              if (enclosingClass == null) {
                return null;
              }

              String className = newClass.getClazz().toString();
              int genericStart = className.indexOf('<');
              if (genericStart >= 0) {
                className = className.substring(0, genericStart);
              }
              className = className.substring(className.lastIndexOf('.') + 1);
              return findConstructorArgumentType(enclosingClass, className, index);
            }

            private String findConstructorArgumentType(
                J.ClassDeclaration classDeclaration, String className, int index) {
              if (className.equals(classDeclaration.getSimpleName())) {
                String constructorArgumentType =
                    classDeclaration.getBody().getStatements().stream()
                        .filter(J.MethodDeclaration.class::isInstance)
                        .map(J.MethodDeclaration.class::cast)
                        .filter(method -> className.equals(method.getSimpleName()))
                        .filter(method -> method.getParameters().size() > index)
                        .map(method -> method.getParameters().get(index))
                        .filter(J.VariableDeclarations.class::isInstance)
                        .map(J.VariableDeclarations.class::cast)
                        .map(J.VariableDeclarations::getTypeExpression)
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .findFirst()
                        .orElse(null);
                if (constructorArgumentType != null) {
                  return constructorArgumentType;
                }
              }

              for (Statement statement : classDeclaration.getBody().getStatements()) {
                if (statement instanceof J.ClassDeclaration nestedClass) {
                  String constructorArgumentType =
                      findConstructorArgumentType(nestedClass, className, index);
                  if (constructorArgumentType != null) {
                    return constructorArgumentType;
                  }
                }
              }
              return null;
            }

            private boolean isAlreadyCast(Expression expression) {
              return expression instanceof J.TypeCast;
            }

            private String methodReturnType() {
              J.MethodDeclaration method =
                  getCursor().firstEnclosing(J.MethodDeclaration.class);
              if (method == null || method.getReturnTypeExpression() == null) {
                return null;
              }

              String returnType = method.getReturnTypeExpression().toString();
              return "void".equals(returnType) ? null : returnType;
            }

            private boolean isInsideLambdaBody() {
              for (Cursor cursor = getCursor(); cursor != null; cursor = cursor.getParent()) {
                if (cursor.getValue() instanceof J.Lambda) {
                  return true;
                }
                if (cursor.getValue() instanceof J.MethodDeclaration) {
                  return false;
                }
              }
              return false;
            }

            private String typeName(JavaType type) {
              if (type == null
                  || type instanceof JavaType.Unknown
                  || type instanceof JavaType.Variable
                  || TypeUtils.isOfClassType(type, "java.lang.Object")
                  || type.toString().startsWith("Generic{")) {
                return null;
              }
              return RecipeUtils.getShortName(type.toString());
            }

            private String expectedArgumentType(J.MethodInvocation invocation, int index) {
              JavaType.Method methodType = invocation.getMethodType();
              if (methodType != null && index < methodType.getParameterTypes().size()) {
                String type = typeName(methodType.getParameterTypes().get(index));
                if (type != null) {
                  return type;
                }
              }

              if (invocation.getSelect() != null) {
                return null;
              }

              J.ClassDeclaration classDeclaration =
                  getCursor().firstEnclosing(J.ClassDeclaration.class);
              if (classDeclaration == null) {
                return null;
              }

              return classDeclaration.getBody().getStatements().stream()
                  .filter(J.MethodDeclaration.class::isInstance)
                  .map(J.MethodDeclaration.class::cast)
                  .filter(method -> method.getSimpleName().equals(invocation.getSimpleName()))
                  .filter(method -> method.getParameters().size() > index)
                  .map(method -> method.getParameters().get(index))
                  .filter(J.VariableDeclarations.class::isInstance)
                  .map(J.VariableDeclarations.class::cast)
                  .map(J.VariableDeclarations::getTypeExpression)
                  .filter(Objects::nonNull)
                  .map(Object::toString)
                  .findFirst()
                  .orElse(null);
            }

            private boolean isObjectType(String type) {
              return "Object".equals(type) || "java.lang.Object".equals(type);
            }

            private String declaredType(J.VariableDeclarations declarations) {
              J typeExpression = declarations.getTypeExpression();
              if (typeExpression == null || "var".equals(typeExpression.toString())) {
                return null;
              }

              JavaType type = declarations.getType();
              if (TypeUtils.isOfClassType(type, "java.lang.Object")) {
                return null;
              }

              return typeExpression.toString();
            }

            private boolean isEffectiveVariableLookup(Expression expression) {
              if (!(expression instanceof J.MethodInvocation invocation)
                  || !"get".equals(invocation.getSimpleName())
                  || !(invocation.getSelect() instanceof J.MethodInvocation variablesAsMap)
                  || !"getVariablesAsMap".equals(variablesAsMap.getSimpleName())) {
                return false;
              }

              return variablesAsMap.getSelect() instanceof J.Identifier identifier
                  && "job".equals(identifier.getSimpleName());
            }

            private boolean isCopiedJobWorkerMethod() {
              return MigrateExecutionRecipe.isCopiedJobWorkerMethod(getCursor());
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
