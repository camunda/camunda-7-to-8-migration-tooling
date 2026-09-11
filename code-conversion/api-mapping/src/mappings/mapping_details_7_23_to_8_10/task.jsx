/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
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
							<pre>(string) filter.userTaskKey</pre>
							<p>
								For <code>taskIdIn</code>, use the advanced{" "}
								<code>$or</code> filter with one{" "}
								<code>userTaskKey</code> clause per source ID, or
								issue separate searches. A single{" "}
								<code>userTaskKey</code> matches only one task.
							</p>
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
							<pre>
								(string) filter.processInstanceKey
								<br />
								(string[]) filter.processInstanceKey.$in
							</pre>
							<p>
								Use <code>processInstanceKey</code> for the
								single-value source and the advanced{" "}
								<code>processInstanceKey.$in</code> filter for
								<code>processInstanceIdIn</code>.
							</p>
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
							<pre>(string) filter.processDefinitionKey</pre>
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
							<pre>
								(string) filter.processDefinitionId
								<br />
								(string[]) filter.processDefinitionId.$in
							</pre>
							<p>
								Use <code>processDefinitionId</code> for the
								single-value source and the advanced{" "}
								<code>processDefinitionId.$in</code> filter for
								<code>processDefinitionKeyIn</code>.
							</p>
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
					rightEntry: (
						<>
							<pre>(string) filter.elementInstanceKey</pre>
							<p>
								<code>elementInstanceKey</code> is scalar-only.
								For <code>activityInstanceIdIn</code>, use the
								advanced <code>$or</code> filter with one{" "}
								<code>elementInstanceKey</code> clause per source
								ID, or issue separate searches.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(string[]) tenantIdIn
							<br />
							(boolean) withoutTenantId
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(string) filter.tenantId
								<br />
								(string[]) filter.tenantId.$in
							</pre>
							<p>
								Map <code>tenantIdIn</code> to{" "}
								<code>filter.tenantId.$in</code>.{" "}
								<code>withoutTenantId</code> has no direct
								C8 equivalent; map a non-tenanted C7 task to
								the C8 deployment's explicit default tenant,
								typically <code>&lt;default&gt;</code>.
							</p>
						</>
					),
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
					rightEntry: <pre>(string*) filter.assignee</pre>,
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
					rightEntry: <pre>(string*) filter.candidateGroup</pre>,
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
					rightEntry: (
						<>
							<pre>(string*) filter.candidateUser</pre>
							<p>
								In Camunda 7, <code>candidateUser</code> also
								matches tasks offered to any group the user
								belongs to. Resolve the user's group IDs and use
								an advanced <code>$or</code> filter with the
								direct <code>candidateUser</code> clause plus
								one <code>candidateGroup</code> clause per group,
								or issue separate searches and deduplicate the
								results.
							</p>
						</>
					),
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
							<pre>(string*) filter.assignee</pre>
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
					rightEntry: (
						<>
							<pre>(string) filter.elementId</pre>
							<p>
								Map <code>taskDefinitionKey</code> to
								<code> filter.elementId</code>. For{" "}
								<code>taskDefinitionKeyIn</code>, use one
								<code> $or</code> clause per exact element ID.
								<code> taskDefinitionKeyLike</code> has no
								direct server-side equivalent; filter it
								client-side.
							</p>
						</>
					),
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
					rightEntry: <pre>(integer*) filter.priority</pre>,
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
							(boolean) withoutDueDate
						</pre>
					),
					rightEntry: <pre>(dateTime*) filter.dueDate</pre>,
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
					rightEntry: <pre>(dateTime*) filter.followUpDate</pre>,
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
					rightEntry: <pre>(dateTime*) filter.creationDate</pre>,
				},
				{
					leftEntry: <pre>(object[]*) taskVariables</pre>,
					rightEntry: <pre>(object[]*) filter.localVariables</pre>,
				},
				{
					leftEntry: <pre>(object[]*) processVariables</pre>,
					rightEntry: <pre>(object[]*) filter.processInstanceVariables</pre>,
				},
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
						<>
							<pre>(string*) filter.businessId</pre>
							<p>
								This filter only applies to user tasks created in
								Camunda 8.10 or later.
							</p>
						</>
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
						</pre>
					),
					rightEntry: (
						<>
							<pre>(string*) filter.name</pre>
							<p>
								Set <code>filter.name</code> to the exact name for{" "}
								<code>name</code>. Use the advanced{" "}
								<code>$neq</code> and <code>$like</code> operators
								for <code>nameNotEqual</code> and{" "}
								<code>nameLike</code>. These filters only work for
								data created with Camunda 8.8 and onwards; instances
								from prior versions cannot be found.
							</p>
						</>
					),
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
						applied, similar to a unary test (
						<code>$eq, $neq, $in, $like with wildcards,...</code>).
					</p>
					<p>
						Camunda 7 <code>/task</code> queries return runtime tasks
						only, while the Camunda 8.10 search also returns{" "}
						<code>COMPLETED</code> and <code>CANCELED</code> tasks.
						When no state criterion is supplied, set{" "}
						<code>filter.state.$in</code> to{" "}
						<code>
							["CREATING", "CREATED", "ASSIGNING", "UPDATING",
							"FAILED",
							"COMPLETING", "CANCELING"]
						</code>{" "}
						to preserve the runtime-only result set.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(string) processDefinitionName
							<br />
							(string) processDefinitionNameLike
						</pre>
					),
					rightEntry: <p>Not possible in Camunda 8.10.</p>,
				},
				{
					leftEntry: <pre>(string) executionId</pre>,
					rightEntry: (
						<>
							<p>
								Not yet possible in Camunda 8.10. In Camunda 7,
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
							exist in Camunda 8.10.
						</p>
					),
				},
				{
					leftEntry: <pre>(boolean) includeAssignedTasks</pre>,
					rightEntry: (
						<p>Not applicable for the filter in Camunda 8.10.</p>
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
						<p>Not applicable for the filter in Camunda 8.10.</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) nameNotLike
						</pre>
					),
					rightEntry: (
						<p>
							There is no direct filter operator for{" "}
							<code>nameNotLike</code> in Camunda 8.10.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) description
							<br />
							(string) descriptionLike
						</pre>
					),
					rightEntry: <p>Not possible in Camunda 8.10.</p>,
				},
				{
					leftEntry: (
						<pre>
							(dateTime) updatedAfter
							<br />
							(dateTime) updatedAfterExpression
						</pre>
					),
					rightEntry: <p>Not possible in Camunda 8.10.</p>,
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
							The Camunda 8.10 Search user tasks endpoint does not
							provide a filter for the suspension state of the
							parent process instance. Process instances can be
							filtered by <code>SUSPENDED</code> using the Search
							process instances endpoint.
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
						<p>Not yet possible in Camunda 8.10. No sub tasks.</p>
					),
				},
				{
					leftEntry: <pre>(boolean) withCommentAttachmentInfo</pre>,
					rightEntry: (
						<p>
							Not yet possible in Camunda 8.10. No comments for
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
			"Manual creation of tasks is not yet available in Camunda 8.10.",
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
			"Manual deletion of tasks is not yet available in Camunda 8.10.",
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
				{
					leftEntry: (
						<pre>
							(dateTime) due
							<br />
							(dateTime) followUp
							<br />
							(integer) priority
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(dateTime) changeset.dueDate
								<br />
								(dateTime) changeset.followUpDate
								<br />
								(integer) changeset.priority
							</pre>
							<p>
								Wrap these fields in the request body's{" "}
								<code>changeset</code> object. The{" "}
								<code>priority</code> value must be between 0 and
								100; do not send these fields at the top level.
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
							8.10.
						</p>
					),
				},
				{
					leftEntry: <pre>(string) tenantId</pre>,
					rightEntry: (
						<p>
							It is not possible to change the tenantId of a user
							task in Camunda 8.10.
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
			"It is not possible to throw a BPMN error from a Camunda user task in Camunda 8.10.",
	},
	{
		origin: {
			path: "/task/{id}/bpmnEscalation",
			operation: "post",
		},
		target: {},
		discontinuedExplanation:
			"It is not possible to throw a BPMN escalation from a Camunda user task in Camunda 8.10.",
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
					Camunda 7 <code>variables</code> entries are{" "}
					<code>VariableValueDto</code> wrappers. Unwrap each entry and
					send only its <code>value</code> as the raw JSON value in the
					Camunda 8.10 completion request; do not copy the{" "}
					<code>type</code> or <code>valueInfo</code> metadata.
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
							Camunda 8.10.
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
			"It is not yet possible to delegate a task to a different assignee in Camunda 8.10.",
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
			path: "/user-tasks/{userTaskKey}/effective-variables/search",
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
					details. Form-field types, defaults, and other form metadata
					are not applied by this variable search. Include{" "}
					<code>?truncateValues=false</code> in the request to return
					complete variable values.
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) variableNames</pre>,
					rightEntry: (
						<p>
							The Camunda 8.10 filter accepts one exact variable
							name per request. For a comma-separated list,
							issue one search per name or fetch all pages and
							filter the results client-side.
						</p>
					),
				},
				{
					leftEntry: <pre>(boolean) deserializeValues</pre>,
					rightEntry: (
						<p>
							There is no equivalent in Camunda 8.10. The
							effective-variable search returns JSON values and
							does not support Camunda 7 server-side Java
							deserialization or serialized-value responses.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(object) form field types
							<br />
							(object) form field default values
						</pre>
					),
					rightEntry: (
						<p>
							Camunda 8 returns raw effective variables rather than
							applying Camunda 7 form-field conversion and default
							value rules. Migrate that form behavior separately.
						</p>
					),
				},
			],
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
			"It is not yet possible to delegate a task to a different assignee in Camunda 8.10, so there is also no need to resolve it.",
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
					leftEntry: <pre>(object) variables</pre>,
					rightEntry: <pre>(object) variables</pre>,
				},
			],
			additionalInfo: (
				<p>
					Camunda 7 <code>variables</code> entries are{" "}
					<code>VariableValueDto</code> wrappers. Unwrap each entry and
					send only its <code>value</code> as the raw JSON value in the
					Camunda 8.10 completion request; do not copy the{" "}
					<code>type</code> or <code>valueInfo</code> metadata.
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
							Camunda 8.10.
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
			],
		},
	},
];
