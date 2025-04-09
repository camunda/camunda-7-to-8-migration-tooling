import { useState, useRef } from "react";
import "./App.css";
import { mappingIndex } from "./mappings/mappingIndex";

function App() {
	const [selectedMapping, setSelectedMapping] = useState(mappingIndex[0]);
	const [selectedMethod, setSelectedMethod] = useState("all");
	const [searchText, setSearchText] = useState("");
	const sectionRefs = useRef([]);

	function scrollToSection(elementRef) {
		console.log(elementRef);
		window.scrollTo({
			top: elementRef.offsetTop,
			behavior: "smooth",
		});
	}

	function handleSelectionClick(id) {
		setSelectedMapping(mappingIndex.find((mapping) => mapping.id === id));
	}

	function createC7DocLink(section, operationId) {
		return (
			selectedMapping.c7BaseUrl +
			section.replaceAll(" ", "-") +
			"/operation/" +
			operationId
		);
	}

	function createC8DocLink(operationId) {
		return (
			selectedMapping.c8BaseUrl +
			operationId
				?.split(/(?=[A-Z])/)
				.map((word) => word.charAt(0).toLowerCase() + word.slice(1))
				.join("-")
		);
	}

	function findMappingInfo(path, operation) {
		return selectedMapping.mappings.find((mapping) => {
			return (
				mapping.origin.path == path &&
				mapping.origin.operation == operation
			);
		});
	}

	function createC8Info(mappingPath, mappingOperation) {
		return Object.entries(selectedMapping.c8_specification.paths)
			.flatMap(([path, operations]) => {
				return mappingPath == path
					? Object.entries(operations).flatMap(
							([operation, details]) => {
								return mappingOperation == operation
									? {
											path: path,
											operation: operation,
											url: createC8DocLink(
												details?.operationId
											),
									  }
									: [];
							}
					  )
					: [];
			})
			.find((x) => x !== undefined);
	}

	const sections = selectedMapping.c7_specification.tags.map(
		(tag) => tag.name
	);

	const mappedC7Endpoints = sections
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
									path,
									operation
								);
								return {
									purpose: mappingInfo?.purpose,
									c7Info: {
										path: path,
										operation: operation,
										url: createC7DocLink(
											section,
											details.operationId
										),
									},
									c8Info: createC8Info(
										mappingInfo?.target?.path,
										mappingInfo?.target?.operation
									),
									explanation: mappingInfo?.explanation,
								};
							})
					)
					.flat(),
			};
		});

	return (
		<div className="container">
			<section className="tabs">
				{mappingIndex.map((mapping) => {
					return (
						<button
							key={mapping.id}
							onClick={() => handleSelectionClick(mapping.id)}
						>
							{mapping.tabName}
						</button>
					);
				})}
			</section>
			<section className="filterContainer">
				<h1>Filters</h1>
				<div className="filters">
					<label>
						Filter by C7 endpoint method:{" "}
						<select
							value={selectedMethod}
							onChange={(e) => setSelectedMethod(e.target.value)}
						>
							<option value="all">All</option>
							<option value="get">GET</option>
							<option value="post">POST</option>
							<option value="put">PUT</option>
							<option value="delete">DELETE</option>
							<option value="options">OPTIONS</option>
						</select>
					</label>
					<label>
						Filter C7 endpoint paths by text:{" "}
						<input
							value={searchText}
							onChange={(e) => setSearchText(e.target.value)}
						></input>
					</label>
					<label>
						Jump to section:{" "}
						<select
							onChange={(e) =>
								scrollToSection(
									sectionRefs.current[e.target.value]
								)
							}
						>
							{sections.map((section, index) => {
								return (
									<option key={index} value={index}>
										{section}
									</option>
								);
							})}
						</select>
					</label>
				</div>
			</section>
			<section className="tables">
				<h1>Mappings</h1>
				{mappedC7Endpoints.map((endpoint, index) => {
					return (
						<div key={index}>
							<h2 ref={(el) => (sectionRefs.current[index] = el)}>
								{endpoint.section}
							</h2>
							<table>
								<thead>
									<tr>
										<th className="purpose-column">
											Purpose
										</th>
										<th className="c7-endpoint-column">
											C7 Endpoint
										</th>
										<th className="c8-endpoint-column">
											C8 Endpoint
										</th>
										<th className="explanation-column">
											Explanation
										</th>
									</tr>
								</thead>
								<tbody>
									{endpoint.endpoints.map((endpoint) => {
										return (
											<tr
												key={
													endpoint.c7Info.path +
													endpoint.c7Info.operation
												}
											>
												<td>
													{endpoint.purpose ||
														"to be defined"}
												</td>
												<td>
													<div>
														{endpoint.c7Info.operation.toUpperCase() +
															" " +
															endpoint.c7Info
																.path}
													</div>
													<a
														href={
															endpoint.c7Info.url
														}
														target="_blank"
													>
														Link to docs
													</a>
												</td>
												<td>
													{endpoint.c8Info ? (
														<div>
															<div>
																{endpoint.c8Info.operation?.toUpperCase() +
																	" " +
																	endpoint
																		.c8Info
																		.path}
															</div>
															<a
																href={
																	endpoint
																		.c8Info
																		?.url
																}
																target="_blank"
															>
																Link to docs
															</a>
														</div>
													) : (
														<div>to be defined</div>
													)}
												</td>
												<td>
													{endpoint.explanation ||
														"to be defined"}
												</td>
											</tr>
										);
									})}
								</tbody>
							</table>
						</div>
					);
				})}
			</section>
		</div>
	);
}

export default App;
