import styles from "./tables.module.css";
import { MappingTable } from "./MappingTable";

export function Tables({ sectionRefs, mappedC7Endpoints }) {
	return (
		<section className={styles.tables}>
			{mappedC7Endpoints.map((endpoint, index) => {
				return (
					<div key={index}>
						<h2 ref={(el) => (sectionRefs.current[index] = el)}>
							{endpoint.section}
						</h2>
						<MappingTable endpoint={endpoint} />
					</div>
				);
			})}
		</section>
	);
}
