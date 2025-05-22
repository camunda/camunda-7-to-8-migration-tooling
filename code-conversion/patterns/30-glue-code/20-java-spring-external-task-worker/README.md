# External Task Worker (Spring) &#8594; Job Worker (Spring)

In Camunda 7, external task workers are a way to implement glue code. They are deployed independently from the engine. Thus, they cannot access the engine's services.

The code conversion patterns for the external task workers cover the most important methods how an external task worker can interact with the running process instance:

-   getting and setting process variables
-   reporting a failure
-   raising an incident
-   throwing a BPMN error

There are often multiple methods that achieve the same result. The patterns try to capture as many examples as possible. External task workers are often more easily migrated to job workers in Camunda 8, as the architecture is similar.

## OpenRewrite recipe (WIP)

-   [Recipe "JavaDelegateSpringToZeebeWorkerSpring"](../recipes/src/main/java/org/camunda/migration/rewrite/recipes/glue/JavaDelegateSpringToZeebeWorkerSpring.java)
-   [Learn how to apply recipes](../recipes/)
