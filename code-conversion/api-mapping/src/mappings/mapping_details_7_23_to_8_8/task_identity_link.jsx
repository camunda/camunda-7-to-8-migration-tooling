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
					links in Camunda 8.8. Instead, retrieve the user task and
					extract the necessary information from the response.
				</p>
			),
		},
	},
	{
		origin: {
			path: "/task/{id}/identity-links",
			operation: "post",
		},
		target: {
			path: "/user-tasks/{userTaskKey}",
			operation: "patch",
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
					This endpoint can be used to set{" "}
					<code>candidateGroups</code> and <code>candidateUsers</code>
					. To change the <code>assignee</code>, use the{" "}
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
		target: {
			path: "/user-tasks/{userTaskKey}",
			operation: "patch",
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
					This endpoint can be used to set{" "}
					<code>candidateGroups</code> and <code>candidateUsers</code>
					. A reset is achieved by providing an empty list. To
					unassign the <code>assignee</code>, use the{" "}
					<code>Delete Unassign user task</code> endpoint.
				</p>
			),
		},
	},
];
