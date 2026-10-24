package dev.shii.renderdebug.client.gui;

import dev.shii.renderdebug.client.config.BlockFilterStatus;
import dev.shii.renderdebug.client.config.RenderDebugManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Scrollable block registry table for Minecraft 26.3 built on {@link ContainerObjectSelectionList}
 * and rendered through {@link GuiGraphicsExtractor}.
 *
 * <p>Displays every non-air block in {@link BuiltInRegistries#BLOCK} with its 16x16 item icon,
 * localized display name, registry {@link Identifier}, and an interactive tri-state button cycling
 * {@code DEFAULT -> WHITELIST -> BLACKLIST}.
 */
public final class BlockFilterListWidget extends ContainerObjectSelectionList<BlockFilterListWidget.BlockEntry> {
	public enum CategoryTab {
		ALL,
		ORES,
		TERRAIN,
		CONFIGURED
	}

	private static final Set<String> PRESET_TERRAIN_IDS = new HashSet<>(RenderDebugManager.DEFAULT_BLACKLIST_TERRAIN);
	private static final Set<String> PRESET_ORE_IDS = new HashSet<>(RenderDebugManager.DEFAULT_WHITELIST_ORES);

	private final List<BlockMetadata> allBlocks;
	private final Runnable onRuleChanged;
	private String searchQuery = "";
	private CategoryTab activeTab = CategoryTab.ALL;

	public BlockFilterListWidget(
			Minecraft minecraft,
			int width,
			int height,
			int y,
			int itemHeight,
			Runnable onRuleChanged
	) {
		super(minecraft, width, height, y, itemHeight);
		this.onRuleChanged = onRuleChanged;
		this.allBlocks = collectSortableBlocks();
		refreshEntries();
	}

	private static List<BlockMetadata> collectSortableBlocks() {
		List<BlockMetadata> list = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (block instanceof AirBlock) {
				continue;
			}
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			String idString = id.toString();
			String displayName = block.getName().getString();
			boolean oreLike = isOreOrValuable(id, idString);
			boolean terrainLike = isTerrainBlock(id, idString);
			Item item = block.asItem();
			ItemStack stack = item != Items.AIR ? new ItemStack(item) : ItemStack.EMPTY;
			list.add(new BlockMetadata(
					block,
					idString,
					displayName,
					idString.toLowerCase(Locale.ROOT),
					displayName.toLowerCase(Locale.ROOT),
					oreLike,
					terrainLike,
					stack
			));
		}

		// Sort configured/ore blocks near the top when names tie, otherwise alphabetically by ID path
		list.sort(Comparator.comparing(BlockMetadata::idString));
		return list;
	}

	private static boolean isOreOrValuable(Identifier id, String fullId) {
		if (PRESET_ORE_IDS.contains(fullId)) {
			return true;
		}
		String path = id.getPath();
		return path.endsWith("_ore")
				|| path.contains("ancient_debris")
				|| path.startsWith("raw_")
				|| path.contains("amethyst");
	}

	private static boolean isTerrainBlock(Identifier id, String fullId) {
		if (PRESET_TERRAIN_IDS.contains(fullId)) {
			return true;
		}
		String path = id.getPath();
		return path.equals("lava")
				|| path.endsWith("_dirt")
				|| path.endsWith("_sand")
				|| path.endsWith("_stone")
				|| path.contains("terracotta");
	}

	public void setFilterCriteria(String query, CategoryTab tab) {
		String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (!this.searchQuery.equals(normalized) || this.activeTab != tab) {
			this.searchQuery = normalized;
			this.activeTab = tab;
			refreshEntries();
			this.setScrollAmount(0.0D);
		}
	}

	public CategoryTab getActiveTab() {
		return this.activeTab;
	}

	public int countTotalBlocks() {
		return this.allBlocks.size();
	}

	public int countOreBlocks() {
		int count = 0;
		for (BlockMetadata meta : this.allBlocks) {
			if (meta.oreLike()) {
				count++;
			}
		}
		return count;
	}

	public int countTerrainBlocks() {
		int count = 0;
		for (BlockMetadata meta : this.allBlocks) {
			if (meta.terrainLike()) {
				count++;
			}
		}
		return count;
	}

	public void refreshEntries() {
		double previousScroll = this.scrollAmount();
		this.clearEntries();

		RenderDebugManager manager = RenderDebugManager.getInstance();
		for (BlockMetadata meta : this.allBlocks) {
			if (!matchesTab(meta, manager)) {
				continue;
			}
			if (!this.searchQuery.isEmpty()
					&& !meta.lowerId().contains(this.searchQuery)
					&& !meta.lowerName().contains(this.searchQuery)) {
				continue;
			}
			this.addEntry(new BlockEntry(meta, this.onRuleChanged));
		}

		this.setScrollAmount(previousScroll);
	}

	private boolean matchesTab(BlockMetadata meta, RenderDebugManager manager) {
		return switch (this.activeTab) {
			case ALL -> true;
			case ORES -> meta.oreLike();
			case TERRAIN -> meta.terrainLike();
			case CONFIGURED -> manager.getBlockStatus(meta.block()) != BlockFilterStatus.DEFAULT;
		};
	}

	@Override
	public int getRowWidth() {
		return Math.min(460, this.width - 28);
	}

	@Override
	protected int scrollBarX() {
		return (this.width + this.getRowWidth()) / 2 + 6;
	}

	/**
	 * Overrides the opaque/blurred menu list background with a clean translucent viewport panel
	 * so live chunk rendering changes remain visible behind the list in-game.
	 */
	@Override
	protected void extractListBackground(GuiGraphicsExtractor graphics) {
		graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), 0x700B1018);
	}

	private record BlockMetadata(
			Block block,
			String idString,
			String displayName,
			String lowerId,
			String lowerName,
			boolean oreLike,
			boolean terrainLike,
			ItemStack iconStack
	) {
	}

	public static final class BlockEntry extends ContainerObjectSelectionList.Entry<BlockEntry> {
		private static final int BUTTON_WIDTH = 98;
		private static final int BUTTON_HEIGHT = 18;

		private final BlockMetadata metadata;
		private final Button statusButton;

		BlockEntry(BlockMetadata metadata, Runnable onRuleChanged) {
			this.metadata = metadata;
			this.statusButton = Button.builder(
							buildButtonLabel(RenderDebugManager.getInstance().getBlockStatus(metadata.block())),
							button -> {
								BlockFilterStatus updated = RenderDebugManager.getInstance().cycleBlockStatus(metadata.block());
								button.setMessage(buildButtonLabel(updated));
								if (onRuleChanged != null) {
									onRuleChanged.run();
								}
							}
					)
					.bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
					.tooltip(Tooltip.create(Component.literal(
							"Click to cycle rule for " + metadata.idString() + "\nDEFAULT -> WHITELIST -> BLACKLIST"
					)))
					.build();
		}

		public void syncStatusLabel() {
			BlockFilterStatus current = RenderDebugManager.getInstance().getBlockStatus(this.metadata.block());
			this.statusButton.setMessage(buildButtonLabel(current));
		}

		private static Component buildButtonLabel(BlockFilterStatus status) {
			return switch (status) {
				case DEFAULT -> Component.translatable("screen.renderdebug.status.default")
						.withStyle(ChatFormatting.GRAY);
				case WHITELISTED -> Component.translatable("screen.renderdebug.status.whitelisted")
						.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
				case BLACKLISTED -> Component.translatable("screen.renderdebug.status.blacklisted")
						.withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
			};
		}

		@Override
		public void extractContent(
				GuiGraphicsExtractor graphics,
				int mouseX,
				int mouseY,
				boolean isHovered,
				float tickDelta
		) {
			int x = this.getContentX();
			int y = this.getContentY();
			int entryWidth = this.getContentWidth();
			int entryHeight = this.getContentHeight();

			BlockFilterStatus status = RenderDebugManager.getInstance().getBlockStatus(this.metadata.block());

			// Subtle row tinting based on rule state & hover (Minecraft 26.3 requires full ARGB colors)
			int rowBg = switch (status) {
				case WHITELISTED -> isHovered ? 0x3810B981 : 0x2210B981;
				case BLACKLISTED -> isHovered ? 0x38EF4444 : 0x22EF4444;
				case DEFAULT -> isHovered ? 0x28FFFFFF : 0x140F172A;
			};
			graphics.fill(x, y, x + entryWidth, y + entryHeight, rowBg);

			// Left status accent bar
			int accentColor = switch (status) {
				case WHITELISTED -> 0xFF34D399;
				case BLACKLISTED -> 0xFFF87171;
				case DEFAULT -> 0xFF334155;
			};
			graphics.fill(x, y, x + 2, y + entryHeight, accentColor);

			// 16x16 Block icon (or fallback swatch for fluid-only blocks like water/lava)
			int iconX = x + 6;
			int iconY = y + (entryHeight - 16) / 2;
			if (!this.metadata.iconStack().isEmpty()) {
				graphics.fakeItem(this.metadata.iconStack(), iconX, iconY);
			} else {
				int fallbackColor = this.metadata.idString().contains("lava") ? 0xFFEA580C : 0xFF2563EB;
				graphics.fill(iconX + 1, iconY + 1, iconX + 15, iconY + 15, fallbackColor);
				graphics.outline(iconX + 1, iconY + 1, 14, 14, 0x99FFFFFF);
			}

			// Localized block name + registry Identifier
			Font font = Minecraft.getInstance().font;
			int textX = iconX + 22;
			graphics.text(font, this.metadata.displayName(), textX, y + 2, 0xFFF8FAFC, true);
			graphics.text(font, this.metadata.idString(), textX, y + 12, 0xFF94A3B8, false);

			// Right-aligned tri-state rule button
			this.statusButton.setX(x + entryWidth - BUTTON_WIDTH - 4);
			this.statusButton.setY(y + (entryHeight - BUTTON_HEIGHT) / 2);
			this.statusButton.extractRenderState(graphics, mouseX, mouseY, tickDelta);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(this.statusButton);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(this.statusButton);
		}
	}
}
