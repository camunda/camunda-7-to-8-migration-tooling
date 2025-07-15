# Glue code

Whenever you define code that is executed when a process arrives at a specific state in the process, specifically JavaDelegates and external task workers.

In **Camunda 7**, code executed by a service task or listener can be organized in various ways. A common method is using a **JavaDelegate** for a spring-integrated engine, for which a bean implementing the JavaDelegate interface is referenced via an expression in the BPMN xml. Another method, is the **external task worker pattern**. But you might also leverage Java Unified Expression Language (JUEL).

With the remote engine architecture in **Camunda 8**, you need to use so-called **job workers**.

The glue code patterns look into the different scenarios and proposes code conversion patterns - here a quick summary, including default the  [Migration Analyzer & Diagram Converter](https://github.com/camunda-community-hub/camunda-7-to-8-migration-analyzer) sets.

| Camunda 7 Implementation           | Example                                         | Camunda 8 Job Type        | Notes                                                                 | Link |
|-----------------------------------|-------------------------------------------------|----------------------------|-----------------------------------------------------------------------|------|
| `camunda:class`                   | `camunda:class="com.example.MyDelegate"`        | `myDelegate`               | Class name is converted to camelCase; assumes a `@JobWorker` Spring bean | [JavaDelegate &#8594; Job Worker (Spring)](10-java-spring-delegate/) |
| `camunda:delegateExpression`      | `camunda:delegateExpression="${myBean}"`        | `myBean`                   | Bean name is used directly; assumes a `@JobWorker`-annotated method   | [JavaDelegate &#8594; Job Worker (Spring)](10-java-spring-delegate/) |
| `camunda:expression`             | `camunda:expression="${someBean.doStuff()}"`    | `someBeanDoStuff`                  | Method name used as job type; original expression saved as header so you can have your own worker evaluating the original expression     | [15-java-expression/]() |
| No implementation / fallback     | *(none or unsupported type)*                    | `defaultJobType`           | Uses configured fallback (`"camunda-7-job"` by default)               | []() |




