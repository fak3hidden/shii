# Render Pipeline & Occlusion Debugger (`renderdebug`)

An educational client-side Minecraft **Fabric** mod (**Minecraft 1.21.4**, **Java 21**, **Yarn Mappings**) built to inspect and experiment with Minecraft's chunk rendering pipeline:

1. **Chunk Section Cave Occlusion Graph (`ChunkOcclusionDataBuilder`)**
2. **Neighbor Block Face Culling (`Block#shouldDrawSide`)**
3. **Chunk Mesh Tessellation & Ambient Occlusion (`SectionBuilder` & `AbstractBlockState`)**
4. **RenderLayer Pass Routing & Translucent Alpha Blending (`RenderLayers` & `BufferBuilder`)**
5. **Dynamic GPU Lightmap Ambient Lighting (`LightmapTextureManager`)**

---

## Core Features

- **Dynamic Block Visibility Filter (Whitelist & Blacklist Modes)**:
  - **Blacklist Hide Mode**: Blacklisted terrain blocks (e.g., `stone`, `deepslate`, `dirt`, `netherrack`) bypass standard opaque chunk rendering while all other blocks remain visible.
  - **Whitelist Only Mode**: Isolates only explicitly whitelisted blocks (e.g., specific ores, spawners, chests, portals) while occluding everything else.
- **Dual Occluded Render Passes**:
  - **Invisible (Skip Mesh)**: Filtered blocks return `BlockRenderType.INVISIBLE` strictly on chunk-meshing worker threads, emitting zero quads.
  - **Translucent (Alpha Pass)**: Filtered blocks are rerouted from `SOLID`/`CUTOUT_MIPPED` to `RenderLayer.getTranslucent()` with interior shared-face culling and configurable per-vertex alpha (`6%–100%`).
- **Interactive In-Game Debugger GUI (`Right Shift` by default)**:
  - Master Visibility Filter toggle (`ON` / `OFF`)
  - Fullbright ambient lighting toggle via `LightmapTextureManager`
  - Filter Mode & Occluded Render Style switches + live Opacity slider
  - Searchable, categorized scrollable `ElementListWidget` covering every registered block in `Registries.BLOCK` with rendered item icons and click-to-cycle status badges
  - Non-blurring viewport background (`shouldPause() == false`) so chunk rebuilds and lighting updates are visible live behind the GUI
- **Persistent JSON Configuration**: Saved automatically to `.minecraft/config/renderdebug.json`.

---

## Project Structure

```text
src/main/
├── java/dev/shii/renderdebug/
│   ├── RenderDebugClientMod.java             # ClientModInitializer entrypoint
│   ├── config/
│   │   ├── BlockFilterStatus.java            # DEFAULT, WHITELISTED, BLACKLISTED
│   │   ├── FilterMode.java                   # BLACKLIST_HIDE, WHITELIST_ONLY
│   │   ├── OccludedRenderStyle.java          # INVISIBLE, TRANSLUCENT
│   │   └── RenderDebugManager.java           # Lock-free FilterSnapshot, JSON config, chunk reloader
│   ├── gui/
│   │   ├── BlockFilterListWidget.java        # Scrollable & searchable block list widget
│   │   └── RenderDebugScreen.java            # Interactive clickable debugger Screen
│   ├── input/
│   │   └── ModKeybindings.java               # KeyBinding registration (default: Right Shift)
│   ├── render/
│   │   └── RenderPipelineContext.java        # ThreadLocal chunk-rebuild worker scope tracker
│   └── mixin/
│       ├── SectionBuilderMixin.java          # Scopes ThreadLocal context to chunk mesh builds
│       ├── ChunkOcclusionGraphMixin.java     # Bypasses markClosed() cave culling graph
│       ├── BlockOcclusionMixin.java          # Overrides Block#shouldDrawSide face culling
│       ├── BlockStateRenderMixin.java        # Mesh skip, AO shadow suppression & encased luminance
│       ├── BlockRenderManagerMixin.java      # Fluid & fallback block tessellation guard
│       ├── RenderLayersMixin.java            # Routes filtered blocks to RenderLayer.getTranslucent()
│       ├── BufferBuilderAlphaMixin.java      # Rewrites vertex color alpha across Vanilla & Indigo
│       └── LightmapManagerMixin.java         # GPU lightmap AmbientLightFactor Fullbright override
└── resources/
    ├── fabric.mod.json
    ├── renderdebug.mixins.json
    └── assets/renderdebug/lang/en_us.json
```

---

## Building & Running Locally

Requires **JDK 21+**:

```bash
# Launch a development Minecraft 1.21.4 client with the mod loaded
./gradlew runClient

# Build the remapped production JAR under build/libs/
./gradlew build
```
