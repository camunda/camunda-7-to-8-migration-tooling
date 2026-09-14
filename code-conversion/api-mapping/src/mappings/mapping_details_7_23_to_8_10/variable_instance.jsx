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
					leftEntry: (
						<pre>
							(string) variableName
							<br />
							(string) variableNameLike
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(string) filter.name
								<br />
								(string) filter.name.$eq
								<br />
								(string) filter.name.$like
							</pre>
							<p>
								Use the scalar <code>filter.name</code> for
								<code>variableName</code> when{" "}
								<code>variableNameLike</code> is absent. When
								both C7 parameters are supplied, use the
								advanced object with{" "}
								<code>filter.name.$eq</code> and{" "}
								<code>filter.name.$like</code>; do not combine
								scalar <code>filter.name</code> with{" "}
								<code>$like</code>. Translate each Camunda 7{" "}
								<code>%</code> wildcard to the Camunda 8{" "}
								<code>*</code> wildcard. Before sending{" "}
								<code>$like</code>, escape literal backslashes,{" "}
								<code>*</code>, and <code>?</code> with the
								Camunda 8 backslash escape so C7 literal
								characters do not become C8 wildcards. Camunda
								8 treats <code>%</code> literally.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>(string[]) processInstanceIdIn</pre>
					),
					rightEntry: (
						<>
							<pre>(string[]) filter.processInstanceKey.$in</pre>
							<p>
								Resolve Camunda 7 process-instance IDs to the
								corresponding Camunda 8 process instance keys
								before applying this filter. Keep this predicate
								separate from scope selectors; when multiple source
								filter fields are supplied, intersect their complete
								result sets by <code>variableKey</code>.
							</p>
						</>
					),
				},
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
						<>
							<pre>(string[]) filter.scopeKey.$in</pre>
							<p>
								Resolve Camunda 7 execution, task, activity, and
								variable-scope IDs to the corresponding Camunda 8
								<code>scopeKey</code> values. Depending on the
								source ID and scope, the target can be a
								process-instance key or an element-instance key;
								do not assume every source resolves to an
								element-instance key. These are separate C7
								predicates: do not combine IDs from different source
								fields into one <code>$in</code> list. Issue one
								request per populated field and intersect the complete
								result sets by <code>variableKey</code>.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string[]) tenantIdIn</pre>,
					rightEntry: (
						<>
							<pre>(string) filter.tenantId</pre>
							<p>
								The Camunda 8 filter accepts one tenant ID, while
								C7 treats <code>tenantIdIn</code> as an OR list.
								Issue one request per C7 tenant ID, retrieve all
								pages, then union and deduplicate the results by{" "}
								<code>variableKey</code>. Reapply the requested C7
								sort to the combined result before applying
								pagination or deriving a count. Do not sum
								per-tenant totals.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string) variableValues</pre>,
					rightEntry: (
						<>
							<pre>
								(string) filter.name
								<br />
								(string) filter.value.$eq
								<br />
								(string) filter.value.$neq
								<br />
								(string) filter.value.$like
							</pre>
							<p>
								Parse each C7 expression as{" "}
								<code>name_operator_value</code>. Map{" "}
								<code>eq</code>, <code>neq</code>, and{" "}
								<code>like</code> to the corresponding C8
								operator. For <code>eq</code> and{" "}
								<code>neq</code>, resolve the migrated variable
								type before serializing the value: encode
								numbers and booleans as JSON literals inside the
								string filter value, and actual strings with
								their quotes. The C8 <code>filter.value</code>
								property remains a string. If the type
								cannot be resolved, omit the value filter and
								search or post-filter using C7 string semantics
								instead of assuming a JSON string. For{" "}
								<code>like</code>, mark filters on non-string
								variables unsupported. For string-valued
								variables, escape literal backslashes,{" "}
								<code>*</code>, and <code>?</code>, translate{" "}
								<code>%</code> wildcards to <code>*</code>, and
								JSON-encode the resulting string pattern. C8
								has no numeric comparison operator for variable
								values, so{" "}
								<code>gt</code>, <code>gteq</code>,{" "}
								<code>lt</code>, and <code>lteq</code> are
								unsupported.
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
						<>
							<pre>
								(string) sort[].field
								<br />
								(enum) sort[].order
							</pre>
							<p>
								Map Camunda 7 <code>asc</code> and{" "}
								<code>desc</code> to Camunda 8{" "}
								<code>ASC</code> and <code>DESC</code>.
								This is not a direct mapping for every C7{" "}
								<code>sortBy</code> value. Map only{" "}
								<code>variableName</code> to{" "}
								<code>name</code> and <code>tenantId</code> to{" "}
								<code>tenantId</code>. Resolve{" "}
								<code>activityInstanceId</code> and sort
								client-side. Mark{" "}
								<code>variableType</code> unsupported.
							</p>
						</>
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
						The Camunda 8.10 Search variables endpoint returns
						variables directly defined at the requested scopes. It
						does not include variables inherited from parent scopes.
					</p>
					<p>
						When both <code>variableName</code> and{" "}
						<code>variableNameLike</code> are supplied, use{" "}
						<code>filter.name.$eq</code> and{" "}
						<code>filter.name.$like</code> together in the
						advanced name filter. Do not send scalar{" "}
						<code>filter.name</code> alongside{" "}
						<code>filter.name.$like</code>. Apply the same rule
						when the POST mapping reuses these filters.
					</p>
					<p>
						Use the advanced <code>$in</code> and{" "}
						<code>$like</code> operators for array and pattern
						criteria where the target filter supports them. Variable
						values in{" "}
						<code>filter.value</code> must use their serialized JSON
						representation. For GET requests,{" "}
						<code>variableValues</code> query values are always C7
						<code>String</code> tokens, but C8 compares serialized
						JSON values with type-sensitive equality. Resolve the
						migrated variable type before constructing an{" "}
						<code>eq</code> or <code>neq</code> filter: serialize
						numbers and booleans as JSON literals inside the string
						filter value, and actual strings with their quotes. The
						C8 filter property remains a string. If the type cannot
						be resolved, search without the value filter and
						post-filter using C7 string semantics. Do not
						unconditionally quote every GET token. For POST
						requests, use the same JSON-encoded string produced by{" "}
						<code>JSON.stringify</code>; string values include
						quotes. For <code>like</code>, non-string
						variable values are unsupported. For
						string-valued variables, escape literal backslashes,
						<code>*</code>, and{" "}
						<code>?</code> before translating C7{" "}
						<code>%</code> wildcards to C8 <code>*</code>, then
						JSON-encode the complete pattern.
						Parse each{" "}
						<code>variableValues</code> expression as{" "}
						<code>name_operator_value</code> and map the supported{" "}
						<code>eq</code>, <code>neq</code>, and{" "}
						<code>like</code> operators. The C8 filter does not
						support the numeric comparison operators{" "}
						<code>gt</code>, <code>gteq</code>,{" "}
						<code>lt</code>, or <code>lteq</code>.
					</p>
					<p>
						Camunda 8 defaults <code>page.limit</code> to 100. For
						any unbounded C7 search, or when{" "}
						<code>maxResults</code> can exceed one page, follow{" "}
						<code>page.endCursor</code> with{" "}
						<code>page.after</code> until all matching variables
						are retrieved before applying{" "}
						<code>firstResult</code> and{" "}
						<code>maxResults</code>. Remove <code>page.from</code>
						when switching to cursor traversal because C8 does not
						allow offset and cursor pagination together. Use one
						page only when the requested C7 result window is known
						to fit in it.
					</p>
					<p>
						For multiple <code>variableValues</code> entries, issue
						one request per entry and follow each response's{" "}
						<code>page.endCursor</code> with <code>page.after</code>{" "}
						until all pages are retrieved. Do not send C7{" "}
						<code>firstResult</code> as <code>page.from</code> in
						these cursor-paginated intermediate requests, and do not
						stop traversal at C7 <code>maxResults</code>. Then
						intersect the complete result sets by{" "}
						<code>variableKey</code> before applying the C7 offset
						and limit or deriving counts. Do not intersect only the
						first page or union the responses.
					</p>
					<p>
						Map <code>variableName</code> to{" "}
						<code>sort[].field=name</code>,{" "}
						<code>tenantId</code> to{" "}
						<code>sort[].field=tenantId</code>. C8{" "}
						<code>scopeKey</code> values do not preserve the C7{" "}
						<code>activityInstanceId</code> string ordering, so do
						not map that sort directly. Resolve activity-instance
						IDs and sort client-side after retrieving the complete
						result set, or mark the sort unsupported. There is no C8
						sort field for{" "}
						<code>variableType</code>.
					</p>
					<p>
						When multiple process-instance or scope selector fields
						are supplied, issue separate searches for each populated
						field and intersect their complete result sets by{" "}
						<code>variableKey</code>. Apply the C7 conjunction before
						pagination or counting; do not combine different source
						fields into one C8 <code>$in</code> filter.
					</p>
					<p>
						Include <code>?truncateValues=false</code> to return
						complete variable values.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(string[]) caseInstanceIdIn
							<br />
							(string[]) caseExecutionIdIn
							<br />
							(string[]) batchIdIn
						</pre>
					),
					rightEntry: <p>Camunda 8.10 has no equivalent filter.</p>,
				},
				{
					leftEntry: (
						<pre>
							(boolean) variableNamesIgnoreCase
							<br />
							(boolean) variableValuesIgnoreCase
						</pre>
					),
					rightEntry: <p>Camunda 8.10 has no equivalent option.</p>,
				},
				{
					leftEntry: <pre>(boolean) deserializeValues</pre>,
					rightEntry: (
						<p>
							Camunda 8 stores variable values as JSON and has no
							equivalent for the Camunda 7{" "}
							<code>deserializeValues</code> option. The separate{" "}
							<code>truncateValues=false</code> option only
							controls whether response values are truncated.
						</p>
					),
				},
				{
					leftEntry: <pre>(string) sortBy=variableType</pre>,
					rightEntry: <p>Camunda 8.10 has no equivalent sort field.</p>,
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
					leftEntry: <pre>(object[]) variableValues</pre>,
					rightEntry: (
						<>
							<pre>
								(string) filter.name
								<br />
								(string) filter.value.$eq
								<br />
								(string) filter.value.$neq
								<br />
								(string) filter.value.$like
							</pre>
							<p>
								Map each object's <code>operator</code> value{" "}
								<code>eq</code>, <code>neq</code>, or{" "}
								<code>like</code> to the corresponding C8
								operator. Preserve each POST value's JSON type
								with <code>JSON.stringify</code> before setting{" "}
								<code>filter.value</code>; string values include
								their JSON quotes. For <code>like</code>, mark
								filters on non-string variables unsupported.
								For string-valued variables, escape literal
								backslashes, <code>*</code>, and{" "}
								<code>?</code>, translate C7{" "}
								<code>%</code> wildcards to C8{" "}
								<code>*</code>, and JSON-encode the complete
								string pattern. The numeric{" "}
								<code>gt</code>, <code>gteq</code>,{" "}
								<code>lt</code> and <code>lteq</code> operators
								are unsupported for C8 variable values.
							</p>
						</>
					),
				},
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
				<>
					<p>
						Use the same filter, sorting, and pagination mappings as
						the GET <code>/variable-instance</code> endpoint. The
						Camunda 8 request body uses <code>filter</code>,{" "}
						<code>sort</code>, and <code>page</code> at the root
						level. When both <code>variableName</code> and{" "}
						<code>variableNameLike</code> are supplied, use{" "}
						<code>filter.name.$eq</code> and{" "}
						<code>filter.name.$like</code> together.
					</p>
					<p>
						See the GET <code>/variable-instance</code> mapping for
						filter conversions, scope-key resolution, and unsupported
						parameters. Camunda 8 defaults{" "}
						<code>page.limit</code> to 100, so for any unbounded C7
						POST query, or when <code>maxResults</code> can exceed
						one page, follow <code>page.endCursor</code> with{" "}
						<code>page.after</code> until all matching variables are
						retrieved. Remove <code>page.from</code> when switching
						to cursor traversal because C8 does not allow offset
						and cursor pagination together. Apply{" "}
						<code>firstResult</code> and{" "}
						<code>maxResults</code>. Include{" "}
						<code>?truncateValues=false</code> when complete
						variable values are required; the search endpoint
						truncates values by default.
					</p>
					<p>
						Use one Camunda 8 request per{" "}
						<code>variableValues</code> entry and follow{" "}
						<code>page.endCursor</code> with <code>page.after</code>{" "}
						for each request until all pages are retrieved. Do not
						send C7 <code>firstResult</code> as{" "}
						<code>page.from</code> in these cursor-paginated
						intermediate requests or stop traversal at C7{" "}
						<code>maxResults</code>. Then intersect the complete
						responses by <code>variableKey</code> before applying the
						C7 offset and limit or deriving counts. Apply the same
						complete-page intersection to multiple process-instance
						or scope selector fields, and the complete-page union to
						multiple <code>tenantIdIn</code> values. Do not combine
						different source fields into one <code>$in</code> filter
						or union conjunctive <code>variableValues</code>{" "}
						responses.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(boolean) variableNamesIgnoreCase
							<br />
							(boolean) variableValuesIgnoreCase
							<br />
							(boolean) deserializeValues
						</pre>
					),
					rightEntry: <p>Camunda 8.10 has no equivalent option.</p>,
				},
				{
					leftEntry: <pre>(string) sorting[].sortBy=variableType</pre>,
					rightEntry: <p>Camunda 8.10 has no equivalent sort field.</p>,
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
				Use the same filters as the GET{" "}
				<code>/variable-instance</code> mapping and read{" "}
				<code>page.totalItems</code> and{" "}
				<code>page.hasMoreTotalItems</code> from the Camunda 8 search
				response. When <code>hasMoreTotalItems</code> is{" "}
				<code>false</code> for a single search,{" "}
				<code>page.totalItems</code> is exact and can be returned
				directly. When it is <code>true</code>,{" "}
				<code>page.totalItems</code> is only a lower bound, not the
				exact C7 count; follow <code>page.endCursor</code> and count
				all returned items. For multiple{" "}
				<code>tenantIdIn</code> values,{" "}
				<code>variableValues</code> entries, or process-instance and
				scope selector fields, follow <code>page.endCursor</code> for
				every request, apply the required complete-page union or
				intersection by <code>variableKey</code>, and count the
				deduplicated result. Do not sum capped totals or read one
				search total as the combined C7 count. If a value predicate
				was omitted for client-side type resolution or post-filtering,
				fetch all untruncated pages and count only after applying the
				C7 predicate; an uncapped total would otherwise count the
				unfiltered superset. The C8 search is eventually consistent,
				so the count describes the current C8 index and may lag C7.
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
				Use the same filters as the POST{" "}
				<code>/variable-instance</code> mapping and read{" "}
				<code>page.totalItems</code> and{" "}
				<code>page.hasMoreTotalItems</code> from the Camunda 8 search
				response. When <code>hasMoreTotalItems</code> is{" "}
				<code>false</code> for a single search,{" "}
				<code>page.totalItems</code> is exact and can be returned
				directly. When it is <code>true</code>,{" "}
				<code>page.totalItems</code> is only a lower bound, not the
				exact C7 count; follow <code>page.endCursor</code> and count
				all returned items. For multiple{" "}
				<code>tenantIdIn</code> values,{" "}
				<code>variableValues</code> entries, or process-instance and
				scope selector fields, follow <code>page.endCursor</code> for
				every request, apply the required complete-page union or
				intersection by <code>variableKey</code>, and count the
				deduplicated result. Do not sum capped totals or read one
				search total as the combined C7 count. If a value predicate
				was omitted for client-side type resolution or post-filtering,
				fetch all untruncated pages and count only after applying the
				C7 predicate; an uncapped total would otherwise count the
				unfiltered superset. The C8 search is eventually consistent,
				so the count describes the current C8 index and may lag C7.
				Sorting is not needed for a count.
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
								Camunda 8 variable keys are generated system keys
								and cannot be derived from a Camunda 7 variable
								instance ID. Use a migration-specific ID-to-key
								correlation before calling this endpoint; without
								that correlation, there is no direct mapping.
							</p>
						</>
					),
				},
			],
			additionalInfo: (
				<p>
					The Camunda 8 response's <code>value</code> is a string
					containing the serialized JSON representation, not a typed
					JSON value. Parse it as JSON when recreating the parsed
					Camunda 7 value, or preserve the serialized string when
					the Camunda 7 caller requested the serialized form. The C7
					<code>type</code>, <code>valueInfo</code>, and{" "}
					<code>errorMessage</code> response fields have no direct C8
					equivalent; preserve them from migration metadata when
					available or mark them unavailable. The Camunda 7{" "}
					<code>deserializeValue</code> option has no direct
					equivalent, so apply this conversion explicitly.
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(boolean) deserializeValue</pre>,
					rightEntry: <p>Camunda 8 stores variable values as JSON.</p>,
				},
			],
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
				Camunda 8.10 does not provide a generic binary variable download
				endpoint. The C7 variable-instance <code>id</code> is not a
				Camunda 8 <code>variableKey</code>; resolve the
				migration-specific ID-to-key correlation before calling{" "}
				<code>GET /variables/{"{variableKey}"}</code>
				. Resolve its <code>DocumentReference</code> and download the
				content with <code>GET /documents/{"{documentId}"}</code>.
				Pass its <code>storeId</code> and include{" "}
				<code>contentHash</code> only when it is non-null as query
				parameters. See the process-variable mapping for these
				<code>DocumentReference</code> fields and the conditional
				flow. Other non-document binary variable types have no
				equivalent endpoint.
			</p>
		),
	},
];
