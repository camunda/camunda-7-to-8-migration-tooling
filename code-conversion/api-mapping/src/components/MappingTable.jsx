import { EndpointInfo } from "./EndpointInfo";
import styles from "./mappingTable.module.css";

export function MappingTable({ endpoint }) {
	return (
		<table>
			<thead>
				<tr>
					<th className={styles.c7EndpointColumn}>C7 Endpoint</th>
					<th className={styles.c8EndpointColumn}>C8 Endpoint</th>
					<th className={styles.directColumn}>Direct Mappings</th>
					<th className={styles.notPossibleColumn}>
						Not possible/applicable
					</th>
				</tr>
			</thead>
			<tbody>
				{endpoint.endpoints.map((endpoint) => {
					return (
						<>
							{(endpoint.direct && endpoint.notPossible) ||
							(endpoint.direct == null &&
								endpoint.notPossible == null) ? (
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
									<td>
										{endpoint.direct !== undefined
											? endpoint.direct
											: "to be defined"}
									</td>
									<td>
										{endpoint.notPossible !== undefined
											? endpoint.notPossible
											: "to be defined"}
									</td>
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
										) : endpoint.direct ||
										  endpoint.notPossible ? (
											<div>no suitable mapping</div>
										) : (
											<div>to be defined</div>
										)}
									</td>
									<td colSpan="2">
										{endpoint.direct}
										{endpoint.notPossible}
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
