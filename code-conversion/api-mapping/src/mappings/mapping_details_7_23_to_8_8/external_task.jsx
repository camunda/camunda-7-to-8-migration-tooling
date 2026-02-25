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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) externalTaskId</pre>,
					rightEntry: <pre>(string) filter.jobKey</pre>,
				},
				{
					leftEntry: <pre>(string) topicName</pre>,
					rightEntry: <pre>(string) filter.type</pre>,
				},
				{
					leftEntry: <pre>(string) workerId</pre>,
					rightEntry: <pre>(string) filter.worker</pre>,
				},
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
					leftEntry: <pre>(string) processInstanceId</pre>,
					rightEntry: (
						<>
							<pre>(string) filter.processInstanceKey</pre>
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
					leftEntry: <pre>(string) activityId</pre>,
					rightEntry: <pre>(string) filter.elementId</pre>,
				},
				{
					leftEntry: (
						<pre>
							(string[]) tenantIdIn
							<br />
							(boolean) withoutTenantId
						</pre>
					),
					rightEntry: <pre>(string) filter.tenantId</pre>,
				},
				{
					leftEntry: <pre>(int32) retries</pre>,
					rightEntry: <pre>(int32) filter.retries</pre>,
				},
				{
					leftEntry: <pre>(string) sortBy / sortOrder</pre>,
					rightEntry: <pre>(object[]) sort</pre>,
				},
			],
			additionalInfo: "",
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(boolean) active / suspended</pre>,
					rightEntry: (
						<p>
							Use <code>filter.state</code> to filter by job
							state in Camunda 8.8.
						</p>
					),
				},
				{
					leftEntry: <pre>(boolean) locked / notLocked</pre>,
					rightEntry: (
						<p>
							No direct equivalent in Camunda 8.8.
						</p>
					),
				},
				{
					leftEntry: <pre>(boolean) withRetriesLeft</pre>,
					rightEntry: (
						<p>
							Use <code>filter.retries</code> to filter by
							retries in Camunda 8.8.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) lockExpirationBefore
							<br />
							(string) lockExpirationAfter
						</pre>
					),
					rightEntry: (
						<p>
							Use <code>filter.deadline</code> to filter by
							deadline in Camunda 8.8.
						</p>
					),
				},
				{
					leftEntry: <pre>(int64) priorityHigherThanOrEquals / priorityLowerThanOrEquals</pre>,
					rightEntry: <p>No job priority in Camunda 8.8.</p>,
				},
				{
					leftEntry: <pre>(string[]) activityIdIn</pre>,
					rightEntry: (
						<p>
							Use <code>filter.elementId</code>, but only one
							value at a time.
						</p>
					),
				},
			],
			additionalInfo: "",
		},
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
			<p>
				See <code>Get List</code> endpoint for details.
			</p>
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
			<p>
				See <code>Get List</code> endpoint for details.
			</p>
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
			<p>
				See <code>Get List</code> endpoint for details.
			</p>
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
					rightEntry: <pre>(string) worker</pre>,
				},
				{
					leftEntry: <pre>(int32) maxTasks</pre>,
					rightEntry: <pre>(int32) maxJobsToActivate</pre>,
				},
				{
					leftEntry: <pre>(int64) asyncResponseTimeout</pre>,
					rightEntry: <pre>(int64) requestTimeout</pre>,
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
							<p>Only one type at a time.</p>
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
					rightEntry: <p>No job priority in Camunda 8.8.</p>,
				},
				{
					leftEntry: <pre>(string) topics[].businessKey</pre>,
					rightEntry: (
						<p>
							No businessKey in Camunda 8.8. <a href="https://roadmap.camunda.com/c/296-business-key">Planned for a future release</a>, but unlikly to be required in this context.
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
							In Camunda 8.8, it is not possible to retrict the
							activation of jobs to specific process definitions.
						</p>
					),
				},
				{
					leftEntry: <pre>(object) topics[].processVariables</pre>,
					rightEntry: (
						<p>
							In Camunda 8.8, it is not possible to retrict the
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
			path: "/jobs/{jobKey}",
			operation: "patch",
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
							<pre>(string) jobKey</pre>
							<p>One job at a time.</p>
						</>
					),
				},
			],
			additionalInfo: (
				<>
					<p>
						In Camunda 8.8, this endpoint cannot be used to raise an
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
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string[]) processInstanceIds</pre>,
					rightEntry: (
						<p>Not appliable, as one specified job is patched.</p>
					),
				},
				{
					leftEntry: <pre>(object) externalTaskQuery</pre>,
					rightEntry: (
						<p>Not appliable, as one specified job is patched.</p>
					),
				},
				{
					leftEntry: <pre>(object) processInstanceQuery</pre>,
					rightEntry: (
						<p>Not appliable, as one specified job is patched.</p>
					),
				},
				{
					leftEntry: <pre>(object) historicProcessInstanceQuery</pre>,
					rightEntry: (
						<p>Not appliable, as one specified job is patched.</p>
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
			path: "/jobs/{jobKey}",
			operation: "patch",
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
							<pre>(string) jobKey</pre>
							<p>One job at a time.</p>
						</>
					),
				},
				{
					leftEntry: <pre>(object) processInstanceQuery</pre>,
					rightEntry: (
						<p>Not appliable, as one specified job is patched.</p>
					),
				},
				{
					leftEntry: <pre>(object) historicProcessInstanceQuery</pre>,
					rightEntry: (
						<p>Not appliable, as one specified job is patched.</p>
					),
				},
			],
			additionalInfo: (
				<>
					<p>
						In Camunda 8.8, this endpoint cannot be used to raise an
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
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string[]) processInstanceIds</pre>,
					rightEntry: (
						<p>Not appliable, as one specified job is patched.</p>
					),
				},
				{
					leftEntry: <pre>(object) externalTaskQuery</pre>,
					rightEntry: (
						<p>Not appliable, as one specified job is patched.</p>
					),
				},
				{
					leftEntry: <pre>(object) processInstanceQuery</pre>,
					rightEntry: (
						<p>Not appliable, as one specified job is patched.</p>
					),
				},
				{
					leftEntry: <pre>(object) historicProcessInstanceQuery</pre>,
					rightEntry: (
						<p>Not appliable, as one specified job is patched.</p>
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
		target: {},
		discontinuedExplanation: (
			<div>
				There is no endpoint in Camunda 8.8 yet to search for jobs
				types.
			</div>
		),
	},
	{
		origin: {
			path: "/external-task/{id}",
			operation: "get",
		},
		target: {},
		discontinuedExplanation: (
			<div>
				There is no endpoint in Camunda 8.8 yet to retrieve a specific
				job by <code>jobKey</code>.
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
			additionalInfo: "",
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
			additionalInfo: "",
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(object) localVariables</pre>,
					rightEntry: (
						<p>
							For this endpoint in Camunda 8.8, local variables
							cannot be set. All variables are treated the same.
							If they are defined as local on the task, they will
							be merged into the task scope only. If not, they
							will be merged to all parent scopes or until the
							variable is defined as local in a scope.
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
				There is no endpoint in Camunda 8.8 yet to retrieve the error
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
			],
			additionalInfo: "",
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) workerId</pre>,
					rightEntry: (
						<p>Not necessary to patch the timeout of a job.</p>
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
					leftEntry: <pre>(object) variables</pre>,
					rightEntry: <pre>(object) variables</pre>,
				},
			],
			additionalInfo: "",
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) errorDetails</pre>,
					rightEntry: (
						<p>
							In Camunda 8.8, there is not additional field for
							error details.
						</p>
					),
				},
				{
					leftEntry: <pre>(object) localVariables</pre>,
					rightEntry: (
						<p>
							For this endpoint in Camunda 8.8, local variables
							cannot be set. All variables are treated the same.
							If they are defined as local on the task, they will
							be merged into the task scope only. If not, they
							will be merged to all parent scopes or until the
							variable is defined as local in a scope.
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
			],
			additionalInfo: "",
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) workerId</pre>,
					rightEntry: (
						<p>Not necessary to patch the timeout of a job.</p>
					),
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/external-task/{id}/priority",
			operation: "put",
		},
		target: {},
		discontinuedExplanation: (
			<p>There is no job priority yet in Camunda 8.8.</p>
		),
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
						In Camunda 8.8, this endpoint cannot be used to raise an
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
			],
			additionalInfo: (
				<p>
					Set <code>result.denied</code> to true to reject the
					completion and activate the job again.
				</p>
			),
		},
	},
];
