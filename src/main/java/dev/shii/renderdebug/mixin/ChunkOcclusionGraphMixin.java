package dev.shii.renderdebug.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shii.renderdebug.config.RenderDebugManager;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.util.math.BlockPos;

/**
 * Pipeline Stage 1: Cave & Chunk Visibility Graph Occlusion Override.
 *
 * <p>During chunk section baking, Minecraft feeds every {@code isOpaqueFullCube()}
 * block into {@link ChunkOcclusionDataBuilder#markClosed(BlockPos)} to compute
 * 6-face-to-6-face flood-fill connectivity ({@code ChunkOcclusionData}). During
 * frame rendering, {@code WorldRenderer} traverses chunks via BFS and skips entire
 * underground 16x16x16 sections sealed off by opaque stone/deepslate.
 *
 * <p>Even if individual stone blocks are skipped or made translucent, any underground
 * ore or structure completely encased in stone would still be culled at the chunk-section
 * graph level unless {@code markClosed} is bypassed while the visibility filter is active.
 */
@Mixin(ChunkOcclusionDataBuilder.class)
public abstract class ChunkOcclusionGraphMixin {

	@Inject(method = "markClosed", at = @At("HEAD"), cancellable = true)
	private void renderdebug$preventSectionGraphOcclusion(BlockPos pos, CallbackInfo ci) {
		if (RenderDebugManager.getInstance().isFilterEnabled()) {
			ci.cancel();
		}
	}
}
