/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
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
					<code>scopeKey</code>.{" "}
					To match the Camunda 7 local-variable semantics, retrieve
					the user task's <code>elementInstanceKey</code> and keep
					only variables whose <code>scopeKey</code> equals it.
					This search is paginated with a default page limit of 100.
					Retrieve every page before filtering by{" "}
					<code>scopeKey</code>; the endpoint only supports{" "}
					<code>filter.name</code>, so the scope restriction must be
					applied client-side.
					Include <code>?truncateValues=false</code> in the request to
					return complete variable values.
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
					leftEntry: <pre>(object) modifications</pre>,
					rightEntry: <pre>(object) variables</pre>,
				},
			],
			additionalInfo: (
				<p>
					Set the boolean <code>local</code> to <code>true</code> to
					strictly merge the variables into the local scope. Camunda 7{" "}
					<code>modifications</code> entries are{" "}
					<code>VariableValueDto</code> wrappers. Unwrap each entry and
					send only its <code>value</code> as the corresponding raw JSON
					value in <code>variables</code>; do not copy the{" "}
					<code>type</code> or <code>valueInfo</code> metadata.
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
							Camunda 8.10.
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
		discontinuedExplanation: (
			<p>
				In Camunda 8.10, there is no endpoint to delete a process
				variable. As a non-equivalent workaround, update it to{" "}
				<code>null</code> or an empty string with{" "}
				<code>local: true</code> to keep the update in the local scope.
			</p>
		),
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
					rightEntry: <pre>(string) filter.name</pre>,
				},
			],
			additionalInfo: (
				<p>
					This endpoint returns all variables visible from the user
					task. The scope of each variable is provided by the field{" "}
					<code>scopeKey</code>.{" "}
					To match the Camunda 7 local-variable semantics, retrieve
					the user task's <code>elementInstanceKey</code> and keep
					only the variable whose <code>scopeKey</code> equals it. Use
					<code>filter.name</code> to find the variable named by{" "}
					<code>varName</code>.{" "}
					When filtering the returned results by <code>scopeKey</code>,
					retrieve every page first because the search has a default
					page limit of 100. The endpoint only supports{" "}
					<code>filter.name</code>, so apply the scope restriction
					client-side. Include{" "}
					<code>?truncateValues=false</code> in the request to return
					the complete value.
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
		direct: {
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
					leftEntry: <pre>(string) varName</pre>,
					rightEntry: <pre>(string) variables[varName]</pre>,
				},
				{
					leftEntry: <pre>(any) value</pre>,
					rightEntry: <pre>(any) variables[varName]</pre>,
				},
			],
			additionalInfo: (
				<p>
					Send the value as{" "}
					<code>
						{"{ variables: { [varName]: value }, local: true }"}
					</code>{" "}
					to keep the update in the task-local scope. Camunda 8
					stores variables as JSON, so convert Camunda 7 serialized
					values before sending them; the Camunda 7 <code>type</code>{" "}
					and <code>valueInfo</code> metadata have no direct
					equivalent.
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) type</pre>,
					rightEntry: (
						<p>
							Camunda 8 infers the variable type from the JSON
							value.
						</p>
					),
				},
				{
					leftEntry: <pre>(object) valueInfo</pre>,
					rightEntry: (
						<p>
							Camunda 8 does not accept Camunda 7 serialization
							metadata in this request.
						</p>
					),
				},
			],
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
			"In Camunda 8.10, documents can be uploaded and downloaded via file picker components in forms. The documents are not directly related to user tasks. Instead, the documentIds can be saved in a process variable to access the documents later in the process instance.",
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
			"In Camunda 8.10, documents can be uploaded and downloaded via file picker components in forms. The documents are not directly related to user tasks. Instead, the documentIds can be saved in a process variable to access the documents later in the process instance.",
	},
];
