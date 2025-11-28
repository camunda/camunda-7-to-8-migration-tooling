/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { c7_23 } from "../openapi/camunda7/c7_23";
import { c8_8 } from "../openapi/camunda8/c8_8";
import * as mappings from "./mapping_details_7_23_to_8_8/index.js";

export const mapping_7_23_to_8_8 = {
	id: "7_23_to_8_8",
	tabName: "7.23 to 8.8",
	c7BaseUrl:
		"https://docs.camunda.org/rest/camunda-bpm-platform/7.23-SNAPSHOT/#tag/",
	c8BaseUrl:
		"https://docs.camunda.io/docs/8.8/apis-tools/camunda-api-rest/specifications/",
	c7_specification: c7_23,
	c8_specification: c8_8,
	introduction: (
		<>
			<p>
				The Camunda 7 API and Camunda 8 API share many commonalities
				because of their similar feature sets. Both APIs handle
				resources, events, user tasks, jobs, etc. With the introduction
				of Camunda 8, various aspects of the Camunda 7 API have been
				modernized. Even as we approach feature parity, while also
				introducing new concepts in Camunda 8, the number of API
				endpoint groups and the number of actual endpoints differ
				greatly:
			</p>
			<p>
				<strong>
					The Camunda 7.23 API has 51 endpoint groups and 393
					endpoints.
				</strong>
			</p>
			<p>
				<strong>
					The Camunda 8.8 API has 28 endpoint groups and 103
					endpoints.
				</strong>
			</p>
			<p>
				Here are a few reasons why the number of endpoints has decreased
				substantially:
			</p>
			<ul>
				<li>
					The implementation of API endpoints to get/read/search for
					resources has changed.
				</li>
				<li>
					In Camunda 8, the tenantId is handled as a request body
					field, not a path parameter, which introduced many
					additional endpoints in Camunda 7.
				</li>
				<li>
					In Camunda 8, there are no separate API endpoints for
					historic data.
				</li>
			</ul>
			<h2>Common Mapping Patterns</h2>
			<p>
				There are common mapping patterns that apply to various mappings
				between Camunda 7 and Camunda 8 API endpoints. These patterns
				are introduced here to simplify the following mapping tables.
				The mapping tables will reference the patterns introduced here,
				instead of explaining the pattern multiple times.
			</p>
			<h3>GET resource list, GET resource count → POST search</h3>
			<p>
				In Camunda 7, there are separate endpoints with a similar set of
				parameters to get a list of resources with details or just the
				count. The resource-specific parameters are the same.
				Additionally, the <code>GET resource list</code> endpoint uses
				the parameters{" "}
				<code>sortBy, sortOrder, firstResult, maxResults</code> for
				sorting and pagination.
			</p>
			<p>
				In Camunda 8, one <code>POST search</code> replaces the Camunda
				7 endpoints described above. The root fields of the request body
				are always
				<code>filter</code>, <code>sort</code>, and <code>page</code>.
				The object <code>filter</code> contains specific
				resource-specific fields for filtering purposes. The same fields
				appear under <code>sort[].field</code> to sort the results by
				one or multiple fields. The object <code>page</code> is
				identical among all <code>POST search</code> endpoints and is
				used for pagination.
			</p>
			<h4>Example: Tenants</h4>
			<table>
				<thead>
					<tr>
						<th>Camunda 7 - GET Tenants</th>
						<th>Camunda 7 - GET Tenant Count</th>
						<th>Camunda 8 - POST Search Tenants</th>
					</tr>
				</thead>
				<tbody>
					<tr>
						<td>
							<h5>Resource-specific parameters:</h5>
							<ul>
								<li>
									(string) id: Filter by the id of the tenant.
								</li>
								<li>
									(string) name: Filter by the name of the
									tenant.
								</li>
								<li>
									(string) nameLike: Filter by the name that
									the parameter is a substring of.
								</li>
								<li>
									(string) userMember: Select only tenants
									where the given user is a member of.
								</li>
								<li>
									(string) groupMember: Select only tenants
									where the given group is a member of.
								</li>
								<li>
									(boolean) includingGroupsOfUser: Select only
									tenants where the user or one of his groups
									is a member of.
								</li>
							</ul>
							<h5>Sorting and pagination parameters:</h5>
							<ul>
								<li>
									(string) sortBy: Sort the results
									lexicographically by a given criterion. Must
									be used in conjunction with the sortOrder
									parameter.
								</li>
								<li>
									(string) sortOrder: Sort the results in a
									given order. Values may be asc for ascending
									order or desc for descending order.
								</li>
								<li>
									(integer) firstResult: Pagination of
									results. Specifies the index of the first
									result to return.
								</li>
								<li>
									(integer) maxResults: Pagination of results.
									Specifies the maximum number of results to
									return. Will return less results if there
									are no more results left.
								</li>
							</ul>
						</td>
						<td>
							<h5>Resource-specific parameters:</h5>
							<p>
								Identical to the resource-specific parameters of{" "}
								<code>GET Tenants</code>.
							</p>
							<h5>Sorting and pagination parameters:</h5>
							<p>None.</p>
						</td>
						<td>
							<h5>
								Fields of <code>filter</code> object:
							</h5>
							<ul>
								<li>
									(string) tenantId: The ID of the tenant.
								</li>
								<li>(string) name: The name of the tenant.</li>
							</ul>
							<h5>
								Fields of <code>sorting</code> list of objects:
							</h5>
							<ul>
								<li>
									(string) field: The field to sort by.
									Possible values: [key, tenantId, name]
								</li>
								<li>
									(string) order: The order in which to sort
									the related field. Possible values: [ASC,
									DESC]
								</li>
							</ul>
							<h5>
								Fields of <code>page</code> object:
							</h5>
							<ul>
								<li>
									(int32) from: The index of items to start
									searching from.
								</li>
								<li>
									(int32) limit: The maximum number of items
									to return in one request.
								</li>
								<li>
									(object[]) searchAfter: Items to search
									after. Correlates to the lastSortValues
									property of a previous search response.
								</li>
								<li>
									(object[]) searchBefore: Items to search
									before. Correlates to the firstSortValues
									property of a previous search response.
								</li>
							</ul>
						</td>
					</tr>
					<tr>
						<td>
							<p>Request:</p>
							<pre>GET /tenant?nameLike=Tenant</pre>
							<p>Response:</p>
							<pre>
								{
									'[\n\t{ "id": "tenantOne", "name": "Tenant One"},\n\t{ "id": "tenantTwo", "name": "Tenant Two"}\n]'
								}
							</pre>
						</td>
						<td>
							<p>Request:</p>
							<pre>GET /tenant/count?nameLike=Tenant</pre>
							<p>Response:</p>
							<pre>{'{\n\t"count": 2\n}'}</pre>
						</td>
						<td>
							<p>Request:</p>
							<pre>POST /tenants/search</pre>
							<pre>
								{
									'{\n\t"filter": { "name": "Tenant One" },\n\t"sort": [{ "field": "name" }],\n\t"page": { "from": 0 }\n}'
								}
							</pre>
							<p>Response:</p>
							<pre>
								{
									'{\n\t"items": [\n\t\t{\n\t\t\t"name": "Tenant One",\n\t\t\t"tenantId": "tenantOne",\n\t\t\t"description": "A tenant",\n\t\t\t"tenantKey": "aa883-agas4342-32fre"\n\t\t}\n\t],\n\t"page": {\n\t\t"totalItems": 1,\n\t\t"firstSortValues": [\n\t\t\t{\n\t\t\t\t"name": "Tenant One",\n\t\t\t\t"tenantId": "tenantOne",\n\t\t\t\t"description": "A tenant",\n\t\t\t\t"tenantKey": "aa883-agas4342-32fre"\n\t\t\t}\n\t\t],\n\t\t"lastSortValue": [\n\t\t\t{\n\t\t\t\t"name": "Tenant One",\n\t\t\t\t"tenantId": "tenantOne",\n\t\t\t\t"description": "A tenant",\n\t\t\t\t"tenantKey": "aa883-agas4342-32fre"\n\t\t\t}\n\t\t]\n\t}\n}'
								}
							</pre>
						</td>
					</tr>
				</tbody>
			</table>
			<h4>Explanation</h4>
			<p>
				In their basic functionality, the two Camunda 7 endpoints are
				fully replaced by the new Camunda 8 endpoint.
			</p>
			<p>
				The <code>nameLike</code> functionality is not available in this
				example in Camunda 8. A similar functionality is available for
				important APIs, like searching for process instances, using
				unary-test-like advanced filters. The same is true for{" "}
				<code>...In</code>, where a list of strings is provided for
				filtering purposes.
			</p>
			<h3>/tenant-id/{"{tenant-id}"} → POST search</h3>
			<p>
				In Camunda 7, many endpoints are duplicated in their basic
				functionality to account for tenantIds. This is done by using
				appending the tenantId as a path parameter to an existing
				endpoint: <code>/tenant-id/{"{tenant-id}"}</code>
			</p>
			<h4>Example: GET Decision Definition By Key</h4>
			<table>
				<thead>
					<tr>
						<th>GET Decision Definition By Key</th>
						<th>GET Decision Definition By Key And Tenant Id</th>
						<th>POST Search decision definitions</th>
					</tr>
				</thead>
				<tbody>
					<tr>
						<td>
							<h5>Path parameters:</h5>
							<ul>
								<li>
									(string) key: The key of the decision
									definition (the latest version thereof) to
									be retrieved.
								</li>
							</ul>
						</td>
						<td>
							<h5>Path parameters:</h5>
							<ul>
								<li>
									(string) key: The key of the decision
									definition (the latest version thereof) to
									be retrieved.
								</li>
								<li>
									(string) tenant-id: string The id of the
									tenant the decision definition belongs to.
								</li>
							</ul>
						</td>
						<td>
							<h5>
								Fields of <code>filter</code> object:
							</h5>
							<ul>
								<li>
									(string) decisionDefinitionId: The DMN ID of
									the decision definition..
								</li>
								<li>
									(string) name: The DMN name of the decision
									definition.
								</li>
								<li>
									(int32) version: The assigned version of the
									decision definition.
								</li>
								<li>
									(string) decisionRequirementsId: The DMN ID
									of the decision requirements graph that the
									decision definition is part of.
								</li>
								<li>
									(string) tenantId: The tenant ID of the
									decision definition.
								</li>
								<li>
									(string) decisionDefinitionKey: The assigned
									key, which acts as a unique identifier for
									this decision definition.
								</li>
								<li>
									(string) decisionRequirementsKey: The
									assigned key of the decision requirements
									graph that the decision definition is part
									of.
								</li>
							</ul>
							<h5>
								Fields of <code>sorting</code> list of objects:
							</h5>
							<ul>
								<li>
									(string) field: The field to sort by.
									Possible values: [decisionDefinitionKey,
									decisionDefinitionId, name, version,
									decisionRequirementsId,
									decisionRequirementsKey, tenantId]
								</li>
								<li>
									(string) order: The order in which to sort
									the related field. Possible values: [ASC,
									DESC]
								</li>
							</ul>
							<h5>
								Fields of <code>page</code> object:
							</h5>
							<ul>
								<li>
									(int32) from: The index of items to start
									searching from.
								</li>
								<li>
									(int32) limit: The maximum number of items
									to return in one request.
								</li>
								<li>
									(object[]) searchAfter: Items to search
									after. Correlates to the lastSortValues
									property of a previous search response.
								</li>
								<li>
									(object[]) searchBefore: Items to search
									before. Correlates to the firstSortValues
									property of a previous search response.
								</li>
							</ul>
						</td>
					</tr>
					<tr>
						<td>
							<p>Request:</p>
							<code>
								GET /decision-definition/key/dish-decision
							</code>
						</td>
						<td>
							<p>Request:</p>
							<code>
								GET
								/decision-definition/key/dish-decision/tenant-id/aTenantId
							</code>
						</td>
						<td>
							<p>Request:</p>
							<pre>POST /decision-definitions/search</pre>
							<pre>
								{
									'{\n\t"filter": { "id": "dish-decision", "tenantId": "aTenantId" }\n}'
								}
							</pre>
						</td>
					</tr>
				</tbody>
			</table>
			<h4>Explanation:</h4>
			<p>
				In their basic functionality, the two Camunda 7 endpoints are
				fully replaced by the new Camunda 8 endpoint.
			</p>
			<p>The Camunda 8 endpoint offers more filter options.</p>
			<h3 id="key-to-id">Camunda 7 key → Camunda 8 id</h3>
			<p>
				In Camunda 7, a "key" refers to an intrinsic identifier of a
				resource, e.g., the id of the BPMN 2.0 XML process definition
				used to retrieve the latest version of a specific process
				definition. In contrast, an "id" is an assigned identifier,
				e.g., the id of a deployed process definition.
			</p>
			<p>
				In Camunda 8, the terms "key" and "id" are reversed. A "key" is
				an assigned identifier, e.g., the processDefinitionKey returned
				when deploying a process definition. The processDefinitionId is
				the id of the BPMN 2.0 XML process definition. As you can see,
				the keys and ids in Camunda 8 are also prepended with
				descriptive information.
			</p>
		</>
	),
	mappings: [
		...mappings.authorization,
		...mappings.batch,
		...mappings.condition,
		...mappings.decision_definition,
		...mappings.decision_requirements_definition,
		...mappings.deployment,
		...mappings.engine,
		...mappings.event_subscription,
		...mappings.execution,
		...mappings.external_task,
		...mappings.filter,
		...mappings.group,
		...mappings.historic_activity_instance,
		...mappings.historic_batch,
		...mappings.historic_decision_definition,
		...mappings.historic_decision_instance,
		...mappings.historic_decision_requirements_definition,
		...mappings.historic_detail,
		...mappings.historic_external_task_log,
		...mappings.historic_identity_link_log,
		...mappings.historic_incident,
		...mappings.historic_job_log,
		...mappings.historic_process_definition,
		...mappings.historic_process_instance,
		...mappings.historic_task_instance,
		...mappings.historic_user_operation_log,
		...mappings.historic_variable_instance,
		...mappings.history_cleanup,
		...mappings.identity,
		...mappings.incident,
		...mappings.job,
		...mappings.job_definition,
		...mappings.message,
		...mappings.metrics,
		...mappings.migration,
		...mappings.modification,
		...mappings.process_definition,
		...mappings.process_instance,
		...mappings.signal,
		...mappings.schema_log,
		...mappings.task,
		...mappings.task_attachment,
		...mappings.task_comment,
		...mappings.task_identity_link,
		...mappings.task_local_variable,
		...mappings.task_variable,
		...mappings.telemetry,
		...mappings.tenant,
		...mappings.user,
		...mappings.variable_instance,
		...mappings.version,
	],
};
