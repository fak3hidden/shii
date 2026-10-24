package dev.shii.renderdebug.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.shii.renderdebug.config.RenderDebugManager;
import dev.shii.renderdebug.render.RenderPipelineContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.fluid.FluidState;

/**
 * Pipeline Stage 4: RenderLayer Routing Override (Opaque -> Translucent Pass).
 *
 * <p>Minecraft's chunk renderer partitions section geometry into distinct draw passes:
 * <ol>
 *   <li>{@code RenderLayer.getSolid()} — opaque terrain, depth-write ON, {@code GL_BLEND} OFF</li>
 *   <li>{@code RenderLayer.getCutoutMipped()} / {@code getCutout()} — alpha-tested foliage/glass</li>
 *   <li>{@code RenderLayer.getTranslucent()} — back-to-front sorted quads, {@code GL_BLEND}
 *       ({@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA}) ON</li>
 * </ol>
 *
 * <p>When {@code OccludedRenderStyle.TRANSLUCENT} is active, both Vanilla's
 * {@code SectionBuilder#build} and Fabric API's Indigo ({@code BlockRenderInfo#prepareForBlock})
 * query {@link RenderLayers#getBlockLayer(BlockState)} immediately before emitting a block's
 * quads. Redirecting filtered blocks to {@link RenderLayer#getTranslucent()} places their
 * vertices into the depth-sorted alpha-blended buffer and flags {@link RenderPipelineContext}
 * so {@link BufferBuilderAlphaMixin} applies the configured alpha value to those vertices.
 */
@Mixin(RenderLayers.class)
public abstract class RenderLayersMixin {

	@Inject(method = "getBlockLayer", at = @At("HEAD"), cancellable = true)
	private static void renderdebug$routeFilteredBlocksToTranslucentLayer(
			BlockState state,
			CallbackInfoReturnable<RenderLayer> cir
	) {
		boolean translucent = RenderDebugManager.getInstance().shouldRenderTranslucent(state);
		if (RenderPipelineContext.isInChunkRebuild()) {
			RenderPipelineContext.setCurrentBlockTranslucent(translucent);
		}
		if (translucent) {
			cir.setReturnValue(RenderLayer.getTranslucent());
		}
	}

	@Inject(method = "getFluidLayer", at = @At("HEAD"), cancellable = true)
	private static void renderdebug$routeFilteredFluidsToTranslucentLayer(
			FluidState state,
			CallbackInfoReturnable<RenderLayer> cir
	) {
		boolean translucent = RenderDebugManager.getInstance().shouldRenderTranslucent(state.getBlockState());
		if (RenderPipelineContext.isInChunkRebuild()) {
			RenderPipelineContext.setCurrentBlockTranslucent(translucent);
		}
		if (translucent) {
			cir.setReturnValue(RenderLayer.getTranslucent());
		}
	}
}
