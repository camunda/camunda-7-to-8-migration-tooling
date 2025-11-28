import styles from "./toTopButton.module.css";

export function ToTopButton({ scrollPosition, scrollToTop }) {
	return (
		<div
			className={
				scrollPosition > 2000 ? styles.buttonToTop : styles.buttonHidden
			}
		>
			<button onClick={() => scrollToTop()}>Back To Top!</button>
		</div>
	);
}
