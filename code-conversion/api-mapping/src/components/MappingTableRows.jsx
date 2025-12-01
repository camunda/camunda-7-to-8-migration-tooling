/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { Fragment } from "react";
import { EndpointInfo } from "./EndpointInfo";
import { MappingTableEntry } from "./MappingTableEntry";
import styles from "./mappingTable.module.css";

export const MappingTableRows = ({ endpoint }) => (
	<>
		{endpoint.endpoints.map(
			({
				c7Info,
				c8Info,
				direct,
				conceptual,
				discontinued,
				mappedExplanation,
				roadmapExplanation,
				discontinuedExplanation,
			}) => (
				<Fragment key={c7Info.path + c7Info.operation}>
					{direct || conceptual || discontinued ? (
						<>
							<tr key={c7Info.path + c7Info.operation + "1"}>
								<td rowSpan="3">
									<EndpointInfo endpointInfo={c7Info} />
								</td>
								<td rowSpan="3">
									{c8Info ? (
										<EndpointInfo endpointInfo={c8Info} />
									) : null}
								</td>
								{direct ? (
									<td className={styles.green}>
										<MappingTableEntry
											leftHeader="Camunda 7 Mapped Parameter"
											rightHeader="Camunda 8 Equivalent Parameter"
											rowInfo={direct.rowInfo}
											additionalInfo={
												direct.additionalInfo
											}
										/>
									</td>
								) : null}
							</tr>
							<tr key={c7Info.path + c7Info.operation + "2"}>
								{conceptual ? (
									<td className={styles.orange}>
										<MappingTableEntry
											leftHeader="Camunda 7 Mapped Parameter"
											rightHeader="Camunda 8 Conceptionally Equivalent Parameter"
											rowInfo={conceptual.rowInfo}
											additionalInfo={
												conceptual.additionalInfo
											}
										/>
									</td>
								) : null}
							</tr>
							<tr key={c7Info.path + c7Info.operation + "3"}>
								{discontinued ? (
									<td className={styles.red}>
										<MappingTableEntry
											leftHeader="Camunda 7 Parameter"
											rightHeader="Explanation"
											rowInfo={discontinued.rowInfo}
											additionalInfo={
												discontinued.additionalInfo
											}
										/>
									</td>
								) : null}
							</tr>
						</>
					) : (
						<tr
							className={
								!c8Info &&
								!mappedExplanation &&
								!roadmapExplanation &&
								!discontinuedExplanation
									? styles.grey
									: null
							}
							key={c7Info.path + c7Info.operation + "4"}
						>
							<td>
								<EndpointInfo endpointInfo={c7Info} />
							</td>
							<td>
								{c8Info ? (
									<EndpointInfo endpointInfo={c8Info} />
								) : discontinuedExplanation ? (
									<div>no suitable mapping</div>
								) : roadmapExplanation ? (
									<div>no suitable mapping yet</div>
								) : (
									<div>to be defined</div>
								)}
							</td>
							<td
								className={
									mappedExplanation
										? styles.green
										: roadmapExplanation
										? styles.orange
										: discontinuedExplanation
										? styles.red
										: null
								}
							>
								{mappedExplanation ||
									roadmapExplanation ||
									discontinuedExplanation || (
										<div>to be defined</div>
									)}
							</td>
						</tr>
					)}
				</Fragment>
			)
		)}
	</>
);
