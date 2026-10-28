/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useState, useRef, useEffect } from "react";
import { mappingIndex } from "./mappings/mappingIndex";
import { EndpointFilter } from "./components/EndpointFilter";
import { Tables } from "./components/Tables";
import { ToTopButton } from "./components/ToTopButton";
import { createMappedC7Endpoints } from "./utils/internalMappingUtils";
import styles from "./app.module.css";
import { EndpointNavigation } from "./components/EndpointNavigation";

function App() {
	const [selectedMappingId, setSelectedMappingId] = useState(
		mappingIndex[0].id
	);
	const [mappingLoad, setMappingLoad] = useState({
		id: mappingIndex[0].id,
		status: "loading",
	});
	const selectedMapping =
		mappingLoad.id === selectedMappingId &&
		mappingLoad.status === "loaded"
			? mappingLoad.mapping
			: null;
	const mappingLoadError =
		mappingLoad.id === selectedMappingId &&
		mappingLoad.status === "error";
	const [selectedMethod, setSelectedMethod] = useState("all");
	const [searchText, setSearchText] = useState("");
	const [hideTBDEndpoints, setHideTBDEndpoints] = useState(true);
	const [showMappedEndpoints, setShowMappedEndpoints] = useState(false);
	const [showRoadmapEndpoints, setShowRoadmapEndpoints] = useState(false);
	const [showDiscontinuedEndpoints, setShowDiscontinuedEndpoints] =
		useState(false);
	const [sortAlphabetically, setSortAlphabetically] = useState(false);
	const [scrollPosition, setScrollPosition] = useState(0);
	const sectionRefs = useRef([]);
	const refScrollUp = useRef();

	useEffect(() => {
		let isActive = true;
		Promise.resolve()
			.then(() => {
				const mappingEntry = mappingIndex.find(
					(mapping) => mapping.id === selectedMappingId
				);
				if (!mappingEntry) {
					throw new Error(`Unknown API mapping id: ${selectedMappingId}`);
				}
				return mappingEntry.load();
			})
			.then((mapping) => {
				if (isActive) {
					setMappingLoad({
						id: selectedMappingId,
						status: "loaded",
						mapping,
					});
				}
			})
			.catch((error) => {
				if (isActive) {
					console.error("Unable to load API mapping:", error);
					setMappingLoad({ id: selectedMappingId, status: "error" });
				}
			});

		return () => {
			isActive = false;
		};
	}, [selectedMappingId]);

	function scrollToTop() {
		scrollToRef(refScrollUp.current);
	}

	function scrollToSection(sectionIndex) {
		const elementRef = sectionRefs.current[sectionIndex];
		scrollToRef(elementRef);
	}

	function scrollToRef(elementRef) {
		window.scrollTo({
			top: elementRef.offsetTop,
			behavior: "smooth",
		});
	}

	function updateScrollPosition() {
		setScrollPosition(window.pageYOffset);
	}

	useEffect(() => {
		window.addEventListener("scroll", updateScrollPosition);
	});

	function handleSelectionClick(id) {
		setSelectedMappingId(id);
	}

	const mappedC7Endpoints = selectedMapping
		? createMappedC7Endpoints(
				selectedMapping,
				selectedMethod,
				searchText,
				hideTBDEndpoints,
				showMappedEndpoints,
				showRoadmapEndpoints,
				showDiscontinuedEndpoints,
				sortAlphabetically
			)
		: [];
	return (
		<>
			<div className={styles.container}>
				<details className={styles.introduction}>
					<summary className={styles.summary}>Introduction</summary>
					{selectedMapping?.introduction}
				</details>
				<div ref={refScrollUp}></div>
				<h1>Mappings</h1>
				<EndpointNavigation
					reducedMappingIndex={mappingIndex.map(({ id, tabName }) => {
						return { id, tabName };
					})}
					handleSelectionClick={handleSelectionClick}
					displayedSections={mappedC7Endpoints.map(
						(section) => section.section
					)}
					scrollToSection={scrollToSection}
					scrollPosition={scrollPosition}
				/>
				{mappingLoadError ? (
					<p role="alert">
						Unable to load the selected API mapping. Reload the page
						to try again.
					</p>
				) : !selectedMapping ? (
					<p role="status">Loading API mapping…</p>
				) : (
					<>
						<EndpointFilter
							selectedMethod={selectedMethod}
							setSelectedMethod={setSelectedMethod}
							searchText={searchText}
							setSearchText={setSearchText}
							hideTBDEndpoints={hideTBDEndpoints}
							setHideTBDEndpoints={setHideTBDEndpoints}
							sortAlphabetically={sortAlphabetically}
							setSortAlphabetically={setSortAlphabetically}
							showMappedEndpoints={showMappedEndpoints}
							setShowMappedEndpoints={setShowMappedEndpoints}
							showRoadmapEndpoints={showRoadmapEndpoints}
							setShowRoadmapEndpoints={setShowRoadmapEndpoints}
							showDiscontinuedEndpoints={
								showDiscontinuedEndpoints
							}
							setShowDiscontinuedEndpoints={
								setShowDiscontinuedEndpoints
							}
						/>
						<Tables
							sectionRefs={sectionRefs}
							mappedC7Endpoints={mappedC7Endpoints}
						/>
					</>
				)}
			</div>
			<ToTopButton
				scrollPosition={scrollPosition}
				scrollToTop={scrollToTop}
			/>
		</>
	);
}

export default App;
