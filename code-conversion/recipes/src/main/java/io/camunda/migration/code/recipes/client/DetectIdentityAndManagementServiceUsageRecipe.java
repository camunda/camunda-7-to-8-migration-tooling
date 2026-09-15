/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client;

import io.camunda.migration.code.recipes.utils.RecipeUtils;
import io.camunda.migration.code.recipes.utils.ReplacementUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
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
  private static final String IDENTITY_MIGRATOR_URL =
      "https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/data-migrator/identity/";
  static final String IDENTITY_MARKER = "IdentityService has no direct Java client equivalent";
  static final String MANAGEMENT_MARKER =
      "ManagementService has no direct Java client equivalent";

  private static final MethodMatcher SET_JOB_RETRIES_MATCHER =
      new MethodMatcher(
          MANAGEMENT_SERVICE_FQN + " setJobRetries(java.lang.String, int)");
  private static final JavaTemplate SET_JOB_RETRIES_TEMPLATE =
      RecipeUtils.createSimpleJavaTemplate(
          "#{camundaClient:any(io.camunda.client.CamundaClient)}"
              + ".newUpdateJobCommand(Long.parseLong(#{jobId:any(java.lang.String)}))"
              + ".retries(#{retries:any(int)}).send().join()");
  private static final List<String> IDENTITY_METHODS =
      List.of(
          "createUserQuery",
          "saveUser",
          "createGroupQuery",
          "saveGroup",
          "createMembership",
          "deleteMembership",
          "setAuthenticatedUserId",
          "clearAuthentication",
          "createAuthorizationQuery");
  private static final Map<String, String> MANAGEMENT_METHOD_HINTS =
      Map.of(
          "createJobQuery", "Use POST /v2/jobs/search or CamundaClient job search requests.",
          "executeJob",
              "In tests, use processTestContext.increaseTime(Duration); otherwise use the job API.",
          "createIncidentQuery", "Use POST /v2/incidents/search.",
          "getRegisteredDeployments", "Use the Orchestration Cluster REST API to search deployments.",
          "suspendJobByProcessInstanceId",
              "Use the Orchestration Cluster REST API to suspend the related process instance.");

  @Override
  public @NonNull String getDisplayName() {
    return "Detect IdentityService and ManagementService usage";
  }

  @Override
  public @NonNull String getDescription() {
    return "Adds migration TODO comments for Camunda 7 IdentityService and ManagementService "
        + "usage and migrates ManagementService.setJobRetries to the Camunda 8 client.";
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
          public J.MethodInvocation visitMethodInvocation(
              J.MethodInvocation invocation, ExecutionContext ctx) {
            J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
            if (!SET_JOB_RETRIES_MATCHER.matches(visited)) {
              return visited;
            }
            return (J.MethodInvocation)
                SET_JOB_RETRIES_TEMPLATE.apply(
                    getCursor(),
                    visited.getCoordinates().replace(),
                    ReplacementUtils.createArgs(
                        visited,
                        RecipeUtils.createSimpleIdentifier(
                            "camundaClient", "io.camunda.client.CamundaClient"),
                        List.of(
                            new ReplacementUtils.SimpleReplacementSpec.NamedArg("jobId", 0),
                            new ReplacementUtils.SimpleReplacementSpec.NamedArg("retries", 1))));
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
            ServiceCall serviceCall = findServiceCall(statement);
            if (serviceCall == null || serviceCall.isJobRetries()) {
              return statement;
            }
            return addCommentToStatement(statement, serviceCall);
          }

          private ServiceCall findServiceCall(Statement statement) {
            AtomicReference<ServiceCall> found = new AtomicReference<>();
            new JavaIsoVisitor<AtomicReference<ServiceCall>>() {
              @Override
              public J.MethodInvocation visitMethodInvocation(
                  J.MethodInvocation invocation, AtomicReference<ServiceCall> current) {
                if (current.get() == null) {
                  ServiceCall serviceCall = serviceCall(invocation);
                  if (serviceCall != null) {
                    current.set(serviceCall);
                    return invocation;
                  }
                }
                return super.visitMethodInvocation(invocation, current);
              }

              @Override
              public J.Block visitBlock(J.Block nestedBlock, AtomicReference<ServiceCall> current) {
                return nestedBlock;
              }
            }.visit(statement, found);
            return found.get();
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

          private Statement addCommentToStatement(Statement statement, ServiceCall serviceCall) {
            String marker =
                serviceCall.serviceFqn().equals(IDENTITY_SERVICE_FQN)
                    ? IDENTITY_MARKER
                    : MANAGEMENT_MARKER;
            return (Statement)
                statement.withComments(
                    Stream.concat(
                            statement.getComments().stream(),
                            methodComments(statement, serviceCall, marker).stream())
                        .toList());
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
                      " For bulk migration of users/groups/authorizations, use the Identity Data Migrator."),
                  RecipeUtils.createSimpleComment(
                      declaration,
                      " For runtime identity management, use the Camunda Admin REST API or your identity provider's API."),
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
                        + (serviceCall.serviceFqn().equals(IDENTITY_SERVICE_FQN)
                            ? IDENTITY_MIGRATOR_URL
                            : ADMIN_API_URL)));
          }

          private String methodHint(ServiceCall serviceCall) {
            if (serviceCall.serviceFqn().equals(IDENTITY_SERVICE_FQN)) {
              if ("setAuthenticatedUserId".equals(serviceCall.methodName())
                  || "clearAuthentication".equals(serviceCall.methodName())) {
                return "Authentication is handled at the transport layer with JWT/OAuth.";
              }
              if (IDENTITY_METHODS.contains(serviceCall.methodName())) {
                return "Use the Camunda Admin REST API or your identity provider's API.";
              }
              return "Use the Camunda Admin REST API or your identity provider's API.";
            }
            return MANAGEMENT_METHOD_HINTS.getOrDefault(
                serviceCall.methodName(),
                "Use CamundaClient or the Orchestration Cluster REST API.");
          }

          private record ServiceCall(String serviceFqn, String methodName) {
            private boolean isJobRetries() {
              return MANAGEMENT_SERVICE_FQN.equals(serviceFqn)
                  && "setJobRetries".equals(methodName);
            }
          }
        });
  }
}
