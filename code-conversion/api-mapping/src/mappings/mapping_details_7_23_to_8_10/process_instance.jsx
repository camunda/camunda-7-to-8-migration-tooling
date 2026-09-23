/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
export const process_instance = [
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
							<pre>(string*) filter.processInstanceKey</pre>
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
							<pre>(string*) filter.processDefinitionKey</pre>
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
							<pre>(string*) filter.processDefinitionId</pre>
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
					rightEntry: <pre>(string*) filter.parentProcessInstanceKey</pre>,
				},
				{
					leftEntry: <pre>(boolean) rootProcessInstances</pre>,
					rightEntry: (
						<>
							<pre>(string*) filter.parentElementInstanceKey</pre>
							<p>
								Check whether{" "}
								<code>parentElementInstanceKey</code> is absent
								to identify a root process instance.
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
							<pre>(string*) filter.state</pre>
							<p>
								Use <code>ACTIVE</code> for active process
								instances and <code>SUSPENDED</code> for
								suspended process instances. Other supported
								values include <code>COMPLETED</code> and{" "}
								<code>TERMINATED</code>.
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
							(boolean) filter.hasIncident
							<br />
							(string*) filter.errorMessage
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
					rightEntry: (
						<>
							<pre>(string*) filter.tenantId</pre>
							<p>
								Map <code>withoutTenantId=true</code> and{" "}
								<code>processDefinitionWithoutTenantId=true</code>{" "}
								to the C8 default-tenant alias{" "}
								<code>&lt;default&gt;</code> in{" "}
								<code>filter.tenantId</code>.
							</p>
						</>
					),
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
					rightEntry: <pre>(object[]*) filter.variables</pre>,
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
						<pre>
							(string*) filter.businessId
							<br />
							(string*) filter.businessId.$like
						</pre>
					),
				},
			],
			additionalInfo: (
				<>
					<p>
						Asterisks signify that an advanced filter can be
						applied, similar to a unary test (
						<code>$eq, $neq, $in, $like with wildcards,...</code>).
					</p>
					<p>
						Camunda 7 returns runtime process instances only. When
						neither <code>active</code> nor <code>suspended</code> is
						supplied, set <code>filter.state.$in</code> to{" "}
						<code>["ACTIVE", "SUSPENDED"]</code> so the Camunda 8.10
						search does not include historical{" "}
						<code>COMPLETED</code> or <code>TERMINATED</code>{" "}
						instances.
					</p>
				</>
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
					rightEntry: <p>No CMMN in Camunda 8.10.</p>,
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
							elementInstanceIds. Use the POST
							/element-instances/search endpoint instead to find
							all process instances related to specific element
							details.
						</p>
					),
				},
				{
					leftEntry: <pre>(boolean) leafProcessInstances</pre>,
					rightEntry: (
						<p>
							It is not possible to find process instances that
							have no active subprocess instance in Camunda 8.10.
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
		target: [
			{
				path: "/process-instances/cancellation",
				operation: "post",
			},
			{
				path: "/process-instances/{processInstanceKey}/cancellation",
				operation: "post",
			},
		],
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string[]) processInstanceIds</pre>,
					rightEntry: (
						<>
							<pre>
								(string) processInstanceKey
								<br />
								(string[]) filter.processInstanceKey.$in
							</pre>
							<p>
								For explicit IDs that may identify suspended or
								child process instances, issue one request to
								the individual cancellation endpoint per ID.
								Use{" "}
								<code>filter.processInstanceKey.$in</code> only
								when every selected ID is an active root
								process instance. See{" "}
								<a href="#key-to-id">Camunda 7 key → Camunda 8 id</a>
							</p>
						</>
					),
				},
			],
			additionalInfo: (
				<>
					<p>
						The batch cancellation endpoint only cancels ACTIVE
						root process instances. It ignores{" "}
						<code>filter.state</code> and{" "}
						<code>filter.parentProcessInstanceKey</code>. For
						selections that may include suspended or child process
						instances, resolve the matching IDs first and issue
						individual cancellation requests.
					</p>
					<p>
						Asterisks signify that an advanced filter can be
						applied, similar to a unary test (
						<code>$eq, $neq, $in, $like with wildcards,...</code>).
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) deleteReason</pre>,
					rightEntry: (
						<p>
							It is not possible to add a delete reason in Camunda
							8.10.
						</p>
					),
				},
				{
					leftEntry: <pre>(boolean) skipCustomListeners</pre>,
					rightEntry: (
						<p>
							Not yet possible in Camunda 8.10. It's not possible
							to skip execution listeners for activities ended as
							part of this request.
						</p>
					),
				},
				{
					leftEntry: <pre>(boolean) skipSubprocesses</pre>,
					rightEntry: (
						<p>
							Camunda 8 cancellation cascades to child process
							instances. There is no equivalent of{" "}
							<code>skipSubprocesses=true</code>, so preserving
							subprocesses requires a separate migration strategy.
						</p>
					),
				},
				{
					leftEntry: <pre>(boolean) skipIoMappings</pre>,
					rightEntry: (
						<p>
							Not yet possible in Camunda 8.10. It's not possible
							to skip input/output mappings for activities ended
							as part of this request.
						</p>
					),
				},
				{
					leftEntry: <pre>(object) processInstanceQuery</pre>,
					rightEntry: (
						<p>
							The batch cancellation endpoint only cancels ACTIVE
							root process instances and ignores{" "}
							<code>state</code> and{" "}
							<code>parentProcessInstanceKey</code> filters.
							Use the batch endpoint only after ensuring the
							selected instances are active roots; otherwise,
							search for the matching IDs and cancel each one
							with the individual cancellation endpoint.
						</p>
					),
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
		target: {},
		discontinuedExplanation: (
			<p>
				Camunda 8.10 has no equivalent endpoint for deleting historic
				process instances by query. Process-instance cancellation only
				affects active root instances and does not delete history. Use
				the configured history retention and cleanup mechanism instead.
			</p>
		),
	},
	{
		origin: {
			path: "/process-instance/job-retries",
			operation: "post",
		},
		target: {
			path: "/jobs/batch-update",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string[]) processInstances</pre>,
					rightEntry: (
						<>
							<pre>(string[]) filter.processInstanceKey.$in</pre>
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
					leftEntry: <pre>(object) processInstanceQuery</pre>,
					rightEntry: (
						<>
							<pre>(object) filter</pre>
							<p>
								Translate supported query fields into the
								nested <code>filter</code> object. Do not send
								<code>processInstanceQuery</code> as a
								top-level field.
							</p>
							<p>
								<code>processInstanceQuery.superProcessInstance</code>{" "}
								cannot be mapped to{" "}
								<code>filter.parentProcessInstanceKey</code>{" "}
								because this endpoint ignores that filter.
								Resolve the child instance keys first and
								pass them as{" "}
								<code>filter.processInstanceKey.$in</code>.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(integer) retries</pre>,
					rightEntry: <pre>(int32) changeset.retries</pre>,
				},
			],
			additionalInfo: (
				<>
					<p>
						The Camunda 8.10 request body must be{" "}
						<code>{"{ filter: ..., changeset: { retries: ... } }"}</code>
						. The operation is asynchronous and returns a batch
						operation key that can be used to track its progress.
					</p>
					<p>
						When both <code>processInstances</code> and{" "}
						<code>processInstanceQuery</code> are supplied, issue
						separate batch updates for the two selector sources.
						Camunda 7 unions them, while Camunda 8 combines filter
						fields conjunctively.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(dateTime) dueDate</pre>,
					rightEntry: <p>Not applicable in Camunda 8.10.</p>,
				},
			],
		},
	},
	{
		origin: {
			path: "/process-instance/job-retries-historic-query-based",
			operation: "post",
		},
		target: {
			path: "/jobs/batch-update",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string[]) processInstances</pre>,
					rightEntry: (
						<>
							<pre>(string[]) filter.processInstanceKey.$in</pre>
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
					leftEntry: <pre>(integer) retries</pre>,
					rightEntry: <pre>(int32) changeset.retries</pre>,
				},
			],
			additionalInfo: (
				<>
					<p>
						The Camunda 8.10 request body must be{" "}
						<code>
							{"{ filter: ..., changeset: { retries: ... } }"}
						</code>
						. Only explicitly translatable active-job criteria
						can be represented by the Camunda 8 job filter; the
						operation is asynchronous.
					</p>
					<p>
						If both <code>processInstances</code> and{" "}
						<code>historicProcessInstanceQuery</code> are supplied,
						only the process-instance IDs can be mapped; historic
						criteria are not supported. Issue separate batch updates
						when distinct active selector sources must be preserved,
						because Camunda 8 combines filter fields conjunctively.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: (
						<pre>(object) historicProcessInstanceQuery</pre>
					),
					rightEntry: (
						<p>
							Historic-only criteria have no equivalent in the
							Camunda 8.10 job filter.
						</p>
					),
				},
				{
					leftEntry: <pre>(dateTime) dueDate</pre>,
					rightEntry: <p>Not applicable in Camunda 8.10.</p>,
				},
			],
		},
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
			additionalInfo: (
				<p>
					Camunda 7 <code>variables</code> entries are{" "}
					<code>VariableValueDto</code> wrappers. Unwrap each entry
					and send only its <code>value</code> as the raw JSON value
					in the Camunda 8.10 publication request; do not copy the{" "}
					<code>type</code> or <code>valueInfo</code> metadata.
				</p>
			),
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
		target: [
			{
				path: "/process-instances/suspension",
				operation: "post",
			},
			{
				path: "/process-instances/resumption",
				operation: "post",
			},
		],
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
					leftEntry: (
						<pre>
							(string) processDefinitionKey
							<br />
							(string) processDefinitionTenantId
							<br />
							(boolean) processDefinitionWithoutTenantId
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(string) filter.processDefinitionId
								<br />
								(string) filter.tenantId
							</pre>
							<p>
								Map{" "}
								<code>processDefinitionWithoutTenantId=true</code>{" "}
								to the C8 default-tenant alias{" "}
								<code>&lt;default&gt;</code> in{" "}
								<code>filter.tenantId</code>.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string[]) processInstanceIds</pre>,
					rightEntry: (
						<>
							<pre>(string[]) filter.processInstanceKey.$in</pre>
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
					leftEntry: <pre>(object) processInstanceQuery</pre>,
					rightEntry: (
						<>
							<pre>(object) filter</pre>
							<p>
								Translate supported query fields into the
								nested <code>filter</code> object. Do not send
								<code>processInstanceQuery</code> as a
								top-level field.
							</p>
							<p>
								<code>processInstanceQuery.superProcessInstance</code>{" "}
								cannot be mapped to{" "}
								<code>filter.parentProcessInstanceKey</code>{" "}
								because these endpoints ignore that filter.
								Resolve the child instance keys first and
								pass them as{" "}
								<code>filter.processInstanceKey.$in</code>.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(boolean) suspended</pre>,
					rightEntry: (
						<p>
							Use{" "}
							<code>/process-instances/suspension</code> for{" "}
							<code>true</code> and{" "}
							<code>/process-instances/resumption</code> for{" "}
							<code>false</code>.
						</p>
					),
				},
			],
			additionalInfo: (
				<>
					<p>
					The Camunda 8.10 request body must be{" "}
					<code>{"{ filter: ... }"}</code>. With{" "}
					<code>suspended: true</code>, the suspension endpoint
					updates only active process instances. With{" "}
					<code>suspended: false</code>, use the resumption endpoint
					to update only suspended process instances. Both endpoints
					execute asynchronously and return a{" "}
					<code>batchOperationKey</code>; monitor the batch operation
					before relying on the updates being complete.
				</p>
				<p>
					Camunda 7 unions <code>processInstanceIds</code> and{" "}
					<code>processInstanceQuery</code> when both are
					supplied. Preserve that behavior with separate translated
					entries in <code>filter.$or</code>; do not combine the
					selectors as top-level filter fields because Camunda 8
					combines those fields conjunctively.
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
							(object) historicProcessInstanceQuery
						</pre>
					),
					rightEntry: (
						<p>
							These selectors have no equivalent in the Camunda
							8.10 process instance filter.
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
							(string[]) activityIdIn
							<br />
							(boolean) leafProcessInstances
						</pre>
					),
					rightEntry: (
						<p>
							These process-instance query fields are not
							supported by the Camunda 8.10 batch filter.
						</p>
					),
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/process-instance/suspended-async",
			operation: "post",
		},
		target: [
			{
				path: "/process-instances/suspension",
				operation: "post",
			},
			{
				path: "/process-instances/resumption",
				operation: "post",
			},
		],
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
					leftEntry: (
						<pre>
							(string) processDefinitionKey
							<br />
							(string) processDefinitionTenantId
							<br />
							(boolean) processDefinitionWithoutTenantId
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(string) filter.processDefinitionId
								<br />
								(string) filter.tenantId
							</pre>
							<p>
								Map{" "}
								<code>processDefinitionWithoutTenantId=true</code>{" "}
								to the C8 default-tenant alias{" "}
								<code>&lt;default&gt;</code> in{" "}
								<code>filter.tenantId</code>.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string[]) processInstanceIds</pre>,
					rightEntry: (
						<>
							<pre>(string[]) filter.processInstanceKey.$in</pre>
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
					leftEntry: <pre>(object) processInstanceQuery</pre>,
					rightEntry: (
						<>
							<pre>(object) filter</pre>
							<p>
								Translate supported query fields into the
								nested <code>filter</code> object. Do not send
								<code>processInstanceQuery</code> as a
								top-level field.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(boolean) suspended</pre>,
					rightEntry: (
						<p>
							Use{" "}
							<code>/process-instances/suspension</code> for{" "}
							<code>true</code> and{" "}
							<code>/process-instances/resumption</code> for{" "}
							<code>false</code>.
						</p>
					),
				},
			],
			additionalInfo: (
				<>
					<p>
					The Camunda 8.10 request body must be{" "}
					<code>{"{ filter: ... }"}</code>. With{" "}
					<code>suspended: true</code>, the suspension endpoint
					updates only active process instances. With{" "}
					<code>suspended: false</code>, use the resumption endpoint
					to update only suspended process instances. Both endpoints
					execute asynchronously and return a{" "}
					<code>batchOperationKey</code>; monitor the batch operation
					before relying on the updates being complete.
				</p>
				<p>
					Camunda 7 unions <code>processInstanceIds</code> and{" "}
					<code>processInstanceQuery</code> when both are
					supplied. Preserve that behavior with separate translated
					entries in <code>filter.$or</code>; do not combine the
					selectors as top-level filter fields because Camunda 8
					combines those fields conjunctively.
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
							(object) historicProcessInstanceQuery
						</pre>
					),
					rightEntry: (
						<p>
							These selectors have no equivalent in the Camunda
							8.10 process instance filter.
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
							(string[]) activityIdIn
							<br />
							(boolean) leafProcessInstances
						</pre>
					),
					rightEntry: (
						<p>
							These process-instance query fields are not
							supported by the Camunda 8.10 batch filter.
						</p>
					),
				},
			],
			additionalInfo: "",
		},
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
								In Camunda 8.10, variables can be updated for one
								particular scope. This can be a process
								instance. In this case the{" "}
								<code>elementInstanceKey</code> is the
								processInstanceKey. For each ID in{" "}
								<code>processInstanceIds</code>, issue a separate
								request. This is synchronous and does not preserve
								the source batch or asynchronous behavior.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(object) variables</pre>,
					rightEntry: <pre>(object) variables</pre>,
				},
			],
			additionalInfo: (
				<p>
					Camunda 7 <code>variables</code> entries are{" "}
					<code>VariableValueDto</code> wrappers. Unwrap each entry
					and send only its <code>value</code> as the raw JSON value
					in each Camunda 8.10 variables request; do not copy the{" "}
					<code>type</code> or <code>valueInfo</code> metadata.
				</p>
			),
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
							In Camunda 8.10, variables can be updated for one
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
					rightEntry: <p>Not possible in Camunda 8.10.</p>,
				},
				{
					leftEntry: <pre>(boolean) skipSubprocesses</pre>,
					rightEntry: (
						<p>
							Not yet possible in Camunda 8.10. Subprocesses are
							also canceled.
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
				Not yet possible in Camunda 8.10. You can retrieve all active
				elements of a specific process instance using the{" "}
				<code>POST Search element instances</code> endpoint. But this
				endpoint does not provide a tree structure or information about
				the element hierarchy.
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
				Not yet possible in Camunda 8.10. Adding comments to user tasks
				is on the roadmap of Camunda 8.
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
							<code>cancel</code> maps to{" "}
							<code>terminateInstructions</code>. Only{" "}
							<code>startBeforeActivity</code> maps to{" "}
							<code>activateInstructions</code>.{" "}
							<code>startAfterActivity</code> and{" "}
							<code>startTransition</code> have no direct equivalent
							and are not supported by this mapping.
						</p>
					),
				},
				{
					leftEntry: <pre>(object) instructions[].variables</pre>,
					rightEntry: (
						<>
							<pre>
								(object[]) activateInstructions[].variableInstructions
								<br />
								(object) activateInstructions[].variableInstructions[].variables
							</pre>
							<p>
								Variables are supported on activation instructions
								only; termination instructions do not accept{" "}
								<code>variableInstructions</code>. Convert Camunda 7
								typed variable values to the corresponding Camunda 8
								variable values before sending the request.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) instructions[].activityId
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
							In Camunda 8.10, the element is targeted via the{" "}
							<code>elementId</code>.
						</p>
					),
				},
				{
					leftEntry: <pre>(string) instructions[].transitionId</pre>,
					rightEntry: (
						<p>
							The <code>startTransition</code> instruction has no
							direct equivalent in Camunda 8.10.
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
					rightEntry: <p>Not possible in Camunda 8.10.</p>,
				},
				{
					leftEntry: <pre>(string) annotation</pre>,
					rightEntry: (
						<p>
							Not yet possible in Camunda 8.10. An integer{" "}
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
		target: [
			{
				path: "/process-instances/{processInstanceKey}/suspension",
				operation: "post",
			},
			{
				path: "/process-instances/{processInstanceKey}/resumption",
				operation: "post",
			},
		],
		mappedExplanation: (
			<p>
				The Camunda 8.10 Suspend process instance endpoint suspends the
				specified active process instance. Use the Resume process
				instance endpoint when the Camunda 7 <code>suspended</code> value
				is <code>false</code>.
			</p>
		),
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
							<pre>
								(string) filter.processInstanceKey
								<br />
								(string) filter.scopeKey
							</pre>
							<p>
								Use the same converted root process-instance key
								for both fields. See{" "}
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
					Set <code>filter.scopeKey</code> to the root process-instance
					key so child-local variables are excluded. Include{" "}
					<code>?truncateValues=false</code> in the request to return
					complete variable values.
				</p>
			),
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
					leftEntry: <pre>(object) modifications</pre>,
					rightEntry: <pre>(object) variables</pre>,
				},
			],
			additionalInfo: (
				<p>
					Camunda 7 <code>modifications</code> entries are{" "}
					<code>VariableValueDto</code> wrappers. Unwrap each entry and
					send only its <code>value</code> as the corresponding raw JSON
					value in <code>variables</code>; do not copy the{" "}
					<code>type</code> or <code>valueInfo</code> metadata.
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string[]) deletions</pre>,
					rightEntry: (
						<p>
							It is not possible to delete variables in Camunda
							8.10. But you can set them to <code>null</code> or an
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
				In Camunda 8.10, there is no endpoint to delete a process
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
							<pre>
								(string) filter.processInstanceKey
								<br />
								(string) filter.scopeKey
							</pre>
							<p>
								Use the same converted root process-instance key
								for both fields. See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string) varName</pre>,
					rightEntry: <pre>(string) filter.name</pre>,
				},
			],
			additionalInfo: (
				<p>
					Set <code>filter.scopeKey</code> to the root process-instance
					key so the name filter does not match a child-local variable.
					Include <code>?truncateValues=false</code> in the request to
					return the complete value.
				</p>
			),
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
								In Camunda 8.10, variables can be updated for one
								particular scope. This can be a process
								instance. In this case the{" "}
								<code>elementInstanceKey</code> is the{" "}
								<code>processInstanceKey</code>.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string) varName</pre>,
					rightEntry: <pre>(string) variables[varName]</pre>,
				},
				{
					leftEntry: <pre>(any) value</pre>,
					rightEntry: <pre>(any) variables[varName]</pre>,
				},
			],
			additionalInfo: (
				<p>
					Send the value as{" "}
					<code>
						{"{ variables: { [varName]: value } }"}
					</code>
					. The default <code>local: false</code> behavior matches
					Camunda 7's visible-variable update, which writes to the
					outermost visible scope. Camunda 8 stores variables as JSON,
					so convert Camunda 7 serialized values before sending them;
					the Camunda 7 <code>type</code> and{" "}
					<code>valueInfo</code> metadata have no direct equivalent.
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) type</pre>,
					rightEntry: (
						<p>
							Camunda 8 infers the variable type from the JSON
							value.
						</p>
					),
				},
				{
					leftEntry: <pre>(object) valueInfo</pre>,
					rightEntry: (
						<p>
							Camunda 8 does not accept Camunda 7 serialization
							metadata in this request.
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
					rightEntry: (
						<p>
							Read the process variable named{" "}
							<code>varName</code> first and use the{" "}
							<code>documentId</code> from its stored{" "}
							<code>DocumentReference</code>.
						</p>
					),
				},
			],
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<p>
							In Camunda 8.10, a document is uploaded to a store in
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
					rightEntry: (
						<p>
							Store the returned <code>DocumentReference</code> in
							the process variable named <code>varName</code>.
							The optional <code>documentId</code> query parameter
							is independent of the variable name; if omitted, a
							new document ID is generated.
						</p>
					),
				},
				{
					leftEntry: <pre>(binary) data</pre>,
					rightEntry: <pre>(binary) file</pre>,
				},
			],
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<p>
							In Camunda 8.10, a document is uploaded to a store in
							AWS or GCP, not to a specific process instance. In
							the future, there might be multiple potential stores
							to upload a document to via the <code>storeId</code>
							.
						</p>
					),
				},
				{
					leftEntry: <pre>(string) valueType</pre>,
					rightEntry: (
						<p>
							Camunda 7 accepts the <code>Bytes</code> and{" "}
							<code>File</code> enum values, while Camunda 8{" "}
							<code>metadata.contentType</code> expects a MIME
							type such as <code>application/pdf</code>. Derive
							the MIME type from the uploaded file;{" "}
							<code>valueType</code> has no direct equivalent.
						</p>
					),
				},
			],
		},
	},
];
