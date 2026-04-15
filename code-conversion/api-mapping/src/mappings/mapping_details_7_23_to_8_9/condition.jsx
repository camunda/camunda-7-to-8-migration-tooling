/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
export const condition = [
	{
		origin: {
			path: "/condition",
			operation: "post",
		},
		target: {
			path: "/conditionals/evaluation",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				In Camunda 8.9, the{" "}
				<code>POST Evaluate root level conditional start events</code>{" "}
				endpoint can be used to evaluate conditions. It evaluates
				root-level conditional start events for process definitions and
				returns the keys of all created process instances.
			</div>
		),
	},
];
