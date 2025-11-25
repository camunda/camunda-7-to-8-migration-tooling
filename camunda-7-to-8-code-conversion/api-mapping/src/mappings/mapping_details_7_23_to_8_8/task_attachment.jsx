export const task_attachment = [
	{
		origin: {
			path: "/task/{id}/attachment",
			operation: "get",
		},
		target: {},
		mappedExplanation: (
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
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<p>
							In Camunda 8.8, documents are not directly relatable
							to tasks. They are referenced by{" "}
							<code>documentIds</code> which are returned on
							upload.
						</p>
					),
				},
			],
			additionalInfo: (
				<p>
					For more information, take a look at{" "}
					<a
						href="https://docs.camunda.io/docs/8.8/guides/document-handling/"
						target="_blank"
					>
						the docs
					</a>
					.
				</p>
			),
		},
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) attachmentId</pre>,
					rightEntry: <pre>(string) documentId</pre>,
				},
			],
			additionalInfo: (
				<p>
					For more information, take a look at{" "}
					<a
						href="https://docs.camunda.io/docs/8.8/guides/document-handling/"
						target="_blank"
					>
						the docs
					</a>
					.
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<p>
							In Camunda 8.8, documents are not directly relatable
							to tasks. They are referenced by{" "}
							<code>documentIds</code> which are returned on
							upload.
						</p>
					),
				},
			],
			additionalInfo: (
				<p>
					For more information, take a look at{" "}
					<a
						href="https://docs.camunda.io/docs/8.8/guides/document-handling/"
						target="_blank"
					>
						the docs
					</a>
					.
				</p>
			),
		},
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) attachmentId</pre>,
					rightEntry: <pre>(string) documentId</pre>,
				},
			],
			additionalInfo: (
				<p>
					For more information, take a look at{" "}
					<a
						href="https://docs.camunda.io/docs/8.8/guides/document-handling/"
						target="_blank"
					>
						the docs
					</a>
					.
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<p>
							In Camunda 8.8, documents are not directly relatable
							to tasks. They are referenced by documentIds, which
							are returned on upload.
						</p>
					),
				},
			],
			additionalInfo: (
				<p>
					For more information, take a look at{" "}
					<a
						href="https://docs.camunda.io/docs/8.8/guides/document-handling/"
						target="_blank"
					>
						the docs
					</a>
					.
				</p>
			),
		},
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) attachmentId</pre>,
					rightEntry: <pre>(string) documentId</pre>,
				},
			],
			additionalInfo: (
				<p>
					For more information, take a look at{" "}
					<a
						href="https://docs.camunda.io/docs/8.8/guides/document-handling/"
						target="_blank"
					>
						the docs
					</a>
					.
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<p>
							In Camunda 8.8, documents are not directly relatable
							to tasks. They are referenced by documentIds which
							are returned on upload.
						</p>
					),
				},
			],
			additionalInfo: (
				<p>
					For more information, take a look at{" "}
					<a
						href="https://docs.camunda.io/docs/8.8/guides/document-handling/"
						target="_blank"
					>
						the docs
					</a>
					.
				</p>
			),
		},
	},
];
