# OpenRewrite recipes refactoring code from Camunda 7 to Camunda 8 

> [!NOTE]  
> The recipes are still under development. Feedback of course welcome. Expect recipes to work out-of-the-box only in simple scenarios, oftentimes you might want to extend them to suite your needs.

This project contains [recipes](https://docs.openrewrite.org/concepts-and-explanations/recipes) for the open source tool [OpenRewrite](https://docs.openrewrite.org/) provided under Apache License.

Some transformation examples can be found in the [Camunda 7 to 8 code conversion patterns](../patterns/). 

This guide supports users in migrating Java-based process logic from Camunda 7 to Camunda 8, with a focus on transforming:

* Client code using the Camunda 7 Java API
* Java delegates
* External task workers
* Unit tests (WIP)

The code transformation is performed in three phases: prepare, migrate, and cleanup. Each phase is designed to make clear and easy-to-understand changes while keeping the code compilable:

* **Prepare**: The Camunda 7 code remains mostly untouched, but some preparations are done. For example, the conversion of the TypedValueAPI to the JavaObjectAPI, or the addition of Maven dependencies.
* **Migrate**: Now that all necessary dependencies are available, the Camunda 7 methods are replaced with the most suitable Camunda 8 Client methods. Not all methods can be rewritten one-to-one. Comments are added to the rewritten code if adjustments of parameters took place, i.e., if a parameter was removed or added. Please revisit the code base and ensure the code behaves appropriately. For example, the concept of an execution with an executionId does not exist in Camunda 8. Many methods relying on the executionId have no one-to-one replacement in Camunda 8.
* **Cleanup**: Remove unnecessary dependencies, imports, etc.

## Available Recipes

The available recipes are structured by the type of code they transform and the phase of the three-step transformation (prepend org.camunda.migration.rewrite.recipes.):

| Type of Change | Client Code      | Java Delegate | External Worker |
|----------------|------------------| ------------- | --------------- |
| Prepare        | AllClientPrepareRecipes | AllDelegatePrepareRecipes | AllExternalWorkerPrepareRecipes |
| Migrate        | AllClientMigrateRecipes | AllDelegateMigrateRecipes | AllExternalWorkerMigrateRecipes |
| Cleanup        | AllClientCleanupRecipes | AllDelegateCleanupRecipes | AllExternalWorkerCleanupRecipes |
| Combined       | AllClientRecipes | AllDelegateRecipes | AllExternalWorkerRecipes |

The prepare recipes have overlapping functionality which affects projects that use the TypedValueAPI.

To fully transform the code, apply the prepare, migrate, and cleanup recipes for the type of code you wish to transform, or directly apply the combined recipes. Carefully examine the changes made by the recipes. It is expected that the recipes cannot be easily applied in all scenarios. To adjust and extend the recipes to your needs, please refer to the developer guide.

To directly apply the combined recipes, you can run:

* **org.camunda.migration.rewrite.recipes.AllClientRecipes**: Refactors [client code](/patterns/20-client-code).
* **org.camunda.migration.rewrite.recipes.AllDelegateRecipes**: Refactors [Java Delegates (glue code)](/patterns/30-glue-code/10-java-spring-delegate)
* **org.camunda.migration.rewrite.recipes.AllExternalWorkerRecipes**: Refactors [External Workers (glue code)](/patterns/30-glue-code/20-java-spring-external-task-worker)

## Recipe Completeness

The recipes cover the class-structure, dependencies, and basic types and methods of the Camunda 7 code. There are incomplete in two aspects:

* some methods could be transformed, but are currently not included in the recipes
* some methods cannot be transformed as they are not available in Camunda 8

If Camunda 7 code remains after applying the recipes, either extend the available recipes or refactor the code to remove the affected code. You can rerun the cleanup recipes afterwards.

Additionally, the transformed code sometimes requires action, e.g., to define a messageCorrelationKey or a new error message. It can happen that information is lost in the transformation, e.g., local variables being dropped. Pay extra attention when comments have been added, or you see dummy literal strings in the transformed code.

## Running recipes

We describe the process for Maven-based projects here, but you can check the  [Quickstart Guide: Setting up your project and running recipes](https://docs.openrewrite.org/running-recipes/getting-started) for how to do the same with Gradle.

1. Checkout the Maven project you want to migrate. The project should be under version control to review refactorings easily later on.

2. In the pom.xml add the necessary dependencies and specify the recipes you want to run. 

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
                        <recipe>org.camunda.migration.rewrite.recipes.AllClientRecipes</recipe>
                        <recipe>org.camunda.migration.rewrite.recipes.AllDelegateRecipes</recipe>
                    </activeRecipes>
                    <skipMavenParsing>false</skipMavenParsing>
                </configuration>
                <dependencies>
                    <dependency>
                      <groupId>org.camunda.community</groupId>
                      <artifactId>camunda-7-to-8-rewrite-recipes</artifactId>
                      <version>0.0.1-alpha2</version>
                    </dependency>
                </dependencies>
            </plugin>
        </plugins>
    </build>
</project>
```

3. Run the recipes:

```shell
mvn rewrite:run
```

4. Observe logs and examine any changes using your favorite diffing tool to check refactorings done by the recipes.

See also thge [Camunda 7 to 8 Migration Example](https://github.com/camunda-community-hub/camunda-7-to-8-migration-example) to see a complete example being refactored.

You might also want to check the [Quickstart Guide: Setting up your project and running recipes](https://docs.openrewrite.org/running-recipes/getting-started).

## Extending recipes

For many scenarios you might want to extend the recipes. For example, your Java Delegates might not implement ` org.camunda.bpm.engine.delegate.JavaDelegate` but extend your own superclass `org.acme.MyJavaDelegate`. This would not be picked up by the out-of-the-box recipes. 

Please read:
- [Developer Guide](developer_guide.md)