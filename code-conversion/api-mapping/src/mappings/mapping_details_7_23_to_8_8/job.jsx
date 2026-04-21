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
			"In Camunda 8.8, the POST Search jobs endpoint can be used to search for jobs without activating them.",
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
			"In Camunda 8.8, the POST Search jobs endpoint can be used to search for jobs without activating them.",
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
			"In Camunda 8.8, the POST Search jobs endpoint can be used to search for jobs. The response includes a page.totalItems field that provides the total count of matching jobs.",
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
			"In Camunda 8.8, the POST Search jobs endpoint can be used to search for jobs. The response includes a page.totalItems field that provides the total count of matching jobs.",
	},
	{
		origin: {
			path: "/job/retries",
			operation: "post",
		},
		target: {
			path: "/jobs/{jobKey}",
			operation: "patch",
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
								<pre>(string[]) jobIds</pre>
							</td>
							<td>
								<pre>(string) jobKey</pre>{" "}
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
								<pre>(integer) retries</pre>
							</td>
							<td>
								<pre>(int32) changeset.retries</pre>{" "}
							</td>
						</tr>
					</tbody>
				</table>
				<p>In Camunda 8.8, jobs are patched one at a time.</p>
			</>
		),
		discontinued: (
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
								<pre>(object) jobQuery</pre>
							</td>
							<td>
								<p>
									In Camunda 8.8, jobs are patched one at a
									time. Thus, it is not possible to define a
									set of jobs with a jobQuery.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(dateTime) dueDate</pre>
							</td>
							<td>
								<p>Not applicable in Camunda 8.</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
	},
	{
		origin: {
			path: "/job/suspended",
			operation: "put",
		},
		target: {},
		discontinuedExplanation:
			"Not yet possible in Camunda 8.8. Activating/suspending a job is not supported.",
	},
	{
		origin: {
			path: "/job/{id}",
			operation: "delete",
		},
		target: {},
		discontinuedExplanation:
			"It is not possible to delete a job in Camunda 8.8.",
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
			"In Camunda 8.8, the POST Search jobs endpoint can be used to retrieve a specific job by filtering on jobKey.",
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
