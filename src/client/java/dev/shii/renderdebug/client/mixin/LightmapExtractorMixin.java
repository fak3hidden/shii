package dev.shii.renderdebug.client.mixin;

import dev.shii.renderdebug.client.config.RenderDebugManager;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Implements GPU-level Fullbright ambient lighting for Minecraft 26.3 via
 * {@link LightmapRenderStateExtractor}.
 *
 * <h2>Why Not Modify {@code options.gamma()}?</h2>
 * <p>Modern Minecraft validates {@code OptionInstance<Double>} slider ranges strictly in
 * {@code [0.0, 1.0]}, rejecting legacy {@code gamma = 16.0} hacks. Furthermore, in Minecraft 26.3,
 * {@code LightTexture} was refactored into a two-stage extract/render pipeline driven by
 * {@link LightmapRenderStateExtractor#extract(LightmapRenderState, float)} and
 * {@code EnvironmentAttributes.AMBIENT_LIGHT_COLOR}.
 *
 * <h2>How This Injection Works</h2>
 * <ul>
 *   <li>At {@code HEAD}: Detects transitions of {@link RenderDebugManager#isFullbrightEnabled()}
 *       and sets {@code this.needsUpdate = true} so toggling Fullbright ON or OFF updates the GPU
 *       16x16 lightmap texture on the <em>very next frame</em> without waiting for a client tick
 *       or rebuilding a single chunk mesh.</li>
 *   <li>At {@code RETURN}: When Fullbright is enabled, overrides {@code renderState.ambientColor}
 *       with {@link LightmapRenderStateExtractor#WHITE} ({@code 1.0, 1.0, 1.0}), clamps
 *       {@code brightness = 1.0F}, and zeroes out Darkness/Wither boss darkening factors.</li>
 * </ul>
 */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapExtractorMixin {

	@Shadow
	private boolean needsUpdate;

	@Unique
	private boolean renderdebug$wasFullbrightEnabled = false;

	@Inject(method = "extract", at = @At("HEAD"))
	private void renderdebug$forceImmediateUpdateOnToggle(
			LightmapRenderState renderState,
			float partialTicks,
			CallbackInfo ci
	) {
		boolean enabled = RenderDebugManager.getInstance().isFullbrightEnabled();
		if (enabled != this.renderdebug$wasFullbrightEnabled) {
			this.renderdebug$wasFullbrightEnabled = enabled;
			this.needsUpdate = true;
		}
	}

	@Inject(method = "extract", at = @At("RETURN"))
	private void renderdebug$applyFullbrightRenderState(
			LightmapRenderState renderState,
			float partialTicks,
			CallbackInfo ci
	) {
		if (this.renderdebug$wasFullbrightEnabled && renderState.needsUpdate) {
			renderState.ambientColor = LightmapRenderStateExtractor.WHITE;
			renderState.brightness = 1.0F;
			renderState.darknessEffectScale = 0.0F;
			renderState.bossOverlayWorldDarkening = 0.0F;
		}
	}
}
