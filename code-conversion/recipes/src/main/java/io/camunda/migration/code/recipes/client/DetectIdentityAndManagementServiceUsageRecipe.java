/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client;

import io.camunda.migration.code.recipes.utils.RecipeUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;

public class DetectIdentityAndManagementServiceUsageRecipe extends Recipe {

  private static final String IDENTITY_SERVICE_FQN = "org.camunda.bpm.engine.IdentityService";
  private static final String MANAGEMENT_SERVICE_FQN =
      "org.camunda.bpm.engine.ManagementService";
  private static final String ORCHESTRATION_API_URL =
      "https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/";
  private static final String CAMUNDA_JAVA_CLIENT_URL =
      "https://docs.camunda.io/docs/apis-tools/java-client/";
  private static final String IDENTITY_MIGRATOR_URL =
      "https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/data-migrator/identity/";
  private static final String IDENTITY_PROVIDER_URL =
      "https://docs.camunda.io/docs/components/concepts/access-control/connect-to-identity-provider/";
  static final String IDENTITY_DECLARATION_MARKER =
      "IdentityService usage requires method-specific migration guidance";
  static final String IDENTITY_CLIENT_MARKER =
      "IdentityService method has a direct Java client equivalent";
  static final String IDENTITY_NO_DIRECT_MARKER =
      "IdentityService method has no direct Java client equivalent";
  static final String IDENTITY_MANUAL_MARKER =
      "IdentityService method requires manual migration";
  static final String MANAGEMENT_CLIENT_MARKER =
      "ManagementService method has a direct Java client equivalent";
  static final String MANAGEMENT_MARKER =
      "ManagementService has no direct Java client equivalent";
  private static final Set<String> MANAGEMENT_CLIENT_METHODS =
      Set.of(
          "createJobQuery",
          "createIncidentQuery",
          "setJobRetries",
          "setJobRetriesAsync",
          "setJobRetriesByJobsAsync",
          "setJobRetriesByProcessAsync");

  private static final MethodMatcher SET_JOB_RETRIES_MATCHER =
      new MethodMatcher(
          MANAGEMENT_SERVICE_FQN + " setJobRetries(java.lang.String, int)");
  private static final MethodMatcher CREATE_JOB_QUERY_MATCHER =
      new MethodMatcher(MANAGEMENT_SERVICE_FQN + " createJobQuery()");
  private static final MethodMatcher JOB_QUERY_TIMERS_MATCHER =
      new MethodMatcher("org.camunda.bpm.engine.runtime.JobQuery timers()");
  private static final Map<String, String> IDENTITY_METHOD_HINTS =
      Map.ofEntries(
          Map.entry("newUser", "Use CamundaClient.newCreateUserCommand()."),
          Map.entry(
              "saveUser",
              "Use CamundaClient.newCreateUserCommand() for new users or newUpdateUserCommand(userId) for existing users."),
          Map.entry("createUserQuery", "Use CamundaClient.newUsersSearchRequest()."),
          Map.entry("deleteUser", "Use CamundaClient.newDeleteUserCommand(userId)."),
          Map.entry("newGroup", "Use CamundaClient.newCreateGroupCommand()."),
          Map.entry("createGroupQuery", "Use CamundaClient.newGroupsSearchRequest()."),
          Map.entry(
              "saveGroup",
              "Use CamundaClient.newCreateGroupCommand() for new groups or newUpdateGroupCommand(groupId) for existing groups."),
          Map.entry("deleteGroup", "Use CamundaClient.newDeleteGroupCommand(groupId)."),
          Map.entry(
              "createMembership",
              "Use CamundaClient.newAssignUserToGroupCommand().username(userId).groupId(groupId)."),
          Map.entry(
              "deleteMembership",
              "Use CamundaClient.newUnassignUserFromGroupCommand().username(userId).groupId(groupId)."),
          Map.entry("newTenant", "Use CamundaClient.newCreateTenantCommand()."),
          Map.entry("createTenantQuery", "Use CamundaClient.newTenantsSearchRequest()."),
          Map.entry(
              "saveTenant",
              "Use CamundaClient.newCreateTenantCommand() for new tenants or newUpdateTenantCommand(tenantId) for existing tenants."),
          Map.entry("deleteTenant", "Use CamundaClient.newDeleteTenantCommand(tenantId)."),
          Map.entry(
              "createTenantUserMembership",
              "Use CamundaClient.newAssignUserToTenantCommand().username(userId).tenantId(tenantId)."),
          Map.entry(
              "createTenantGroupMembership",
              "Use CamundaClient.newAssignGroupToTenantCommand().groupId(groupId).tenantId(tenantId)."),
          Map.entry(
              "deleteTenantUserMembership",
              "Use CamundaClient.newUnassignUserFromTenantCommand().username(userId).tenantId(tenantId)."),
          Map.entry(
              "deleteTenantGroupMembership",
              "Use CamundaClient.newUnassignGroupFromTenantCommand().groupId(groupId).tenantId(tenantId)."));
  private static final Set<String> IDENTITY_AUTHENTICATION_METHODS =
      Set.of(
          "checkPassword",
          "checkPasswordAgainstPolicy",
          "getPasswordPolicy",
          "setAuthenticatedUserId",
          "setAuthentication",
          "unlockUser",
          "getCurrentAuthentication",
          "clearAuthentication");
  private static final Map<String, String> MANAGEMENT_METHOD_HINTS =
      Map.of(
          "createJobQuery", "Use POST /v2/jobs/search or CamundaClient job search requests.",
          "executeJob",
              "Camunda 8 has no operation to execute an arbitrary job by ID. In timer tests, use processTestContext.increaseTime(Duration); production work must run in a job worker that activates jobs by type.",
          "createIncidentQuery", "Use POST /v2/incidents/search.",
          "getRegisteredDeployments",
              "Camunda 8 uses job-type-based workers instead of deployment-aware registration. There is no direct equivalent; use deployment search only as an optional inventory.",
          "updateJobSuspensionState",
              "Job suspension is unsupported in Camunda 8. If pausing the entire process instance is acceptable, use the process-instance suspension API.");

  @Override
  public @NonNull String getDisplayName() {
    return "Detect IdentityService and ManagementService usage";
  }

  @Override
  public @NonNull String getDescription() {
    return "Adds migration TODO comments for Camunda 7 IdentityService and ManagementService "
        + "usage, including guidance for migrating ManagementService.setJobRetries.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return Preconditions.check(
        Preconditions.or(
            new UsesType<>(IDENTITY_SERVICE_FQN, true),
            new UsesType<>(MANAGEMENT_SERVICE_FQN, true)),
        new JavaIsoVisitor<>() {

          @Override
          public J.VariableDeclarations visitVariableDeclarations(
              J.VariableDeclarations declarations, ExecutionContext ctx) {
            J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, ctx);
            JavaType.FullyQualified type = visited.getTypeAsFullyQualified();
            if (!isSupportedService(type)
                || isMethodParameter()
                || alreadyAnnotated(visited.getComments())) {
              return visited;
            }
            return visited.withComments(
                Stream.concat(
                        visited.getComments().stream(),
                        declarationComments(type.getFullyQualifiedName(), visited).stream())
                    .toList());
          }

          @Override
          public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block visited = super.visitBlock(block, ctx);
            List<Statement> newStatements = new ArrayList<>();
            boolean changed = false;
            for (Statement statement : visited.getStatements()) {
              Statement annotated = maybeAnnotateStatement(statement);
              if (annotated != statement) {
                changed = true;
              }
              newStatements.add(annotated);
            }
            return changed ? visited.withStatements(newStatements) : visited;
          }

          private Statement maybeAnnotateStatement(Statement statement) {
            List<ServiceCall> serviceCalls = findServiceCalls(statement);
            serviceCalls =
                serviceCalls.stream()
                    .distinct()
                    .filter(call -> !alreadyAnnotated(statement.getComments(), call))
                    .toList();
            if (serviceCalls.isEmpty()) {
              return statement;
            }
            return addCommentsToStatement(statement, serviceCalls);
          }

          private List<ServiceCall> findServiceCalls(Statement statement) {
            List<ServiceCall> found = new ArrayList<>();
            new JavaIsoVisitor<List<ServiceCall>>() {
              @Override
              public J.MethodInvocation visitMethodInvocation(
                  J.MethodInvocation invocation, List<ServiceCall> current) {
                if (isTimerQueryInvocation(invocation)) {
                  current.add(
                      new ServiceCall(
                          MANAGEMENT_SERVICE_FQN, "timers", true));
                }
                ServiceCall serviceCall = serviceCall(invocation);
                if (serviceCall != null
                    && !(CREATE_JOB_QUERY_MATCHER.matches(invocation)
                        && isWithinTimerQuery())) {
                  current.add(serviceCall);
                }
                return (J.MethodInvocation) super.visitMethodInvocation(invocation, current);
              }

              @Override
              public J.MemberReference visitMemberReference(
                  J.MemberReference reference, List<ServiceCall> current) {
                ServiceCall serviceCall = serviceCall(reference);
                if (serviceCall != null) {
                  current.add(serviceCall);
                }
                return super.visitMemberReference(reference, current);
              }

              @Override
              public J.Block visitBlock(J.Block nestedBlock, List<ServiceCall> current) {
                return nestedBlock;
              }

              private boolean isWithinTimerQuery() {
                org.openrewrite.Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                  if (cursor.getValue() instanceof J.MethodInvocation parent
                      && isTimerQueryInvocation(parent)) {
                    return true;
                  }
                  cursor = cursor.getParent();
                }
                return false;
              }

            }.visit(statement, found);
            return found;
          }

          private boolean isTimerQueryInvocation(J.MethodInvocation invocation) {
            if (!"timers".equals(invocation.getSimpleName())) {
              return false;
            }
            J select = invocation.getSelect();
            while (select instanceof J.MethodInvocation query) {
              if (CREATE_JOB_QUERY_MATCHER.matches(query)) {
                return true;
              }
              select = query.getSelect();
            }
            return false;
          }

          private ServiceCall serviceCall(J.MethodInvocation invocation) {
            if (SET_JOB_RETRIES_MATCHER.matches(invocation)) {
              return new ServiceCall(
                  MANAGEMENT_SERVICE_FQN,
                  invocation.getSimpleName(),
                  RetrySelection.SINGLE_JOB_ID);
            }
            if (JOB_QUERY_TIMERS_MATCHER.matches(invocation)
                && !isTimerQueryInvocation(invocation)) {
              return new ServiceCall(MANAGEMENT_SERVICE_FQN, "timers", true);
            }
            if (new MethodMatcher(IDENTITY_SERVICE_FQN + " *(..)").matches(invocation)) {
              return new ServiceCall(IDENTITY_SERVICE_FQN, invocation.getSimpleName());
            }
            if (new MethodMatcher(MANAGEMENT_SERVICE_FQN + " *(..)").matches(invocation)) {
              return new ServiceCall(
                  MANAGEMENT_SERVICE_FQN,
                  invocation.getSimpleName(),
                  retrySelection(invocation));
            }
            return null;
          }

          private ServiceCall serviceCall(J.MemberReference reference) {
            JavaType.Method methodType = reference.getMethodType();
            if (methodType == null || methodType.getDeclaringType() == null) {
              return null;
            }
            if (JOB_QUERY_TIMERS_MATCHER.matches(reference)) {
              return new ServiceCall(MANAGEMENT_SERVICE_FQN, "timers", true);
            }
            String serviceFqn = methodType.getDeclaringType().getFullyQualifiedName();
            if (!IDENTITY_SERVICE_FQN.equals(serviceFqn)
                && !MANAGEMENT_SERVICE_FQN.equals(serviceFqn)) {
              return null;
            }
            return new ServiceCall(
                serviceFqn, methodType.getName(), retrySelection(methodType));
          }

          private RetrySelection retrySelection(J.MethodInvocation invocation) {
            if ("setJobRetries".equals(invocation.getSimpleName())) {
              if (SET_JOB_RETRIES_MATCHER.matches(invocation)) {
                return RetrySelection.SINGLE_JOB_ID;
              }
              return invocation.getArguments().size() == 1
                  ? RetrySelection.SYNC_RETRY_BUILDER
                  : RetrySelection.SYNC_BULK_OR_QUERY;
            }
            if ("setJobRetriesByJobsAsync".equals(invocation.getSimpleName())) {
              return RetrySelection.ASYNC_BUILDER_JOBS;
            }
            if ("setJobRetriesByProcessAsync".equals(invocation.getSimpleName())) {
              return RetrySelection.ASYNC_BUILDER_PROCESS;
            }
            if (!"setJobRetriesAsync".equals(invocation.getSimpleName())) {
              return RetrySelection.NOT_APPLICABLE;
            }
            return retrySelection(invocation.getMethodType());
          }

          private RetrySelection retrySelection(JavaType.Method methodType) {
            if (methodType == null) {
              return RetrySelection.ASYNC_OTHER;
            }
            if ("setJobRetries".equals(methodType.getName())) {
              if (methodType.getParameterTypes().size() == 2
                  && isStringType(methodType.getParameterTypes().get(0))
                  && isIntType(methodType.getParameterTypes().get(1))) {
                return RetrySelection.SINGLE_JOB_ID;
              }
              return methodType.getParameterTypes().size() == 1
                  ? RetrySelection.SYNC_RETRY_BUILDER
                  : RetrySelection.SYNC_BULK_OR_QUERY;
            }
            if ("setJobRetriesByJobsAsync".equals(methodType.getName())) {
              return RetrySelection.ASYNC_BUILDER_JOBS;
            }
            if ("setJobRetriesByProcessAsync".equals(methodType.getName())) {
              return RetrySelection.ASYNC_BUILDER_PROCESS;
            }
            if (!"setJobRetriesAsync".equals(methodType.getName())) {
              return RetrySelection.NOT_APPLICABLE;
            }
            boolean hasJobIds = hasParameterType(methodType, "java.util.List");
            return switch (queryKind(methodType)) {
              case JOB ->
                  hasJobIds
                      ? RetrySelection.ASYNC_JOB_IDS_AND_QUERY
                      : RetrySelection.ASYNC_JOB_QUERY_ONLY;
              case PROCESS_INSTANCE, HISTORIC_PROCESS_INSTANCE ->
                  hasJobIds
                      ? RetrySelection.ASYNC_PROCESS_IDS_AND_QUERY
                      : RetrySelection.ASYNC_PROCESS_QUERY_ONLY;
              case PROCESS_INSTANCE_AND_HISTORIC ->
                  RetrySelection.ASYNC_PROCESS_IDS_AND_HISTORIC_QUERY;
              case NONE ->
                  hasJobIds
                      ? RetrySelection.ASYNC_IDS_ONLY
                      : RetrySelection.ASYNC_OTHER;
            };
          }

          private AsyncQueryKind queryKind(JavaType.Method methodType) {
            boolean hasProcessInstanceQuery =
                hasParameterType(
                    methodType, "org.camunda.bpm.engine.runtime.ProcessInstanceQuery");
            boolean hasHistoricProcessInstanceQuery =
                hasParameterType(
                    methodType,
                    "org.camunda.bpm.engine.history.HistoricProcessInstanceQuery");
            if (hasProcessInstanceQuery && hasHistoricProcessInstanceQuery) {
              return AsyncQueryKind.PROCESS_INSTANCE_AND_HISTORIC;
            }
            if (hasParameterType(methodType, "org.camunda.bpm.engine.runtime.JobQuery")) {
              return AsyncQueryKind.JOB;
            }
            if (hasProcessInstanceQuery) {
              return AsyncQueryKind.PROCESS_INSTANCE;
            }
            if (hasHistoricProcessInstanceQuery) {
              return AsyncQueryKind.HISTORIC_PROCESS_INSTANCE;
            }
            return AsyncQueryKind.NONE;
          }

          private boolean hasParameterType(JavaType.Method methodType, String fullyQualifiedName) {
            return methodType.getParameterTypes().stream()
                .anyMatch(
                    type ->
                        type instanceof JavaType.FullyQualified fullyQualified
                            && fullyQualifiedName.equals(fullyQualified.getFullyQualifiedName()));
          }

          private boolean isStringType(JavaType type) {
            return type == JavaType.Primitive.String
                || type instanceof JavaType.FullyQualified fullyQualified
                    && "java.lang.String".equals(fullyQualified.getFullyQualifiedName());
          }

          private boolean isIntType(JavaType type) {
            return type == JavaType.Primitive.Int;
          }

          private boolean isSupportedService(JavaType.FullyQualified type) {
            return type != null
                && (IDENTITY_SERVICE_FQN.equals(type.getFullyQualifiedName())
                    || MANAGEMENT_SERVICE_FQN.equals(type.getFullyQualifiedName()));
          }

          private boolean isMethodParameter() {
            org.openrewrite.Cursor cursor = getCursor().getParent();
            while (cursor != null) {
              Object value = cursor.getValue();
              if (value instanceof J.MethodDeclaration) {
                return true;
              }
              if (value instanceof J.Block) {
                return false;
              }
              cursor = cursor.getParent();
            }
            return false;
          }

          private Statement addCommentsToStatement(
              Statement statement, List<ServiceCall> serviceCalls) {
            List<Comment> comments = new ArrayList<>(statement.getComments());
            for (ServiceCall serviceCall : serviceCalls) {
              comments.addAll(methodComments(statement, serviceCall));
            }
            return (Statement) statement.withComments(comments);
          }

          private boolean alreadyAnnotated(List<Comment> comments) {
            return comments.stream()
                .anyMatch(
                    comment ->
                        comment instanceof TextComment textComment
                            && (textComment.getText().contains(IDENTITY_DECLARATION_MARKER)
                                || textComment.getText().contains(IDENTITY_CLIENT_MARKER)
                                || textComment.getText().contains(IDENTITY_NO_DIRECT_MARKER)
                                || textComment.getText().contains(IDENTITY_MANUAL_MARKER)
                                || textComment.getText().contains(MANAGEMENT_CLIENT_MARKER)
                                || textComment.getText().contains(MANAGEMENT_MARKER)));
          }

          private boolean alreadyAnnotated(List<Comment> comments, ServiceCall serviceCall) {
            String marker = methodMarker(serviceCall);
            String methodCall = "(" + serviceCall.methodName() + "())";
            String hint = methodHint(serviceCall);
            String commentText =
                comments.stream()
                    .filter(TextComment.class::isInstance)
                    .map(TextComment.class::cast)
                    .map(TextComment::getText)
                    .collect(java.util.stream.Collectors.joining("\n"));
            return commentText.contains(marker)
                && commentText.contains(methodCall)
                && commentText.contains(hint);
          }

          private List<Comment> declarationComments(
              String serviceFqn, J.VariableDeclarations declaration) {
            if (IDENTITY_SERVICE_FQN.equals(serviceFqn)) {
              return List.of(
                  RecipeUtils.createSimpleComment(
                      declaration,
                      " TODO: " + IDENTITY_DECLARATION_MARKER + " in Camunda 8."),
                  RecipeUtils.createSimpleComment(
                      declaration,
                      " Use CamundaClient identity APIs (for example, newUsersSearchRequest(), newCreateUserCommand(), newGroupsSearchRequest(), newAssignUserToGroupCommand(), and newAuthorizationSearchRequest()) or the Orchestration Cluster REST API where available."),
                  RecipeUtils.createSimpleComment(
                      declaration,
                      " For bulk migration of users, groups, authorizations, and tenants, use the Identity Data Migrator."),
                  RecipeUtils.createSimpleComment(
                      declaration,
                      " For authentication, use transport-level JWT/OAuth and configure the identity provider."),
                  RecipeUtils.createSimpleComment(
                      declaration, " See: " + CAMUNDA_JAVA_CLIENT_URL),
                  RecipeUtils.createSimpleComment(
                      declaration, " See: " + ORCHESTRATION_API_URL),
                  RecipeUtils.createSimpleComment(
                      declaration, " See: " + IDENTITY_PROVIDER_URL),
                  RecipeUtils.createSimpleComment(
                      declaration, " See: " + IDENTITY_MIGRATOR_URL));
            }
            return List.of(
                RecipeUtils.createSimpleComment(
                    declaration, " TODO: " + MANAGEMENT_MARKER + " in Camunda 8."),
                RecipeUtils.createSimpleComment(
                    declaration, " Use CamundaClient or the Orchestration Cluster REST API."),
                RecipeUtils.createSimpleComment(
                    declaration, " See: " + ORCHESTRATION_API_URL));
          }

          private List<Comment> methodComments(Statement statement, ServiceCall serviceCall) {
            String hint = methodHint(serviceCall);
            return List.of(
                RecipeUtils.createSimpleComment(
                    statement,
                    " TODO: "
                        + methodMarker(serviceCall)
                        + " in Camunda 8 ("
                        + serviceCall.methodName()
                        + "())."),
                RecipeUtils.createSimpleComment(statement, " " + hint),
                RecipeUtils.createSimpleComment(
                    statement,
                    " See: "
                        + methodDocsUrl(serviceCall)));
          }

          private String methodHint(ServiceCall serviceCall) {
            if (serviceCall.serviceFqn().equals(IDENTITY_SERVICE_FQN)) {
              String identityHint = IDENTITY_METHOD_HINTS.get(serviceCall.methodName());
              if (identityHint != null) {
                return identityHint;
              }
              if (isIdentityAuthenticationMethod(serviceCall)) {
                return "Authentication and password operations are handled by the identity provider; use its API instead.";
              }
              return "Review the Camunda 8 identity APIs or identity provider for this operation.";
            }
            if (serviceCall.timerQuery()) {
              return "Camunda 8 timers are wait states, not searchable jobs. In timer tests, use processTestContext.increaseTime(Duration).";
            }
            return switch (serviceCall.retrySelection()) {
              case SINGLE_JOB_ID ->
                  "Map the Camunda 7 job id to a Camunda 8 job key, then use CamundaClient.newUpdateJobCommand(jobKey).updateRetries(n).send().join().";
              case SYNC_RETRY_BUILDER ->
                  "Preserve the retry builder's job or job-definition selector and due-date semantics, resolve the selected Camunda 8 job keys, and update retries with CamundaClient.newUpdateJobCommand(jobKey).updateRetries(n).send().join().";
              case SYNC_BULK_OR_QUERY ->
                  "For synchronous bulk or query updates, preserve the selection, resolve each affected Camunda 7 job id to a Camunda 8 job key, and update each job with CamundaClient.newUpdateJobCommand(jobKey).updateRetries(n).send().join(); account for partial success.";
              case ASYNC_IDS_ONLY ->
                  "Use CamundaClient.newCreateBatchOperationCommand().updateJob().retries(n).filter(jobFilter).send().join() after mapping the Camunda 7 IDs to Camunda 8 job keys.";
              case ASYNC_JOB_QUERY_ONLY ->
                  "Use CamundaClient.newCreateBatchOperationCommand().updateJob().retries(n).filter(jobFilter).send().join() after translating the Camunda 7 query to a Camunda 8 JobFilter.";
              case ASYNC_JOB_IDS_AND_QUERY ->
                  "Resolve the Camunda 7 IDs and query separately, union and deduplicate their mapped Camunda 8 job keys, then use CamundaClient.newCreateBatchOperationCommand().updateJob().retries(n).filter(jobFilter).send().join(); a single conjunctive JobFilter cannot represent the C7 union.";
              case ASYNC_PROCESS_QUERY_ONLY ->
                  "Resolve the Camunda 7 process-instance query to matching process instances and their job keys, then use the Camunda 8 batch job update API.";
              case ASYNC_PROCESS_IDS_AND_QUERY ->
                  "Resolve the Camunda 7 process-instance IDs and query separately, union and deduplicate their matching Camunda 8 job keys, then use the Camunda 8 batch job update API.";
              case ASYNC_PROCESS_IDS_AND_HISTORIC_QUERY ->
                  "Resolve the Camunda 7 process-instance IDs, process-instance query, and historic process-instance query separately, union and deduplicate their matching Camunda 8 job keys, then use the Camunda 8 batch job update API.";
              case ASYNC_BUILDER_JOBS ->
                  "Preserve the job retry builder's job/job-definition selector and union semantics, then use the Camunda 8 batch job update API.";
              case ASYNC_BUILDER_PROCESS ->
                  "Preserve the process retry builder's process selector and union semantics, then use the Camunda 8 batch job update API.";
              case ASYNC_OTHER ->
                  "Preserve the Camunda 7 selection semantics and use the Camunda 8 batch job update API.";
              case NOT_APPLICABLE -> MANAGEMENT_METHOD_HINTS.getOrDefault(
                  serviceCall.methodName(),
                  "Use CamundaClient or the Orchestration Cluster REST API.");
            };
          }

          private String methodMarker(ServiceCall serviceCall) {
            if (MANAGEMENT_SERVICE_FQN.equals(serviceCall.serviceFqn())) {
              return !serviceCall.timerQuery()
                      && MANAGEMENT_CLIENT_METHODS.contains(serviceCall.methodName())
                  ? MANAGEMENT_CLIENT_MARKER
                  : MANAGEMENT_MARKER;
            }
            if (IDENTITY_METHOD_HINTS.containsKey(serviceCall.methodName())) {
              return IDENTITY_CLIENT_MARKER;
            }
            if (isIdentityAuthenticationMethod(serviceCall)) {
              return IDENTITY_NO_DIRECT_MARKER;
            }
            return IDENTITY_MANUAL_MARKER;
          }

          private boolean isIdentityAuthenticationMethod(ServiceCall serviceCall) {
            return IDENTITY_SERVICE_FQN.equals(serviceCall.serviceFqn())
                && IDENTITY_AUTHENTICATION_METHODS.contains(serviceCall.methodName());
          }

          private String methodDocsUrl(ServiceCall serviceCall) {
            if (!IDENTITY_SERVICE_FQN.equals(serviceCall.serviceFqn())) {
              return ORCHESTRATION_API_URL;
            }
            return isIdentityAuthenticationMethod(serviceCall)
                ? IDENTITY_PROVIDER_URL
                : IDENTITY_METHOD_HINTS.containsKey(serviceCall.methodName())
                    ? CAMUNDA_JAVA_CLIENT_URL
                    : ORCHESTRATION_API_URL;
          }

          private record ServiceCall(
              String serviceFqn,
              String methodName,
              RetrySelection retrySelection,
              boolean timerQuery) {
            private ServiceCall(String serviceFqn, String methodName) {
              this(serviceFqn, methodName, RetrySelection.NOT_APPLICABLE, false);
            }

            private ServiceCall(
                String serviceFqn, String methodName, RetrySelection retrySelection) {
              this(serviceFqn, methodName, retrySelection, false);
            }

            private ServiceCall(
                String serviceFqn, String methodName, boolean timerQuery) {
              this(serviceFqn, methodName, RetrySelection.NOT_APPLICABLE, timerQuery);
            }
          }

          private enum RetrySelection {
            NOT_APPLICABLE,
            SINGLE_JOB_ID,
            SYNC_RETRY_BUILDER,
            SYNC_BULK_OR_QUERY,
            ASYNC_IDS_ONLY,
            ASYNC_JOB_QUERY_ONLY,
            ASYNC_JOB_IDS_AND_QUERY,
            ASYNC_PROCESS_QUERY_ONLY,
            ASYNC_PROCESS_IDS_AND_QUERY,
            ASYNC_PROCESS_IDS_AND_HISTORIC_QUERY,
            ASYNC_BUILDER_JOBS,
            ASYNC_BUILDER_PROCESS,
            ASYNC_OTHER
          }

          private enum AsyncQueryKind {
            NONE,
            JOB,
            PROCESS_INSTANCE,
            HISTORIC_PROCESS_INSTANCE,
            PROCESS_INSTANCE_AND_HISTORIC
          }
        });
  }
}
