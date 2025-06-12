package org.camunda.migration.rewrite.recipes.client;

import org.camunda.migration.rewrite.recipes.client.migrate.ReplaceCancelProcessInstanceMethodsRecipe;
import org.camunda.migration.rewrite.recipes.client.migrate.ReplaceSignalMethodsRecipe;
import org.camunda.migration.rewrite.recipes.client.migrate.ReplaceStartProcessInstanceMethodsRecipe;
import org.openrewrite.Recipe;
import org.openrewrite.java.ReplaceAnnotation;

import java.util.Arrays;
import java.util.List;

public class AllClientMigrateRecipes extends Recipe {

    /**
     * Instantiates a new instance.
     */
    public AllClientMigrateRecipes(String CLIENT_WRAPPER_PACKAGE) {
        this.CLIENT_WRAPPER_PACKAGE = CLIENT_WRAPPER_PACKAGE;
    }

    @Override
    public String getDisplayName() {
        return "Runs all client migrate recipes";
    }

    @Override
    public String getDescription() {
        return "Replaces signal methods, replaces cancel process instance methods, replaces deployment annotations";
    }

    String CLIENT_WRAPPER_PACKAGE;

    @Override
    public List<Recipe> getRecipeList() {
        return Arrays.asList(
                new ReplaceSignalMethodsRecipe(CLIENT_WRAPPER_PACKAGE),
                new ReplaceCancelProcessInstanceMethodsRecipe(CLIENT_WRAPPER_PACKAGE),
                new ReplaceStartProcessInstanceMethodsRecipe(CLIENT_WRAPPER_PACKAGE),
                new ReplaceAnnotation("@org.camunda.bpm.spring.boot.starter.annotation.EnableProcessApplication", "@io.camunda.spring.client.annotation.Deployment(resources = \"classpath*:/bpmn/**/*.bpmn\")", null)
        );
    }
}
