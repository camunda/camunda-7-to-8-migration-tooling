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
      "IdentityService method has a direct Camunda 8 Java client equivalent";
  static final String IDENTITY_NO_DIRECT_MARKER =
      "IdentityService method has no direct Java client equivalent";
  static final String IDENTITY_MANUAL_MARKER =
      "IdentityService method requires manual migration";
  static final String MANAGEMENT_MARKER =
      "ManagementService has no direct Java client equivalent";

  private static final MethodMatcher SET_JOB_RETRIES_MATCHER =
      new MethodMatcher(
          MANAGEMENT_SERVICE_FQN + " setJobRetries(java.lang.String, int)");
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
          "suspendJobByProcessInstanceId",
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
            if (alreadyAnnotated(statement.getComments())) {
              return statement;
            }
            List<ServiceCall> serviceCalls = findServiceCalls(statement);
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
                ServiceCall serviceCall = serviceCall(invocation);
                if (serviceCall != null) {
                  current.add(serviceCall);
                }
                return (J.MethodInvocation) super.visitMethodInvocation(invocation, current);
              }

              @Override
              public J.Block visitBlock(J.Block nestedBlock, List<ServiceCall> current) {
                return nestedBlock;
              }

            }.visit(statement, found);
            return found;
          }

          private ServiceCall serviceCall(J.MethodInvocation invocation) {
            if (SET_JOB_RETRIES_MATCHER.matches(invocation)) {
              return new ServiceCall(MANAGEMENT_SERVICE_FQN, invocation.getSimpleName(), true);
            }
            if (new MethodMatcher(IDENTITY_SERVICE_FQN + " *(..)").matches(invocation)) {
              return new ServiceCall(IDENTITY_SERVICE_FQN, invocation.getSimpleName(), false);
            }
            if (new MethodMatcher(MANAGEMENT_SERVICE_FQN + " *(..)").matches(invocation)) {
              return new ServiceCall(MANAGEMENT_SERVICE_FQN, invocation.getSimpleName(), false);
            }
            return null;
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
                                || textComment.getText().contains(MANAGEMENT_MARKER)));
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
                      " For bulk migration of authorizations and tenants, use the Identity Data Migrator."),
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
            if (isJobRetryMethod(serviceCall.methodName())) {
              if ("setJobRetries".equals(serviceCall.methodName())
                  && serviceCall.singleJobRetry()) {
                return "Map the Camunda 7 job id to a Camunda 8 job key, then use CamundaClient.newUpdateJobCommand(jobKey).updateRetries(n).send().join().";
              }
              return "Preserve the bulk or query semantics, resolve each affected Camunda 7 job id to a Camunda 8 job key, then use CamundaClient.newUpdateJobCommand(jobKey).updateRetries(n).send().join() for each job.";
            }
            return MANAGEMENT_METHOD_HINTS.getOrDefault(
                serviceCall.methodName(),
                "Use CamundaClient or the Orchestration Cluster REST API.");
          }

          private String methodMarker(ServiceCall serviceCall) {
            if (!IDENTITY_SERVICE_FQN.equals(serviceCall.serviceFqn())) {
              return MANAGEMENT_MARKER;
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

          private boolean isJobRetryMethod(String methodName) {
            return "setJobRetries".equals(methodName) || "setJobRetriesAsync".equals(methodName);
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
              String serviceFqn, String methodName, boolean singleJobRetry) {}
        });
  }
}
