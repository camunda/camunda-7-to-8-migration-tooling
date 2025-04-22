export const task_7_23_to_8_8 = [
	{
		origin: {
			path: "/task",
			operation: "get",
		},
		target: {
			path: "/user-tasks/search",
			operation: "post",
		},
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>
									(string) taskId
									<br />
									(string[]) taskIdIn
								</pre>
							</td>
							<td>
								<pre>(string) userTaskKey</pre>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) processInstanceId
									<br />
									(string[]) processInstanceIdIn
								</pre>
							</td>
							<td>
								<pre>(string) processInstanceKey</pre>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) processInstanceId
									<br />
									(string[]) processInstanceIdIn
								</pre>
							</td>
							<td>
								<pre>(string) processInstanceKey</pre>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) processDefinitionId</pre>
							</td>
							<td>
								<pre>(string) processDefinitionKey</pre>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) processDefinitionKey
									<br />
									(string[]) processDefinitionKeyIn
								</pre>
							</td>
							<td>
								<pre>(string) processDefinitionId</pre>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string[]) activityInstanceIdIn</pre>
							</td>
							<td>
								<pre>(string) elementInstanceKey</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string[]) tenantIdIn
									<br />
									(boolean) withoutTenantId
								</pre>
							</td>
							<td>
								<pre>(string) tenantId</pre>
							</td>
						</tr>
						<tr>
							<td>
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
							</td>
							<td>
								<pre>(string*) assignee</pre>
							</td>
						</tr>
						<tr>
							<td>
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
							</td>
							<td>
								<pre>(string*) candidateGroup</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) candidateUser
									<br />
									(string) candidateUserExpression
									<br />
									(boolean) withCandidateUsers
									<br />
									(boolean) withoutCandidateUsers
								</pre>
							</td>
							<td>
								<pre>(string*) candidateUser</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(boolean) assigned
									<br />
									(boolean) unassigned
								</pre>
							</td>
							<td>
								<pre>(string*) assignee</pre>
								<p>Use advanced filters.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) taskDefinitionKey
									<br />
									(string[]) taskDefinitionKeyIn
									<br />
									(string) taskDefinitionKeyLike
								</pre>
							</td>
							<td>
								<pre>(string) elementId</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(integer) priority
									<br />
									(integer) maxPriority
									<br />
									(integer) minPriority
								</pre>
							</td>
							<td>
								<pre>(integer*) priority</pre>
							</td>
						</tr>
						<tr>
							<td>
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
							</td>
							<td>
								<pre>(dateTime*) dueDate</pre>
							</td>
						</tr>
						<tr>
							<td>
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
									(dateTime)
									followUpBeforeOrNotExistentExpression
								</pre>
							</td>
							<td>
								<pre>(dateTime*) followUpDate</pre>
							</td>
						</tr>
						<tr>
							<td>
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
							</td>
							<td>
								<pre>(dateTime*) creationDate</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object[]*) taskVariables</pre>
							</td>
							<td>
								<pre>(object[]*) localVariables</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object[]*) processVariables</pre>
							</td>
							<td>
								<pre>(object[]*) processInstanceVariables</pre>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					<code>...Like</code> and <code>...In</code> parameters are
					grouped together with the parameter they relate to.
				</p>
				<p>
					Asterisks signify that an advanced filter can be applied,
					similar to a unit test (
					<code>$eq, $neq, $in, $like with wildcards,...</code>).
				</p>
			</>
		),
		notPossible: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Explanation</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>
									(string) processInstanceBusinessKey
									<br />
									(string)
									processInstanceBusinessKeyExpression
									<br />
									(string[]) processInstanceBusinessKeyIn
									<br />
									(string) processInstanceBusinessKeyLike
									<br />
									(string)
									processInstanceBusinessKeyLikeExpression
								</pre>
							</td>
							<td>
								<p>
									No businessKey in Camunda 8.8. Planned for
									Camunda 8.9.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) processDefinitionName
									<br />
									(string) processDefinitionNameLike
								</pre>
							</td>
							<td>
								<p>Not possible in Camunda 8.8.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) executionId</pre>
							</td>
							<td>
								<p>
									Not possible in Camunda 8.8. In Camunda 7,
									the executionId is used to differentiate
									between parallel executions in one process
									instance.
								</p>
								<p>
									In Camunda 8, unique identifiers for a user
									task are the <code>userTaskKey</code> and{" "}
									<code>elementInstanceKey</code>.
								</p>
							</td>
						</tr>
						<tr>
							<td>
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
							</td>
							<td>
								<p>No CMMN in Camunda 8.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) owner
									<br />
									(string) ownerExpression
									<br />
									(string) delegationState
								</pre>
							</td>
							<td>
								<p>
									Concept of owner and delegation of a task
									does not exist in Camunda 8.8.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(boolean) includeAssignedTasks</pre>
							</td>
							<td>
								<p>
									Not applicable for the filter in Camunda
									8.8.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) involvedUser
									<br />
									(string) involvedUserExpression
								</pre>
							</td>
							<td>
								<p>
									Not applicable for the filter in Camunda
									8.8.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) name
									<br />
									(string) nameNotEqual
									<br />
									(string) nameLike
									<br />
									(string) nameNotLike
								</pre>
							</td>
							<td>
								<p>Not possible in Camunda 8.8.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) description
									<br />
									(string) descriptionLike
								</pre>
							</td>
							<td>
								<p>Not possible in Camunda 8.8.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(dateTime) updatedAfter
									<br />
									(dateTime) updatedAfterExpression
								</pre>
							</td>
							<td>
								<p>Not possible in Camunda 8.8.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(boolean) active
									<br />
									(boolean) suspended
								</pre>
							</td>
							<td>
								<p>
									Not possible in Camunda 8.8.
									Activating/suspending a process instance is
									on the roadmap of Camunda 8.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(boolean) variableNamesIgnoreCase
									<br />
									(boolean) variableValuesIgnoreCase
								</pre>
							</td>
							<td>
								<p>
									No direct mapping. Use advanced filters
									appropriately.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) parentTaskId</pre>
							</td>
							<td>
								<p>
									Not possible in Camunda 8.8. No sub tasks.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(boolean) withCommentAttachmentInfo</pre>
							</td>
							<td>
								<p>
									Not possible in Camunda 8.8. No comments for
									tasks yet.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
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
		explanation: (
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
		explanation: (
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
		explanation: (
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
		explanation: "There is no manual creation of tasks in Camunda 8.8.",
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
		explanation:
			"Use advanced filters to get all tasks for all candidate groups. The response is not returned per candidate group as in Camunda 7, but all necessary information is present.",
	},
	{
		origin: {
			path: "/task/{id}",
			operation: "delete",
		},
		target: {},
		explanation: "There is no manual deletion of tasks in Camunda 8.8.",
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
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) userTaskKey</pre>
								<p>
									See{" "}
									<a href="#key-to-id">
										Camunda 7 key → Camunda 8 id
									</a>
								</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
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
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) userTaskKey</pre>
								<p>
									See{" "}
									<a href="#key-to-id">
										Camunda 7 key → Camunda 8 id
									</a>
								</p>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					See <code>Get List</code> endpoint for more details.
				</p>
			</>
		),
		notPossible: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Explanation</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) assignee</pre>
							</td>
							<td>
								<p>
									The assignee cannot be adjusted with this
									endpoint, use the Assign task endpoint. This
									ensures correct event emission for assignee
									changes.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) formKey
									<br />
									(object) camundaFormRef
								</pre>
							</td>
							<td>
								<p>
									It is not possible to change the form in
									Camunda 8.8.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) tenantId</pre>
							</td>
							<td>
								<p>
									It is not possible to change the tenantId of
									a user task in Camunda 8.8.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					See <code>Get List</code> endpoint for more details.
				</p>
			</>
		),
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
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) userTaskKey</pre>{" "}
								<p>
									See{" "}
									<a href="#key-to-id">
										Camunda 7 key → Camunda 8 id
									</a>
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) userId</pre>
							</td>
							<td>
								<pre>(string) assignee</pre>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					The Camunda 7 endpoint overrides the assignee. To achieve
					the same behaviour in Camunda 8, set{" "}
					<code>allowOverride</code> to true.
				</p>
			</>
		),
	},
	{
		origin: {
			path: "/task/{id}/bpmnError",
			operation: "post",
		},
		target: {},
		explanation:
			"It is not possible to throw a BPMN error from a Camunda user task in Camunda 8.8.",
	},
	{
		origin: {
			path: "/task/{id}/bpmnEscalation",
			operation: "post",
		},
		target: {},
		explanation:
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
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) userTaskKey</pre>{" "}
								<p>
									See{" "}
									<a href="#key-to-id">
										Camunda 7 key → Camunda 8 id
									</a>
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) userId</pre>
							</td>
							<td>
								<pre>(string) assignee</pre>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					The Camunda 7 endpoint checks if there is already an
					assignee. To achieve the same behaviour in Camunda 8, set{" "}
					<code>allowOverride</code> to false.
				</p>
			</>
		),
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
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) userTaskKey</pre>{" "}
								<p>
									See{" "}
									<a href="#key-to-id">
										Camunda 7 key → Camunda 8 id
									</a>
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object) variables</pre>
							</td>
							<td>
								<pre>(object) variables</pre>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					The Camunda 7 endpoint checks if there is already an
					assignee. To achieve the same behaviour in Camunda 8, set{" "}
					<code>allowOverride</code> to false.
				</p>
			</>
		),
		notPossible: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Explanation</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(boolean) withVariablesInReturn</pre>
							</td>
							<td>
								<p>
									This endpoint does not return process
									variables in Camunda 8.8.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
	},
	{
		origin: {
			path: "/task/{id}/delegate",
			operation: "post",
		},
		target: {},
		explanation: "It is not possible to delegate a task in Camunda 8.8.",
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
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) userTaskKey</pre>{" "}
								<p>
									See{" "}
									<a href="#key-to-id">
										Camunda 7 key → Camunda 8 id
									</a>
								</p>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					In Camunda 8, this endpoint returns linked forms. It does
					not support embedded forms.
				</p>
			</>
		),
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
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) userTaskKey</pre>{" "}
								<p>
									See{" "}
									<a href="#key-to-id">
										Camunda 7 key → Camunda 8 id
									</a>
								</p>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					In Camunda 8, this endpoint returns linked forms. It does
					not support embedded forms.
				</p>
			</>
		),
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
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) userTaskKey</pre>{" "}
								<p>
									See{" "}
									<a href="#key-to-id">
										Camunda 7 key → Camunda 8 id
									</a>
								</p>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					By not applying any filters, all variables of the user task
					are returned as a list of objects, specifying various
					details.
				</p>
			</>
		),
	},
	{
		origin: {
			path: "/task/{id}/rendered-form",
			operation: "get",
		},
		target: {},
		explanation: "Redundant in Camunda 8: no Generated Task Form approach.",
	},
	{
		origin: {
			path: "/task/{id}/resolve",
			operation: "post",
		},
		target: {},
		explanation:
			"Concept of owner and delegation of a task does not exist in Camunda 8.8.",
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
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) userTaskKey</pre>{" "}
								<p>
									See{" "}
									<a href="#key-to-id">
										Camunda 7 key → Camunda 8 id
									</a>
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object[]) variables</pre>
							</td>
							<td>
								<pre>(object[]) variables</pre>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
		notPossible: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Explanation</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(boolean) withVariablesInReturn</pre>
							</td>
							<td>
								<p>
									This endpoint does not return process
									variables in Camunda 8.8.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
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
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) userTaskKey</pre>{" "}
								<p>
									See{" "}
									<a href="#key-to-id">
										Camunda 7 key → Camunda 8 id
									</a>
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object[]) variables</pre>
							</td>
							<td>
								<pre>(object[]) variables</pre>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
	},
];
