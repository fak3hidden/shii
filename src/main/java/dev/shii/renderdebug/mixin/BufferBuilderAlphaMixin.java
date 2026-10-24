package dev.shii.renderdebug.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import dev.shii.renderdebug.config.RenderDebugManager;
import dev.shii.renderdebug.render.RenderPipelineContext;
import net.minecraft.client.render.BufferBuilder;

/**
 * Pipeline Stage 4b: Vertex Color Alpha Channel Injection.
 *
 * <p>Simply rerouting an opaque block like {@code minecraft:stone} to
 * {@code RenderLayer.getTranslucent()} is NOT sufficient to make it visually
 * transparent: {@code stone.png} has 100% opaque texels ({@code texel.a = 1.0}),
 * and baked block quads emit {@code vertexColor.a = 255} ({@code 0xFF}). Because
 * the terrain fragment shader computes {@code fragColor.a = texel.a * vertexColor.a},
 * the block would still appear completely solid unless {@code vertexColor.a} is reduced.
 *
 * <p>In Minecraft 1.21+, all chunk vertex colors pass through {@link BufferBuilder}:
 * <ul>
 *   <li>Fabric API's Indigo renderer and {@code FluidRenderer} call instance method
 *       {@code BufferBuilder#color(int r, int g, int b, int a)}.</li>
 *   <li>Vanilla's {@code VertexConsumer#quad} fast path calls {@code BufferBuilder#vertex(...)}
 *       and {@code BufferBuilder#color(int argb)}, which write packed 32-bit ARGB via
 *       static helper {@code BufferBuilder#putColor(long pointer, int argb)}.</li>
 * </ul>
 * Intercepting both methods ensures consistent translucency across Vanilla and Indigo.
 */
@Mixin(BufferBuilder.class)
public abstract class BufferBuilderAlphaMixin {

	@ModifyVariable(
			method = "color(IIII)Lnet/minecraft/client/render/VertexConsumer;",
			at = @At("HEAD"),
			ordinal = 3,
			argsOnly = true
	)
	private int renderdebug$overrideRgbaAlphaByte(int alpha) {
		if (RenderPipelineContext.isCurrentBlockTranslucent()) {
			return RenderDebugManager.getInstance().getTranslucentAlphaByte();
		}
		return alpha;
	}

	@ModifyVariable(
			method = "putColor(JI)V",
			at = @At("HEAD"),
			ordinal = 0,
			argsOnly = true
	)
	private static int renderdebug$overridePackedArgbAlphaByte(int argb) {
		if (RenderPipelineContext.isCurrentBlockTranslucent()) {
			int customAlpha = RenderDebugManager.getInstance().getTranslucentAlphaByte() & 0xFF;
			return (customAlpha << 24) | (argb & 0x00FFFFFF);
		}
		return argb;
	}
}
