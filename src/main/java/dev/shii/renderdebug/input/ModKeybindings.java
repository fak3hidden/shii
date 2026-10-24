package dev.shii.renderdebug.input;

import org.lwjgl.glfw.GLFW;

import dev.shii.renderdebug.config.RenderDebugManager;
import dev.shii.renderdebug.gui.RenderDebugScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Registers client-side keybindings with Fabric's {@link KeyBindingHelper}
 * and polls input state at the end of every client tick.
 */
public final class ModKeybindings {
	private static final String CATEGORY = "category.renderdebug.general";

	private static KeyBinding openGuiKey;
	private static KeyBinding quickToggleFilterKey;
	private static KeyBinding quickToggleFullbrightKey;

	private ModKeybindings() {
	}

	public static void register() {
		// Default keybinding: Right Shift (GLFW_KEY_RIGHT_SHIFT) to open the debugger UI.
		openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.renderdebug.open_gui",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				CATEGORY
		));

		// Optional unbound hotkeys configurable in Options -> Controls -> Key Binds.
		quickToggleFilterKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.renderdebug.toggle_filter",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				CATEGORY
		));

		quickToggleFullbrightKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.renderdebug.toggle_fullbright",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openGuiKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new RenderDebugScreen(null));
				}
			}

			while (quickToggleFilterKey.wasPressed()) {
				RenderDebugManager manager = RenderDebugManager.getInstance();
				manager.toggleFilter();
				if (client.player != null) {
					boolean active = manager.isFilterEnabled();
					client.player.sendMessage(
							Text.literal("Block Visibility Filter: ")
									.append(Text.literal(active ? "ACTIVE" : "OFF")
											.formatted(active ? Formatting.GREEN : Formatting.RED)),
							true
					);
				}
			}

			while (quickToggleFullbrightKey.wasPressed()) {
				RenderDebugManager manager = RenderDebugManager.getInstance();
				manager.toggleFullbright();
				if (client.player != null) {
					boolean active = manager.isFullbrightEnabled();
					client.player.sendMessage(
							Text.literal("Fullbright Ambient Lighting: ")
									.append(Text.literal(active ? "ACTIVE" : "OFF")
											.formatted(active ? Formatting.GOLD : Formatting.GRAY)),
							true
					);
				}
			}
		});
	}
}
