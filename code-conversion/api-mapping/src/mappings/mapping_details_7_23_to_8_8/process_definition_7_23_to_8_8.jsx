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
];
