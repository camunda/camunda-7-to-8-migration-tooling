# Search Process Definitions

The following patterns focus on methods how to search for process definitions in Camunda 7 and how they convert to Camunda 8.

## ProcessEngine (Camunda 7)

```java
    public List<ProcessDefinition> searchProcessDefinitions(String name) {
        return engine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionName(name)
                .orderByTenantId()
                .list();
    }
```

-   various filter options, for more information, see the [API Mapping WebApp](https://camunda.github.io/camunda-7-to-8-migration-tooling/)

## CamundaClient (Camunda 8)

```java
    public List<ProcessDefinition> searchProcessDefinitions(String name) {
        return camundaClient.newProcessDefinitionSearchRequest()
                .filter(processDefinitionFilter -> processDefinitionFilter.name(name))
                .sort(processDefinitionSort -> processDefinitionSort.tenantId())
                .send()
                .join()
                .items();
    }
```
