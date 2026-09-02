# File Variables &#8594; Document API

In Camunda 7, files are stored as `FileValue` process variables (Typed Value API) carrying content plus filename and MIME type. Camunda 8 has no typed file variable: files become **documents** managed by the [Document API](https://docs.camunda.io/docs/components/document-handling/getting-started/) (`newCreateDocumentCommand`); the process variable holds only the resulting **document reference**, never a bare filename string.

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

-   store the plain `DocumentReference` in the process variable; forms consuming it (e.g. a `documentPreview` component) expect a FEEL expression over an *array* — do the one-element wrap (`[contract]`) in the form's FEEL, not in the variable
-   documents have store-specific time-to-live and size limits — check the document handling docs for your storage backend
