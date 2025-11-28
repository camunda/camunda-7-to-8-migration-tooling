/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import styles from "./tabs.module.css";

export function Tabs({ reducedMappingIndex, handleSelectionClick }) {
	return (
		<div className={styles.tabs}>
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
		</div>
	);
}
