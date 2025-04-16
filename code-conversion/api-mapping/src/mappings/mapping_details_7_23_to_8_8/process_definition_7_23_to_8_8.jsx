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
				<p>
					<code>...Like</code> and <code>...In</code> parameters are
					grouped together with the parameter they relate to.
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
		explanation: "See Get List endpoint.",
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
								<pre>(string) key</pre>
							</td>
							<td>
								<pre>(string) resourceKey</pre>
								<p>
									In Camunda 8, the resourceKey can be a
									processDefinitionKey, decisionDefinitionKey
									or formKey. This does not directly map to
									the key in Camunda 7.
								</p>
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
								<pre>(string) key</pre>
							</td>
							<td>
								<pre>(string) processDefinitionKey</pre>
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
				<p>
					<strong>Different base path!</strong>
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
								<pre>(string) key</pre>
							</td>
							<td>
								<pre>(string) processDefinitionKey</pre>{" "}
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
			path: "/process-definition/key/{key}/statistics",
			operation: "get",
		},
		target: {
			path: "/process-definitions/{processDefinitionKey}/statistics/flownode-instances",
			operation: "post",
		},
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
								<pre>(string) key</pre>
							</td>
							<td>
								<pre>(string) processDefinitionKey</pre>
								<p>
									In Camunda 8, the statistics can be
									retrieved for a unique processDefinitionKey
									which does not correspond to the key in
									Camunda 7. See{" "}
									<a href="#key-to-id">
										Camunda 7 key → Camunda 8 id
									</a>
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(boolean) failedJobs</pre>
							</td>
							<td>
								<p>
									The number of canceled instances of the flow
									node is always included in the response.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(boolean) incidents</pre>
							</td>
							<td>
								<p>
									The number of incidents instances for the
									flow node is always included in the
									response.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) incidentsForType</pre>
							</td>
							<td>
								<p>Not possible in Camunda 8.8.</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
	},
	{
		origin: {
			path: "/process-definition/key/{key}/submit-form",
			operation: "post",
		},
		target: {
			path: "/process-instances",
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
								<pre>(string) key</pre>
							</td>
							<td>
								<pre>(string) processDefinitionId</pre>
								<p>
									If the processDefinitionId is used, a
									version can be specified. Alternatively, the
									unique processDefinitionKey can be used.
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
								<pre>(string) businessKey</pre>
							</td>
							<td>
								<p>
									No businessKey in Camunda 8.8. Planned for
									Camunda 8.9.
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
			path: "/process-definition/key/{key}/suspended",
			operation: "put",
		},
		target: {},
		explanation:
			"Not possible in Camunda 8.8. Activating/suspending process instances is on the roadmap.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}",
			operation: "delete",
		},
		target: {
			path: "/resources/{resourceKey}/deletion",
			operation: "post",
		},
		explanation:
			"See Delete By Key endpoint. Deletion of resource is not tenant-specific in Camunda 8.8.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}",
			operation: "get",
		},
		target: {
			path: "/process-definitions/search",
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
								<pre>(string) tenant-id</pre>
							</td>
							<td>
								<pre>(string) tenantId</pre>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
	},
	{
		origin: {
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}/deployed-start-form",
			operation: "get",
		},
		target: {
			path: "/process-definitions/{processDefinitionKey}/form",
			operation: "get",
		},
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
								<pre>(string) key</pre>
							</td>
							<td>
								<pre>(string) processDefinitionKey</pre>
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
						<tr>
							<td>
								<pre>(string) tenant-id</pre>
							</td>
							<td>
								<p>
									This endpoint is not tenant-specific in
									Camunda 8.8. The tenantId of the start form
									can be checked in the response.
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
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}/diagram",
			operation: "get",
		},
		target: {},
		explanation: "Not available in Camunda 8.8.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}/form-variables",
			operation: "get",
		},
		target: {},
		explanation: "Redundant in Camunda 8: no Generated Task Form approach.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}/history-time-to-live",
			operation: "put",
		},
		target: {},
		explanation:
			"Time to live in Camunda 8 is not set in the process definition. Instead, the Camunda 8 applications have specific retention times.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}/rendered-form",
			operation: "get",
		},
		target: {},
		explanation: "Redundant in Camunda 8: no Generated Task Form approach.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}/start",
			operation: "post",
		},
		target: {
			path: "/process-instances",
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
								<pre>(string) tenant-id</pre>
							</td>
							<td>
								<pre>(string) tenantId</pre>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					<strong>Different base path!</strong>
				</p>
				<p>
					For all other fields, see <strong>Start Instance</strong>{" "}
					endpoint without tenant-id.
				</p>
			</>
		),
		notPossible: (
			<p>
				For other fields, see{" "}
				<strong>Get Activity Instance Statistics</strong> endpoint
				without tenant-id.
			</p>
		),
	},
	{
		origin: {
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}/startForm",
			operation: "get",
		},
		target: {
			path: "/process-definitions/{processDefinitionKey}/form",
			operation: "get",
		},
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
								<pre>(string) key</pre>
							</td>
							<td>
								<pre>(string) processDefinitionKey</pre>
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
						<tr>
							<td>
								<pre>(string) tenant-id</pre>
							</td>
							<td>
								<p>
									This endpoint is not tenant-specific in
									Camunda 8.8. You can check the tenantId in
									the response.
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
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}/statistics",
			operation: "get",
		},
		target: {
			path: "/process-definitions/{processDefinitionKey}/statistics/flownode-instances",
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
								<pre>(string) tenant-id</pre>
							</td>
							<td>
								<pre>(string) tenantId</pre>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					For all other fields, see{" "}
					<strong>Get Activity Instance Statistics</strong> endpoint
					without tenant-id.
				</p>
			</>
		),
		notPossible: (
			<p>
				For other fields, see{" "}
				<strong>Get Activity Instance Statistics</strong> endpoint
				without tenant-id.
			</p>
		),
	},
	{
		origin: {
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}/submit-form",
			operation: "post",
		},
		target: {
			path: "/process-instances",
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
								<pre>(string) tenant-id</pre>
							</td>
							<td>
								<pre>(string) tenantId</pre>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					For all other fields, see <strong>Submit Start Form</strong>{" "}
					endpoint without tenant-id.
				</p>
			</>
		),
		notPossible: (
			<p>
				For other fields, see <strong>Submit Start Form</strong>{" "}
				endpoint without tenant-id.
			</p>
		),
	},
	{
		origin: {
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}/suspended",
			operation: "put",
		},
		target: {},
		explanation:
			"Not possible in Camunda 8.8. Activating/suspending process instances is on the roadmap.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}/xml",
			operation: "get",
		},
		target: {
			path: "/process-definitions/{processDefinitionKey}/xml",
			operation: "get",
		},
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
								<pre>(string) key</pre>
							</td>
							<td>
								<pre>(string) processDefinitionKey</pre>
								<p>
									In Camunda 8, the XML can be retrieved for a
									unique processDefinitionKey which does not
									correspond to the key in Camunda 7. See{" "}
									<a href="#key-to-id">
										Camunda 7 key → Camunda 8 id
									</a>
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) tenant-id</pre>
							</td>
							<td>
								<p>
									This endpoint is not tenant-specific in
									Camunda 8.8. You can check the tenantId in
									the response.
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
			path: "/process-definition/key/{key}/xml",
			operation: "get",
		},
		target: {
			path: "/process-definitions/{processDefinitionKey}/xml",
			operation: "get",
		},
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
								<pre>(string) key</pre>
							</td>
							<td>
								<pre>(string) processDefinitionKey</pre>
								<p>
									In Camunda 8, the XML can be retrieved for a
									unique processDefinitionKey which does not
									correspond to the key in Camunda 7. See{" "}
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
			path: "/process-definition/statistics",
			operation: "get",
		},
		target: {},
		explanation:
			"There is on endpoint to group process instance statistics by process definition in Camunda 8.8. Statistics can be grouped by flow nodes, see Get Activity Instance Statistics.",
	},
	{
		origin: {
			path: "/process-definition/suspended",
			operation: "put",
		},
		target: {},
		explanation:
			"Not possible in Camunda 8.8. Activating/suspending process instances is on the roadmap.",
	},
	{
		origin: {
			path: "/process-definition/{id}",
			operation: "delete",
		},
		target: {
			path: "/resources/{resourceKey}/deletion",
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
								<pre>(string) resourceKey</pre>
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
			path: "/process-definition/{id}",
			operation: "get",
		},
		target: {
			path: "/process-definitions/search",
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
								<pre>(string) processDefinitionKey</pre>
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
			path: "/process-definition/{id}/deployed-start-form",
			operation: "get",
		},
		target: {
			path: "/process-definitions/{processDefinitionKey}/form",
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
								<pre>(string) processDefinitionKey</pre>
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
			path: "/process-definition/{id}/diagram",
			operation: "get",
		},
		target: {},
		explanation: "Not available in Camunda 8.8.",
	},
	{
		origin: {
			path: "/process-definition/{id}/form-variables",
			operation: "get",
		},
		target: {},
		explanation: "Redundant in Camunda 8: no Generated Task Form approach.",
	},
	{
		origin: {
			path: "/process-definition/{id}/history-time-to-live",
			operation: "put",
		},
		target: {},
		explanation:
			"Time to live in Camunda 8 is not set in the process definition. Instead, the Camunda 8 applications have specific retention times.",
	},
	{
		origin: {
			path: "/process-definition/{id}/rendered-form",
			operation: "get",
		},
		target: {},
		explanation: "Redundant in Camunda 8: no Generated Task Form approach.",
	},
	{
		origin: {
			path: "/process-definition/{id}/restart",
			operation: "post",
		},
		target: {},
		explanation:
			"Not possible in Camunda 8.8. A running process instance can be modified. A canceled process instance cannot be modified.",
	},
	{
		origin: {
			path: "/process-definition/{id}/restart-async",
			operation: "post",
		},
		target: {},
		explanation:
			"Not possible in Camunda 8.8. A running process instance can be modified. A canceled process instance cannot be modified.",
	},
	{
		origin: {
			path: "/process-definition/{id}/start",
			operation: "post",
		},
		target: {
			path: "/process-instances",
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
								<pre>(string) processDefinitionKey</pre>
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
					<strong>Different base path!</strong>
				</p>
				<p>
					For other fields, see <strong>Start Instance</strong> by key
					endpoint.
				</p>
			</>
		),
		notPossible: (
			<p>
				For other fields, see <strong>Start Instance</strong> by key
				endpoint.
			</p>
		),
	},
	{
		origin: {
			path: "/process-definition/{id}/startForm",
			operation: "get",
		},
		target: {
			path: "/process-definitions/{processDefinitionKey}/form",
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
								<pre>(string) processDefinitionKey</pre>{" "}
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
			path: "/process-definition/{id}/static-called-process-definitions",
			operation: "get",
		},
		target: {},
		explanation: "Not possible in Camunda 8.8.",
	},
	{
		origin: {
			path: "/process-definition/{id}/statistics",
			operation: "get",
		},
		target: {
			path: "/process-definitions/{processDefinitionKey}/statistics/flownode-instances",
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
								<pre>(string) processDefinitionKey</pre>
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
					For other fields, see{" "}
					<strong>Get Activity Instance Statistics</strong> by key
					endpoint.
				</p>
			</>
		),
		notPossible: (
			<p>
				For other fields, see{" "}
				<strong>Get Activity Instance Statistics</strong> by key
				endpoint.
			</p>
		),
	},
	{
		origin: {
			path: "/process-definition/{id}/submit-form",
			operation: "post",
		},
		target: {},
		explanation: "Redundant in Camunda 8: no Generated Task Form approach.",
	},
	{
		origin: {
			path: "/process-definition/{id}/suspended",
			operation: "put",
		},
		target: {},
		explanation:
			"Not possible in Camunda 8.8. Activating/suspending process instances is on the roadmap.",
	},
	{
		origin: {
			path: "/process-definition/{id}/xml",
			operation: "get",
		},
		target: {
			path: "/process-definitions/{processDefinitionKey}/xml",
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
								<pre>(string) processDefinitionKey</pre>
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
		notPossible: "Only direct mappings.",
	},
];
