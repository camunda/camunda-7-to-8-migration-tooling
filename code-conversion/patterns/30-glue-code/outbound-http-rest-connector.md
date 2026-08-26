# Outbound HTTP &#8594; REST Connector

In Camunda 7, outbound HTTP calls are made with the `camunda:connector` / **http-connector** extension on service tasks, or with hand-rolled HTTP client code inside a JavaDelegate or external task worker.

Camunda 8 provides a standard out-of-the-box [REST connector](https://docs.camunda.io/docs/components/connectors/protocol/rest/) for outbound HTTP. Prefer it wherever it covers the need instead of porting HTTP client code into a job worker.

| Camunda 7                                          | Camunda 8                                  |
| -------------------------------------------------- | ------------------------------------------ |
| `camunda:connector` with http-connector on a task  | Service task templated with the out-of-the-box REST connector (URL, method, headers, authentication, and result expression configured on the element) |
| HTTP client code inside a delegate/worker          | Same — keep a custom `@JobWorker` only for what the connector cannot express (complex multi-step logic, non-HTTP protocols) |

-   connector templates are applied in the Modeler or as element templates in the BPMN XML; no Java code is needed for the HTTP call itself
-   the connector's result expression maps the HTTP response into process variables — this replaces the delegate's `setVariable` calls
-   for more information, see [the REST connector docs](https://docs.camunda.io/docs/components/connectors/protocol/rest/)
