import Markdown from "react-markdown";

export function EndpointInfo({ endpointInfo }) {
	return (
		<>
			<h3>{endpointInfo.details?.summary}</h3>
			<code>
				{endpointInfo.operation?.toUpperCase() +
					" " +
					endpointInfo.path}
			</code>
			<br />
			<a href={endpointInfo.url} target="_blank">
				Link to docs
			</a>
			<br />
			<Markdown>{endpointInfo.details?.description}</Markdown>
		</>
	);
}
