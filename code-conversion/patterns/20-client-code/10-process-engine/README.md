# `ProcessEngine`

The ProcessEngine offers various services (think RuntimeService) to interact with the Camunda 7 engine.

The specific services are:

- RepositoryService: Manages Deployments
- RuntimeService: For starting and searching ProcessInstances
- TaskService: Exposes operations to manage human (standalone) Tasks, such as claiming, completing and assigning tasks
- IdentityService: Used for managing Users, Groups and the relations between them
- ManagementService: Exposes engine admin and maintenance operations, which have no relation to the runtime execution of business processes
- HistoryService: Exposes information about ongoing and past process instances
- FormService: Access to form data and rendered forms for starting new process instances and completing tasks

The different methods of these services are grouped into separated .md files by action, such as starting a process instance, with multiple examples covering different methods of performing the same action.


## OpenRewrite recipe (WIP)

-   [Recipe "ProcessEngineToZeebeClient"](../recipes/src/main/java/org/camunda/migration/rewrite/recipes/client/ProcessEngineToZeebeClient.java)
-   [Learn how to apply recipes](../recipes/)
