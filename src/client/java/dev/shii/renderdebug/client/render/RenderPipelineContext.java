package dev.shii.renderdebug.client.render;

/**
 * Per-thread rendering pipeline state tracker scoped strictly to {@code SectionCompiler#compile}.
 *
 * <p>Minecraft 26.3 shares {@code BlockState} methods (such as {@code getShadeBrightness}) across
 * server logic, entity AI, particle physics, and chunk-section compilation threads, while also
 * sharing {@code BufferBuilder} across UI, entity, and terrain rendering. Storing chunk-compilation
 * flags in {@link ThreadLocal} primitives guarantees:
 * <ul>
 *   <li>Integrated server gameplay, light propagation, and mob spawning are never altered.</li>
 *   <li>Custom translucent vertex alpha is injected <em>only</em> while tessellating an occluded
 *       block or fluid inside {@code SectionCompiler#compile}.</li>
 * </ul>
 */
public final class RenderPipelineContext {
	private static final ThreadLocal<Boolean> IN_CHUNK_REBUILD = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private static final ThreadLocal<Boolean> CURRENT_BLOCK_TRANSLUCENT = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private RenderPipelineContext() {
	}

	public static void enterChunkRebuild() {
		IN_CHUNK_REBUILD.set(Boolean.TRUE);
		CURRENT_BLOCK_TRANSLUCENT.set(Boolean.FALSE);
	}

	public static void exitChunkRebuild() {
		CURRENT_BLOCK_TRANSLUCENT.set(Boolean.FALSE);
		IN_CHUNK_REBUILD.set(Boolean.FALSE);
	}

	public static boolean isInChunkRebuild() {
		return IN_CHUNK_REBUILD.get();
	}

	public static void setCurrentBlockTranslucent(boolean translucent) {
		CURRENT_BLOCK_TRANSLUCENT.set(translucent ? Boolean.TRUE : Boolean.FALSE);
	}

	public static boolean isCurrentBlockTranslucent() {
		return IN_CHUNK_REBUILD.get() && CURRENT_BLOCK_TRANSLUCENT.get();
	}
}
