/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0.
 */
export async function loadOpenApiSpec(specUrl, exportName) {
	const specModule = await import(/* @vite-ignore */ specUrl);
	const specification = specModule[exportName];

	if (!specification) {
		throw new Error(`OpenAPI module does not export "${exportName}".`);
	}

	return specification;
}
