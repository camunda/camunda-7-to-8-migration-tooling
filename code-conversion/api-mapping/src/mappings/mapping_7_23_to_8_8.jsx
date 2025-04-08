import { c7_23 } from "../openapi/camunda7/c7_23";
import { c8_8 } from "../openapi/camunda8/c8_8";

export const mapping_7_23_to_8_8 = {
	id: "7_23_to_8_8",
	tab_name: "7.23 to 8.8",
	c7BaseUrl:
		"https://docs.camunda.org/rest/camunda-bpm-platform/7.23-SNAPSHOT/#tag/",
	c8BaseUrl:
		"https://docs.camunda.io/docs/8.8/apis-tools/camunda-api-rest/specifications/",
	c7_specification: c7_23,
	c8_specification: c8_8,
	internalAPIs: ["Authorization", "Batch", "Filter", "Group"],
	historyAPIs: [
		"Historic Activity Instance",
		"Historic Batch",
		"Historic Decision Definition",
		"Historic Decision Instance",
		"Historic Decision Requirements Definition",
		"Historic Detail",
		"Historic External Task Log",
		"Historic Identity Link Log",
		"Historic Incident",
		"Historic Job Log",
		"Historic Process Definition",
		"Historic Process Instance",
		"Historic Task Instance",
		"Historic User Operation Log",
		"Historic Variable Instance",
		"History Cleanup",
	],
	mappings: [
		{
			origin: {
				path: "/authorization/create",
				operation: "post",
			},
			target: {
				path: "/authorizations",
				operation: "post",
			},
			purpose: "This endpoint is used to create a new authorization",
			explanation: (
				<div>
					<div>Some extra text here</div>
					<table>
						<thead>
							<tr>
								<th>Parameter</th>
								<th>Field</th>
							</tr>
						</thead>
						<tbody>
							<tr>
								<td>a</td>
								<td>b</td>
							</tr>
						</tbody>
					</table>
				</div>
			),
		},
	],
};
