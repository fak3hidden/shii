package dev.shii.renderdebug.client.mixin;

import dev.shii.renderdebug.client.config.RenderDebugManager;
import dev.shii.renderdebug.client.render.RenderPipelineContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Eliminates dark smooth-lighting Ambient Occlusion (AO) corner shadows cast by occluded blocks.
 *
 * <p>During vertex AO calculation, {@code BlockModelLighter.Cache#getShadeBrightness} queries
 * {@link BlockBehaviour.BlockStateBase#getShadeBrightness(BlockGetter, BlockPos)} on neighboring
 * blocks. Full-cube opaque blocks normally return {@code 0.2F}, darkening the 4 corners of any
 * adjacent exposed ore quad. Scoping this override to {@link RenderPipelineContext#isInChunkRebuild()}
 * returns {@code 1.0F} for occluded blocks solely on chunk-compiler threads without affecting
 * gameplay logic.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateAmbientOcclusionMixin {

	@Shadow
	protected abstract BlockState asState();

	@Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true)
	private void renderdebug$suppressOccludedBlockAoShadow(
			BlockGetter level,
			BlockPos pos,
			CallbackInfoReturnable<Float> cir
	) {
		if (!RenderPipelineContext.isInChunkRebuild()) {
			return;
		}

		RenderDebugManager manager = RenderDebugManager.getInstance();
		if (manager.isFilterActive() && !manager.shouldRenderNormally(this.asState())) {
			cir.setReturnValue(1.0F);
		}
	}
}
