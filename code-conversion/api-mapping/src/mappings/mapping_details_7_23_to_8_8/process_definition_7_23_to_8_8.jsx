export const process_definition_7_23_to_8_8 = [
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
								<pre>(string) filter.processDefinitionKey</pre>
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
								<pre>(string) filter.processDefinitionId</pre>
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
	{
		origin: {
			path: "/process-definition/count",
			operation: "get",
		},
		target: {
			path: "/process-definitions/search",
			operation: "post",
		},
		purpose:
			"This endpoint is used to find the number of process definitions that fulfill the query criteria.",
		explanation: (
			<div>
				There is no specific endpoint for this in Camunda 8.8. Use the{" "}
				<code>POST /process-definitions/search</code> endpoint instead.
			</div>
		),
	},
	{
		origin: {
			path: "/process-definition/key/{key}",
			operation: "delete",
		},
		target: {
			path: "/resources/{resourceKey}/deletion",
			operation: "post",
		},
		purpose:
			"This endpoint is used to delete a process definition that belongs to no tenant.",
		explanation: (
			<div>
				<div>
					Mapping of C7 endpoint parameters to C8 endpoint parameters
					and fields:
				</div>
				<table>
					<thead>
						<tr>
							<th>C7 Parameter</th>
							<th>C8 Path Parameter/Field</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) key</pre>
							</td>
							<td>
								<pre>(string) resourceKey</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(boolean) cascade</pre>
							</td>
							<td>
								Deletion is only possible if there are no
								running process instances. The effect of{" "}
								<code>cascade</code> always applies in Camunda
								8.
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
								Does not apply if no running process instances
								are deleted with the resource.
							</td>
						</tr>
					</tbody>
				</table>
			</div>
		),
	},
	{
		origin: {
			path: "/process-definition/key/{key}",
			operation: "get",
		},
		target: {
			path: "/process-definitions/{processDefinitionKey}",
			operation: "get",
		},
		purpose:
			"This endpoint is used to retrieve information about a deployed process definition that does not belong to a tenant.",
		explanation: (
			<div>
				<div>
					Mapping of C7 endpoint parameters to C8 endpoint parameters:
				</div>
				<table>
					<thead>
						<tr>
							<th>C7 Path Parameter</th>
							<th>C8 Path Parameter</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) key</pre>
							</td>
							<td>
								<pre>(string) processDefinitionKey</pre>
							</td>
						</tr>
					</tbody>
				</table>
			</div>
		),
	},
	{
		origin: {
			path: "/process-definition/key/{key}/deployed-start-form",
			operation: "get",
		},
		target: {
			path: "/process-definitions/{processDefinitionKey}/form",
			operation: "get",
		},
		purpose:
			"This endpoint is used to retrieve the deployed start form of a process definition.",
		explanation: (
			<div>
				<div>
					Mapping of C7 endpoint parameters to C8 endpoint parameters:
				</div>
				<table>
					<thead>
						<tr>
							<th>C7 Path Parameter</th>
							<th>C8 Path Parameter</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) key</pre>
							</td>
							<td>
								<pre>(string) processDefinitionKey</pre>
							</td>
						</tr>
					</tbody>
				</table>
			</div>
		),
	},
	{
		origin: {
			path: "/process-definition/key/{key}/diagram",
			operation: "get",
		},
		target: {},
		purpose:
			"This endpoint is used to retrieve a deployed image that has the same filename as the process definition referenced by the key.",
		explanation: "Not available in Camunda 8.8",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/form-variables",
			operation: "get",
		},
		target: {},
		purpose:
			"This endpoint is used to retrieve information about start form variables for forms defined via the Generated Task Form approach.",
		explanation: "Redundant in Camunda 8.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/history-time-to-live",
			operation: "put",
		},
		target: {},
		purpose:
			"This endpoint is used to update the history time to live for the latest version of the process definition referenced by key.",
		explanation:
			"Time to live in Camunda 8 is not set in the process definition. Instead, the Camunda 8 applications have specific retention times.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/rendered-form",
			operation: "get",
		},
		target: {},
		purpose:
			"This endpoint is used to retrieve the deployed start form of a process definition for a form that is defined via the Generated Task Form approach.",
		explanation: "Redundant in Camunda 8",
	},
];
