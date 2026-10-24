package dev.shii.renderdebug.client.mixin;

import dev.shii.renderdebug.client.config.RenderDebugManager;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides hidden-surface face culling in {@link Block#shouldRenderFace(BlockState, BlockState, Direction)}.
 *
 * <h2>Why Neighbor Face Culling Must Be Overridden</h2>
 * <p>Both Vanilla {@code ModelBlockRenderer} and Fabric Indigo {@code AltModelBlockRendererImpl}
 * invoke {@code Block.shouldRenderFace(state, neighborState, direction)} before emitting each
 * directional quad. Normally, if {@code diamond_ore} is surrounded on all 6 sides by solid
 * {@code stone} ({@code neighborState.getFaceOcclusionShape(...) == Shapes.block()}),
 * {@code shouldRenderFace} returns {@code false} for all 6 faces — leaving the ore completely
 * hollow with 0 emitted quads even after the surrounding stone is hidden!
 *
 * <h2>Culling Matrix Applied Here</h2>
 * <ul>
 *   <li><b>Visible block bordering an occluded neighbor</b>: Force {@code true} so all exposed
 *       faces of whitelisted ores/structures are emitted into the chunk mesh.</li>
 *   <li><b>Translucent ghost block bordering another translucent ghost block</b>: Force {@code false}
 *       to cull interior shared faces between adjacent stone/deepslate blocks, preventing thousands
 *       of overlapping internal alpha seams.</li>
 *   <li><b>Translucent ghost block bordering a fully hidden block</b>: Force {@code true}.</li>
 * </ul>
 */
@Mixin(Block.class)
public abstract class BlockOcclusionMixin {

	@Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
	private static void renderdebug$overrideNeighborFaceCulling(
			BlockState state,
			BlockState neighborState,
			Direction direction,
			CallbackInfoReturnable<Boolean> cir
	) {
		RenderDebugManager manager = RenderDebugManager.getInstance();
		if (!manager.isFilterActive()) {
			return;
		}

		boolean selfVisible = manager.shouldRenderNormally(state);
		boolean neighborVisible = manager.shouldRenderNormally(neighborState);

		if (selfVisible && !neighborVisible) {
			// Whitelisted/visible block adjacent to an invisible or translucent-ghost block:
			// always emit this face so buried blocks are rendered as complete 3D cubes.
			cir.setReturnValue(true);
			return;
		}

		if (!selfVisible && manager.isBlockTranslucent(state)) {
			if (manager.isBlockTranslucent(neighborState)) {
				// Cull shared interior faces between two adjacent ghost-translucent blocks
				// so underground terrain appears as a clean translucent shell rather than a noisy grid.
				cir.setReturnValue(false);
			} else if (manager.isBlockFullyHidden(neighborState)) {
				cir.setReturnValue(true);
			}
		}
	}
}
