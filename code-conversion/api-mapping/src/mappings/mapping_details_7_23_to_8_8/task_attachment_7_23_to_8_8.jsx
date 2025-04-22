export const task_attachment_7_23_to_8_8 = [
	{
		origin: {
			path: "/task/{id}/attachment",
			operation: "get",
		},
		target: {},
		explanation: (
			<>
				<div>
					In Camunda 8.8, documents can be uploaded and downloaded via
					file picker components in forms, but not retrieved by{" "}
					<code>userTaskKey</code> via the API. Documents are
					identified by documentIds, which are returned in the
					response when uploading documents. You can store these
					documentIds in a process variable to access the documents
					later in the process instance.
				</div>
				<div>
					For more information, take a look at{" "}
					<a
						href="https://docs.camunda.io/docs/8.8/guides/document-handling/"
						target="_blank"
					>
						the docs
					</a>
					.
				</div>
			</>
		),
	},
	{
		origin: {
			path: "/task/{id}/attachment/create",
			operation: "post",
		},
		target: {
			path: "/documents",
			operation: "post",
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
								<p>
									In Camunda 8.8, documents are not directly
									relatable to tasks. There are referened by
									documentIds which are returned on upload.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
				<div>
					For more information, take a look at{" "}
					<a
						href="https://docs.camunda.io/docs/8.8/guides/document-handling/"
						target="_blank"
					>
						the docs
					</a>
					.
				</div>
			</>
		),
	},
	{
		origin: {
			path: "/task/{id}/attachment/{attachmentId}",
			operation: "delete",
		},
		target: {
			path: "/documents/{documentId}",
			operation: "delete",
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
								<pre>(string) attachmentId</pre>
							</td>
							<td>
								<pre>(string) documentId</pre>
							</td>
						</tr>
					</tbody>
				</table>
				<div>
					For more information, take a look at{" "}
					<a
						href="https://docs.camunda.io/docs/8.8/guides/document-handling/"
						target="_blank"
					>
						the docs
					</a>
					.
				</div>
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
								<p>
									In Camunda 8.8, documents are not directly
									relatable to tasks. There are referened by
									documentIds which are returned on upload.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
				<div>
					For more information, take a look at{" "}
					<a
						href="https://docs.camunda.io/docs/8.8/guides/document-handling/"
						target="_blank"
					>
						the docs
					</a>
					.
				</div>
			</>
		),
	},
	{
		origin: {
			path: "/task/{id}/attachment/{attachmentId}",
			operation: "get",
		},
		target: {
			path: "/documents/{documentId}",
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
								<pre>(string) attachmentId</pre>
							</td>
							<td>
								<pre>(string) documentId</pre>
							</td>
						</tr>
					</tbody>
				</table>
				<div>
					For more information, take a look at{" "}
					<a
						href="https://docs.camunda.io/docs/8.8/guides/document-handling/"
						target="_blank"
					>
						the docs
					</a>
					.
				</div>
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
								<p>
									In Camunda 8.8, documents are not directly
									relatable to tasks. There are referened by
									documentIds which are returned on upload.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
				<div>
					For more information, take a look at{" "}
					<a
						href="https://docs.camunda.io/docs/8.8/guides/document-handling/"
						target="_blank"
					>
						the docs
					</a>
					.
				</div>
			</>
		),
	},
	{
		origin: {
			path: "/task/{id}/attachment/{attachmentId}/data",
			operation: "get",
		},
		target: {
			path: "/documents/{documentId}",
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
								<pre>(string) attachmentId</pre>
							</td>
							<td>
								<pre>(string) documentId</pre>
							</td>
						</tr>
					</tbody>
				</table>
				<div>
					For more information, take a look at{" "}
					<a
						href="https://docs.camunda.io/docs/8.8/guides/document-handling/"
						target="_blank"
					>
						the docs
					</a>
					.
				</div>
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
								<p>
									In Camunda 8.8, documents are not directly
									relatable to tasks. There are referened by
									documentIds which are returned on upload.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
				<div>
					For more information, take a look at{" "}
					<a
						href="https://docs.camunda.io/docs/8.8/guides/document-handling/"
						target="_blank"
					>
						the docs
					</a>
					.
				</div>
			</>
		),
	},
];
