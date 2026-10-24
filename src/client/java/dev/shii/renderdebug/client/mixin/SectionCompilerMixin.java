package dev.shii.renderdebug.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.shii.renderdebug.client.config.RenderDebugManager;
import dev.shii.renderdebug.client.render.RenderPipelineContext;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Core chunk-compilation pipeline hooks for Minecraft 26.3's {@link SectionCompiler}.
 *
 * <h2>How This Manipulates Minecraft 26.3's Render Layers</h2>
 * <ol>
 *   <li><b>Worker-Thread Scoping ({@code compile} HEAD/RETURN)</b>: Activates
 *       {@link RenderPipelineContext} on the current chunk-builder worker thread so downstream
 *       AO, lightmap, and {@code BufferBuilder} overrides never leak onto the server thread or UI.</li>
 *   <li><b>Zero-Quad Mesh Bypass ({@code getRenderShape} WrapOperation)</b>: In Minecraft 26.3,
 *       Fabric API's Indigo renderer ({@code SectionCompilerMixin}) {@code @Redirect}s
 *       {@code ModelBlockRenderer#tesselateBlock} to {@code AltModelBlockRenderer#tesselateBlock}.
 *       Intercepting {@code blockState.getRenderShape()} immediately upstream inside {@code compile}
 *       cleanly cooperates with <em>both</em> Vanilla and Indigo: returning {@link RenderShape#INVISIBLE}
 *       skips tessellation completely for {@code INVISIBLE} occluded blocks, whereas returning the
 *       original {@link RenderShape#MODEL} with {@code CURRENT_BLOCK_TRANSLUCENT = true} routes the
 *       block into the translucent ghost pipeline.</li>
 *   <li><b>Render Layer Re-Routing ({@code getOrBeginLayer} ModifyVariable)</b>: Vanilla's
 *       {@code quadOutput}, {@code opaqueQuadOutput}, {@code fluidOutput}, and Fabric Indigo's
 *       {@code altQuadOutput} all acquire their {@code BufferBuilder} through
 *       {@code SectionCompiler#getOrBeginLayer(..., ChunkSectionLayer)}. Rewriting the requested
 *       {@link ChunkSectionLayer} from {@code SOLID} or {@code CUTOUT} to
 *       {@link ChunkSectionLayer#TRANSLUCENT} ensures occluded ghost blocks are depth-sorted via
 *       {@code MeshData#sortQuads} and drawn during RenderPearl's blended transparency pass
 *       ({@code executeClassicTransparency} / {@code executeOit}).</li>
 * </ol>
 */
@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {

	@Inject(method = "compile", at = @At("HEAD"))
	private void renderdebug$enterCompileScope(
			SectionPos sectionPos,
			RenderSectionRegion region,
			VertexSorting vertexSorting,
			SectionBufferBuilderPack builders,
			CallbackInfoReturnable<SectionCompiler.Results> cir
	) {
		RenderPipelineContext.enterChunkRebuild();
	}

	@Inject(method = "compile", at = @At("RETURN"))
	private void renderdebug$exitCompileScope(
			SectionPos sectionPos,
			RenderSectionRegion region,
			VertexSorting vertexSorting,
			SectionBufferBuilderPack builders,
			CallbackInfoReturnable<SectionCompiler.Results> cir
	) {
		RenderPipelineContext.exitChunkRebuild();
	}

	@Inject(
			method = "compile",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/block/state/BlockState;isAir()Z"
			)
	)
	private void renderdebug$resetPerBlockContext(
			SectionPos sectionPos,
			RenderSectionRegion region,
			VertexSorting vertexSorting,
			SectionBufferBuilderPack builders,
			CallbackInfoReturnable<SectionCompiler.Results> cir
	) {
		// Guarantee clean per-block state at the top of every 16x16x16 BlockPos iteration.
		RenderPipelineContext.setCurrentBlockTranslucent(false);
	}

	@WrapOperation(
			method = "compile",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/block/state/BlockState;getRenderShape()Lnet/minecraft/world/level/block/RenderShape;"
			)
	)
	private RenderShape renderdebug$filterBlockRenderShape(
			BlockState state,
			Operation<RenderShape> original
	) {
		RenderDebugManager manager = RenderDebugManager.getInstance();
		if (manager.isBlockFullyHidden(state)) {
			// Returning INVISIBLE skips both Vanilla ModelBlockRenderer and Fabric Indigo AltModelBlockRenderer,
			// emitting zero vertices into the chunk section's GPU vertex buffer.
			RenderPipelineContext.setCurrentBlockTranslucent(false);
			return RenderShape.INVISIBLE;
		}

		// If the block is occluded in TRANSLUCENT style, flag this thread so getOrBeginLayer() routes
		// its quads to ChunkSectionLayer.TRANSLUCENT and BufferBuilderAlphaMixin injects ghost alpha.
		RenderPipelineContext.setCurrentBlockTranslucent(manager.isBlockTranslucent(state));
		return original.call(state);
	}

	@ModifyVariable(
			method = "getOrBeginLayer",
			at = @At("HEAD"),
			argsOnly = true
	)
	private ChunkSectionLayer renderdebug$rerouteLayerToTranslucent(ChunkSectionLayer requestedLayer) {
		if (RenderPipelineContext.isCurrentBlockTranslucent()) {
			// Redirect SOLID and CUTOUT quads into the depth-sorted alpha-blended TRANSLUCENT pass.
			// Blocks drawn in ChunkSectionLayer.SOLID have alpha blending disabled in their RenderPipeline
			// and write opaque depth, which would otherwise hide ores behind black/opaque geometry.
			return ChunkSectionLayer.TRANSLUCENT;
		}
		return requestedLayer;
	}
}
