export const external_task_7_23_to_8_8 = [
	{
		origin: {
			path: "/external-task",
			operation: "get",
		},
		target: {},
		explanation: (
			<p>
				There is no endpoint in Camunda 8.8 to search for jobs without
				activating them. Please take a look at the{" "}
				<code>POST Activate jobs</code> endpoint to check if it meets
				your needs.
			</p>
		),
	},
	{
		origin: {
			path: "/external-task",
			operation: "post",
		},
		target: {},
		explanation: (
			<p>
				There is no endpoint in Camunda 8.8 to search for jobs without
				activating them. Please take a look at the{" "}
				<code>POST Activate jobs</code> endpoint to check if it meets
				your needs.
			</p>
		),
	},
	{
		origin: {
			path: "/external-task/count",
			operation: "get",
		},
		target: {},
		explanation: (
			<p>
				There is no endpoint in Camunda 8.8 to search for jobs without
				activating them. Please take a look at the{" "}
				<code>POST Activate jobs</code> endpoint to check if it meets
				your needs.
			</p>
		),
	},
	{
		origin: {
			path: "/external-task/count",
			operation: "post",
		},
		target: {},
		explanation: (
			<p>
				There is no endpoint in Camunda 8.8 to search for jobs without
				activating them. Please take a look at the{" "}
				<code>POST Activate jobs</code> endpoint to check if it meets
				your needs.
			</p>
		),
	},
	{
		origin: {
			path: "/external-task/fetchAndLock",
			operation: "post",
		},
		target: {
			path: "/jobs/activation",
			operation: "post",
		},
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) workerId</pre>
							</td>
							<td>
								<pre>(string) worker</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(int32) maxTasks</pre>
							</td>
							<td>
								<pre>(int32) maxJobsToActivate</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(int64) asyncResponseTimeout</pre>
							</td>
							<td>
								<pre>(int64) requestTimeout</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(int64) asyncResponseTimeout</pre>
							</td>
							<td>
								<pre>(int64) requestTimeout</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) topics[].topicName</pre>
							</td>
							<td>
								<pre>(string) type</pre>
								<p>Only one type at a time.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(int64) topics[].lockDuration</pre>
							</td>
							<td>
								<pre>(int64) timeout</pre>
								<p>Only one type at a time.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string[]) topics[].variables</pre>
							</td>
							<td>
								<pre>(string[]) fetchVariable</pre>
								<p>Only one type at a time.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string[]) topics[].tenantIdIn
									<br />
									(boolean) withoutTenantId
								</pre>
							</td>
							<td>
								<pre>(string[]) tenantIds</pre>
								<p>Only one type at a time.</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
		notPossible: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Explanation</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(boolean) usePriority</pre>
							</td>
							<td>
								<p>No job priority in Camunda 8.8.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(boolean) usePriority</pre>
							</td>
							<td>
								<p>No job priority in Camunda 8.8.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) topics[].businessKey</pre>
							</td>
							<td>
								<p>
									No businessKey in Camunda 8.8. Planned for
									Camunda 8.9, but unlikly to be used in this
									context.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(string) topics[].processDefinitionId
									<br />
									(string[]) topics[].processDefinitionIdIn
									<br />
									(string) topics[].processDefinitionKey
									<br />
									(string[]) topics[].processDefinitionKeyIn
									<br />
									(string)
									topics[].processDefinitionVersionTag
								</pre>
							</td>
							<td>
								<p>
									In Camunda 8.8, it is not possible to
									retrict the activation of jobs to specific
									process definitions.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object) topics[].processVariables</pre>
							</td>
							<td>
								<p>
									In Camunda 8.8, it is not possible to
									retrict the activation of jobs to process
									instances with specific variables and their
									values.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(boolean) topics[].deserializeValues</pre>
							</td>
							<td>
								<p>Not applicable in Camunda 8, only JSON.</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(boolean)
									topics[].includeExtensionProperties
								</pre>
							</td>
							<td>
								<p>
									Extension properties are always included in
									the response as <code>customHeaders</code>.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
	},
	{
		origin: {
			path: "/external-task/retries",
			operation: "put",
		},
		target: {
			path: "/jobs/{jobKey}",
			operation: "patch",
		},
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(int32) retries</pre>
							</td>
							<td>
								<pre>(int32) changeset.retries</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string[]) externalTaskIds</pre>
							</td>
							<td>
								<pre>(string) jobKey</pre>
								<p>One job at a time.</p>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					In Camunda 8.8, this endpoint cannot be used to raise an
					incident. The provided number of retries has to be a
					positive number.
				</p>
				<p>
					Use the <code>POST Fail job</code> endpoint instead. With no
					remaining retries, an incident will be raised.
				</p>
			</>
		),
		notPossible: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Explanation</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string[]) processInstanceIds</pre>
							</td>
							<td>
								<p>
									Not appliable, as one specified job is
									patched.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object) externalTaskQuery</pre>
							</td>
							<td>
								<p>
									Not appliable, as one specified job is
									patched.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object) processInstanceQuery</pre>
							</td>
							<td>
								<p>
									Not appliable, as one specified job is
									patched.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object) historicProcessInstanceQuery</pre>
							</td>
							<td>
								<p>
									Not appliable, as one specified job is
									patched.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
	},
	{
		origin: {
			path: "/external-task/retries-async",
			operation: "post",
		},
		target: {
			path: "/jobs/{jobKey}",
			operation: "patch",
		},
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(int32) retries</pre>
							</td>
							<td>
								<pre>(int32) changeset.retries</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string[]) externalTaskIds</pre>
							</td>
							<td>
								<pre>(string) jobKey</pre>
								<p>One job at a time.</p>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					In Camunda 8.8, this endpoint cannot be used to raise an
					incident. The provided number of retries has to be a
					positive number.
				</p>
				<p>
					Use the <code>POST Fail job</code> endpoint instead. With no
					remaining retries, an incident will be raised.
				</p>
			</>
		),
		notPossible: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Explanation</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string[]) processInstanceIds</pre>
							</td>
							<td>
								<p>
									Not appliable, as one specified job is
									patched.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object) externalTaskQuery</pre>
							</td>
							<td>
								<p>
									Not appliable, as one specified job is
									patched.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object) processInstanceQuery</pre>
							</td>
							<td>
								<p>
									Not appliable, as one specified job is
									patched.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object) historicProcessInstanceQuery</pre>
							</td>
							<td>
								<p>
									Not appliable, as one specified job is
									patched.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
	},
	{
		origin: {
			path: "/external-task/topic-names",
			operation: "get",
		},
		target: {},
		explanation: (
			<p>There is no endpoint in Camunda 8.8 to search for jobs types.</p>
		),
	},
	{
		origin: {
			path: "/external-task/{id}",
			operation: "get",
		},
		target: {},
		explanation: (
			<p>
				There is no endpoint in Camunda 8.8 to retrieve a specific job
				by <code>jobKey</code>.
			</p>
		),
	},
	{
		origin: {
			path: "/external-task/{id}/bpmnError",
			operation: "post",
		},
		target: {
			path: "/jobs/{jobKey}/error",
			operation: "post",
		},
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) jobKey</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) errorCode</pre>
							</td>
							<td>
								<pre>(string) errorCode</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) errorMessage</pre>
							</td>
							<td>
								<pre>(string) errorMessage</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object) variables</pre>
							</td>
							<td>
								<pre>(object) variables</pre>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
		notPossible: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Explanation</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) workerId</pre>
							</td>
							<td>
								<p>
									Not required to throw a BPMN error for a
									job.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
	},
	{
		origin: {
			path: "/external-task/{id}/complete",
			operation: "post",
		},
		target: {
			path: "/jobs/{jobKey}/completion",
			operation: "post",
		},
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) jobKey</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object) variables</pre>
							</td>
							<td>
								<pre>(object) variables</pre>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
		notPossible: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Explanation</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(object) localVariables</pre>
							</td>
							<td>
								<p>
									For this endpoint in Camunda 8.8, local
									variables cannot be set. All variables are
									treated the same. If they are defined as
									local on the task, they will be merged into
									the task scope only. If not, they will be
									merged to all parent scopes or until the
									variable is defined as local in a scope.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) workerId</pre>
							</td>
							<td>
								<p>Not required to complete a job.</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
	},
	{
		origin: {
			path: "/external-task/{id}/errorDetails",
			operation: "get",
		},
		target: {},
		explanation: (
			<p>
				There is no endpoint in Camunda 8.8 to retrieve the error
				details for a specific running job.
			</p>
		),
	},
	{
		origin: {
			path: "/external-task/{id}/extendLock",
			operation: "post",
		},
		target: {
			path: "/jobs/{jobKey}",
			operation: "patch",
		},
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) jobKey</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(int64) newDuration</pre>
							</td>
							<td>
								<pre>(int64) changeset.timeout</pre>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
		notPossible: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Explanation</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) workerId</pre>
							</td>
							<td>
								<p>
									Not necessary to patch the timeout of a job.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
	},
	{
		origin: {
			path: "/external-task/{id}/failure",
			operation: "post",
		},
		target: {
			path: "/jobs/{jobKey}/failure",
			operation: "post",
		},
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) jobKey</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) errorMessage</pre>
							</td>
							<td>
								<pre>(string) errorMessage</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>
									(int32) retries
									<br />
									(int64) retryTimeout
								</pre>
							</td>
							<td>
								<pre>
									(int32) retries
									<br />
									(int64) retryBackOff
								</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object) variables</pre>
							</td>
							<td>
								<pre>(object) variables</pre>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
		notPossible: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Explanation</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) errorDetails</pre>
							</td>
							<td>
								<p>
									In Camunda 8.8, there is not additional
									field for error details.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(object) localVariables</pre>
							</td>
							<td>
								<p>
									For this endpoint in Camunda 8.8, local
									variables cannot be set. All variables are
									treated the same. If they are defined as
									local on the task, they will be merged into
									the task scope only. If not, they will be
									merged to all parent scopes or until the
									variable is defined as local in a scope.
								</p>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(string) workerId</pre>
							</td>
							<td>
								<p>
									Not necessary to patch the timeout of a job.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
	},
	{
		origin: {
			path: "/external-task/{id}/lock",
			operation: "post",
		},
		target: {
			path: "/jobs/{jobKey}",
			operation: "patch",
		},
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) jobKey</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(int64) newDuration</pre>
							</td>
							<td>
								<pre>(int64) changeset.timeout</pre>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
		notPossible: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Explanation</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) workerId</pre>
							</td>
							<td>
								<p>
									Not necessary to patch the timeout of a job.
								</p>
							</td>
						</tr>
					</tbody>
				</table>
			</>
		),
	},
	{
		origin: {
			path: "/external-task/{id}/priority",
			operation: "put",
		},
		target: {},
		explanation: <p>No job priority in Camunda 8.8.</p>,
	},
	{
		origin: {
			path: "/external-task/{id}/retries",
			operation: "put",
		},
		target: {
			path: "/jobs/{jobKey}",
			operation: "patch",
		},
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) jobKey</pre>
							</td>
						</tr>
						<tr>
							<td>
								<pre>(int32) retries</pre>
							</td>
							<td>
								<pre>(int32) changeset.retries</pre>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					In Camunda 8.8, this endpoint cannot be used to raise an
					incident. The provided number of retries has to be a
					positive number.
				</p>
				<p>
					Use the <code>POST Fail job</code> endpoint instead. With no
					remaining retries, an incident will be raised.
				</p>
			</>
		),
	},
	{
		origin: {
			path: "/external-task/{id}/unlock",
			operation: "post",
		},
		target: {
			path: "/jobs/{jobKey}/completion",
			operation: "post",
		},
		direct: (
			<>
				<table>
					<thead>
						<tr>
							<th>Camunda 7</th>
							<th>Camunda 8</th>
						</tr>
					</thead>
					<tbody>
						<tr>
							<td>
								<pre>(string) id</pre>
							</td>
							<td>
								<pre>(string) jobKey</pre>
							</td>
						</tr>
					</tbody>
				</table>
				<p>
					Set <code>result.denied</code> to true to reject the
					completion and activate the job again.
				</p>
			</>
		),
	},
];
