export const process_definition = [
	{
		origin: {
			path: "/process-definition",
			operation: "get",
		},
		target: {
			path: "/process-definitions/search",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(string) processDefinitionId
							<br />
							(string[]) processDefinitionIdIn
						</pre>
					),
					rightEntry: (
						<>
							<pre>(string) processDefinitionKey</pre>
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
							(string) name
							<br />
							(string) nameLike
						</pre>
					),
					rightEntry: <pre>(string) name</pre>,
				},
				{
					leftEntry: (
						<pre>
							(string) key
							<br />
							(string[]) keysIn
							<br />
							(string) keyLike
						</pre>
					),
					rightEntry: (
						<>
							<pre>(string) processDefinitionId</pre>
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
					),
					rightEntry: (
						<>
							<pre>
								(integer) version
								<br />
								(string) versionTag
							</pre>
							<p>
								In Camunda 8, versions are integers and assigned
								on deployment. To get the latest version, omit
								the version filter, but sort by version and
								select the first item.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) resourceName
							<br />
							(string) resourceNameLike
						</pre>
					),
					rightEntry: <pre>(string) resourceName</pre>,
				},
				{
					leftEntry: (
						<pre>
							(string[]) tenantIdIn
							<br />
							(boolean) withoutTenantId
							<br />
							(boolean) includeProcessDefinitionsWithoutTenantId
						</pre>
					),
					rightEntry: (
						<>
							<pre>(string) tenantId</pre>
							<p>
								If multi-tenancy is disabled, all resources are
								deployed to the tenantId "default". There is no
								"withoutTenantId" in Camunda 8.
							</p>
						</>
					),
				},
			],
			additionalInfo: (
				<p>
					<code>...Like</code> and <code>...In</code> parameters are
					grouped together with the parameter they relate to.
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(string) deploymentId
							<br />
							(date-time) deployedAfter
							<br />
							(date-time) deployedAt
						</pre>
					),
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
							(string) category
							<br />
							(string) categoryLike
						</pre>
					),
					rightEntry: (
						<p>
							The concept of category has not been adopted in
							Camunda 8.8.
						</p>
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
						<p>
							Suspending a process definition is not yet possible
							in Camunda 8.8.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) incidentId
							<br />
							(string) incidentType
							<br />
							(string) incidentMessage
							<br />
							(string) incidentMessageLike
						</pre>
					),
					rightEntry: (
						<p>
							It is not possible to search for process definitions
							with specific incident information in Camunda 8.8.
							But it is possible to search for incidents and
							filter or sort by process definition id or key.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) startableBy
							<br />
							(boolean) startablePermissionCheck
						</pre>
					),
					rightEntry: (
						<p>
							Search authorizations with resourceType
							PROCESS_DEFINITION instead.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(boolean) startableInTasklist
							<br />
							(boolean) notStartableInTasklist
						</pre>
					),
					rightEntry: (
						<p>
							All processes a users is allowed to start, can be
							started from Tasklist.
						</p>
					),
				},
			],
			additionalInfo: "",
		},
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
		mappedExplanation: (
			<p>
				See <code>GET Get list</code> endpoint.
			</p>
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
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) key</pre>,
					rightEntry: (
						<>
							<pre>(string) resourceKey</pre>
							<p>
								In Camunda 8, the resourceKey can be a
								processDefinitionKey, decisionDefinitionKey or
								formKey. This does not directly map to the key
								in Camunda 7.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(boolean) cascade</pre>,
					rightEntry: (
						<p>
							Deletion is only possible if there are no running
							process instances. The effect of{" "}
							<code>cascade</code> always applies in Camunda 8.
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
					rightEntry: (
						<p>
							Does not apply if no running process instances are
							deleted with the resource.
						</p>
					),
				},
			],
			additionalInfo: "",
		},
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) key</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionId</pre>
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
			additionalInfo: "",
		},
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
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) key</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionKey</pre>
							<p>
								In Camunda 8, the start form can be retrieved
								for a unique processDefinitionKey which does not
								correspond to the key in Camunda 7. See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/process-definition/key/{key}/diagram",
			operation: "get",
		},
		target: {},
		discontinuedExplanation: "Not available in Camunda 8.8.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/form-variables",
			operation: "get",
		},
		target: {},
		discontinuedExplanation:
			"Redundant in Camunda 8: no Generated Task Form approach.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/history-time-to-live",
			operation: "put",
		},
		target: {},
		discontinuedExplanation:
			"Time to live in Camunda 8 is not set in the process definition. Instead, the Camunda 8 applications have specific retention times.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/rendered-form",
			operation: "get",
		},
		target: {},
		discontinuedExplanation:
			"Redundant in Camunda 8: no Generated Task Form approach.",
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) key</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionId</pre>
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
				{
					leftEntry: <pre>(object[]) startInstructions</pre>,
					rightEntry: <pre>(object[]) startInstructions</pre>,
				},
				{
					leftEntry: <pre>(boolean) withVariablesInReturn</pre>,
					rightEntry: (
						<>
							<pre>
								(boolean) awaitCompletion
								<br />
								(integer) requestTimeout
							</pre>
							<p>
								All processing in Camunda 8 is asynchronous. To
								receive a synchronous response, you can await
								completion and specify a request timeout.
							</p>
						</>
					),
				},
			],
			additionalInfo: (
				<p>
					<strong>Different base path!</strong>
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) businessKey</pre>,
					rightEntry: (
						<p>
							No businessKey in Camunda 8.8. <a href="https://roadmap.camunda.com/c/296-business-key">Planned for a future release</a>.
						</p>
					),
				},
				{
					leftEntry: <pre>(string) caseInstanceId</pre>,
					rightEntry: <p>No CMMN in Camunda 8.</p>,
				},
				{
					leftEntry: (
						<pre>
							(boolean) skipCustomListeners
							<br />
							(boolean) skipIoMappings
						</pre>
					),
					rightEntry: (
						<p>
							Not yet possible in Camunda 8.8. This functionality
							might be extended alongside the startInstructions
							functionality.
						</p>
					),
				},
			],
			additionalInfo: "",
		},
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
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) key</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionKey</pre>
							<p>
								In Camunda 8, the start form can be retrieved
								for a unique processDefinitionKey which does not
								correspond to the key in Camunda 7. See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/process-definition/key/{key}/statistics",
			operation: "get",
		},
		target: {
			path: "/process-definitions/{processDefinitionKey}/statistics/element-instances",
			operation: "post",
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) key</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionKey</pre>
							<p>
								In Camunda 8, the statistics can be retrieved
								for a unique processDefinitionKey which does not
								correspond to the key in Camunda 7. See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(boolean) failedJobs</pre>,
					rightEntry: (
						<p>
							The number of canceled instances of the element is
							always included in the response.
						</p>
					),
				},
				{
					leftEntry: <pre>(boolean) incidents</pre>,
					rightEntry: (
						<p>
							The number of incidents instances for the element is
							always included in the response.
						</p>
					),
				},
				{
					leftEntry: <pre>(string) incidentsForType</pre>,
					rightEntry: <p>Not possible in Camunda 8.8.</p>,
				},
			],
			additionalInfo: "",
		},
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) key</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionId</pre>
							<p>
								If the processDefinitionId is used, a version
								can be specified. Alternatively, the unique
								processDefinitionKey can be used.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(object) variables</pre>,
					rightEntry: <pre>(object) variables</pre>,
				},
			],
			additionalInfo: "",
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) businessKey</pre>,
					rightEntry: (
						<p>
							No businessKey in Camunda 8.8. <a href="https://roadmap.camunda.com/c/296-business-key">Planned for a future release</a>.
						</p>
					),
				},
			],
		},
	},
	{
		origin: {
			path: "/process-definition/key/{key}/suspended",
			operation: "put",
		},
		target: {},
		roadmapExplanation:
			"Not yet possible in Camunda 8.8. Activating/suspending process instances is on the roadmap.",
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
		mappedExplanation:
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) key</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionId</pre>
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
					leftEntry: <pre>(string) tenant-id</pre>,
					rightEntry: <pre>(string) tenantId</pre>,
				},
			],
		},
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
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) key</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionKey</pre>
							<p>
								In Camunda 8, the start form can be retrieved
								for a unique processDefinitionKey which does not
								correspond to the key in Camunda 7. See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string) tenant-id</pre>,
					rightEntry: (
						<p>
							This endpoint is not tenant-specific in Camunda 8.8.
							The tenantId of the start form can be checked in the
							response.
						</p>
					),
				},
			],
		},
	},
	{
		origin: {
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}/diagram",
			operation: "get",
		},
		target: {},
		discontinuedExplanation: "Not available in Camunda 8.8.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}/form-variables",
			operation: "get",
		},
		target: {},
		discontinuedExplanation:
			"Redundant in Camunda 8: no Generated Task Form approach.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}/history-time-to-live",
			operation: "put",
		},
		target: {},
		discontinuedExplanation:
			"Time to live in Camunda 8 is not set in the process definition. Instead, the Camunda 8 applications have specific retention times.",
	},
	{
		origin: {
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}/rendered-form",
			operation: "get",
		},
		target: {},
		discontinuedExplanation:
			"Redundant in Camunda 8: no Generated Task Form approach.",
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) tenant-id</pre>,
					rightEntry: <pre>(string) tenantId</pre>,
				},
			],
			additionalInfo: (
				<>
					<p>
						<strong>Different base path!</strong>
					</p>
					<p>
						For all other fields, see{" "}
						<strong>Start Instance</strong> endpoint without
						tenant-id.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [],
			additionalInfo: (
				<p>
					For other fields, see{" "}
					<strong>Get Activity Instance Statistics</strong> endpoint
					without tenant-id.
				</p>
			),
		},
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
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) key</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionKey</pre>
							<p>
								In Camunda 8, the start form can be retrieved
								for a unique processDefinitionKey which does not
								correspond to the key in Camunda 7. See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string) tenant-id</pre>,
					rightEntry: (
						<p>
							This endpoint is not tenant-specific in Camunda 8.8.
							You can check the tenantId in the response.
						</p>
					),
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}/statistics",
			operation: "get",
		},
		target: {
			path: "/process-definitions/{processDefinitionKey}/statistics/element-instances",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) tenant-id</pre>,
					rightEntry: <pre>(string) tenantId</pre>,
				},
			],
			additionalInfo: (
				<p>
					For all other fields, see{" "}
					<strong>Get Activity Instance Statistics</strong> endpoint
					without tenant-id.
				</p>
			),
		},
		discontinued: {
			rowInfo: [],
			additionalInfo: (
				<p>
					For other fields, see{" "}
					<strong>Get Activity Instance Statistics</strong> endpoint
					without tenant-id.
				</p>
			),
		},
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) tenant-id</pre>,
					rightEntry: <pre>(string) tenantId</pre>,
				},
			],
			additionalInfo: (
				<p>
					For all other fields, see <strong>Submit Start Form</strong>{" "}
					endpoint without tenant-id.
				</p>
			),
		},
		discontinued: {
			rowInfo: [],
			additionalInfo: (
				<p>
					For other fields, see <strong>Submit Start Form</strong>{" "}
					endpoint without tenant-id.
				</p>
			),
		},
	},
	{
		origin: {
			path: "/process-definition/key/{key}/tenant-id/{tenant-id}/suspended",
			operation: "put",
		},
		target: {},
		mappedExplanation:
			"Not yet possible in Camunda 8.8. Activating/suspending process instances is on the roadmap.",
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
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) key</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionKey</pre>
							<p>
								In Camunda 8, the XML can be retrieved for a
								unique processDefinitionKey which does not
								correspond to the key in Camunda 7. See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string) tenant-id</pre>,
					rightEntry: (
						<p>
							This endpoint is not tenant-specific in Camunda 8.8.
							You can check the tenantId in the response.
						</p>
					),
				},
			],
			additionalInfo: "",
		},
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
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) key</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionKey</pre>
							<p>
								In Camunda 8, the XML can be retrieved for a
								unique processDefinitionKey which does not
								correspond to the key in Camunda 7. See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/process-definition/statistics",
			operation: "get",
		},
		target: {},
		discontinuedExplanation:
			"There is no endpoint yet to group process instance statistics by process definition in Camunda 8.8. Statistics can be grouped by elements, see Get Activity Instance Statistics.",
	},
	{
		origin: {
			path: "/process-definition/suspended",
			operation: "put",
		},
		target: {},
		roadmapExplanation:
			"Not yet possible in Camunda 8.8. Activating/suspending process instances is on the roadmap.",
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: <pre>(string) resourceKey</pre>,
				},
			],
			additionalInfo: "",
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(boolean) cascade</pre>,
					rightEntry: (
						<p>
							Deletion is only possible if there are no running
							process instances. The effect of{" "}
							<code>cascade</code> always applies in Camunda 8.
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
					rightEntry: (
						<p>
							Does not apply if no running process instances are
							deleted with the resource.
						</p>
					),
				},
			],
			additionalInfo: "",
		},
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionKey</pre>
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
			additionalInfo: "",
		},
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionKey</pre>
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
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/process-definition/{id}/diagram",
			operation: "get",
		},
		target: {},
		discontinuedExplanation: "Not available in Camunda 8.8.",
	},
	{
		origin: {
			path: "/process-definition/{id}/form-variables",
			operation: "get",
		},
		target: {},
		discontinuedExplanation:
			"Redundant in Camunda 8: no Generated Task Form approach.",
	},
	{
		origin: {
			path: "/process-definition/{id}/history-time-to-live",
			operation: "put",
		},
		target: {},
		discontinuedExplanation:
			"Time to live in Camunda 8 is not set in the process definition. Instead, the Camunda 8 applications have specific retention times.",
	},
	{
		origin: {
			path: "/process-definition/{id}/rendered-form",
			operation: "get",
		},
		target: {},
		discontinuedExplanation:
			"Redundant in Camunda 8: no Generated Task Form approach.",
	},
	{
		origin: {
			path: "/process-definition/{id}/restart",
			operation: "post",
		},
		target: {},
		discontinuedExplanation:
			"Not yet possible in Camunda 8.8. A running process instance can be modified. A canceled process instance cannot be modified.",
	},
	{
		origin: {
			path: "/process-definition/{id}/restart-async",
			operation: "post",
		},
		target: {},
		discontinuedExplanation:
			"Not yet possible in Camunda 8.8. A running process instance can be modified. A canceled process instance cannot be modified.",
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionKey</pre>
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
				<>
					<p>
						<strong>Different base path!</strong>
					</p>
					<p>
						For other fields, see <strong>Start Instance</strong> by
						key endpoint.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [],
			additionalInfo: (
				<p>
					For other fields, see <strong>Start Instance</strong> by key
					endpoint.
				</p>
			),
		},
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionKey</pre>
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
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/process-definition/{id}/static-called-process-definitions",
			operation: "get",
		},
		target: {},
		discontinuedExplanation: "Not possible in Camunda 8.8.",
	},
	{
		origin: {
			path: "/process-definition/{id}/statistics",
			operation: "get",
		},
		target: {
			path: "/process-definitions/{processDefinitionKey}/statistics/element-instances",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionKey</pre>
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
					For other fields, see{" "}
					<strong>Get Activity Instance Statistics</strong> by key
					endpoint.
				</p>
			),
		},
		discontinued: {
			rowInfo: [],
			additionalInfo: (
				<p>
					For other fields, see{" "}
					<strong>Get Activity Instance Statistics</strong> by key
					endpoint.
				</p>
			),
		},
	},
	{
		origin: {
			path: "/process-definition/{id}/submit-form",
			operation: "post",
		},
		target: {},
		discontinuedExplanation:
			"Redundant in Camunda 8: no Generated Task Form approach.",
	},
	{
		origin: {
			path: "/process-definition/{id}/suspended",
			operation: "put",
		},
		target: {},
		roadmapExplanation:
			"Not yet possible in Camunda 8.8. Activating/suspending process instances is on the roadmap.",
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionKey</pre>
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
			additionalInfo: "",
		},
	},
];
