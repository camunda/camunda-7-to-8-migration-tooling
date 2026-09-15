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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.java.tree.J.VariableDeclarations;

/** Migrates Camunda 7 RepositoryService deployments and flags unsupported queries. */
public class MigrateRepositoryServiceRecipe extends org.openrewrite.Recipe {

  private static final String PROCESS_ENGINE = "org.camunda.bpm.engine.ProcessEngine";
  private static final String REPOSITORY_SERVICE = "org.camunda.bpm.engine.RepositoryService";
  private static final String CAMUNDA_CLIENT = "io.camunda.client.CamundaClient";
  private static final String DEPLOYMENT_TODO =
      " TODO: RepositoryService deployment method was not migrated automatically";
  private static final String QUERY_TODO =
      " TODO: RepositoryService query was not migrated automatically. "
          + "Migrate it manually with the corresponding Camunda 8 Java client search request "
          + "or REST endpoint.";
  private static final String USAGE_TODO =
      " TODO: RepositoryService usage was not migrated automatically. Migrate it manually.";

  private static final MethodMatcher CREATE_DEPLOYMENT =
      new MethodMatcher(REPOSITORY_SERVICE + " createDeployment()");
  private static final MethodMatcher PROCESS_ENGINE_GET_REPOSITORY_SERVICE =
      new MethodMatcher(PROCESS_ENGINE + " getRepositoryService()");
  private static final MethodMatcher REPOSITORY_SERVICE_QUERY =
      new MethodMatcher(REPOSITORY_SERVICE + " create*Query(..)");
  private static final MethodMatcher REPOSITORY_SERVICE_METHOD =
      new MethodMatcher(REPOSITORY_SERVICE + " *(..)");
  private static final MethodMatcher DEPLOY =
      new MethodMatcher("org.camunda.bpm.engine.repository.DeploymentBuilder deploy()");

  private static final class ClassContext {
    private final Map<String, String> repositoryServiceClients;
    private final Set<String> repositoryServiceFields;
    private final Set<String> nonFieldRepositoryServiceVariables;
    private final boolean canConvertRepositoryServiceFields;
    private final boolean hasCamundaClient;
    private final boolean hasSplitDeploymentBuilder;

    private ClassContext(
        Map<String, String> repositoryServiceClients,
        Set<String> repositoryServiceFields,
        Set<String> nonFieldRepositoryServiceVariables,
        boolean canConvertRepositoryServiceFields,
        boolean hasCamundaClient,
        boolean hasSplitDeploymentBuilder) {
      this.repositoryServiceClients = repositoryServiceClients;
      this.repositoryServiceFields = repositoryServiceFields;
      this.nonFieldRepositoryServiceVariables = nonFieldRepositoryServiceVariables;
      this.canConvertRepositoryServiceFields = canConvertRepositoryServiceFields;
      this.hasCamundaClient = hasCamundaClient;
      this.hasSplitDeploymentBuilder = hasSplitDeploymentBuilder;
    }
  }

  @Override
  public String getDisplayName() {
    return "Migrate RepositoryService deployments";
  }

  @Override
  public String getDescription() {
    return "Replaces RepositoryService deployment builders with CamundaClient deploy commands and "
        + "adds migration guidance for RepositoryService queries.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return Preconditions.check(
        Preconditions.or(
            new org.openrewrite.java.search.UsesType<>(REPOSITORY_SERVICE, true),
            new org.openrewrite.java.search.UsesMethod<>(REPOSITORY_SERVICE + " createDeployment()", true),
            new org.openrewrite.java.search.UsesMethod<>(
                REPOSITORY_SERVICE + " create*Query(..)", true)),
        new JavaIsoVisitor<>() {
          private ClassContext classContext;

          @Override
          public J.CompilationUnit visitCompilationUnit(
              J.CompilationUnit compilationUnit, ExecutionContext ctx) {
            J.CompilationUnit visited = super.visitCompilationUnit(compilationUnit, ctx);
            if (!containsRepositoryServiceType(visited, ctx)) {
              return visited.withImports(
                  visited.getImports().stream()
                      .filter(
                          import_ ->
                              !import_.getQualid().toString().equals(REPOSITORY_SERVICE))
                      .toList());
            }
            return visited;
          }

          @Override
          public J.ClassDeclaration visitClassDeclaration(
              J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
            ClassContext previousContext = classContext;
            classContext = createClassContext(classDeclaration, ctx);
            boolean hasDirectRepositoryServiceFieldReference =
                containsDirectRepositoryServiceFieldReference(classDeclaration, ctx);

            try {
              List<Statement> statements = new ArrayList<>();
              for (Statement statement : classDeclaration.getBody().getStatements()) {
                if (!(statement instanceof VariableDeclarations declaration)
                    || !isDirectRepositoryServiceType(declaration)) {
                  statements.add(statement);
                  continue;
                }

                if (!classContext.canConvertRepositoryServiceFields) {
                  statements.add(
                      isExternallyAccessibleRepositoryServiceField(declaration)
                          && !hasDirectRepositoryServiceFieldReference
                          ? addCommentIfMissing(declaration, USAGE_TODO)
                          : statement);
                } else if (!classContext.hasCamundaClient) {
                  statements.add(replaceType(declaration));
                }
              }

              if (classContext.hasCamundaClient
                  || classContext.canConvertRepositoryServiceFields) {
                maybeAddImport(CAMUNDA_CLIENT);
              }
              J.ClassDeclaration visited =
                  super.visitClassDeclaration(
                      classDeclaration
                          .withBody(classDeclaration.getBody().withStatements(statements)),
                      ctx);
              return visited;
            } finally {
              classContext = previousContext;
            }
          }

          @Override
          public J.MethodInvocation visitMethodInvocation(
              J.MethodInvocation invocation, ExecutionContext ctx) {
            J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);

            if (DEPLOY.matches(visited) && containsMethod(visited, CREATE_DEPLOYMENT)) {
              return migrateDeployment(visited, ctx);
            }

            return visited;
          }

          private ClassContext createClassContext(
              J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
            Set<String> repositoryServiceFields = new HashSet<>();
            Set<UUID> repositoryServiceFieldDeclarationIds = new HashSet<>();
            Set<UUID> fieldDeclarationIds = new HashSet<>();
            String existingClientIdentifier = null;
            for (Statement statement : classDeclaration.getBody().getStatements()) {
              if (!(statement instanceof VariableDeclarations declaration)) {
                continue;
              }
              fieldDeclarationIds.add(declaration.getId());
              if (isDirectRepositoryServiceType(declaration)) {
                repositoryServiceFieldDeclarationIds.add(declaration.getId());
                declaration.getVariables().stream()
                    .map(J.VariableDeclarations.NamedVariable::getSimpleName)
                    .forEach(repositoryServiceFields::add);
              } else if (existingClientIdentifier == null
                  && TypeUtils.isOfClassType(declaration.getType(), CAMUNDA_CLIENT)) {
                existingClientIdentifier =
                    declaration.getVariables().get(0).getSimpleName();
              }
            }

            Set<String> nonFieldRepositoryServiceVariables =
                collectNonFieldRepositoryServiceVariables(
                    classDeclaration, fieldDeclarationIds, ctx);
            boolean repositoryServiceFieldShadowed =
                hasRepositoryServiceFieldShadowing(
                    classDeclaration, fieldDeclarationIds, repositoryServiceFields, ctx);
            boolean clientIdentifierShadowed =
                existingClientIdentifier != null
                    && hasNonFieldVariableNamed(
                        classDeclaration, fieldDeclarationIds, existingClientIdentifier, ctx);
            boolean canConvertRepositoryServiceFields =
                !repositoryServiceFields.isEmpty()
                    && nonFieldRepositoryServiceVariables.isEmpty()
                    && !repositoryServiceFieldShadowed
                    && !clientIdentifierShadowed
                    && !hasExternallyAccessibleRepositoryServiceField(classDeclaration)
                    && hasRepositoryServiceUsage(
                        classDeclaration, repositoryServiceFields, ctx)
                    && !hasUnsupportedRepositoryServiceUsage(
                        classDeclaration,
                        repositoryServiceFields,
                        repositoryServiceFieldDeclarationIds,
                        nonFieldRepositoryServiceVariables,
                        ctx)
                    && !containsProcessEngineRepositoryServiceGetter(classDeclaration, ctx)
                    && !hasNestedClass(classDeclaration, ctx);
            Map<String, String> repositoryServiceClients = new HashMap<>();
            if (canConvertRepositoryServiceFields) {
              if (existingClientIdentifier != null && !clientIdentifierShadowed) {
                String clientIdentifier = existingClientIdentifier;
                repositoryServiceFields.forEach(
                    field -> {
                      if (!nonFieldRepositoryServiceVariables.contains(field)) {
                        repositoryServiceClients.put(field, clientIdentifier);
                      }
                    });
              } else {
                repositoryServiceFields.forEach(
                    field -> repositoryServiceClients.put(field, field));
              }
            }
            return new ClassContext(
                repositoryServiceClients,
                repositoryServiceFields,
                nonFieldRepositoryServiceVariables,
                canConvertRepositoryServiceFields,
                existingClientIdentifier != null,
                hasSplitDeploymentBuilder(classDeclaration, ctx));
          }

          private boolean hasRepositoryServiceFieldShadowing(
              J.ClassDeclaration classDeclaration,
              Set<UUID> fieldDeclarationIds,
              Set<String> repositoryServiceFields,
              ExecutionContext ctx) {
            boolean[] shadowed = {false};
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.VariableDeclarations visitVariableDeclarations(
                  J.VariableDeclarations declarations, ExecutionContext nestedCtx) {
                if (!fieldDeclarationIds.contains(declarations.getId())
                    && declarations.getVariables().stream()
                        .anyMatch(
                            variable ->
                                repositoryServiceFields.contains(variable.getSimpleName()))) {
                  shadowed[0] = true;
                }
                return super.visitVariableDeclarations(declarations, nestedCtx);
              }
            }.visit(classDeclaration, ctx);
            return shadowed[0];
          }

          private boolean hasExternallyAccessibleRepositoryServiceField(
              J.ClassDeclaration classDeclaration) {
            return classDeclaration.getBody().getStatements().stream()
                .filter(J.VariableDeclarations.class::isInstance)
                .map(J.VariableDeclarations.class::cast)
                .anyMatch(this::isExternallyAccessibleRepositoryServiceField);
          }

          private boolean isExternallyAccessibleRepositoryServiceField(
              J.VariableDeclarations declaration) {
            return isDirectRepositoryServiceType(declaration)
                && declaration.getModifiers().stream()
                    .noneMatch(
                        modifier -> modifier.getType() == J.Modifier.Type.Private);
          }

          private boolean hasNestedClass(
              J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
            boolean[] found = {false};
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.ClassDeclaration visitClassDeclaration(
                  J.ClassDeclaration nestedClass, ExecutionContext nestedCtx) {
                found[0] = true;
                return nestedClass;
              }

              @Override
              public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext nestedCtx) {
                if (newClass.getBody() != null) {
                  found[0] = true;
                }
                return newClass;
              }
            }.visit(classDeclaration.getBody(), ctx);
            return found[0];
          }

          private boolean hasUnsupportedRepositoryServiceUsage(
              J.ClassDeclaration classDeclaration,
              Set<String> repositoryServiceFields,
              Set<UUID> repositoryServiceFieldDeclarationIds,
              Set<String> nonFieldRepositoryServiceVariables,
              ExecutionContext ctx) {
            Set<UUID> createDeployments = new HashSet<>();
            Set<UUID> migratedCreateDeployments = new HashSet<>();
            boolean[] unsupportedUsage = {!nonFieldRepositoryServiceVariables.isEmpty()};

            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.MethodInvocation visitMethodInvocation(
                  J.MethodInvocation invocation, ExecutionContext nestedCtx) {
                String receiver = receiverIdentifier(invocation.getSelect());
                if (CREATE_DEPLOYMENT.matches(invocation)) {
                  createDeployments.add(invocation.getId());
                  if (!repositoryServiceFields.contains(receiver)) {
                    unsupportedUsage[0] = true;
                  }
                  return invocation;
                } else if (REPOSITORY_SERVICE_QUERY.matches(invocation)
                    || repositoryServiceFields.contains(receiver)
                    || isProcessEngineRepositoryServiceGetter(invocation)) {
                  unsupportedUsage[0] = true;
                }
                if (invocation.getArguments().stream()
                    .anyMatch(
                        argument ->
                            isRepositoryServiceFieldReference(
                                argument, repositoryServiceFields))) {
                  unsupportedUsage[0] = true;
                }

                if (DEPLOY.matches(invocation)) {
                  List<J.MethodInvocation> sourceMethods = sourceMethods(invocation);
                  if (!sourceMethods.isEmpty()
                      && CREATE_DEPLOYMENT.matches(sourceMethods.get(0))) {
                    migratedCreateDeployments.add(sourceMethods.get(0).getId());
                    if (!isSupportedDeploymentChain(invocation)
                        || !(getCursor().getParentTreeCursor().getValue() instanceof J.Block)) {
                      unsupportedUsage[0] = true;
                    }
                  }
                }
                return super.visitMethodInvocation(invocation, nestedCtx);
              }

              @Override
              public J.VariableDeclarations visitVariableDeclarations(
                  J.VariableDeclarations declarations, ExecutionContext nestedCtx) {
                if (repositoryServiceFieldDeclarationIds.contains(declarations.getId())) {
                  if (declarations.getVariables().stream()
                      .anyMatch(variable -> variable.getInitializer() != null)) {
                    unsupportedUsage[0] = true;
                  }
                  return declarations;
                }
                return super.visitVariableDeclarations(declarations, nestedCtx);
              }

              @Override
              public J.Identifier visitIdentifier(
                  J.Identifier identifier, ExecutionContext nestedCtx) {
                if (repositoryServiceFields.contains(identifier.getSimpleName())) {
                  unsupportedUsage[0] = true;
                }
                return super.visitIdentifier(identifier, nestedCtx);
              }
            }.visit(classDeclaration, ctx);

            if (!createDeployments.equals(migratedCreateDeployments)) {
              unsupportedUsage[0] = true;
            }

            return unsupportedUsage[0];
          }

          private boolean hasRepositoryServiceUsage(
              J.ClassDeclaration classDeclaration,
              Set<String> repositoryServiceFields,
              ExecutionContext ctx) {
            boolean[] found = {false};
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.MethodInvocation visitMethodInvocation(
                  J.MethodInvocation invocation, ExecutionContext nestedCtx) {
                if (CREATE_DEPLOYMENT.matches(invocation)
                    || REPOSITORY_SERVICE_QUERY.matches(invocation)
                    || repositoryServiceFields.contains(receiverIdentifier(invocation.getSelect()))
                    || invocation.getArguments().stream()
                        .anyMatch(
                            argument ->
                                isRepositoryServiceFieldReference(
                                    argument, repositoryServiceFields))) {
                  found[0] = true;
                }
                return super.visitMethodInvocation(invocation, nestedCtx);
              }
            }.visit(classDeclaration, ctx);
            return found[0];
          }

          private boolean containsProcessEngineRepositoryServiceGetter(
              J tree, ExecutionContext ctx) {
            boolean[] found = {false};
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.MethodInvocation visitMethodInvocation(
                  J.MethodInvocation invocation, ExecutionContext nestedCtx) {
                if (isProcessEngineRepositoryServiceGetter(invocation)) {
                  found[0] = true;
                }
                return super.visitMethodInvocation(invocation, nestedCtx);
              }
            }.visit(tree, ctx);
            return found[0];
          }

          private boolean hasNonFieldVariableNamed(
              J.ClassDeclaration classDeclaration,
              Set<UUID> fieldDeclarationIds,
              String variableName,
              ExecutionContext ctx) {
            boolean[] shadowed = {false};
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.VariableDeclarations visitVariableDeclarations(
                  J.VariableDeclarations declarations, ExecutionContext nestedCtx) {
                if (!fieldDeclarationIds.contains(declarations.getId())
                    && declarations.getVariables().stream()
                        .anyMatch(variable -> variable.getSimpleName().equals(variableName))) {
                  shadowed[0] = true;
                }
                return super.visitVariableDeclarations(declarations, nestedCtx);
              }
            }.visit(classDeclaration, ctx);
            return shadowed[0];
          }

          private boolean hasSplitDeploymentBuilder(
              J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
            Set<UUID> createDeployments = new HashSet<>();
            Set<UUID> chainedDeployments = new HashSet<>();
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.MethodInvocation visitMethodInvocation(
                  J.MethodInvocation invocation, ExecutionContext nestedCtx) {
                if (CREATE_DEPLOYMENT.matches(invocation)) {
                  createDeployments.add(invocation.getId());
                } else if (DEPLOY.matches(invocation)) {
                  List<J.MethodInvocation> sourceMethods = sourceMethods(invocation);
                  if (!sourceMethods.isEmpty()
                      && CREATE_DEPLOYMENT.matches(sourceMethods.get(0))) {
                    chainedDeployments.add(sourceMethods.get(0).getId());
                  }
                }
                return super.visitMethodInvocation(invocation, nestedCtx);
              }
            }.visit(classDeclaration, ctx);
            return !createDeployments.isEmpty()
                && !createDeployments.equals(chainedDeployments);
          }

          private boolean containsRepositoryServiceType(J tree, ExecutionContext ctx) {
            boolean[] found = {false};
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.Import visitImport(J.Import import_, ExecutionContext nestedCtx) {
                return import_;
              }

              @Override
              public J.Identifier visitIdentifier(
                  J.Identifier identifier, ExecutionContext nestedCtx) {
                if (identifier.getSimpleName().equals("RepositoryService")) {
                  found[0] = true;
                }
                return super.visitIdentifier(identifier, nestedCtx);
              }
            }.visit(tree, ctx);
            return found[0];
          }

          private boolean isDirectRepositoryServiceType(
              J.VariableDeclarations declarations) {
            TypeTree typeExpression = declarations.getTypeExpression();
            boolean hasRepositoryServiceName =
                (typeExpression instanceof J.Identifier identifier
                        && identifier.getSimpleName().equals("RepositoryService"))
                    || (typeExpression instanceof J.FieldAccess fieldAccess
                        && fieldAccess.getName().getSimpleName().equals("RepositoryService"));
            return hasRepositoryServiceName
                && TypeUtils.isOfClassType(declarations.getType(), REPOSITORY_SERVICE);
          }

          private Set<String> collectNonFieldRepositoryServiceVariables(
              J.ClassDeclaration classDeclaration,
              Set<UUID> fieldDeclarationIds,
              ExecutionContext ctx) {
            Set<String> variables = new HashSet<>();
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.VariableDeclarations visitVariableDeclarations(
                  J.VariableDeclarations declarations, ExecutionContext nestedCtx) {
                if (TypeUtils.isOfClassType(declarations.getType(), REPOSITORY_SERVICE)
                    && !fieldDeclarationIds.contains(declarations.getId())) {
                  declarations.getVariables().stream()
                      .map(J.VariableDeclarations.NamedVariable::getSimpleName)
                      .forEach(variables::add);
                }
                return super.visitVariableDeclarations(declarations, nestedCtx);
              }
            }.visit(classDeclaration, ctx);
            return variables;
          }

          @Override
          public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            List<Statement> originalStatements = block.getStatements();
            J.Block visited = super.visitBlock(block, ctx);
            List<Statement> statements = new ArrayList<>();
            for (int i = 0; i < visited.getStatements().size(); i++) {
              Statement statement = visited.getStatements().get(i);
              if (statement instanceof J.MethodDeclaration
                  || statement instanceof J.ClassDeclaration) {
                statements.add(statement);
              } else {
                Statement annotated = statement;
                boolean hasDirectRepositoryServiceReference =
                    i < originalStatements.size()
                        && containsDirectRepositoryServiceFieldReference(
                            originalStatements.get(i), ctx);
                boolean hasRepositoryQuery = containsRepositoryQuery(annotated, ctx);
                boolean hasRepositoryDeployment = containsRepositoryDeployment(annotated, ctx);
                if (hasRepositoryQuery) {
                  annotated = addCommentIfMissing(annotated, QUERY_TODO);
                }
                if (hasRepositoryDeployment
                    || (classContext != null
                        && classContext.hasSplitDeploymentBuilder
                        && containsDeploymentBuilderDeploy(annotated, ctx))) {
                  annotated = addCommentIfMissing(annotated, DEPLOYMENT_TODO);
                }
                if ((!hasRepositoryQuery
                        && !hasRepositoryDeployment
                        && containsProcessEngineRepositoryServiceGetter(annotated, ctx))
                    || containsUnsupportedRepositoryServiceUsage(annotated, ctx)
                    || hasDirectRepositoryServiceReference) {
                  annotated = addCommentIfMissing(annotated, USAGE_TODO);
                }
                statements.add(annotated);
              }
            }
            return visited.withStatements(statements);
          }

          @Override
          public J.VariableDeclarations visitVariableDeclarations(
              J.VariableDeclarations declarations, ExecutionContext ctx) {
            boolean hasDirectRepositoryServiceReference =
                containsDirectRepositoryServiceFieldReference(declarations, ctx);
            J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, ctx);
            boolean hasRepositoryQuery = containsRepositoryQuery(visited, ctx);
            boolean hasRepositoryDeployment = containsRepositoryDeployment(visited, ctx);
            if (hasRepositoryQuery) {
              return addCommentIfMissing(visited, QUERY_TODO);
            }
            if (hasRepositoryDeployment) {
              visited = addCommentIfMissing(visited, DEPLOYMENT_TODO);
            }
            if ((!hasRepositoryQuery
                    && !hasRepositoryDeployment
                    && containsProcessEngineRepositoryServiceGetter(visited, ctx))
                || containsUnsupportedRepositoryServiceUsage(visited, ctx)
                || hasDirectRepositoryServiceReference) {
              visited = addCommentIfMissing(visited, USAGE_TODO);
            }
            return visited;
          }

          @Override
          public J.Return visitReturn(J.Return returnStatement, ExecutionContext ctx) {
            boolean hasDirectRepositoryServiceReference =
                containsDirectRepositoryServiceFieldReference(returnStatement, ctx);
            J.Return visited = super.visitReturn(returnStatement, ctx);
            if (visited.getExpression() instanceof J.MethodInvocation invocation
                && containsRepositoryQuery(invocation)) {
              return addCommentIfMissing(visited, QUERY_TODO);
            }
            if (containsProcessEngineRepositoryServiceGetter(visited, ctx)) {
              return addCommentIfMissing(visited, USAGE_TODO);
            }
            if (containsUnsupportedRepositoryServiceUsage(visited, ctx)
                || hasDirectRepositoryServiceReference) {
              return addCommentIfMissing(visited, USAGE_TODO);
            }
            return visited;
          }

          private J.MethodInvocation migrateDeployment(
              J.MethodInvocation deployInvocation, ExecutionContext ctx) {
            if (!(getCursor().getParentTreeCursor().getValue() instanceof J.Block)) {
              return deployInvocation;
            }

            List<J.MethodInvocation> sourceMethods = sourceMethods(deployInvocation);

            if (sourceMethods.isEmpty() || !CREATE_DEPLOYMENT.matches(sourceMethods.get(0))) {
              return addCommentIfMissing(deployInvocation, DEPLOYMENT_TODO);
            }
            String clientIdentifier =
                classContext == null
                    ? null
                    : classContext.repositoryServiceClients.get(
                        receiverIdentifier(sourceMethods.get(0).getSelect()));
            if (clientIdentifier == null) {
              return addCommentIfMissing(deployInvocation, DEPLOYMENT_TODO);
            }

            StringBuilder templateCode =
                new StringBuilder(
                    "#{client:any(io.camunda.client.CamundaClient)}"
                        + "\n    .newDeployResourceCommand()");
            List<Expression> arguments = new ArrayList<>();
            boolean hasResource = false;
            Expression tenantId = null;

            for (J.MethodInvocation method : sourceMethods.subList(1, sourceMethods.size())) {
              switch (method.getSimpleName()) {
                case "addClasspathResource" -> {
                  if (method.getArguments().size() != 1) {
                    return addCommentIfMissing(deployInvocation, DEPLOYMENT_TODO);
                  }
                  templateCode.append("\n    .addResourceFromClasspath(#{any(java.lang.String)})");
                  arguments.add(method.getArguments().get(0));
                  hasResource = true;
                }
                case "addInputStream" -> {
                  if (method.getArguments().size() != 2) {
                    return addCommentIfMissing(deployInvocation, DEPLOYMENT_TODO);
                  }
                  templateCode.append(
                      "\n    .addResourceStream(#{any(java.io.InputStream)}, #{any(java.lang.String)})");
                  arguments.add(method.getArguments().get(1));
                  arguments.add(method.getArguments().get(0));
                  hasResource = true;
                }
                case "addString" -> {
                  if (method.getArguments().size() != 2) {
                    return addCommentIfMissing(deployInvocation, DEPLOYMENT_TODO);
                  }
                  templateCode.append(
                      "\n    .addResourceStringUtf8(#{any(java.lang.String)}, #{any(java.lang.String)})");
                  arguments.add(method.getArguments().get(0));
                  arguments.add(method.getArguments().get(1));
                  hasResource = true;
                }
                case "tenantId" -> {
                  if (method.getArguments().size() != 1) {
                    return addCommentIfMissing(deployInvocation, DEPLOYMENT_TODO);
                  }
                  if (tenantId != null) {
                    return addCommentIfMissing(deployInvocation, DEPLOYMENT_TODO);
                  }
                  tenantId = method.getArguments().get(0);
                }
                case "name", "source" -> {
                  // Deployment names and sources have no direct equivalent in the C8 command.
                }
                default -> {
                  return addCommentIfMissing(deployInvocation, DEPLOYMENT_TODO);
                }
              }
            }

            if (!hasResource) {
              return addCommentIfMissing(deployInvocation, DEPLOYMENT_TODO);
            }

            if (tenantId != null) {
              templateCode.append("\n    .tenantId(#{any(java.lang.String)})");
              arguments.add(tenantId);
            }
            templateCode.append("\n    .send()\n    .join()");
            JavaTemplate template =
                RecipeUtils.createSimpleJavaTemplate(
                    templateCode.toString(),
                    CAMUNDA_CLIENT,
                    "io.camunda.client.api.command.DeployResourceCommandStep1");
            Object[] templateArguments = new Object[arguments.size() + 1];
            templateArguments[0] =
                RecipeUtils.createSimpleIdentifier(clientIdentifier, CAMUNDA_CLIENT);
            System.arraycopy(arguments.toArray(), 0, templateArguments, 1, arguments.size());
            J.MethodInvocation replacement =
                (J.MethodInvocation)
                    RecipeUtils.applyTemplate(
                        template, deployInvocation, getCursor(), templateArguments, List.of());
            return maybeAutoFormat(deployInvocation, replacement, ctx);
          }

          private boolean isSupportedDeploymentChain(J.MethodInvocation deployInvocation) {
            List<J.MethodInvocation> sourceMethods = sourceMethods(deployInvocation);
            if (sourceMethods.isEmpty() || !CREATE_DEPLOYMENT.matches(sourceMethods.get(0))) {
              return false;
            }

            boolean hasResource = false;
            boolean hasTenantId = false;
            for (J.MethodInvocation method : sourceMethods.subList(1, sourceMethods.size())) {
              switch (method.getSimpleName()) {
                case "addClasspathResource" -> {
                  if (method.getArguments().size() != 1) {
                    return false;
                  }
                  hasResource = true;
                }
                case "addInputStream", "addString" -> {
                  if (method.getArguments().size() != 2) {
                    return false;
                  }
                  hasResource = true;
                }
                case "tenantId" -> {
                  if (method.getArguments().size() != 1 || hasTenantId) {
                    return false;
                  }
                  hasTenantId = true;
                }
                case "name", "source" -> {}
                default -> {
                  return false;
                }
              }
            }
            return hasResource;
          }

          private boolean containsRepositoryQuery(J.MethodInvocation invocation) {
            return containsMethod(invocation, REPOSITORY_SERVICE_QUERY);
          }

          private boolean containsRepositoryQuery(J tree, ExecutionContext ctx) {
            boolean[] found = {false};
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.MethodInvocation visitMethodInvocation(
                  J.MethodInvocation invocation, ExecutionContext nestedCtx) {
                if (REPOSITORY_SERVICE_QUERY.matches(invocation)) {
                  found[0] = true;
                }
                return super.visitMethodInvocation(invocation, nestedCtx);
              }
            }.visit(tree, ctx);
            return found[0];
          }

          private boolean containsUnsupportedRepositoryServiceUsage(
              J tree, ExecutionContext ctx) {
            boolean[] found = {false};
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.MethodInvocation visitMethodInvocation(
                  J.MethodInvocation invocation, ExecutionContext nestedCtx) {
                if (isUnsupportedRepositoryServiceInvocation(invocation)) {
                  found[0] = true;
                }
                if (classContext != null
                    && invocation.getArguments().stream()
                        .anyMatch(
                            argument ->
                                isRepositoryServiceFieldReference(
                                    argument, classContext.repositoryServiceFields))) {
                  found[0] = true;
                }
                return super.visitMethodInvocation(invocation, nestedCtx);
              }
            }.visit(tree, ctx);
            return found[0];
          }

          private boolean isUnsupportedRepositoryServiceInvocation(
              J.MethodInvocation invocation) {
            if (CREATE_DEPLOYMENT.matches(invocation)
                || REPOSITORY_SERVICE_QUERY.matches(invocation)) {
              return false;
            }
            return REPOSITORY_SERVICE_METHOD.matches(invocation)
                || (classContext != null
                    && !classContext.canConvertRepositoryServiceFields
                    && isNamedRepositoryServiceFieldReference(
                        invocation.getSelect(), classContext.repositoryServiceFields));
          }

          private boolean isProcessEngineRepositoryServiceGetter(
              J.MethodInvocation invocation) {
            return PROCESS_ENGINE_GET_REPOSITORY_SERVICE.matches(invocation)
                || invocation.getSimpleName().equals("getRepositoryService");
          }

          private boolean containsDirectRepositoryServiceFieldReference(
              J tree, ExecutionContext ctx) {
            boolean[] found = {false};
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.MethodInvocation visitMethodInvocation(
                  J.MethodInvocation invocation, ExecutionContext nestedCtx) {
                if (classContext != null
                    && isNamedRepositoryServiceFieldReference(
                        invocation.getSelect(), classContext.repositoryServiceFields)) {
                  if (!CREATE_DEPLOYMENT.matches(invocation)
                      && !REPOSITORY_SERVICE_QUERY.matches(invocation)) {
                    found[0] = true;
                  }
                  invocation.getArguments().forEach(argument -> visit(argument, nestedCtx));
                  return invocation;
                }
                return super.visitMethodInvocation(invocation, nestedCtx);
              }

              @Override
              public J.VariableDeclarations visitVariableDeclarations(
                  J.VariableDeclarations declarations, ExecutionContext nestedCtx) {
                declarations.getVariables().stream()
                    .map(J.VariableDeclarations.NamedVariable::getInitializer)
                    .filter(java.util.Objects::nonNull)
                    .forEach(initializer -> visit(initializer, nestedCtx));
                return declarations;
              }

              @Override
              public J.Identifier visitIdentifier(
                  J.Identifier identifier, ExecutionContext nestedCtx) {
                if (classContext != null
                    && !isVariableDeclarationName(identifier)
                    && isNamedRepositoryServiceFieldReference(
                        identifier, classContext.repositoryServiceFields)) {
                  found[0] = true;
                }
                return super.visitIdentifier(identifier, nestedCtx);
              }

              @Override
              public J.MemberReference visitMemberReference(
                  J.MemberReference memberReference, ExecutionContext nestedCtx) {
                if (classContext != null
                    && isNamedRepositoryServiceFieldReference(
                        memberReference.getContaining(), classContext.repositoryServiceFields)) {
                  found[0] = true;
                }
                return super.visitMemberReference(memberReference, nestedCtx);
              }
            }.visit(tree, ctx);
            return found[0];
          }

          private boolean containsRepositoryDeployment(J tree, ExecutionContext ctx) {
            boolean[] found = {false};
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.MethodInvocation visitMethodInvocation(
                  J.MethodInvocation invocation, ExecutionContext nestedCtx) {
                if (CREATE_DEPLOYMENT.matches(invocation)) {
                  found[0] = true;
                }
                return super.visitMethodInvocation(invocation, nestedCtx);
              }
            }.visit(tree, ctx);
            return found[0];
          }

          private boolean containsDeploymentBuilderDeploy(J tree, ExecutionContext ctx) {
            boolean[] found = {false};
            new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.MethodInvocation visitMethodInvocation(
                  J.MethodInvocation invocation, ExecutionContext nestedCtx) {
                if (DEPLOY.matches(invocation)) {
                  found[0] = true;
                }
                return super.visitMethodInvocation(invocation, nestedCtx);
              }
            }.visit(tree, ctx);
            return found[0];
          }

          private J.VariableDeclarations replaceType(J.VariableDeclarations declaration) {
            J.Identifier type = RecipeUtils.createSimpleIdentifier("CamundaClient", CAMUNDA_CLIENT);
            if (declaration.getTypeExpression() instanceof J.Identifier identifier) {
              type = type.withPrefix(identifier.getPrefix());
            } else if (declaration.getTypeExpression() instanceof J.FieldAccess fieldAccess) {
              type = type.withPrefix(fieldAccess.getPrefix());
            }
            return declaration
                .withTypeExpression(type)
                .withType(JavaType.ShallowClass.build(CAMUNDA_CLIENT));
          }

          private J.VariableDeclarations addCommentIfMissing(
              J.VariableDeclarations declaration, String text) {
            if (declaration.getComments().stream()
                .anyMatch(
                    comment ->
                        comment instanceof TextComment textComment
                            && textComment.getText().contains(text.trim()))) {
              return declaration;
            }
            return declaration.withComments(
                java.util.stream.Stream.concat(
                        declaration.getComments().stream(),
                        java.util.stream.Stream.of(
                            RecipeUtils.createSimpleComment(declaration, text)))
                    .toList());
          }

          private Statement addCommentIfMissing(Statement statement, String text) {
            if (statement.getComments().stream()
                .anyMatch(
                    comment ->
                        comment instanceof TextComment textComment
                            && textComment.getText().contains(text.trim()))) {
              return statement;
            }
            return statement.withComments(
                java.util.stream.Stream.concat(
                        statement.getComments().stream(),
                        java.util.stream.Stream.of(
                            RecipeUtils.createSimpleComment(statement, text)))
                    .toList());
          }

          private boolean containsMethod(J.MethodInvocation invocation, MethodMatcher matcher) {
            for (J.MethodInvocation method : invocationChain(invocation)) {
              if (matcher.matches(method)) {
                return true;
              }
            }
            return false;
          }

          private List<J.MethodInvocation> invocationChain(J.MethodInvocation invocation) {
            List<J.MethodInvocation> chain = new ArrayList<>();
            Expression current = invocation;
            while (current instanceof J.MethodInvocation method) {
              chain.add(method);
              current = method.getSelect();
            }
            return chain;
          }

          private List<J.MethodInvocation> sourceMethods(J.MethodInvocation deployInvocation) {
            List<J.MethodInvocation> sourceMethods = invocationChain(deployInvocation);
            sourceMethods.remove(0);
            java.util.Collections.reverse(sourceMethods);
            return sourceMethods;
          }

          private String receiverIdentifier(Expression select) {
            if (select instanceof J.Identifier identifier) {
              return identifier.getSimpleName();
            }
            if (select instanceof J.FieldAccess fieldAccess) {
              if (fieldAccess.getTarget() instanceof J.Identifier target
                  && target.getSimpleName().equals("this")) {
                return fieldAccess.getName().getSimpleName();
              }
            }
            return null;
          }

          private boolean isVariableDeclarationName(J.Identifier identifier) {
            J.VariableDeclarations.NamedVariable variable =
                getCursor().firstEnclosing(J.VariableDeclarations.NamedVariable.class);
            return variable != null
                && variable.getName().getId().equals(identifier.getId());
          }

          private boolean isRepositoryServiceFieldReference(
              Expression expression, Set<String> repositoryServiceFields) {
            if (expression instanceof J.Identifier identifier) {
              return isRepositoryServiceFieldReference(identifier, repositoryServiceFields);
            }
            return expression instanceof J.FieldAccess fieldAccess
                && isRepositoryServiceFieldReference(
                    fieldAccess.getName(), repositoryServiceFields);
          }

          private boolean isRepositoryServiceFieldReference(
              J.Identifier identifier, Set<String> repositoryServiceFields) {
            if (!repositoryServiceFields.contains(identifier.getSimpleName())) {
              return false;
            }
            JavaType.Variable fieldType = identifier.getFieldType();
            return fieldType == null
                || TypeUtils.isOfClassType(fieldType.getType(), REPOSITORY_SERVICE);
          }

          private boolean isNamedRepositoryServiceFieldReference(
              Expression expression, Set<String> repositoryServiceFields) {
            return (expression instanceof J.Identifier identifier
                    && repositoryServiceFields.contains(identifier.getSimpleName()))
                || (expression instanceof J.FieldAccess fieldAccess
                    && repositoryServiceFields.contains(
                        fieldAccess.getName().getSimpleName()));
          }

          private J.Return addCommentIfMissing(J.Return statement, String text) {
            if (statement.getComments().stream()
                  .anyMatch(
                      comment ->
                          comment instanceof TextComment textComment
                              && textComment.getText().contains(text.trim()))) {
              return statement;
            }
            return statement.withComments(
                  java.util.stream.Stream.concat(
                          statement.getComments().stream(),
                          java.util.stream.Stream.of(RecipeUtils.createSimpleComment(statement, text)))
                      .toList());
          }

          private J.MethodInvocation addCommentIfMissing(
              J.MethodInvocation invocation, String text) {
            if (invocation.getComments().stream()
                .anyMatch(
                      comment ->
                          comment instanceof TextComment textComment
                              && textComment.getText().contains(text.trim()))) {
              return invocation;
            }
            return invocation.withComments(
                java.util.stream.Stream.concat(
                          invocation.getComments().stream(),
                          java.util.stream.Stream.of(RecipeUtils.createSimpleComment(invocation, text)))
                      .toList());
          }

        });
  }
}
