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
			<h1>Introduction</h1>
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
			<table>
				<thead>
					<tr>
						<th>Pattern</th>
						<th>Camunda 7</th>
						<th>Camunda 8</th>
					</tr>
				</thead>
				<tbody>
					<tr>
						<td>GET resource list, GET resource count</td>
						<td>
							Separate endpoints with a similar set of parameters.
							The <code>GET resource list</code> endpoint uses the
							parameters{" "}
							<code>
								sortBy, sortOrder, firstResult, maxResults
							</code>{" "}
							for sorting and pagination.
						</td>
						<td>
							One <code>POST search</code> endpoint with filters,
							sorting and pagination. The fields <code>page</code>{" "}
							and <code>sort</code> look identical for all such{" "}
							<code>POST search</code> endpoints. The field{" "}
							<code>filter</code> offers a number of suitable
							fields corresponding to fields of the resource
							itself. The same fields appear under{" "}
							<code>sort[].field</code> for sorting purposes.
						</td>
					</tr>
					<tr>
						<td>Handling of tenantId</td>
						<td>
							To replicate the same functionality as an existing
							endpoint for a specific tenant, the tenantId is
							appended as a path parameter as{" "}
							<code>/tenant-id/{"{tenant-id}"}</code>
						</td>
						<td>
							The tenantId is handled as a field, not path
							parameter.
						</td>
					</tr>
					<tr>
						<td>Historic Data</td>
						<td>
							Camunda 7 has many endpoints to retrieve historic
							data that have a similar parameter set as the
							analogous runtime endpoint.
						</td>
						<td>
							Camunda 8 does not differentiate via separate
							endpoints and insteads uses states, startDates and
							endDates to replicate a similar functionality.
						</td>
					</tr>
				</tbody>
			</table>
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
