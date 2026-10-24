package dev.shii.renderdebug.client;

import dev.shii.renderdebug.client.config.RenderDebugManager;
import dev.shii.renderdebug.client.input.ModKeybindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Primary client entrypoint for the Render Pipeline & Occlusion Debugger mod (Minecraft 26.3).
 *
 * <p>Bootstraps the lock-free {@link RenderDebugManager} filter state snapshot, registers SDL3
 * {@link ModKeybindings} via {@code fabric-key-mapping-api-v1}, and flushes user settings to disk
 * on client shutdown.
 */
public final class RenderDebugClientMod implements ClientModInitializer {
	public static final String MOD_ID = "renderdebug";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("[RenderDebug] Initializing Minecraft 26.3 Render Pipeline & Occlusion Debugger...");

		RenderDebugManager manager = RenderDebugManager.getInstance();

		// Defer initial block registry snapshot rebuild until registries are frozen on client start,
		// while loading saved JSON configuration immediately.
		manager.loadConfig();
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> manager.rebuildSnapshot());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> manager.saveConfig());

		ModKeybindings.register();

		LOGGER.info("[RenderDebug] Ready. Press Right Shift in-game to open the Render Pipeline Debugger UI.");
	}
}
