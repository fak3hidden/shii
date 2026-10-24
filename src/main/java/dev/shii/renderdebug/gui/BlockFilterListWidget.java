package dev.shii.renderdebug.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import dev.shii.renderdebug.config.BlockFilterStatus;
import dev.shii.renderdebug.config.RenderDebugManager;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Scrollable, searchable widget listing registered Minecraft {@link Block}s with
 * live status badges to cycle each block between {@code DEFAULT}, {@code WHITELISTED},
 * and {@code BLACKLISTED}.
 */
public class BlockFilterListWidget extends ElementListWidget<BlockFilterListWidget.BlockRowEntry> {

	public enum CategoryTab {
		ALL("All"),
		ORES("Ores & Loot"),
		TERRAIN("Terrain"),
		WHITELISTED("Whitelisted"),
		BLACKLISTED("Blacklisted");

		private final String label;

		CategoryTab(String label) {
			this.label = label;
		}

		public String getLabel() {
			return this.label;
		}
	}

	private final List<BlockMetadata> allBlocks;
	private final Runnable onRuleChanged;

	public BlockFilterListWidget(
			MinecraftClient client,
			int width,
			int height,
			int y,
			int itemHeight,
			Runnable onRuleChanged
	) {
		super(client, width, height, y, itemHeight);
		this.onRuleChanged = onRuleChanged;
		this.allBlocks = collectRegisteredBlocks();
	}

	private static List<BlockMetadata> collectRegisteredBlocks() {
		List<BlockMetadata> entries = new ArrayList<>();
		for (Block block : Registries.BLOCK) {
			if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
				continue;
			}
			Identifier id = Registries.BLOCK.getId(block);
			String idString = id.toString();
			String displayName = block.getName().getString();
			entries.add(new BlockMetadata(
					block,
					idString,
					displayName,
					idString.toLowerCase(Locale.ROOT),
					displayName.toLowerCase(Locale.ROOT)
			));
		}
		entries.sort(Comparator.comparing(BlockMetadata::idString));
		return entries;
	}

	/**
	 * Rebuilds visible rows matching the active search query and category tab.
	 */
	public void refreshEntries(String rawQuery, CategoryTab category) {
		String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
		RenderDebugManager manager = RenderDebugManager.getInstance();

		List<BlockRowEntry> filtered = new ArrayList<>();
		for (BlockMetadata meta : this.allBlocks) {
			if (!query.isEmpty()
					&& !meta.lowerName().contains(query)
					&& !meta.lowerId().contains(query)) {
				continue;
			}
			BlockFilterStatus status = manager.getBlockStatus(meta.block());
			if (!matchesCategory(meta, status, category)) {
				continue;
			}
			filtered.add(new BlockRowEntry(this.client, meta, this.onRuleChanged));
		}

		this.replaceEntries(filtered);
		this.setScrollY(0.0);
	}

	private static boolean matchesCategory(BlockMetadata meta, BlockFilterStatus status, CategoryTab tab) {
		String path = meta.lowerId();
		return switch (tab) {
			case ALL -> true;
			case WHITELISTED -> status == BlockFilterStatus.WHITELISTED;
			case BLACKLISTED -> status == BlockFilterStatus.BLACKLISTED;
			case ORES -> path.contains("_ore")
					|| path.contains("ancient_debris")
					|| path.contains("raw_")
					|| path.contains("amethyst")
					|| path.contains("spawner")
					|| path.contains("vault")
					|| path.contains("chest")
					|| path.contains("barrel")
					|| path.contains("shulker_box")
					|| path.contains("portal");
			case TERRAIN -> path.endsWith(":stone")
					|| path.contains("deepslate")
					|| path.contains("dirt")
					|| path.contains("grass_block")
					|| path.contains("granite")
					|| path.contains("diorite")
					|| path.contains("andesite")
					|| path.contains("tuff")
					|| path.contains("calcite")
					|| path.contains("sand")
					|| path.contains("gravel")
					|| path.contains("netherrack")
					|| path.contains("basalt")
					|| path.contains("blackstone")
					|| path.contains("end_stone")
					|| path.contains("bedrock");
		};
	}

	@Override
	public int getRowWidth() {
		return Math.min(392, this.width - 28);
	}

	private record BlockMetadata(
			Block block,
			String idString,
			String displayName,
			String lowerId,
			String lowerName
	) {
	}

	public static final class BlockRowEntry extends ElementListWidget.Entry<BlockRowEntry> {
		private static final int BUTTON_WIDTH = 98;
		private static final int BUTTON_HEIGHT = 18;

		private final MinecraftClient client;
		private final BlockMetadata meta;
		private final ItemStack iconStack;
		private final ButtonWidget statusButton;
		private BlockFilterStatus currentStatus;

		BlockRowEntry(MinecraftClient client, BlockMetadata meta, Runnable onRuleChanged) {
			this.client = client;
			this.meta = meta;
			this.iconStack = new ItemStack(meta.block().asItem());
			this.currentStatus = RenderDebugManager.getInstance().getBlockStatus(meta.block());

			this.statusButton = ButtonWidget.builder(this.currentStatus.toBadgeText(), button -> {
				this.currentStatus = RenderDebugManager.getInstance().cycleBlockStatus(meta.block());
				button.setMessage(this.currentStatus.toBadgeText());
				onRuleChanged.run();
			})
					.dimensions(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
					.tooltip(Tooltip.of(Text.literal("Click to cycle: Default -> Whitelisted -> Blacklisted")))
					.build();
		}

		@Override
		public void render(
				DrawContext context,
				int index,
				int y,
				int x,
				int entryWidth,
				int entryHeight,
				int mouseX,
				int mouseY,
				boolean hovered,
				float tickDelta
		) {
			// Subtle row card background + colored left status indicator bar
			int bgColor = hovered ? 0x66303846 : (index % 2 == 0 ? 0x44181C24 : 0x44101318);
			context.fill(x, y, x + entryWidth, y + entryHeight - 2, bgColor);
			context.fill(x, y, x + 3, y + entryHeight - 2, this.currentStatus.getAccentColorArgb());

			// Render 16x16 block item icon (with fallback badge for fluid/technical blocks)
			int iconX = x + 7;
			int iconY = y + (entryHeight - 18) / 2;
			if (!this.iconStack.isEmpty()) {
				context.drawItem(this.iconStack, iconX, iconY);
			} else {
				context.fill(iconX + 1, iconY + 1, iconX + 15, iconY + 15, 0xFF2C3E50);
				context.drawTextWithShadow(this.client.textRenderer, "B", iconX + 5, iconY + 4, 0xFFAAB7B8);
			}

			// Render localized block name and registry Identifier
			TextRenderer font = this.client.textRenderer;
			int textX = x + 28;
			int maxTextWidth = Math.max(60, entryWidth - BUTTON_WIDTH - 38);

			String trimmedName = font.trimToWidth(this.meta.displayName(), maxTextWidth);
			String trimmedId = font.trimToWidth(this.meta.idString(), maxTextWidth);

			context.drawTextWithShadow(font, trimmedName, textX, y + 2, 0xFFFFFFFF);
			context.drawTextWithShadow(
					font,
					Text.literal(trimmedId).formatted(Formatting.DARK_GRAY),
					textX,
					y + 12,
					0xFF888888
			);

			// Position and draw interactive status button on the right side of the row
			int buttonX = x + entryWidth - BUTTON_WIDTH - 4;
			int buttonY = y + (entryHeight - 2 - BUTTON_HEIGHT) / 2;
			this.statusButton.setPosition(buttonX, buttonY);
			this.statusButton.render(context, mouseX, mouseY, tickDelta);
		}

		@Override
		public List<? extends Element> children() {
			return List.of(this.statusButton);
		}

		@Override
		public List<? extends Selectable> selectableChildren() {
			return List.of(this.statusButton);
		}
	}
}
