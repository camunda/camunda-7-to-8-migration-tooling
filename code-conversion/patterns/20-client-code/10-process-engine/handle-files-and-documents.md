# File Variables &#8594; Document API

In Camunda 7, files are stored as process variables of type `FileValue` (via the Typed Value API), carrying the file content plus filename and MIME type metadata.

Camunda 8 has no typed file variable. Files become **documents** managed by the [Document API](https://docs.camunda.io/docs/components/document-handling/getting-started/) (`newCreateDocumentCommand`); the process variable holds only the resulting **document reference**, never a bare filename string.

| Camunda 7                          | Camunda 8                                                        |
| ---------------------------------- | ---------------------------------------------------------------- |
| `FileValue` variable (`Values.fileValue(...)`) | Document uploaded via `camundaClient.newCreateDocumentCommand()`; the `DocumentReference` is stored as the process variable |

## Creating a File Variable

### ProcessEngine (Camunda 7)

```java
    public void storeFile(DelegateExecution execution) {
        FileValue contract = Variables.fileValue("contract.pdf")
                .file(fileBytes)
                .mimeType("application/pdf")
                .create();
        execution.setVariable("contract", contract);
    }
```

### CamundaClient (Camunda 8)

```java
    public DocumentReference storeDocument(byte[] fileBytes) {
        return camundaClient.newCreateDocumentCommand()
                .content(fileBytes)
                .fileName("contract.pdf")
                .contentType("application/pdf")
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
```

-   store the plain `DocumentReference` in the process variable — do not wrap it in an array in the variable itself
-   forms consuming the reference (for example a `documentPreview` component) expect a FEEL expression over an *array* of references; do the one-element wrap (`[contract]`) in the form's FEEL expression, not in the variable
-   documents have a store-specific time-to-live and size limits — check the [document handling docs](https://docs.camunda.io/docs/components/document-handling/getting-started/) for your storage backend
