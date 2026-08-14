# Diagram Converter CLI

The command-line interface for the Camunda 7 to 8 Diagram Converter. It can convert diagrams from the local file system or directly from a running Camunda 7 process engine.

For usage documentation, see the [official documentation](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter/).

To convert the latest BPMN and DMN definitions from a running Camunda 7 engine, create the target directory with the platform-native file tools first. The CLI does not create a missing target directory, so output creation fails if it does not already exist. Then use the `engine` subcommand with the engine REST URL:

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
