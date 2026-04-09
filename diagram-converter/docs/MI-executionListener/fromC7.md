# Valid Configuration Options: Service Task with Multi-Instance + Execution Listeners

Based on the Camunda 7.19 engine source (BPMN XSD schemas and `BpmnParse.java`):

---

## 1. Service Task Attributes (on `<serviceTask>`)

### Standard BPMN Attributes (inherited from `tTask` → `tActivity` → `tFlowNode` → `tFlowElement` → `tBaseElement`)
| Attribute | Type | Required | Default |
|---|---|---|---|
| `id` | ID | optional | — |
| `name` | string | optional | — |
| `implementation` | tImplementation | optional | `##WebService` |
| `operationRef` | QName | optional | — |
| `isForCompensation` | boolean | optional | `false` |
| `startQuantity` | integer | optional | `1` |
| `completionQuantity` | integer | optional | `1` |
| `default` | IDREF | optional | — |

### Camunda Extension Attributes (on `<serviceTask>`)
| Attribute | Description |
|---|---|
| `camunda:class` | Fully qualified Java class implementing `JavaDelegate` or `ActivityBehavior` |
| `camunda:expression` | EL expression evaluated at runtime |
| `camunda:delegateExpression` | EL expression resolving to `JavaDelegate`/`ActivityBehavior` |
| `camunda:resultVariable` | Variable name to store expression result (only with `expression`) |
| `camunda:type` | Built-in type: `mail`, `shell`, or `external` |
| `camunda:topic` | Topic name (when `type="external"`) |
| `camunda:taskPriority` | Priority (when `type="external"`) |
| `camunda:asyncBefore` | Async before the activity (boolean) |
| `camunda:asyncAfter` | Async after the activity (boolean) |
| `camunda:exclusive` | Exclusive job execution (boolean) |

> **One of `camunda:class`, `camunda:expression`, `camunda:delegateExpression`, or `camunda:type` is mandatory.**

---

## 2. Multi-Instance Loop Characteristics (`<multiInstanceLoopCharacteristics>`)

Nested inside `<serviceTask>`.

### Standard BPMN Attributes & Elements
| Config | Type | Required | Description |
|---|---|---|---|
| `isSequential` (attr) | boolean | optional (default `false`) | `true` = sequential, `false` = parallel |
| `behavior` (attr) | enum | optional (default `All`) | `None`, `One`, `All`, `Complex` |
| `<loopCardinality>` | expression | optional* | Fixed number of instances |
| `<loopDataInputRef>` | QName | optional* | Reference to a collection variable |
| `<loopDataOutputRef>` | QName | optional | Reference to output data |
| `<inputDataItem>` | tDataInput | optional | Element variable name from collection |
| `<outputDataItem>` | tDataOutput | optional | Output element variable |
| `<completionCondition>` | expression | optional | Expression evaluated after each instance; if `true`, remaining instances are cancelled |
| `<complexBehaviorDefinition>` | — | optional | Complex behavior definitions |

### Camunda Extension Attributes (on `<multiInstanceLoopCharacteristics>`)
| Attribute | Description |
|---|---|
| `camunda:collection` | Collection expression or process variable name (alternative to `loopDataInputRef`) |
| `camunda:elementVariable` | Variable name for each element in the collection (alternative to `inputDataItem`) |
| `camunda:asyncBefore` | Async before each **inner** activity instance |
| `camunda:asyncAfter` | Async after each **inner** activity instance |
| `camunda:exclusive` | Exclusive job execution for inner async jobs |

> **Validation**: Either `loopCardinality` OR `loopDataInputRef`/`camunda:collection` **must** be set.
> If `inputDataItem`/`camunda:elementVariable` is set, then `loopDataInputRef`/`camunda:collection` must also be set.

### Async Behavior for Multi-Instance
When a service task has multi-instance characteristics, **two levels of async** are possible:
- **`camunda:asyncBefore`/`asyncAfter` on `<serviceTask>`** → applies to the **multi-instance body** (outer scope)
- **`camunda:asyncBefore`/`asyncAfter` on `<multiInstanceLoopCharacteristics>`** → applies to each **inner activity instance**

---

## 3. Execution Listeners (`<camunda:executionListener>`)

Placed inside `<extensionElements>` of the `<serviceTask>`.

### Attributes
| Attribute | Required | Description |
|---|---|---|
| `event` | **required** | Must be `start` or `end` |
| `class` | one of these required | Fully qualified class implementing `ExecutionListener` |
| `expression` | one of these required | EL expression evaluated on event |
| `delegateExpression` | one of these required | EL expression resolving to an `ExecutionListener` |

> **One of `class`, `expression`, `delegateExpression`, or a nested `<camunda:script>` element is mandatory.**

### Nested Elements
| Element | Description |
|---|---|
| `<camunda:field>` | Field injection (0..n), with `name` (required), plus `stringValue`/`expression` attr or `<string>`/`<expression>` child |
| `<camunda:script>` | Inline script (alternative to class/expression/delegateExpression), with `scriptFormat` attribute and script body |

### Important: Execution Listener Scope with Multi-Instance
Execution listeners defined on the `<serviceTask>` element fire for **each instance** of the multi-instance activity (inner activity), **not** on the multi-instance body. The events are:
- `start` — fired when each instance starts
- `end` — fired when each instance ends

---

## 4. Other Extension Elements (inside `<extensionElements>`)

| Element | Description |
|---|---|
| `<camunda:field>` | Field injection into delegate class |
| `<camunda:inputOutput>` | Input/output parameter mappings (`<camunda:inputParameter>`, `<camunda:outputParameter>`) |
| `<camunda:properties>` | Custom key-value properties (`<camunda:property name="..." value="..."/>`) |
| `<camunda:failedJobRetryTimeCycle>` | Retry configuration for async jobs |
| `<camunda:errorEventDefinition>` | Camunda error event definitions (for external tasks) |
| `<camunda:connector>` | Connector configuration (alternative to class/expression) |

---

## 5. Example XML

```xml
<serviceTask id="myTask" name="My Service Task"
             camunda:class="com.example.MyDelegate">
  <extensionElements>
    <!-- Execution Listeners -->
    <camunda:executionListener event="start" 
                               class="com.example.MyStartListener"/>
    <camunda:executionListener event="end" 
                               delegateExpression="${myEndListener}"/>
    <camunda:executionListener event="start" 
                               expression="${someBean.doSomething()}"/>
    <camunda:executionListener event="end">
      <camunda:script scriptFormat="groovy">
        println "Task ended"
      </camunda:script>
    </camunda:executionListener>

    <!-- Field Injection -->
    <camunda:field name="url" stringValue="http://example.com"/>

    <!-- Input/Output -->
    <camunda:inputOutput>
      <camunda:inputParameter name="input1">${myVar}</camunda:inputParameter>
      <camunda:outputParameter name="output1">${result}</camunda:outputParameter>
    </camunda:inputOutput>
  </extensionElements>

  <!-- Multi-Instance Configuration -->
  <multiInstanceLoopCharacteristics isSequential="false"
                                     camunda:collection="${myList}"
                                     camunda:elementVariable="item"
                                     camunda:asyncBefore="true">
    <completionCondition>${nrOfCompletedInstances / nrOfInstances >= 0.5}</completionCondition>
  </multiInstanceLoopCharacteristics>
</serviceTask>
```
