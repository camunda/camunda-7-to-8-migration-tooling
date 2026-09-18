/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
export const variable_instance = [
	{
		origin: {
			path: "/variable-instance",
			operation: "get",
		},
		target: {
			path: "/variables/search",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) variableName</pre>,
					rightEntry: <pre>(string) filter.name</pre>,
				},
				{
					leftEntry: <pre>(string) variableNameLike</pre>,
					rightEntry: (
						<>
							<pre>(string) filter.name.$like</pre>
							<p>
								Use the pattern syntax accepted by the Camunda 8
								filter; do not copy a Camunda 7 pattern unchanged.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string[]) processInstanceIdIn</pre>,
					rightEntry: (
						<>
							<pre>(string[]) filter.processInstanceKey.$in</pre>
							<p>
								Resolve Camunda 7 process-instance IDs to Camunda 8
								process-instance keys before applying the filter.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) sortBy
							<br />
							(string) sortOrder
						</pre>
					),
					rightEntry: (
						<pre>
							(string) sort[].field
							<br />
							(enum) sort[].order
						</pre>
					),
				},
				{
					leftEntry: (
						<pre>
							(integer) firstResult
							<br />
							(integer) maxResults
						</pre>
					),
					rightEntry: (
						<pre>
							(integer) page.from
							<br />
							(integer) page.limit
						</pre>
					),
				},
			],
			additionalInfo: (
				<>
					<p>
						Camunda 8 returns variables defined directly at the
						requested scope; it does not include variables inherited
						from parent scopes. Add <code>?truncateValues=false</code>{" "}
						when complete values are required.
					</p>
					<p>
						A single equality predicate can use <code>filter.name</code>{" "}
						and <code>filter.value</code>. The value must use its
						serialized JSON representation, including quotes for
						strings.
					</p>
					<p>
						Map only sort fields with equivalent semantics and convert
						Camunda 7 <code>asc</code>/<code>desc</code> values to
						Camunda 8 <code>ASC</code>/<code>DESC</code>.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(string[]) executionIdIn
							<br />
							(string[]) taskIdIn
							<br />
							(string[]) activityInstanceIdIn
							<br />
							(string[]) variableScopeIdIn
						</pre>
					),
					rightEntry: (
						<p>
							There is no generic ID conversion to{" "}
							<code>filter.scopeKey</code>. Resolve source IDs with
							migration-specific correlation before using the target
							filter.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(string[]) tenantIdIn
							<br />
							(string) variableValues
						</pre>
					),
					rightEntry: (
						<p>
							These query forms have no direct one-request mapping.
							Camunda 8 accepts one <code>filter.tenantId</code> and
							one serialized JSON variable-value filter; preserve
							application-specific query semantics outside this
							mapping.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(string[]) caseInstanceIdIn
							<br />
							(string[]) caseExecutionIdIn
							<br />
							(string[]) batchIdIn
							<br />
							(boolean) variableNamesIgnoreCase
							<br />
							(boolean) variableValuesIgnoreCase
							<br />
							(boolean) deserializeValues
							<br />
							(string) sortBy=variableType
						</pre>
					),
					rightEntry: <p>Camunda 8.10 has no equivalent option.</p>,
				},
			],
		},
	},
	{
		origin: {
			path: "/variable-instance",
			operation: "post",
		},
		target: {
			path: "/variables/search",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(object[]) sorting</pre>,
					rightEntry: (
						<pre>
							(string) sort[].field
							<br />
							(enum) sort[].order
						</pre>
					),
				},
				{
					leftEntry: (
						<pre>
							(integer) firstResult
							<br />
							(integer) maxResults
						</pre>
					),
					rightEntry: (
						<pre>
							(integer) page.from
							<br />
							(integer) page.limit
						</pre>
					),
				},
			],
			additionalInfo: (
				<p>
					Use the same compatible filters, sort conversion, and
					pagination mappings as the GET <code>/variable-instance</code>{" "}
					endpoint. The Camunda 8 request body contains{" "}
					<code>filter</code>, <code>sort</code>, and <code>page</code>{" "}
					at its root. Do not copy the Camunda 7 query body unchanged.
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(object[]) variableValues</pre>,
					rightEntry: (
						<p>
							A single equality predicate can be translated to{" "}
							<code>filter.name</code> and serialized JSON{" "}
							<code>filter.value</code>. Other operators and
							multiple predicates require application-specific
							migration logic.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(boolean) variableNamesIgnoreCase
							<br />
							(boolean) variableValuesIgnoreCase
							<br />
							(boolean) deserializeValues
							<br />
							(string) sorting[].sortBy=variableType
						</pre>
					),
					rightEntry: <p>Camunda 8.10 has no equivalent option.</p>,
				},
			],
		},
	},
	{
		origin: {
			path: "/variable-instance/count",
			operation: "get",
		},
		target: {
			path: "/variables/search",
			operation: "post",
		},
		mappedExplanation: (
			<p>
				Camunda 8 has no dedicated variable count endpoint. Use the same
				supported filters as the GET <code>/variable-instance</code>{" "}
				mapping and read <code>page.totalItems</code> from the search
				response. When <code>page.hasMoreTotalItems</code> is{" "}
				<code>true</code>, the result is a lower bound rather than an
				exact count.
			</p>
		),
	},
	{
		origin: {
			path: "/variable-instance/count",
			operation: "post",
		},
		target: {
			path: "/variables/search",
			operation: "post",
		},
		mappedExplanation: (
			<p>
				Camunda 8 has no dedicated variable count endpoint. Use the same
				supported filters as the POST <code>/variable-instance</code>{" "}
				mapping and read <code>page.totalItems</code> from the search
				response. When <code>page.hasMoreTotalItems</code> is{" "}
				<code>true</code>, the result is a lower bound rather than an
				exact count.
			</p>
		),
	},
	{
		origin: {
			path: "/variable-instance/{id}",
			operation: "get",
		},
		target: {
			path: "/variables/{variableKey}",
			operation: "get",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: (
						<>
							<pre>(string) variableKey</pre>
							<p>
								A Camunda 7 variable-instance ID does not directly
								convert to a Camunda 8 variable key. Use
								migration-specific ID-to-key correlation before
								calling this endpoint.
							</p>
						</>
					),
				},
			],
			additionalInfo: (
				<p>
					The Camunda 8 <code>value</code> is a serialized JSON string.
					Parse it when a typed value is required. The Camunda 7{" "}
					<code>deserializeValue</code> option has no direct equivalent.
				</p>
			),
		},
	},
	{
		origin: {
			path: "/variable-instance/{id}/data",
			operation: "get",
		},
		target: {},
		discontinuedExplanation: (
			<p>
				Camunda 8.10 has no generic binary variable download endpoint.
				Use <code>GET /variables/{"{variableKey}"}</code> to retrieve a
				JSON-compatible value after resolving its variable key.
			</p>
		),
	},
];
