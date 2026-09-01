# Diagram Converter Webapp

The webapp accepts Camunda Forms (`.form` files) alongside BPMN and DMN models.
Forms are converted by updating their platform metadata and transforming exact
simple JUEL variable references such as `${customerName}` or `#{customerName}`
in component properties to FEEL (`= customerName`). Each transformation is
reported for review. Complex expressions remain unchanged and are reported as
tasks for manual migration. Schema versions and deprecated component properties are
preserved because changing them without a schema-aware migration could alter
form behavior. Forms can be opened as a read-only rendered preview from the
results list.

## Rest API

`POST /check`: Check required tasks for Camunda 7 to 8 migration for all provided models

- Request:
  - Format: `FormData`
  - Fields
    - `file` (`MultipartFile`): 1..n BPMN, DMN or form file(s) _(mandatory)_
    - `adapterJobType` (`String`): type of the job all service tasks formerly
      implemented as delegates or expressions should have. _(optional)_
    - `platformVersion` (`String`): version of the target platform _(optional)_
    - `adapterEnabled` (`Boolean`): whether the adapter job type should be set in the converted diagram _(default: `true`)_
  - Headers
    - `Accept`: Either `application/json`, `application/vnd.camunda.analysis+json`, `text/csv` or `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- Response:
  - `200`: Everything fine. The body contains a
    [check results](../core/src/main/java/io/camunda/migration/diagram/converter/BpmnDiagramCheckResult.java),
    either in `application/json`, `application/vnd.camunda.analysis+json` or
    flattened as `text/csv` or an XLSX file.

`POST /convert`: convert the provided BPMN, DMN or form model from Camunda 7 to Camunda 8

- Request:
  - Format: `FormData`
  - Fields
    - `file` (`MultipartFile`): BPMN, DMN or form file _(mandatory)_
    - `appendDocumentation` (`Boolean`): whether the check results should also
      be added to the documentation of each BPMN element _(default: `false`)_
    - `adapterJobType` (`String`): type of the job all service tasks formerly
      implemented as delegates or expressions should have. _(optional)_
    - `platformVersion` (`String`): version of the target platform _(optional)_
    - `adapterEnabled` (`Boolean`): whether the adapter job type should be set in the converted diagram _(default: `true`)_
- Response:
  - `200`: Everything fine. The body contains the converted model. The header
    contains a `Content-Disposition` field that declares this as attachment and
    holds a filename. The `Content-Type` is `application/bpmn+xml`,
    `application/dmn+xml` or `application/json` for forms.

`POST /convertBatch`: Convert all provided models from Camunda 7 to 8 and return a ZIP file

## Slack Notifications

The app can be configured to notify people in the background via Slack.

To use this feature, you need to configure the following:

```yaml
notification:
  slack:
    enabled: true
    token: <YOUR_BOT_TOKEN>
    channel-name: <NAME_OR_ID_OF_THE_CHANNEL>
```

In order to function, the slack app this notification service will be connected
to needs these scopes: `channels:read`, `chat:write`, `chat:write.public` and
`files:write`.

Also, the Bot needs to be added to the channel it should send the notifications
to. Otherwise, no stacktrace files will arrive.
