import styles from "./endpointInfo.module.css";

export function EndpointInfo({ endpointInfo }) {
	return (
		<>
			<h3>{endpointInfo.details?.summary}</h3>
			<div>
				{endpointInfo.operation?.toUpperCase() +
					" " +
					endpointInfo.path}
			</div>
			<a href={endpointInfo.url} target="_blank">
				Link to docs
			</a>
			<br />
			<br />
			<div className={styles.description}>
				{endpointInfo.details?.description}
			</div>
		</>
	);
}
