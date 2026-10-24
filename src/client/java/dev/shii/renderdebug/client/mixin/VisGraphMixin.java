package dev.shii.renderdebug.client.mixin;

import dev.shii.renderdebug.client.config.RenderDebugManager;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overrides Minecraft 26.3's sub-chunk cave-culling graph ({@link VisGraph}).
 *
 * <h2>Pipeline Role of {@code VisGraph}</h2>
 * <p>During {@code SectionCompiler#compile}, Minecraft marks every solid full-cube block via
 * {@link VisGraph#setOpaque(BlockPos)} and then runs a 3D breadth-first flood fill in
 * {@code VisGraph#resolve()} to compute a 6x6 {@code VisibilitySet} describing which pairs of
 * chunk-section faces can see one another. If all 4,096 blocks in an underground sub-chunk are
 * solid stone/deepslate, {@code SectionOcclusionGraph} skips submitting that entire sub-chunk and
 * every sub-chunk behind it to the GPU — hiding whitelisted ores inside!
 *
 * <p>Cancelling {@code setOpaque} while the visibility filter is active keeps all 6 face portals
 * open in the {@code VisibilitySet}, allowing {@code LevelExtractor} to traverse into underground
 * chunk sections and submit whitelisted block meshes.
 */
@Mixin(VisGraph.class)
public abstract class VisGraphMixin {

	@Inject(method = "setOpaque", at = @At("HEAD"), cancellable = true)
	private void renderdebug$keepSectionGraphOpen(BlockPos pos, CallbackInfo ci) {
		if (RenderDebugManager.getInstance().isFilterActive()) {
			ci.cancel();
		}
	}
}
