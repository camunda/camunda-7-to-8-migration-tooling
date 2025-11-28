/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
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
