# Advanced Camunda 8 gRPC examples

These examples show the two levels of gRPC access available to a Java process solution:

| Approach | Choose it when | You are responsible for |
| --- | --- | --- |
| [Streaming job worker](src/main/java/io/camunda/conversion/advanced/grpc/StreamingJobWorkerExample.java) | Implementing normal Camunda 8 job workers | The handler, job outcomes, worker sizing, and application lifecycle |
| [Generated gateway stub](src/main/java/io/camunda/conversion/advanced/grpc/GeneratedGatewayStubExample.java) | A required gateway RPC is not exposed by the supported client API | Channel and TLS setup, authentication metadata and token refresh, deadlines, retries, request construction, error handling, and shutdown |

Prefer the high-level `CamundaClient` API. It is the supported application-facing API and manages job streaming, backpressure, command creation, and reconnect behavior. Generated gateway classes are a lower-level escape hatch. They follow the gateway protocol rather than the Java client's public API and can change with the Camunda version.

## Streaming job worker

`StreamingJobWorkerExample` opens a streaming worker for jobs of type `process-payment`, waits until the JVM receives a shutdown signal, and closes both the worker and client. Set the job variable `outcome` to exercise each result:

- `complete` (or omit it) completes the job
- `failure` reports a technical failure with reduced retries and a retry backoff
- `bpmn-error` throws the BPMN error `PAYMENT_REJECTED`

Configure a local cluster without authentication:

```bash
export CAMUNDA_GRPC_ADDRESS=http://localhost:26500
```

For an OAuth-secured cluster, also set:

```bash
export CAMUNDA_CLIENT_ID=...
export CAMUNDA_CLIENT_SECRET=...
export CAMUNDA_TOKEN_AUDIENCE=...
export CAMUNDA_TOKEN_URL=https://.../oauth/token
```

Run the example from this directory:

```bash
mvn compile exec:java \
  -Dexec.mainClass=io.camunda.conversion.advanced.grpc.StreamingJobWorkerExample
```

Stop it with `Ctrl+C`; the shutdown hook lets the try-with-resources block close the worker before closing the client.

## Generated gateway stub

`GeneratedGatewayStubExample` calls the representative `Topology` RPC. It configures a plaintext or TLS channel from the address scheme, optionally attaches bearer-token metadata, applies a deadline, prints the returned brokers, and always shuts down the channel.

```bash
export CAMUNDA_GRPC_ADDRESS=https://example.camunda.io:443
export CAMUNDA_GRPC_TOKEN=...

mvn compile exec:java \
  -Dexec.mainClass=io.camunda.conversion.advanced.grpc.GeneratedGatewayStubExample
```

Omit `CAMUNDA_GRPC_TOKEN` only when the target cluster has authentication disabled. A static token keeps this example focused on direct stub usage; a real application must acquire and refresh tokens securely, classify gRPC status errors, and apply an appropriate retry policy.
