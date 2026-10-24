package dev.shii.renderdebug.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.shii.renderdebug.config.RenderDebugManager;
import dev.shii.renderdebug.render.RenderPipelineContext;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/**
 * Pipeline Stage 3: BlockState Mesh Visibility, Ambient Occlusion & Encased Luminance.
 *
 * <p>Targets {@link AbstractBlock.AbstractBlockState} to coordinate three critical
 * properties during chunk mesh construction:
 * <ol>
 *   <li><b>{@code getRenderType()}</b>: Scoped strictly to {@link RenderPipelineContext#isInChunkRebuild()}.
 *       When a block is filtered in {@code INVISIBLE} mode, returning {@link BlockRenderType#INVISIBLE}
 *       causes {@code SectionBuilder#build} to skip tessellation before either Vanilla's
 *       {@code BlockRenderManager} or Fabric API's Indigo {@code TerrainRenderContext} runs—while
 *       preserving {@code BlockRenderType.MODEL} outside chunk builds (held items, hit outlines,
 *       and breaking particles).</li>
 *   <li><b>{@code getAmbientOcclusionLightLevel()}</b>: Forces {@code 1.0F} when filtering or
 *       Fullbright is active so adjacent solid terrain blocks do not darken visible blocks via
 *       smooth-lighting corner AO weights.</li>
 *   <li><b>{@code getLuminance()}</b>: Underground blocks completely encased in solid stone have
 *       {@code SkyLight = 0} and {@code BlockLight = 0} in the server's {@code LightingProvider}.
 *       Returning luminance {@code 15} ONLY while {@link RenderPipelineContext#isInChunkRebuild()}
 *       is true illuminates visible encased blocks in both Vanilla and Fabric Indigo without
 *       affecting Singleplayer server light propagation, mob spawning, or ice melting.</li>
 * </ol>
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class BlockStateRenderMixin {

	@Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
	private void renderdebug$skipInvisibleFilteredBlocksInChunkMesh(CallbackInfoReturnable<BlockRenderType> cir) {
		if (!RenderPipelineContext.isInChunkRebuild()) {
			return;
		}
		BlockState self = (BlockState) (Object) this;
		if (RenderDebugManager.getInstance().shouldSkipBlockMesh(self)) {
			cir.setReturnValue(BlockRenderType.INVISIBLE);
		}
	}

	@Inject(method = "getAmbientOcclusionLightLevel", at = @At("HEAD"), cancellable = true)
	private void renderdebug$suppressNeighborAmbientOcclusionShadows(
			BlockView world,
			BlockPos pos,
			CallbackInfoReturnable<Float> cir
	) {
		RenderDebugManager manager = RenderDebugManager.getInstance();
		if (manager.isFilterEnabled() || manager.isFullbrightEnabled()) {
			cir.setReturnValue(1.0F);
		}
	}

	@Inject(method = "getLuminance", at = @At("HEAD"), cancellable = true)
	private void renderdebug$illuminateEncasedBlocksDuringChunkMeshing(CallbackInfoReturnable<Integer> cir) {
		if (!RenderPipelineContext.isInChunkRebuild()) {
			return;
		}
		RenderDebugManager manager = RenderDebugManager.getInstance();
		if (manager.isFullbrightEnabled()) {
			cir.setReturnValue(15);
			return;
		}
		if (manager.isFilterEnabled()) {
			BlockState self = (BlockState) (Object) this;
			if (manager.isBlockVisible(self)) {
				cir.setReturnValue(15);
			}
		}
	}
}
