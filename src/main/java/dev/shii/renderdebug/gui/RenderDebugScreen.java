package dev.shii.renderdebug.gui;

import dev.shii.renderdebug.config.OccludedRenderStyle;
import dev.shii.renderdebug.config.RenderDebugManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Interactive visual debugging GUI opened via Right Shift ({@code GLFW_KEY_RIGHT_SHIFT}).
 *
 * <p>Provides live controls for:
 * <ul>
 *   <li>Master Block Visibility Filter activation/deactivation</li>
 *   <li>Fullbright ambient lighting via {@code LightmapTextureManager}</li>
 *   <li>Filter evaluation mode ({@code Blacklist Hide} vs {@code Whitelist Only})</li>
 *   <li>Occluded block render pass ({@code Invisible} mesh skip vs {@code Translucent} alpha pass + opacity slider)</li>
 *   <li>Searchable, categorized scrollable list of all registered {@code Block}s</li>
 * </ul>
 *
 * <p>Note: {@link #renderBackground} intentionally uses {@link #renderInGameBackground}
 * without post-process screen blur and {@link #shouldPause()} returns {@code false},
 * allowing developers to observe chunk re-tessellation and lighting updates live in
 * the 3D viewport behind the interface.
 */
public class RenderDebugScreen extends Screen {
	private static final int CONTENT_WIDTH = 396;
	private static final int LIST_TOP = 96;
	private static final int FOOTER_OFFSET = 28;
	private static final int ROW_HEIGHT = 24;

	private final Screen parent;

	private ButtonWidget masterToggleBtn;
	private ButtonWidget fullbrightBtn;
	private ButtonWidget filterModeBtn;
	private ButtonWidget occludedStyleBtn;
	private OpacitySlider alphaSlider;
	private TextFieldWidget searchBox;
	private BlockFilterListWidget blockList;

	private final ButtonWidget[] categoryTabButtons = new ButtonWidget[BlockFilterListWidget.CategoryTab.values().length];
	private BlockFilterListWidget.CategoryTab activeTab = BlockFilterListWidget.CategoryTab.ORES;
	private String currentSearch = "";

	public RenderDebugScreen(Screen parent) {
		super(Text.translatable("screen.renderdebug.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		RenderDebugManager manager = RenderDebugManager.getInstance();
		int panelWidth = Math.min(CONTENT_WIDTH, this.width - 16);
		int left = (this.width - panelWidth) / 2;

		// =====================================================================
		// Row 1 (y = 26): Master Filter Toggle | Fullbright Toggle | Filter Mode
		// =====================================================================
		int topColWidth = (panelWidth - 8) / 3;

		this.masterToggleBtn = this.addDrawableChild(ButtonWidget.builder(getMasterToggleText(), btn -> {
			manager.toggleFilter();
			updateControlLabels();
		})
				.dimensions(left, 26, topColWidth, 20)
				.tooltip(Tooltip.of(Text.literal("Activates/deactivates custom block occlusion & chunk mesh filtering.")))
				.build());

		this.fullbrightBtn = this.addDrawableChild(ButtonWidget.builder(getFullbrightText(), btn -> {
			manager.toggleFullbright();
			updateControlLabels();
		})
				.dimensions(left + topColWidth + 4, 26, topColWidth, 20)
				.tooltip(Tooltip.of(Text.literal("Overrides LightmapTextureManager ambient factor to 1.0 (Fullbright).")))
				.build());

		this.filterModeBtn = this.addDrawableChild(ButtonWidget.builder(getFilterModeText(), btn -> {
			manager.toggleFilterMode();
			updateControlLabels();
		})
				.dimensions(left + (topColWidth + 4) * 2, 26, panelWidth - (topColWidth + 4) * 2, 20)
				.tooltip(Tooltip.of(Text.literal(
						"Blacklist Hide: hides Blacklisted blocks, shows all others.\n"
								+ "Whitelist Only: shows ONLY Whitelisted blocks."
				)))
				.build());

		// =====================================================================
		// Row 2 (y = 50): Occluded Render Style | Opacity Slider | Search Input
		// =====================================================================
		int styleWidth = Math.max(116, panelWidth / 3);
		int sliderWidth = Math.max(96, panelWidth / 4);
		int searchWidth = panelWidth - styleWidth - sliderWidth - 8;

		this.occludedStyleBtn = this.addDrawableChild(ButtonWidget.builder(getOccludedStyleText(), btn -> {
			manager.toggleOccludedStyle();
			updateControlLabels();
		})
				.dimensions(left, 50, styleWidth, 20)
				.tooltip(Tooltip.of(Text.literal(
						"Invisible: skips block tessellation completely.\n"
								+ "Translucent: routes occluded shells to RenderLayer.getTranslucent()."
				)))
				.build());

		this.alphaSlider = this.addDrawableChild(new OpacitySlider(
				left + styleWidth + 4,
				50,
				sliderWidth,
				20,
				manager.getTranslucentAlpha()
		));

		this.searchBox = new TextFieldWidget(
				this.textRenderer,
				left + styleWidth + sliderWidth + 8,
				50,
				searchWidth,
				20,
				Text.literal("Search blocks")
		);
		this.searchBox.setPlaceholder(Text.literal("Search block or ID...").formatted(Formatting.DARK_GRAY));
		this.searchBox.setText(this.currentSearch);
		this.searchBox.setChangedListener(query -> {
			this.currentSearch = query;
			if (this.blockList != null) {
				this.blockList.refreshEntries(this.currentSearch, this.activeTab);
			}
		});
		this.addDrawableChild(this.searchBox);

		// =====================================================================
		// Row 3 (y = 74): Quick Category Filter Tabs
		// =====================================================================
		BlockFilterListWidget.CategoryTab[] tabs = BlockFilterListWidget.CategoryTab.values();
		int tabGap = 3;
		int tabWidth = (panelWidth - tabGap * (tabs.length - 1)) / tabs.length;

		for (int i = 0; i < tabs.length; i++) {
			final BlockFilterListWidget.CategoryTab tab = tabs[i];
			int tabX = left + i * (tabWidth + tabGap);
			int currentTabW = (i == tabs.length - 1) ? (left + panelWidth - tabX) : tabWidth;

			this.categoryTabButtons[i] = this.addDrawableChild(ButtonWidget.builder(getTabText(tab), btn -> {
				this.activeTab = tab;
				updateControlLabels();
				this.blockList.refreshEntries(this.currentSearch, this.activeTab);
			}).dimensions(tabX, 74, currentTabW, 18).build());
		}

		// =====================================================================
		// Center: Scrollable Block Filter List
		// =====================================================================
		int listHeight = Math.max(48, this.height - LIST_TOP - FOOTER_OFFSET - 4);
		this.blockList = this.addDrawableChild(new BlockFilterListWidget(
				this.client,
				this.width,
				listHeight,
				LIST_TOP,
				ROW_HEIGHT,
				this::onBlockRuleChanged
		));
		this.blockList.refreshEntries(this.currentSearch, this.activeTab);

		// =====================================================================
		// Footer: Reset Presets | Rebuild Chunks | Done
		// =====================================================================
		int footerY = this.height - 24;
		int footerBtnW = (panelWidth - 8) / 3;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset Presets"), btn -> {
			manager.resetToDefaults();
			updateControlLabels();
			this.blockList.refreshEntries(this.currentSearch, this.activeTab);
		})
				.dimensions(left, footerY, footerBtnW, 20)
				.tooltip(Tooltip.of(Text.literal("Restores default ore whitelist and terrain blacklist rules.")))
				.build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Rebuild Chunks"), btn -> manager.reloadWorldChunks())
				.dimensions(left + footerBtnW + 4, footerY, footerBtnW, 20)
				.tooltip(Tooltip.of(Text.literal("Forces WorldRenderer#reload() to re-tessellate all chunk sections.")))
				.build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> this.close())
				.dimensions(left + (footerBtnW + 4) * 2, footerY, panelWidth - (footerBtnW + 4) * 2, 20)
				.build());

		updateControlLabels();
	}

	private void onBlockRuleChanged() {
		updateControlLabels();
		if (this.activeTab == BlockFilterListWidget.CategoryTab.WHITELISTED
				|| this.activeTab == BlockFilterListWidget.CategoryTab.BLACKLISTED) {
			this.blockList.refreshEntries(this.currentSearch, this.activeTab);
		}
	}

	private void updateControlLabels() {
		RenderDebugManager manager = RenderDebugManager.getInstance();
		if (this.masterToggleBtn != null) {
			this.masterToggleBtn.setMessage(getMasterToggleText());
		}
		if (this.fullbrightBtn != null) {
			this.fullbrightBtn.setMessage(getFullbrightText());
		}
		if (this.filterModeBtn != null) {
			this.filterModeBtn.setMessage(getFilterModeText());
		}
		if (this.occludedStyleBtn != null) {
			this.occludedStyleBtn.setMessage(getOccludedStyleText());
		}
		if (this.alphaSlider != null) {
			this.alphaSlider.active = manager.getOccludedStyle() == OccludedRenderStyle.TRANSLUCENT;
		}
		BlockFilterListWidget.CategoryTab[] tabs = BlockFilterListWidget.CategoryTab.values();
		for (int i = 0; i < tabs.length; i++) {
			if (this.categoryTabButtons[i] != null) {
				this.categoryTabButtons[i].setMessage(getTabText(tabs[i]));
			}
		}
	}

	private static Text getMasterToggleText() {
		boolean on = RenderDebugManager.getInstance().isFilterEnabled();
		return Text.literal("Filter: ")
				.append(Text.literal(on ? "ON" : "OFF")
						.formatted(on ? Formatting.GREEN : Formatting.RED, Formatting.BOLD));
	}

	private static Text getFullbrightText() {
		boolean on = RenderDebugManager.getInstance().isFullbrightEnabled();
		return Text.literal("Fullbright: ")
				.append(Text.literal(on ? "ON" : "OFF")
						.formatted(on ? Formatting.GOLD : Formatting.GRAY, Formatting.BOLD));
	}

	private static Text getFilterModeText() {
		return Text.literal("Mode: ")
				.append(Text.literal(RenderDebugManager.getInstance().getFilterMode().getLabel())
						.formatted(Formatting.AQUA));
	}

	private static Text getOccludedStyleText() {
		OccludedRenderStyle style = RenderDebugManager.getInstance().getOccludedStyle();
		String shortLabel = style == OccludedRenderStyle.INVISIBLE ? "Invisible" : "Translucent";
		return Text.literal("Occluded: ")
				.append(Text.literal(shortLabel)
						.formatted(style == OccludedRenderStyle.TRANSLUCENT ? Formatting.LIGHT_PURPLE : Formatting.YELLOW));
	}

	private Text getTabText(BlockFilterListWidget.CategoryTab tab) {
		RenderDebugManager manager = RenderDebugManager.getInstance();
		String label = switch (tab) {
			case WHITELISTED -> tab.getLabel() + " (" + manager.getWhitelistedCount() + ")";
			case BLACKLISTED -> tab.getLabel() + " (" + manager.getBlacklistedCount() + ")";
			default -> tab.getLabel();
		};
		if (tab == this.activeTab) {
			return Text.literal(label).formatted(Formatting.YELLOW, Formatting.UNDERLINE);
		}
		return Text.literal(label).formatted(Formatting.GRAY);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// Use a semi-transparent gradient without screen blur so chunk updates remain visible behind the UI.
		this.renderInGameBackground(context);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		// Draw header title and live rule summary above Row 1
		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("Render Pipeline & Occlusion Debugger").formatted(Formatting.WHITE, Formatting.BOLD),
				this.width / 2,
				10,
				0xFFFFFFFF
		);
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}

	@Override
	public boolean shouldPause() {
		// Keep client chunk builder and render ticks running while inspecting the world.
		return false;
	}

	/**
	 * Slider controlling vertex alpha (6% - 100%) when {@link OccludedRenderStyle#TRANSLUCENT}
	 * is active. Defers full chunk reload until mouse release to avoid redundant remeshing.
	 */
	private static final class OpacitySlider extends SliderWidget {
		private static final double MIN_ALPHA = 0.06;
		private static final double MAX_ALPHA = 1.00;

		OpacitySlider(int x, int y, int width, int height, float currentAlpha) {
			super(x, y, width, height, Text.empty(), toNormalized(currentAlpha));
			this.updateMessage();
		}

		private static double toNormalized(float alpha) {
			return Math.clamp((alpha - MIN_ALPHA) / (MAX_ALPHA - MIN_ALPHA), 0.0, 1.0);
		}

		private float toAlpha() {
			return (float) (MIN_ALPHA + this.value * (MAX_ALPHA - MIN_ALPHA));
		}

		@Override
		protected void updateMessage() {
			int pct = Math.round(toAlpha() * 100.0f);
			this.setMessage(Text.literal("Alpha: " + pct + "%"));
		}

		@Override
		protected void applyValue() {
			RenderDebugManager.getInstance().setTranslucentAlphaLive(toAlpha());
		}

		@Override
		public void onRelease(double mouseX, double mouseY) {
			super.onRelease(mouseX, mouseY);
			RenderDebugManager.getInstance().saveAndReloadChunks();
		}
	}
}
