# Camunda 7 to 8 Diagram Converter

A tool for analyzing and converting Camunda 7 models (BPMN & DMN) to Camunda 8 format. The Diagram Converter:

- Analyzes Camunda 7 models to identify tasks required for migration to Camunda 8.
- Converts those models to Camunda 8 format.
- Can be extended to accommodate special requirements.

## Online Availability

The Diagram Converter is available online at [https://diagram-converter.camunda.io/](https://diagram-converter.camunda.io/), hosted by Camunda. Your diagrams are transiently processed - we don't store any of your data.

## Documentation

For installation and usage instructions, see the [official documentation](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter/).

See it in action in the **[Camunda 7 to 8 Migration Example](https://github.com/camunda-community-hub/camunda-7-to-8-migration-example)**.

## Understanding Conversions

To understand what conversions will be applied, have a look at the [BPMN Conversion Test Cases YAML](core/src/test/resources/BPMN_CONVERSION.yaml) - as this lists given Camunda 7 BPMN snippets, and the expected Camunda 8 snippet of the transformed BPMN. For example:

```yaml
- name: Service Task with delegateExpression
  givenBpmn: |
    <bpmn:serviceTask id="serviceTask" name="Service Task" camunda:delegateExpression="${myDelegate}" />
  expectedBpmn: |
    <bpmn:serviceTask completionQuantity="1" id="serviceTask" implementation="##WebService" isForCompensation="false" name="Service Task" startQuantity="1">
      <extensionElements>
         <zeebe:taskHeaders>
         <zeebe:header key="delegateExpression" value="${myDelegate}"/>
         </zeebe:taskHeaders>
         <zeebe:taskDefinition type="myDelegate"/>
      </extensionElements>
    </bpmn:serviceTask>
  expectedMessages: |
    <conversion:message severity="REVIEW">Delegate class or expression '${myDelegate}' has been transformed to job type 'myDelegate'.</conversion:message>
```

## Development

### Building from Source

**Requirements**:
- Java 21 or higher
- Maven 3.6+

**Steps**:

1. Clone the repository:

   ```shell
   git clone https://github.com/camunda/camunda-7-to-8-migration-tooling.git
   cd camunda-7-to-8-migration-tooling/diagram-converter
   ```
2. Build the project:

   ```shell
   mvn clean install
   ```

### Embedding into your own Java Applications

You can embed the core diagram conversion as a library into your own Java application. See the [core module documentation](core/README.md) for details.

### How to Extend Diagram Conversion

You can extend diagram conversion by leveraging the SPI. See the [extension example project](extension-example/) for a reference implementation.

## Contributing

See the [contribution guidelines](../README.md#contributing).

## License

The source files in this repository are made available under the [Camunda License Version 1.0](../CAMUNDA-LICENSE-1.0.txt).

