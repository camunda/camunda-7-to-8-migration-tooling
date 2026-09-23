/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
export const version = [
	{
		origin: {
			path: "/version",
			operation: "get",
		},
		target: {
			path: "/topology",
			operation: "get",
		},
		mappedExplanation: (
			<div>
				In Camunda 8.10, the <code>GET /topology</code> endpoint returns
				the gateway version together with the current cluster topology.
				It is the closest available equivalent to the Camunda 7 version
				endpoint, but the response shape is different.
			</div>
		),
	},
];
