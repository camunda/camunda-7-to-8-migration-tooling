export function MappingTableEntry({
	leftHeader,
	rightHeader,
	rowInfo,
	additionalInfo,
}) {
	return (
		<>
			<table>
				<thead>
					<tr>
						<th>{leftHeader}</th>
						<th>{rightHeader}</th>
					</tr>
				</thead>
				<tbody>
					{rowInfo?.map(({ leftEntry, rightEntry }, index) => {
						return (
							<tr key={leftEntry + rightEntry + index}>
								<td>{leftEntry}</td>
								<td>{rightEntry}</td>
							</tr>
						);
					})}
				</tbody>
			</table>
			<div>{additionalInfo}</div>
		</>
	);
}
