import styles from "./tabs.module.css";

export function Tabs({ reducedMappingIndex, handleSelectionClick }) {
	return (
		<section className={styles.tabs}>
			{reducedMappingIndex.map((mapping) => {
				return (
					<button
						key={mapping.id}
						onClick={() => handleSelectionClick(mapping.id)}
					>
						{mapping.tabName}
					</button>
				);
			})}
		</section>
	);
}
