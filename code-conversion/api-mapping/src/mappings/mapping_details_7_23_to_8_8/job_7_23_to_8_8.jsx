export const job_7_23_to_8_8 = [
	// {
	// 	origin: {
	// 		path: "/job",
	// 		operation: "get",
	// 	},
	// 	target: {},
	// 	explanation:
	// 		"In Camunda 8.8, there is no endpoint to search all jobs. Instead, a specific set of jobs is activated by job type.",
	// },
	// {
	// 	origin: {
	// 		path: "/job",
	// 		operation: "post",
	// 	},
	// 	target: {},
	// 	explanation:
	// 		"In Camunda 8.8, there is no endpoint to search all jobs. Instead, a specific set of jobs is activated by job type.",
	// },
	// {
	// 	origin: {
	// 		path: "/job/count",
	// 		operation: "get",
	// 	},
	// 	target: {},
	// 	explanation:
	// 		"In Camunda 8.8, there is no endpoint to search all jobs. Instead, a specific set of jobs is activated by job type.",
	// },
	// {
	// 	origin: {
	// 		path: "/job/count",
	// 		operation: "post",
	// 	},
	// 	target: {},
	// 	explanation:
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
	// 	notPossible: (
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
	// 	explanation:
	// 		"Not possible in Camunda 8.8. Activating/suspending a process instance is on the roadmap of Camunda 8.",
	// },
	// {
	// 	origin: {
	// 		path: "/job/{id}",
	// 		operation: "delete",
	// 	},
	// 	target: {},
	// 	explanation: "It is not possible to delete a job in Camunda 8.8.",
	// },
	// {
	// 	origin: {
	// 		path: "/job/{id}",
	// 		operation: "get",
	// 	},
	// 	target: {},
	// 	explanation:
	// 		"In Camunda 8.8, there is no endpoint to get a specific job by id. Instead, a specific set of jobs is activated by job type.",
	// },
	// {
	// 	origin: {
	// 		path: "/job/{id}/duedate",
	// 		operation: "put",
	// 	},
	// 	target: {},
	// 	explanation: "DueDate is not applicable in Camunda 8.",
	// },
	// {
	// 	origin: {
	// 		path: "/job/{id}/duedate/recalculate",
	// 		operation: "post",
	// 	},
	// 	target: {},
	// 	explanation: "DueDate is not applicable in Camunda 8.",
	// },
];
