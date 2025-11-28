import { Tabs } from "./Tabs";
import styles from "./endpointnavigation.module.css";

export function EndpointNavigation({
	reducedMappingIndex,
	handleSelectionClick,
	displayedSections,
	scrollToSection,
	scrollPosition,
}) {
	return (
		<section className={styles.navigationContainer}>
			<h2>Navigation</h2>
			<div className={styles.navigations}>
				<label>
					<span>Jump to section:</span>
					<select
						key={scrollPosition}
						className={styles.navigateBySection}
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
				<label>
					<span>Select Mapping:</span>
					<Tabs
						reducedMappingIndex={reducedMappingIndex}
						handleSelectionClick={handleSelectionClick}
					/>
				</label>
			</div>
		</section>
	);
}
