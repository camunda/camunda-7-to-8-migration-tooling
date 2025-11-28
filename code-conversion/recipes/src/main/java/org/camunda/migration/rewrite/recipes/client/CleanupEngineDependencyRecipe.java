package org.camunda.migration.rewrite.recipes.client;

import java.util.*;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.*;
import org.openrewrite.java.tree.*;

public class CleanupEngineDependencyRecipe extends Recipe {
  /** Instantiates a new instance. */
  public CleanupEngineDependencyRecipe() {}

  @Override
  public String getDisplayName() {
    return "Remove process engine dependency";
  }

  @Override
  public String getDescription() {
    return "Removes process engine dependency. Tries to remove import.";
  }

  String PROCESS_ENGINE = "org.camunda.bpm.engine.ProcessEngine";
  String RUNTIME_SERVICE = "org.camunda.bpm.engine.RuntimeService";
  String TASK_SERVICE = "org.camunda.bpm.engine.TaskService";
  String REPOSITORY_SERVICE = "org.camunda.bpm.engine.RepositoryService";

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {

    // define preconditions
    TreeVisitor<?, ExecutionContext> check =
        Preconditions.or(
            new UsesType<>(PROCESS_ENGINE, true),
            new UsesType<>(RUNTIME_SERVICE, true),
            new UsesType<>(TASK_SERVICE, true),
            new UsesType<>(REPOSITORY_SERVICE, true));

    return Preconditions.check(
        check,
        new JavaIsoVisitor<>() {

          /**
           * Removing an LST element cannot be done by visiting it directly. Visiting
           * J.ClassDeclaration targets the smallest LST element to filter out the dependencies to
           * be removed. The body of the class contains statements, which can be any type that is
           * allowed to be in a class body. All statements apart from the ones to be removed are
           * collected. If the statement is instanceof J.VariableDeclarations and defines variables
           * of the type to remove, it is not collected. The class declaration is returned with new
           * statements in its body.
           */
          @Override
          public J.ClassDeclaration visitClassDeclaration(
              J.ClassDeclaration classDeclaration, ExecutionContext ctx) {

            List<Statement> newStatements = new ArrayList<>();
            for (Statement statement : classDeclaration.getBody().getStatements()) {
              if (statement instanceof J.VariableDeclarations varDecls
                  && (TypeUtils.isOfClassType(varDecls.getType(), PROCESS_ENGINE)
                      || TypeUtils.isOfClassType(varDecls.getType(), RUNTIME_SERVICE)
                      || TypeUtils.isOfClassType(varDecls.getType(), TASK_SERVICE)
                      || TypeUtils.isOfClassType(varDecls.getType(), REPOSITORY_SERVICE))) {
                // This is the statement we want to remove, so skip adding it
                continue;
              }
              newStatements.add(statement);
            }

            maybeRemoveImport(PROCESS_ENGINE);
            maybeRemoveImport(RUNTIME_SERVICE);
            maybeRemoveImport(TASK_SERVICE);
            maybeRemoveImport(REPOSITORY_SERVICE);

            return classDeclaration.withBody(
                classDeclaration.getBody().withStatements(newStatements));
          }
        });
  }
}
