package dev.shii.renderdebug.render;

/**
 * Per-thread state tracker scoped strictly to client-side chunk mesh construction
 * ({@code SectionBuilder#build}).
 *
 * <p>Why {@link ThreadLocal} is essential here:
 * <ol>
 *   <li>Minecraft builds chunk sections asynchronously across a pool of background
 *       chunk-builder worker threads. Each thread tessellates one 16x16x16 section
 *       sequentially block-by-block.</li>
 *   <li>Methods on {@code AbstractBlockState} (such as {@code getRenderType()} and
 *       {@code getLuminance()}) are shared between the client renderer AND the
 *       integrated server thread in Singleplayer. Scoping our overrides to
 *       {@link #isInChunkRebuild()} guarantees zero side effects on server lighting,
 *       mob spawning, ice melting, raycasting, or block-breaking particles.</li>
 *   <li>Tracking {@link #isCurrentBlockTranslucent()} per worker thread allows
 *       {@code BufferBuilderAlphaMixin} to rewrite vertex alpha exclusively for
 *       filtered blocks routed to {@code RenderLayer.getTranslucent()}, working
 *       identically under both Vanilla {@code BlockModelRenderer} and Fabric API's
 *       Indigo renderer ({@code TerrainRenderContext}).</li>
 * </ol>
 */
public final class RenderPipelineContext {
	private static final ThreadLocal<Boolean> IN_CHUNK_REBUILD = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private static final ThreadLocal<Boolean> CURRENT_BLOCK_TRANSLUCENT = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private RenderPipelineContext() {
	}

	public static void beginChunkRebuild() {
		IN_CHUNK_REBUILD.set(Boolean.TRUE);
		CURRENT_BLOCK_TRANSLUCENT.set(Boolean.FALSE);
	}

	public static void endChunkRebuild() {
		IN_CHUNK_REBUILD.remove();
		CURRENT_BLOCK_TRANSLUCENT.remove();
	}

	public static boolean isInChunkRebuild() {
		return IN_CHUNK_REBUILD.get();
	}

	public static void setCurrentBlockTranslucent(boolean translucent) {
		CURRENT_BLOCK_TRANSLUCENT.set(translucent);
	}

	public static boolean isCurrentBlockTranslucent() {
		return IN_CHUNK_REBUILD.get() && CURRENT_BLOCK_TRANSLUCENT.get();
	}
}
