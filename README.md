# Render Pipeline & Occlusion Debugger (`26.3`)

An educational client-side **Minecraft `26.3` Fabric** visual debugging utility designed to study and inspect Minecraft's modern rendering pipeline: **chunk section mesh compilation (`SectionCompiler`), `VisGraph` cave-occlusion flood-fill culling, hidden-surface face culling, RenderPearl `ChunkSectionLayer` routing, vertex alpha injection, and `LightmapRenderStateExtractor` GPU lighting**.

---

## Target Platform & Toolchain

| Component | Version / Details |
| :--- | :--- |
| **Minecraft** | `26.3` (Unobfuscated official Mojang mappings, RenderPearl GPU backend) |
| **Java** | **Java 25** (`JAVA_25`) |
| **Fabric Loader** | `>= 0.20.1` |
| **Fabric API** | `0.165.0+26.3` |
| **Fabric Loom** | `1.19-SNAPSHOT` (`net.fabricmc.fabric-loom` with `splitEnvironmentSourceSets()`) |
| **Gradle** | `9.8.2` |

---

## Core Features

1. **Block Visibility Filter (Occlusion Override)**
   - Dynamically **WHITELIST** or **BLACKLIST** any block registered in `BuiltInRegistries.BLOCK` (including ores, terrain overburden, storage/spawner blocks, and fluids such as `minecraft:water` and `minecraft:lava`).
   - **Invisible Mode (`OccludedRenderStyle.INVISIBLE`)**: Bypasses tessellation in `SectionCompiler#compile` and `FluidRenderer#tesselate`, emitting `0` vertices into the GPU chunk buffer while exposing adjacent whitelisted block faces.
   - **Translucent Ghost Mode (`OccludedRenderStyle.TRANSLUCENT`)**: Reroutes `ChunkSectionLayer.SOLID` and `ChunkSectionLayer.CUTOUT` geometry into `ChunkSectionLayer.TRANSLUCENT` with configurable per-vertex alpha (`20..240` / `8%..94%`), enabling depth-sorted alpha-blended terrain X-ray inspection while culling noisy interior shared faces between adjacent ghost blocks.
   - **Lock-Free Worker-Thread Concurrency**: Publishes immutable `IdentityHashMap`-backed `FilterSnapshot` instances via a `volatile` reference so concurrent `SectionCompiler` worker threads query block rules in `O(1)` without locks.

2. **Interactive In-Game Inspector Screen (`Right Shift`)**
   - Bound to **Right Shift** (`SDLScancode.SDL_SCANCODE_RSHIFT`) via Minecraft `26.3`'s SDL3 `KeyMappingHelper` API (plus optional unbound hotkeys for toggling the Visibility Filter and Fullbright directly in-game).
   - **Live Non-Blurring Viewport**: Suppresses pause behavior (`isPauseScreen() == false`) and bypasses `Screen#extractBackground`'s Gaussian blur shader so live chunk rebuilds and lighting changes remain crisp behind the UI.
   - **Controls**:
     - Master **Visibility Filter** toggle (`ACTIVE` / `OFF`)
     - **Fullbright** ambient lighting toggle (`ON` / `OFF`)
     - **Filter Policy** switch (`WHITELIST` / `BLACKLIST`)
     - **Occluded Render Style** switch (`INVISIBLE` / `TRANSLUCENT`)
     - **Ghost Alpha Slider** (live preview while dragging; commits a single `LevelExtractor#allChanged()` chunk rebuild on mouse release)
     - Real-time **Search Box** (`EditBox`), **Category Tabs** (`All`, `Ores`, `Terrain`, `Configured`), **16x16 Block Item Icons**, and per-block `DEFAULT -> WHITELIST -> BLACKLIST` rule buttons.

3. **Zero-Chunk-Rebuild GPU Fullbright (`LightmapExtractorMixin`)**
   - Hooks Minecraft `26.3`'s `LightmapRenderStateExtractor#extract(LightmapRenderState, float)` pipeline to override `ambientColor` to `LightmapRenderStateExtractor.WHITE` (`1.0, 1.0, 1.0`) and `brightness` to `1.0F` on the very next frame — without touching `options.gamma()` or violating `OptionInstance` slider bounds.

---

## Rendering Pipeline Architecture (8 Surgical Mixins)

Simply skipping a block model inside `SectionCompiler` is not enough in Minecraft `26.3` — five distinct engine stages would otherwise keep underground blocks invisible or pitch-black:

| Pipeline Stage | Mixin Class | Target (`26.3` Mojang Class) | How It Manipulates the Rendering Engine |
| :--- | :--- | :--- | :--- |
| **1. Mesh Bypass & Layer Routing** | `SectionCompilerMixin` | `SectionCompiler` | Scopes `RenderPipelineContext` (`ThreadLocal`) to chunk-builder threads; intercepts `BlockState#getRenderShape()` (compatible with both Vanilla `ModelBlockRenderer` and Fabric Indigo's `AltModelBlockRenderer`); and rewrites `getOrBeginLayer` to route translucent-occluded quads to `ChunkSectionLayer.TRANSLUCENT` for `MeshData#sortQuads` depth sorting. |
| **2. Fluid Mesh Filtering** | `FluidRendererMixin` | `FluidRenderer` | Cancels `tesselate(...)` for hidden fluids or marks `CURRENT_BLOCK_TRANSLUCENT` so water and opaque lava route into `ChunkSectionLayer.TRANSLUCENT` with custom alpha. |
| **3. Sub-Chunk Cave Graph** | `VisGraphMixin` | `VisGraph` | Cancels `setOpaque(BlockPos)` while the filter is active so `VisGraph#resolve()` keeps all 6 sub-chunk portals open in `VisibilitySet`, preventing `SectionOcclusionGraph` from culling underground chunk sections. |
| **4. Neighbor Face Culling** | `BlockOcclusionMixin` | `Block` | Overrides `shouldRenderFace(BlockState, BlockState, Direction)` to force-emit faces of visible blocks bordering occluded terrain (`true`) while culling shared interior seams between adjacent ghost-translucent blocks (`false`). |
| **5. Smooth-Lighting AO** | `BlockStateAmbientOcclusionMixin` | `BlockBehaviour.BlockStateBase` | Returns `1.0F` from `getShadeBrightness` on chunk-compile threads for occluded blocks so hidden stone does not cast dark `0.2F` corner shadows onto exposed ore quads. |
| **6. Encased Face Luminance** | `BlockModelLighterCacheMixin` | `BlockModelLighter.Cache` | Used by Vanilla `BlockModelLighter` and Fabric Indigo `AoCalculator` / `FlatLighter`: returns `LightCoordsUtil.FULL_BRIGHT` (`0x00F000F0`) when a visible block face samples light inside an occluded solid neighbor, keeping natural cave air dark unless Fullbright is enabled. |
| **7. Vertex Alpha Injection** | `BufferBuilderAlphaMixin` | `BufferBuilder` | Rewrites vertex alpha across all three `26.3` color entrypoints (`addVertex(FFFIFFIIFFF)V`, `setColor(IIII)`, and `setColor(I)`) while preserving biome RGB tints. |
| **8. GPU Lightmap Extraction** | `LightmapExtractorMixin` | `LightmapRenderStateExtractor` | Forces immediate 1-frame lightmap texture regeneration on toggle and overrides `LightmapRenderState` ambient lighting to `1.0F` pure white. |

---

## Project Structure

```text
shii/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── src/
│   ├── main/
│   │   └── resources/
│   │       ├── fabric.mod.json
│   │       └── assets/renderdebug/lang/en_us.json
│   └── client/
│       ├── resources/
│       │   └── renderdebug.client.mixins.json
│       └── java/dev/shii/renderdebug/client/
│           ├── RenderDebugClientMod.java
│           ├── config/
│           │   ├── BlockFilterStatus.java
│           │   ├── FilterMode.java
│           │   ├── OccludedRenderStyle.java
│           │   └── RenderDebugManager.java
│           ├── render/
│           │   └── RenderPipelineContext.java
│           ├── input/
│           │   └── ModKeybindings.java
│           ├── gui/
│           │   ├── BlockFilterListWidget.java
│           │   └── RenderDebugScreen.java
│           └── mixin/
│               ├── SectionCompilerMixin.java
│               ├── FluidRendererMixin.java
│               ├── VisGraphMixin.java
│               ├── BlockOcclusionMixin.java
│               ├── BlockStateAmbientOcclusionMixin.java
│               ├── BlockModelLighterCacheMixin.java
│               ├── BufferBuilderAlphaMixin.java
│               └── LightmapExtractorMixin.java
└── README.md
```

---

## Building & Running Locally

Minecraft `26.3` and Fabric Loom `1.19+` require a **Java 25 JVM**.

This repository includes Gradle 9's **`gradle/gradle-daemon-jvm.properties`** (`toolchainVersion=25`) and the **`foojay-resolver-convention`** plugin, so **even if your system's default `java` is Java 21**, Gradle will automatically download and run its build daemon + Minecraft `26.3` on **OpenJDK 25** (cached cleanly under `~/.gradle/jdks/` without changing your system Java):

```bash
# Pull latest changes if updating an existing clone/download
git pull

# Launch Minecraft 26.3 client in a development environment
./gradlew runClient

# Build the production mod JAR under build/libs/
./gradlew build
```

*(Alternatively, if you prefer managing JDKs manually on Linux/Pop!_OS via SDKMAN or Temurin, you can run `JAVA_HOME=/path/to/jdk-25 ./gradlew runClient`.)*

Configuration is persisted automatically to `<instance>/config/renderdebug.json`.
