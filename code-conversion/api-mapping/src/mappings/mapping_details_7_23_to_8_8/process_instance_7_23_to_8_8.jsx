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
		purpose:
			"This endpoint is used to find process instances using various optional parameters.",
		explanation: (
			<div>
				<div>
					Mapping of C7 endpoint parameters to C8 endpoint request
					body fields:
				</div>
				<table>
					<thead>
						<tr>
							<th>C7 Parameter</th>
							<th>C8 Field</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>
									(string[]) processInstanceIds
									<br />
									(string) processDefinitionId
									<br />
									(string) processDefinitionKey
									<br />
									(string[]) processDefinitionKeyIn
									<br />
									(string[]) processDefinitionKeyNotIn
								</pre>
							</td>
							<td>
								<pre>
									(string*) filter.processInstanceKey
									<br />
									(string*) filter.processDefinitionId
									<br />
									(string*) filter.processDefinitionName
									<br />
									(string*) filter.processDefinitionKey
								</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) superProcessInstance
									<br />
									(boolean) rootProcessInstances
								</pre>
							</td>
							<td>
								<pre>
									(string*) filter.parentProcessInstanceKey
									<br />
									(string*) filter.parentFlowNodeInstanceKey
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
								<pre>(string*) filter.tenantId</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(boolean) withIncident
									<br />
									(string) incidentId
									<br />
									(string) incidentType
									<br />
									(string) incidentMessage
									<br />
									(string) incidentMessageLike
									<br />
								</pre>
							</td>
							<td>
								<pre>
									(boolean) filter.hasIncident
									<br />
									(string*) filter.errorMessage
								</pre>
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
								<pre>(string*) filter.state</pre>
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
								<pre>(object[]*) filter.variables</pre>
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
							<td>No businessKey in Camunda 8.8</td>
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
							<td>No CMMN in Camunda 8.8</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) deploymentId
									<br />
									(string[]) activityIdIn
									<br />
									(string) subProcessInstance
									<br />
									(boolean) leafProcessInstances
								</pre>
							</td>
							<td>Not possible in Camunda 8.8</td>
						</tr>
						<tr>
							<td>Not possible in Camunda 7.23</td>
							<td>
								<pre>
									(date-time*) filter.startDate
									<br />
									(date-time*) filter.endDate
									<br />
									(integer*) filter.processDefinitionVersion
									<br />
									(string*) filter.processDefinitionVersionTag
									<br />
									(string*) filter.batchOperationId
									<br />
									(boolean) filter.hasRetriesLeft
								</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) sortBy <br />
									(string) sortOrder
								</pre>
							</td>
							<td>
								<pre>
									(string) sort[].field <br />
									(enum) sort[].order
								</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(integer) firstResult <br />
									(integer) maxResults
								</pre>
							</td>
							<td>
								<pre>
									(integer) page.from <br />
									(integer) page.limit <br />
									(object[]) page.searchAfter <br />
									(object[]) page.searchBefore
								</pre>
							</td>
						</tr>
					</tbody>
				</table>
				<div>
					Asterisks signify that an advanced filter can be applied,
					similar to a unit test (
					<code>$eq, $neq, $in, $like with wildcards,...</code>).
				</div>
			</div>
		),
	},
];
