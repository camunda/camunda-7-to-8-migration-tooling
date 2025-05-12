export const process_instance_7_23_to_8_8 = [
	{
		origin: {
			path: "/process-instance",
			operation: "get",
		},
		target: {
			path: "/process-instances/search",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string[]) processInstanceIds</pre>,
					rightEntry: (
						<>
							<pre>(string*) processInstanceKey</pre>
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
							<pre>(string*) processDefinitionKey</pre>
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
							<br />
							(string[]) processDefinitionKeyNotIn
						</pre>
					),
					rightEntry: (
						<>
							<pre>(string*) processDefinitionId</pre>
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
					leftEntry: <pre>(string) superProcessInstance</pre>,
					rightEntry: <pre>(string*) parentProcessInstanceKey</pre>,
				},
				{
					leftEntry: <pre>(boolean) rootProcessInstances</pre>,
					rightEntry: (
						<>
							<pre>(string*) parentFlowNodeInstanceKey</pre>
							<p>
								Check existence of parentFlowNodeInstanceKey to
								infer the process being a root process instance
								or not.
							</p>
						</>
					),
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
						<>
							<pre>(string*) state</pre>
							<p>
								<code>suspended</code> is not applicable to
								Camunda 8.8.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(boolean) withIncident
							<br />
							(string) incidentMessage
							<br />
							(string) incidentMessageLike
						</pre>
					),
					rightEntry: (
						<pre>
							(boolean) hasIncident
							<br />
							(string*) errorMessage
						</pre>
					),
				},
				{
					leftEntry: (
						<pre>
							(string[]) tenantIdIn
							<br />
							(boolean) withoutTenantId
							<br />
							(boolean) processDefinitionWithoutTenantId
						</pre>
					),
					rightEntry: <pre>(string*) tenantId</pre>,
				},
				{
					leftEntry: (
						<pre>
							(map) variables
							<br />
							(boolean) variableNamesIgnoreCase
							<br />
							(boolean) variableValuesIgnoreCase
						</pre>
					),
					rightEntry: <pre>(object[]*) variables</pre>,
				},
			],
			additionalInfo: (
				<p>
					Asterisks signify that an advanced filter can be applied,
					similar to a unit test (
					<code>$eq, $neq, $in, $like with wildcards,...</code>).
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) deploymentId</pre>,
					rightEntry: (
						<p>
							While deployments are assigned a deploymentKey,
							deployments do not represent resources that can be
							searched for or otherwise used for filtering.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) businessKey
							<br />
							(string) businessKeyLike
						</pre>
					),
					rightEntry: (
						<p>
							No businessKey in Camunda 8.8. Planned for Camunda
							8.9.
						</p>
					),
				},
				{
					leftEntry: <pre>(string) subProcessInstance</pre>,
					rightEntry: (
						<p>
							It is not possible to find all process instances
							that have a process instance with a specific
							processInstanceKey as a subprocess. Instead use the
							processInstanceKey to get the process instance and
							look up the parentProcessInstanceKey.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) caseInstanceId
							<br />
							(string) superCaseInstance
							<br />
							(string) subCaseInstance
						</pre>
					),
					rightEntry: <p>No CMMN in Camunda 8.8.</p>,
				},
				{
					leftEntry: (
						<pre>
							(string) incidentId
							<br />
							(string) incidentType
						</pre>
					),
					rightEntry: (
						<p>
							It is not possible to filter by incidentId or
							incidentType. Use the POST /incidents/search
							endpoint instead to find all process instances
							related to specific incident details.
						</p>
					),
				},
				{
					leftEntry: <pre>(string[]) activityIdIn</pre>,
					rightEntry: (
						<p>
							It is not possible to filter by a list of possible
							flowNodeInstanceIds. Use the POST
							/flownode-instances/search endpoint instead to find
							all process instances related to specific flownode
							details.
						</p>
					),
				},
				{
					leftEntry: <pre>(boolean) leafProcessInstances</pre>,
					rightEntry: (
						<p>
							It is not possible to find process instances that
							have no active subprocess instance in Camunda 8.8.
						</p>
					),
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/process-instance",
			operation: "post",
		},
		target: {
			path: "/process-instances/search",
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
			path: "/process-instance/count",
			operation: "get",
		},
		target: {
			path: "/process-instances/search",
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
			path: "/process-instance/count",
			operation: "post",
		},
		target: {
			path: "/process-instances/search",
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
			path: "/process-instance/delete",
			operation: "post",
		},
		target: {
			path: "/process-instances/batch-operations/cancellation",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string[]) processInstanceIds</pre>,
					rightEntry: (
						<>
							<pre>(string*) processDefinitionKey</pre>
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
					Asterisks signify that an advanced filter can be applied,
					similar to a unit test (
					<code>$eq, $neq, $in, $like with wildcards,...</code>).
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) deleteReason</pre>,
					rightEntry: (
						<p>
							It is not possible to add a delete reason in Camunda
							8.8.
						</p>
					),
				},
				{
					leftEntry: <pre>(boolean) skipCustomListeners</pre>,
					rightEntry: (
						<p>
							Not yet possible in Camunda 8.8. It's not possible to
							skip execution listeners for activities ended as
							part of this request.
						</p>
					),
				},
				{
					leftEntry: <pre>(boolean) skipSubprocesses</pre>,
					rightEntry: (
						<p>
							Subprocesses are not automatically deleted by this
							request.
						</p>
					),
				},
				{
					leftEntry: <pre>(boolean) skipIoMappings</pre>,
					rightEntry: (
						<p>
							Not yet possible in Camunda 8.8. It's not possible to
							skip input/output mappings for activities ended as
							part of this request.
						</p>
					),
				},
				{
					leftEntry: <pre>(object) processInstanceQuery</pre>,
					rightEntry: <p>See Get List endpoint for details.</p>,
				},
				{
					leftEntry: <pre>(object) historicProcessInstanceQuery</pre>,
					rightEntry: (
						<p>
							See{" "}
							<strong>
								Delete Async Historic Query Based (POST)
							</strong>{" "}
							for details.
						</p>
					),
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/process-instance/delete-historic-query-based",
			operation: "post",
		},
		target: {
			path: "/process-instances/batch-operations/cancellation",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(dateTime) startedBefore
							<br />
							(dateTime) startedAfter
							<br />
							(dateTime) finishedBefore
							<br />
							(dateTime) finishedAfter
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(dateTime*) startDate
								<br />
								(dateTime*) endDate
							</pre>
							<p>
								Use advanced filters on startDate and endDate.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(boolean) active
							<br />
							(boolean) suspended
							<br />
							(boolean) completed
						</pre>
					),
					rightEntry: (
						<>
							<pre>(object*) state</pre>
							<p>
								<code>suspended</code> is not applicable to
								Camunda 8.8.
							</p>
						</>
					),
				},
			],
			additionalInfo: (
				<>
					<p>
						Asterisks signify that an advanced filter can be
						applied, similar to a unit test (
						<code>$eq, $neq, $in, $like with wildcards,...</code>).
					</p>
					<p>
						See Get List endpoint for details on most fields of the
						historicProcessInstanceQuery.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(boolean) finished
							<br />
							(boolean) unfinished
						</pre>
					),
					rightEntry: (
						<p>
							These flags specify if a process instance has been
							completed or terminated regularly, or has been
							canceled by other means. This differentiation does
							not exist in Camunda 8.8.
						</p>
					),
				},
				{
					leftEntry: <pre>(boolean) withRootIncidents</pre>,
					rightEntry: <p>Not possible in Camunda 8.8.</p>,
				},
				{
					leftEntry: (
						<pre>
							(string[]) incidentIdIn
							<br />
							(string) incidentType
							<br />
							(string) incidentStatus
						</pre>
					),
					rightEntry: <p>Not possible in Camunda 8.8.</p>,
				},
				{
					leftEntry: (
						<pre>
							(dateTime) executedActivityAfter
							<br />
							(dateTime) executedActivityBefore
							<br />
							(dateTime) executedJobAfter
							<br />
							(dateTime) executedJobBefore
						</pre>
					),
					rightEntry: (
						<p>These filters are not available in Camunda 8.8.</p>
					),
				},
				{
					leftEntry: <pre>(string) startedBy</pre>,
					rightEntry: <p>This is not possible in Camunda 8.8.</p>,
				},
				{
					leftEntry: (
						<pre>
							(string[]) executedActivityIdIn
							<br />
							(string[]) activeActivityIdIn
						</pre>
					),
					rightEntry: <p>This is not possible in Camunda 8.8.</p>,
				},
				{
					leftEntry: (
						<pre>
							(boolean) externallyTerminated
							<br />
							(boolean) internallyTerminated
						</pre>
					),
					rightEntry: <p>This is not possible in Camunda 8.8.</p>,
				},
			],
			additionalInfo: (
				<p>
					Asterisks signify that an advanced filter can be applied,
					similar to a unit test (
					<code>$eq, $neq, $in, $like with wildcards,...</code>).
				</p>
			),
		},
	},
	{
		origin: {
			path: "/process-instance/job-retries",
			operation: "post",
		},
		target: {},
		discontinuedExplanation: (
			<p>
				There is no batch endpoint yet to update retries for multiple jobs
				at once. You can update each job with the Update job endpoint.
			</p>
		),
	},
	{
		origin: {
			path: "/process-instance/job-retries-historic-query-based",
			operation: "post",
		},
		target: {},
		discontinuedExplanation: (
			<p>
				There is no batch endpoint yet to update retries for multiple jobs
				at once. You can update each job with the Update job endpoint.
			</p>
		),
	},
	{
		origin: {
			path: "/process-instance/message-async",
			operation: "post",
		},
		target: {
			path: "/messages/publication",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) messageName</pre>,
					rightEntry: <pre>(string) name</pre>,
				},
				{
					leftEntry: <pre>(object) variables</pre>,
					rightEntry: <pre>(object) variables</pre>,
				},
			],
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(string[]) processInstanceIds
							<br />
							(object) processInstanceQuery
							<br />
							(object) historicProcessInstanceQuery
						</pre>
					),
					rightEntry: (
						<p>
							In Camunda 8, a message is published to a cluster
							and tenant. A restriction to specific process
							instances is not possible.
						</p>
					),
				},
			],
		},
	},
	{
		origin: {
			path: "/process-instance/suspended",
			operation: "put",
		},
		target: {},
		roadmapExplanation:
			"Not yet possible in Camunda 8.8. Activating/suspending process instances is on the roadmap.",
	},
	{
		origin: {
			path: "/process-instance/suspended-async",
			operation: "post",
		},
		target: {},
		roadmapExplanation:
			"Not yet possible in Camunda 8.8. Activating/suspending process instances is on the roadmap.",
	},
	{
		origin: {
			path: "/process-instance/variables-async",
			operation: "post",
		},
		target: {
			path: "/element-instances/{elementInstanceKey}/variables",
			operation: "put",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string[]) processInstanceIds</pre>,
					rightEntry: (
						<>
							<pre>(string) elementInstanceKey</pre>
							<p>
								In Camunda 8.8, variables can be updated for one
								particular scope. This can be a process
								instance. In this case the{" "}
								<code>elementInstanceKey</code> is the
								processInstanceKey.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(object) variables</pre>,
					rightEntry: <pre>(object) variables</pre>,
				},
			],
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(object) processInstanceQuery
							<br />
							(object) historicProcessInstanceQuery
						</pre>
					),
					rightEntry: (
						<p>
							In Camunda 8.8, variables can be updated for one
							particular scope, not for a batch of process
							instances.
						</p>
					),
				},
			],
		},
	},
	{
		origin: {
			path: "/process-instance/{id}",
			operation: "delete",
		},
		target: {
			path: "/process-instances/{processInstanceKey}/cancellation",
			operation: "post",
		},

		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
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
			],
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(boolean) skipCustomListeners
							<br />
							(boolean) skipIoMappings
						</pre>
					),
					rightEntry: <p>Not possible in Camunda 8.8.</p>,
				},
				{
					leftEntry: <pre>(boolean) skipSubprocesses</pre>,
					rightEntry: (
						<p>
							Not yet possible in Camunda 8.8. Subprocesses are also
							canceled.
						</p>
					),
				},
				{
					leftEntry: <pre>(boolean) failIfNotExists</pre>,
					rightEntry: (
						<p>
							Endpoint answers with <code>404</code> if the
							process instance is not found.
						</p>
					),
				},
			],
		},
	},
	{
		origin: {
			path: "/process-instance/{id}",
			operation: "get",
		},
		target: {
			path: "/process-instances/{processInstanceKey}",
			operation: "get",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
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
			],
		},
	},
	{
		origin: {
			path: "/process-instance/{id}/activity-instances",
			operation: "get",
		},
		target: {},
		mappedExplanation: (
			<p>
				Not yet possible in Camunda 8.8. You can retrieve all active flow
				nodes of a specific process instance using the{" "}
				<code>POST Search flow node instances</code> endpoint. But this
				endpoint does not provide a tree structure or information about
				the flow node hierarchy.
			</p>
		),
	},
	{
		origin: {
			path: "/process-instance/{id}/comment",
			operation: "get",
		},
		target: {},
		roadmapExplanation: (
			<p>
				Not yet possible in Camunda 8.8. Adding comments to user tasks is on
				the roadmap of Camunda 8.
			</p>
		),
	},
	{
		origin: {
			path: "/process-instance/{id}/modification",
			operation: "post",
		},
		target: {
			path: "/process-instances/{processInstanceKey}/modification",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
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
					leftEntry: <pre>(object[]) instructions</pre>,
					rightEntry: (
						<pre>
							(object[]) activateInstructions <br />
							(object[]) terminateInstructions
						</pre>
					),
				},
				{
					leftEntry: <pre>(string) instructions[].type</pre>,
					rightEntry: (
						<p>
							<code>cancel</code> and <code>start...</code> types
							are mapped to <code>terminateInstructions</code> and{" "}
							<code>activateInstructions</code>.
						</p>
					),
				},
				{
					leftEntry: <pre>(object) instructions[].variables</pre>,
					rightEntry: (
						<pre>
							(object) activateInstructions[].variableInstructions
							<br />
							(object)
							terminateInstructions[].variableInstructions
						</pre>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) instructions[].activityId
							<br />
							(string) instructions[].transitionId
						</pre>
					),
					rightEntry: (
						<pre>
							(object) activateInstructions[].elementId
							<br />
							(object) terminateInstructions[].elementId
						</pre>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) instructions[].ancestorActivityInstanceId
						</pre>
					),
					rightEntry: (
						<pre>
							(object)
							activateInstructions[].ancestorElementInstanceKey
							<br />
							(object)
							terminateInstructions[].ancestorElementInstanceKey
						</pre>
					),
				},
			],
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(string) instructions[].activityInstanceId
							<br />
							(string) instructions[].transitionInstanceId
						</pre>
					),
					rightEntry: (
						<p>
							In Camunda 8.8, the element is targeted via the{" "}
							<code>elementId</code>.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(boolean) skipCustomListeners
							<br />
							(boolean) skipIoMappings
						</pre>
					),
					rightEntry: <p>Not possible in Camunda 8.8.</p>,
				},
				{
					leftEntry: <pre>(string) annotation</pre>,
					rightEntry: (
						<p>
							Not yet possible in Camunda 8.8. A integer{" "}
							<code>operationReference</code> can be added to the
							request.
						</p>
					),
				},
			],
		},
	},
	{
		origin: {
			path: "/process-instance/{id}/modification-async",
			operation: "post",
		},
		target: {
			path: "/process-instances/{processInstanceKey}/modification",
			operation: "post",
		},
		mappedExplanation: (
			<p>
				See <code>Modify Process Instance Execution State</code>{" "}
				endpoint for details.
			</p>
		),
	},
	{
		origin: {
			path: "/process-instance/{id}/suspended",
			operation: "put",
		},
		target: {},
		roadmapExplanation:
			"Not yet possible in Camunda 8.8. Activating/suspending process instances is on the roadmap.",
	},
	{
		origin: {
			path: "/process-instance/{id}/variables",
			operation: "get",
		},
		target: {
			path: "/variables/search",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
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
			],
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(boolean) deserializeValues</pre>,
					rightEntry: <p>Not applicable, only JSON in Camunda 8.</p>,
				},
			],
		},
	},
	{
		origin: {
			path: "/process-instance/{id}/variables",
			operation: "post",
		},
		target: {
			path: "/element-instances/{elementInstanceKey}/variables",
			operation: "put",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) elementInstanceKey</pre>
							<p>
								The <code>elementInstanceKey</code> can be the{" "}
								<code>processInstanceKey</code> that corresponds
								to <code>id</code>.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(object[]) modifications</pre>,
					rightEntry: <pre>(object[]) variables</pre>,
				},
			],
			additionalInfo: null,
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string[]) deletions</pre>,
					rightEntry: (
						<p>
							It is not possible to delete variables in Camunda
							8.8. But you can set them to <code>null</code> or an
							empty string.
						</p>
					),
				},
			],
			additionalInfo: null,
		},
	},
	{
		origin: {
			path: "/process-instance/{id}/variables/{varName}",
			operation: "delete",
		},
		target: {},
		mappedExplanation: (
			<p>
				In Camunda 8.8, there is no endpoint to delete a process
				variable. You can update a variable to null or an empty string
				using the <code>PUT Update element instance variables</code>{" "}
				endpoint.
			</p>
		),
	},
	{
		origin: {
			path: "/process-instance/{id}/variables/{varName}",
			operation: "get",
		},
		target: {
			path: "/variables/search",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
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
					leftEntry: <pre>(string) varName</pre>,
					rightEntry: <pre>(string) name</pre>,
				},
			],
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(boolean) deserializeValues</pre>,
					rightEntry: <p>Not applicable, only JSON in Camunda 8.</p>,
				},
			],
		},
	},
	{
		origin: {
			path: "/process-instance/{id}/variables/{varName}",
			operation: "put",
		},
		target: {
			path: "/element-instances/{elementInstanceKey}/variables",
			operation: "put",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) elementInstanceKey</pre>
							<p>
								In Camunda 8.8, variables can be updated for one
								particular scope. This can be a process
								instance. In this case the{" "}
								<code>elementInstanceKey</code> is the{" "}
								<code>processInstanceKey</code>.
							</p>
						</>
					),
				},
			],
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) varName</pre>,
					rightEntry: (
						<p>
							Set the <code>varName</code> as one of the variables
							to update.
						</p>
					),
				},
			],
		},
	},
	{
		origin: {
			path: "/process-instance/{id}/variables/{varName}/data",
			operation: "get",
		},
		target: {
			path: "/documents/{documentId}",
			operation: "get",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) varName</pre>,
					rightEntry: <pre>(string) documentId</pre>,
				},
			],
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<p>
							In Camunda 8.8, a document is uploaded to a store in
							AWS or GCP, not to a specific process instance. In
							the future, there might be multiple potential stores
							to upload a document to via the <code>storeId</code>
							.
						</p>
					),
				},
			],
		},
	},
	{
		origin: {
			path: "/process-instance/{id}/variables/{varName}/data",
			operation: "post",
		},
		target: {
			path: "/documents",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) varName</pre>,
					rightEntry: <pre>(string) documentId</pre>,
				},
				{
					leftEntry: <pre>(binary) data</pre>,
					rightEntry: <pre>(binary) file</pre>,
				},
				{
					leftEntry: <pre>(string) valueType</pre>,
					rightEntry: <pre>(string) metadata.contentType</pre>,
				},
			],
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<p>
							In Camunda 8.8, a document is uploaded to a store in
							AWS or GCP, not to a specific process instance. In
							the future, there might be multiple potential stores
							to upload a document to via the <code>storeId</code>
							.
						</p>
					),
				},
			],
		},
	},
];
