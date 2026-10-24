package dev.shii.renderdebug.mixin;

import com.mojang.blaze3d.systems.VertexSorter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.shii.renderdebug.render.RenderPipelineContext;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.util.math.ChunkSectionPos;

/**
 * Pipeline Stage 0: Chunk Section Rebuild Scope Tracker.
 *
 * <p>{@link SectionBuilder#build} runs on client background chunk-builder worker
 * threads to bake each 16x16x16 chunk section into GPU vertex buffers per
 * {@code RenderLayer}. Marking the thread's {@link RenderPipelineContext} active
 * at {@code HEAD} and clearing it at {@code RETURN} isolates our {@code BlockState}
 * and {@code BufferBuilder} overrides strictly to chunk mesh tessellation—preventing
 * client-side visual overrides from leaking into integrated-server game logic in
 * Singleplayer.
 */
@Mixin(SectionBuilder.class)
public abstract class SectionBuilderMixin {

	@Inject(method = "build", at = @At("HEAD"))
	private void renderdebug$onBeginChunkSectionBuild(
			ChunkSectionPos sectionPos,
			ChunkRendererRegion renderRegion,
			VertexSorter vertexSorter,
			BlockBufferAllocatorStorage allocatorStorage,
			CallbackInfoReturnable<SectionBuilder.RenderData> cir
	) {
		RenderPipelineContext.beginChunkRebuild();
	}

	@Inject(method = "build", at = @At("RETURN"))
	private void renderdebug$onEndChunkSectionBuild(
			ChunkSectionPos sectionPos,
			ChunkRendererRegion renderRegion,
			VertexSorter vertexSorter,
			BlockBufferAllocatorStorage allocatorStorage,
			CallbackInfoReturnable<SectionBuilder.RenderData> cir
	) {
		RenderPipelineContext.endChunkRebuild();
	}
}
