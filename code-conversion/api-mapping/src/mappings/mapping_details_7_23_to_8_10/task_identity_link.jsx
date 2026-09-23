/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
export const task_identity_link = [
	{
		origin: {
			path: "/task/{id}/identity-links",
			operation: "get",
		},
		target: {
			path: "/user-tasks/{userTaskKey}",
			operation: "get",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) userTaskKey</pre>
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
					There is no specific API endpoint to retrieve the identity
					links in Camunda 8.10. Instead, retrieve the user task and
					extract the assignee, candidate users, and candidate
					groups from the response. Owner and custom identity-link
					types have no equivalent in the Camunda 8.10 response.
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(string) owner
							<br />
							(string) custom identity-link type
						</pre>
					),
					rightEntry: (
						<p>
							These identity-link types are unavailable in the
							Camunda 8.10 user-task response and must be
							migrated separately.
						</p>
					),
				},
			],
		},
	},
	{
		origin: {
			path: "/task/{id}/identity-links",
			operation: "post",
		},
		target: [
			{
				path: "/user-tasks/{userTaskKey}",
				operation: "patch",
			},
			{
				path: "/user-tasks/{userTaskKey}/assignment",
				operation: "post",
			},
		],
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) userTaskKey</pre>
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
					This endpoint can be used to set{" "}
					<code>candidateGroups</code> and <code>candidateUsers</code>.
					Because the Camunda 8.10 PATCH replaces each candidate list,
					first retrieve the current user task, append the new
					identity to the relevant list, and submit the complete
					list to preserve existing candidates.
					To change the <code>assignee</code>, use the{" "}
					<code>POST Assign user task</code> endpoint.
				</p>
			),
		},
	},
	{
		origin: {
			path: "/task/{id}/identity-links/delete",
			operation: "post",
		},
		target: [
			{
				path: "/user-tasks/{userTaskKey}",
				operation: "patch",
			},
			{
				path: "/user-tasks/{userTaskKey}/assignee",
				operation: "delete",
			},
		],
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) userTaskKey</pre>
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
					This endpoint can be used to set{" "}
					<code>candidateGroups</code> and <code>candidateUsers</code>.
					Because the Camunda 8.10 PATCH replaces each candidate list,
					first retrieve the current user task, remove only the
					requested identity from the relevant list, and submit the
					complete remaining list. To unassign the{" "}
					<code>assignee</code>, use the{" "}
					<code>Unassign user task</code> endpoint.
				</p>
			),
		},
	},
];
