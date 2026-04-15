/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
export const event_subscription = [
	{
		origin: {
			path: "/event-subscription",
			operation: "get",
		},
		target: {
			path: "/message-subscriptions/search",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				In Camunda 8.9, the{" "}
				<code>POST Search message subscriptions</code> endpoint can be
				used to search for message subscriptions. Note that Camunda 8
				only supports message-type event subscriptions via this endpoint.
				Other event subscription types (signal, conditional, timer) do
				not have a dedicated search endpoint in Camunda 8.9.
			</div>
		),
	},
	{
		origin: {
			path: "/event-subscription/count",
			operation: "get",
		},
		target: {
			path: "/message-subscriptions/search",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				In Camunda 8.9, the{" "}
				<code>POST Search message subscriptions</code> endpoint can be
				used to search for message subscriptions. The response includes a{" "}
				<code>page.totalItems</code> field that provides the total count
				of matching subscriptions. Note that this only covers
				message-type event subscriptions.
			</div>
		),
	},
];
