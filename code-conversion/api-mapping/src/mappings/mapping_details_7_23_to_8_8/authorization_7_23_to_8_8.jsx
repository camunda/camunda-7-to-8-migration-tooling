export const authorization_7_23_to_8_8 = [
	{
		origin: {
			path: "/authorization",
			operation: "get",
		},
		target: {
			path: "/authorizations/search",
			operation: "post",
		},
		direct: (
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
		direct: "Redundant.",
	},
	{
		origin: {
			path: "/authorization/check",
			operation: "get",
		},
		target: {
			path: "/authentication/me",
			operation: "get",
		},
		direct: (
			<div>
				Instead of receiving an <code>authorized</code> boolean for a
				specific check, a list of <code>authorizedApplications</code>{" "}
				can be retrieved for the authenticated user.
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
		direct: (
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
		direct: (
			<div>
				One to one mapping. For more details on authorizations in
				Camunda 8 check the{" "}
				<a href="https://docs.camunda.io/docs/8.8/components/identity/authorization/">
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
		direct: (
			<div>
				One to one mapping. For more details on authorizations in
				Camunda 8 check the{" "}
				<a href="https://docs.camunda.io/docs/8.8/components/identity/authorization/">
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
		direct: (
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
		direct: "Redundant.",
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
		direct: (
			<div>
				One to one mapping. For more details on authorizations in
				Camunda 8 check the{" "}
				<a href="https://docs.camunda.io/docs/8.8/components/identity/authorization/">
					docs
				</a>
				. Authorizations are handled via the <strong>Identity</strong>{" "}
				webapp.
			</div>
		),
	},
];
