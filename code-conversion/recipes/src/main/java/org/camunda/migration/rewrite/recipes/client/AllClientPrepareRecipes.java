package org.camunda.migration.rewrite.recipes.client;

import org.camunda.migration.rewrite.recipes.client.prepare.AddCamundaClientWrapperDependencyRecipe;
import org.camunda.migration.rewrite.recipes.client.prepare.AddProcessEngineDependencyRecipe;
import org.camunda.migration.rewrite.recipes.client.prepare.EnsureProcessEngineRecipe;
import org.camunda.migration.rewrite.recipes.client.prepare.ReplaceTypedValueAPIRecipe;
import org.openrewrite.Recipe;
import org.openrewrite.java.dependencies.AddDependency;

import java.util.Arrays;
import java.util.List;

public class AllClientPrepareRecipes extends Recipe {

    /**
     * Instantiates a new instance.
     */
    public AllClientPrepareRecipes(String CLIENT_WRAPPER_PACKAGE) {
        this.CLIENT_WRAPPER_PACKAGE = CLIENT_WRAPPER_PACKAGE;
    }

    @Override
    public String getDisplayName() {
        return "Runs all client prepare recipes";
    }

    @Override
    public String getDescription() {
        return "Adds new dependencies, ensures usage of process engine instead of services, converts TypedValue API to JavaObject API.";
    }

    String CLIENT_WRAPPER_PACKAGE;

    @Override
    public List<Recipe> getRecipeList() {
        return Arrays.asList(
                new AddDependency("io.camunda", "spring-boot-starter-camunda-sdk", "8.8.0-alpha4.1", null, null, null, null, null, null, null, null, null, null, null),
                new AddCamundaClientWrapperDependencyRecipe(CLIENT_WRAPPER_PACKAGE),
                new AddProcessEngineDependencyRecipe(),
                new EnsureProcessEngineRecipe(),
                new ReplaceTypedValueAPIRecipe()
        );
    }
}
