import { EndpointInfo } from "./EndpointInfo";
import styles from "./mappingTable.module.css";

export function MappingTable({ endpoint }) {
	return (
		<table>
			<thead>
				<tr>
					<th className={styles.c7EndpointColumn}>C7 Endpoint</th>
					<th className={styles.c8EndpointColumn}>C8 Endpoint</th>
					<th className={styles.explanationColumn}>Explanation</th>
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
