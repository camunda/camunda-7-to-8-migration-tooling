# Glue code

Whenever you define code that is executed when a process arrives at a specific state in the process, specifically JavaDelegates and external task workers.

In **Camunda 7**, code executed by a service task or listener can be organized in various ways. A common method is using a **JavaDelegate** for a spring-integrated engine, for which a bean implementing the JavaDelegate interface is referenced via an expression in the BPMN xml. Another method, that is closer to the architecture enforced in Camunda 8, is the **external task worker pattern**.

With the enforced remote engine architecture in **Camunda 8**, only the **external task worker pattern** can be used, utilizing so-called **job workers**.
