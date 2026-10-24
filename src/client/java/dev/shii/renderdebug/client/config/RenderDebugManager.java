package dev.shii.renderdebug.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.shii.renderdebug.client.RenderDebugClientMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Central state manager, lock-free chunk-compiler snapshot publisher, and JSON persistence layer
 * for the Render Pipeline & Occlusion Debugger (Minecraft 26.3).
 *
 * <h2>Concurrency Architecture</h2>
 * <p>Minecraft 26.3 compiles 16x16x16 chunk sections concurrently across background worker threads
 * via {@code SectionCompiler#compile}. Synchronizing per-block and per-face checks across millions
 * of faces would stall worker threads. Instead, all UI mutations publish an immutable
 * {@link FilterSnapshot} backed by {@link IdentityHashMap}-based reference sets to a single
 * {@code volatile} field, making worker-thread queries completely lock-free {@code O(1)} pointer
 * comparisons.
 */
public final class RenderDebugManager {
	private static final RenderDebugManager INSTANCE = new RenderDebugManager();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * Default whitelisted ore & inspection blocks pre-populated for immediate render-pipeline testing.
	 */
	public static final List<String> DEFAULT_WHITELIST_ORES = List.of(
			"minecraft:coal_ore",
			"minecraft:deepslate_coal_ore",
			"minecraft:iron_ore",
			"minecraft:deepslate_iron_ore",
			"minecraft:copper_ore",
			"minecraft:deepslate_copper_ore",
			"minecraft:gold_ore",
			"minecraft:deepslate_gold_ore",
			"minecraft:redstone_ore",
			"minecraft:deepslate_redstone_ore",
			"minecraft:emerald_ore",
			"minecraft:deepslate_emerald_ore",
			"minecraft:lapis_ore",
			"minecraft:deepslate_lapis_ore",
			"minecraft:diamond_ore",
			"minecraft:deepslate_diamond_ore",
			"minecraft:nether_gold_ore",
			"minecraft:nether_quartz_ore",
			"minecraft:ancient_debris",
			"minecraft:raw_iron_block",
			"minecraft:raw_copper_block",
			"minecraft:raw_gold_block",
			"minecraft:amethyst_cluster",
			"minecraft:budding_amethyst",
			"minecraft:spawner",
			"minecraft:trial_spawner",
			"minecraft:vault",
			"minecraft:chest",
			"minecraft:trapped_chest",
			"minecraft:ender_chest",
			"minecraft:barrel"
	);

	/**
	 * Default blacklisted common terrain/overburden blocks for selective terrain stripping in BLACKLIST mode.
	 */
	public static final List<String> DEFAULT_BLACKLIST_TERRAIN = List.of(
			"minecraft:stone",
			"minecraft:deepslate",
			"minecraft:granite",
			"minecraft:diorite",
			"minecraft:andesite",
			"minecraft:tuff",
			"minecraft:calcite",
			"minecraft:dripstone_block",
			"minecraft:dirt",
			"minecraft:coarse_dirt",
			"minecraft:rooted_dirt",
			"minecraft:grass_block",
			"minecraft:podzol",
			"minecraft:mycelium",
			"minecraft:mud",
			"minecraft:clay",
			"minecraft:gravel",
			"minecraft:sand",
			"minecraft:red_sand",
			"minecraft:sandstone",
			"minecraft:red_sandstone",
			"minecraft:netherrack",
			"minecraft:basalt",
			"minecraft:smooth_basalt",
			"minecraft:blackstone",
			"minecraft:soul_sand",
			"minecraft:soul_soil",
			"minecraft:end_stone",
			"minecraft:bedrock",
			"minecraft:water"
	);

	private final Path configPath;
	private final Set<String> whitelistedIds = new LinkedHashSet<>();
	private final Set<String> blacklistedIds = new LinkedHashSet<>();

	private boolean filterActive = false;
	private boolean fullbrightEnabled = false;
	private FilterMode filterMode = FilterMode.WHITELIST;
	private OccludedRenderStyle occludedRenderStyle = OccludedRenderStyle.INVISIBLE;
	private int translucentAlpha = 72;

	/**
	 * Lock-free immutable snapshot read on chunk-compile worker threads.
	 */
	private volatile FilterSnapshot snapshot = FilterSnapshot.EMPTY;

	private RenderDebugManager() {
		this.configPath = FabricLoader.getInstance().getConfigDir().resolve("renderdebug.json");
		applyDefaultPresetsInternal();
	}

	public static RenderDebugManager getInstance() {
		return INSTANCE;
	}

	// =========================================================================
	// Fast Lock-Free Queries (Called on Chunk SectionCompiler Worker Threads)
	// =========================================================================

	public boolean isFilterActive() {
		return this.snapshot.filterActive();
	}

	public boolean isFullbrightEnabled() {
		return this.snapshot.fullbrightEnabled();
	}

	public FilterMode getFilterMode() {
		return this.snapshot.filterMode();
	}

	public OccludedRenderStyle getOccludedRenderStyle() {
		return this.snapshot.occludedRenderStyle();
	}

	public int getTranslucentAlpha() {
		return this.snapshot.translucentAlpha();
	}

	/**
	 * Returns {@code true} if {@code state} should render with its normal vanilla geometry and layer,
	 * or {@code false} if the active visibility filter marks it as occluded (invisible or ghost-translucent).
	 */
	public boolean shouldRenderNormally(BlockState state) {
		if (state == null || state.isAir()) {
			return true;
		}
		FilterSnapshot current = this.snapshot;
		if (!current.filterActive()) {
			return true;
		}
		Block block = state.getBlock();
		return switch (current.filterMode()) {
			case WHITELIST -> current.whitelistedBlocks().contains(block);
			case BLACKLIST -> !current.blacklistedBlocks().contains(block);
		};
	}

	/**
	 * Returns {@code true} when {@code state} is occluded AND the current style is {@link OccludedRenderStyle#INVISIBLE},
	 * meaning {@code SectionCompiler} / {@code FluidRenderer} should emit zero vertices for this block.
	 */
	public boolean isBlockFullyHidden(BlockState state) {
		if (state == null || state.isAir()) {
			return false;
		}
		FilterSnapshot current = this.snapshot;
		return current.filterActive()
				&& current.occludedRenderStyle() == OccludedRenderStyle.INVISIBLE
				&& !shouldRenderNormallyWithSnapshot(current, state.getBlock());
	}

	/**
	 * Returns {@code true} when {@code state} is occluded AND the current style is {@link OccludedRenderStyle#TRANSLUCENT},
	 * meaning its quads should be routed to {@code ChunkSectionLayer.TRANSLUCENT} with {@link #getTranslucentAlpha()}.
	 */
	public boolean isBlockTranslucent(BlockState state) {
		if (state == null || state.isAir()) {
			return false;
		}
		FilterSnapshot current = this.snapshot;
		return current.filterActive()
				&& current.occludedRenderStyle() == OccludedRenderStyle.TRANSLUCENT
				&& !shouldRenderNormallyWithSnapshot(current, state.getBlock());
	}

	private static boolean shouldRenderNormallyWithSnapshot(FilterSnapshot current, Block block) {
		return switch (current.filterMode()) {
			case WHITELIST -> current.whitelistedBlocks().contains(block);
			case BLACKLIST -> !current.blacklistedBlocks().contains(block);
		};
	}

	// =========================================================================
	// UI State Mutations (Called on Render Thread)
	// =========================================================================

	public synchronized boolean toggleFilterActive() {
		this.filterActive = !this.filterActive;
		publishAndReload(true);
		return this.filterActive;
	}

	public synchronized void setFilterActive(boolean active) {
		if (this.filterActive != active) {
			this.filterActive = active;
			publishAndReload(true);
		}
	}

	public synchronized boolean toggleFullbright() {
		this.fullbrightEnabled = !this.fullbrightEnabled;
		// Fullbright updates the GPU lightmap state every frame via LightmapExtractorMixin,
		// but if the block visibility filter is off we do not even need a chunk rebuild!
		publishAndReload(false);
		return this.fullbrightEnabled;
	}

	public synchronized FilterMode cycleFilterMode() {
		this.filterMode = this.filterMode.toggle();
		publishAndReload(this.filterActive);
		return this.filterMode;
	}

	public synchronized OccludedRenderStyle cycleOccludedRenderStyle() {
		this.occludedRenderStyle = this.occludedRenderStyle.toggle();
		publishAndReload(this.filterActive);
		return this.occludedRenderStyle;
	}

	/**
	 * Updates the translucent alpha value (20..240) without immediately rebuilding all chunks
	 * while the user is dragging the slider; call {@link #commitTranslucentAlphaChange()} on mouse release.
	 */
	public synchronized void previewTranslucentAlpha(int alpha) {
		int clamped = Mth.clamp(alpha, 20, 240);
		if (this.translucentAlpha != clamped) {
			this.translucentAlpha = clamped;
			rebuildSnapshot();
		}
	}

	public synchronized void commitTranslucentAlphaChange() {
		saveConfig();
		if (this.filterActive && this.occludedRenderStyle == OccludedRenderStyle.TRANSLUCENT) {
			triggerChunkReload();
		}
	}

	public synchronized BlockFilterStatus getBlockStatus(Block block) {
		String id = BuiltInRegistries.BLOCK.getKey(block).toString();
		if (this.whitelistedIds.contains(id)) {
			return BlockFilterStatus.WHITELISTED;
		}
		if (this.blacklistedIds.contains(id)) {
			return BlockFilterStatus.BLACKLISTED;
		}
		return BlockFilterStatus.DEFAULT;
	}

	public synchronized BlockFilterStatus cycleBlockStatus(Block block) {
		BlockFilterStatus next = getBlockStatus(block).next();
		setBlockStatus(block, next);
		return next;
	}

	public synchronized void setBlockStatus(Block block, BlockFilterStatus status) {
		String id = BuiltInRegistries.BLOCK.getKey(block).toString();
		this.whitelistedIds.remove(id);
		this.blacklistedIds.remove(id);
		if (status == BlockFilterStatus.WHITELISTED) {
			this.whitelistedIds.add(id);
		} else if (status == BlockFilterStatus.BLACKLISTED) {
			this.blacklistedIds.add(id);
		}
		publishAndReload(this.filterActive);
	}

	public synchronized void resetToDefaultPresets() {
		applyDefaultPresetsInternal();
		publishAndReload(this.filterActive);
	}

	public synchronized void clearAllBlockRules() {
		this.whitelistedIds.clear();
		this.blacklistedIds.clear();
		publishAndReload(this.filterActive);
	}

	public synchronized int getConfiguredBlockCount() {
		return this.whitelistedIds.size() + this.blacklistedIds.size();
	}

	private void applyDefaultPresetsInternal() {
		this.whitelistedIds.clear();
		this.blacklistedIds.clear();
		this.whitelistedIds.addAll(DEFAULT_WHITELIST_ORES);
		this.blacklistedIds.addAll(DEFAULT_BLACKLIST_TERRAIN);
	}

	private void publishAndReload(boolean reloadChunks) {
		rebuildSnapshot();
		saveConfig();
		if (reloadChunks) {
			triggerChunkReload();
		}
	}

	/**
	 * Resolves configured {@link Identifier} strings against {@link BuiltInRegistries#BLOCK}
	 * into identity-backed {@link Set}s and publishes a new immutable {@link FilterSnapshot}.
	 */
	public synchronized void rebuildSnapshot() {
		Set<Block> whitelistSet = Collections.newSetFromMap(new IdentityHashMap<>());
		Set<Block> blacklistSet = Collections.newSetFromMap(new IdentityHashMap<>());

		for (String rawId : this.whitelistedIds) {
			Identifier id = Identifier.tryParse(rawId);
			if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
				whitelistSet.add(BuiltInRegistries.BLOCK.getValue(id));
			}
		}

		for (String rawId : this.blacklistedIds) {
			Identifier id = Identifier.tryParse(rawId);
			if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
				blacklistSet.add(BuiltInRegistries.BLOCK.getValue(id));
			}
		}

		this.snapshot = new FilterSnapshot(
				this.filterActive,
				this.fullbrightEnabled,
				this.filterMode,
				this.occludedRenderStyle,
				this.translucentAlpha,
				whitelistSet,
				blacklistSet
		);
	}

	/**
	 * Schedules an asynchronous rebuild of all compiled chunk sections via Minecraft 26.3's
	 * {@code LevelExtractor#allChanged()} on the main client render thread.
	 */
	public void triggerChunkReload() {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}
		client.execute(() -> {
			if (client.level != null && client.levelExtractor != null) {
				client.levelExtractor.allChanged();
			}
		});
	}

	// =========================================================================
	// JSON Configuration Persistence (config/renderdebug.json)
	// =========================================================================

	public synchronized void loadConfig() {
		if (!Files.exists(this.configPath)) {
			rebuildSnapshot();
			saveConfig();
			return;
		}

		try (Reader reader = Files.newBufferedReader(this.configPath, StandardCharsets.UTF_8)) {
			ConfigData data = GSON.fromJson(reader, ConfigData.class);
			if (data != null) {
				this.filterActive = data.filterActive;
				this.fullbrightEnabled = data.fullbrightEnabled;
				if (data.filterMode != null) {
					this.filterMode = data.filterMode;
				}
				if (data.occludedRenderStyle != null) {
					this.occludedRenderStyle = data.occludedRenderStyle;
				}
				this.translucentAlpha = Mth.clamp(data.translucentAlpha <= 0 ? 72 : data.translucentAlpha, 20, 240);

				if (data.blockRules != null) {
					this.whitelistedIds.clear();
					this.blacklistedIds.clear();
					for (Map.Entry<String, BlockFilterStatus> entry : data.blockRules.entrySet()) {
						if (entry.getKey() == null || entry.getValue() == null) {
							continue;
						}
						if (entry.getValue() == BlockFilterStatus.WHITELISTED) {
							this.whitelistedIds.add(entry.getKey());
						} else if (entry.getValue() == BlockFilterStatus.BLACKLISTED) {
							this.blacklistedIds.add(entry.getKey());
						}
					}
				}
			}
		} catch (IOException | JsonParseException e) {
			RenderDebugClientMod.LOGGER.warn("[RenderDebug] Failed to read config {}, falling back to defaults.", this.configPath, e);
		}

		rebuildSnapshot();
	}

	public synchronized void saveConfig() {
		try {
			Files.createDirectories(this.configPath.getParent());
			ConfigData data = new ConfigData();
			data.filterActive = this.filterActive;
			data.fullbrightEnabled = this.fullbrightEnabled;
			data.filterMode = this.filterMode;
			data.occludedRenderStyle = this.occludedRenderStyle;
			data.translucentAlpha = this.translucentAlpha;
			data.blockRules = new LinkedHashMap<>();
			for (String id : this.whitelistedIds) {
				data.blockRules.put(id, BlockFilterStatus.WHITELISTED);
			}
			for (String id : this.blacklistedIds) {
				data.blockRules.put(id, BlockFilterStatus.BLACKLISTED);
			}

			try (Writer writer = Files.newBufferedWriter(this.configPath, StandardCharsets.UTF_8)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			RenderDebugClientMod.LOGGER.warn("[RenderDebug] Failed to save config to {}.", this.configPath, e);
		}
	}

	private record FilterSnapshot(
			boolean filterActive,
			boolean fullbrightEnabled,
			FilterMode filterMode,
			OccludedRenderStyle occludedRenderStyle,
			int translucentAlpha,
			Set<Block> whitelistedBlocks,
			Set<Block> blacklistedBlocks
	) {
		static final FilterSnapshot EMPTY = new FilterSnapshot(
				false,
				false,
				FilterMode.WHITELIST,
				OccludedRenderStyle.INVISIBLE,
				72,
				Set.of(),
				Set.of()
		);
	}

	private static final class ConfigData {
		boolean filterActive = false;
		boolean fullbrightEnabled = false;
		FilterMode filterMode = FilterMode.WHITELIST;
		OccludedRenderStyle occludedRenderStyle = OccludedRenderStyle.INVISIBLE;
		int translucentAlpha = 72;
		Map<String, BlockFilterStatus> blockRules = new LinkedHashMap<>();
	}
}
