package dev.shii.renderdebug.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shii.renderdebug.config.RenderDebugManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

/**
 * Pipeline Stage 3b: Fluid & Vanilla Fallback Tessellation Guard.
 *
 * <p>In {@code SectionBuilder#build}, fluid states ({@code water}, {@code lava}) are
 * tessellated via {@link BlockRenderManager#renderFluid} independently of
 * {@code BlockState#getRenderType()}. This Mixin ensures blacklisted fluids also
 * bypass mesh generation when {@code Invisible} mode is active, and acts as an
 * explicit guard on {@link BlockRenderManager#renderBlock} when Indigo is disabled.
 */
@Mixin(BlockRenderManager.class)
public abstract class BlockRenderManagerMixin {

	@Inject(method = "renderFluid", at = @At("HEAD"), cancellable = true)
	private void renderdebug$filterFluidTessellation(
			BlockPos pos,
			BlockRenderView world,
			VertexConsumer vertexConsumer,
			BlockState blockState,
			FluidState fluidState,
			CallbackInfo ci
	) {
		if (RenderDebugManager.getInstance().shouldSkipBlockMesh(blockState)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
	private void renderdebug$filterVanillaBlockTessellation(
			BlockState state,
			BlockPos pos,
			BlockRenderView world,
			MatrixStack matrices,
			VertexConsumer vertexConsumer,
			boolean cull,
			Random random,
			CallbackInfo ci
	) {
		if (RenderDebugManager.getInstance().shouldSkipBlockMesh(state)) {
			ci.cancel();
		}
	}
}
