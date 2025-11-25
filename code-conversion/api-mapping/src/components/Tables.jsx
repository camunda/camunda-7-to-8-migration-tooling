/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
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
