package dev.shii.renderdebug.client.mixin;

import dev.shii.renderdebug.client.config.RenderDebugManager;
import dev.shii.renderdebug.client.render.RenderPipelineContext;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents buried whitelisted blocks from rendering as pitch-black silhouettes when Fullbright is OFF.
 *
 * <h2>How Vertex Light Sampling Works in Minecraft 26.3</h2>
 * <p>Both Vanilla {@link BlockModelLighter} and Fabric Indigo's {@code AoCalculator} &
 * {@code FlatLighter} sample packed sky/block lightmap UV coordinates through
 * {@link BlockModelLighter.Cache#getLightCoords(BlockState, BlockAndTintGetter, BlockPos)} at the
 * neighbor position {@code pos.relative(faceDirection)}. When an ore block is encased inside solid
 * stone at Y=-54, the client {@code LightEngine} reports {@code 0} sky light and {@code 0} block
 * light inside that solid stone neighbor.
 *
 * <p>When tessellating a <b>visible</b> block ({@code !isCurrentBlockTranslucent()}) whose neighbor
 * {@code state} is an <b>occluded</b> solid block ({@code !shouldRenderNormally(state)}), returning
 * {@link LightCoordsUtil#FULL_BRIGHT} ({@code 0x00F000F0}) illuminates only faces exposed by our
 * occlusion filter — while faces bordering natural dark cave air ({@code shouldRenderNormally(air)})
 * retain authentic cave lighting until Fullbright is enabled!
 */
@Mixin(BlockModelLighter.Cache.class)
public abstract class BlockModelLighterCacheMixin {

	@Inject(method = "getLightCoords", at = @At("HEAD"), cancellable = true)
	private void renderdebug$illuminateFacesBorderingOccludedTerrain(
			BlockState state,
			BlockAndTintGetter level,
			BlockPos pos,
			CallbackInfoReturnable<Integer> cir
	) {
		if (!RenderPipelineContext.isInChunkRebuild() || RenderPipelineContext.isCurrentBlockTranslucent()) {
			return;
		}

		RenderDebugManager manager = RenderDebugManager.getInstance();
		if (manager.isFilterActive() && !manager.shouldRenderNormally(state)) {
			cir.setReturnValue(LightCoordsUtil.FULL_BRIGHT);
		}
	}
}
