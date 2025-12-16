# Camunda 7 to 8 Model Converter

A tool for analyzing and converting Camunda 7 models (BPMN & DMN) to Camunda 8 format. The Model Converter:

- Analyzes Camunda 7 models to identify tasks required for migration to Camunda 8.
- Converts those models to Camunda 8 format.
- Can be extended to accommodate special requirements.

See it in action in the **[Camunda 7 to 8 Migration Example](https://github.com/camunda-community-hub/camunda-7-to-8-migration-example)**.

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

## Table of Contents

- [Installation](#installation)
  - [SaaS](#saas)
  - [Local Java Installation](#local-java-installation)
  - [CLI](#cli-command-line-interface)
  - [Building from Source](#building-from-source)
  - [Embedding into your own Java Applications](#embedding-into-your-own-java-applications)
- [How to Use](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/#migration-analyzer) (Refers to the [Camunda Migration Guide](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/))
- [How to Extend](#how-to-extend-diagram-conversion)
- [License](#license)

## Installation

You can use the Model Converter in the following ways:

- **Web Interface**: A locally installed web-based wizard for the Model Converter, implemented as a Java Spring application. This can be installed locally as Java jar, or consumed as SaaS from a hosted version.
- **CLI**: A Command-Line Interface for the Model Converter, implemented as a Java application.

### SaaS

A free hosted version of the Model Converter is available at [https://migration-analyzer.consulting-sandbox.camunda.cloud/](https://migration-analyzer.consulting-sandbox.camunda.cloud/).

Note that your models are **not** stored on this platform, and all processing happens in-memory. Your data is transmitted securely over HTTPS. However, we don't give any guarantees on this free SaaS version.

### Local Java Installation

You can also install the web application locally.

**Requirements**:
- Java 21 or higher

**Steps**:

1. Download the latest Model Converter web application: https://github.com/camunda/camunda-7-to-8-migration-tooling/releases.
2. Run the application. Navigate to the directory where the JAR file was downloaded and execute the following command (replace `{version}` with the actual version number, e.g., `0.2.0`):

   ```shell
   java -jar camunda-7-to-8-diagram-converter-webapp-{version}.jar
   ```
3. Access the web application at [http://localhost:8080/](http://localhost:8080/).

To run the application on a different port, use the following command (replace `{version}` with the actual version number, e.g., `0.2.0`):

```shell
java -Dserver.port=8090 -jar camunda-7-to-8-diagram-converter-webapp-{version}.jar
```

### CLI (Command Line Interface)

The CLI is a standalone Java application.

**Steps**:

1. Download the latest Model Converter CLI application: https://github.com/camunda/camunda-7-to-8-migration-tooling/releases.
2. Run the CLI with the following command (replace `{version}` with the actual version number, e.g., `0.2.0`):

   ```shell
   java -jar camunda-7-to-8-diagram-converter-cli-{version}.jar --help
   ```

The typical way is to run it in `local` mode and reference your BPMN model file:

```shell
java -jar camunda-7-to-8-diagram-converter-cli-{version}.jar local myBpmnModel.bpmn
```

### Building from Source

You can build this project from source using Maven.

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

You can also embed the core diagram conversion as library into your own Java application. This is [explained here](core/README.md).

## How to Extend Diagram Conversion?

You can extend diagram conversion by leveraging the SPI. You can find an example in the [extension example project](extension-example/).

## License

The source files in this repository are made available under the [Camunda License Version 1.0](../CAMUNDA-LICENSE-1.0.txt).

