package org.camunda.migration.rewrite.recipes.client.cleanup;

import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.*;
import org.openrewrite.java.template.internal.AbstractRefasterJavaVisitor;
import org.openrewrite.java.tree.*;

import java.util.*;

public class RemoveEngineDependencyRecipe extends Recipe {
    /**
     * Instantiates a new instance.
     */
    public RemoveEngineDependencyRecipe() {
    }

    @Override
    public String getDisplayName() {
        return "Remove process engine dependency";
    }

    @Override
    public String getDescription() {
        return "Removes process engine dependency.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaVisitor<ExecutionContext> javaVisitor = new AbstractRefasterJavaVisitor() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                System.out.println(classDecl);
                List<Statement> newStatements = new ArrayList<>();
                for (Statement statement : classDecl.getBody().getStatements()) {
                    if (statement instanceof J.VariableDeclarations varDecls) {
                        JavaType type = varDecls.getType();
                        if (TypeUtils.isOfClassType(type, "org.camunda.bpm.engine.ProcessEngine")) {
                            // This is the field we want to remove, so skip adding it
                            continue;
                        }
                    }
                    newStatements.add(statement);
                }

                maybeRemoveImport("org.camunda.bpm.engine.ProcessEngine");

                return classDecl.withBody(classDecl.getBody().withStatements(newStatements));
            }
        };
        return Preconditions.check(
                new UsesType<>("org.camunda.bpm.engine.ProcessEngine", true),
                javaVisitor
        );

    }
}
