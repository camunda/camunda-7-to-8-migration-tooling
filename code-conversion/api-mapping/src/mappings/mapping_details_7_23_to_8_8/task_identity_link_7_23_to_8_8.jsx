export const task_identity_link_7_23_to_8_8 = [
	{
		origin: {
			path: "/task/{id}/identity-links",
			operation: "get",
		},
		target: {
			path: "/user-tasks/{userTaskKey}",
			operation: "get",
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
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) userTaskKey</pre>
								<p>
									See{" "}
									<a href="#key-to-id">
										Camunda 7 key → Camunda 8 id
									</a>
								</p>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					These is no specific API endpoint to retrieve the identity
					links in Camunda 8.8. Instead, retrieve the user task and
					extract the necessary information from the response.
				</p>
			</>
		),
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
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) userTaskKey</pre>
								<p>
									See{" "}
									<a href="#key-to-id">
										Camunda 7 key → Camunda 8 id
									</a>
								</p>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					This endpoint can be used to set{" "}
					<code>candidateGroups</code> and <code>candidateUsers</code>
					. To change the <code>assignee</code>, use the{" "}
					<code>POST Assign user task</code> endpoint.
				</p>
			</>
		),
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
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) userTaskKey</pre>
								<p>
									See{" "}
									<a href="#key-to-id">
										Camunda 7 key → Camunda 8 id
									</a>
								</p>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					This endpoint can be used to set{" "}
					<code>candidateGroups</code> and <code>candidateUsers</code>
					. A reset is achieved by providing an empty list. To
					unassign the <code>assignee</code>, use the{" "}
					<code>Delete Unassign user task</code> endpoint.
				</p>
			</>
		),
	},
];
