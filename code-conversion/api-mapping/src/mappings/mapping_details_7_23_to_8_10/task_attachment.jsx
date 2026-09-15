/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
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
					In Camunda 8.10, documents can be uploaded and downloaded via
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
						href="https://docs.camunda.io/docs/8.10/guides/document-handling/"
						target="_blank"
						rel="noopener noreferrer"
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
							In Camunda 8.10, documents are not directly relatable
							to tasks. They are referenced by{" "}
							<code>documentIds</code> which are returned on
							upload.
						</p>
					),
				},
			],
			additionalInfo: (
				<>
					<p>
						Upload the Camunda 7 attachment content to Camunda 8
						first and persist the returned{" "}
						<code>documentId</code>. The source{" "}
						<code>attachmentId</code> cannot be sent directly as a
						Camunda 8 document ID.
					</p>
					<p>
						For more information, take a look at{" "}
						<a
							href="https://docs.camunda.io/docs/8.10/guides/document-handling/"
							target="_blank"
							rel="noopener noreferrer"
						>
							the docs
						</a>
						.
					</p>
				</>
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
				<>
					<p>
						Upload the Camunda 7 attachment content to Camunda 8
						first and persist the returned{" "}
						<code>documentId</code>. The source{" "}
						<code>attachmentId</code> cannot be sent directly as a
						Camunda 8 document ID.
					</p>
					<p>
						For more information, take a look at{" "}
						<a
							href="https://docs.camunda.io/docs/8.10/guides/document-handling/"
							target="_blank"
							rel="noopener noreferrer"
						>
							the docs
						</a>
						.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<p>
							In Camunda 8.10, documents are not directly relatable
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
						href="https://docs.camunda.io/docs/8.10/guides/document-handling/"
						target="_blank"
						rel="noopener noreferrer"
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
		target: {},
		discontinuedExplanation: (
			<p>
				Camunda 7 returns attachment metadata from this endpoint. The
				Camunda 8.10 <code>GET /documents/{"{documentId}"}</code>{" "}
				endpoint downloads binary content, so it is not an equivalent
				metadata operation. Manage attachment metadata in the
				application when migrating.
			</p>
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) attachmentId</pre>,
					rightEntry: (
						<p>
							Use the Camunda 8 <code>documentId</code> created
							during migration. It is not interchangeable with the
							Camunda 7 <code>attachmentId</code>.
						</p>
					),
				},
			],
			additionalInfo: (
				<p>
					Retain or look up the Camunda 8 document ID created during
					migration before downloading the content.{" "}
					For more information, take a look at{" "}
					<a
						href="https://docs.camunda.io/docs/8.10/guides/document-handling/"
						target="_blank"
						rel="noopener noreferrer"
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
							In Camunda 8.10, documents are not directly relatable
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
						href="https://docs.camunda.io/docs/8.10/guides/document-handling/"
						target="_blank"
						rel="noopener noreferrer"
					>
						the docs
					</a>
					.
				</p>
			),
		},
	},
];
