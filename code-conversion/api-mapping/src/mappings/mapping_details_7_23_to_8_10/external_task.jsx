/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
export const external_task = [
	{
		origin: {
			path: "/external-task",
			operation: "get",
		},
		target: {
			path: "/jobs/search",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				In Camunda 8.10, the <code>POST Search jobs</code> endpoint can
				be used to search for jobs without activating them. Note that
				external tasks in Camunda 7 correspond to jobs in Camunda 8.
			</div>
		),
	},
	{
		origin: {
			path: "/external-task",
			operation: "post",
		},
		target: {
			path: "/jobs/search",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				In Camunda 8.10, the <code>POST Search jobs</code> endpoint can
				be used to search for jobs without activating them. Note that
				external tasks in Camunda 7 correspond to jobs in Camunda 8.
			</div>
		),
	},
	{
		origin: {
			path: "/external-task/count",
			operation: "get",
		},
		target: {
			path: "/jobs/search",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				In Camunda 8.10, the <code>POST Search jobs</code> endpoint can
				be used to search for jobs. The response includes a{" "}
				<code>page.totalItems</code> field that provides the total count
				of matching jobs.
			</div>
		),
	},
	{
		origin: {
			path: "/external-task/count",
			operation: "post",
		},
		target: {
			path: "/jobs/search",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				In Camunda 8.10, the <code>POST Search jobs</code> endpoint can
				be used to search for jobs. The response includes a{" "}
				<code>page.totalItems</code> field that provides the total count
				of matching jobs.
			</div>
		),
	},
	{
		origin: {
			path: "/external-task/fetchAndLock",
			operation: "post",
		},
		target: {
			path: "/jobs/activation",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) workerId</pre>,
					rightEntry: (
						<>
							<pre>(string) worker</pre>
							<p>
								<code>worker</code> identifies the worker for
								logging only. Set{" "}
								<code>withLease: true</code> to preserve
								fetch-and-lock fencing, then pass the returned{" "}
								<code>leaseToken</code> to completion, failure,
								and BPMN-error requests.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(int32) maxTasks</pre>,
					rightEntry: (
						<>
							<pre>(int32) maxJobsToActivate</pre>
							<p>
								Camunda 8 accepts one <code>type</code> per
								request. Issue one activation request per topic
								and treat <code>maxTasks</code> as a shared
								budget: set{" "}
								<code>maxJobsToActivate</code> to the remaining
								budget and subtract the number of activated jobs
								before the next request.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(int64) asyncResponseTimeout</pre>,
					rightEntry: <pre>(int64) requestTimeout</pre>,
				},
				{
					leftEntry: <pre>(string) topics[].topicName</pre>,
					rightEntry: (
						<>
							<pre>(string) type</pre>
							<p>Only one type at a time.</p>
						</>
					),
				},
				{
					leftEntry: <pre>(int64) topics[].lockDuration</pre>,
					rightEntry: (
						<>
							<pre>(int64) timeout</pre>
							<p>Only one type at a time.</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string[]) topics[].variables</pre>,
					rightEntry: (
						<>
							<pre>(string[]) fetchVariable</pre>
							<p>Only one type at a time.</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(string[]) topics[].tenantIdIn
							<br />
							(boolean) withoutTenantId
						</pre>
					),
					rightEntry: (
						<>
							<pre>(string[]) tenantIds</pre>
							<p>
								Map <code>withoutTenantId=true</code> to the{" "}
								<code>&lt;default&gt;</code> tenant alias and
								append it to <code>tenantIds</code> alongside
								any <code>tenantIdIn</code> values. Only one
								type at a time.
							</p>
						</>
					),
				},
			],
			additionalInfo: "",
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(boolean) usePriority</pre>,
					rightEntry: (
						<p>
							The Camunda 8.10 job activation request has no
							<code>usePriority</code> flag. Job priority can be
							updated separately through the Update job endpoint.
						</p>
					),
				},
				{
					leftEntry: <pre>(string) topics[].businessKey</pre>,
					rightEntry: (
						<p>
							Camunda 8.10 does not support activation-time
							filtering by business ID. Jobs from process
							instances created in Camunda 8.10 expose{" "}
							<code>businessId</code> in the activation response,
							so it can be used after activation.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) topics[].processDefinitionId
							<br />
							(string[]) topics[].processDefinitionIdIn
							<br />
							(string) topics[].processDefinitionKey
							<br />
							(string[]) topics[].processDefinitionKeyIn
							<br />
							(string) topics[].processDefinitionVersionTag
						</pre>
					),
					rightEntry: (
						<p>
							In Camunda 8.10, it is not possible to restrict the
							activation of jobs to specific process definitions.
						</p>
					),
				},
				{
					leftEntry: <pre>(object) topics[].processVariables</pre>,
					rightEntry: (
						<p>
							In Camunda 8.10, it is not possible to restrict the
							activation of jobs to process instances with
							specific variables and their values.
						</p>
					),
				},
				{
					leftEntry: <pre>(boolean) topics[].deserializeValues</pre>,
					rightEntry: <p>Not applicable in Camunda 8, only JSON.</p>,
				},
				{
					leftEntry: (
						<pre>(boolean) topics[].includeExtensionProperties</pre>
					),
					rightEntry: (
						<p>
							Extension properties are always included in the
							response as <code>customHeaders</code>.
						</p>
					),
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/external-task/retries",
			operation: "put",
		},
		target: {
			path: "/jobs/batch-update",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(int32) retries</pre>,
					rightEntry: <pre>(int32) changeset.retries</pre>,
				},
				{
					leftEntry: <pre>(string[]) externalTaskIds</pre>,
					rightEntry: (
						<>
							<pre>(string[]) filter.jobKey.$in</pre>
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
					leftEntry: (
						<pre>
							(string) externalTaskQuery.externalTaskId
							<br />
							(string[]) externalTaskQuery.externalTaskIdIn
							<br />
							(string) externalTaskQuery.topicName
							<br />
							(string) externalTaskQuery.workerId
							<br />
							(string) externalTaskQuery.processInstanceId
							<br />
							(string[]) externalTaskQuery.processInstanceIdIn
							<br />
							(string) externalTaskQuery.activityId
							<br />
							(string[]) externalTaskQuery.activityIdIn
							<br />
							(string[]) externalTaskQuery.tenantIdIn
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(string) filter.jobKey
								<br />
								(string[]) filter.jobKey.$in
								<br />
								(string) filter.type
								<br />
								(string) filter.worker
								<br />
								(string) filter.processInstanceKey
								<br />
								(string[]) filter.processInstanceKey.$in
								<br />
								(string) filter.elementId
								<br />
								(string[]) filter.elementId.$in
								<br />
								(string[]) filter.tenantId.$in
							</pre>
							<p>
								Use the advanced <code>$in</code> operator for
								array-valued criteria and the corresponding
								advanced string operators for C7{" "}
								<code>...Like</code> criteria.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(dateTime) externalTaskQuery.lockExpirationAfter
							<br />
							(dateTime) externalTaskQuery.lockExpirationBefore
							<br />
							(boolean) externalTaskQuery.withRetriesLeft
							<br />
							(boolean) externalTaskQuery.noRetriesLeft
							<br />
							(int64) externalTaskQuery.priorityHigherThanOrEquals
							<br />
							(int64) externalTaskQuery.priorityLowerThanOrEquals
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(dateTime) filter.deadline.$gt
								<br />
								(dateTime) filter.deadline.$lt
								<br />
								(int32) filter.retries.$gt
								<br />
								(int32) filter.retries.$eq
								<br />
								(int32) filter.priority.$gte
								<br />
								(int32) filter.priority.$lte
							</pre>
							<p>
								Use the advanced comparison operators shown
								above; these criteria must not be copied as
								top-level fields.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(string[]) processInstanceQuery.processInstanceIds
							<br />
							(string) processInstanceQuery.processDefinitionKey
							<br />
							(string[]) processInstanceQuery.processDefinitionKeyIn
							<br />
							(string[]) processInstanceQuery.tenantIdIn
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(string) filter.processInstanceKey
								<br />
								(string[]) filter.processInstanceKey.$in
								<br />
								(string) filter.processDefinitionId
								<br />
								(string[]) filter.processDefinitionId.$in
								<br />
								(string[]) filter.tenantId.$in
							</pre>
							<p>
								Translate each supported field into the nested{" "}
								<code>filter</code> object. A C7 process
								definition key maps to the C8 process definition
								ID; a versioned C7 definition ID is not
								interchangeable with it.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) externalTaskQuery.processDefinitionId
							<br />
							(string) processInstanceQuery.processDefinitionId
						</pre>
					),
					rightEntry: (
						<>
							<pre>(string) filter.processDefinitionKey</pre>
							<p>
								Translate each version-specific Camunda 7
								process definition ID to the corresponding
								Camunda 8 process definition key before
								applying this filter.
							</p>
						</>
					),
				},
			],
			additionalInfo: (
				<>
					<p>
						The Camunda 8.10 Update jobs (batch) endpoint is
						asynchronous; its batch operation key can be used to
						track progress.
					</p>
					<p>
						Do not pass either Camunda 7 query object directly as a
						Camunda 8 <code>filter</code>. Translate only the
						supported fields listed above and omit unsupported
						criteria.
					</p>
					<p>
						Camunda 7 unions <code>externalTaskIds</code>,{" "}
						<code>processInstanceIds</code>, and
						query-selected tasks. Because fields in one Camunda 8{" "}
						<code>filter</code> are conjunctive, issue separate
						batch-update requests for each supported selector and
						remove overlapping job keys before subsequent requests
						to avoid updating a job twice.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(boolean) externalTaskQuery.locked
							<br />
							(boolean) externalTaskQuery.notLocked
							<br />
							(boolean) externalTaskQuery.active
							<br />
							(boolean) externalTaskQuery.suspended
							<br />
							(string) externalTaskQuery.executionId
							<br />
							(object) externalTaskQuery.sorting
							<br />
							(string) processInstanceQuery.deploymentId
							<br />
							(string) processInstanceQuery.businessKey
							<br />
							(string) processInstanceQuery.businessKeyLike
							<br />
							(string) processInstanceQuery.caseInstanceId
							<br />
							(string) processInstanceQuery.superProcessInstance
							<br />
							(string) processInstanceQuery.subProcessInstance
							<br />
							(boolean) processInstanceQuery.active
							<br />
							(boolean) processInstanceQuery.suspended
							<br />
							(object) processInstanceQuery.variables
							<br />
							(boolean) processInstanceQuery.withIncident
						</pre>
					),
					rightEntry: (
						<p>
							These Camunda 7 criteria have no equivalent in the
							Camunda 8 active job filter. Do not include them in
							the batch update request; apply any required
							filtering in a separate migration step.
						</p>
					),
				},
				{
					leftEntry: <pre>(object) historicProcessInstanceQuery</pre>,
					rightEntry: (
						<p>
							Historic process instance query fields do not have
							a direct equivalent in the active job batch update
							filter.
						</p>
					),
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/external-task/retries-async",
			operation: "post",
		},
		target: {
			path: "/jobs/batch-update",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(int32) retries</pre>,
					rightEntry: <pre>(int32) changeset.retries</pre>,
				},
				{
					leftEntry: <pre>(string[]) externalTaskIds</pre>,
					rightEntry: (
						<>
							<pre>(string[]) filter.jobKey.$in</pre>
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
					leftEntry: (
						<pre>
							(string) externalTaskQuery.externalTaskId
							<br />
							(string[]) externalTaskQuery.externalTaskIdIn
							<br />
							(string) externalTaskQuery.topicName
							<br />
							(string) externalTaskQuery.workerId
							<br />
							(string) externalTaskQuery.processInstanceId
							<br />
							(string[]) externalTaskQuery.processInstanceIdIn
							<br />
							(string) externalTaskQuery.activityId
							<br />
							(string[]) externalTaskQuery.activityIdIn
							<br />
							(string[]) externalTaskQuery.tenantIdIn
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(string) filter.jobKey
								<br />
								(string[]) filter.jobKey.$in
								<br />
								(string) filter.type
								<br />
								(string) filter.worker
								<br />
								(string) filter.processInstanceKey
								<br />
								(string[]) filter.processInstanceKey.$in
								<br />
								(string) filter.elementId
								<br />
								(string[]) filter.elementId.$in
								<br />
								(string[]) filter.tenantId.$in
							</pre>
							<p>
								Use the advanced <code>$in</code> operator for
								array-valued criteria and the corresponding
								advanced string operators for C7{" "}
								<code>...Like</code> criteria.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(dateTime) externalTaskQuery.lockExpirationAfter
							<br />
							(dateTime) externalTaskQuery.lockExpirationBefore
							<br />
							(boolean) externalTaskQuery.withRetriesLeft
							<br />
							(boolean) externalTaskQuery.noRetriesLeft
							<br />
							(int64) externalTaskQuery.priorityHigherThanOrEquals
							<br />
							(int64) externalTaskQuery.priorityLowerThanOrEquals
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(dateTime) filter.deadline.$gt
								<br />
								(dateTime) filter.deadline.$lt
								<br />
								(int32) filter.retries.$gt
								<br />
								(int32) filter.retries.$eq
								<br />
								(int32) filter.priority.$gte
								<br />
								(int32) filter.priority.$lte
							</pre>
							<p>
								Use the advanced comparison operators shown
								above; these criteria must not be copied as
								top-level fields.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(string[]) processInstanceQuery.processInstanceIds
							<br />
							(string) processInstanceQuery.processDefinitionKey
							<br />
							(string[]) processInstanceQuery.processDefinitionKeyIn
							<br />
							(string[]) processInstanceQuery.tenantIdIn
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(string) filter.processInstanceKey
								<br />
								(string[]) filter.processInstanceKey.$in
								<br />
								(string) filter.processDefinitionId
								<br />
								(string[]) filter.processDefinitionId.$in
								<br />
								(string[]) filter.tenantId.$in
							</pre>
							<p>
								Translate each supported field into the nested{" "}
								<code>filter</code> object. A C7 process
								definition key maps to the C8 process definition
								ID; a versioned C7 definition ID is not
								interchangeable with it.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) externalTaskQuery.processDefinitionId
							<br />
							(string) processInstanceQuery.processDefinitionId
						</pre>
					),
					rightEntry: (
						<>
							<pre>(string) filter.processDefinitionKey</pre>
							<p>
								Translate each version-specific Camunda 7
								process definition ID to the corresponding
								Camunda 8 process definition key before
								applying this filter.
							</p>
						</>
					),
				},
			],
			additionalInfo: (
				<>
					<p>
						The Camunda 8.10 Update jobs (batch) endpoint is
						asynchronous; its batch operation key can be used to
						track progress.
					</p>
					<p>
						Do not pass either Camunda 7 query object directly as a
						Camunda 8 <code>filter</code>. Translate only the
						supported fields listed above and omit unsupported
						criteria.
					</p>
					<p>
						Camunda 7 unions <code>externalTaskIds</code>,{" "}
						<code>processInstanceIds</code>, and
						query-selected tasks. Because fields in one Camunda 8{" "}
						<code>filter</code> are conjunctive, issue separate
						batch-update requests for each supported selector and
						remove overlapping job keys before subsequent requests
						to avoid updating a job twice.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(boolean) externalTaskQuery.locked
							<br />
							(boolean) externalTaskQuery.notLocked
							<br />
							(boolean) externalTaskQuery.active
							<br />
							(boolean) externalTaskQuery.suspended
							<br />
							(string) externalTaskQuery.executionId
							<br />
							(object) externalTaskQuery.sorting
							<br />
							(string) processInstanceQuery.deploymentId
							<br />
							(string) processInstanceQuery.businessKey
							<br />
							(string) processInstanceQuery.businessKeyLike
							<br />
							(string) processInstanceQuery.caseInstanceId
							<br />
							(string) processInstanceQuery.superProcessInstance
							<br />
							(string) processInstanceQuery.subProcessInstance
							<br />
							(boolean) processInstanceQuery.active
							<br />
							(boolean) processInstanceQuery.suspended
							<br />
							(object) processInstanceQuery.variables
							<br />
							(boolean) processInstanceQuery.withIncident
						</pre>
					),
					rightEntry: (
						<p>
							These Camunda 7 criteria have no equivalent in the
							Camunda 8 active job filter. Do not include them in
							the batch update request; apply any required
							filtering in a separate migration step.
						</p>
					),
				},
				{
					leftEntry: <pre>(object) historicProcessInstanceQuery</pre>,
					rightEntry: (
						<p>
							Historic process instance query fields do not have
							a direct equivalent in the active job batch update
							filter.
						</p>
					),
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/external-task/topic-names",
			operation: "get",
		},
		target: {
			path: "/jobs/search",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				The Camunda 8.10 Search jobs endpoint can search by the
				<code>type</code> field. External task topic names correspond to
				job types in Camunda 8, but the endpoint is paginated and can
				return terminal jobs. Page through all results, retain only
				current jobs, and collect unique <code>type</code> values.
				Translate <code>withLockedTasks</code>,{" "}
				<code>withUnlockedTasks</code>, and{" "}
				<code>withRetriesLeft</code> using the job's worker, deadline,
				and retries fields. There is no exact single-request
				equivalent, so unsupported filter combinations require
				client-side handling.
			</div>
		),
	},
	{
		origin: {
			path: "/external-task/{id}",
			operation: "get",
		},
		target: {
			path: "/jobs/search",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				In Camunda 8.10, the <code>POST Search jobs</code> endpoint can
				be used to retrieve a specific job by filtering on{" "}
				<code>jobKey</code>.
			</div>
		),
	},
	{
		origin: {
			path: "/external-task/{id}/bpmnError",
			operation: "post",
		},
		target: {
			path: "/jobs/{jobKey}/error",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: <pre>(string) jobKey</pre>,
				},
				{
					leftEntry: <pre>(string) errorCode</pre>,
					rightEntry: <pre>(string) errorCode</pre>,
				},
				{
					leftEntry: <pre>(string) errorMessage</pre>,
					rightEntry: <pre>(string) errorMessage</pre>,
				},
				{
					leftEntry: <pre>(object) variables</pre>,
					rightEntry: <pre>(object) variables</pre>,
				},
			],
			additionalInfo: (
				<>
					<p>
						The Camunda 8.10 <code>variables</code> are created in
						the catching error event's local scope. Map Camunda 7{" "}
						<code>variables</code> entries by unwrapping each{" "}
						<code>VariableValueDto</code> and sending only its{" "}
						<code>value</code> as the raw JSON value. Do not copy
						the <code>type</code> or <code>valueInfo</code>{" "}
						metadata.
					</p>
					<p>
						When jobs are activated with{" "}
						<code>withLease: true</code>, pass the returned{" "}
						<code>leaseToken</code> in this request.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) workerId</pre>,
					rightEntry: (
						<p>Not required to throw a BPMN error for a job.</p>
					),
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/external-task/{id}/complete",
			operation: "post",
		},
		target: {
			path: "/jobs/{jobKey}/completion",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: <pre>(string) jobKey</pre>,
				},
				{
					leftEntry: <pre>(object) variables</pre>,
					rightEntry: <pre>(object) variables</pre>,
				},
			],
			additionalInfo: (
				<>
					<p>
						Camunda 7 <code>variables</code> entries are{" "}
						<code>VariableValueDto</code> wrappers. Unwrap each
						entry and send only its <code>value</code> as the raw
						JSON value; do not copy the <code>type</code> or{" "}
						<code>valueInfo</code> metadata.
					</p>
					<p>
						When jobs are activated with{" "}
						<code>withLease: true</code>, pass the returned{" "}
						<code>leaseToken</code> in this request.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(object) localVariables</pre>,
					rightEntry: (
						<p>
							Camunda 7 local variables are not included in the
							Camunda 8.10 completion request. Update them
							separately with a scope-aware variable update.
						</p>
					),
				},
				{
					leftEntry: <pre>(string) workerId</pre>,
					rightEntry: <p>Not required to complete a job.</p>,
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/external-task/{id}/errorDetails",
			operation: "get",
		},
		target: {},
		discontinuedExplanation: (
			<div>
				There is no endpoint in Camunda 8.10 yet to retrieve the error
				details for a specific running job.
			</div>
		),
	},
	{
		origin: {
			path: "/external-task/{id}/extendLock",
			operation: "post",
		},
		target: {
			path: "/jobs/{jobKey}",
			operation: "patch",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: <pre>(string) jobKey</pre>,
				},
				{
					leftEntry: <pre>(int64) newDuration</pre>,
					rightEntry: <pre>(int64) changeset.timeout</pre>,
				},
				{
					leftEntry: <pre>(string) workerId</pre>,
					rightEntry: (
						<>
							<pre>(string) leaseToken</pre>
							<p>
								Use the token returned when activating the job
								with <code>withLease: true</code> to preserve
								worker-ownership fencing.
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
			path: "/external-task/{id}/failure",
			operation: "post",
		},
		target: {
			path: "/jobs/{jobKey}/failure",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: <pre>(string) jobKey</pre>,
				},
				{
					leftEntry: <pre>(string) errorMessage</pre>,
					rightEntry: <pre>(string) errorMessage</pre>,
				},
				{
					leftEntry: (
						<pre>
							(int32) retries
							<br />
							(int64) retryTimeout
						</pre>
					),
					rightEntry: (
						<pre>
							(int32) retries
							<br />
							(int64) retryBackOff
						</pre>
					),
				},
				{
					leftEntry: <pre>(object) localVariables</pre>,
					rightEntry: <pre>(object) variables</pre>,
				},
			],
			additionalInfo: (
				<>
					<p>
						Camunda 7 <code>localVariables</code> entries are{" "}
						<code>VariableValueDto</code> wrappers. Unwrap each
						entry and send only its <code>value</code> as the raw
						JSON value in Camunda 8 <code>variables</code>; do not
						copy the <code>type</code> or{" "}
						<code>valueInfo</code> metadata.
					</p>
					<p>
						When jobs are activated with{" "}
						<code>withLease: true</code>, pass the returned{" "}
						<code>leaseToken</code> in this request.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) errorDetails</pre>,
					rightEntry: (
						<p>
							In Camunda 8.10, there is no additional field for
							error details.
						</p>
					),
				},
				{
					leftEntry: <pre>(object) variables</pre>,
					rightEntry: (
						<p>
							Camunda 7 non-local variables are propagated beyond
							the external task. The Camunda 8.10 failure
							endpoint only creates variables in the job task's
							local scope, so update non-local variables
							separately with a scope-aware variable update.
						</p>
					),
				},
				{
					leftEntry: <pre>(string) workerId</pre>,
					rightEntry: (
						<p>Not necessary to report a failure for a job.</p>
					),
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/external-task/{id}/lock",
			operation: "post",
		},
		target: {},
		discontinuedExplanation: (
			<p>
				Camunda 8.10 jobs are acquired through the{" "}
				<code>POST /jobs/activation</code> endpoint by job type. The{" "}
				<code>PATCH /jobs/&#123;jobKey&#125;</code> endpoint only updates
				job fields such as the timeout; it does not activate or assign a
				job, so explicit lock-by-ID is not available.
			</p>
		),
	},
	{
		origin: {
			path: "/external-task/{id}/priority",
			operation: "put",
		},
		target: {
			path: "/jobs/{jobKey}",
			operation: "patch",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: <pre>(string) jobKey</pre>,
				},
				{
					leftEntry: <pre>(int64) priority</pre>,
					rightEntry: <pre>(int32) changeset.priority</pre>,
				},
			],
			additionalInfo: (
				<p>
					Job priority is supported by the Camunda 8.10 Update job
					endpoint. Camunda 7 accepts a signed 64-bit priority, while
					Camunda 8.10 accepts a signed 32-bit value; validate the
					priority is within the signed 32-bit range before sending
					it.
				</p>
			),
		},
	},
	{
		origin: {
			path: "/external-task/{id}/retries",
			operation: "put",
		},
		target: {
			path: "/jobs/{jobKey}",
			operation: "patch",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: <pre>(string) jobKey</pre>,
				},
				{
					leftEntry: <pre>(int32) retries</pre>,
					rightEntry: <pre>(int32) changeset.retries</pre>,
				},
			],
			additionalInfo: (
				<>
					<p>
						In Camunda 8.10, this endpoint cannot be used to raise an
						incident. The provided number of retries has to be a
						positive number.
					</p>
					<p>
						Use the <code>POST Fail job</code> endpoint instead.
						With no remaining retries, an incident will be raised.
					</p>
				</>
			),
		},
	},
	{
		origin: {
			path: "/external-task/{id}/unlock",
			operation: "post",
		},
		target: {},
		discontinuedExplanation: (
			<p>
				There is no Camunda 8.10 endpoint to unlock a job without
				completing it. Completing a job advances the process and is not
				equivalent to unlocking an external task.
			</p>
		),
	},
];
