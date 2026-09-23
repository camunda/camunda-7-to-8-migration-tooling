/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
export const authorization = [
	{
		origin: {
			path: "/authorization",
			operation: "get",
		},
		target: {
			path: "/authorizations/search",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				Replaced by a <code>POST search</code> endpoint. Authorizations
				are handled via the <strong>Identity</strong> webapp.
			</div>
		),
	},
	{
		origin: {
			path: "/authorization",
			operation: "options",
		},
		target: {},
		discontinuedExplanation: (
			<div>
				Redundant. The Camunda 8 API does not use <code>OPTIONS</code>{" "}
				calls.
			</div>
		),
	},
	{
		origin: {
			path: "/authorization/check",
			operation: "get",
		},
		target: {
			path: "/authentication/me/authorizations/search",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				The authenticated principal's authorization records can be
				retrieved with this endpoint, but it does not return an{" "}
				<code>authorized</code> boolean for an arbitrary user,
				resource, and permission check. The Camunda 7{" "}
				<code>userId</code> case requires separate handling.
			</div>
		),
	},
	{
		origin: {
			path: "/authorization/count",
			operation: "get",
		},
		target: {
			path: "/authorizations/search",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				Replaced by a <code>POST search</code> endpoint. Authorizations
				are handled via the <strong>Identity</strong> webapp.
			</div>
		),
	},
	{
		origin: {
			path: "/authorization/create",
			operation: "post",
		},
		target: {
			path: "/authorizations",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				This requires a semantic conversion, not a one-to-one request
				forward. Map C7 <code>userId</code>/<code>groupId</code> to
				C8 <code>ownerId</code>/<code>ownerType</code>, convert the
				numeric <code>resourceType</code> to the C8 enum, map{" "}
				<code>permissions</code> to <code>permissionTypes</code>, and
				preserve <code>resourceId</code>. C7 <code>type</code> grant,
				revoke, and global semantics have no direct C8 field and need
				explicit handling. For more details on authorizations in
				Camunda 8 check the{" "}
				<a href="https://docs.camunda.io/docs/8.10/components/identity/authorization/">
					docs
				</a>
				. Authorizations are handled via the <strong>Identity</strong>{" "}
				webapp.
			</div>
		),
	},
	{
		origin: {
			path: "/authorization/{id}",
			operation: "delete",
		},
		target: {
			path: "/authorizations/{authorizationKey}",
			operation: "delete",
		},
		mappedExplanation: (
			<div>
				The path requires the C8 system-assigned{" "}
				<code>authorizationKey</code>, not the C7 authorization{" "}
				<code>id</code>. Resolve the C7 id through the C8 search or a
				persisted id mapping before deleting. For more details on
				authorizations in Camunda 8 check the{" "}
				<a href="https://docs.camunda.io/docs/8.10/components/identity/authorization/">
					docs
				</a>
				. Authorizations are handled via the <strong>Identity</strong>{" "}
				webapp.
			</div>
		),
	},
	{
		origin: {
			path: "/authorization/{id}",
			operation: "get",
		},
		target: {
			path: "/authorizations/search",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				Replaced by a <code>POST search</code> endpoint. Authorizations
				are handled via the <strong>Identity</strong> webapp.
			</div>
		),
	},
	{
		origin: {
			path: "/authorization/{id}",
			operation: "options",
		},
		target: {},
		discontinuedExplanation: (
			<div>
				Redundant. The Camunda 8 API does not use <code>OPTIONS</code>{" "}
				calls.
			</div>
		),
	},
	{
		origin: {
			path: "/authorization/{id}",
			operation: "put",
		},
		target: {
			path: "/authorizations/{authorizationKey}",
			operation: "put",
		},
		mappedExplanation: (
			<div>
				This update requires the C8 system-assigned{" "}
				<code>authorizationKey</code>, not the C7 authorization{" "}
				<code>id</code>. Convert the body to C8{" "}
				<code>ownerId</code>/<code>ownerType</code>, the C8 enum{" "}
				<code>resourceType</code>, and <code>permissionTypes</code>;
				C7's fields are not forwarded one-to-one. For more details on
				authorizations in Camunda 8 check the{" "}
				<a href="https://docs.camunda.io/docs/8.10/components/identity/authorization/">
					docs
				</a>
				. Authorizations are handled via the <strong>Identity</strong>{" "}
				webapp.
			</div>
		),
	},
];
