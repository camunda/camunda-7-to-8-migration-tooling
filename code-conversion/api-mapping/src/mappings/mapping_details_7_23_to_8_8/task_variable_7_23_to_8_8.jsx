export const task_variable_7_23_to_8_8 = [
	{
		origin: {
			path: "/task/{id}/variables",
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
			path: "/task/{id}/variables",
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
					Set the boolean <code>local</code> to <code>false</code> to
					merge the variables into all parent scopes or until the
					variable is defined as a local variable.
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
								<code>userTaskKey</code>. You can get the{" "}
								<code>elementInstanceKey</code> of the user task
								by retrieving the user task via the{" "}
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
			path: "/task/{id}/variables/{varName}",
			operation: "delete",
		},
		target: {},
		discontinuedExplanation:
			"In Camunda 8.8, there is no endpoint to delete a process variables. You can update it to null or an empty string.",
	},
	{
		origin: {
			path: "/task/{id}/variables/{varName}",
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
			path: "/task/{id}/variables/{varName}",
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
		},
	},
	{
		origin: {
			path: "/task/{id}/variables/{varName}/data",
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
			path: "/task/{id}/variables/{varName}/data",
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
