package dev.shii.renderdebug.client.gui;

import dev.shii.renderdebug.client.config.FilterMode;
import dev.shii.renderdebug.client.config.OccludedRenderStyle;
import dev.shii.renderdebug.client.config.RenderDebugManager;
import dev.shii.renderdebug.client.input.ModKeybindings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Interactive Render Pipeline & Occlusion Debugger {@link Screen} for Minecraft 26.3.
 *
 * <h2>Live Render-Pipeline Inspection Design</h2>
 * <ul>
 *   <li>{@link #isPauseScreen()} returns {@code false} so background {@code SectionCompiler} worker
 *       threads keep compiling chunk meshes while the screen is open.</li>
 *   <li>{@link #extractBackground} draws a light translucent vignette without invoking
 *       {@code super.extractBackground}, preventing Minecraft 26.3's menu Gaussian blur shader from
 *       obscuring the live 3D world behind the debugger.</li>
 *   <li>The Ghost Alpha slider previews alpha values live while dragging and commits a single
 *       {@code LevelExtractor#allChanged()} chunk rebuild on {@code onRelease(MouseButtonEvent)}.</li>
 * </ul>
 */
public final class RenderDebugScreen extends Screen {
	private final @Nullable Screen parent;

	private Button masterFilterButton;
	private Button fullbrightButton;
	private Button filterModeButton;
	private Button renderStyleButton;
	private GhostAlphaSlider alphaSlider;
	private EditBox searchBox;
	private Button tabAllButton;
	private Button tabOresButton;
	private Button tabTerrainButton;
	private Button tabConfiguredButton;
	private BlockFilterListWidget blockList;

	private BlockFilterListWidget.CategoryTab selectedTab = BlockFilterListWidget.CategoryTab.ALL;
	private String savedSearchText = "";

	public RenderDebugScreen(@Nullable Screen parent) {
		super(Component.translatable("screen.renderdebug.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		RenderDebugManager manager = RenderDebugManager.getInstance();

		int panelWidth = Math.min(460, this.width - 20);
		int leftX = (this.width - panelWidth) / 2;
		int gap = 4;
		int halfWidth = (panelWidth - gap) / 2;
		int thirdWidth = (panelWidth - gap * 2) / 3;
		int quarterWidth = (panelWidth - gap * 3) / 4;

		// Row 1 (Y = 34): Master Visibility Filter Toggle + Fullbright Ambient Lighting Toggle
		int row1Y = 34;
		this.masterFilterButton = this.addRenderableWidget(
				Button.builder(buildMasterFilterLabel(manager.isFilterActive()), button -> {
							boolean active = manager.toggleFilterActive();
							button.setMessage(buildMasterFilterLabel(active));
						})
						.bounds(leftX, row1Y, halfWidth, 20)
						.tooltip(Tooltip.create(Component.literal(
								"Master switch for SectionCompiler, VisGraph, and Block.shouldRenderFace overrides."
						)))
						.build()
		);

		this.fullbrightButton = this.addRenderableWidget(
				Button.builder(buildFullbrightLabel(manager.isFullbrightEnabled()), button -> {
							boolean enabled = manager.toggleFullbright();
							button.setMessage(buildFullbrightLabel(enabled));
						})
						.bounds(leftX + halfWidth + gap, row1Y, halfWidth, 20)
						.tooltip(Tooltip.create(Component.literal(
								"Overrides LightmapRenderStateExtractor ambient lighting to pure white (1.0, 1.0, 1.0) without modifying options.gamma()."
						)))
						.build()
		);

		// Row 2 (Y = 58): Filter Policy (WHITELIST/BLACKLIST) + Occluded Style (INVISIBLE/TRANSLUCENT) + Ghost Alpha Slider
		int row2Y = 58;
		this.filterModeButton = this.addRenderableWidget(
				Button.builder(buildFilterModeLabel(manager.getFilterMode()), button -> {
							FilterMode mode = manager.cycleFilterMode();
							button.setMessage(buildFilterModeLabel(mode));
						})
						.bounds(leftX, row2Y, thirdWidth, 20)
						.tooltip(Tooltip.create(Component.literal(
								"WHITELIST: Only whitelisted blocks render normally.\nBLACKLIST: Only blacklisted blocks are occluded."
						)))
						.build()
		);

		this.renderStyleButton = this.addRenderableWidget(
				Button.builder(buildRenderStyleLabel(manager.getOccludedRenderStyle()), button -> {
							OccludedRenderStyle style = manager.cycleOccludedRenderStyle();
							button.setMessage(buildRenderStyleLabel(style));
							this.alphaSlider.active = (style == OccludedRenderStyle.TRANSLUCENT);
						})
						.bounds(leftX + thirdWidth + gap, row2Y, thirdWidth, 20)
						.tooltip(Tooltip.create(Component.literal(
								"INVISIBLE: Skips tessellation (0 quads).\nTRANSLUCENT: Reroutes geometry to ChunkSectionLayer.TRANSLUCENT with custom vertex alpha."
						)))
						.build()
		);

		int sliderWidth = panelWidth - (thirdWidth + gap) * 2;
		this.alphaSlider = this.addRenderableWidget(
				new GhostAlphaSlider(leftX + (thirdWidth + gap) * 2, row2Y, sliderWidth, 20, manager.getTranslucentAlpha())
		);
		this.alphaSlider.active = (manager.getOccludedRenderStyle() == OccludedRenderStyle.TRANSLUCENT);

		// Row 3 (Y = 82): Search EditBox
		int row3Y = 82;
		this.searchBox = new EditBox(
				this.font,
				leftX,
				row3Y,
				panelWidth,
				18,
				Component.translatable("screen.renderdebug.search_hint")
		);
		this.searchBox.setMaxLength(128);
		this.searchBox.setHint(Component.translatable("screen.renderdebug.search_hint").withStyle(ChatFormatting.DARK_GRAY));
		this.searchBox.setValue(this.savedSearchText);
		this.searchBox.setResponder(query -> {
			this.savedSearchText = query;
			if (this.blockList != null) {
				this.blockList.setFilterCriteria(query, this.selectedTab);
			}
		});
		this.addRenderableWidget(this.searchBox);

		// Scrollable Block Registry List (created before tabs so tab counts are populated immediately)
		int listTop = 126;
		int listBottom = this.height - 32;
		int listHeight = Math.max(48, listBottom - listTop);
		this.blockList = new BlockFilterListWidget(
				this.minecraft,
				this.width,
				listHeight,
				listTop,
				24,
				this::refreshTabLabels
		);
		this.blockList.setFilterCriteria(this.savedSearchText, this.selectedTab);

		// Row 4 (Y = 104): Category Tabs (All / Ores / Terrain / Configured)
		int row4Y = 104;
		this.tabAllButton = this.addRenderableWidget(
				Button.builder(Component.empty(), _ -> selectTab(BlockFilterListWidget.CategoryTab.ALL))
						.bounds(leftX, row4Y, quarterWidth, 18)
						.build()
		);
		this.tabOresButton = this.addRenderableWidget(
				Button.builder(Component.empty(), _ -> selectTab(BlockFilterListWidget.CategoryTab.ORES))
						.bounds(leftX + (quarterWidth + gap), row4Y, quarterWidth, 18)
						.build()
		);
		this.tabTerrainButton = this.addRenderableWidget(
				Button.builder(Component.empty(), _ -> selectTab(BlockFilterListWidget.CategoryTab.TERRAIN))
						.bounds(leftX + (quarterWidth + gap) * 2, row4Y, quarterWidth, 18)
						.build()
		);
		int lastTabWidth = panelWidth - (quarterWidth + gap) * 3;
		this.tabConfiguredButton = this.addRenderableWidget(
				Button.builder(Component.empty(), _ -> selectTab(BlockFilterListWidget.CategoryTab.CONFIGURED))
						.bounds(leftX + (quarterWidth + gap) * 3, row4Y, lastTabWidth, 18)
						.build()
		);

		this.addRenderableWidget(this.blockList);
		refreshTabLabels();

		// Footer Row (Y = height - 26): Presets, Clear Rules, Rebuild Chunks, Done
		int footerY = this.height - 26;
		this.addRenderableWidget(
				Button.builder(Component.translatable("screen.renderdebug.preset_ores"), _ -> {
							manager.resetToDefaultPresets();
							this.blockList.refreshEntries();
							refreshTabLabels();
						})
						.bounds(leftX, footerY, quarterWidth, 20)
						.tooltip(Tooltip.create(Component.literal(
								"Restores default ore whitelist and common terrain blacklist rules."
						)))
						.build()
		);

		this.addRenderableWidget(
				Button.builder(Component.translatable("screen.renderdebug.clear_rules"), _ -> {
							manager.clearAllBlockRules();
							this.blockList.refreshEntries();
							refreshTabLabels();
						})
						.bounds(leftX + (quarterWidth + gap), footerY, quarterWidth, 20)
						.build()
		);

		this.addRenderableWidget(
				Button.builder(Component.translatable("screen.renderdebug.reload_chunks"), _ -> manager.triggerChunkReload())
						.bounds(leftX + (quarterWidth + gap) * 2, footerY, quarterWidth, 20)
						.tooltip(Tooltip.create(Component.literal(
								"Schedules Minecraft 26.3 LevelExtractor#allChanged() across all loaded sections."
						)))
						.build()
		);

		this.addRenderableWidget(
				Button.builder(CommonComponents.GUI_DONE, _ -> this.onClose())
						.bounds(leftX + (quarterWidth + gap) * 3, footerY, lastTabWidth, 20)
						.build()
		);
	}

	private void selectTab(BlockFilterListWidget.CategoryTab tab) {
		this.selectedTab = tab;
		if (this.blockList != null) {
			this.blockList.setFilterCriteria(this.savedSearchText, tab);
		}
		refreshTabLabels();
	}

	private void refreshTabLabels() {
		if (this.blockList == null) {
			return;
		}
		int configuredCount = RenderDebugManager.getInstance().getConfiguredBlockCount();
		this.tabAllButton.setMessage(styleTabLabel(
				Component.translatable("screen.renderdebug.tab_all", this.blockList.countTotalBlocks()),
				this.selectedTab == BlockFilterListWidget.CategoryTab.ALL
		));
		this.tabOresButton.setMessage(styleTabLabel(
				Component.translatable("screen.renderdebug.tab_ores", this.blockList.countOreBlocks()),
				this.selectedTab == BlockFilterListWidget.CategoryTab.ORES
		));
		this.tabTerrainButton.setMessage(styleTabLabel(
				Component.translatable("screen.renderdebug.tab_terrain", this.blockList.countTerrainBlocks()),
				this.selectedTab == BlockFilterListWidget.CategoryTab.TERRAIN
		));
		this.tabConfiguredButton.setMessage(styleTabLabel(
				Component.translatable("screen.renderdebug.tab_configured", configuredCount),
				this.selectedTab == BlockFilterListWidget.CategoryTab.CONFIGURED
		));
	}

	private static Component styleTabLabel(Component base, boolean active) {
		return active
				? Component.empty().append(base).withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
				: Component.empty().append(base).withStyle(ChatFormatting.GRAY);
	}

	private static Component buildMasterFilterLabel(boolean active) {
		return active
				? Component.translatable("screen.renderdebug.filter_on").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
				: Component.translatable("screen.renderdebug.filter_off").withStyle(ChatFormatting.RED);
	}

	private static Component buildFullbrightLabel(boolean enabled) {
		return enabled
				? Component.translatable("screen.renderdebug.fullbright_on").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
				: Component.translatable("screen.renderdebug.fullbright_off").withStyle(ChatFormatting.GRAY);
	}

	private static Component buildFilterModeLabel(FilterMode mode) {
		return mode == FilterMode.WHITELIST
				? Component.translatable("screen.renderdebug.mode_whitelist").withStyle(ChatFormatting.GOLD)
				: Component.translatable("screen.renderdebug.mode_blacklist").withStyle(ChatFormatting.LIGHT_PURPLE);
	}

	private static Component buildRenderStyleLabel(OccludedRenderStyle style) {
		return style == OccludedRenderStyle.INVISIBLE
				? Component.translatable("screen.renderdebug.style_invisible").withStyle(ChatFormatting.YELLOW)
				: Component.translatable("screen.renderdebug.style_translucent").withStyle(ChatFormatting.AQUA);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// Deliberately bypass super.extractBackground() so Minecraft 26.3 does not apply Gaussian blur
		// over the live chunk geometry we are inspecting in real time.
		graphics.fillGradient(0, 0, this.width, this.height, 0x68090D16, 0x8C090D16);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		// Top header bar & pipeline status summary (ARGB colors required by GuiGraphicsExtractor)
		graphics.fill(0, 0, this.width, 30, 0xCC0B1120);
		graphics.fill(0, 29, this.width, 30, 0xFF1E293B);

		graphics.centeredText(this.font, this.title, this.width / 2, 6, 0xFFF8FAFC);

		RenderDebugManager manager = RenderDebugManager.getInstance();
		String telemetry = String.format(
				"Pipeline: %s | Mode: %s | Occluded Pass: %s | Lightmap: %s",
				manager.isFilterActive() ? "OVERRIDE ACTIVE" : "VANILLA",
				manager.getFilterMode().name(),
				manager.getOccludedRenderStyle() == OccludedRenderStyle.TRANSLUCENT
						? "TRANSLUCENT (" + manager.getTranslucentAlpha() + "/255)"
						: "CULLED (0 QUADS)",
				manager.isFullbrightEnabled() ? "FULLBRIGHT (1.0)" : "ENVIRONMENT"
		);
		graphics.centeredText(this.font, telemetry, this.width / 2, 18, 0xFF94A3B8);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!this.searchBox.canConsumeInput() && ModKeybindings.matchesOpenGuiKey(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		RenderDebugManager.getInstance().saveConfig();
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(this.parent);
		}
	}

	/**
	 * Slider controlling the translucent vertex alpha (20..240) injected by {@code BufferBuilderAlphaMixin}.
	 * Defers {@code LevelExtractor#allChanged()} chunk rebuild until {@link #onRelease(MouseButtonEvent)}
	 * so dragging remains smooth at high render distances.
	 */
	private static final class GhostAlphaSlider extends AbstractSliderButton {
		private static final int MIN_ALPHA = 20;
		private static final int MAX_ALPHA = 240;

		GhostAlphaSlider(int x, int y, int width, int height, int currentAlpha) {
			super(x, y, width, height, Component.empty(), toNormalized(currentAlpha));
			this.updateMessage();
		}

		private static double toNormalized(int alpha) {
			return Mth.clamp((double) (alpha - MIN_ALPHA) / (double) (MAX_ALPHA - MIN_ALPHA), 0.0D, 1.0D);
		}

		private int toAlphaByte() {
			return Mth.clamp((int) Math.round(MIN_ALPHA + this.value * (MAX_ALPHA - MIN_ALPHA)), MIN_ALPHA, MAX_ALPHA);
		}

		@Override
		protected void updateMessage() {
			int alpha = toAlphaByte();
			int percent = Math.round((alpha / 255.0F) * 100.0F);
			this.setMessage(Component.translatable("screen.renderdebug.alpha_slider", percent, alpha));
		}

		@Override
		protected void applyValue() {
			RenderDebugManager.getInstance().previewTranslucentAlpha(toAlphaByte());
		}

		@Override
		public void onRelease(MouseButtonEvent event) {
			super.onRelease(event);
			RenderDebugManager.getInstance().commitTranslucentAlphaChange();
		}
	}
}
