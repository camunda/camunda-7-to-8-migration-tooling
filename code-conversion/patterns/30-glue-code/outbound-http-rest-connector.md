# Outbound HTTP &#8594; REST Connector

C7 outbound HTTP uses the `camunda:connector` / **http-connector** extension or hand-rolled HTTP client code inside a delegate/worker. Camunda 8 provides a standard out-of-the-box [REST connector](https://docs.camunda.io/docs/components/connectors/protocol/rest/) — prefer it wherever it covers the need instead of porting HTTP client code into a job worker.

| Camunda 7                                          | Camunda 8                                  |
| -------------------------------------------------- | ------------------------------------------ |
| `camunda:connector` with http-connector, HTTP client code in a delegate/worker | Service task with the out-of-the-box REST connector (URL, method, headers, authentication, result expression configured on the element; no Java code) |

-   the connector's result expression maps the HTTP response into process variables — replacing the delegate's `setVariable` calls
-   keep a custom `@JobWorker` only for what the connector cannot express (complex multi-step logic, non-HTTP protocols)
