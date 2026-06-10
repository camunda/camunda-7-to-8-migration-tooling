# Expression &#8594; Job Worker (Spring)

In Camunda 7, you can use arbitrary expression in JUEL, the Java Unified Expression Language. Those expressions might access the Spring context as well as Camunda's context.

JUEL is not supported in Camunda 8. And more importantly, Camunda cannot directly evaluate any expressions that might include your own applications context, like its Spring beans.

**Triage your expressions before reaching for a JUEL job worker** — most expressions do not need one:

1. **Pure data expressions** (e.g. `${amount > 5000}`, `${order.customer.name}`) — convert to FEEL. The [Diagram Converter](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter/) converts simple JUEL expressions to FEEL automatically.
2. **Conditional events** with JUEL conditions — since **Camunda 8.9**, BPMN conditional events (start, intermediate catch, and boundary) are supported natively with FEEL conditions; the Diagram Converter detects and converts them.
3. **Expressions invoking Spring beans** (e.g. `${someBean.doStuff(x)}` as a service task expression or delegate expression) — preferably refactor into a regular job worker (one worker per bean method, clean and testable). Only if you have many such expressions and want a mechanical migration, use the generic JUEL job worker described below.

## Camunda 7: Expression

You can use expressions denoted by `$` or `#`. The following example will call a method on the Spring bean `someBean` and hand over the process variable `processVariableA` as a parameter. The return value is captured in the process variable `someResult`:

```xml
<camunda:expression="${someBean.doStuff(processVariableA)}" camunda:resultVariable="someResult">
```


## Camunda 8: JUEL Job Worker

In Camunda 8 you can store the required configuration parameters in header attributes. This is what the [Migration Analyzer & Diagram Converter](https://github.com/camunda-community-hub/camunda-7-to-8-migration-analyzer) does out-of-the-box.

```xml
<bpmn:serviceTask >
  <bpmn:extensionElements>
    <zeebe:taskHeaders>
      <zeebe:header key="expression" value="${someBean.doStuff(processVariableA)}"/>
      <zeebe:header key="resultVariable" value="someResult"/>
    </zeebe:taskHeaders>
```

Then you need to add a worker that can evaluate JUEL expressions. How this is implemented exactly depends on the exact requirements you have. A simple example is shown in [the camunda-7-to-8-migration-example](https://github.com/camunda-community-hub/camunda-7-to-8-migration-example/blob/main/process-solution-camunda-8/src/main/java/org/camunda/community/migration/example/el/JuelExpressionEvaluatorWorker.java):

```java
  @JobWorker(type = "JuelExpressionEvaluatorWorker")
  public Map<String, Object> executeJobMigrated(ActivatedJob job) throws Exception {
    Map<String, Object> resultMap = new HashMap<>();
    String expression = job.getCustomHeaders().get("expression");
    Object result = evaluate(expression, job.getVariablesAsMap());
    String resultVariable = job.getCustomHeaders().get("resultVariable");
    resultMap.put(resultVariable, result);
    return resultMap;
  }
```

Then you can set the job type to 

```xml
<bpmn:serviceTask >
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="JuelExpressionEvaluatorWorker"/>
```
