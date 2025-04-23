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
	displayedSections,
	scrollToSection,
	scrollPosition,
}) {
	return (
		<section className={styles.filterContainer}>
			<h2>Filters</h2>
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
						Sort alphabetically:{" "}
						<input
							type="checkbox"
							checked={sortAlphabetically}
							onChange={() =>
								setSortAlphabetically(!sortAlphabetically)
							}
						/>
					</label>
					<label>
						Jump to section:{" "}
						<select
							key={scrollPosition}
							className={styles.filterBySection}
							onChange={(e) => scrollToSection(e.target.value)}
							defaultValue={displayedSections[0]}
						>
							{displayedSections.map((section, index) => {
								return (
									<option key={index} value={index}>
										{section}
									</option>
								);
							})}
						</select>
					</label>
				</div>
				<div>
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
