# Diagram Converter CLI

The command-line interface for the Camunda 7 to 8 Diagram Converter. It can convert diagrams from the local file system or directly from a running Camunda 7 process engine.

For usage documentation, see the [official documentation](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/diagram-converter/).

## Developer Notes

### File Encoding on Windows

When running the CLI on Windows with diagrams containing special characters (e.g., Umlaute), add the Java option `-Dfile.encoding=UTF-8`:

```shell
java -Dfile.encoding=UTF-8 -jar camunda-7-to-8-diagram-converter-cli-{version}.jar local myDiagram.bpmn
```

### Supported File Extensions

Diagrams must have the `.bpmn` or `.bpmn20.xml` file ending to be processed.

