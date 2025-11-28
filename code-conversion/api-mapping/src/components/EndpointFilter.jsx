import styles from "./endpointfilter.module.css";

export function EndpointFilter({
	selectedMethod,
	setSelectedMethod,
	searchText,
	setSearchText,
	hideTBDEndpoints,
	setHideTBDEndpoints,
	sortAlphabetically,
	setSortAlphabetically,
	showMappedEndpoints,
	setShowMappedEndpoints,
	showRoadmapEndpoints,
	setShowRoadmapEndpoints,
	showDiscontinuedEndpoints,
	setShowDiscontinuedEndpoints,
}) {
	return (
		<section className={styles.filterContainer}>
			<h2>Filters and Sorting</h2>
			<div className={styles.filters}>
				<div className={styles.stableFilters}>
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
							className={styles.filterByText}
							value={searchText}
							onChange={(e) => setSearchText(e.target.value)}
						></input>
					</label>
					<label>
						Sort paths alphabetically:{" "}
						<input
							type="checkbox"
							checked={sortAlphabetically}
							onChange={() =>
								setSortAlphabetically(!sortAlphabetically)
							}
						/>
					</label>
				</div>
				<div className={styles.checkboxFilters}>
					<label>
						Show only mapped endpoints:{" "}
						<input
							type="checkbox"
							checked={showMappedEndpoints}
							onChange={() => {
								setShowMappedEndpoints(!showMappedEndpoints);
								setShowRoadmapEndpoints(false);
								setShowDiscontinuedEndpoints(false);
								setHideTBDEndpoints(true);
							}}
						/>
					</label>
					<label>
						Show only on roadmap endpoints :{" "}
						<input
							type="checkbox"
							checked={showRoadmapEndpoints}
							onChange={() => {
								setShowMappedEndpoints(false);
								setShowRoadmapEndpoints(!showRoadmapEndpoints);
								setShowDiscontinuedEndpoints(false);
								setHideTBDEndpoints(true);
							}}
						/>
					</label>
					<label>
						Show only discontinued endpoints :{" "}
						<input
							type="checkbox"
							checked={showDiscontinuedEndpoints}
							onChange={() => {
								setShowMappedEndpoints(false);
								setShowRoadmapEndpoints(false);
								setShowDiscontinuedEndpoints(
									!showDiscontinuedEndpoints
								);
								setHideTBDEndpoints(true);
							}}
						/>
					</label>
					<label>
						Hide TBD endpoints and sections:{" "}
						<input
							type="checkbox"
							checked={hideTBDEndpoints}
							onChange={() =>
								setHideTBDEndpoints(!hideTBDEndpoints)
							}
						/>
					</label>
				</div>
			</div>
		</section>
	);
}
