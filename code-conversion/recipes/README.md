# OpenRewrite recipes refactoring code from Camunda 7 to Camunda 8 

> [!NOTE]  
> The current recipes are more a proof of concept, the whole project was just kicked off and will be filled with more and better recipes throughout Q2 of 2025. Feedback of course welcome.

This project contains [recipes](https://docs.openrewrite.org/concepts-and-explanations/recipes) for the open source tool [OpenRewrite](https://docs.openrewrite.org/) provided under Apache License.

Those recipes automate refactoring according to the [code conversion patterns](../patterns/).

# Manual preparation - the CamundaClientWrapper

The Camunda Spring SDK client offers blocking and reactive programming styles via sophisticated builder patterns. The recipes in this project are built to rewrite blocking Camunda 7 methods to blocking Camunda 8 methods. This approach facilitates the migration, but does not offer the full benefits of the asynchronous nature of Camunda 8. Camunda recommends a reactive programming style to unlock the performance and latency benefits of Camunda 8. Please revisit your code base after applying the recipes and consider to refactor it to use reactive programming.

The CamundaClientWrapper is a simple Java class that offers blocking methods similar to the methods available in Camunda 7. Please add this class manually to your project. The fully qualified name of the class is necessary as a parameter for the recipes. 

# Recipe phases

The recipes are sorted into three phases: prepare, migrate, and cleanup.

## Prepare

In this phase, the Camunda 7 code remains mostly untouched, except the conversion of the TypedValueAPI to the JavaObjectAPI. Dependencies are added, and the engine services are all run through the ProcessEngine, like engine.getRuntimeService()... instead of runtimeService...

## Migrate

Now that all necessary dependencies are available, the Camunda 7 methods are replaced with the most suitable CamundaClientWrapper methods. Not all methods can be rewritten one-to-one. Comments are added to the rewritten code if adjustments of parameters took place, i.e., if a parameter was removed or added. Please revisit the code base and ensure the code behaves appropriately. For example, the concept of an execution with an executionId does not exist in Camunda 8. Many methods relying on the executionId have no one-to-one replacement in Camunda 8.

## Cleanup

This phase contains recipes to remove unnecessary dependencies, imports, etc.

# Running recipes

We describe the process for Maven-based projects here, but you can check the  [Quickstart Guide: Setting up your project and running recipes](https://docs.openrewrite.org/running-recipes/getting-started) for how to do the same with Gradle.

1. Checkout the Maven project you want to migrate. The project should be under version control to review refactorings easily later on.

2. Copy the CamundaClientWrapper in your code base

3. Create a [declarative rewrite.yml file](https://docs.openrewrite.org/running-recipes/popular-recipe-guides/authoring-declarative-yaml-recipes):
```yml
---
type: specs.openrewrite.org/v1beta/recipe
name: a.name.for.your.recipe
description: a recipe to specify what you want to do
recipeList:
  - org.camunda.migration.rewrite.recipes.client.AllClientPrepareRecipes:
      CLIENT_WRAPPER_PACKAGE: fully.qualified.name.of.the.CamundaClientWrapper
```

4. In the pom.xml add the necessary dependencies and specify the declarative recipe you just described

```xml
<project>
    <build>
        <plugins>
            <plugin>
                <groupId>org.openrewrite.maven</groupId>
                <artifactId>rewrite-maven-plugin</artifactId>
                <version>6.0.5</version>
                <configuration>
                    <activeRecipes>
                        <recipe>a.name.for.your.recipe</recipe>
                    </activeRecipes>
                    <skipMavenParsing>true</skipMavenParsing>
                </configuration>
                <dependencies>
                    <dependency>
                      <groupId>org.camunda.community</groupId>
                      <artifactId>camunda-7-to-8-rewrite-recipes</artifactId>
                      <version>0.0.1-alpha1</version>
                    </dependency>
                </dependencies>
            </plugin>
        </plugins>
    </build>
</project>
```

5. Run `mvn rewrite:run` to run your recipe.

6. Observe logs and examine any changes using your favorite diffing tool to check refactorings done by the recipes.

You might also want to check the [Quickstart Guide: Setting up your project and running recipes](https://docs.openrewrite.org/running-recipes/getting-started).
