# Developer Guide: Migrating from Camunda 7 to Camunda 8

This developer guide extends the user guide.

## Introduction

This guide is aimed at developers who are adjusting or extending the migration recipes. Such changes are likely to affect the following aspects:

* preconditions of existing recipes to ensure recipes are applied
* additional transformation rules for existing recipes based on the abstract migration recipe
* new recipes based on the abstract migration recipe to cover more client code
* bug fixes

## Project Structure

In the [README](./README.md), the recipes that are available for the user are presented, e.g., AllClientPrepareRecipes. These recipes are themselves made up of multiple recipes that make granular changes. You can find all custom recipes in the [source folder](./src/main/java/org/camunda/migration/rewrite/recipes), separated by type of code and transformation phase. These custom recipes are supplemented with existing OpenRewrite recipes and composed into the aforementioned usable declarative recipes. You can inspect their composition in the [META-INF.rewrite](./src/main/resources/META-INF/rewrite) folder. When adding a new custom or existing OpenRewrite recipe, ensure that it is added to the correct composed recipe.

The folder [sharedRecipes](./src/main/java/org/camunda/migration/rewrite/recipes/sharedRecipes) contains two important recipes:

* AbstractMigrationRecipe: extracted transformation logic for reusability purposes
* ReplaceTypedValueAPIRecipe: a combined recipe to transform TypedValueAPI types and method calls to JavaObjectAPI types and method calls

The folder [utils](./src/main/java/org/camunda/migration/rewrite/recipes/utils)