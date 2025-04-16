import { EndpointInfo } from "./EndpointInfo";
import styles from "./mappingTable.module.css";

export function MappingTable({ endpoint }) {
	return (
		<table>
			<thead>
				<tr>
					<th className={styles.c7EndpointColumn}>C7 Endpoint</th>
					<th className={styles.c8EndpointColumn}>C8 Endpoint</th>
					<th className={styles.explanationColumn} colSpan="2">
						Explanation
					</th>
				</tr>
			</thead>
			<tbody>
				{endpoint.endpoints.map((endpoint) => {
					return (
						<>
							{endpoint.direct || endpoint.notPossible ? (
								<tr
									key={
										endpoint.c7Info.path +
										endpoint.c7Info.operation
									}
								>
									<td>
										<EndpointInfo
											endpointInfo={endpoint.c7Info}
										/>
									</td>
									<td>
										{endpoint.c8Info ? (
											<EndpointInfo
												endpointInfo={endpoint.c8Info}
											/>
										) : endpoint.direct ||
										  endpoint.notPossible ? (
											<div>no suitable mapping</div>
										) : (
											<div>to be defined</div>
										)}
									</td>
									<td>{endpoint.direct}</td>
									<td>{endpoint.notPossible}</td>
								</tr>
							) : (
								<tr
									key={
										endpoint.c7Info.path +
										endpoint.c7Info.operation
									}
								>
									<td>
										<EndpointInfo
											endpointInfo={endpoint.c7Info}
										/>
									</td>
									<td>
										{endpoint.c8Info ? (
											<EndpointInfo
												endpointInfo={endpoint.c8Info}
											/>
										) : endpoint.explanation ? (
											<p>no suitable mapping</p>
										) : (
											<p>to be defined</p>
										)}
									</td>
									<td colSpan="2">
										{endpoint.explanation || (
											<p>to be defined</p>
										)}
									</td>
								</tr>
							)}
						</>
					);
				})}
			</tbody>
		</table>
	);
}
