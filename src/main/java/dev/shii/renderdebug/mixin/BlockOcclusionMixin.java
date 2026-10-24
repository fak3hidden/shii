package dev.shii.renderdebug.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.shii.renderdebug.config.OccludedRenderStyle;
import dev.shii.renderdebug.config.RenderDebugManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;

/**
 * Pipeline Stage 2: Neighbor Block Face Culling Override.
 *
 * <p>Both Vanilla's {@code BlockModelRenderer} and Fabric API's Indigo renderer
 * ({@code BlockRenderInfo#shouldDrawSide}) call {@link Block#shouldDrawSide} to test
 * whether each of a block's 6 directional faces is hidden behind an adjacent opaque
 * block's culling shape.
 *
 * <p>Without this Mixin:
 * <ul>
 *   <li>A whitelisted block (e.g., {@code diamond_ore}) encased on all 6 sides by
 *       {@code deepslate} has all 6 of its quads culled because {@code deepslate}
 *       reports an opaque full-cube culling face—making the ore invisible even when
 *       the surrounding {@code deepslate} mesh is skipped!</li>
 *   <li>Conversely, in {@link OccludedRenderStyle#TRANSLUCENT} mode, rendering all 6
 *       internal faces of thousands of adjacent underground stone blocks would cause
 *       severe fragment-shader alpha overdraw and transparency sorting artifacts.</li>
 * </ul>
 */
@Mixin(Block.class)
public abstract class BlockOcclusionMixin {

	@Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
	private static void renderdebug$overrideAdjacentFaceCulling(
			BlockState state,
			BlockState otherState,
			Direction side,
			CallbackInfoReturnable<Boolean> cir
	) {
		RenderDebugManager manager = RenderDebugManager.getInstance();
		if (!manager.isFilterEnabled()) {
			return;
		}

		boolean selfVisible = manager.isBlockVisible(state);
		boolean neighborIsAir = otherState.isAir();
		boolean neighborVisible = !neighborIsAir && manager.isBlockVisible(otherState);

		if (selfVisible) {
			// Case A: This block is whitelisted/visible.
			// If the neighbor is filtered/occluded (e.g. stone next to diamond_ore),
			// force the face to render so the filtered neighbor cannot cull it.
			// If the neighbor is ALSO visible (e.g. two adjacent diamond_ore blocks),
			// fall through to vanilla culling so shared interior faces between visible
			// blocks are still culled normally.
			if (!neighborIsAir && !neighborVisible) {
				cir.setReturnValue(true);
			}
			return;
		}

		// Case B: This block itself is filtered/occluded (e.g. blacklisted stone or dirt).
		if (manager.getOccludedStyle() == OccludedRenderStyle.INVISIBLE) {
			cir.setReturnValue(false);
			return;
		}

		// Case C: OccludedRenderStyle.TRANSLUCENT.
		// 1. Draw faces exposed to air (surface terrain & cave tunnel shells).
		// 2. Cull shared interior faces between two adjacent filtered blocks to eliminate
		//    alpha-blending overdraw across thousands of underground blocks.
		// 3. Against a visible block, only draw the translucent face if the visible neighbor
		//    is NOT an opaque full cube (prevents coplanar Z-fighting against solid ores
		//    while preserving translucent floors beneath chests or torches).
		if (neighborIsAir) {
			cir.setReturnValue(true);
		} else if (!neighborVisible) {
			cir.setReturnValue(false);
		} else {
			cir.setReturnValue(!otherState.isOpaqueFullCube());
		}
	}
}
