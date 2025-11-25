/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
export const task_comment = [
	{
		origin: {
			path: "/task/{id}/comment",
			operation: "get",
		},
		target: {},
		discontinuedExplanation: (
			<div>
				Not yet possible in Camunda 8.8. No comments for tasks yet.
			</div>
		),
	},
	{
		origin: {
			path: "/task/{id}/comment/create",
			operation: "post",
		},
		target: {},
		discontinuedExplanation: (
			<div>
				Not yet possible in Camunda 8.8. No comments for tasks yet.
			</div>
		),
	},
	{
		origin: {
			path: "/task/{id}/comment/{commentId}",
			operation: "get",
		},
		target: {},
		discontinuedExplanation: (
			<div>
				Not yet possible in Camunda 8.8. No comments for tasks yet.
			</div>
		),
	},
];
