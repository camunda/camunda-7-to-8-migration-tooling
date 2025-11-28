export const task = [
	{
		origin: {
			path: "/task",
			operation: "get",
		},
		target: {
			path: "/user-tasks/search",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(string) taskId
							<br />
							(string[]) taskIdIn
						</pre>
					),
					rightEntry: (
						<>
							<pre>(string) userTaskKey</pre>
							<p>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) processInstanceId
							<br />
							(string[]) processInstanceIdIn
						</pre>
					),
					rightEntry: (
						<>
							<pre>(string) processInstanceKey</pre>
							<p>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string) processDefinitionId</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionKey</pre>
							<p>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) processDefinitionKey
							<br />
							(string[]) processDefinitionKeyIn
						</pre>
					),
					rightEntry: (
						<>
							<pre>(string) processDefinitionId</pre>
							<p>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string[]) activityInstanceIdIn</pre>,
					rightEntry: <pre>(string) elementInstanceKey</pre>,
				},
				{
					leftEntry: (
						<pre>
							(string[]) tenantIdIn
							<br />
							(boolean) withoutTenantId
						</pre>
					),
					rightEntry: <pre>(string) tenantId</pre>,
				},
				{
					leftEntry: (
						<pre>
							(string) assignee
							<br />
							(string) assigneeExpression
							<br />
							(string) assigneeLike
							<br />
							(string) assigneeLikeExpression
							<br />
							(string) assigneeIn
							<br />
							(string) assigneeNotIn
						</pre>
					),
					rightEntry: <pre>(string*) assignee</pre>,
				},
				{
					leftEntry: (
						<pre>
							(string) candidateGroup
							<br />
							(string) candidateGroupLike
							<br />
							(string) candidateGroupExpression
							<br />
							(string[]) candidateGroups
							<br />
							(string[]) candidateGroupsExpression
							<br />
							(boolean) withCandidateGroups
							<br />
							(boolean) withoutCandidateGroups
						</pre>
					),
					rightEntry: <pre>(string*) candidateGroup</pre>,
				},
				{
					leftEntry: (
						<pre>
							(string) candidateUser
							<br />
							(string) candidateUserExpression
							<br />
							(boolean) withCandidateUsers
							<br />
							(boolean) withoutCandidateUsers
						</pre>
					),
					rightEntry: <pre>(string*) candidateUser</pre>,
				},
				{
					leftEntry: (
						<pre>
							(boolean) assigned
							<br />
							(boolean) unassigned
						</pre>
					),
					rightEntry: (
						<>
							<pre>(string*) assignee</pre>
							<p>Use advanced filters.</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) taskDefinitionKey
							<br />
							(string[]) taskDefinitionKeyIn
							<br />
							(string) taskDefinitionKeyLike
						</pre>
					),
					rightEntry: <pre>(string) elementId</pre>,
				},
				{
					leftEntry: (
						<pre>
							(integer) priority
							<br />
							(integer) maxPriority
							<br />
							(integer) minPriority
						</pre>
					),
					rightEntry: <pre>(integer*) priority</pre>,
				},
				{
					leftEntry: (
						<pre>
							(dateTime) dueDate
							<br />
							(dateTime) dueDateExpression
							<br />
							(dateTime) dueAfter
							<br />
							(dateTime) dueAfterExpression
							<br />
							(dateTime) dueBefore
							<br />
							(dateTime) dueBeforeExpression
							<br />
							(dateTime) withoutDueDate
						</pre>
					),
					rightEntry: <pre>(dateTime*) dueDate</pre>,
				},
				{
					leftEntry: (
						<pre>
							(dateTime) followUpDate
							<br />
							(dateTime) followUpDateExpression
							<br />
							(dateTime) followUpAfter
							<br />
							(dateTime) followUpAfterExpression
							<br />
							(dateTime) followUpBefore
							<br />
							(dateTime) followUpBeforeExpression
							<br />
							(dateTime) followUpBeforeOrNotExistent
							<br />
							(dateTime) followUpBeforeOrNotExistentExpression
						</pre>
					),
					rightEntry: <pre>(dateTime*) followUpDate</pre>,
				},
				{
					leftEntry: (
						<pre>
							(dateTime) createdOn
							<br />
							(dateTime) createdOnExpression
							<br />
							(dateTime) createdAfter
							<br />
							(dateTime) createdAfterExpression
							<br />
							(dateTime) createdBefore
							<br />
							(dateTime) createdBeforeExpression
						</pre>
					),
					rightEntry: <pre>(dateTime*) creationDate</pre>,
				},
				{
					leftEntry: <pre>(object[]*) taskVariables</pre>,
					rightEntry: <pre>(object[]*) localVariables</pre>,
				},
				{
					leftEntry: <pre>(object[]*) processVariables</pre>,
					rightEntry: <pre>(object[]*) processInstanceVariables</pre>,
				},
			],
			additionalInfo: (
				<>
					<p>
						<code>...Like</code> and <code>...In</code> parameters
						are grouped together with the parameter they relate to.
					</p>
					<p>
						Asterisks signify that an advanced filter can be
						applied, similar to a unit test (
						<code>$eq, $neq, $in, $like with wildcards,...</code>).
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(string) processInstanceBusinessKey
							<br />
							(string) processInstanceBusinessKeyExpression
							<br />
							(string[]) processInstanceBusinessKeyIn
							<br />
							(string) processInstanceBusinessKeyLike
							<br />
							(string) processInstanceBusinessKeyLikeExpression
						</pre>
					),
					rightEntry: (
						<p>
							No businessKey in Camunda 8.8. <a href="https://roadmap.camunda.com/c/296-business-key">Planned for a future release</a>.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) processDefinitionName
							<br />
							(string) processDefinitionNameLike
						</pre>
					),
					rightEntry: <p>Not possible in Camunda 8.8.</p>,
				},
				{
					leftEntry: <pre>(string) executionId</pre>,
					rightEntry: (
						<>
							<p>
								Not yet possible in Camunda 8.8. In Camunda 7,
								the executionId is used to differentiate between
								parallel executions in one process instance.
							</p>
							<p>
								In Camunda 8, unique identifiers for a user task
								are the <code>userTaskKey</code> and{" "}
								<code>elementInstanceKey</code>.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) caseInstanceId
							<br />
							(string) caseInstanceBusinessKey
							<br />
							(string) caseInstanceBusinessKeyLike
							<br />
							(string) caseDefinitionId
							<br />
							(string) caseDefinitionKey
							<br />
							(string) caseDefinitionName
							<br />
							(string) caseDefinitionNameLike
							<br />
							(string) caseExecutionId
							<br />
							(object[]*) caseInstanceVariables
						</pre>
					),
					rightEntry: <p>No CMMN in Camunda 8.</p>,
				},
				{
					leftEntry: (
						<pre>
							(string) owner
							<br />
							(string) ownerExpression
							<br />
							(string) delegationState
						</pre>
					),
					rightEntry: (
						<p>
							Concept of owner and delegation of a task does not
							exist in Camunda 8.8.
						</p>
					),
				},
				{
					leftEntry: <pre>(boolean) includeAssignedTasks</pre>,
					rightEntry: (
						<p>Not applicable for the filter in Camunda 8.8.</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) involvedUser
							<br />
							(string) involvedUserExpression
						</pre>
					),
					rightEntry: (
						<p>Not applicable for the filter in Camunda 8.8.</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) name
							<br />
							(string) nameNotEqual
							<br />
							(string) nameLike
							<br />
							(string) nameNotLike
						</pre>
					),
					rightEntry: <p>Not possible in Camunda 8.8.</p>,
				},
				{
					leftEntry: (
						<pre>
							(string) description
							<br />
							(string) descriptionLike
						</pre>
					),
					rightEntry: <p>Not possible in Camunda 8.8.</p>,
				},
				{
					leftEntry: (
						<pre>
							(dateTime) updatedAfter
							<br />
							(dateTime) updatedAfterExpression
						</pre>
					),
					rightEntry: <p>Not possible in Camunda 8.8.</p>,
				},
				{
					leftEntry: (
						<pre>
							(boolean) active
							<br />
							(boolean) suspended
						</pre>
					),
					rightEntry: (
						<p>
							Not yet possible in Camunda 8.8.
							Activating/suspending a process instance is on the
							roadmap of Camunda 8.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(boolean) variableNamesIgnoreCase
							<br />
							(boolean) variableValuesIgnoreCase
						</pre>
					),
					rightEntry: (
						<p>
							No direct mapping. Use advanced filters
							appropriately.
						</p>
					),
				},
				{
					leftEntry: <pre>(string) parentTaskId</pre>,
					rightEntry: (
						<p>Not yet possible in Camunda 8.8. No sub tasks.</p>
					),
				},
				{
					leftEntry: <pre>(boolean) withCommentAttachmentInfo</pre>,
					rightEntry: (
						<p>
							Not yet possible in Camunda 8.8. No comments for
							tasks yet.
						</p>
					),
				},
			],
		},
	},
	{
		origin: {
			path: "/task",
			operation: "post",
		},
		target: {
			path: "/user-tasks/search",
			operation: "post",
		},
		mappedExplanation: (
			<p>
				See <code>Get List</code> endpoint for details.
			</p>
		),
	},
	{
		origin: {
			path: "/task/count",
			operation: "get",
		},
		target: {
			path: "/user-tasks/search",
			operation: "post",
		},
		mappedExplanation: (
			<p>
				See <code>Get List</code> endpoint for details.
			</p>
		),
	},
	{
		origin: {
			path: "/task/count",
			operation: "post",
		},
		target: {
			path: "/user-tasks/search",
			operation: "post",
		},
		mappedExplanation: (
			<p>
				See <code>Get List</code> endpoint for details.
			</p>
		),
	},
	{
		origin: {
			path: "/task/create",
			operation: "post",
		},
		target: {},
		discontinuedExplanation:
			"Manual creation of tasks is not yet available in Camunda 8.8.",
	},
	{
		origin: {
			path: "/task/report/candidate-group-count",
			operation: "get",
		},
		target: {
			path: "/user-tasks/search",
			operation: "post",
		},
		mappedExplanation:
			"Use advanced filters to get all tasks for all candidate groups. The response is not returned per candidate group as in Camunda 7, but all necessary information is present.",
	},
	{
		origin: {
			path: "/task/{id}",
			operation: "delete",
		},
		target: {},
		discontinuedExplanation:
			"Manual deletion of tasks is not yet available in Camunda 8.8.",
	},
	{
		origin: {
			path: "/task/{id}",
			operation: "get",
		},
		target: {
			path: "/user-tasks/{userTaskKey}",
			operation: "get",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) userTaskKey</pre>
							<p>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
			],
		},
	},
	{
		origin: {
			path: "/task/{id}",
			operation: "put",
		},
		target: {
			path: "/user-tasks/{userTaskKey}",
			operation: "patch",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) userTaskKey</pre>
							<p>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
			],
			additionalInfo: (
				<p>
					See <code>Get List</code> endpoint for more details.
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) assignee</pre>,
					rightEntry: (
						<p>
							The assignee cannot be adjusted with this endpoint.
							Use the <code>Assign task</code> endpoint. This
							ensures correct event emission for assignee changes.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) formKey
							<br />
							(object) camundaFormRef
						</pre>
					),
					rightEntry: (
						<p>
							It is not possible to change the form in Camunda
							8.8.
						</p>
					),
				},
				{
					leftEntry: <pre>(string) tenantId</pre>,
					rightEntry: (
						<p>
							It is not possible to change the tenantId of a user
							task in Camunda 8.8.
						</p>
					),
				},
			],
			additionalInfo: (
				<p>
					See <code>Get List</code> endpoint for more details.
				</p>
			),
		},
	},
	{
		origin: {
			path: "/task/{id}/assignee",
			operation: "post",
		},
		target: {
			path: "/user-tasks/{userTaskKey}/assignment",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) userTaskKey</pre>
							<p>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string) userId</pre>,
					rightEntry: <pre>(string) assignee</pre>,
				},
			],
			additionalInfo: (
				<p>
					The Camunda 7 endpoint overrides the assignee. To achieve
					the same behaviour in Camunda 8, set{" "}
					<code>allowOverride</code> to true.
				</p>
			),
		},
	},
	{
		origin: {
			path: "/task/{id}/bpmnError",
			operation: "post",
		},
		target: {},
		discontinuedExplanation:
			"It is not possible to throw a BPMN error from a Camunda user task in Camunda 8.8.",
	},
	{
		origin: {
			path: "/task/{id}/bpmnEscalation",
			operation: "post",
		},
		target: {},
		discontinuedExplanation:
			"It is not possible to throw a BPMN escalation from a Camunda user task in Camunda 8.8.",
	},
	{
		origin: {
			path: "/task/{id}/claim",
			operation: "post",
		},
		target: {
			path: "/user-tasks/{userTaskKey}/assignment",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) userTaskKey</pre>
							<p>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string) userId</pre>,
					rightEntry: <pre>(string) assignee</pre>,
				},
			],
			additionalInfo: (
				<p>
					The Camunda 7 endpoint checks if there is already an
					assignee. To achieve the same behaviour in Camunda 8, set{" "}
					<code>allowOverride</code> to false.
				</p>
			),
		},
	},
	{
		origin: {
			path: "/task/{id}/complete",
			operation: "post",
		},
		target: {
			path: "/user-tasks/{userTaskKey}/completion",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) userTaskKey</pre>
							<p>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(object) variables</pre>,
					rightEntry: <pre>(object) variables</pre>,
				},
			],
			additionalInfo: (
				<p>
					The Camunda 7 endpoint checks if there is already an
					assignee. To achieve the same behaviour in Camunda 8, set{" "}
					<code>allowOverride</code> to false.
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(boolean) withVariablesInReturn</pre>,
					rightEntry: (
						<p>
							This endpoint does not return process variables in
							Camunda 8.8.
						</p>
					),
				},
			],
		},
	},
	{
		origin: {
			path: "/task/{id}/delegate",
			operation: "post",
		},
		target: {},
		discontinuedExplanation:
			"It is not yet possible to delegate a task to a different assignee in Camunda 8.8.",
	},
	{
		origin: {
			path: "/task/{id}/deployed-form",
			operation: "get",
		},
		target: {
			path: "/user-tasks/{userTaskKey}/form",
			operation: "get",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) userTaskKey</pre>
							<p>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
			],
			additionalInfo: (
				<p>
					In Camunda 8, this endpoint returns linked forms. It does
					not support embedded forms.
				</p>
			),
		},
	},
	{
		origin: {
			path: "/task/{id}/form",
			operation: "get",
		},
		target: {
			path: "/user-tasks/{userTaskKey}/form",
			operation: "get",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) userTaskKey</pre>
							<p>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
			],
			additionalInfo: (
				<p>
					In Camunda 8, this endpoint returns linked forms. It does
					not support embedded forms.
				</p>
			),
		},
	},
	{
		origin: {
			path: "/task/{id}/form-variables",
			operation: "get",
		},
		target: {
			path: "/user-tasks/{userTaskKey}/variables/search",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) userTaskKey</pre>
							<p>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
			],
			additionalInfo: (
				<p>
					By not applying any filters, all variables of the user task
					are returned as a list of objects, specifying various
					details.
				</p>
			),
		},
	},
	{
		origin: {
			path: "/task/{id}/rendered-form",
			operation: "get",
		},
		target: {},
		discontinuedExplanation:
			"Redundant in Camunda 8: no Generated Task Form approach.",
	},
	{
		origin: {
			path: "/task/{id}/resolve",
			operation: "post",
		},
		target: {},
		discontinuedExplanation:
			"It is not yet possible to delegate a task to a different assignee in Camunda 8.8, so there is also no need to resolve it.",
	},
	{
		origin: {
			path: "/task/{id}/submit-form",
			operation: "post",
		},
		target: {
			path: "/user-tasks/{userTaskKey}/completion",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) userTaskKey</pre>
							<p>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(object[]) variables</pre>,
					rightEntry: <pre>(object[]) variables</pre>,
				},
			],
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(boolean) withVariablesInReturn</pre>,
					rightEntry: (
						<p>
							This endpoint does not return process variables in
							Camunda 8.8.
						</p>
					),
				},
			],
		},
	},
	{
		origin: {
			path: "/task/{id}/unclaim",
			operation: "post",
		},
		target: {
			path: "/user-tasks/{userTaskKey}/assignee",
			operation: "delete",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) userTaskKey</pre>
							<p>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(object[]) variables</pre>,
					rightEntry: <pre>(object[]) variables</pre>,
				},
			],
		},
	},
];
