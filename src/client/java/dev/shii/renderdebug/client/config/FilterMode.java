package dev.shii.renderdebug.client.config;

/**
 * Global filtering policy applied by {@link RenderDebugManager} when the visibility filter is active.
 *
 * <ul>
 *   <li>{@link #WHITELIST}: Only {@link BlockFilterStatus#WHITELISTED} blocks render normally;
 *       all other non-air solid/fluid blocks are occluded (invisible or translucent).</li>
 *   <li>{@link #BLACKLIST}: Only {@link BlockFilterStatus#BLACKLISTED} blocks are occluded;
 *       all other blocks continue rendering normally.</li>
 * </ul>
 */
public enum FilterMode {
	WHITELIST,
	BLACKLIST;

	public FilterMode toggle() {
		return this == WHITELIST ? BLACKLIST : WHITELIST;
	}
}
