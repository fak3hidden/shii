package dev.shii.renderdebug.client.config;

/**
 * Controls how occluded (filtered-out) blocks are processed inside {@code SectionCompiler#compile}.
 *
 * <ul>
 *   <li>{@link #INVISIBLE}: Bypasses model/fluid tessellation altogether so zero quads are emitted
 *       into the chunk section's GPU {@code MeshData}.</li>
 *   <li>{@link #TRANSLUCENT}: Reroutes block and fluid geometry into {@code ChunkSectionLayer.TRANSLUCENT}
 *       with custom vertex alpha so surrounding terrain renders as depth-sorted ghost geometry.</li>
 * </ul>
 */
public enum OccludedRenderStyle {
	INVISIBLE,
	TRANSLUCENT;

	public OccludedRenderStyle toggle() {
		return this == INVISIBLE ? TRANSLUCENT : INVISIBLE;
	}
}
