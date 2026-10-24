package dev.shii.renderdebug.client.config;

/**
 * Per-block visibility rule assigned in the Render Pipeline Debugger UI.
 *
 * <ul>
 *   <li>{@link #DEFAULT}: Inherits visibility from the active {@link FilterMode}.</li>
 *   <li>{@link #WHITELISTED}: Explicitly kept fully visible when {@link FilterMode#WHITELIST} is active.</li>
 *   <li>{@link #BLACKLISTED}: Explicitly occluded (hidden or translucent) when {@link FilterMode#BLACKLIST} is active.</li>
 * </ul>
 */
public enum BlockFilterStatus {
	DEFAULT,
	WHITELISTED,
	BLACKLISTED;

	/**
	 * Cycles through {@code DEFAULT -> WHITELISTED -> BLACKLISTED -> DEFAULT} on each UI button click.
	 */
	public BlockFilterStatus next() {
		return switch (this) {
			case DEFAULT -> WHITELISTED;
			case WHITELISTED -> BLACKLISTED;
			case BLACKLISTED -> DEFAULT;
		};
	}
}
