# Conversion CLI Tool

The command line interface tool can convert a Camunda Platform 7 diagram or all
diagrams inside a folder (plus sub directories) or convert all diagrams from a
Camunda Platform 7 process engine.

All diagrams that should be converted need to have the `.bpmn`, `.bpmn20.xml`,
`.dmn`, or `.dmn11.xml` file ending.

To convert the latest BPMN and DMN definitions from a running Camunda 7 engine, use the `engine` subcommand with the engine REST URL. Converted diagrams are written directly into the target directory (resource subdirectories are flattened); the CLI creates the target directory if it does not already exist.

```shell
java -Dfile.encoding=UTF-8 -jar camunda-7-to-8-diagram-converter-cli-{version}.jar engine http://localhost:8080/engine-rest --target-directory .camunda-migration/c7-models --platform-version 8.9
```

The engine mode supports optional Basic authentication with `--username` and `--password`, and writes converted files plus optional analysis reports to the target directory. It does not provide direct database or OIDC acquisition.

Use `--documentation` to append all findings to BPMN element documentation, or
`--only-task-and-warning` to append only TASK and WARNING findings.

> **Security note:** passing `--password` on the command line can expose the secret in shell history and OS process listings. Use a trusted environment and consider temporary or dedicated credentials.

## Developer Notes

The tool logs all results (either location of the new file or the exception the
migration tool faced during migration). Diagrams that are already in C8 format
will be omitted but logged. So you can follow the progress of converting.

In case you run the CLI tool on your Windows computer and your process diagram
contains Umlaute, add the java option `-Dfile.encoding=UTF-8` between the java
and -jar parameters:

```
java -Dfile.encoding=UTF-8 -jar camunda-7-to-8-diagram-converter-cli-{version}.jar
```

## Commands

### Convert diagrams from the local file systems

```
Usage: camunda-7-to-8-diagram-converter-cli local [-dhoV] [--check] [--csv]
       [--disable-default-job-type] [-nr] [--default-job-type=<defaultJobType>]
       [--platform-version=<platformVersion>] [--prefix=<prefix>] <file>
Converts the diagram from the given directory or file

Execute as:

java -Dfile.encoding=UTF-8 -jar camunda-7-to-8-diagram-converter-cli-{version}.jar local

Parameter:
      <file>                 The file to convert or directory to search in
Options:
      --check                If enabled, no converted diagrams are exported
      --csv                  If enabled, a CSV file will be created containing
                               the results for all conversions
  -d, --documentation        If enabled, messages are also appended to
                               documentation
      --default-job-type=<defaultJobType>
                             If set, the default value from the
                               'converter-properties.properties' for the job
                               type is overridden
      --disable-default-job-type
                             Disables the default job type
  -h, --help                 Show this help message and exit.
      -nr, --not-recursive   If enabled, recursive search in subfolders will be
                               omitted
  -o, --override             If enabled, existing files are overridden
      --platform-version=<platformVersion>
                             Semantic version of the target platform, defaults
                               to latest version
      --prefix=<prefix>      Prefix for the name of the generated file
                               Default: converted-c8-
  -V, --version              Print version information and exit.
```

**Example:**

```
java -Dfile.encoding=UTF-8 -jar camunda-7-to-8-diagram-converter-cli-{version}.jar local c:\myDirectory
```

### Convert diagrams from a running process engine

```
Usage: camunda-7-to-8-diagram-converter-cli engine [-dhoV] [--check] [--csv]
       [--disable-default-job-type] [--default-job-type=<defaultJobType>]
       [-p=<password>] [--platform-version=<platformVersion>]
       [--prefix=<prefix>] [-t=<targetDirectory>] [-u=<username>] <url>
Description: Converts the diagrams from the given process engine

Execute as:

java -Dfile.encoding=UTF-8 -jar camunda-7-to-8-diagram-converter-cli-{version}.jar engine

Parameter:
      <url>               Fully qualified http(s) address to the process engine
                            REST API
                            Default: http://localhost:8080/engine-rest
Options:
      --check             If enabled, no converted diagrams are exported
      --csv               If enabled, a CSV file will be created containing the
                            results for all conversions
  -d, --documentation     If enabled, messages are also appended to
                            documentation
      --default-job-type=<defaultJobType>
                          If set, the default value from the
                            'converter-properties.properties' for the job type
                            is overridden
      --disable-default-job-type
                          Disables the default job type
  -h, --help              Show this help message and exit.
  -o, --override          If enabled, existing files are overridden
  -p, --password=<password>
                          Password for basic auth
      --platform-version=<platformVersion>
                          Semantic version of the target platform, defaults to
                            latest version
      --prefix=<prefix>   Prefix for the name of the generated file
                            Default: converted-c8-
  -t, --target-directory=<targetDirectory>
                          The directory to save the .bpmn files
                            Default: .
  -u, --username=<username>
                          Username for basic auth
  -V, --version           Print version information and exit.
```

**Example:**

```
java -Dfile.encoding=UTF-8 -jar camunda-7-to-8-diagram-converter-cli-{version}.jar engine http://localhost:8080/engine-rest
```

### Supported File Extensions

Diagrams must have the `.bpmn`, `.bpmn20.xml`, `.dmn`, or `.dmn11.xml` file ending to be processed.

Camunda 7 form files (`.form`) are converted as well. The converter updates the platform metadata (`executionPlatform` becomes `Camunda Cloud`, `executionPlatformVersion` becomes the target platform version) and transforms exact simple JUEL variable references such as `${customerName}` or `#{customerName}` in component properties to FEEL (`= customerName`). Each transformation is reported for review. Complex expressions, interpolation, method calls, and Camunda 7 execution context references remain unchanged and are reported as tasks for manual migration. The schema version and deprecated component properties are preserved because changing them without a schema-aware migration could alter form behavior; the JSON may be re-formatted. Form files are analyzed but not exported in `--check` mode.
