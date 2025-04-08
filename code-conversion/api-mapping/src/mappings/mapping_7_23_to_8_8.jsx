import { c7_23 } from "../openapi/camunda7/c7_23";
import { c8_8 } from "../openapi/camunda8/c8_8";

export const mapping_7_23_to_8_8 = {
	id: "7_23_to_8_8",
	tabName: "7.23 to 8.8",
	c7BaseUrl:
		"https://docs.camunda.org/rest/camunda-bpm-platform/7.23-SNAPSHOT/#tag/",
	c8BaseUrl:
		"https://docs.camunda.io/docs/8.8/apis-tools/camunda-api-rest/specifications/",
	c7_specification: c7_23,
	c8_specification: c8_8,
	mappings: [
		// Authorization
		{
			origin: {
				path: "/authorization",
				operation: "get",
			},
			target: {
				path: "/authorizations/search",
				operation: "post",
			},
			purpose:
				"This endpoint is used to find an authorization using various optional parameters.",
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
										(string) id
										<br />
										(integer) type
										<br />
										(integer) resourceType
										<br />
										(string) resourceId
									</pre>
								</td>
								<td>
									<pre>
										(string) filter.ownerId
										<br />
										(enum) filter.ownerType
										<br />
										(string[]) filter.resourceIds
										<br />
										(enum) filter.resourceType
									</pre>
								</td>
							</tr>
							<tr>
								<td>
									<pre>
										(string[]) userIdIn
										<br />
										(string[]) groupIdIn
									</pre>
								</td>
								<td>
									Replaced by a combination of{" "}
									<code>resourceIds</code> and{" "}
									<code>resourceType</code>
								</td>
							</tr>
							<tr>
								<td>
									<pre>
										(string) sortBy
										<br />
										(string) sortOrder
									</pre>
								</td>
								<td>
									<pre>
										(string) sort[].field
										<br />
										(enum) sort[].order
									</pre>
								</td>
							</tr>
							<tr>
								<td>
									<pre>
										(integer) firstResult
										<br />
										(integer) maxResults
									</pre>
								</td>
								<td>
									<pre>
										(integer) page.from
										<br />
										(integer) page.limit
										<br />
										(object[]) page.searchAfter
										<br />
										(object[]) page.searchBefore
									</pre>
								</td>
							</tr>
						</tbody>
					</table>
				</div>
			),
		},
		// Batch
		// Condition
		// Decision Definition
		// Decision Requirements Definition
		// Deployment
		// Engine
		// Event Subscription
		// Execution
		// External Task
		// Filter
		// Group
		// Historic Activity Instance
		// Historic Batch
		// Historic Decision Definition
		// Historic Decision Instance
		// Historic Decision Requirements Definition
		// Historic Detail
		// Historic External Task Log
		// Historic Identity Link Log
		// Historic Incident
		// Historic Job Log
		// Historic Process Definition
		// Historic Process Instance
		// Historic Task Instance
		// Historic User Operation Log
		// Historic Variable Instance
		// History Cleanup
		// Identity
		// Incident
		// Job
		// Job Definition
		// Message
		// Metrics
		// Migratio
		// Modification
		// Process Definition
		{
			origin: {
				path: "/process-definition",
				operation: "get",
			},
			target: {
				path: "/process-definitions/search",
				operation: "post",
			},
			purpose:
				"This endpoint is used to find process definitions using various optional parameters.",
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
										(string) name
										<br />
										(string) nameLike
									</pre>
								</td>
								<td>
									<pre>(string) filter.name</pre>
								</td>
							</tr>
							<tr>
								<td>
									<pre>
										(string) key
										<br />
										(string[]) keys
										<br />
										(string) keyLike
									</pre>
								</td>
								<td>
									<pre>
										(string) filter.processDefinitionKey
									</pre>
								</td>
							</tr>
							<tr>
								<td>
									<pre>
										(string) processDefinitionId
										<br />
										(string[]) processDefinitionIdIn
									</pre>
								</td>
								<td>
									<pre>
										(string) filter.processDefinitionId
									</pre>
								</td>
							</tr>
							<tr>
								<td>
									<pre>
										(string) resourceName
										<br />
										(string) resourceNameLike
									</pre>
								</td>
								<td>
									<pre>(string) filter.resourceName</pre>
								</td>
							</tr>
							<tr>
								<td>
									<pre>
										(string[]) tenantIdIn
										<br />
										(boolean) withoutTenantId
										<br />
										(boolean)
										includeProcessDefinitionsWithoutTenantId
									</pre>
								</td>
								<td>
									<pre>(string) filter.tenantId</pre>
								</td>
							</tr>
							<tr>
								<td>
									<pre>
										(integer) version
										<br />
										(string) versionTag
										<br />
										(string) versionTagLike
										<br />
										(boolean) withoutVersionTag
									</pre>
								</td>
								<td>
									<pre>
										(integer) filter.version
										<br />
										(string) filter.versionTag
									</pre>
								</td>
							</tr>
							<tr>
								<td>
									<pre>
										(string) deploymentId
										<br />
										(date-time) deployedAfter
										<br />
										(date-time) deployedAt
										<br />
										(string) category
										<br />
										(string) categoryLike
										<br />
										(boolean) latestVersion
										<br />
										(string) startableBy
										<br />
										(boolean) active
										<br />
										(boolean) suspended
										<br />
										(string) incidentId
										<br />
										(string) incidentType
										<br />
										(string) incidentMessage
										<br />
										(string) incidentMessageLike
										<br />
										(boolean) startableInTasklist
										<br />
										(boolean) notStartableInTasklist
										<br />
										(boolean) startablePermissionCheck
										<br />
									</pre>
								</td>
								<td>Not possible in Camunda 8.8</td>
							</tr>
							<tr>
								<td>
									<pre>
										(string) sortBy
										<br />
										(string) sortOrder
									</pre>
								</td>
								<td>
									<pre>
										(string) sort[].field
										<br />
										(enum) sort[].order
									</pre>
								</td>
							</tr>
							<tr>
								<td>
									<pre>
										(integer) firstResult
										<br />
										(integer) maxResults
									</pre>
								</td>
								<td>
									<pre>
										(integer) page.from
										<br />
										(integer) page.limit
										<br />
										(object[]) page.searchAfter
										<br />
										(object[]) page.searchBefore
									</pre>
								</td>
							</tr>
						</tbody>
					</table>
				</div>
			),
		},
		// Process Instance
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
										(string*)
										filter.parentProcessInstanceKey
										<br />
										(string*)
										filter.parentFlowNodeInstanceKey
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
										(boolean)
										processDefinitionWithoutTenantId
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
										(integer*)
										filter.processDefinitionVersion
										<br />
										(string*)
										filter.processDefinitionVersionTag
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
						Asterisks signify that an advanced filter can be
						applied, similar to a unit test (
						<code>$eq, $neq, $in, $like with wildcards,...</code>).
					</div>
				</div>
			),
		},
		// Signal
		// Schema Log
		// Task
		// Task Attachment
		// Task Comment
		// Task Identity Link
		// Task Local Variable
		// Task Variable
		// Telemetry
		// Tenant
		// User
		// Variable Instance
		// Version
	],
};
