## Camunda 7 entities
| Entity Type | Operation Type | Category | Properties |
|-------------|----------------|----------|------------|
| **Task** | Assign | TaskWorker | • **assignee**: The id of the user who was assigned to the task |
| | Claim | TaskWorker | • **assignee**: The id of the user who claimed the task |
| | Complete | TaskWorker | • **delete**: The new delete state, `true` |
| | Create | TaskWorker | *No additional property is logged* |
| | Delegate | TaskWorker | When delegating a task, three log entries are created, containing one of the following properties:<br>• **delegation**: The resulting delegation state, `PENDING`<br>• **owner**: The original owner of the task<br>• **assignee**: The user this task has been assigned to |
| | Delete | TaskWorker | • **delete**: The new delete state, `true` |
| | Resolve | TaskWorker | • **delegation**: The resulting delegation state, `RESOLVED` |
| | SetOwner | TaskWorker | • **owner**: The new owner of the task |
| | SetPriority | TaskWorker | • **priority**: The new priority of the task |
| | Update | TaskWorker | The manually changed property of a task, where manually means that a property got directly changed. Claiming a task via the TaskService wouldn't be logged with an update entry, but setting the assignee directly would be. One of the following is possible:<br>• **description**: The new description of the task<br>• **owner**: The new owner of the task<br>• **assignee**: The new assignee to the task<br>• **dueDate**: The new due date of the task |
| | DeleteHistory | Operator | • **nrOfInstances**: the amount of decision instances that were deleted<br>• **async**: by default `false` since the operation can only be performed synchronously |
| **ProcessInstance** | Create | Operator | *No additional property is logged* |
| | Activate | Operator | • **suspensionState**: The new suspension state, `active` |
| | Delete | Operator | In case of regular operation:<br>*No additional property is logged*<br><br>In case of batch operation:<br>• **nrOfInstances**: the amount of process instances that were deleted<br>• **async**: `true` if operation was performed asynchronously as a batch, `false` if operation was performed synchronously<br>• **deleteReason**: the reason for deletion<br>• **type**: `history` in case of deletion of historic process instances |
| | ModifyProcessInstance | Operator | • **nrOfInstances**: The amount of process instances modified<br>• **async**: `true` if modification was performed asynchronously as a batch, `false` if modification was performed synchronously<br>• **processDefinitionVersion**: The version of the process definition |
| | Suspend | Operator | • **suspensionState**: The new suspension state, `suspended` |
| | Migrate | Operator | • **processDefinitionId**: The id of the process definition that instances are migrated to<br>• **nrOfInstances**: The amount of process instances migrated<br>• **nrOfVariables**: The amount of set variables. Only present when variables were set<br>• **async**: `true` if migration was performed asynchronously as a batch, `false` if migration was performed synchronously |
| | RestartProcessInstance | Operator | • **nrOfInstances**: The amount of process instances restarted<br>• **async**: `true` if restart was performed asynchronously as a batch, `false` if restart was performed synchronously |
| | DeleteHistory | Operator | • **nrOfInstances**: the amount of process instances that were deleted<br>• **async**: `true` if operation was performed asynchronously as a batch, `false` if operation was performed synchronously<br>• **deleteReason**: the reason for deletion. This property exists only if the operation was performed asynchronously |
| | CreateIncident | Operator | • **incidentType**: The type of incident that was created<br>• **configuration**: The configuration of the incident that was created |
| | Resolve | Operator | • **incidentId**: The id of the incident that was resolved |
| | SetRemovalTime | Operator | • **async**: `true` if operation was performed asynchronously as a batch<br>• **nrOfInstances**: The amount of updated instances<br>• **removalTime**: The date of which an instance shall be removed<br>• **mode**: `CALCULATED_REMOVAL_TIME` if the removal time was calculated, `ABSOLUTE_REMOVAL_TIME` if the removal time was set explicitly, `CLEARED_REMOVAL_TIME` if the removal time was cleared<br>• **hierarchical**: `true` if the removal time was set across the hiearchy, `false` if the hierarchy was neglected |
| | SetVariables | Operator | • **async**: `true` if operation was performed asynchronously as a batch<br>• **nrOfInstances**: The amount of affected instances<br>• **nrOfVariables**: The amount of set variables |
| | CorrelateMessage | Operator | • **async**: `true` if operation was performed asynchronously as a batch<br>• **nrOfInstances**: The amount of affected instances<br>• **nrOfVariables**: The amount of set variables<br>• **messageName**: The name of the correlated message |
| **Incident** | SetAnnotation | Operator | • **incidentId**: the id of the annotated incident |
| | ClearAnnotation | Operator | • **incidentId**: the id of the annotated incident |
| **IdentityLink** | AddUserLink | TaskWorker | • **candidate**: The new candidate user associated |
| | DeleteUserLink | TaskWorker | • **candidate**: The previously associated user |
| | AddGroupLink | TaskWorker | • **candidate**: The new group associated |
| | DeleteGroupLink | TaskWorker | • **candidate**: The previously associated group |
| **Attachment** | AddAttachment | TaskWorker | • **name**: The name of the added attachment |
| | DeleteAttachment | TaskWorker | • **name**: The name of the deleted attachment |
| **JobDefinition** | ActivateJobDefinition | Operator | • **suspensionState**: the new suspension state `active` |
| | SetPriority | Operator | • **overridingPriority**: the new overriding job priority. Is `null`, if the priority was cleared. |
| | SuspendJobDefinition | Operator | • **suspensionState**: the new suspension state `suspended` |
| **ProcessDefinition** | ActivateProcessDefinition | Operator | • **suspensionState**: the new suspension state `active` |
| | SuspendProcessDefinition | Operator | • **suspensionState**: the new suspension state `suspended` |
| | Delete | Operator | • **cascade**: if the value is set to `true`, then all instances including history are also deleted. |
| | UpdateHistoryTimeToLive | Operator | • **historyTimeToLive**: the new history time to live. |
| **DecisionDefinition** | UpdateHistoryTimeToLive | Operator | • **historyTimeToLive**: the new history time to live.<br>• **decisionDefinitionId**: the id of the decision definition whose history time to live is updated.<br>• **decisionDefinitionKey**: the key of the decision definition whose history time to live is updated. |
| | Evaluate | Operator | • **decisionDefinitionId**: the id of the decision definition that was evaluated.<br>• **decisionDefinitionKey**: the key of the decision definition that was evaluated. |
| **CaseDefinition** | UpdateHistoryTimeToLive | Operator | • **historyTimeToLive**: the new history time to live.<br>• **caseDefinitionKey**: the key of the case definition whose history time to live is updated. |
| **Job** | ActivateJob | Operator | • **suspensionState**: the new suspension state `active` |
| | SetPriority | Operator | • **priority**: the new priority of the job |
| | SetJobRetries | Operator | • **retries**: the new number of retries<br>• **nrOfInstances**: the number of jobs that were updated.<br>• **async**: `true` if operation was performed asynchronously as a batch, `false` if operation was performed synchronously |
| | SuspendJob | Operator | • **suspensionState**: the new suspension state `suspended`<br>• **async**: `true` if operation was performed asynchronously as a batch, `false` if operation was performed synchronously |
| | Execute | Operator | *No additional property is logged* |
| | Delete | Operator | *No additional property is logged* |
| | SetDueDate | Operator | • **duedate**: the new due date of the job |
| | RecalculateDueDate | Operator | • **creationDateBased**: if the value is set to `true`, the new due date was calculated based on the creation date of the job. Otherwise, it was calculated using the date the recalcuation took place.<br>• **duedate**: the new due date of the job |
| | CreateHistoryCleanupJobs | Operator | • **immediatelyDue**: `true` if the operation was performed immediately, `false` if the operation was scheduled regularly |
| **Variable** | ModifyVariable | Operator/<br>TaskWorker | *No additional property is logged* |
| | RemoveVariable | Operator/<br>TaskWorker | *No additional property is logged* |
| | SetVariable | Operator/<br>TaskWorker | *No additional property is logged* |
| | DeleteHistory | Operator | In case of single operation:<br>• **name**: the name of the variable whose history was deleted<br><br>In case of list operation by process instance:<br>*No additional property is logged* |
| **Deployment** | Create | Operator | • **duplicateFilterEnabled**: if the value is set to `true`, then during the creation of the deployment the given resources have been checked for duplicates in the set of previous deployments. Otherwise, the duplicate filtering has been not executed.<br>• **deployChangedOnly**: this property is only logged when `duplicateFilterEnabled` is set to `true`. If the property value is set to `true` then only changed resources have been deployed. Otherwise, all resources are redeployed if any resource has changed. |
| | Delete | Operator | • **cascade**: if the value is set to `true`, then all instances including history are also deleted. |
| **Batch** | ActivateBatch | Operator | • **suspensionState**: the new suspension state `active` |
| | SuspendBatch | Operator | • **suspensionState**: the new suspension state `suspended` |
| | Delete | Operator | • **cascadeToHistory**: `true` if historic data related to the batch job is deleted as well, `false` if only the runtime data is deleted. |
| | DeleteHistory | Operator | *No additional property is logged* |
| | SetRemovalTime | Operator | • **async**: `true` if operation was performed asynchronously as a batch<br>• **nrOfInstances**: The amount of updated instances<br>• **removalTime**: The date of which an instance shall be removed<br>• **mode**: `CALCULATED_REMOVAL_TIME` if the removal time was calculated, `ABSOLUTE_REMOVAL_TIME` if the removal time was set explicitly, `CLEARED_REMOVAL_TIME` if the removal time was cleared |
| **ExternalTask** | SetExternalTaskRetries | Operator | • **retries**: the new number of retries<br>• **nrOfInstances**: the amount of external tasks that were updated<br>• **async**: `true` if operation was performed asynchronously as a batch, `false` if operation was performed synchronously |
| | SetPriority | Operator | • **priority**: the new priority |
| | Unlock | Operator | *No additional property is logged* |
| **DecisionInstance** | DeleteHistory | Operator | • **nrOfInstances**: the amount of decision instances that were deleted<br>• **async**: `true` if operation was performed asynchronously as a batch, `false` if operation was performed synchronously<br>• **deleteReason**: the reason for deletion. This property exists only if operation was performed asynchronously |
| | SetRemovalTime | Operator | • **async**: `true` if operation was performed asynchronously as a batch<br>• **nrOfInstances**: The amount of updated instances<br>• **removalTime**: The date of which an instance shall be removed<br>• **mode**: `CALCULATED_REMOVAL_TIME` if the removal time was calculated, `ABSOLUTE_REMOVAL_TIME` if the removal time was set explicitly, `CLEARED_REMOVAL_TIME` if the removal time was cleared<br>• **hierarchical**: `true` if the removal time was set across the hiearchy, `false` if the hierarchy was neglected |
| **CaseInstance** | DeleteHistory | Operator | • **nrOfInstances**: The amount of case instances that were deleted. Only present if executed in bulk delete. |
| **Metrics** | Delete | Operator | • **timestamp**: The date for which all metrics older than that have been deleted. Only present if specified by the user.<br>• **reporter**: The reporter for which all metrics reported by it have been deleted. Only present if specified by the user. |
| **TaskMetrics** | Delete | Operator | • **timestamp**: The date for which all task metrics older than that have been deleted. Only present if specified by the user. |
| **OperationLog** | SetAnnotation | Operator | • **operationId**: the id of the annotated operation log |
| | ClearAnnotation | Operator | • **operationId**: the id of the annotated operation log |
| **Filter** | Create | TaskWorker | • **filterId**: the id of the filter that been created |
| | Update | TaskWorker | • **filterId**: the id of the filter that been updated |
| | Delete | TaskWorker | • **filterId**: the id of the filter that been deleted |
| **Comment** | Update | TaskWorker | *No additional property is logged* |
| | Delete | TaskWorker | *No additional property is logged* |
| **User** | Create | Admin | • **userId**: the id of the user that has been created |
| | Update | Admin | • **userId**: the id of the user that has been updated |
| | Delete | Admin | • **userId**: the id of the user that has been deleted |
| | Unlock | Admin | • **userId**: the id of the user that has been unlocked |
| **Group** | Create | Admin | • **groupId**: the id of the group that has been created |
| | Update | Admin | • **groupId**: the id of the group that has been updated |
| | Delete | Admin | • **groupId**: the id of the group that has been deleted |
| **Tenant** | Create | Admin | • **tenantId**: the id of the tenant that has been created |
| | Update | Admin | • **tenantId**: the id of the tenant that has been updated |
| | Delete | Admin | • **tenantId**: the id of the tenant that has been deleted |
| **Group membership** | Create | Admin | • **userId**: the id of the user that has been added to the group<br>• **groupId**: the id of the group that the user has been added to |
| | Delete | Admin | • **userId**: the id of the user that has been deleted from the group<br>• **groupId**: the id of the group that the user has been deleted from |
| **TenantMembership** | Create | Admin | • **tenantId**: the id of the tenant that the group or user was associated with<br>• **userId**: the id of the user that has been associated with the tenant. Is not present if the `groupId` is set<br>• **groupId**: the id of the group that has been associated with the tenant. Is not present if the `userId` is set |
| | Delete | Admin | • **tenantId**: the id of the tenant that the group or user has been deleted from<br>• **userId**: the id of the user that has been deleted from the tenant. Is not present if the `groupId` is set<br>• **groupId**: the id of the group that has been deleted from the tenant. Is not present if the `userId` is set |
| **Authorization** | Create | Admin | • **permissions**: the list of permissions that has been granted or revoked<br>• **permissionBits**: the permissions bit mask that is persisted with the authorization<br>• **type**: the type of authorization, can be either 0 (GLOBAL), 1 (GRANT) or 2 (REVOKE)<br>• **resource**: the name of the resource type<br>• **resourceId**: The id of the resource. Can be `'*'` if granted or revoked for all instances of the resource type.<br>• **userId**: The id of the user the authorization is bound to. Can be `'*'` if granted or revoked for all users. Is not present when `groupId` is set.<br>• **groupId**: The id of the group the authorization is bound to. Is not present when `userId` is set. |
| | Update | Admin | • **permissions**: the list of permissions that has been granted or revoked<br>• **permissionBits**: the permissions bit mask that is persisted with the authorization<br>• **type**: the type of authorization, can be either 0 (GLOBAL), 1 (GRANT) or 2 (REVOKE)<br>• **resource**: the name of the resource type<br>• **resourceId**: The id of the resource. Can be `'*'` if granted or revoked for all instances of the resource type.<br>• **userId**: The id of the user the authorization is bound to. Can be `'*'` if granted or revoked for all users. Is not present when `groupId` is set.<br>• **groupId**: The id of the group the authorization is bound to. Is not present when `userId` is set. |
| | Delete | Admin | • **permissions**: the list of permissions that has been granted or revoked<br>• **permissionBits**: the permissions bit mask that is persisted with the authorization<br>• **type**: the type of authorization, can be either 0 (GLOBAL), 1 (GRANT) or 2 (REVOKE)<br>• **resource**: the name of the resource type<br>• **resourceId**: The id of the resource. Can be `'*'` if granted or revoked for all instances of the resource type.<br>• **userId**: The id of the user the authorization is bound to. Can be `'*'` if granted or revoked for all users. Is not present when `groupId` is set.<br>• **groupId**: The id of the group the authorization is bound to. Is not present when `userId` is set. |
| **Property** | Create | Admin | • **name**: the name of the property that was created |
| | Update | Admin | • **name**: the name of the property that was updated |
| | Delete | Admin | • **name**: the name of the property that was deleted |


## Camunda 8 entities

| Entity Type | Operation Type | Category |
|-------------|----------------|----------|
| **Authorization** | CREATE | ADMIN |
| | UPDATE | ADMIN |
| | DELETE | ADMIN |
| **BatchOperation** | CREATE | DEPLOYED_RESOURCES |
| | RESUME | DEPLOYED_RESOURCES |
| | SUSPEND | DEPLOYED_RESOURCES |
| | CANCEL | DEPLOYED_RESOURCES |
| **DecisionEvaluation** | EVALUATE | DEPLOYED_RESOURCES |
| **Decision** | CREATE | DEPLOYED_RESOURCES |
| | DELETE | DEPLOYED_RESOURCES |
| **DecisionRequirements** | CREATE | DEPLOYED_RESOURCES |
| | DELETE | DEPLOYED_RESOURCES |
| **Form** | CREATE | DEPLOYED_RESOURCES |
| | DELETE | DEPLOYED_RESOURCES |
| **Group** | CREATE | ADMIN |
| | UPDATE | ADMIN |
| | DELETE | ADMIN |
| | ASSIGN | ADMIN |
| | UNASSIGN | ADMIN |
| **Incident** | RESOLVE | DEPLOYED_RESOURCES |
| **MappingRule** | CREATE | ADMIN |
| | UPDATE | ADMIN |
| | DELETE | ADMIN |
| **Process** | CREATE | DEPLOYED_RESOURCES |
| | DELETE | DEPLOYED_RESOURCES |
| **ProcessInstanceCreation** | CREATE | DEPLOYED_RESOURCES |
| **ProcessInstance** | CANCEL | DEPLOYED_RESOURCES |
| **ProcessInstanceMigration** | MIGRATE | DEPLOYED_RESOURCES |
| **ProcessInstanceModification** | MODIFY | DEPLOYED_RESOURCES |
| **Resource** | CREATE | DEPLOYED_RESOURCES |
| | DELETE | DEPLOYED_RESOURCES |
| **Tenant** | CREATE | ADMIN |
| | UPDATE | ADMIN |
| | DELETE | ADMIN |
| | ASSIGN | ADMIN |
| | UNASSIGN | ADMIN |
| **Role** | CREATE | ADMIN |
| | UPDATE | ADMIN |
| | DELETE | ADMIN |
| | ASSIGN | ADMIN |
| | UNASSIGN | ADMIN |
| **User** | CREATE | ADMIN |
| | UPDATE | ADMIN |
| | DELETE | ADMIN |
| **UserTask** | ASSIGN | USER_TASKS |
| | UPDATE | USER_TASKS |
| | COMPLETE | USER_TASKS |
| **Variable** | CREATE | DEPLOYED_RESOURCES |
| | UPDATE | DEPLOYED_RESOURCES |


## Camunda 7 to Camunda 8 Migration Mapping

This table shows which Camunda 7 audit log entities and operations can be migrated to Camunda 8.

| Camunda 7 Entity | Camunda 7 Operation Type  | Camunda 8 Entity | Camunda 8 Operation Type | Camunda 8 Category | Can be Migrated |
|------------------|---------------------------|------------------|--------------------------|--------------------|-----------------|
| **Task** | Assign                    | UserTask | ASSIGN                   | USER_TASKS         | Yes             |
| | Claim                     | UserTask | ASSIGN                   | USER_TASKS         | Yes             |
| | Complete                  | UserTask | COMPLETE                 | USER_TASKS         | Yes             |
| | Create                    | UserTask | CREATE                   | USER_TASKS         | Yes             |
| | Delegate                  | UserTask | ASSIGN                   | USER_TASKS         | Yes             |
| | Delete                    | UserTask | DELETE                   | USER_TASKS         | Yes             |
| | Resolve                   | UserTask | UPDATE                   | USER_TASKS         | Yes             |
| | SetOwner                  | UserTask | ASSIGN                   | USER_TASKS         | Yes             |
| | SetPriority               | UserTask | UPDATE                   | USER_TASKS         | Yes             |
| | Update                    | UserTask | UPDATE                   | USER_TASKS         | Yes             |
| | DeleteHistory             | UserTask | DELETE                   | USER_TASKS         | Yes             |
| **ProcessInstance** | Create                    | ProcessInstance | CREATE                   | DEPLOYED_RESOURCES | Yes             |
| | Activate                  | ProcessInstance | -                        | -                  | No              |
| | Delete                    | ProcessInstance | CANCEL                   | DEPLOYED_RESOURCES | Yes             |
| | ModifyProcessInstance     | ProcessInstanceModification | MODIFY                   | DEPLOYED_RESOURCES | Yes             |
| | Suspend                   | ProcessInstance | -                        | -                  | No              |
| | Migrate                   | ProcessInstanceMigration | MIGRATE                  | DEPLOYED_RESOURCES | Yes             |
| | RestartProcessInstance    | ProcessInstance | -                        | -                  | No              |
| | DeleteHistory             | ProcessInstance | DELETE                   | DEPLOYED_RESOURCES | Yes             |
| | CreateIncident            | Incident | -                        | -                  | No              |
| | Resolve                   | Incident | RESOLVE                  | DEPLOYED_RESOURCES | Yes             |
| | SetRemovalTime            | ProcessInstance | -                        | -                  | No              |
| | SetVariables              | Variable | CREATE/UPDATE            | DEPLOYED_RESOURCES | Yes             |
| | CorrelateMessage          | ProcessInstance | -                        | -                  | No              |
| **Incident** | SetAnnotation             | Incident | -                        | -                  | No              |
| | ClearAnnotation           | Incident | -                        | -                  | No              |
| **IdentityLink** | AddUserLink               | UserTask | -                        | -                  | No              |
| | DeleteUserLink            | UserTask | -                        | -                  | No              |
| | AddGroupLink              | UserTask | -                        | -                  | No              |
| | DeleteGroupLink           | UserTask | -                        | -                  | No              |
| **Attachment** | AddAttachment             | UserTask | -                        | -                  | No              |
| | DeleteAttachment          | UserTask | -                        | -                  | No              |
| **JobDefinition** | ActivateJobDefinition     | - | -                        | -                  | No              |
| | SetPriority               | - | -                        | -                  | No              |
| | SuspendJobDefinition      | - | -                        | -                  | No              |
| **ProcessDefinition** | ActivateProcessDefinition | Resource | -                        | -                  | No              |
| | SuspendProcessDefinition  | Resource | -                        | -                  | No              |
| | Delete                    | Resource | DELETE                   | DEPLOYED_RESOURCES | Yes             |
| | UpdateHistoryTimeToLive   | Resource | -                        | -                  | No              |
| **DecisionDefinition** | UpdateHistoryTimeToLive   | Decision | -                        | -                  | No              |
| | Evaluate                  | DecisionEvaluation | EVALUATE                 | DEPLOYED_RESOURCES | Yes             |
| **CaseDefinition** | UpdateHistoryTimeToLive   | - | -                        | -                  | No              |
| **Job** | ActivateJob               | - | -                        | -                  | No              |
| | SetPriority               | - | -                        | -                  | No              |
| | SetJobRetries             | - | -                        | -                  | No              |
| | SuspendJob                | - | -                        | -                  | No              |
| | Execute                   | - | -                        | -                  | No              |
| | Delete                    | - | -                        | -                  | No              |
| | SetDueDate                | - | -                        | -                  | No              |
| | RecalculateDueDate        | - | -                        | -                  | No              |
| | CreateHistoryCleanupJobs  | - | -                        | -                  | No              |
| **Variable** | ModifyVariable            | Variable | UPDATE                   | DEPLOYED_RESOURCES | Yes             |
| | RemoveVariable            | Variable | DELETE                   | DEPLOYED_RESOURCES | Yes             |
| | SetVariable               | Variable | CREATE/UPDATE            | DEPLOYED_RESOURCES | Yes             |
| | DeleteHistory             | Variable | DELETE                   | DEPLOYED_RESOURCES | Yes             |
| **Deployment** | Create                    | Resource | CREATE                   | DEPLOYED_RESOURCES | Yes             |
| | Delete                    | Resource | DELETE                   | DEPLOYED_RESOURCES | Yes             |
| **Batch** | ActivateBatch             | BatchOperation | -                        | -                  | No              |
| | SuspendBatch              | BatchOperation | -                        | -                  | No              |
| | Delete                    | BatchOperation | -                        | -                  | No              |
| | DeleteHistory             | BatchOperation | -                        | -                  | No              |
| | SetRemovalTime            | BatchOperation | -                        | -                  | No              |
| **ExternalTask** | SetExternalTaskRetries    | - | -                        | -                  | No              |
| | SetPriority               | - | -                        | -                  | No              |
| | Unlock                    | - | -                        | -                  | No              |
| **DecisionInstance** | DeleteHistory             | Decision | DELETE                   | DEPLOYED_RESOURCES | Yes             |
| | SetRemovalTime            | Decision | -                        | -                  | No              |
| **CaseInstance** | DeleteHistory             | - | -                        | -                  | No              |
| **Metrics** | Delete                    | - | -                        | -                  | No              |
| **TaskMetrics** | Delete                    | - | -                        | -                  | No              |
| **OperationLog** | SetAnnotation             | - | -                        | -                  | No              |
| | ClearAnnotation           | - | -                        | -                  | No              |
| **Filter** | Create                    | - | -                        | -                  | No              |
| | Update                    | - | -                        | -                  | No              |
| | Delete                    | - | -                        | -                  | No              |
| **Comment** | Update                    | - | -                        | -                  | No              |
| | Delete                    | - | -                        | -                  | No              |
| **User** | Create                    | User | CREATE                   | ADMIN              | Yes             |
| | Update                    | User | UPDATE                   | ADMIN              | Yes             |
| | Delete                    | User | DELETE                   | ADMIN              | Yes             |
| | Unlock                    | User | -                        | -                  | No              |
| **Group** | Create                    | Group | CREATE                   | ADMIN              | Yes             |
| | Update                    | Group | UPDATE                   | ADMIN              | Yes             |
| | Delete                    | Group | DELETE                   | ADMIN              | Yes             |
| **Group membership** | Create                    | Group | ASSIGN                   | ADMIN              | Yes             |
| | Delete                    | Group | UNASSIGN                 | ADMIN              | Yes             |
| **TenantMembership** | Create                    | Tenant | ASSIGN                   | ADMIN              | Yes             |
| | Delete                    | Tenant | UNASSIGN                 | ADMIN              | Yes             |
| **Authorization** | Create                    | Authorization | CREATE                   | ADMIN              | Yes             |
| | Update                    | Authorization | UPDATE                   | ADMIN              | Yes             |
| | Delete                    | Authorization | DELETE                   | ADMIN              | Yes             |
| **Property** | Create                    | - | -                        | -                  | No              |
| | Update                    | - | -                        | -                  | No              |
| | Delete                    | - | -                        | -                  | No              |

### Notes:
- **Yes**: The entity and operation can be migrated to Camunda 8 with equivalent functionality
- **No**: The entity or operation has no equivalent in Camunda 8 (typically because the feature doesn't exist in Camunda 8)
- **-**: No equivalent entity exists in Camunda 8

### Entity Mappings:
Based on the `AuditLogTransformer.java` implementation:
- **Task** → **UserTask**
- **ProcessInstance** → **ProcessInstance**
- **ProcessDefinition** → **Resource**
- **Deployment** → **Resource**
- **DecisionInstance** → **Decision**
- **DecisionDefinition** → **Decision**
- **DecisionRequirementsDefinition** → **Decision**
- **Batch** → **BatchOperation** (approximation)
- **Incident** → **Incident**
- **Variable** → **Variable**
- **User** → **User**
- **Group** → **Group**
- **Tenant** → **Tenant**
- **Authorization** → **Authorization**
