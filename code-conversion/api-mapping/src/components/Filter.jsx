export function Filter({
	selectedMethod,
	setSelectedMethod,
	searchText,
	setSearchText,
	hideTBDEndpoints,
	setHideTBDEndpoints,
	displayedSections,
	scrollToSection,
}) {
	return (
		<section className="filter-container">
			<h2>Filters</h2>
			<div className="filters">
				<div className="stable-filters">
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
							onChange={(e) => scrollToSection(e.target.value)}
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
