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
		explanation: (
			<>
				<h3>Direct mappings</h3>
				<p>
					<code>...Like</code> and <code>...In</code> parameters are
					grouped together with the parameter they relate to.
				</p>
				<table>
					<thead>
						<tr>
							<th>Camunda 7 Filters</th>
							<th>Camunda 8 Filters</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>
									(string) processDefinitionId
									<br />
									(string[]) processDefinitionIdIn
								</pre>
							</td>
							<td>
								<pre>(string) processDefinitionKey</pre>
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
									(string) name
									<br />
									(string) nameLike
								</pre>
							</td>
							<td>
								<pre>(string) name</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) key
									<br />
									(string[]) keysIn
									<br />
									(string) keyLike
								</pre>
							</td>
							<td>
								<pre>(string) processDefinitionId</pre>
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
									(integer) version
									<br />
									(boolean) latestVersion
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
									(integer) version
									<br />
									(string) versionTag
								</pre>
								<p>
									In Camunda 8, versions are integers and
									assigned on deployment. To get the latest
									version, omit the version filter, but sort
									by version and select the first item.
								</p>
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
								<pre>(string) resourceName</pre>
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
								<pre>(string) tenantId</pre>
								<p>
									If multi-tenancy is disabled, all resources
									are deployed to the tenantId "default".
									There is no "withoutTenantId" in Camunda 8.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
				<h3>Not possible/applicable in Camunda 8.8:</h3>
				<table>
					<thead>
						<tr>
							<th>Camunda 7 Filters</th>
							<th>Explanation</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>
									(string) deploymentId
									<br />
									(date-time) deployedAfter
									<br />
									(date-time) deployedAt
								</pre>
							</td>
							<td>
								<p>
									While deployments are assigned a
									deploymentKey, deployments do not represent
									resources that can be searched for or
									otherwise used for filtering.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) category
									<br />
									(string) categoryLike
								</pre>
							</td>
							<td>
								<p>
									The concept of category has not been adopted
									in Camunda 8.8.
								</p>
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
								Suspending a process definition is not yet
								possible in Camunda 8.8.
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) incidentId
									<br />
									(string) incidentType
									<br />
									(string) incidentMessage
									<br />
									(string) incidentMessageLike
								</pre>
							</td>
							<td>
								It is not possible to search for process
								definitions with specific incident information
								in Camunda 8.8. But it is possible to search for
								incidents and filter or sort by process
								definition id or key.
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) startableBy
									<br />
									(boolean) startablePermissionCheck
								</pre>
							</td>
							<td>
								Search authorizations with resourceType
								PROCESS_DEFINITION instead.
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(boolean) startableInTasklist
									<br />
									(boolean) notStartableInTasklist
									<br />
								</pre>
							</td>
							<td>
								All processes a users is allowed to start, can
								be started from Tasklist.
							</td>
						</tr>
					</tbody>
				</table>
			</>
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
		explanation: (
			<div>
				Replaced by <code>POST /process-definitions/search</code>{" "}
				endpoint.
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
		explanation: (
			<>
				<p>
					<strong>Different base path!</strong>
				</p>
				<h3>Direct Mappings</h3>
				<table>
					<thead>
						<tr>
							<th>Camunda 7 Parameters</th>
							<th>Camunda 8 Parameters</th>
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
					</tbody>
				</table>
				<h3>Not possible/applicable in Camunda 8.8:</h3>
				<table>
					<thead>
						<tr>
							<th>Camunda 7 Parameter</th>
							<th>Explanation</th>
						</tr>
					</thead>
					<tbody>
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
			</>
		),
	},
	{
		origin: {
			path: "/process-definition/key/{key}",
			operation: "get",
		},
		target: {
			path: "/process-definitions/search",
			operation: "post",
		},
		explanation: (
			<>
				<h3>Direct Mappings</h3>
				<table>
					<thead>
						<tr>
							<th>Camunda 7 Parameters</th>
							<th>Camunda 8 Filters</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) key</pre>
							</td>
							<td>
								<pre>(string) processDefinitionId</pre>
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
			path: "/process-definition/key/{key}/deployed-start-form",
			operation: "get",
		},
		target: {
			path: "/process-definitions/{processDefinitionKey}/form",
			operation: "get",
		},
		explanation: (
			<>
				<h3>Not possible/applicable in Camunda 8.8:</h3>
				<table>
					<thead>
						<tr>
							<th>Camunda 7 Parameters</th>
							<th>Explanation</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) key</pre>
							</td>
							<td>
								<p>
									In Camunda 8, the start form can be
									retrieved for a unique processDefinitionKey
									which does not correspond to the key in
									Camunda 7. See{" "}
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
			path: "/process-definition/key/{key}/diagram",
			operation: "get",
		},
		target: {},
		explanation: "Not available in Camunda 8.8.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/form-variables",
			operation: "get",
		},
		target: {},
		explanation: "Redundant in Camunda 8: no Generated Task Form approach.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/history-time-to-live",
			operation: "put",
		},
		target: {},
		explanation:
			"Time to live in Camunda 8 is not set in the process definition. Instead, the Camunda 8 applications have specific retention times.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/rendered-form",
			operation: "get",
		},
		target: {},
		explanation: "Redundant in Camunda 8: no Generated Task Form approach.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/start",
			operation: "post",
		},
		target: {
			path: "/process-instances",
			operation: "post",
		},
		explanation: (
			<>
				<p>
					<strong>Different base path!</strong>
				</p>
				<h3>Direct Mappings</h3>
				<table>
					<thead>
						<tr>
							<th>Camunda 7 Fields</th>
							<th>Camunda 8 Fields</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) key</pre>
							</td>
							<td>
								<pre>(string) processDefinitionId</pre>
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
						<tr>
							<td>
								<pre>(object[]) startInstructions</pre>
							</td>
							<td>
								<pre>(object[]) startInstructions</pre> (only
								startBeforeElement)
							</td>
						</tr>
						<tr>
							<td>
								<pre>(boolean) withVariablesInReturn</pre>
							</td>
							<td>
								<pre>
									(boolean) awaitCompletion
									<br />
									(integer) requestTimeout
								</pre>
								<p>
									All processing in Camunda 8 is asynchronous.
									To receive a synchronous response, you can
									await completion and specify a request
									timeout.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
				<h3>Not possible/applicable in Camunda 8.8:</h3>
				<table>
					<thead>
						<tr>
							<th>Camunda 7 Fields</th>
							<th>Explanation</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) businessKey</pre>
							</td>
							<td>
								Not available in Camunda 8.8. Planned for
								Camunda 8.9.
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) caseInstanceId</pre>
							</td>
							<td>No CMMN in Camunda 8.</td>
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
								Not possible in Camunda 8.8. This functionality
								might be extended alongside the
								startInstructions functionality.
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
	},
	{
		origin: {
			path: "/process-definition/key/{key}/startForm",
			operation: "get",
		},
		target: {
			path: "/process-definitions/{processDefinitionKey}/form",
			operation: "get",
		},
		explanation: (
			<>
				<h3>Not possible/applicable in Camunda 8.8:</h3>
				<table>
					<thead>
						<tr>
							<th>Camunda 7 Parameters</th>
							<th>Explanation</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) key</pre>
							</td>
							<td>
								<p>
									In Camunda 8, the start form can be
									retrieved for a unique processDefinitionKey
									which does not correspond to the key in
									Camunda 7. See{" "}
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
];
