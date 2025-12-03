# Process Solution Camunda 7 Example

This module contains a small Camunda 7 process solution that is used as **input** for the OpenRewrite-based code conversion recipes from the `code-conversion/recipes` module.

## How to Run the Migration

1. Build the recipes module:

```bash
cd code-conversion
mvn -pl recipes install -DskipTests
```

2. Build this example module:

```bash
cd code-conversion/examples/process-solution-camunda-7
mvn compile
```

3. Run the code conversion recipes on this module:

```bash
cd code-conversion/examples/process-solution-camunda-7
mvn rewrite:run
```

> The `rewrite:run` goal uses the recipes configured in this module's `pom.xml`. You can override them with `-DactiveRecipes=...` when needed.

## What to Expect in the Console Output

When you run `mvn rewrite:run`, Maven / OpenRewrite will:

- Print which recipes are active and how many source files are being scanned.
- Show a short summary if changes are made (for example, that files were updated or that a recipe produced results).
- If no matching patterns are found for the active recipes, you may see a message indicating that there are no results / no changes to apply.

After the run completes, inspect the changes using your IDE or `git diff` to see how the Camunda 7 example code was transformed.
