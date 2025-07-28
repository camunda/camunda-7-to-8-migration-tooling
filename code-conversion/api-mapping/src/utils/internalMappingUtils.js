function createC7DocLink(selectedMapping, section, operationId) {
	return (
		selectedMapping.c7BaseUrl +
		section.replaceAll(" ", "-") +
		"/operation/" +
		operationId
	);
}

function createC8DocLink(selectedMapping, operationId) {
	return (
		selectedMapping.c8BaseUrl +
		operationId
			?.match(/([A-Z]+(?=[A-Z][a-z])|[A-Z]?[a-z]+|[A-Z]+)/g)
			.map((word) => word.toLowerCase())
			.join("-")
	);
}

function findMappingInfo(selectedMapping, path, operation) {
	return selectedMapping.mappings.find((mapping) => {
		return (
			mapping.origin.path == path && mapping.origin.operation == operation
		);
	});
}

function createC8Info(selectedMapping, mappingPath, mappingOperation) {
	return Object.entries(selectedMapping.c8_specification.paths)
		.flatMap(([path, operations]) => {
			return mappingPath == path
				? Object.entries(operations).flatMap(([operation, details]) => {
						return mappingOperation == operation
							? {
									path: path,
									operation: operation,
									url: createC8DocLink(
										selectedMapping,
										details?.operationId
									),
									details,
							  }
							: [];
				  })
				: [];
		})
		.find((x) => x !== undefined);
}

export function createMappedC7Endpoints(
	selectedMapping,
	selectedMethod,
	searchText,
	hideTBDEndpoints,
	showMappedEndpoints,
	showRoadmapEndpoints,
	showDiscontinuedEndpoints,
	sortAlphabetically
) {
	const sections = selectedMapping.c7_specification.tags.map(
		(tag) => tag.name
	);

	return sections
		.filter((section) =>
			Object.entries(selectedMapping.c7_specification.paths).some(
				([path, operations]) =>
					Object.entries(operations).some(
						([operation, operationValue]) =>
							operationValue.tags.includes(section) &&
							(selectedMethod == "all" ||
								operation == selectedMethod) &&
							(searchText == "" || path.includes(searchText))
					)
			)
		)
		.map((section) => {
			return {
				section,
				endpoints: Object.entries(
					selectedMapping.c7_specification.paths
				)
					.filter(([path, operations]) =>
						Object.entries(operations).some(
							([operation, operationValue]) =>
								operationValue.tags.includes(section)
						)
					)
					.map(([path, operations]) =>
						Object.entries(operations)
							.filter(
								([operation, details]) =>
									(selectedMethod == "all" ||
										operation == selectedMethod) &&
									(searchText == "" ||
										path.includes(searchText))
							)
							.map(([operation, details]) => {
								const mappingInfo = findMappingInfo(
									selectedMapping,
									path,
									operation
								);
								return {
									c7Info: {
										path: path,
										operation: operation,
										url: createC7DocLink(
											selectedMapping,
											section,
											details.operationId
										),
										details,
									},
									c8Info: createC8Info(
										selectedMapping,
										mappingInfo?.target?.path,
										mappingInfo?.target?.operation
									),
									direct: mappingInfo?.direct,
									conceptual: mappingInfo?.conceptual,
									discontinued: mappingInfo?.discontinued,
									mappedExplanation:
										mappingInfo?.mappedExplanation,
									roadmapExplanation:
										mappingInfo?.roadmapExplanation,
									discontinuedExplanation:
										mappingInfo?.discontinuedExplanation,
								};
							})
							.filter(
								(endpoint) =>
									!hideTBDEndpoints ||
									endpoint?.direct !== undefined ||
									endpoint?.conceptual !== undefined ||
									endpoint?.discontinued !== undefined ||
									endpoint?.mappedExplanation !== undefined ||
									endpoint?.roadmapExplanation !==
										undefined ||
									endpoint?.discontinuedExplanation !==
										undefined
							)
							.filter(
								(endpoint) =>
									(!showMappedEndpoints ||
										endpoint?.direct !== undefined ||
										endpoint?.conceptual !== undefined ||
										endpoint?.discontinued !== undefined ||
										endpoint?.mappedExplanation !==
											undefined) &&
									(!showRoadmapEndpoints ||
										endpoint?.roadmapExplanation !==
											undefined) &&
									(!showDiscontinuedEndpoints ||
										endpoint?.discontinuedExplanation !==
											undefined)
							)
					)
					.flat()
					.sort((a, b) => {
						if (!sortAlphabetically) {
							return 0;
						}
						if (
							a?.c7Info?.details?.summary <
							b?.c7Info?.details?.summary
						) {
							return -1;
						} else if (
							a?.c7Info?.details?.summary >
							b?.c7Info?.details?.summary
						) {
							return 1;
						} else if (a?.c7Info?.path.includes("tenant-id")) {
							return 1;
						} else if (b?.c7Info?.path.includes("tenant-id")) {
							return -1;
						} else {
							return 0;
						}
					}),
			};
		})
		.filter(
			(section) =>
				!hideTBDEndpoints ||
				section.endpoints.some(
					(endpoint) =>
						endpoint?.direct !== undefined ||
						endpoint?.conceptual !== undefined ||
						endpoint?.discontinued !== undefined ||
						endpoint?.mappedExplanation !== undefined ||
						endpoint?.roadmapExplanation !== undefined ||
						endpoint?.discontinuedExplanation !== undefined
				)
		);
}
