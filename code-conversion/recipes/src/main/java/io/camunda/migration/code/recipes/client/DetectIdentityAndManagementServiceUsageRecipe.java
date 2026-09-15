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
  private static final String ADMIN_API_URL =
      "https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/orchestration-cluster-api-rest-overview/";
  private static final String CAMUNDA_JAVA_CLIENT_URL =
      "https://docs.camunda.io/docs/apis-tools/java-client/";
  private static final String IDENTITY_MIGRATOR_URL =
      "https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/data-migrator/identity/";
  private static final String IDENTITY_PROVIDER_URL =
      "https://docs.camunda.io/docs/components/concepts/access-control/connect-to-identity-provider/";
  static final String IDENTITY_MARKER =
      "IdentityService requires method-specific migration guidance";
  static final String MANAGEMENT_MARKER =
      "ManagementService has no direct Java client equivalent";

  private static final MethodMatcher SET_JOB_RETRIES_MATCHER =
      new MethodMatcher(
          MANAGEMENT_SERVICE_FQN + " setJobRetries(java.lang.String, int)");
  private static final Map<String, String> IDENTITY_METHOD_HINTS =
      Map.of(
          "createUserQuery", "Use CamundaClient.newUsersSearchRequest().",
          "saveUser",
              "Use CamundaClient.newCreateUserCommand() or newUpdateUserCommand(userId).",
          "createGroupQuery", "Use CamundaClient.newGroupsSearchRequest().",
          "saveGroup",
              "Use CamundaClient.newCreateGroupCommand() or newUpdateGroupCommand(groupId).",
          "createMembership", "Use CamundaClient.newAssignUserToGroupCommand().",
          "deleteMembership", "Use CamundaClient.newUnassignUserFromGroupCommand().",
          "createAuthorizationQuery", "Use CamundaClient.newAuthorizationSearchRequest().");
  private static final Map<String, String> MANAGEMENT_METHOD_HINTS =
      Map.of(
          "createJobQuery", "Use POST /v2/jobs/search or CamundaClient job search requests.",
          "executeJob",
              "In tests, use processTestContext.increaseTime(Duration); otherwise use the job API.",
          "createIncidentQuery", "Use POST /v2/incidents/search.",
          "getRegisteredDeployments",
              "Camunda 8 uses job-type-based workers instead of deployment-aware registration. There is no direct equivalent; use deployment search only as an optional inventory.",
          "suspendJobByProcessInstanceId",
              "Job suspension is unsupported in Camunda 8. If pausing the entire process instance is acceptable, use the process-instance suspension API.",
          "setJobRetries",
              "Map the Camunda 7 job id to a Camunda 8 job key, then use CamundaClient.newUpdateJobCommand(jobKey).updateRetries(n).send().join().");

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
              return new ServiceCall(MANAGEMENT_SERVICE_FQN, invocation.getSimpleName());
            }
            if (new MethodMatcher(IDENTITY_SERVICE_FQN + " *(..)").matches(invocation)) {
              return new ServiceCall(IDENTITY_SERVICE_FQN, invocation.getSimpleName());
            }
            if (new MethodMatcher(MANAGEMENT_SERVICE_FQN + " *(..)").matches(invocation)) {
              return new ServiceCall(MANAGEMENT_SERVICE_FQN, invocation.getSimpleName());
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
              String marker =
                  serviceCall.serviceFqn().equals(IDENTITY_SERVICE_FQN)
                      ? IDENTITY_MARKER
                      : MANAGEMENT_MARKER;
              comments.addAll(methodComments(statement, serviceCall, marker));
            }
            return (Statement) statement.withComments(comments);
          }

          private boolean alreadyAnnotated(List<Comment> comments) {
            return comments.stream()
                .anyMatch(
                    comment ->
                        comment instanceof TextComment textComment
                            && (textComment.getText().contains(IDENTITY_MARKER)
                                || textComment.getText().contains(MANAGEMENT_MARKER)));
          }

          private List<Comment> declarationComments(
              String serviceFqn, J.VariableDeclarations declaration) {
            if (IDENTITY_SERVICE_FQN.equals(serviceFqn)) {
              return List.of(
                  RecipeUtils.createSimpleComment(
                      declaration, " TODO: " + IDENTITY_MARKER + " in Camunda 8."),
                  RecipeUtils.createSimpleComment(
                      declaration,
                      " Use method-specific Camunda Java Client, Orchestration Cluster REST API, or identity provider guidance."),
                  RecipeUtils.createSimpleComment(
                      declaration,
                      " For bulk migration of users/groups/authorizations, use the Identity Data Migrator."),
                  RecipeUtils.createSimpleComment(declaration, " See: " + CAMUNDA_JAVA_CLIENT_URL),
                  RecipeUtils.createSimpleComment(declaration, " See: " + ADMIN_API_URL),
                  RecipeUtils.createSimpleComment(
                      declaration, " See: " + IDENTITY_MIGRATOR_URL));
            }
            return List.of(
                RecipeUtils.createSimpleComment(
                    declaration, " TODO: " + MANAGEMENT_MARKER + " in Camunda 8."),
                RecipeUtils.createSimpleComment(
                    declaration, " Use CamundaClient or the Orchestration Cluster REST API."),
                RecipeUtils.createSimpleComment(declaration, " See: " + ADMIN_API_URL));
          }

          private List<Comment> methodComments(
              Statement statement, ServiceCall serviceCall, String marker) {
            String hint = methodHint(serviceCall);
            return List.of(
                RecipeUtils.createSimpleComment(
                    statement,
                    " TODO: "
                        + marker
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
              if ("setAuthenticatedUserId".equals(serviceCall.methodName())
                  || "clearAuthentication".equals(serviceCall.methodName())) {
                return "Authentication is handled at the transport layer with JWT/OAuth; configure the identity provider instead.";
              }
              return IDENTITY_METHOD_HINTS.getOrDefault(
                  serviceCall.methodName(),
                  "Use the Orchestration Cluster REST API or your identity provider's API.");
            }
            return MANAGEMENT_METHOD_HINTS.getOrDefault(
                serviceCall.methodName(),
                "Use CamundaClient or the Orchestration Cluster REST API.");
          }

          private String methodDocsUrl(ServiceCall serviceCall) {
            if (!IDENTITY_SERVICE_FQN.equals(serviceCall.serviceFqn())) {
              return ADMIN_API_URL;
            }
            return "setAuthenticatedUserId".equals(serviceCall.methodName())
                    || "clearAuthentication".equals(serviceCall.methodName())
                ? IDENTITY_PROVIDER_URL
                : IDENTITY_METHOD_HINTS.containsKey(serviceCall.methodName())
                    ? CAMUNDA_JAVA_CLIENT_URL
                    : ADMIN_API_URL;
          }

          private record ServiceCall(String serviceFqn, String methodName) {}
        });
  }
}
