/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
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
					leftEntry: <pre>(string[]) processDefinitionIdIn</pre>,
					rightEntry: (
						<p>
							<code>filter.processDefinitionKey</code> accepts a
							single value. Issue a separate search for each
							<code>processDefinitionIdIn</code> value.
						</p>
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
					rightEntry: (
						<pre>
							(string) filter.name
							<br />
							(string) filter.name.$like
						</pre>
					),
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
							<pre>
								(string) filter.processDefinitionId
								<br />
								(string[]) filter.processDefinitionId.$in
								<br />
								(string) filter.processDefinitionId.$like
							</pre>
							<p>
								Map <code>key</code> to the scalar filter. Map{" "}
								<code>keysIn</code> to{" "}
								<code>filter.processDefinitionId.$in</code>.
								Map <code>keyLike</code> to{" "}
								<code>$like</code> after converting C7{" "}
								<code>%</code>/<code>_</code> wildcards to C8{" "}
								<code>*</code>/<code>?</code>. See{" "}
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
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(integer) filter.version
								<br />
								(boolean) filter.isLatestVersion
							</pre>
							<p>
								In Camunda 8, versions are integers and assigned
								on deployment. To get the latest version of every
								matching process definition, set{" "}
								<code>filter.isLatestVersion=true</code>.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string) versionTag</pre>,
					rightEntry: <pre>(string) filter.versionTag</pre>,
				},
				{
					leftEntry: (
						<pre>
							(string) versionTagLike
							<br />
							(boolean) withoutVersionTag
						</pre>
					),
					rightEntry: (
						<p>
							There is no direct Camunda 8.10 equivalent. Apply
							these restrictions client-side after retrieving the
							results.
						</p>
					),
				},
				{
					leftEntry: <pre>(string) resourceName</pre>,
					rightEntry: <pre>(string) filter.resourceName</pre>,
				},
				{
					leftEntry: <pre>(string) resourceNameLike</pre>,
					rightEntry: (
						<p>
							There is no direct Camunda 8.10 equivalent. Apply
							this restriction client-side after retrieving the
							results.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(string[]) tenantIdIn
						</pre>
					),
					rightEntry: (
						<p>
							<code>filter.tenantId</code> accepts a single value.
							Issue a separate search for each{" "}
							<code>tenantIdIn</code> value.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(boolean) withoutTenantId
							<br />
							(boolean) includeProcessDefinitionsWithoutTenantId
						</pre>
					),
					rightEntry: (
						<>
							<pre>(string) filter.tenantId</pre>
							<p>
								Map <code>withoutTenantId=true</code> to{" "}
								<code>filter.tenantId: "&lt;default&gt;"</code>.
								For{" "}
								<code>
									includeProcessDefinitionsWithoutTenantId=true
								</code>
								, issue an additional search with this filter and
								merge its results with the regular search.
							</p>
						</>
					),
				},
			],
			additionalInfo: (
				<>
					<p>
						<code>processDefinitionKey</code>,{" "}
						<code>versionTag</code>, <code>resourceName</code>, and{" "}
						<code>tenantId</code> are exact, single-value filters in
						Camunda 8.10. The Camunda 7{" "}
						<code>processDefinitionIdIn</code> and{" "}
						<code>tenantIdIn</code> filters require separate
						searches, while <code>versionTagLike</code> and{" "}
						<code>resourceNameLike</code> have no direct equivalent.
					</p>
					<p>
						When <code>filter.state</code> is omitted, Camunda 8.10
						also returns definitions in the{" "}
						<code>DRAINING</code> and <code>DELETED</code> states.
						Set <code>filter.state</code> to{" "}
						<code>ACTIVE</code> by default to preserve the Camunda 7
						runtime list behavior.
					</p>
				</>
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
							Camunda 8.10.
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
							in Camunda 8.10.
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
							with specific incident information in Camunda 8.10.
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
							All processes a user is allowed to start can be
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
							Camunda 8.10 accepts deletion while process
							instances are running, places the definition in{" "}
							<code>DRAINING</code>, and removes it after those
							instances finish. This does not implement C7{" "}
							<code>cascade</code>; history removal is controlled
							separately by <code>deleteHistory</code>.
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
							<pre>
								(string) filter.processDefinitionId
								<br />
								(boolean) filter.isLatestVersion
							</pre>
							<p>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
								. Set{" "}
								<code>filter.isLatestVersion</code> to{" "}
								<code>true</code> to match Camunda 7's
								latest-version response.
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
		discontinuedExplanation: "Not available in Camunda 8.10.",
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
					leftEntry: <pre>(string) businessKey</pre>,
					rightEntry: <pre>(string) businessId</pre>,
				},
				{
					leftEntry: <pre>(object[]) startInstructions</pre>,
					rightEntry: (
						<>
							<pre>(object[]) startInstructions</pre>
							<p>
								Only <code>startBeforeActivity</code> maps directly
								to an instruction with{" "}
								<code>elementId: activityId</code>. The other
								instruction types and fields, including{" "}
								<code>startAfterActivity</code>,{" "}
								<code>startTransition</code>, cancel behavior, and
								instruction-local variables, are not supported by
								the Camunda 8.10 request.
							</p>
						</>
					),
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
				<>
					<p>
						Camunda 7 <code>variables</code> entries are{" "}
						<code>VariableValueDto</code> wrappers. The Camunda 8.10
						creation request expects raw JSON values, so unwrap each
						entry and send only its <code>value</code>; do not copy
						the <code>type</code> or <code>valueInfo</code> metadata.
					</p>
					<p>
						<strong>Different base path!</strong>
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
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
							Not yet possible in Camunda 8.10. This functionality
							might be extended alongside the startInstructions
							functionality.
						</p>
					),
				},
			],
			additionalInfo: (
				<p>
					Camunda 7 <code>variables</code> entries are{" "}
					<code>VariableValueDto</code> wrappers. Unwrap each entry
					and send only its <code>value</code> as the raw JSON value
					in the Camunda 8.10 creation request; do not copy the{" "}
					<code>type</code> or <code>valueInfo</code> metadata.
				</p>
			),
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
					rightEntry: <p>Not possible in Camunda 8.10.</p>,
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
				{
					leftEntry: <pre>(string) businessKey</pre>,
					rightEntry: <pre>(string) businessId</pre>,
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/process-definition/key/{key}/suspended",
			operation: "put",
		},
		target: {},
		roadmapExplanation:
			"Suspending a process definition is not yet possible in Camunda 8.10.",
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
			"See Delete By Key endpoint. Deletion of resource is not tenant-specific in Camunda 8.10.",
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
							<pre>
								(string) filter.processDefinitionId
								<br />
								(boolean) filter.isLatestVersion
							</pre>
							<p>
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
								. Set{" "}
								<code>filter.isLatestVersion</code> to{" "}
								<code>true</code> to match Camunda 7's
								latest-version response.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string) tenant-id</pre>,
					rightEntry: <pre>(string) filter.tenantId</pre>,
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
							This endpoint is not tenant-specific in Camunda 8.10.
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
		discontinuedExplanation: "Not available in Camunda 8.10.",
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
							This endpoint is not tenant-specific in Camunda 8.10.
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
					rightEntry: <pre>(string) filter.tenantId</pre>,
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
		roadmapExplanation:
			"Suspending a process definition is not yet possible in Camunda 8.10.",
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
							This endpoint is not tenant-specific in Camunda 8.10.
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
			"There is no endpoint yet to group process instance statistics by process definition in Camunda 8.10. Statistics can be grouped by elements, see Get Activity Instance Statistics.",
	},
	{
		origin: {
			path: "/process-definition/suspended",
			operation: "put",
		},
		target: {},
		roadmapExplanation:
			"Suspending a process definition is not yet possible in Camunda 8.10.",
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
					rightEntry: (
						<p>
							Resolve the migrated definition to its C8{" "}
							<code>processDefinitionKey</code> and use that key
							as <code>resourceKey</code>; the C7 definition id is
							not the C8 system-assigned key.
						</p>
					),
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
							Camunda 8.10 accepts deletion while process
							instances are running, places the definition in{" "}
							<code>DRAINING</code>, and removes it after those
							instances finish. This does not implement C7{" "}
							<code>cascade</code>; history removal is controlled
							separately by <code>deleteHistory</code>.
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
		discontinuedExplanation: "Not available in Camunda 8.10.",
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
			"Not yet possible in Camunda 8.10. A running process instance can be modified. A canceled process instance cannot be modified.",
	},
	{
		origin: {
			path: "/process-definition/{id}/restart-async",
			operation: "post",
		},
		target: {},
		discontinuedExplanation:
			"Not yet possible in Camunda 8.10. A running process instance can be modified. A canceled process instance cannot be modified.",
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
		discontinuedExplanation: "Not possible in Camunda 8.10.",
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
				{
					leftEntry: <pre>(object) variables</pre>,
					rightEntry: <pre>(object) variables</pre>,
				},
				{
					leftEntry: <pre>(string) businessKey</pre>,
					rightEntry: <pre>(string) businessId</pre>,
				},
			],
			additionalInfo: (
				<p>
					Camunda 7 <code>variables</code> entries are{" "}
					<code>VariableValueDto</code> wrappers. Unwrap each entry
					and send only its <code>value</code> as the raw JSON value
					in the Camunda 8.10 creation request; do not copy the{" "}
					<code>type</code> or <code>valueInfo</code> metadata.
				</p>
			),
		},
	},
	{
		origin: {
			path: "/process-definition/{id}/suspended",
			operation: "put",
		},
		target: {},
		roadmapExplanation:
			"Suspending a process definition is not yet possible in Camunda 8.10.",
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
