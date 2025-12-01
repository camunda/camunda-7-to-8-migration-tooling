/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import Markdown from "react-markdown";

export function EndpointInfo({ endpointInfo }) {
	return (
		<>
			<h3>{endpointInfo.details?.summary}</h3>
			<code>
				{endpointInfo.operation?.toUpperCase() +
					" " +
					endpointInfo.path}
			</code>
			<br />
			<a href={endpointInfo.url} target="_blank">
				Link to docs
			</a>
			<br />
			<Markdown>{endpointInfo.details?.description}</Markdown>
		</>
	);
}
