import { useState, useRef, useEffect } from "react";
import { mappingIndex } from "./mappings/mappingIndex";
import { Filter } from "./components/filter";
import { Tabs } from "./components/Tabs";
import { MappingTable } from "./components/MappingTable";
import { createMappedC7Endpoints } from "./utils/internalMappingUtils";
import "./App.css";

function App() {
	const [selectedMapping, setSelectedMapping] = useState(mappingIndex[0]);
	const [selectedMethod, setSelectedMethod] = useState("all");
	const [searchText, setSearchText] = useState("");
	const [hideTBDEndpoints, setHideTBDEndpoints] = useState(false);
	const sectionRefs = useRef([]);
	const refScrollUp = useRef();
	const [scrollPosition, setScrollPosition] = useState(0);

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
		setSelectedMapping(mappingIndex.find((mapping) => mapping.id === id));
	}

	const mappedC7Endpoints = createMappedC7Endpoints(
		selectedMapping,
		selectedMethod,
		searchText,
		hideTBDEndpoints
	);

	return (
		<>
			<div className="container">
				<Tabs
					reducedMappingIndex={mappingIndex.map(({ id, tabName }) => {
						return { id, tabName };
					})}
					handleSelectionClick={handleSelectionClick}
				/>
				<section className="introduction">
					{selectedMapping.introduction}
				</section>
				<div ref={refScrollUp}></div>
				<h1>Mappings</h1>
				<Filter
					selectedMethod={selectedMethod}
					setSelectedMethod={setSelectedMethod}
					searchText={searchText}
					setSearchText={setSearchText}
					hideTBDEndpoints={hideTBDEndpoints}
					setHideTBDEndpoints={setHideTBDEndpoints}
					displayedSections={mappedC7Endpoints.map(
						(section) => section.section
					)}
					scrollToSection={scrollToSection}
				/>
				<section className="tables">
					{mappedC7Endpoints.map((endpoint, index) => {
						return (
							<div key={index}>
								<h2
									ref={(el) =>
										(sectionRefs.current[index] = el)
									}
								>
									{endpoint.section}
								</h2>
								<MappingTable endpoint={endpoint} />
							</div>
						);
					})}
				</section>
			</div>
			<div
				className="button-to-top"
				style={{ display: scrollPosition > 2000 ? "block" : "none" }}
			>
				<button onClick={() => scrollToRef(refScrollUp.current)}>
					Back To Top!
				</button>
			</div>
		</>
	);
}

export default App;
