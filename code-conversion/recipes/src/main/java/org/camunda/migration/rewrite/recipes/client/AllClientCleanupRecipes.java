package org.camunda.migration.rewrite.recipes.client;

import org.camunda.migration.rewrite.recipes.client.cleanup.RemoveEngineDependencyRecipe;
import org.camunda.migration.rewrite.recipes.client.cleanup.RemoveImportsManuallyRecipe;
import org.openrewrite.Recipe;
import org.openrewrite.java.RemoveUnusedImports;

import java.util.Arrays;
import java.util.List;

public class AllClientCleanupRecipes extends Recipe {

    /**
     * Instantiates a new instance.
     */
    public AllClientCleanupRecipes() {

    }

    @Override
    public String getDisplayName() {
        return "Runs all client cleanup recipes";
    }

    @Override
    public String getDescription() {
        return "Removes engine dependencies";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return Arrays.asList(
                new RemoveEngineDependencyRecipe(),
                new RemoveUnusedImports(),
                new RemoveImportsManuallyRecipe()
        );
    }
}
