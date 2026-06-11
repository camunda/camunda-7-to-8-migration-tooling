# Business Key &#8594; Business ID / Tags

In Camunda 7, a process instance can carry a **business key**: a domain identifier (order number, claim ID) that is set on start and used to find instances later.

Camunda 8 did not support business keys for a long time. Since **Camunda 8.9**, the direct successor is the **Business ID**. Since **Camunda 8.8**, **process instance tags** are available as a lightweight alternative.

| Camunda 7              | Camunda 8                                                                 |
| ---------------------- | ------------------------------------------------------------------------- |
| `businessKey`          | `businessId` (8.9+) — immutable, propagated to call-activity children, searchable, optional cluster-level uniqueness enforcement |
| (no equivalent)        | `tags` (8.8+) — up to 10 immutable labels per instance, included in search responses and activated jobs |

If your target version is **8.8**, use tags (for example, `order:1234`) or store the identifier as a regular process variable and filter by variable in searches.

## Setting the Business Key

### ProcessEngine (Camunda 7)

```java
    public ProcessInstance startProcessWithBusinessKey(String processDefinitionKey, String businessKey, VariableMap variableMap) {
        return engine.getRuntimeService().startProcessInstanceByKey(processDefinitionKey, businessKey, variableMap);
    }
```

### CamundaClient (Camunda 8.9+)

```java
    public ProcessInstanceEvent startProcessWithBusinessId(String processDefinitionId, String businessId, Map<String, Object> variableMap) {
        return camundaClient.newCreateInstanceCommand()
                .bpmnProcessId(processDefinitionId)
                .latestVersion()
                .businessId(businessId)
                .variables(variableMap)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
```

-   the Business ID is immutable — it cannot be changed or removed after creation (Camunda 7 allowed updating the business key, Camunda 8 does not)
-   the Business ID is automatically propagated to child instances created via call activities
-   uniqueness can be enforced at the cluster level: only one *running* root instance per process definition may carry the same Business ID, which enables idempotent process starts
-   maximum length is 256 characters
-   for more information, see [the docs on process instance creation](https://docs.camunda.io/docs/components/concepts/process-instance-creation/#business-id)

## Searching by Business Key

### ProcessEngine (Camunda 7)

```java
    public List<ProcessInstance> findByBusinessKey(String businessKey) {
        return engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .list();
    }
```

### CamundaClient (Camunda 8.9+)

```java
    public List<ProcessInstance> findByBusinessId(String businessId) {
        return camundaClient.newProcessInstanceSearchRequest()
                .filter(filter -> filter.businessId(businessId))
                .send()
                .join()
                .items();
    }
```

## Alternative: Tags (Camunda 8.8+)

If the identifier is only needed for routing, correlation, or filtering — not for uniqueness — tags are sufficient and available from 8.8:

```java
    public ProcessInstanceEvent startProcessWithTag(String processDefinitionId, String orderId, Map<String, Object> variableMap) {
        return camundaClient.newCreateInstanceCommand()
                .bpmnProcessId(processDefinitionId)
                .latestVersion()
                .tags("order:" + orderId)
                .variables(variableMap)
                .send()
                .join();
    }
```

-   tags are immutable after creation, maximum of 10 unique tags per process instance
-   tags are inherited by all jobs created from that instance, so job workers can read them without inspecting variables
-   tags are API/client-only metadata — they are not shown in Operate or Tasklist
-   do not store secrets or PII in tags, they propagate with jobs and exports
-   for more information, see [the docs on tags](https://docs.camunda.io/docs/components/concepts/process-instance-creation/#tags)
