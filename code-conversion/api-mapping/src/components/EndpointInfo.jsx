export function EndpointInfo({ endpointInfo }) {
	return (
		<>
			<div>
				<strong>{endpointInfo.details?.summary}</strong>
			</div>
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
			<div>{endpointInfo.details?.description}</div>
		</>
	);
}
