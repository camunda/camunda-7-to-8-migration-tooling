export const task_local_variable_7_23_to_8_8 = [
	{
		origin: {
			path: "/task/{id}/localVariables",
			operation: "get",
		},
		target: {
			path: "/user-tasks/{userTaskKey}/variables/search",
			operation: "post",
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
					This endpoint returns all variables visible from the user
					task. The scope of each variable is provided by the field{" "}
					<code>scopeKey</code>.
				</p>
			</>
		),
	},
	{
		origin: {
			path: "/task/{id}/localVariables",
			operation: "post",
		},
		target: {
			path: "/element-instances/{elementInstanceKey}/variables",
			operation: "put",
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
								<pre>(object[]) modificaitons</pre>
							</td>
							<td>
								<pre>(object[]) variables</pre>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					Set the boolean <code>local</code> to <code>true</code> to
					strictly merge the variables into the local scope.
				</p>
			</>
		),
		notPossible: (
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
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) elementInstanceKey</pre>
								<p>
									The <code>elementInstanceKey</code> is not
									the <code>userTaskKey</code>. You can get
									the <code>elementInstanceKey</code> of the
									user task by retrieving the user task via
									the <code>GET Get user task</code> endpoint.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string[]) deletions</pre>
							</td>
							<td>
								<p>
									It is not possible to delete process
									variables in Camunda 8.8.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
	},
	{
		origin: {
			path: "/task/{id}/localVariables/{varName}",
			operation: "delete",
		},
		target: {},
		explanation:
			"In Camunda 8.8, there is no endpoint to delete a process variables. You can update it to null or an empty string.",
	},
	{
		origin: {
			path: "/task/{id}/localVariables/{varName}",
			operation: "get",
		},
		target: {
			path: "/user-tasks/{userTaskKey}/variables/search",
			operation: "post",
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
						<tr>
							<td>
								<pre>(string) varName</pre>
							</td>
							<td>
								<pre>(string) name</pre>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					This endpoint returns all variables visible from the user
					task. The scope of each variable is provided by the field{" "}
					<code>scopeKey</code>.
				</p>
			</>
		),
	},
	{
		origin: {
			path: "/task/{id}/localVariables/{varName}",
			operation: "put",
		},
		target: {
			path: "/element-instances/{elementInstanceKey}/variables",
			operation: "put",
		},
		notPossible: (
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
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) elementInstanceKey</pre>
								<p>
									The <code>elementInstanceKey</code> is not
									the <code>userTaskKey</code>. You can get
									the <code>elementInstanceKey</code> of the
									user task by retrieving the user task via
									the <code>GET Get user task</code> endpoint.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
	},
	{
		origin: {
			path: "/task/{id}/localVariables/{varName}/data",
			operation: "get",
		},
		target: {
			path: "/documents/{documentId}",
			operation: "get",
		},
		explanation:
			"In Camunda 8.8, documents can be uploaded an downloaded via file picker components in forms. The documents are not directly related to user tasks. Instead, the documentIds can be saved in a process variable to access the documents later in the process instance.",
	},
	{
		origin: {
			path: "/task/{id}/localVariables/{varName}/data",
			operation: "post",
		},
		target: {
			path: "/documents",
			operation: "post",
		},
		explanation:
			"In Camunda 8.8, documents can be uploaded an downloaded via file picker components in forms. The documents are not directly related to user tasks. Instead, the documentIds can be saved in a process variable to access the documents later in the process instance.",
	},
];
