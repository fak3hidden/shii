package dev.shii.renderdebug.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.shii.renderdebug.client.RenderDebugClientMod;
import dev.shii.renderdebug.client.config.RenderDebugManager;
import dev.shii.renderdebug.client.gui.RenderDebugScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.sdl.SDLScancode;

/**
 * Registers SDL3 keybindings for Minecraft 26.3 using {@code fabric-key-mapping-api-v1}
 * and polls them on {@link ClientTickEvents#END_CLIENT_TICK}.
 *
 * <ul>
 *   <li><b>Right Shift</b> ({@link SDLScancode#SDL_SCANCODE_RSHIFT}): Opens the {@link RenderDebugScreen}
 *       via Minecraft 26.3's {@code client.gui.setScreen(...)}.</li>
 *   <li><b>Toggle Filter</b> (unbound by default): Toggles the Block Visibility Filter directly in-game.</li>
 *   <li><b>Toggle Fullbright</b> (unbound by default): Toggles Fullbright ambient lighting directly in-game.</li>
 * </ul>
 */
public final class ModKeybindings {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(RenderDebugClientMod.MOD_ID, "general")
	);

	private static KeyMapping openGuiKey;
	private static KeyMapping toggleFilterKey;
	private static KeyMapping toggleFullbrightKey;

	private ModKeybindings() {
	}

	public static void register() {
		openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.renderdebug.open_gui",
				InputConstants.Type.KEYBOARD,
				SDLScancode.SDL_SCANCODE_RSHIFT,
				CATEGORY
		));

		toggleFilterKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.renderdebug.toggle_filter",
				InputConstants.Type.KEYBOARD,
				InputConstants.UNKNOWN.getValue(),
				CATEGORY
		));

		toggleFullbrightKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.renderdebug.toggle_fullbright",
				InputConstants.Type.KEYBOARD,
				InputConstants.UNKNOWN.getValue(),
				CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openGuiKey.consumeClick()) {
				if (!(client.gui.screen() instanceof RenderDebugScreen)) {
					client.gui.setScreen(new RenderDebugScreen(client.gui.screen()));
				}
			}

			while (toggleFilterKey.consumeClick()) {
				boolean active = RenderDebugManager.getInstance().toggleFilterActive();
				if (client.player != null) {
					Component stateText = Component.literal(active ? "ACTIVE" : "OFF")
							.withStyle(active ? ChatFormatting.GREEN : ChatFormatting.RED);
					client.player.sendSystemMessage(Component.translatable("chat.renderdebug.filter_toggled", stateText));
				}
			}

			while (toggleFullbrightKey.consumeClick()) {
				boolean enabled = RenderDebugManager.getInstance().toggleFullbright();
				if (client.player != null) {
					Component stateText = Component.literal(enabled ? "ON" : "OFF")
							.withStyle(enabled ? ChatFormatting.AQUA : ChatFormatting.GRAY);
					client.player.sendSystemMessage(Component.translatable("chat.renderdebug.fullbright_toggled", stateText));
				}
			}
		});
	}

	public static boolean matchesOpenGuiKey(KeyEvent event) {
		return openGuiKey != null && openGuiKey.matches(event);
	}
}
