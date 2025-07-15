# OpenRewrite recipes refactoring code from Camunda 7 to Camunda 8 

> [!NOTE]  
> The recipes are still under development. Feedback of course welcome. Expect recipes to work out-of-the-box only in simple scenarios, oftentimes you might want to extend them to suite your needs.

This project contains [recipes](https://docs.openrewrite.org/concepts-and-explanations/recipes) for the open source tool [OpenRewrite](https://docs.openrewrite.org/) provided under Apache License.

Those recipes automate refactoring according to the [Camunda 7 to 8 code conversion patterns](../patterns/). The recipes are sorted into three phases, that run sequentually:

* **Prepare**: The Camunda 7 code remains mostly untouched, but some preparations are done. For example, the conversion of the TypedValueAPI to the JavaObjectAPI, or the addition of Maven dependencies.
* **Migrate**: Now that all necessary dependencies are available, the Camunda 7 methods are replaced with the most suitable Camunda 8 Client methods. Not all methods can be rewritten one-to-one. Comments are added to the rewritten code if adjustments of parameters took place, i.e., if a parameter was removed or added. Please revisit the code base and ensure the code behaves appropriately. For example, the concept of an execution with an executionId does not exist in Camunda 8. Many methods relying on the executionId have no one-to-one replacement in Camunda 8.
* **Cleanup**: Remove unnecessary dependencies, imports, etc.

You can find a list of all recipes in the various yml files in [/recipes/src/main/resources/META-INF/rewrite](/recipes/src/main/resources/META-INF/rewrite). 

Typically you just use the following recipes:

* **org.camunda.migration.rewrite.recipes.AllClientRecipes**: Refactors [client code](/patterns/20-client-code).
* **org.camunda.migration.rewrite.recipes.AllDelegateRecipes**: Refactors [Java Delegates (glue code)](/patterns/30-glue-code)

# Running recipes

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

# Extending recipes

For many scenarios you might want to extend the recipes.

For example, your Java Delegates might not implement ` org.camunda.bpm.engine.delegate.JavaDelegate` but extend your own superclass `org.acme.MyJavaDelegate`. This would not be picked up by the out-of-the-box recipes. 

However, you can could extend [InjectJobWorkerRecipe.java](/recipes/src/main/java/org/camunda/migration/rewrite/recipes/delegate/prepare/InjectJobWorkerRecipe.java#L34) where the preconditions only include classes implementing the original JavaDelegate:

```java
public TreeVisitor<?, ExecutionContext> getVisitor() {

// define preconditions
TreeVisitor<?, ExecutionContext> check =
    Preconditions.and(
        Preconditions.not(new UsesType<>("io.camunda.client.api.response.ActivatedJob", true)),
        new UsesType<>("org.camunda.bpm.engine.delegate.DelegateExecution", true));
```

You could now adjust this to

```java
public TreeVisitor<?, ExecutionContext> getVisitor() {

// define preconditions
TreeVisitor<?, ExecutionContext> check =
    Preconditions.and(
        Preconditions.not(new UsesType<>(RecipeConstants.Type.ACTIVATED_JOB, true)),
        Preconditions.or(
            new UsesType<>("org.camunda.bpm.engine.delegate.DelegateExecution", true),
            new UsesType<>("org.acme.MyJavaDelegate", true),
        )); 
```

Now the recipe would also pick up those delegates and add the Camunda 8 Job Worker.

You might need to do some more changes, as your `execute` method might have also be renamed or carry different parameters. We recommend not trying to perfectly extend our recipe code - but to check it out and change it on your own fork/branch. Remember that such refactoring code is only running once for the migration and can be dumped afterwards.