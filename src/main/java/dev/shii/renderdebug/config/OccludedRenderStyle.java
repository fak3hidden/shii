package dev.shii.renderdebug.config;

/**
 * Controls how filtered/occluded blocks are handled during chunk mesh tessellation.
 */
public enum OccludedRenderStyle {
	/**
	 * Filtered blocks return {@code BlockRenderType.INVISIBLE} during chunk meshing
	 * and emit zero vertices into any chunk {@code BufferBuilder}.
	 */
	INVISIBLE("Invisible (Skip Mesh)"),

	/**
	 * Filtered blocks are redirected from {@code SOLID}/{@code CUTOUT} to
	 * {@code RenderLayer.getTranslucent()} with overridden vertex alpha, rendering
	 * a semi-transparent shell around caves and terrain surfaces.
	 */
	TRANSLUCENT("Translucent (Alpha Pass)");

	private final String label;

	OccludedRenderStyle(String label) {
		this.label = label;
	}

	public String getLabel() {
		return this.label;
	}

	public OccludedRenderStyle toggle() {
		return this == INVISIBLE ? TRANSLUCENT : INVISIBLE;
	}
}
