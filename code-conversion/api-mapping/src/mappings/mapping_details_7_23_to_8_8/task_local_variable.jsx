export const task_local_variable = [
	{
		origin: {
			path: "/task/{id}/localVariables",
			operation: "get",
		},
		target: {
			path: "/user-tasks/{userTaskKey}/variables/search",
			operation: "post",
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
					This endpoint returns all variables visible from the user
					task. The scope of each variable is provided by the field{" "}
					<code>scopeKey</code>.
				</p>
			),
		},
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(object[]) modifications</pre>,
					rightEntry: <pre>(object[]) variables</pre>,
				},
			],
			additionalInfo: (
				<p>
					Set the boolean <code>local</code> to <code>true</code> to
					strictly merge the variables into the local scope.
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) elementInstanceKey</pre>
							<p>
								The <code>elementInstanceKey</code> is not the{" "}
								<code>userTaskKey</code>. You can get the
								<code>elementInstanceKey</code> of the user task
								by retrieving the user task via the
								<code>GET Get user task</code> endpoint.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string[]) deletions</pre>,
					rightEntry: (
						<p>
							It is not possible to delete process variables in
							Camunda 8.8.
						</p>
					),
				},
			],
		},
	},
	{
		origin: {
			path: "/task/{id}/localVariables/{varName}",
			operation: "delete",
		},
		target: {},
		mappedExplanation:
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
				{
					leftEntry: <pre>(string) varName</pre>,
					rightEntry: <pre>(string) name</pre>,
				},
			],
			additionalInfo: (
				<p>
					This endpoint returns all variables visible from the user
					task. The scope of each variable is provided by the field{" "}
					<code>scopeKey</code>.
				</p>
			),
		},
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
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) elementInstanceKey</pre>
							<p>
								The <code>elementInstanceKey</code> is not the{" "}
								<code>userTaskKey</code>. You can get the
								<code>elementInstanceKey</code> of the user task
								by retrieving the user task via the
								<code>GET Get user task</code> endpoint.
							</p>
						</>
					),
				},
			],
			additionalInfo: null, // No additional info to include here, but can be added if needed
		},
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
		mappedExplanation:
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
		mappedExplanation:
			"In Camunda 8.8, documents can be uploaded an downloaded via file picker components in forms. The documents are not directly related to user tasks. Instead, the documentIds can be saved in a process variable to access the documents later in the process instance.",
	},
];
