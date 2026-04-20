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
			path: "/status",
			operation: "get",
		},
		mappedExplanation: (
			<div>
				In Camunda 8.9, the <code>GET Cluster status</code> endpoint can
				be used to check the health and version of the cluster. Unlike
				the Camunda 7 version endpoint which returns the REST API
				version, the Camunda 8 status endpoint checks cluster health by
				verifying partition leadership and returns cluster topology
				information.
			</div>
		),
	},
];
