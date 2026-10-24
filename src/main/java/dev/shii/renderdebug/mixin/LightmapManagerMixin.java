package dev.shii.renderdebug.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.shii.renderdebug.config.RenderDebugManager;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.world.dimension.DimensionType;

/**
 * Pipeline Stage 5: Fullbright Ambient Lighting via {@link LightmapTextureManager}.
 *
 * <p>Why we target {@link LightmapTextureManager} rather than mutating {@code options.gamma}:
 * <ul>
 *   <li>Since Minecraft 1.19+, {@code SimpleOption<Double>} enforces strict slider bounds
 *       validation ({@code [0.0, 1.0]}), rejecting out-of-range gamma hacks like {@code 16.0}.</li>
 *   <li>In Minecraft 1.21.2+, {@link LightmapTextureManager#update(float)} renders the 16x16
 *       sky/block lightmap into {@code lightmapFramebuffer} via the GPU {@code lightmap}
 *       fragment shader using uniform {@code AmbientLightFactor = dimension.ambientLight()}.
 *       When {@code AmbientLightFactor} is {@code 1.0F}, the shader mixes every light-level
 *       combination to pure white {@code (1.0, 1.0, 1.0, 1.0)} on the very next frame without
 *       mutating {@code options.txt}.</li>
 * </ul>
 */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapManagerMixin {

	@Redirect(
			method = "update(F)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/dimension/DimensionType;ambientLight()F"
			)
	)
	private float renderdebug$overrideGpuLightmapAmbientFactor(DimensionType dimension) {
		if (RenderDebugManager.getInstance().isFullbrightEnabled()) {
			return 1.0F;
		}
		return dimension.ambientLight();
	}

	@Inject(method = "getDarknessFactor(F)F", at = @At("HEAD"), cancellable = true)
	private void renderdebug$suppressDarknessDimming(float delta, CallbackInfoReturnable<Float> cir) {
		if (RenderDebugManager.getInstance().isFullbrightEnabled()) {
			cir.setReturnValue(0.0F);
		}
	}

	@Inject(method = "getBrightness(Lnet/minecraft/world/dimension/DimensionType;I)F", at = @At("HEAD"), cancellable = true)
	private static void renderdebug$overrideCpuBrightnessLookup(
			DimensionType type,
			int lightLevel,
			CallbackInfoReturnable<Float> cir
	) {
		if (RenderDebugManager.getInstance().isFullbrightEnabled()) {
			cir.setReturnValue(1.0F);
		}
	}
}
