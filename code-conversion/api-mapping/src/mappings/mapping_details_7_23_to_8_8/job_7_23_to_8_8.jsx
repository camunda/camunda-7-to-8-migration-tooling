export const job_7_23_to_8_8 = [
	// {
	// 	origin: {
	// 		path: "/authorization",
	// 		operation: "get",
	// 	},
	// 	target: {
	// 		path: "/authorizations/search",
	// 		operation: "post",
	// 	},
	// 	direct: (
	// 		<div>
	// 			<div>
	// 				Mapping of C7 endpoint parameters to C8 endpoint request
	// 				body fields:
	// 			</div>
	// 			<table>
	// 				<thead>
	// 					<tr>
	// 						<th>C7 Parameter</th>
	// 						<th>C8 Field</th>
	// 					</tr>
	// 				</thead>
	// 				<tbody>
	// 					<tr>
	// 						<td>
	// 							<pre>
	// 								(string) id
	// 								<br />
	// 								(integer) type
	// 								<br />
	// 								(integer) resourceType
	// 								<br />
	// 								(string) resourceId
	// 							</pre>
	// 						</td>
	// 						<td>
	// 							<pre>
	// 								(string) filter.ownerId
	// 								<br />
	// 								(enum) filter.ownerType
	// 								<br />
	// 								(string[]) filter.resourceIds
	// 								<br />
	// 								(enum) filter.resourceType
	// 							</pre>
	// 						</td>
	// 					</tr>
	// 					<tr>
	// 						<td>
	// 							<pre>
	// 								(string[]) userIdIn
	// 								<br />
	// 								(string[]) groupIdIn
	// 							</pre>
	// 						</td>
	// 						<td>
	// 							Replaced by a combination of{" "}
	// 							<code>resourceIds</code> and{" "}
	// 							<code>resourceType</code>
	// 						</td>
	// 					</tr>
	// 					<tr>
	// 						<td>
	// 							<pre>
	// 								(string) sortBy
	// 								<br />
	// 								(string) sortOrder
	// 							</pre>
	// 						</td>
	// 						<td>
	// 							<pre>
	// 								(string) sort[].field
	// 								<br />
	// 								(enum) sort[].order
	// 							</pre>
	// 						</td>
	// 					</tr>
	// 					<tr>
	// 						<td>
	// 							<pre>
	// 								(integer) firstResult
	// 								<br />
	// 								(integer) maxResults
	// 							</pre>
	// 						</td>
	// 						<td>
	// 							<pre>
	// 								(integer) page.from
	// 								<br />
	// 								(integer) page.limit
	// 								<br />
	// 								(object[]) page.searchAfter
	// 								<br />
	// 								(object[]) page.searchBefore
	// 							</pre>
	// 						</td>
	// 					</tr>
	// 				</tbody>
	// 			</table>
	// 		</div>
	// 	),
	// },
];
