package dev.shii.renderdebug;

import dev.shii.renderdebug.config.RenderDebugManager;
import dev.shii.renderdebug.input.ModKeybindings;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client-side Fabric entrypoint for the Render Pipeline & Occlusion Debugger.
 *
 * <p>Declared under the {@code "client"} entrypoint with {@code "environment": "client"}
 * in {@code fabric.mod.json} so client-only classes ({@code Screen}, {@code KeyBinding},
 * {@code RenderLayer}) are never loaded on dedicated servers.
 */
public final class RenderDebugClientMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RenderDebugManager.getInstance().initialize();
		ModKeybindings.register();
		RenderDebugManager.LOGGER.info("Initialized Render Pipeline & Occlusion Debugger (default GUI key: Right Shift).");
	}
}
