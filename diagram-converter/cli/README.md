# Diagram Converter CLI

The command-line interface for the Camunda 7 to 8 Diagram Converter. It can convert diagrams from the local file system or directly from a running Camunda 7 process engine.

For usage documentation, see the [official documentation](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter/).

To convert the latest BPMN and DMN definitions from a running Camunda 7 engine, use the `engine` subcommand with the engine REST URL. Converted diagrams are written directly into the target directory (resource subdirectories are flattened); the CLI creates the target directory if it does not already exist.

```shell
java -Dfile.encoding=UTF-8 -jar camunda-7-to-8-diagram-converter-cli-{version}.jar engine http://localhost:8080/engine-rest --target-directory .camunda-migration/c7-models --platform-version 8.9
```

The engine mode supports optional Basic authentication with `--username` and `--password`, and writes converted files plus optional analysis reports to the target directory. It does not provide direct database or OIDC acquisition.

> **Security note:** passing `--password` on the command line can expose the secret in shell history and OS process listings. Use a trusted environment and consider temporary or dedicated credentials.

## Developer Notes

### File Encoding on Windows

When running the CLI on Windows with diagrams containing special characters (e.g., Umlaute), add the Java option `-Dfile.encoding=UTF-8`:

```shell
java -Dfile.encoding=UTF-8 -jar camunda-7-to-8-diagram-converter-cli-{version}.jar local myDiagram.bpmn
```

### Supported File Extensions

Diagrams must have the `.bpmn`, `.bpmn20.xml`, `.dmn`, or `.dmn11.xml` file ending to be processed.

Camunda 7 form files (`.form`) are converted as well. The converter updates the platform metadata (`executionPlatform` becomes `Camunda Cloud`, `executionPlatformVersion` becomes the target platform version) and transforms exact simple JUEL variable references such as `${customerName}` or `#{customerName}` in component properties to FEEL (`= customerName`). Each transformation is reported for review. Complex expressions, interpolation, method calls, and Camunda 7 execution context references remain unchanged and are reported for manual migration. The schema version and deprecated component properties are preserved because changing them without a schema-aware migration could alter form behavior; the JSON may be re-formatted. Form files are analyzed but not exported in `--check` mode.
