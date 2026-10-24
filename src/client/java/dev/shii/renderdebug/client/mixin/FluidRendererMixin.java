package dev.shii.renderdebug.client.mixin;

import dev.shii.renderdebug.client.config.RenderDebugManager;
import dev.shii.renderdebug.client.render.RenderPipelineContext;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extends block visibility filtering to fluids ({@code minecraft:water}, {@code minecraft:lava},
 * and waterlogged blocks) during {@link FluidRenderer#tesselate}.
 *
 * <p>In {@code SectionCompiler#compile}, {@code FluidRenderer#tesselate} runs just before
 * {@code blockState.getRenderShape()}. Scoping {@link RenderPipelineContext#setCurrentBlockTranslucent}
 * around {@code tesselate} ensures:
 * <ul>
 *   <li>Fully hidden fluids emit zero vertices ({@code ci.cancel()}).</li>
 *   <li>Translucent-occluded fluids (including normally opaque {@code ChunkSectionLayer.SOLID} lava!)
 *       are routed through {@code SectionCompiler#getOrBeginLayer} into
 *       {@code ChunkSectionLayer.TRANSLUCENT} with custom vertex alpha.</li>
 * </ul>
 */
@Mixin(FluidRenderer.class)
public abstract class FluidRendererMixin {

	@Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
	private void renderdebug$beforeFluidTesselate(
			BlockAndTintGetter level,
			BlockPos pos,
			FluidRenderer.Output output,
			BlockState blockState,
			FluidState fluidState,
			CallbackInfo ci
	) {
		if (!RenderPipelineContext.isInChunkRebuild()) {
			return;
		}

		RenderDebugManager manager = RenderDebugManager.getInstance();
		if (manager.isBlockFullyHidden(blockState)) {
			RenderPipelineContext.setCurrentBlockTranslucent(false);
			ci.cancel();
			return;
		}

		RenderPipelineContext.setCurrentBlockTranslucent(manager.isBlockTranslucent(blockState));
	}

	@Inject(method = "tesselate", at = @At("RETURN"))
	private void renderdebug$afterFluidTesselate(
			BlockAndTintGetter level,
			BlockPos pos,
			FluidRenderer.Output output,
			BlockState blockState,
			FluidState fluidState,
			CallbackInfo ci
	) {
		if (RenderPipelineContext.isInChunkRebuild()) {
			RenderPipelineContext.setCurrentBlockTranslucent(false);
		}
	}
}
