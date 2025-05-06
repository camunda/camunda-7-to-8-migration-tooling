# Camunda 7 to Camunda 8 Code Conversion Pattern Catalog

> [!NOTE]  
> The pattern catalog was just kicked off and will be filled with more patterns throughout Q2 of 2025. The current patterns are more exemplary to discuss the structure. Feedback of course welcome.

## Introduction

This catalog contains code conversion patterns for **client code** and **glue code** for **Java (Spring)**-based code:

-   **Client code**: Whenever your solutions calls the Camunda API, e.g., to start new process instances.
-   **Glue code**: Whenever you define code that is executed when a process arrives at a specific state in the process, e.g., via JavaDelegates.

These patterns are language-specific. For language-agnostic information about the Camunda 7 and Camunda 8 API endpoints, see the **[Camunda 7 API to Camunda 8 API Mapping WebApp (WIP)](https://camunda-community-hub.github.io/camunda-7-to-8-code-conversion/)**.

## Client Code

In **Camunda 7**, the communication with the Camunda engine is structured into several **services**:

-   RepositoryService: Manages Deployments
-   RuntimeService: For starting and searching ProcessInstances
-   TaskService: Exposes operations to manage human (standalone) Tasks, such as claiming, completing and assigning tasks
-   IdentityService: Used for managing Users, Groups and the relations between them
-   ManagementService: Exposes engine admin and maintenance operations, which have no relation to the runtime execution of business processes
-   HistoryService: Exposes information about ongoing and past process instances.
-   FormService: Access to form data and rendered forms for starting new process instances and completing tasks.

This project focuses on the **RepositoryService**, **RuntimeService** and **TaskService**.

With the release of **Camunda 8.8**, the Camunda API is harmonized to make communication with Camunda 8 clusters simpler. Thus, a new client SDK is released as well: **Camunda Spring SDK**, see the [latest release in maven central](https://mvnrepository.com/artifact/io.camunda/spring-boot-starter-camunda-sdk).

## Glue Code

In **Camunda 7**, code executed by a service task or listener can be organized in various ways. A common method is using a **JavaDelegate** for a spring-integrated engine, for which a bean implementing the JavaDelegate interface is referenced via an expression in the BPMN xml. Another method, that is closer to the architecture enforced in Camunda 8, is the **external task worker pattern**.

With the enforced remote engine architecture in **Camunda 8**, only the **external task worker pattern** can be used, utilizing so-called **job workers**.

## Remarks

The code conversion patterns describe the necessary changes to convert a Camunda 7 code base to Camunda 8. Changes to the BPMN XML are covered by different tooling.
