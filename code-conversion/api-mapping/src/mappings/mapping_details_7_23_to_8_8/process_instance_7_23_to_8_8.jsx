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
								<pre>(string[]) processInstanceIds</pre>
							</td>
							<td>
								<pre>(string*) processInstanceKey</pre>
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
								<pre>(string) processDefinitionId</pre>
							</td>
							<td>
								<pre>(string*) processDefinitionKey</pre>
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
								<pre>
									(string) processDefinitionKey
									<br />
									(string[]) processDefinitionKeyIn
									<br />
									(string[]) processDefinitionKeyNotIn
								</pre>
							</td>
							<td>
								<pre>(string*) processDefinitionId</pre>
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
								<pre>(string) superProcessInstance</pre>
							</td>
							<td>
								<pre>(string*) parentProcessInstanceKey</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(boolean) rootProcessInstances</pre>
							</td>
							<td>
								<pre>(string*) parentFlowNodeInstanceKey</pre>
								<p>
									Check exisitance of
									parentFlowNodeInstanceKey to infer the
									process being a root process instance or
									not.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(boolean) active
									<br />
									(boolean) suspended
									<br />
								</pre>
							</td>
							<td>
								<pre>(string*) state</pre>
								<p>
									<code>suspended</code> is not applicable to
									Camunda 8.8.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(boolean) withIncident
									<br />
									(string) incidentMessage
									<br />
									(string) incidentMessageLike
									<br />
								</pre>
							</td>
							<td>
								<pre>
									(boolean) hasIncident
									<br />
									(string*) errorMessage
								</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string[]) tenantIdIn
									<br />
									(boolean) withoutTenantId
									<br />
									(boolean) processDefinitionWithoutTenantId
								</pre>
							</td>
							<td>
								<pre>(string*) tenantId</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(map) variables
									<br />
									(boolean) variableNamesIgnoreCase
									<br />
									(boolean) variableValuesIgnoreCase
								</pre>
							</td>
							<td>
								<pre>(object[]*) variables</pre>
							</td>
						</tr>
					</tbody>
				</table>
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
								<pre>(string) deploymentId</pre>
							</td>
							<td>
								While deployments are assigned a deploymentKey,
								deployments do not represent resources that can
								be searched for or otherwise used for filtering.
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) businessKey
									<br />
									(string) businessKeyLike
								</pre>
							</td>
							<td>
								No businessKey in Camunda 8.8. Planned for
								Camunda 8.9.
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) subProcessInstance</pre>
							</td>
							<td>
								It is not possible to find all process instances
								that have a process instances with a specific
								processInstanceKey as a subprocess. Instead use
								the processInstanceKey to get the process
								instance and look up the
								parentProcessInstanceKey.
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) caseInstanceId
									<br />
									(string) superCaseInstance
									<br />
									(string) subCaseInstance
									<br />
								</pre>
							</td>
							<td>No CMMN in Camunda 8.8.</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) incidentId
									<br />
									(string) incidentType
								</pre>
							</td>
							<td>
								<p>
									It is not possible to filter by incidentId
									or incidentType. Use the POST
									/incidents/search endpoint instead to find
									all process instances related to specific
									incident details.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string[]) activityIdIn</pre>
							</td>
							<td>
								<p>
									It is not possible to filter by a list of
									possible flowNodeInstanceIds. Use the POST
									/flownode-instances/search endpoint instead
									to find all process instances related to
									specific flownode details.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(boolean) leafProcessInstances</pre>
							</td>
							<td>
								<p>
									It is not possible to find process instances
									that have no active subprocess instance in
									Camunda 8.8.
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
			path: "/process-instance",
			operation: "post",
		},
		target: {
			path: "/process-instances/search",
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
			path: "/process-instance/count",
			operation: "get",
		},
		target: {
			path: "/process-instances/search",
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
			path: "/process-instance/count",
			operation: "post",
		},
		target: {
			path: "/process-instances/search",
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
			path: "/process-instance/delete",
			operation: "post",
		},
		target: {
			path: "/process-instances/batch-operations/cancellation",
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
								<pre>(string[]) processInstanceIds</pre>
							</td>
							<td>
								<pre>(string*) processDefinitionKey</pre>
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
								<pre>(string) deleteReason</pre>
							</td>
							<td>
								<p>
									It is not possible to add a delete reason in
									Camunda 8.8.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(boolean) skipCustomListeners</pre>
							</td>
							<td>
								<p>
									Not possible in Camunda 8.8. It's not
									possible to skip execution listeners for
									activities ended as part of this request.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(boolean) skipSubprocesses</pre>
							</td>
							<td>
								<p>
									Subprocesses are not automatically deleted
									by this request.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(boolean) skipIoMappings</pre>
							</td>
							<td>
								<p>
									Not possible in Camunda 8.8. It's not
									possible to skip input/output mappings for
									activities ended as part of this request.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object) processInstanceQuery</pre>
							</td>
							<td>
								<p>See Get List endpoint for details.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object) historicProcessInstanceQuery</pre>
							</td>
							<td>
								<p>
									See{" "}
									<strong>
										Delete Async Historic Query Based (POST)
									</strong>{" "}
									for details.
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
			path: "/process-instance/delete-historic-query-based",
			operation: "post",
		},
		target: {
			path: "/process-instances/batch-operations/cancellation",
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
									(dateTime) startedBefore
									<br />
									(dateTime) startedAfter
									<br />
									(dateTime) finishedBefore
									<br />
									(dateTime) finishedAfter
								</pre>
							</td>
							<td>
								<pre>
									(dateTime*) startDate
									<br />
									(dateTime*) endDate
								</pre>
								<p>
									Use advanced filters on startDate and
									endDate.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(boolean) active
									<br />
									(boolean) suspended
									<br />
									(boolean) completed
								</pre>
							</td>
							<td>
								<pre>(object*) state</pre>
								<p>
									<code>suspended</code> is not applicable to
									Camunda 8.8.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					Asterisks signify that an advanced filter can be applied,
					similar to a unit test (
					<code>$eq, $neq, $in, $like with wildcards,...</code>).
				</p>
				<p>
					See Get List endpoint for details on most fields of the
					historicProcessInstanceQuery.
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
									(boolean) finished
									<br />
									(boolean) unfinished
								</pre>
							</td>
							<td>
								<p>
									These flags specify if a process instance
									has been completed or terminated regularly,
									or has been canceled by other means. This
									differentiation does not exist in Camunda
									8.8.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(boolean) withRootIncidents</pre>
							</td>
							<td>
								<p>Not possible in Camunda 8.8.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string[]) incidentIdIn
									<br />
									(string) incidentType
									<br />
									(string) incidentStatus
								</pre>
							</td>
							<td>
								<p>Not possible in Camunda 8.8.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(dateTime) executedActivityAfter
									<br />
									(dateTime) executedActivityBefore
									<br />
									(dateTime) executedJobAfter
									<br />
									(dateTime) executedJobBefore
								</pre>
							</td>
							<td>
								<p>
									These filters are not available in Camunda
									8.8.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) startedBy</pre>
							</td>
							<td>
								<p>This is not possible in Camunda 8.8.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string[]) executedActivityIdIn
									<br />
									(string[]) activeActivityIdIn
								</pre>
							</td>
							<td>
								<p>This is not possible in Camunda 8.8.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(boolean) externallyTerminated
									<br />
									(boolean) internallyTerminated
								</pre>
							</td>
							<td>
								<p>This is not possible in Camunda 8.8.</p>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					Asterisks signify that an advanced filter can be applied,
					similar to a unit test (
					<code>$eq, $neq, $in, $like with wildcards,...</code>).
				</p>
			</>
		),
	},
	{
		origin: {
			path: "/process-instance/job-retries",
			operation: "post",
		},
		target: {},
		explanation: (
			<p>
				There is no batch endpoint to update retries for multiple jobs
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
		explanation: (
			<p>
				There is no batch endpoint to update retries for multiple jobs
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
								<pre>(string) messageName</pre>
							</td>
							<td>
								<pre>(string) name</pre>
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
									(string[]) processInstanceIds
									<br />
									(object) processInstanceQuery
									<br />
									(object) historicProcessInstanceQuery
								</pre>
							</td>
							<td>
								<p>
									In Camunda 8, a message is published to a
									cluster and tenant. A restriction to
									specific process instances is not possible.
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
			path: "/process-instance/suspended",
			operation: "put",
		},
		target: {},
		explanation:
			"Not possible in Camunda 8.8. Activating/suspending process instances is on the roadmap.",
	},
	{
		origin: {
			path: "/process-instance/suspended-async",
			operation: "post",
		},
		target: {},
		explanation:
			"Not possible in Camunda 8.8. Activating/suspending process instances is on the roadmap.",
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
								<pre>(string[]) processInstanceIds</pre>
							</td>
							<td>
								<pre>(string) elementInstanceKey</pre>
								<p>
									In Camunda 8.8, variables can be updated for
									one particular scope. This can be a process
									instance. In this case the{" "}
									<code>elementInstanceKey</code> is the
									processInstanceKey.
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
									(object) processInstanceQuery
									<br />
									(object) historicProcessInstanceQuery
								</pre>
							</td>
							<td>
								<p>
									In Camunda 8.8, variables can be updated for
									one particular scope, not for a batch of
									process instances.
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
			path: "/process-instance/{id}",
			operation: "delete",
		},
		target: {
			path: "/process-instances/{processInstanceKey}/cancellation",
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
								<pre>(string) processInstanceKey</pre>
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
									(boolean) skipCustomListeners
									<br />
									(boolean) skipIoMappings
								</pre>
							</td>
							<td>
								<p>Not possible in Camunda 8.8.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(boolean) skipSubprocesses</pre>
							</td>
							<td>
								<p>
									Not possible in Camunda 8.8. Subprocesses
									are also canceled.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(boolean) failIfNotExists</pre>
							</td>
							<td>
								<p>
									Endpoint answers with <code>404</code> if
									the process instance is not found.
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
			path: "/process-instance/{id}",
			operation: "get",
		},
		target: {
			path: "/process-instances/{processInstanceKey}",
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
								<pre>(string) processInstanceKey</pre>
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
			path: "/process-instance/{id}/activity-instances",
			operation: "get",
		},
		target: {},
		explanation: (
			<p>
				Not possible in Camunda 8.8. You can retrieve all active flow
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
		explanation: (
			<p>
				Not possible in Camunda 8.8. Adding comments to user tasks is on
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
								<pre>(string) processInstanceKey</pre>
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
								<pre>(object[]) instructions</pre>
							</td>
							<td>
								<pre>
									(object[]) activateInstructions <br />
									(object[]) terminateInstructions
								</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) instructions[].type</pre>
							</td>
							<td>
								<p>
									<code>cancel</code> and{" "}
									<code>start...</code> types are mapped to{" "}
									<code>terminateInstructions</code> and{" "}
									<code>activateInstructions</code>.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object) instructions[].variables</pre>
							</td>
							<td>
								<pre>
									(object)
									activateInstructions[].variableInstructions
									<br />
									(object)
									terminateInstructions[].variableInstructions
								</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) instructions[].activityId
									<br />
									(string) instructions[].transitionId
								</pre>
							</td>
							<td>
								<pre>
									(object) activateInstructions[].elementId
									<br />
									(object) terminateInstructions[].elementId
								</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string)
									instructions[].ancestorActivityInstanceId
								</pre>
							</td>
							<td>
								<pre>
									(object)
									activateInstructions[].ancestorElementInstanceKey
									<br />
									(object)
									terminateInstructions[].ancestorElementInstanceKey
								</pre>
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
								<pre>
									(string) instructions[].activityInstanceId
									<br />
									(string) instructions[].transitionInstanceId
								</pre>
							</td>
							<td>
								<p>
									In Camunda 8.8, the element is targeted via
									the <code>elementId</code>.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(boolean) skipCustomListeners
									<br />
									(boolean) skipIoMappings
								</pre>
							</td>
							<td>
								<p>Not possible in Camunda 8.8.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) annotation</pre>
							</td>
							<td>
								<p>
									Not possible in Camunda 8.8. A integer
									operationReference can be added to the
									request.
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
			path: "/process-instance/{id}/modification-async",
			operation: "post",
		},
		target: {
			path: "/process-instances/{processInstanceKey}/modification",
			operation: "post",
		},
		explanation: (
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
		explanation:
			"Not possible in Camunda 8.8. Activating/suspending process instances is on the roadmap.",
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
								<pre>(string) processInstanceKey</pre>
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
								<pre>(boolean) deserializeValues</pre>
							</td>
							<td>
								<p>Not applicable, only JSON in Camunda 8.</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
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
								<pre>(string) elementInstanceKey</pre>
								<p>
									The <code>elementInstanceKey</code> can be
									the processInstanceKey that corresponds to{" "}
									<code>id</code>.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object[]) modifications</pre>
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
								<pre>(string[]) deletions</pre>
							</td>
							<td>
								<p>
									It is not possible to delete variables in
									Camunda 8.8. But you can set them to null or
									an empty string.
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
			path: "/process-instance/{id}/variables/{varName}",
			operation: "delete",
		},
		target: {},
		explanation: (
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
								<pre>(string) processInstanceKey</pre>
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
								<pre>(string) varName</pre>
							</td>
							<td>
								<pre>(string) name</pre>
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
								<pre>(boolean) deserializeValues</pre>
							</td>
							<td>
								<p>Not applicable, only JSON in Camunda 8.</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
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
								<pre>(string) elementInstanceKey</pre>
								<p>
									In Camunda 8.8, variables can be updated for
									one particular scope. This can be a process
									instance. In this case the{" "}
									<code>elementInstanceKey</code> is the
									processInstanceKey.
								</p>
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
								<pre>(string) varName</pre>
							</td>
							<td>
								<p>
									Set the <code>varName</code> as one of the
									variables to update.
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
			path: "/process-instance/{id}/variables/{varName}/data",
			operation: "get",
		},
		target: {
			path: "/documents/{documentId}",
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
								<pre>(string) varName</pre>
							</td>
							<td>
								<pre>(string) documentId</pre>
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
								<pre>(string) id</pre>
							</td>
							<td>
								<p>
									In Camunda 8.8, a document is uploaded to a
									store in AWS or GCP, not to a specific
									process instance. In the future, there might
									be multiple potential stores to upload a
									document to via the <code>storeId</code>.
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
			path: "/process-instance/{id}/variables/{varName}/data",
			operation: "post",
		},
		target: {
			path: "/documents",
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
								<pre>(string) varName</pre>
							</td>
							<td>
								<pre>(string) documentId</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(binary) data</pre>
							</td>
							<td>
								<pre>(binary) file</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) valueType</pre>
							</td>
							<td>
								<pre>(string) metadata.contentType</pre>
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
								<pre>(string) id</pre>
							</td>
							<td>
								<p>
									In Camunda 8.8, a document is uploaded to a
									store in AWS or GCP, not to a specific
									process instance. In the future, there might
									be multiple potential stores to upload a
									document to via the <code>storeId</code>.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
	},
];
