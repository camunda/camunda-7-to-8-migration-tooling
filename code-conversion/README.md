# Camunda 7 to 8 Code Conversion

As Camunda 8 is a complete rewrite of Camunda 7, you must adjust solutions developed for Camunda 7 to run on Camunda 8.

This project:

-   Describes **[code conversion patterns (WIP)](patterns/)**, specifically for **client code** and **glue code** of Java (Spring) solutions.

-   Provides **[OpenRewrite recipes (WIP)](recipes/)** to automate refactoring of Java solutions.

-   Hosts a **[Camunda 7 API to Camunda 8 API Mapping WebApp (WIP)](https://camunda-community-hub.github.io/camunda-7-to-8-code-conversion/)** to quickly find language-agnostic information about Camunda 7 API endpoints and how they relate to Camunda 8 endpoints. Specifically:
    -   Which Camunda 7 endpoints map directly to Camunda 8 endpoints and how
    -   Which Camunda 7 endpoints are on the roadmap for upcoming Camunda 8 releases
    -   Which Camunda 7 endpoints are handled conceptually differently in Camunda 8
    -   Which Camunda 7 endpoints are discontinued

See also **[Camunda 7 to 8 Migration Example in Action](https://github.com/camunda-community-hub/camunda-7-to-8-migration-example)**
