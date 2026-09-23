/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
export const job = [
	{
		origin: {
			path: "/job",
			operation: "get",
		},
		target: {
			path: "/jobs/search",
			operation: "post",
		},
		mappedExplanation:
			"In Camunda 8.10, the POST Search jobs endpoint can be used to search for jobs without activating them.",
	},
	{
		origin: {
			path: "/job",
			operation: "post",
		},
		target: {
			path: "/jobs/search",
			operation: "post",
		},
		mappedExplanation:
			"In Camunda 8.10, the POST Search jobs endpoint can be used to search for jobs without activating them.",
	},
	{
		origin: {
			path: "/job/count",
			operation: "get",
		},
		target: {
			path: "/jobs/search",
			operation: "post",
		},
		mappedExplanation:
			"In Camunda 8.10, the POST Search jobs endpoint can be used to search for jobs. The response includes a page.totalItems field that provides the total count of matching jobs.",
	},
	{
		origin: {
			path: "/job/count",
			operation: "post",
		},
		target: {
			path: "/jobs/search",
			operation: "post",
		},
		mappedExplanation:
			"In Camunda 8.10, the POST Search jobs endpoint can be used to search for jobs. The response includes a page.totalItems field that provides the total count of matching jobs.",
	},
	{
		origin: {
			path: "/job/retries",
			operation: "post",
		},
		target: {
			path: "/jobs/batch-update",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string[]) jobIds</pre>,
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
					leftEntry: <pre>(object) jobQuery</pre>,
					rightEntry: (
						<p>
							Do not copy <code>jobQuery</code> directly to{" "}
							<code>filter</code>. Translate supported criteria as
							described below; criteria without a listed mapping
							have no Camunda 8 equivalent.
						</p>
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
						Translate the supported <code>jobQuery</code> criteria
						as follows:
					</p>
					<pre>
						{`jobId → filter.jobKey
jobIds → filter.jobKey.$in
processInstanceId → filter.processInstanceKey
processInstanceIds → filter.processInstanceKey.$in
processDefinitionId → filter.processDefinitionKey
processDefinitionKey → filter.processDefinitionId
activityId → filter.elementId
failedActivityId → filter.elementId
withRetriesLeft=true → filter.retries.$gt=0
noRetriesLeft=true → filter.retries.$eq=0
createTimes → filter.creationTime.$gt / filter.creationTime.$lt
exceptionMessage → filter.errorMessage
priorityLowerThanOrEquals → filter.priority.$lte
priorityHigherThanOrEquals → filter.priority.$gte
tenantIdIn → filter.tenantId.$in
withoutTenantId=true → filter.tenantId="<default>"`}
					</pre>
					<p>
						Apply the{" "}
						<a href="#key-to-id">Camunda 7 key → Camunda 8 id</a>{" "}
						conversion to job and process-instance IDs. The
						<code>jobDefinitionId</code>, <code>executionId</code>,{" "}
						<code>executable</code>, <code>timers</code>,{" "}
						<code>messages</code>, 						<code>withException</code>,{" "}
						<code>active</code>, <code>suspended</code>, and{" "}
						<code>includeJobsWithoutTenantId</code> criteria have no
						direct equivalent in the Camunda 8.10 job filter. C7{" "}
						<code>dueDates</code> also has no direct equivalent:
						C7 due dates control when a job becomes executable, while
						the C8 <code>deadline</code> is an activated-job lease
						expiry.
						Sorting is not applicable to a batch update.
					</p>
					<p>
						Camunda 7 applies top-level <code>jobIds</code> and{" "}
						<code>jobQuery</code> as a union, while fields in one
						Camunda 8 <code>filter</code> are conjunctive. Issue
						separate batch-update requests for the ID selector and
						the translated query selector. Remove overlapping job
						keys before the second request to avoid updating a job
						twice.
					</p>
					<p>
						The Camunda 8.10 Update jobs (batch) endpoint is
						asynchronous; its batch operation key can be used to
						track progress.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(dateTime) dueDate</pre>,
					rightEntry: <p>Not applicable in Camunda 8.</p>,
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/job/{id}/priority",
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
			path: "/job/{id}/retries",
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
				<p>
					The Camunda 8.10 Update job endpoint updates the retry count
					synchronously.
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(dateTime) dueDate</pre>,
					rightEntry: <p>Not applicable in Camunda 8.</p>,
				},
			],
		},
	},
	{
		origin: {
			path: "/job/suspended",
			operation: "put",
		},
		target: {},
		discontinuedExplanation:
			"Not yet possible in Camunda 8.10. Activating/suspending a job is not supported.",
	},
	{
		origin: {
			path: "/job/{id}",
			operation: "delete",
		},
		target: {},
		discontinuedExplanation: "It is not possible to delete a job in Camunda 8.10.",
	},
	{
		origin: {
			path: "/job/{id}",
			operation: "get",
		},
		target: {
			path: "/jobs/search",
			operation: "post",
		},
		mappedExplanation:
			"In Camunda 8.10, the POST Search jobs endpoint can be used to retrieve a specific job by filtering on jobKey.",
	},
	{
		origin: {
			path: "/job/{id}/duedate",
			operation: "put",
		},
		target: {},
		discontinuedExplanation: "DueDate is not applicable in Camunda 8.",
	},
	{
		origin: {
			path: "/job/{id}/duedate/recalculate",
			operation: "post",
		},
		target: {},
		discontinuedExplanation: "DueDate is not applicable in Camunda 8.",
	},
];
