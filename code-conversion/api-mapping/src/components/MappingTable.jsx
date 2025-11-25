/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import styles from "./mappingTable.module.css";
import { MappingTableRows } from "./MappingTableRows";

export function MappingTable({ endpoint }) {
	return (
		<table>
			<thead>
				<tr className={styles.headers}>
					<th rowSpan="3">C7 Endpoint Information</th>
					<th rowSpan="3">C8 Endpoint Information</th>
					<th className={styles.green}>
						Mapped Endpoint Explanation or ‡‡ Direct Parameter
						Mappings
					</th>
				</tr>
				<tr>
					<th className={styles.orange}>
						On Roadmap Explanation or ‡‡ Conceptual Parameter
						Mappings
					</th>
				</tr>
				<tr>
					<th className={styles.red}>
						Discontinued Endpoint Explanation or ‡‡ Discontinued
						Parameter Mappings
					</th>
				</tr>
			</thead>
			<tbody>
				<MappingTableRows endpoint={endpoint} />
			</tbody>
		</table>
	);
}
