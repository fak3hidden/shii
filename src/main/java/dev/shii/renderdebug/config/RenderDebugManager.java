package dev.shii.renderdebug.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Central state and configuration manager for the Render Pipeline & Occlusion Debugger.
 *
 * <p>Thread-Safety Design:
 * Chunk meshing runs concurrently across multiple background worker threads, querying
 * block visibility tens of thousands of times per 16x16x16 section. To avoid lock
 * contention or {@code Identifier} string lookups on hot rendering paths, all mutable
 * UI state is compiled into an immutable {@link FilterSnapshot} backed by identity
 * {@link Set}s ({@link IdentityHashMap}) and published via a single {@code volatile}
 * reference. Because Minecraft guarantees a singleton {@link Block} instance per
 * registered block type, worker threads evaluate block visibility in O(1) pointer
 * comparisons with zero heap allocations.
 */
public final class RenderDebugManager {
	public static final String MOD_ID = "renderdebug";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final RenderDebugManager INSTANCE = new RenderDebugManager();

	/**
	 * Default whitelisted target blocks (ores, spawners, containers, portals) for
	 * studying selective underground occlusion and chunk rendering.
	 */
	private static final List<String> DEFAULT_WHITELIST = List.of(
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
			"minecraft:barrel",
			"minecraft:obsidian",
			"minecraft:crying_obsidian",
			"minecraft:nether_portal",
			"minecraft:end_portal_frame",
			"minecraft:end_portal"
	);

	/**
	 * Default blacklisted terrain blocks (stone, dirt, deepslate, netherrack, etc.)
	 * that normally occlude underground geometry.
	 */
	private static final List<String> DEFAULT_BLACKLIST = List.of(
			"minecraft:stone",
			"minecraft:deepslate",
			"minecraft:cobblestone",
			"minecraft:cobbled_deepslate",
			"minecraft:granite",
			"minecraft:diorite",
			"minecraft:andesite",
			"minecraft:tuff",
			"minecraft:calcite",
			"minecraft:dripstone_block",
			"minecraft:smooth_basalt",
			"minecraft:basalt",
			"minecraft:blackstone",
			"minecraft:dirt",
			"minecraft:coarse_dirt",
			"minecraft:rooted_dirt",
			"minecraft:grass_block",
			"minecraft:podzol",
			"minecraft:mycelium",
			"minecraft:mud",
			"minecraft:sand",
			"minecraft:red_sand",
			"minecraft:sandstone",
			"minecraft:red_sandstone",
			"minecraft:gravel",
			"minecraft:clay",
			"minecraft:netherrack",
			"minecraft:soul_sand",
			"minecraft:soul_soil",
			"minecraft:end_stone",
			"minecraft:bedrock"
	);

	private final Path configPath;
	private final Map<String, BlockFilterStatus> blockRules = new LinkedHashMap<>();

	private boolean filterEnabled = false;
	private boolean fullbrightEnabled = false;
	private FilterMode filterMode = FilterMode.BLACKLIST_HIDE;
	private OccludedRenderStyle occludedStyle = OccludedRenderStyle.INVISIBLE;
	private float translucentAlpha = 0.28f;

	/**
	 * Immutable lock-free snapshot read by chunk builder and render threads.
	 */
	private volatile FilterSnapshot snapshot = FilterSnapshot.EMPTY;

	private RenderDebugManager() {
		this.configPath = FabricLoader.getInstance().getConfigDir().resolve("renderdebug.json");
	}

	public static RenderDebugManager getInstance() {
		return INSTANCE;
	}

	/**
	 * Called once during {@code ClientModInitializer#onInitializeClient()} after
	 * {@link Registries#BLOCK} has been populated.
	 */
	public synchronized void initialize() {
		applyDefaultRules();
		loadFromDisk();
		rebuildSnapshot();
	}

	public synchronized void resetToDefaults() {
		this.blockRules.clear();
		applyDefaultRules();
		this.filterMode = FilterMode.BLACKLIST_HIDE;
		this.occludedStyle = OccludedRenderStyle.INVISIBLE;
		this.translucentAlpha = 0.28f;
		saveAndReloadChunks();
	}

	private void applyDefaultRules() {
		for (String id : DEFAULT_WHITELIST) {
			this.blockRules.put(id, BlockFilterStatus.WHITELISTED);
		}
		for (String id : DEFAULT_BLACKLIST) {
			this.blockRules.put(id, BlockFilterStatus.BLACKLISTED);
		}
	}

	// =========================================================================
	// Hot-Path Lock-Free Queries (Safe on Chunk Worker & Render Threads)
	// =========================================================================

	public boolean isFilterEnabled() {
		return this.snapshot.filterEnabled();
	}

	public boolean isFullbrightEnabled() {
		return this.snapshot.fullbrightEnabled();
	}

	public FilterMode getFilterMode() {
		return this.snapshot.filterMode();
	}

	public OccludedRenderStyle getOccludedStyle() {
		return this.snapshot.occludedStyle();
	}

	public float getTranslucentAlpha() {
		return this.snapshot.translucentAlpha();
	}

	public int getTranslucentAlphaByte() {
		return this.snapshot.translucentAlphaByte();
	}

	/**
	 * Returns {@code true} if {@code state} should render as a normal visible block
	 * under the active filter configuration, or {@code false} if it is filtered/occluded.
	 */
	public boolean isBlockVisible(BlockState state) {
		if (state.isAir()) {
			return false;
		}
		FilterSnapshot snap = this.snapshot;
		if (!snap.filterEnabled()) {
			return true;
		}
		return snap.isBlockVisible(state.getBlock());
	}

	/**
	 * Returns {@code true} when a block's mesh should be skipped completely during
	 * chunk section building ({@link OccludedRenderStyle#INVISIBLE}).
	 */
	public boolean shouldSkipBlockMesh(BlockState state) {
		if (state.isAir()) {
			return false;
		}
		FilterSnapshot snap = this.snapshot;
		return snap.filterEnabled()
				&& snap.occludedStyle() == OccludedRenderStyle.INVISIBLE
				&& !snap.isBlockVisible(state.getBlock());
	}

	/**
	 * Returns {@code true} when a block should be rerouted to {@code RenderLayer.getTranslucent()}
	 * with overridden vertex alpha ({@link OccludedRenderStyle#TRANSLUCENT}).
	 */
	public boolean shouldRenderTranslucent(BlockState state) {
		if (state.isAir()) {
			return false;
		}
		FilterSnapshot snap = this.snapshot;
		return snap.filterEnabled()
				&& snap.occludedStyle() == OccludedRenderStyle.TRANSLUCENT
				&& !snap.isBlockVisible(state.getBlock());
	}

	// =========================================================================
	// UI Mutation & Chunk Reload Triggers (Main Client Thread)
	// =========================================================================

	public synchronized void toggleFilter() {
		this.filterEnabled = !this.filterEnabled;
		saveAndReloadChunks();
	}

	public synchronized void setFilterEnabled(boolean enabled) {
		if (this.filterEnabled != enabled) {
			this.filterEnabled = enabled;
			saveAndReloadChunks();
		}
	}

	public synchronized void toggleFullbright() {
		this.fullbrightEnabled = !this.fullbrightEnabled;
		rebuildSnapshot();
		saveToDisk();
		// Reload chunks as well so baked block-light luminance updates cleanly alongside the GPU lightmap.
		reloadWorldChunks();
	}

	public synchronized void toggleFilterMode() {
		this.filterMode = this.filterMode.toggle();
		saveAndReloadChunks();
	}

	public synchronized void toggleOccludedStyle() {
		this.occludedStyle = this.occludedStyle.toggle();
		saveAndReloadChunks();
	}

	/**
	 * Updates the live translucency alpha immediately (e.g. while dragging a slider)
	 * without forcing a full chunk reload until {@link #saveAndReloadChunks()} is called.
	 */
	public synchronized void setTranslucentAlphaLive(float alpha) {
		this.translucentAlpha = MathHelper.clamp(alpha, 0.06f, 1.0f);
		rebuildSnapshot();
	}

	public synchronized BlockFilterStatus getBlockStatus(Block block) {
		Identifier id = Registries.BLOCK.getId(block);
		return this.blockRules.getOrDefault(id.toString(), BlockFilterStatus.DEFAULT);
	}

	public synchronized BlockFilterStatus cycleBlockStatus(Block block) {
		Identifier id = Registries.BLOCK.getId(block);
		String key = id.toString();
		BlockFilterStatus next = this.blockRules.getOrDefault(key, BlockFilterStatus.DEFAULT).next();
		if (next == BlockFilterStatus.DEFAULT) {
			this.blockRules.remove(key);
		} else {
			this.blockRules.put(key, next);
		}
		rebuildSnapshot();
		saveToDisk();
		if (this.filterEnabled) {
			reloadWorldChunks();
		}
		return next;
	}

	public int getWhitelistedCount() {
		return this.snapshot.whitelistedBlocks().size();
	}

	public int getBlacklistedCount() {
		return this.snapshot.blacklistedBlocks().size();
	}

	public synchronized void saveAndReloadChunks() {
		rebuildSnapshot();
		saveToDisk();
		reloadWorldChunks();
	}

	/**
	 * Instructs Minecraft's {@code WorldRenderer} to invalidate and rebuild all
	 * loaded chunk section meshes on the worker pool so occlusion and render-layer
	 * changes appear immediately in-game.
	 */
	public void reloadWorldChunks() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.worldRenderer != null && client.world != null) {
			client.execute(client.worldRenderer::reload);
		}
	}

	// =========================================================================
	// Snapshot Compilation & JSON Persistence
	// =========================================================================

	private void rebuildSnapshot() {
		Set<Block> whitelist = Collections.newSetFromMap(new IdentityHashMap<>());
		Set<Block> blacklist = Collections.newSetFromMap(new IdentityHashMap<>());

		for (Map.Entry<String, BlockFilterStatus> entry : this.blockRules.entrySet()) {
			Identifier id = Identifier.tryParse(entry.getKey());
			if (id == null) {
				continue;
			}
			Registries.BLOCK.getOptionalValue(id).ifPresent(block -> {
				if (entry.getValue() == BlockFilterStatus.WHITELISTED) {
					whitelist.add(block);
				} else if (entry.getValue() == BlockFilterStatus.BLACKLISTED) {
					blacklist.add(block);
				}
			});
		}

		int alphaByte = MathHelper.clamp(Math.round(this.translucentAlpha * 255.0f), 15, 255);
		this.snapshot = new FilterSnapshot(
				this.filterEnabled,
				this.fullbrightEnabled,
				this.filterMode,
				this.occludedStyle,
				this.translucentAlpha,
				alphaByte,
				Collections.unmodifiableSet(whitelist),
				Collections.unmodifiableSet(blacklist)
		);
	}

	private void loadFromDisk() {
		if (!Files.exists(this.configPath)) {
			saveToDisk();
			return;
		}
		try (Reader reader = Files.newBufferedReader(this.configPath, StandardCharsets.UTF_8)) {
			SerializedConfig data = GSON.fromJson(reader, SerializedConfig.class);
			if (data == null) {
				return;
			}
			this.filterEnabled = data.filterEnabled;
			this.fullbrightEnabled = data.fullbrightEnabled;
			if (data.filterMode != null) {
				this.filterMode = data.filterMode;
			}
			if (data.occludedStyle != null) {
				this.occludedStyle = data.occludedStyle;
			}
			if (data.translucentAlpha > 0.0f) {
				this.translucentAlpha = MathHelper.clamp(data.translucentAlpha, 0.06f, 1.0f);
			}
			if (data.blockRules != null) {
				this.blockRules.clear();
				for (Map.Entry<String, BlockFilterStatus> entry : data.blockRules.entrySet()) {
					if (entry.getKey() != null && entry.getValue() != null && entry.getValue() != BlockFilterStatus.DEFAULT) {
						this.blockRules.put(entry.getKey(), entry.getValue());
					}
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Failed to load renderdebug.json; falling back to defaults.", e);
		}
	}

	private void saveToDisk() {
		try {
			Files.createDirectories(this.configPath.getParent());
			SerializedConfig data = new SerializedConfig();
			data.filterEnabled = this.filterEnabled;
			data.fullbrightEnabled = this.fullbrightEnabled;
			data.filterMode = this.filterMode;
			data.occludedStyle = this.occludedStyle;
			data.translucentAlpha = this.translucentAlpha;
			data.blockRules = new LinkedHashMap<>(this.blockRules);

			try (Writer writer = Files.newBufferedWriter(this.configPath, StandardCharsets.UTF_8)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			LOGGER.warn("Failed to save renderdebug.json", e);
		}
	}

	private static final class SerializedConfig {
		boolean filterEnabled = false;
		boolean fullbrightEnabled = false;
		FilterMode filterMode = FilterMode.BLACKLIST_HIDE;
		OccludedRenderStyle occludedStyle = OccludedRenderStyle.INVISIBLE;
		float translucentAlpha = 0.28f;
		Map<String, BlockFilterStatus> blockRules = new LinkedHashMap<>();
	}

	public record FilterSnapshot(
			boolean filterEnabled,
			boolean fullbrightEnabled,
			FilterMode filterMode,
			OccludedRenderStyle occludedStyle,
			float translucentAlpha,
			int translucentAlphaByte,
			Set<Block> whitelistedBlocks,
			Set<Block> blacklistedBlocks
	) {
		static final FilterSnapshot EMPTY = new FilterSnapshot(
				false,
				false,
				FilterMode.BLACKLIST_HIDE,
				OccludedRenderStyle.INVISIBLE,
				0.28f,
				71,
				Set.of(),
				Set.of()
		);

		public boolean isBlockVisible(Block block) {
			if (this.filterMode == FilterMode.WHITELIST_ONLY) {
				return this.whitelistedBlocks.contains(block);
			}
			// FilterMode.BLACKLIST_HIDE
			if (this.whitelistedBlocks.contains(block)) {
				return true;
			}
			return !this.blacklistedBlocks.contains(block);
		}
	}
}
