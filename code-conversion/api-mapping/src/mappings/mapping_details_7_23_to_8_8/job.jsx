/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
export const job = [
	// {
	// 	origin: {
	// 		path: "/job",
	// 		operation: "get",
	// 	},
	// 	target: {},
	// 	mappedExplanation:
	// 		"In Camunda 8.8, there is no endpoint to search all jobs. Instead, a specific set of jobs is activated by job type.",
	// },
	// {
	// 	origin: {
	// 		path: "/job",
	// 		operation: "post",
	// 	},
	// 	target: {},
	// 	mappedExplanation:
	// 		"In Camunda 8.8, there is no endpoint to search all jobs. Instead, a specific set of jobs is activated by job type.",
	// },
	// {
	// 	origin: {
	// 		path: "/job/count",
	// 		operation: "get",
	// 	},
	// 	target: {},
	// 	mappedExplanation:
	// 		"In Camunda 8.8, there is no endpoint to search all jobs. Instead, a specific set of jobs is activated by job type.",
	// },
	// {
	// 	origin: {
	// 		path: "/job/count",
	// 		operation: "post",
	// 	},
	// 	target: {},
	// 	mappedExplanation:
	// 		"In Camunda 8.8, there is no endpoint to search all jobs. Instead, a specific set of jobs is activated by job type.",
	// },
	// {
	// 	origin: {
	// 		path: "/job/retries",
	// 		operation: "post",
	// 	},
	// 	target: {
	// 		path: "/jobs/{jobKey}",
	// 		operation: "patch",
	// 	},
	// 	direct: (
	// 		<>
	// 			<table>
	// 				<thead>
	// 					<tr>
	// 						<th>Camunda 7</th>
	// 						<th>Camunda 8</th>
	// 					</tr>
	// 				</thead>
	// 				<tbody>
	// 					<tr>
	// 						<td>
	// 							<pre>(string[]) jobIds</pre>
	// 						</td>
	// 						<td>
	// 							<pre>(string) jobKey</pre>{" "}
	// 							<p>
	// 								See{" "}
	// 								<a href="#key-to-id">
	// 									Camunda 7 key → Camunda 8 id
	// 								</a>
	// 							</p>
	// 						</td>
	// 					</tr>
	// 					<tr>
	// 						<td>
	// 							<pre>(integer) retries</pre>
	// 						</td>
	// 						<td>
	// 							<pre>(int32) changeset.retries</pre>{" "}
	// 						</td>
	// 					</tr>
	// 				</tbody>
	// 			</table>
	// 			<p>In Camunda 8.8, jobs are patched one at a time.</p>
	// 		</>
	// 	),
	// 	discontinued: (
	// 		<>
	// 			<table>
	// 				<thead>
	// 					<tr>
	// 						<th>Camunda 7</th>
	// 						<th>Explanation</th>
	// 					</tr>
	// 				</thead>
	// 				<tbody>
	// 					<tr>
	// 						<td>
	// 							<pre>(object) jobQuery</pre>
	// 						</td>
	// 						<td>
	// 							<p>
	// 								In Camunda 8.8, jobs are patched one at a
	// 								time. Thus, it is not possible to define a
	// 								set of jobs with a jobQuery.
	// 							</p>
	// 						</td>
	// 					</tr>
	// 					<tr>
	// 						<td>
	// 							<pre>(dateTime) dueDate</pre>
	// 						</td>
	// 						<td>
	// 							<p>Not applicable in Camunda 8.</p>
	// 						</td>
	// 					</tr>
	// 				</tbody>
	// 			</table>
	// 		</>
	// 	),
	// },
	// {
	// 	origin: {
	// 		path: "/job/suspended",
	// 		operation: "put",
	// 	},
	// 	target: {},
	// 	mappedExplanation:
	// 		"Not yet possible in Camunda 8.8. Activating/suspending a process instance is on the roadmap of Camunda 8.",
	// },
	// {
	// 	origin: {
	// 		path: "/job/{id}",
	// 		operation: "delete",
	// 	},
	// 	target: {},
	// 	mappedExplanation: "It is not possible to delete a job in Camunda 8.8.",
	// },
	// {
	// 	origin: {
	// 		path: "/job/{id}",
	// 		operation: "get",
	// 	},
	// 	target: {},
	// 	mappedExplanation:
	// 		"In Camunda 8.8, there is no endpoint to get a specific job by id. Instead, a specific set of jobs is activated by job type.",
	// },
	// {
	// 	origin: {
	// 		path: "/job/{id}/duedate",
	// 		operation: "put",
	// 	},
	// 	target: {},
	// 	mappedExplanation: "DueDate is not applicable in Camunda 8.",
	// },
	// {
	// 	origin: {
	// 		path: "/job/{id}/duedate/recalculate",
	// 		operation: "post",
	// 	},
	// 	target: {},
	// 	mappedExplanation: "DueDate is not applicable in Camunda 8.",
	// },
];
