# OpenRewrite recipes fro migrating from Camunda 7 to Camunda 8 

This project contains [recipes](https://docs.openrewrite.org/concepts-and-explanations/recipes) for the open source tool [OpenRewrite](https://docs.openrewrite.org/) provided under Apache License.

Those recipes automate refactoring according to the [code conversion patterns](../patterns/).

# Recipe catalog

| Recipe/Pattern name  | Description | Class name |
| ------------- | ------------- | ------------- |
| [Java Delegate (Spring) &#8594; Job Worker (Spring)](../patterns/glue-code.md#java-delegate-spring--job-worker-spring)   | Change Java Delegates that are referenced as Spring beans to Spring-based Job Workers.  | org.camunda.migration.rewrite.recipes.glue.JavaDelegateSpringToZeebeWorkerSpring |

# Running recipes

We describe the process for Maven-based projects here, but you can check the  [Quickstart Guide: Setting up your project and running recipes](https://docs.openrewrite.org/running-recipes/getting-started) for how to do the same with Gradle.

1. Checkout the Maven project you want to migrate. The project should be under version control to review refactorings easily later on.

2. In the pom.xml of your project you wish to migrate, make your recipe module a plugin dependency of rewrite-maven-plugin:

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
                        <!-- Adjust list of recipes to what you want to apply: -->
                        <recipe>org.camunda.migration.rewrite.recipes.glue.JavaDelegateSpringToZeebeWorkerSpring</recipe>
                    </activeRecipes>
          					<skipMavenParsing>true</skipMavenParsing>
                </configuration>
                <dependencies>
                    <dependency>
                      <groupId>org.camunda.community</groupId>
                      <artifactId>camunda-7-to-8-rewrite-recipes</artifactId>
                      <version>0.0.1-SNAPSHOT</version>
                    </dependency>
                </dependencies>
            </plugin>
        </plugins>
    </build>
</project>
```

3. Run `mvn rewrite:run` to run your recipe.

4. Observe logs and examine any changes using your favorite diffing tool to check refactorings done by the recipes.

You might also want to check the [Quickstart Guide: Setting up your project and running recipes](https://docs.openrewrite.org/running-recipes/getting-started).