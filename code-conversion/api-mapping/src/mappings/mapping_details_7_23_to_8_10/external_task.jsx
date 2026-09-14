/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
export const external_task = [
	{
		origin: {
			path: "/external-task",
			operation: "get",
		},
		target: {
			path: "/jobs/search",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				In Camunda 8.10, the <code>POST Search jobs</code> endpoint can
				be used to search for jobs without activating them. Note that
				external tasks in Camunda 7 correspond to BPMN element jobs
				in Camunda 8. Set <code>filter.kind</code> to{" "}
				<code>BPMN_ELEMENT</code> and, when the C7 query identifies a
				topic, set <code>filter.type</code> to the corresponding C8
				job type. <code>filter.kind</code> alone also includes other
				BPMN element job types, so a search without a known topic/type
				restriction is not an exact C7 external-task mapping.
				Unlike Camunda 7's current external tasks, C8 search results can
				include completed and canceled jobs. Follow{" "}
				<code>page.endCursor</code> through all pages and use{" "}
				<code>endTime == null</code> as the C8 current-job candidate
				check before applying C7 pagination. C7 and C8 lifecycle and
				exhausted-retry semantics are not fully equivalent; preserve
				source correlation for exact parity or mark the case
				unsupported. C7 <code>withRetriesLeft=true</code> includes
				tasks with{" "}
				<code>retries &gt; 0</code> or a null retry value. Because C8
				exposes a non-null <code>retries</code> field,{" "}
				<code>filter.retries.$gt</code> is not an exact mapping; treat
				this selector as unsupported unless migration metadata preserves
				the C7 null case. For{" "}
				<code>noRetriesLeft=true</code>, retain only jobs with{" "}
				<code>retries == 0</code> before applying C7 pagination.
				Camunda 7's <code>active</code> and{" "}
				<code>suspended</code> query flags are valid filters, but C8's{" "}
				<code>JobFilter</code> has no equivalent fields. If either is{" "}
				<code>true</code>, apply the predicate in a separate
				post-filter before applying C7 pagination, or mark the mapping
				unsupported. A false value is a no-op in C7; do not silently
				omit a true filter.
				C7 criteria not listed above must not be silently dropped.
				Translate <code>workerId</code>, process/activity/tenant
				selectors, priority bounds, and sorting only when migration
				metadata and the corresponding C8 field preserve their
				semantics. Validate priority bounds against C8's signed
				32-bit range and account for jobs without stored priority.
				<code>externalTaskId</code> and{" "}
				<code>externalTaskIdIn</code> have no C8 job-field equivalent.
				The <code>locked</code>, <code>notLocked</code>,{" "}
				<code>lockExpirationAfter</code>, and{" "}
				<code>lockExpirationBefore</code> criteria require a
				source-correlated post-filter. Apply these predicates before
				C7 pagination, or mark the request unsupported.
			</div>
		),
	},
	{
		origin: {
			path: "/external-task",
			operation: "post",
		},
		target: {
			path: "/jobs/search",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				In Camunda 8.10, the <code>POST Search jobs</code> endpoint can
				be used to search for jobs without activating them. Note that
				external tasks in Camunda 7 correspond to BPMN element jobs
				in Camunda 8. Set <code>filter.kind</code> to{" "}
				<code>BPMN_ELEMENT</code> and, when the C7 query identifies a
				topic, set <code>filter.type</code> to the corresponding C8
				job type. <code>filter.kind</code> alone also includes other
				BPMN element job types, so a search without a known topic/type
				restriction is not an exact C7 external-task mapping.
				Unlike Camunda 7's current external tasks, C8 search results can
				include completed and canceled jobs. Follow{" "}
				<code>page.endCursor</code> through all pages and use{" "}
				<code>endTime == null</code> as the C8 current-job candidate
				check before applying C7 pagination. C7 and C8 lifecycle and
				exhausted-retry semantics are not fully equivalent; preserve
				source correlation for exact parity or mark the case
				unsupported. C7 <code>withRetriesLeft=true</code> includes
				tasks with{" "}
				<code>retries &gt; 0</code> or a null retry value. Because C8
				exposes a non-null <code>retries</code> field,{" "}
				<code>filter.retries.$gt</code> is not an exact mapping; treat
				this selector as unsupported unless migration metadata preserves
				the C7 null case. For{" "}
				<code>noRetriesLeft=true</code>, retain only jobs with{" "}
				<code>retries == 0</code> before applying C7 pagination.
				Camunda 7's <code>active</code> and{" "}
				<code>suspended</code> query flags are valid filters, but C8's{" "}
				<code>JobFilter</code> has no equivalent fields. If either is{" "}
				<code>true</code>, apply the predicate in a separate
				post-filter before applying C7 pagination, or mark the mapping
				unsupported. A false value is a no-op in C7; do not silently
				omit a true filter.
				C7 criteria not listed above must not be silently dropped.
				Translate <code>workerId</code>, process/activity/tenant
				selectors, priority bounds, and sorting only when migration
				metadata and the corresponding C8 field preserve their
				semantics. Validate priority bounds against C8's signed
				32-bit range and account for jobs without stored priority.
				<code>externalTaskId</code> and{" "}
				<code>externalTaskIdIn</code> have no C8 job-field equivalent.
				The <code>locked</code>, <code>notLocked</code>,{" "}
				<code>lockExpirationAfter</code>, and{" "}
				<code>lockExpirationBefore</code> criteria require a
				source-correlated post-filter. Apply these predicates before
				C7 pagination, or mark the request unsupported.
			</div>
		),
	},
	{
		origin: {
			path: "/external-task/count",
			operation: "get",
		},
		target: {
			path: "/jobs/search",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				In Camunda 8.10, the <code>POST Search jobs</code> endpoint can
				be used to search for jobs. The response includes a{" "}
				<code>page.totalItems</code> field, but it can be capped. If{" "}
				<code>page.hasMoreTotalItems</code> is{" "}
				<code>true</code>, <code>page.totalItems</code> is only a
				lower bound, not the exact count. Set{" "}
				<code>filter.kind</code> to <code>BPMN_ELEMENT</code> and
				<code>filter.type</code> to the known external-task topic
				type. <code>filter.kind</code> alone also includes unrelated
				BPMN element job types, so an unqualified count is not exact
				and can overcount C7 external tasks.
				The C7 count includes only current external tasks. Follow{" "}
				<code>page.endCursor</code> through all result pages and use{" "}
				<code>endTime == null</code> as the C8 current-job candidate
				check before counting instead of using{" "}
				<code>page.totalItems</code>. C7 exhausted-retry lifecycle
				semantics require source correlation for exact parity; mark
				that case unsupported when the correlation is unavailable.
				After fetching all pages, apply the requested retry predicate
				before counting. <code>noRetriesLeft=true</code> maps to{" "}
				<code>retries == 0</code>. C7{" "}
				<code>withRetriesLeft=true</code> also includes null retry
				values, which C8 cannot represent; treat that selector as
				unsupported unless migration metadata preserves the source
				value.
				Camunda 7's <code>active</code> and{" "}
				<code>suspended</code> query flags are valid filters, but C8's{" "}
				<code>JobFilter</code> has no equivalent fields. If either is{" "}
				<code>true</code>, apply the predicate in a separate
				post-filter before counting, or mark the mapping unsupported.
				A false value is a no-op in C7; do not silently omit a true
				filter.
				C7 criteria not listed above must not be silently dropped.
				Translate <code>workerId</code>, process/activity/tenant
				selectors, priority bounds, and sorting only when migration
				metadata and the corresponding C8 field preserve their
				semantics. Validate priority bounds against C8's signed
				32-bit range and account for jobs without stored priority.
				<code>externalTaskId</code> and{" "}
				<code>externalTaskIdIn</code> have no C8 job-field equivalent.
				The <code>locked</code>, <code>notLocked</code>,{" "}
				<code>lockExpirationAfter</code>, and{" "}
				<code>lockExpirationBefore</code> criteria require a
				source-correlated post-filter. Apply these predicates before
				counting, or mark the request unsupported.
				The C8 search is eventually consistent, so even a fully paged
				count describes the current C8 index and may lag Camunda 7.
			</div>
		),
	},
	{
		origin: {
			path: "/external-task/count",
			operation: "post",
		},
		target: {
			path: "/jobs/search",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				In Camunda 8.10, the <code>POST Search jobs</code> endpoint can
				be used to search for jobs. The response includes a{" "}
				<code>page.totalItems</code> field, but it can be capped. If{" "}
				<code>page.hasMoreTotalItems</code> is{" "}
				<code>true</code>, <code>page.totalItems</code> is only a
				lower bound, not the exact count. Set{" "}
				<code>filter.kind</code> to <code>BPMN_ELEMENT</code> and
				<code>filter.type</code> to the known external-task topic
				type. <code>filter.kind</code> alone also includes unrelated
				BPMN element job types, so an unqualified count is not exact
				and can overcount C7 external tasks.
				The C7 count includes only current external tasks. Follow{" "}
				<code>page.endCursor</code> through all result pages and use{" "}
				<code>endTime == null</code> as the C8 current-job candidate
				check before counting instead of using{" "}
				<code>page.totalItems</code>. C7 exhausted-retry lifecycle
				semantics require source correlation for exact parity; mark
				that case unsupported when the correlation is unavailable.
				After fetching all pages, apply the requested retry predicate
				before counting. <code>noRetriesLeft=true</code> maps to{" "}
				<code>retries == 0</code>. C7{" "}
				<code>withRetriesLeft=true</code> also includes null retry
				values, which C8 cannot represent; treat that selector as
				unsupported unless migration metadata preserves the source
				value.
				Camunda 7's <code>active</code> and{" "}
				<code>suspended</code> query flags are valid filters, but C8's{" "}
				<code>JobFilter</code> has no equivalent fields. If either is{" "}
				<code>true</code>, apply the predicate in a separate
				post-filter before counting, or mark the mapping unsupported.
				A false value is a no-op in C7; do not silently omit a true
				filter.
				C7 criteria not listed above must not be silently dropped.
				Translate <code>workerId</code>, process/activity/tenant
				selectors, priority bounds, and sorting only when migration
				metadata and the corresponding C8 field preserve their
				semantics. Validate priority bounds against C8's signed
				32-bit range and account for jobs without stored priority.
				<code>externalTaskId</code> and{" "}
				<code>externalTaskIdIn</code> have no C8 job-field equivalent.
				The <code>locked</code>, <code>notLocked</code>,{" "}
				<code>lockExpirationAfter</code>, and{" "}
				<code>lockExpirationBefore</code> criteria require a
				source-correlated post-filter. Apply these predicates before
				counting, or mark the request unsupported.
				The C8 search is eventually consistent, so even a fully paged
				count describes the current C8 index and may lag Camunda 7.
			</div>
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) workerId</pre>,
					rightEntry: (
						<>
							<pre>(string) worker</pre>
							<p>
								<code>worker</code> identifies the worker for
								logging only. Set{" "}
								<code>withLease: true</code> to preserve
								fetch-and-lock fencing, then pass the returned{" "}
								<code>leaseToken</code> to completion, failure,
								and BPMN-error requests.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(int32) maxTasks</pre>,
					rightEntry: (
						<>
							<pre>(int32) maxJobsToActivate</pre>
							<p>
								Camunda 8 accepts one <code>type</code> per
								request. Issue one activation request per topic
								and treat <code>maxTasks</code> as a shared
								budget: set{" "}
								<code>maxJobsToActivate</code> to the remaining
								budget and subtract the number of activated jobs
								before the next request.
							</p>
						</>
					),
				},
				{
					leftEntry: <pre>(int64) asyncResponseTimeout</pre>,
					rightEntry: <pre>(int64) requestTimeout</pre>,
				},
				{
					leftEntry: <pre>(string) topics[].topicName</pre>,
					rightEntry: (
						<>
							<pre>(string) type</pre>
							<p>Only one type at a time.</p>
						</>
					),
				},
				{
					leftEntry: <pre>(int64) topics[].lockDuration</pre>,
					rightEntry: (
						<>
							<pre>(int64) timeout</pre>
							<p>Only one type at a time.</p>
						</>
					),
				},
				{
					leftEntry: <pre>(string[]) topics[].variables</pre>,
					rightEntry: (
						<>
							<pre>(string[]) fetchVariable</pre>
							<p>Only one type at a time.</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(string[]) topics[].tenantIdIn
							<br />
							(boolean) withoutTenantId
						</pre>
					),
					rightEntry: (
						<>
							<pre>(string[]) tenantIds</pre>
							<p>
								Map <code>withoutTenantId=true</code> to the{" "}
								<code>&lt;default&gt;</code> tenant alias and
								append it to <code>tenantIds</code> alongside
								any <code>tenantIdIn</code> values. Only one
								type at a time.
							</p>
						</>
					),
				},
			],
			additionalInfo: "",
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(boolean) usePriority</pre>,
					rightEntry: (
						<p>
							The Camunda 8.10 job activation request has no
							<code>usePriority</code> flag. Job priority can be
							updated separately through the Update job endpoint.
						</p>
					),
				},
				{
					leftEntry: <pre>(string) topics[].businessKey</pre>,
					rightEntry: (
						<p>
							Camunda 8.10 does not support activation-time
							filtering by business ID. Jobs from process
							instances created in Camunda 8.10 expose{" "}
							<code>businessId</code> in the activation response,
							so it can be used after activation.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) topics[].processDefinitionId
							<br />
							(string[]) topics[].processDefinitionIdIn
							<br />
							(string) topics[].processDefinitionKey
							<br />
							(string[]) topics[].processDefinitionKeyIn
							<br />
							(string) topics[].processDefinitionVersionTag
						</pre>
					),
					rightEntry: (
						<p>
							In Camunda 8.10, it is not possible to restrict the
							activation of jobs to specific process definitions.
						</p>
					),
				},
				{
					leftEntry: <pre>(object) topics[].processVariables</pre>,
					rightEntry: (
						<p>
							In Camunda 8.10, it is not possible to restrict the
							activation of jobs to process instances with
							specific variables and their values.
						</p>
					),
				},
				{
					leftEntry: <pre>(boolean) topics[].deserializeValues</pre>,
					rightEntry: <p>Not applicable in Camunda 8, only JSON.</p>,
				},
				{
					leftEntry: (
						<pre>(boolean) topics[].includeExtensionProperties</pre>
					),
					rightEntry: (
						<p>
							Extension properties are always included in the
							response as <code>customHeaders</code>.
						</p>
					),
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/external-task/retries",
			operation: "put",
		},
		target: {
			path: "/jobs/batch-update",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(int32) retries</pre>,
					rightEntry: <pre>(int32) changeset.retries</pre>,
				},
				{
					leftEntry: <pre>(string[]) externalTaskIds</pre>,
					rightEntry: (
						<p>
							These identifiers are not exposed as a Camunda 8
							job field. Resolve each one through
							migration-specific correlation from the C7
							external-task ID to its corresponding current C8
							job key; a candidate search alone cannot establish
							that relationship. If the correlation is
							unavailable, mark this selector unsupported.
							Deduplicate the correlated keys and do not pass
							the IDs directly as a batch update filter.
						</p>
					),
				},
				{
					leftEntry: <pre>(string[]) processInstanceIds</pre>,
					rightEntry: (
						<p>
							Translate each C7 process-instance ID through
							migration-specific ID-to-key correlation before
							searching. If that correlation is unavailable, mark
							the selector unsupported. Resolve the matching
							current, deduplicated Camunda 8 job keys through
							the candidate-search flow below and do not pass
							C7 process-instance IDs directly as a batch update
							filter.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) externalTaskQuery.externalTaskId
							<br />
							(string[]) externalTaskQuery.externalTaskIdIn
							<br />
							(string) externalTaskQuery.topicName
							<br />
							(string) externalTaskQuery.workerId
							<br />
							(string) externalTaskQuery.processInstanceId
							<br />
							(string[]) externalTaskQuery.processInstanceIdIn
							<br />
							(string) externalTaskQuery.activityId
							<br />
							(string[]) externalTaskQuery.activityIdIn
							<br />
							(string[]) externalTaskQuery.tenantIdIn
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(string) filter.jobKey
								<br />
								(string[]) filter.jobKey.$in
								<br />
								(JobKindEnum) filter.kind
								<br />
								(string) filter.type
								<br />
								(string) filter.worker
								<br />
								(string) filter.processInstanceKey
								<br />
								(string[]) filter.processInstanceKey.$in
								<br />
								(string) filter.elementId
								<br />
								(string[]) filter.elementId.$in
								<br />
								(string[]) filter.tenantId.$in
							</pre>
							<p>
								Use the advanced <code>$in</code> operator for
								array-valued criteria and the corresponding
								advanced string operators for C7{" "}
								<code>...Like</code> criteria.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(dateTime) externalTaskQuery.lockExpirationAfter
							<br />
							(dateTime) externalTaskQuery.lockExpirationBefore
							<br />
							(boolean) externalTaskQuery.noRetriesLeft
							<br />
							(int64) externalTaskQuery.priorityHigherThanOrEquals
							<br />
							(int64) externalTaskQuery.priorityLowerThanOrEquals
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(dateTime) filter.deadline.$gt
								<br />
								(dateTime) filter.deadline.$lt
								<br />
								(int32) filter.retries.$eq
								<br />
								(int32) filter.priority.$gte
								<br />
								(int32) filter.priority.$lte
							</pre>
							<p>
								Use the advanced comparison operators shown
								above only when searching candidates. Validate
								each C7 priority bound against the signed
								32-bit C8 range. Jobs created before Camunda
								8.10 may have no stored priority and are
								excluded by priority filters; preserve source
								correlation or mark that selector unsupported.
								For <code>noRetriesLeft</code>, emit{" "}
								<code>filter.retries.$eq=0</code> only when
								the C7 flag is <code>true</code>;{" "}
								<code>false</code> and an omitted flag are
								no-ops and must not add a retry predicate.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(string[]) processInstanceQuery.processInstanceIds
							<br />
							(string) processInstanceQuery.processDefinitionKey
							<br />
							(string[]) processInstanceQuery.processDefinitionKeyIn
							<br />
							(string[]) processInstanceQuery.tenantIdIn
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(string) filter.processInstanceKey
								<br />
								(string[]) filter.processInstanceKey.$in
								<br />
								(string) filter.processDefinitionId
								<br />
								(string[]) filter.processDefinitionId.$in
								<br />
								(string[]) filter.tenantId.$in
							</pre>
							<p>
								Use these fields only to search candidate
								<code>BPMN_ELEMENT</code> jobs. A C7 process
								definition key maps to the C8 process definition
								ID; a versioned C7 definition ID is not
								interchangeable with it. Do not pass this
								selector directly to the batch update; submit
								only the deduplicated current job keys.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) externalTaskQuery.processDefinitionId
							<br />
							(string) processInstanceQuery.processDefinitionId
						</pre>
					),
					rightEntry: (
						<>
							<pre>(string) filter.processDefinitionKey</pre>
							<p>
								Use this only to search candidates. Translate
								each version-specific Camunda 7 process
								definition ID to the corresponding Camunda 8
								process definition key, then submit only the
								deduplicated current job keys to the batch
								update.
							</p>
						</>
					),
				},
			],
			additionalInfo: (
				<>
					<p>
						The Camunda 8.10 Update jobs (batch) endpoint is
						asynchronous; its batch operation key can be used to
						track progress.
					</p>
					<p>
						For exact <code>jobKey</code> or{" "}
						<code>jobKey.$in</code> selectors, set{" "}
						<code>filter.kind</code> to{" "}
						<code>BPMN_ELEMENT</code>. For a selector with a C7
						topic, also set its corresponding{" "}
						<code>filter.type</code>. A selector without a topic
						that is not constrained by an exact job key requires a
						complete allowlist of all external-task job types and
						one search per type; a single arbitrary type
						under-selects, while <code>filter.kind</code> alone
						over-selects. If that allowlist is unavailable, mark
						the selector unsupported and do not run the batch
						update. An exact job key or correlated
						<code>jobKey.$in</code> selector does not need that
						allowlist.
					</p>
					<p>
						For retry updates, a kind/type filter can still match
						terminal job records. Search each translated selector
						first, follow all cursor pages, and use{" "}
						<code>endTime == null</code> as the C8 current-job
						candidate check. C7 and C8 lifecycle and nullable-retry
						semantics are not fully equivalent; preserve source
						correlation for exact exhausted/nullable cases and mark
						the selector unsupported when it is unavailable. Submit
						only the resulting deduplicated{" "}
						<code>jobKey.$in</code> together with{" "}
						<code>filter.endTime.$exists=false</code> to protect
						against jobs ending after candidate search. An exact
						job key must pass the same check; never send a broad
						kind/type filter directly when C7 current-task
						semantics are required. A C7 <code>retries=0</code>
						request creates an incident, while the C8 batch
						changeset only changes the retry count; reject this
						value or route it through a separate per-job
						failure/incident flow.
					</p>
					<p>
						Do not pass either Camunda 7 query object directly as a
						Camunda 8 <code>filter</code>. Reject or mark the
						selector unsupported when it contains a C7 criterion
						that cannot be mapped or post-filtered. Do not omit an
						unsupported criterion from a mutating request because
						that would broaden the set of jobs being updated.
					</p>
					<p>
						Camunda 7 unions <code>externalTaskIds</code>,{" "}
						<code>processInstanceIds</code>, and
						query-selected tasks. Because fields in one Camunda 8{" "}
						<code>filter</code> are conjunctive, issue separate
						batch-update requests for each supported selector and
						remove overlapping job keys before subsequent requests
						to avoid updating a job twice.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(boolean) externalTaskQuery.locked
							<br />
							(boolean) externalTaskQuery.notLocked
							<br />
							(boolean) externalTaskQuery.active
							<br />
							(boolean) externalTaskQuery.suspended
							<br />
							(boolean) externalTaskQuery.withRetriesLeft
							<br />
							(string) externalTaskQuery.executionId
							<br />
							(object) externalTaskQuery.sorting
							<br />
							(string) processInstanceQuery.deploymentId
							<br />
							(string) processInstanceQuery.businessKey
							<br />
							(string) processInstanceQuery.businessKeyLike
							<br />
							(string) processInstanceQuery.caseInstanceId
							<br />
							(string) processInstanceQuery.superProcessInstance
							<br />
							(string) processInstanceQuery.subProcessInstance
							<br />
							(boolean) processInstanceQuery.active
							<br />
							(boolean) processInstanceQuery.suspended
							<br />
							(object) processInstanceQuery.variables
							<br />
							(boolean) processInstanceQuery.withIncident
						</pre>
					),
					rightEntry: (
						<p>
							These Camunda 7 criteria have no equivalent in the
							Camunda 8 active job filter. Do not include them in
							the batch update request; apply any required
							filtering in a separate migration step.
						</p>
					),
				},
				{
					leftEntry: <pre>(object) historicProcessInstanceQuery</pre>,
					rightEntry: (
						<p>
							Historic process instance query fields do not have
							a direct equivalent in the active job batch update
							filter.
						</p>
					),
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/external-task/retries-async",
			operation: "post",
		},
		target: {
			path: "/jobs/batch-update",
			operation: "post",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(int32) retries</pre>,
					rightEntry: <pre>(int32) changeset.retries</pre>,
				},
				{
					leftEntry: <pre>(string[]) externalTaskIds</pre>,
					rightEntry: (
						<p>
							These identifiers are not exposed as a Camunda 8
							job field. Resolve each one through
							migration-specific correlation from the C7
							external-task ID to its corresponding current C8
							job key; a candidate search alone cannot establish
							that relationship. If the correlation is
							unavailable, mark this selector unsupported.
							Deduplicate the correlated keys and do not pass
							the IDs directly as a batch update filter.
						</p>
					),
				},
				{
					leftEntry: <pre>(string[]) processInstanceIds</pre>,
					rightEntry: (
						<p>
							Translate each C7 process-instance ID through
							migration-specific ID-to-key correlation before
							searching. If that correlation is unavailable, mark
							the selector unsupported. Resolve the matching
							current, deduplicated Camunda 8 job keys through
							the candidate-search flow below and do not pass
							C7 process-instance IDs directly as a batch update
							filter.
						</p>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) externalTaskQuery.externalTaskId
							<br />
							(string[]) externalTaskQuery.externalTaskIdIn
							<br />
							(string) externalTaskQuery.topicName
							<br />
							(string) externalTaskQuery.workerId
							<br />
							(string) externalTaskQuery.processInstanceId
							<br />
							(string[]) externalTaskQuery.processInstanceIdIn
							<br />
							(string) externalTaskQuery.activityId
							<br />
							(string[]) externalTaskQuery.activityIdIn
							<br />
							(string[]) externalTaskQuery.tenantIdIn
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(string) filter.jobKey
								<br />
								(string[]) filter.jobKey.$in
								<br />
								(JobKindEnum) filter.kind
								<br />
								(string) filter.type
								<br />
								(string) filter.worker
								<br />
								(string) filter.processInstanceKey
								<br />
								(string[]) filter.processInstanceKey.$in
								<br />
								(string) filter.elementId
								<br />
								(string[]) filter.elementId.$in
								<br />
								(string[]) filter.tenantId.$in
							</pre>
							<p>
								Use the advanced <code>$in</code> operator for
								array-valued criteria and the corresponding
								advanced string operators for C7{" "}
								<code>...Like</code> criteria.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(dateTime) externalTaskQuery.lockExpirationAfter
							<br />
							(dateTime) externalTaskQuery.lockExpirationBefore
							<br />
							(boolean) externalTaskQuery.noRetriesLeft
							<br />
							(int64) externalTaskQuery.priorityHigherThanOrEquals
							<br />
							(int64) externalTaskQuery.priorityLowerThanOrEquals
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(dateTime) filter.deadline.$gt
								<br />
								(dateTime) filter.deadline.$lt
								<br />
								(int32) filter.retries.$eq
								<br />
								(int32) filter.priority.$gte
								<br />
								(int32) filter.priority.$lte
							</pre>
							<p>
								Use the advanced comparison operators shown
								above only when searching candidates. Validate
								each C7 priority bound against the signed
								32-bit C8 range. Jobs created before Camunda
								8.10 may have no stored priority and are
								excluded by priority filters; preserve source
								correlation or mark that selector unsupported.
								For <code>noRetriesLeft</code>, emit{" "}
								<code>filter.retries.$eq=0</code> only when
								the C7 flag is <code>true</code>;{" "}
								<code>false</code> and an omitted flag are
								no-ops and must not add a retry predicate.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(string[]) processInstanceQuery.processInstanceIds
							<br />
							(string) processInstanceQuery.processDefinitionKey
							<br />
							(string[]) processInstanceQuery.processDefinitionKeyIn
							<br />
							(string[]) processInstanceQuery.tenantIdIn
						</pre>
					),
					rightEntry: (
						<>
							<pre>
								(string) filter.processInstanceKey
								<br />
								(string[]) filter.processInstanceKey.$in
								<br />
								(string) filter.processDefinitionId
								<br />
								(string[]) filter.processDefinitionId.$in
								<br />
								(string[]) filter.tenantId.$in
							</pre>
							<p>
								Use these fields only to search candidate
								<code>BPMN_ELEMENT</code> jobs. A C7 process
								definition key maps to the C8 process definition
								ID; a versioned C7 definition ID is not
								interchangeable with it. Do not pass this
								selector directly to the batch update; submit
								only the deduplicated current job keys.
							</p>
						</>
					),
				},
				{
					leftEntry: (
						<pre>
							(string) externalTaskQuery.processDefinitionId
							<br />
							(string) processInstanceQuery.processDefinitionId
						</pre>
					),
					rightEntry: (
						<>
							<pre>(string) filter.processDefinitionKey</pre>
							<p>
								Use this only to search candidates. Translate
								each version-specific Camunda 7 process
								definition ID to the corresponding Camunda 8
								process definition key, then submit only the
								deduplicated current job keys to the batch
								update.
							</p>
						</>
					),
				},
			],
			additionalInfo: (
				<>
					<p>
						The Camunda 8.10 Update jobs (batch) endpoint is
						asynchronous; its batch operation key can be used to
						track progress.
					</p>
					<p>
						For exact <code>jobKey</code> or{" "}
						<code>jobKey.$in</code> selectors, set{" "}
						<code>filter.kind</code> to{" "}
						<code>BPMN_ELEMENT</code>. For a selector with a C7
						topic, also set its corresponding{" "}
						<code>filter.type</code>. A selector without a topic
						that is not constrained by an exact job key requires a
						complete allowlist of all external-task job types and
						one search per type; a single arbitrary type
						under-selects, while <code>filter.kind</code> alone
						over-selects. If that allowlist is unavailable, mark
						the selector unsupported and do not run the batch
						update. An exact job key or correlated
						<code>jobKey.$in</code> selector does not need that
						allowlist.
					</p>
					<p>
						Apply the same current-task restriction to this
						asynchronous mapping: do not submit a broad kind/type
						filter directly. Search each translated selector,
						follow all cursor pages, and use{" "}
						<code>endTime == null</code> as the C8 current-job
						candidate check. C7 and C8 lifecycle and nullable-retry
						semantics are not fully equivalent; preserve source
						correlation for exact exhausted/nullable cases and mark
						the selector unsupported when it is unavailable.
						Deduplicate the resulting job keys and submit the
						update with <code>filter.jobKey.$in</code> and{" "}
						<code>filter.endTime.$exists=false</code>. Check exact
						job keys with the same check before updating. A C7{" "}
						<code>retries=0</code> request creates an incident,
						while the C8 batch changeset only changes the retry
						count; reject this value or route it through a
						separate per-job failure/incident flow.
					</p>
					<p>
						Do not pass either Camunda 7 query object directly as a
						Camunda 8 <code>filter</code>. Reject or mark the
						selector unsupported when it contains a C7 criterion
						that cannot be mapped or post-filtered. Do not omit an
						unsupported criterion from a mutating request because
						that would broaden the set of jobs being updated.
					</p>
					<p>
						Camunda 7 unions <code>externalTaskIds</code>,{" "}
						<code>processInstanceIds</code>, and
						query-selected tasks. Because fields in one Camunda 8{" "}
						<code>filter</code> are conjunctive, issue separate
						batch-update requests for each supported selector and
						remove overlapping job keys before subsequent requests
						to avoid updating a job twice.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: (
						<pre>
							(boolean) externalTaskQuery.locked
							<br />
							(boolean) externalTaskQuery.notLocked
							<br />
							(boolean) externalTaskQuery.active
							<br />
							(boolean) externalTaskQuery.suspended
							<br />
							(boolean) externalTaskQuery.withRetriesLeft
							<br />
							(string) externalTaskQuery.executionId
							<br />
							(object) externalTaskQuery.sorting
							<br />
							(string) processInstanceQuery.deploymentId
							<br />
							(string) processInstanceQuery.businessKey
							<br />
							(string) processInstanceQuery.businessKeyLike
							<br />
							(string) processInstanceQuery.caseInstanceId
							<br />
							(string) processInstanceQuery.superProcessInstance
							<br />
							(string) processInstanceQuery.subProcessInstance
							<br />
							(boolean) processInstanceQuery.active
							<br />
							(boolean) processInstanceQuery.suspended
							<br />
							(object) processInstanceQuery.variables
							<br />
							(boolean) processInstanceQuery.withIncident
						</pre>
					),
					rightEntry: (
						<p>
							These Camunda 7 criteria have no equivalent in the
							Camunda 8 active job filter. Do not include them in
							the batch update request; apply any required
							filtering in a separate migration step.
						</p>
					),
				},
				{
					leftEntry: <pre>(object) historicProcessInstanceQuery</pre>,
					rightEntry: (
						<p>
							Historic process instance query fields do not have
							a direct equivalent in the active job batch update
							filter.
						</p>
					),
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/external-task/topic-names",
			operation: "get",
		},
		target: {
			path: "/jobs/search",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				The Camunda 8.10 Search jobs endpoint can search by the
				<code>type</code> field, but <code>BPMN_ELEMENT</code> includes
				job types that are not C7 external-task topics. There is no
				exact mapping from <code>/external-task/topic-names</code> using
				only this endpoint. Use external-task definition metadata or a
				known C7-topic-to-C8-type allowlist, then search each allowed
				type with <code>filter.kind=BPMN_ELEMENT</code> and collect the
				unique types. Without that allowlist, mark this mapping as
				non-equivalent rather than treating every returned type as a
				topic name. The endpoint is paginated and can return terminal
				jobs, so page through all results and use{" "}
				<code>endTime == null</code> as the C8 current-job candidate
				check. C7 and C8 lifecycle and nullable-retry semantics are not
				fully equivalent; preserve source correlation for exact
				exhausted/nullable cases or mark those cases unsupported.
				C7 exposes <code>withRetriesLeft</code>. When it is{" "}
				<code>true</code>, add{" "}
				<code>filter.retries.$gt=0</code> and include correlated
				source tasks whose C7 retry value is null; if those null
				values cannot be correlated, mark the mapping unsupported.
				When the flag is absent, do not add a retry predicate and
				retain all current candidates. C7 has no{" "}
				<code>noRetriesLeft</code> parameter for this endpoint.
				<code>withLockedTasks</code> and{" "}
				<code>withUnlockedTasks</code> are mutually exclusive; when
				both are true, return an empty result. A false value is a
				no-op. C8 worker and deadline fields are not an exact current
				lock test, so use client-side/source correlation or mark the
				combination unsupported.
			</div>
		),
	},
	{
		origin: {
			path: "/external-task/{id}",
			operation: "get",
		},
		target: {
			path: "/jobs/search",
			operation: "post",
		},
		mappedExplanation: (
			<div>
				In Camunda 8.10, the <code>POST Search jobs</code> endpoint can
				be used to retrieve a specific job by filtering on{" "}
				<code>jobKey</code>. Set <code>filter.kind</code> to{" "}
				<code>BPMN_ELEMENT</code> so listener and ad-hoc-subprocess jobs
				are excluded. Resolve the C7 external-task ID to the
				corresponding C8 job key before making the request.
				After the search, use <code>endTime == null</code> as the C8
				current-job candidate check. C7 exhausted/nullable retry
				semantics require source correlation for exact parity; mark
				that case unsupported when the correlation is unavailable.
				Report a job with a non-null <code>endTime</code> as not found.
			</div>
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: <pre>(string) jobKey</pre>,
				},
				{
					leftEntry: <pre>(string) errorCode</pre>,
					rightEntry: <pre>(string) errorCode</pre>,
				},
				{
					leftEntry: <pre>(string) errorMessage</pre>,
					rightEntry: <pre>(string) errorMessage</pre>,
				},
				{
					leftEntry: <pre>(object) variables</pre>,
					rightEntry: <pre>(object) variables</pre>,
				},
			],
			additionalInfo: (
				<>
					<p>
						The Camunda 8.10 <code>variables</code> are created in
						the catching error event's local scope. Map Camunda 7{" "}
						<code>variables</code> entries by unwrapping each{" "}
						<code>VariableValueDto</code> and sending only its{" "}
						<code>value</code> as the raw JSON value. Do not copy
						the <code>type</code> or <code>valueInfo</code>{" "}
						metadata.
					</p>
					<p>
						When jobs are activated with{" "}
						<code>withLease: true</code>, pass the returned{" "}
						<code>leaseToken</code> in this request.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) workerId</pre>,
					rightEntry: (
						<p>Not required to throw a BPMN error for a job.</p>
					),
				},
			],
			additionalInfo: "",
		},
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: <pre>(string) jobKey</pre>,
				},
				{
					leftEntry: <pre>(object) variables</pre>,
					rightEntry: <pre>(object) variables</pre>,
				},
			],
			additionalInfo: (
				<>
					<p>
						Camunda 7 <code>variables</code> entries are{" "}
						<code>VariableValueDto</code> wrappers. Unwrap each
						entry and send only its <code>value</code> as the raw
						JSON value; do not copy the <code>type</code> or{" "}
						<code>valueInfo</code> metadata.
					</p>
					<p>
						When jobs are activated with{" "}
						<code>withLease: true</code>, pass the returned{" "}
						<code>leaseToken</code> in this request.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(object) localVariables</pre>,
					rightEntry: (
						<p>
							Camunda 7 local variables are not included in the
							Camunda 8.10 completion request. Update them
							separately with a scope-aware variable update.
						</p>
					),
				},
				{
					leftEntry: <pre>(string) workerId</pre>,
					rightEntry: <p>Not required to complete a job.</p>,
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/external-task/{id}/errorDetails",
			operation: "get",
		},
		target: {},
		discontinuedExplanation: (
			<div>
				There is no endpoint in Camunda 8.10 yet to retrieve the error
				details for a specific running job.
			</div>
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: <pre>(string) jobKey</pre>,
				},
				{
					leftEntry: <pre>(int64) newDuration</pre>,
					rightEntry: <pre>(int64) changeset.timeout</pre>,
				},
				{
					leftEntry: <pre>(string) workerId</pre>,
					rightEntry: (
						<>
							<pre>(string) leaseToken</pre>
							<p>
								Use the token returned when activating the job
								with <code>withLease: true</code> to preserve
								worker-ownership fencing.
							</p>
						</>
					),
				},
			],
			additionalInfo: "",
		},
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: <pre>(string) jobKey</pre>,
				},
				{
					leftEntry: <pre>(string) errorMessage</pre>,
					rightEntry: <pre>(string) errorMessage</pre>,
				},
				{
					leftEntry: (
						<pre>
							(int32) retries
							<br />
							(int64) retryTimeout
						</pre>
					),
					rightEntry: (
						<pre>
							(int32) retries
							<br />
							(int64) retryBackOff
						</pre>
					),
				},
				{
					leftEntry: <pre>(object) localVariables</pre>,
					rightEntry: <pre>(object) variables</pre>,
				},
			],
			additionalInfo: (
				<>
					<p>
						Camunda 7 <code>localVariables</code> entries are{" "}
						<code>VariableValueDto</code> wrappers. Unwrap each
						entry and send only its <code>value</code> as the raw
						JSON value in Camunda 8 <code>variables</code>; do not
						copy the <code>type</code> or{" "}
						<code>valueInfo</code> metadata.
					</p>
					<p>
						When jobs are activated with{" "}
						<code>withLease: true</code>, pass the returned{" "}
						<code>leaseToken</code> in this request.
					</p>
				</>
			),
		},
		discontinued: {
			rowInfo: [
				{
					leftEntry: <pre>(string) errorDetails</pre>,
					rightEntry: (
						<p>
							In Camunda 8.10, there is no additional field for
							error details.
						</p>
					),
				},
				{
					leftEntry: <pre>(object) variables</pre>,
					rightEntry: (
						<p>
							Camunda 7 non-local variables are propagated beyond
							the external task. The Camunda 8.10 failure
							endpoint only creates variables in the job task's
							local scope, so update non-local variables
							separately with a scope-aware variable update.
						</p>
					),
				},
				{
					leftEntry: <pre>(string) workerId</pre>,
					rightEntry: (
						<p>Not necessary to report a failure for a job.</p>
					),
				},
			],
			additionalInfo: "",
		},
	},
	{
		origin: {
			path: "/external-task/{id}/lock",
			operation: "post",
		},
		target: {},
		discontinuedExplanation: (
			<p>
				Camunda 8.10 jobs are acquired through the{" "}
				<code>POST /jobs/activation</code> endpoint by job type. The{" "}
				<code>PATCH /jobs/&#123;jobKey&#125;</code> endpoint only updates
				job fields such as the timeout; it does not activate or assign a
				job, so explicit lock-by-ID is not available.
			</p>
		),
	},
	{
		origin: {
			path: "/external-task/{id}/priority",
			operation: "put",
		},
		target: {
			path: "/jobs/{jobKey}",
			operation: "patch",
		},
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: <pre>(string) jobKey</pre>,
				},
				{
					leftEntry: <pre>(int64) priority</pre>,
					rightEntry: <pre>(int32) changeset.priority</pre>,
				},
			],
			additionalInfo: (
				<p>
					Job priority is supported by the Camunda 8.10 Update job
					endpoint. Camunda 7 accepts a signed 64-bit priority, while
					Camunda 8.10 accepts a signed 32-bit value; validate the
					priority is within the signed 32-bit range before sending
					it.
				</p>
			),
		},
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
		direct: {
			rowInfo: [
				{
					leftEntry: <pre>(string) id</pre>,
					rightEntry: <pre>(string) jobKey</pre>,
				},
				{
					leftEntry: <pre>(int32) retries</pre>,
					rightEntry: <pre>(int32) changeset.retries</pre>,
				},
			],
			additionalInfo: (
				<>
					<p>
						In Camunda 8.10, this endpoint cannot be used to raise an
						incident. The provided number of retries has to be a
						positive number.
					</p>
					<p>
						Use the <code>POST Fail job</code> endpoint instead.
						With no remaining retries, an incident will be raised.
					</p>
				</>
			),
		},
	},
	{
		origin: {
			path: "/external-task/{id}/unlock",
			operation: "post",
		},
		target: {},
		discontinuedExplanation: (
			<p>
				There is no Camunda 8.10 endpoint to unlock a job without
				completing it. Completing a job advances the process and is not
				equivalent to unlocking an external task.
			</p>
		),
	},
];
