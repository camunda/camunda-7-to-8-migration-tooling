import { EndpointInfo } from "./EndpointInfo";

export function MappingTable({ endpoint }) {
	return (
		<table>
			<thead>
				<tr>
					<th className="c7-endpoint-column">C7 Endpoint</th>
					<th className="c8-endpoint-column">C8 Endpoint</th>
					<th className="explanation-column">Explanation</th>
				</tr>
			</thead>
			<tbody>
				{endpoint.endpoints.map((endpoint) => {
					return (
						<tr
							key={
								endpoint.c7Info.path + endpoint.c7Info.operation
							}
						>
							<td>
								<EndpointInfo endpointInfo={endpoint.c7Info} />
							</td>
							<td>
								{endpoint.c8Info ? (
									<EndpointInfo
										endpointInfo={endpoint.c8Info}
									/>
								) : endpoint.explanation ? (
									<div>no suitable mapping</div>
								) : (
									<div>to be defined</div>
								)}
							</td>
							<td>{endpoint.explanation || "to be defined"}</td>
						</tr>
					);
				})}
			</tbody>
		</table>
	);
}
