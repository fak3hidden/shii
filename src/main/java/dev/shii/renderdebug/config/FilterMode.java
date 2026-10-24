package dev.shii.renderdebug.config;

/**
 * Determines how {@link BlockFilterStatus} rules are evaluated when the
 * Block Visibility Filter is active.
 */
public enum FilterMode {
	/**
	 * Hides (or makes translucent) blocks explicitly marked {@link BlockFilterStatus#BLACKLISTED},
	 * while keeping {@link BlockFilterStatus#WHITELISTED} and {@link BlockFilterStatus#DEFAULT}
	 * blocks fully visible. Ideal for stripping away stone/dirt/deepslate terrain.
	 */
	BLACKLIST_HIDE("Blacklist Hide"),

	/**
	 * Keeps ONLY {@link BlockFilterStatus#WHITELISTED} blocks fully visible; all other blocks
	 * bypass opaque rendering. Ideal for isolating specific target blocks such as ores.
	 */
	WHITELIST_ONLY("Whitelist Only");

	private final String label;

	FilterMode(String label) {
		this.label = label;
	}

	public String getLabel() {
		return this.label;
	}

	public FilterMode toggle() {
		return this == BLACKLIST_HIDE ? WHITELIST_ONLY : BLACKLIST_HIDE;
	}
}
