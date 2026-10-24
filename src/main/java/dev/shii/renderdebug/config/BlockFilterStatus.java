package dev.shii.renderdebug.config;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Per-block visibility classification managed by {@link RenderDebugManager}.
 */
public enum BlockFilterStatus {
	DEFAULT("Default", Formatting.GRAY, 0xFF555555),
	WHITELISTED("Whitelisted", Formatting.GREEN, 0xFF2ECC71),
	BLACKLISTED("Blacklisted", Formatting.RED, 0xFFE74C3C);

	private final String displayName;
	private final Formatting colorFormat;
	private final int accentColorArgb;

	BlockFilterStatus(String displayName, Formatting colorFormat, int accentColorArgb) {
		this.displayName = displayName;
		this.colorFormat = colorFormat;
		this.accentColorArgb = accentColorArgb;
	}

	public BlockFilterStatus next() {
		return switch (this) {
			case DEFAULT -> WHITELISTED;
			case WHITELISTED -> BLACKLISTED;
			case BLACKLISTED -> DEFAULT;
		};
	}

	public Text toBadgeText() {
		return Text.literal("[" + this.displayName + "]").formatted(this.colorFormat);
	}

	public int getAccentColorArgb() {
		return this.accentColorArgb;
	}
}
