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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) tenantId</pre>,
					rightEntry: <pre>(string) tenantId</pre>,
				},
				{
					leftEntry: <pre>(boolean) withoutTenantId</pre>,
					rightEntry: (
						<>
							<pre>(string) tenantId</pre>
							<p>
								Map <code>withoutTenantId=true</code> to the
								<code>tenantId</code> value{" "}
								<code>&lt;default&gt;</code>.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string) processDefinitionId</pre>,
					rightEntry: (
						<>
							<pre>(string) processDefinitionKey</pre>
							<p>
								Resolve the Camunda 7 process definition id to
								the assigned Camunda 8 process definition key.
								See{" "}
								<a href="#key-to-id">
									Camunda 7 key → Camunda 8 id
								</a>
								.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(object) variables</pre>,
					rightEntry: <pre>(object) variables</pre>,
				},
			],
			additionalInfo: (
				<p>
					Camunda 8.10 requires <code>variables</code>. Send the
					variables as raw JSON values; remove the Camunda 7{" "}
					<code>value</code>, <code>type</code>, and{" "}
					<code>valueInfo</code> wrappers before sending them.
					Use an empty object when no variables are needed.
				</p>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) businessKey</pre>,
					rightEntry: (
						<p>
							Camunda 8.10 does not accept a business key for
							conditional evaluation.
						</p>
					),
				},
			],
		},
		mappedExplanation: (
			<div>
				In Camunda 8.10, the{" "}
				<code>POST Evaluate root level conditional start events</code>{" "}
				endpoint can be used to evaluate conditions. It evaluates
				root-level conditional start events for process definitions and
				returns the keys of all created process instances.
			</div>
		),
	},
];
