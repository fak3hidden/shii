package dev.shii.renderdebug.client.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.shii.renderdebug.client.config.RenderDebugManager;
import dev.shii.renderdebug.client.render.RenderPipelineContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Rewrites per-vertex alpha inside {@link BufferBuilder} when emitting ghost-translucent chunk geometry.
 *
 * <h2>Minecraft 26.3 Vertex Color Pathways</h2>
 * <p>In Minecraft 26.3, chunk compilers write vertex colors into {@link BufferBuilder} through three
 * distinct methods depending on the active renderer:
 * <ol>
 *   <li>{@code addVertex(FFFIFFIIFFF)V}: Fast 11-argument block-format path invoked by both Vanilla's
 *       {@code VertexConsumer#putBlockBakedQuad} and Fabric Indigo's {@code QuadViewImpl#buffer},
 *       writing packed {@code 0xAARRGGBB} directly via {@code putRgba(pointer + 12L, color)}.</li>
 *   <li>{@code setColor(IIII)Lcom/mojang/blaze3d/vertex/VertexConsumer;}: 4-channel RGBA path invoked
 *       by {@code FluidRenderer#tesselate} for water and lava vertices.</li>
 *   <li>{@code setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;}: Packed {@code 0xAARRGGBB}
 *       builder path.</li>
 * </ol>
 *
 * <p>Intercepting all three pathways while {@link RenderPipelineContext#isCurrentBlockTranslucent()}
 * is {@code true} replaces the opaque {@code 0xFF} alpha channel with the user's configured
 * {@code Ghost Alpha} byte ({@code 20..240}) while preserving biome grass/foliage/water RGB tints.
 */
@Mixin(BufferBuilder.class)
public abstract class BufferBuilderAlphaMixin {

	@ModifyVariable(
			method = "addVertex(FFFIFFIIFFF)V",
			at = @At("HEAD"),
			ordinal = 0,
			argsOnly = true
	)
	private int renderdebug$applyAlphaToFastVertexColor(int argbColor) {
		if (RenderPipelineContext.isCurrentBlockTranslucent()) {
			int alpha = RenderDebugManager.getInstance().getTranslucentAlpha() & 0xFF;
			return (alpha << 24) | (argbColor & 0x00FFFFFF);
		}
		return argbColor;
	}

	@ModifyVariable(
			method = "setColor(IIII)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
			at = @At("HEAD"),
			ordinal = 3,
			argsOnly = true
	)
	private int renderdebug$applyAlphaToRgbaChannel(int alphaChannel) {
		if (RenderPipelineContext.isCurrentBlockTranslucent()) {
			return RenderDebugManager.getInstance().getTranslucentAlpha() & 0xFF;
		}
		return alphaChannel;
	}

	@ModifyVariable(
			method = "setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
			at = @At("HEAD"),
			ordinal = 0,
			argsOnly = true
	)
	private int renderdebug$applyAlphaToPackedSetColor(int argbColor) {
		if (RenderPipelineContext.isCurrentBlockTranslucent()) {
			int alpha = RenderDebugManager.getInstance().getTranslucentAlpha() & 0xFF;
			return (alpha << 24) | (argbColor & 0x00FFFFFF);
		}
		return argbColor;
	}
}
