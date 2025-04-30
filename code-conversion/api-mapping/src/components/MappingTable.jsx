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
