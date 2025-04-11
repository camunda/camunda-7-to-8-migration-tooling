export function Tabs({ reducedMappingIndex, handleSelectionClick }) {
	return (
		<section className="tabs">
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
