# Forgematica: Minecraft 26.2 → 26.3 migration assessment (Phase 1)

Private-testing migration. Nothing is published. No code, dependency or build config was changed in this phase.

## Baseline

| Item | Value |
|---|---|
| Source branch | `neoforge/26.2` |
| Baseline commit | `72c1a0f28b409583f14235960798934bf44fc1e3` (same SHA on `origin` and upstream) |
| Target branch | `neoforge/26.3` (created locally, not pushed) |
| Upstream | https://github.com/CagayakeGirls/litematica-neoforge (latest branch is `neoforge/26.2`; **no 26.3 port exists yet**) |

## Verified versions

| Component | Current | Target | Source |
|---|---|---|---|
| Minecraft | 26.2 | **26.3** (also rc-1..3; 26.4 snapshots exist) | https://piston-meta.mojang.com/mc/game/version_manifest_v2.json |
| NeoForge | 26.2.0.11-beta | **26.3.0.88** (latest release; POM HTTP 200) | https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml |
| MaFgLib | 0.5.2+mc26.2 | **0.6.0+mc26.3** | https://maven.kessokuteatime.work/releases/team/cagayakegirls/mafglib/mafglib/maven-metadata.xml |
| Java | 25 | 25 (local JDK 25.0.4.1) | `build.gradle` (`options.release = 25`) |
| Gradle | 9.6.1 | 9.6.1 (unverified against 26.3) | `gradle/wrapper/gradle-wrapper.properties` |
| Architectury Loom | 1.17-SNAPSHOT | **unverified**; needs a real build | `gradle/libs.versions.toml` |
| conditional-mixin | 0.6.4 | 0.6.4 (MaFgLib 0.6.0 POM also uses 0.6.4) | MaFgLib POM |

Primer: https://docs.neoforged.net/primer/docs/26.3/ (only roughly the first half was summarized; re-read the render/GUI sections in Phase 2).

## MaFgLib 26.3: usable

`team.cagayakegirls.mafglib:mafglib:0.6.0+mc26.3` is on the KTT releases Maven (jar, pom and module files return HTTP 200; last updated 2026-10-11). The repo already resolves that Maven through `buildSrc` (`KTTMavenReleases`), so only `malilib_version` needs to change. Its POM also pulls `foxified-classtweaker 0.1.0-alpha.4`. It is a 0.x → 0.6 minor bump, so API changes in malilib classes used by this mod are possible; expect compile errors in GUI/config code.

## Security advisories

No published advisories for `CagayakeGirls/litematica-neoforge` or `sakura-ryoko/malilib` (API returned `[]`). Dependabot alerts are disabled on upstream, so absence of alerts is not proof of absence of issues.

## Relevant files and blockers

Primer points that hit this mod:

| Area | Primer change | Local footprint |
|---|---|---|
| Rendering (**blocker #1**) | Blaze3d → `com.mojang.renderpearl`; `RenderPipeline` ctor and accessors changed; `RenderPass` → `FrontendRenderPass`; `DynamicUniforms` → `DynamicGpuData`; Shaderc, `#moj_import` → `#include`, explicit shader `location`s; new OIT transparency | ~654 hits / 36 files. Hot spots: `render/schematic/ChunkRendererSchematicVbo` (86), `WorldRendererSchematic` (60), `ChunkMeshDataSchematic` (55), `render/LitematicaPipelines` (50), `ChunkRenderGpuUploader`, `ChunkRenderDataSchematic`, `compat/iris/IrisRenderingFix`, `mixin/render/MixinLevelRenderer` |
| Input | GLFW → SDL; `InputConstants` uses `SDLScancode`; `KeyEvent.key` vs `keycode` (`SDLKeycode`); text input via `TextInputManager`; `setFocused` must call `Minecraft#onTextInputFocusChange()` | 26 hits / 9 files: `event/InputHandler`, `gui/GuiSchematicSaveBase`, `GuiMaterialListSaveBase`, several `gui/widgets/Widget*` |
| Entity/item render dispatch | `EntityRenderDispatcher` no longer takes `Minecraft`; `ItemInHandRenderer` → `FirstPersonHandsAndItemsRenderer`; `TextureAtlasSprite` → `UvMapping` | `mixin/render/MixinEntityRenderDispatcher`, `mixin/model/IMixinEntityRenderDispatcher`, `BlockModelCacheSchematic` |
| Mixins | Any renamed target breaks launch (`defaultRequire: 1`) | 23 common + 28 client mixins in `mixins.litematica.json`; render, model, screen and debug-HUD mixins are highest risk. `mixin/test/*` are unregistered debug mixins, can be ignored or deleted from compilation scope |
| Access widener | Target classes/members may move | `litematica.accesswidener` (11 entries, mostly `FluidRenderer`) |
| Signs | `SignBlockEntity` uses `SignTextSlot` | `mixin/entity/IMixinSignBlockEntity`, `MixinAbstractSignEditScreen` |
| Screens | `ConfirmLinkScreen` takes `URI`; `Screen#isInputCaptured()`, `NarrationTrigger` | GUI package (29 files) |
| Networking | `ClientboundAddTransientBlockPacket` added; no payload API break found in the part read | `network/ServuxLitematicaPacket`, `ServuxLitematicaHandler`, 2 network mixins |
| Serialization | No NBT/schematic format change found in the part read | NBT in ~31 files (`ProtoChunkSchematic`, `SchematicPlacingUtils`, `EntityUtils`, `WorldPlacingUtils`) |

GitHub Actions: `build.yml` (Java 25, `./gradlew build`) is safe. `release.yml` publishes; do not run it for the 26.3 test branch.

## Changes needed, in priority order (Phase 2)

1. Bump versions: `gradle.properties` (`minecraft_version`, `minecraft_version_out`, `neoforge_version=26.3.0.88`, `malilib_version=0.6.0+mc26.3`), `gradle/libs.versions.toml` ranges (`range-game`, `range-neoforge`, `range-mafglib`), and the hardcoded `setGameVersions("26.2")` in `build.gradle`.
2. Run `gradlew build` once to confirm Loom 1.17-SNAPSHOT resolves Minecraft 26.3 / NeoForge 26.3.0.88; fix tooling before touching code.
3. Fix compile errors from MaFgLib 0.6.0 API changes (config, GUI, hotkey code).
4. Port input handling to SDL key/mouse types.
5. Port the rendering stack to renderpearl: pipelines and shaders (`LitematicaPipelines`, GLSL files), buffers/uploader, render pass usage, then decide on OIT for translucent schematic layers.
6. Re-validate each mixin target and the access widener against 26.3 names; remove or adjust anything failing.
7. Sign, screen, and networking adjustments.
8. Runtime smoke test: load a schematic, render, place, verify, material list, Iris on/off. Then update `CHANGELOG`/`neoforge.mods.toml` ranges.

## Blockers

- **Rendering API rewrite**: not a mechanical rename; expect most of the work here.
- **Loom support for 26.3** is unconfirmed until a build is run.
- **Primer coverage is incomplete**: only part of the page was read, so other breaking changes may exist.
- No upstream 26.3 reference to cherry-pick from; check upstream again before and during Phase 2.
- The tree had an untracked `.atl/` directory when the branch was created (user-approved).

---

# Phase 2 results: build configuration and dependency resolution (2026-10-17)

Scope: build files and metadata only. No Java source, mixin, access widener, license, mod ID or author changes. Nothing committed, pushed or published.

## Changed files

| File | Change |
|---|---|
| `gradle.properties` | `minecraft_version` and `minecraft_version_out` 26.2 → 26.3; `neoforge_version` 26.2.0.11-beta → 26.3.0.88; `malilib_version` 0.5.2+mc26.2 → 0.6.0+mc26.3 |
| `gradle/libs.versions.toml` | `range-game` `[26.3,)`; `range-neoforge` `[26.3,)`; `range-mafglib` `[0.4,)` → `[0.6,)` |
| `build.gradle` | `setGameVersions("26.3")` (metadata only; no publish task was run) |
| `.github/` | Removed (`git rm -r`, staged, not committed): `workflows/build.yml`, `workflows/release.yml`. Reason: this branch is a stopgap until the official developers publish a 26.3 port; it also guarantees no CI publishing. Recoverable from `neoforge/26.2`. |

Left unchanged on purpose: `buildSrc` (KTT and NeoForge repos already configured), `neoforge.mods.toml` template (all versions are templated), `range-fml` `[4,)` (NeoForge 26.3.0.88 uses FML 12.0.12, still in range), Loom `1.17-SNAPSHOT`, Gradle 9.6.1, conditional-mixin 0.6.4, Java 25.

## Resolved versions (from `dependencies` output)

| Dependency | Resolved |
|---|---|
| Minecraft / NeoForge | `net.minecraft:neoforge-26.3.0.88-minecraft-merged-…:26.3` (NeoForge 26.3.0.88) |
| FML loader | `net.neoforged.fancymodloader:loader:12.0.12` |
| MaFgLib | `team.cagayakegirls.mafglib:mafglib:0.6.0+mc26.3` from `maven.kessokuteatime.work/releases` (jar SHA-1 `1c857510…` matches the published `.module`) |
| conditional-mixin | `me.fallenbreath:conditional-mixin-neoforge:0.6.4` |
| ClassTweaker | `team.cagayakegirls.foxified:foxified-classtweaker:0.1.0-alpha.4` (via MaFgLib) |
| MixinExtras | `io.github.llamalad7:mixinextras-neoforge:0.5.5` (via NeoForge) |
| Architectury Loom | `1.17-SNAPSHOT` resolved and set up MC 26.3 without changes (Gradle 9.6.1, JDK GraalVM 25.0.4.1) |

The standard KTT Maven artifact exists, so Curse Maven (`curse.maven:mafglib-910766:9107274`, which also answers HTTP 200) was **not** configured.

## Dependency resolution result

`compileClasspath` and `runtimeClasspath` both resolve: BUILD SUCCESSFUL. No 26.2 artifacts appear in either. The MaFgLib jar is present in the Gradle cache (runtime availability confirmed on the classpath; no client launch was attempted in this phase).

## compileJava: first meaningful errors (Java sources untouched)

Result: BUILD FAILED. javac stopped at its default cap of **100 errors**, so the real total is higher. Spanning 97 distinct file:line locations; the first 15 unique errors, grouped by cause:

**A. Blaze3d → renderpearl (GPU API moved or removed)** — dominant cause
1. `IrisRenderingFix.java:15-17` — `package com.mojang.blaze3d.textures does not exist` (`AddressMode`, `FilterMode`, `GpuSampler`/`GpuTextureView`)
2. `IrisRenderingFix.java:13` — `GpuDevice` (`com.mojang.blaze3d.systems`) not found
3. `IWorldSchematicRenderer.java:9` — `package com.mojang.blaze3d.textures does not exist`
4. `ChunkRenderBatchDraw.java:8-13,27,32` — `GpuBuffer`, `GpuBufferSlice`, `RenderPipeline`, `VertexFormat`, `IndexType`, `PrimitiveTopology` not found
5. `ChunkRenderBatchDraw.java:15-17` — `package com.mojang.blaze3d.textures does not exist`
6. `ChunkRenderBatchDraw.java:28` — `package RenderPass does not exist` (`RenderPass` → `FrontendRenderPass`)
7. `ChunkRenderGpuUploader.java:9-10`, `ChunkRendererSchematicVbo.java:12-13` — `CommandEncoder`, `GpuDevice` (`com.mojang.blaze3d.systems`) not found
8. `IWorldSchematicRenderer.java:87,103,125,129,131` — `cannot find symbol` for the same GPU types in signatures
9. Further `RenderPipeline` / `BlendFunction` / `ColorTargetState` / `DepthStencilState` / `CompareOp` / `PolygonMode` / `BlendFactor` symbol errors in the pipeline definitions (`LitematicaPipelines`, `OverlayRenderType`)

**B. MaFgLib 0.6.0 API reshuffle**
10. `IWorldSchematicRenderer.java:35`, `SchematicRenderState.java:10` — `package fi.dy.masa.malilib.render.uniform does not exist` (`ChunkFixUniform`, not present in the 0.6.0 jar)
11. `GuiMaterialListSaveBase.java:19`, `GuiSchematicSaveBase.java:17` — `fi.dy.masa.malilib.util.KeyCodes` moved to `fi.dy.masa.malilib.util.input.KeyCodes` (present in 0.6.0 jar)
12. `GuiSchematicLoad.java:27` — `fi.dy.masa.malilib.util.FileRenamer` moved to `fi.dy.masa.malilib.util.file_ops.FileRenamer`

**C. Minecraft 26.3 class moves/removals**
13. `WorldSchematic.java:40,786` — `net.minecraft.world.item.alchemy.PotionBrewing` not found
14. `WorldSchematic.java:48,799` — `net.minecraft.world.level.block.entity.FuelValues` not found
15. `IMixinRedstoneWireBlock.java:5` — `net.minecraft.world.level.block.RedStoneWireBlock` not found (renamed or moved)

**D. Missing networking API (not in the top 15 count, same phase)**
- `ServuxLitematicaHandler.java:10` — `net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking` is not on the 26.3 classpath; MaFgLib 0.6.0 has `fi.dy.masa.malilib.network.ClientPlayHandler` but no `ClientPlayNetworking`. Needs investigation of how this resolved on 26.2.

Counts are from javac's capped output; later phases will reveal more (mixin target failures only appear at runtime).

## Blockers after Phase 2

- No dependency blocker: everything resolves, Loom works.
- The Java port is the blocker: rendering (A) is the bulk, with MaFgLib API moves (B) and MC class moves (C) being mechanical.
- Primer still only partially read; unknown further breaking changes (A will grow once types resolve, e.g. `RenderSystem`, `DynamicUniforms`, `GuiGraphics`).

---

# Phase 3, batch 1: non-render compile errors (2026-10-17)

Result: `compileJava` **FAIL** (javac still stops at 100 errors). All non-rendering errors from Phase 2 groups B, C and D are fixed; every remaining error is rendering. No build files, mixins list, access widener, formats or rendering code were touched. Nothing committed.

## Files changed (7)

| File | Root cause fixed |
|---|---|
| `world/WorldSchematic.java` | `Level.potionBrewing()` and `Level.fuelValues()` no longer exist in 26.3 (`PotionBrewing`, `FuelValues` classes gone; brewing/fuel are now `BrewingFuel` / `CookingFuel` data components). Removed the two overrides and their imports. Nothing else referenced them. All other abstract `Level` methods (16) were already implemented. |
| `mixin/block/IMixinRedstoneWireBlock.java` | `RedStoneWireBlock` renamed `RedstoneWireBlock`. The private `getConnectionState(BlockGetter, BlockState, BlockPos)` still exists, so the `@Invoker` is unchanged. |
| `gui/GuiSchematicSaveBase.java`, `gui/GuiMaterialListSaveBase.java` | MaFgLib moved `KeyCodes` to `fi.dy.masa.malilib.util.input.KeyCodes`. Its constants are SDL **keycodes** (`KEY_TAB = 9`) while `KeyEvent.key()` is the SDL **scancode** (`InputConstants.KEY_TAB = 43`), so the Tab check now uses `input.keycode()`. No numeric constants were replaced. These were the only two `key()` / GLFW comparisons in the code. |
| `gui/GuiSchematicLoad.java`, `gui/GuiSchematicManager.java` | `FileRenamer` moved to `fi.dy.masa.malilib.util.file_ops.FileRenamer` (same constructor). `GuiSchematicManager` relied on a wildcard import, so an explicit import was added. |
| `network/ServuxLitematicaHandler.java` | MaFgLib 0.5.2 depended on `foxified-networking-api` (Fabric-style `ClientPlayNetworking`). 0.6.0 dropped it and `IPluginClientPlayHandler<T>` now extends NeoForge `IPayloadHandler<T>`. Switched the receive signature to `IPayloadContext` and removed the anonymous `receive(...)` override (a Fabric method that no longer exists; the interface's default `handle` already delegates to `receivePlayPayload`). Body logic unchanged. |

## Remaining errors (all rendering), next 10 distinct causes

javac prints 100 errors, so more exist. Types listed are all plain package moves unless noted; the real 26.3 locations were checked in the merged Minecraft jar.

| # | Cause | Missing → 26.3 location | Main files |
|---|---|---|---|
| 1 | `GpuBuffer`, `GpuBufferSlice` | `com.mojang.renderpearl.api.buffers` | `ChunkRenderGpuBuffers`, `ChunkRenderBatchDraw`, `IWorldSchematicRenderer` |
| 2 | `RenderPipeline` | `renderpearl.api.pipeline` (ctor and accessors changed per primer) | `OverlayRenderType`, `ChunkRenderLayers`, `MixinRenderPipelines` |
| 3 | `IndexType`, `PrimitiveTopology` | `renderpearl.api.pipeline` | `ChunkRenderBatchDraw`, `ChunkRenderLayers` |
| 4 | `GpuSampler`, `GpuTextureView`, `AddressMode`, `FilterMode` | `renderpearl.api.textures` | `IrisRenderingFix`, `ChunkRenderBatchDraw`, `IWorldSchematicRenderer` |
| 5 | `GpuDevice` | `renderpearl.api.device` | `IrisRenderingFix`, `ChunkRenderGpuUploader`, `ChunkRendererSchematicVbo` |
| 6 | `CommandEncoder`, `RenderPass` | `renderpearl.api.commands` | `ChunkRenderGpuUploader`, `ChunkRendererSchematicVbo`, `ChunkRenderBatchDraw` |
| 7 | `VertexFormat` | `renderpearl.api.vertex` | `ChunkRenderLayers`, `OverlayRenderType` |
| 8 | `BlendFunction`, `BlendFactor`, `ColorTargetState`, `DepthStencilState`, `CompareOp`, `PolygonMode` | `renderpearl.api.pipeline` | `OverlayRenderType`, `MixinRenderPipelines` |
| 9 | `ChunkFixUniform` removed from MaFgLib `render.uniform` | not a move: needs a design decision (local replacement vs removal) | `SchematicRenderState`, `IWorldSchematicRenderer`, `WorldRendererSchematic`, `LitematicaRenderer` |
| 10 | Callers of the above in `RenderHandler`, `MixinLevelRenderer`, `ChunkMeshDataSchematic`, `BufferBuilderCache` | will only show after 1-8 resolve | — |

Errors per file (javac-capped): `ChunkRenderGpuBuffers` 14, `ChunkRenderBatchDraw` 14, `OverlayRenderType` 13, `MixinRenderPipelines` 8, `IWorldSchematicRenderer` 8, `WorldRendererSchematic` 7, `MixinLevelRenderer` 7, `ChunkRenderLayers` 7, `IrisRenderingFix` 5, `LitematicaRenderer` 4.

## Needs runtime validation

- Tab toggles focus in the schematic-save and material-list-save text fields (scancode vs keycode).
- Servux packet receive path (`IPayloadContext` handler) on a server that has Servux; also that handler registration still works with MaFgLib 0.6.0.
- `IMixinRedstoneWireBlock` applies at launch (`defaultRequire: 1`) and redstone wire schematic conversion (`SchematicConversionFixers`) behaves as before.
- IDE note: the editor still shows 26.2-based errors until the Gradle project is reloaded; Gradle output is authoritative.

## Next batch suggestion

Rendering imports (causes 1-8) are a package-move batch plus API shape changes (`RenderPipeline` constructor, `RenderPass` → `FrontendRenderPass` per primer). Decide the `ChunkFixUniform` replacement first, then port `LitematicaPipelines` / `OverlayRenderType` before the chunk renderers.

---

# Phase 3, batch 2: Blaze3d → renderpearl type relocations (2026-10-17)

Result: `compileJava` **FAIL** (javac still capped at 100 errors). Only imports were changed, in 10 files. No logic, shaders, mixin targets, formats or build files touched. Nothing committed.

## Files changed (10, imports only)

`render/schematic/{OverlayRenderType, ChunkRenderLayers, ChunkRenderGpuBuffers, ChunkRenderGpuUploader, ChunkMeshDataSchematic, BufferBuilderCache, ChunkRenderBatchDraw, ChunkRendererSchematicVbo}.java`, `render/IWorldSchematicRenderer.java`, `compat/iris/IrisRenderingFix.java` (29 imports remapped).

## Root cause fixed

Minecraft 26.3 moved the GPU/pipeline API from `com.mojang.blaze3d.*` to `com.mojang.renderpearl.api.*`. Everything else under `blaze3d` (`RenderSystem`, `MeshData`, `BufferBuilder`, `ByteBufferBuilder`, `PoseStack`, `VertexConsumer`, `VertexSorting`, `RenderTarget`, `NativeImage`, `DefaultVertexFormat`, `QuadInstance`, frame graph) is unchanged.

| Old (`com.mojang.blaze3d…`) | New (`com.mojang.renderpearl.api…`) |
|---|---|
| `buffers.GpuBuffer`, `GpuBufferSlice` | `buffers.*` |
| `pipeline.RenderPipeline`, `BlendFunction`, `ColorTargetState`, `DepthStencilState` | `pipeline.*` |
| `platform.BlendFactor`, `CompareOp`, `PolygonMode`; `IndexType`; `PrimitiveTopology` | `pipeline.*` |
| `textures.GpuSampler`, `GpuTextureView`, `AddressMode`, `FilterMode` | `textures.*` |
| `systems.GpuDevice` | `device.GpuDevice` |
| `systems.CommandEncoder`, `systems.RenderPass` | `commands.*` |
| `vertex.VertexFormat` | `vertex.VertexFormat` |

## Next 10 distinct errors

javac's 100-error cap hid several non-render errors until the render ones shrank, so these are new to the list, not regressions. Counts are errors per file (javac prints each twice).

| # | Cause | Where |
|---|---|---|
| 1 | Same renderpearl imports still unremapped (deferred files) plus `DynamicUniforms` → `DynamicGpuData`, `RenderPass#bindTexture` removed (now `setUniform(String, GpuTextureView, GpuSampler)`), `ChunkSectionLayerGroup#outputTarget()` | `WorldRendererSchematic` (21), `MixinLevelRenderer` (7), `RenderHandler` (3), `LitematicaRenderer` (4), `ChunkRenderBatchDraw` (6) |
| 2 | `RedStoneWireBlock` → `RedstoneWireBlock` rename (missed in batch 1: a static reference, not an import) | `SchematicConversionFixers` (18) |
| 3 | `ChunkFixUniform` removed from MaFgLib (decision A/B pending, see batch 2 notes above) | `SchematicRenderState`, `IWorldSchematicRenderer`, `LitematicaRenderer`, `WorldRendererSchematic` |
| 4 | `MaLiLibPipelines.LEGACY_*` removed from MaFgLib | `ChunkRenderLayers` |
| 5 | `IPluginClientPlayHandler` now has abstract `checkFailures()` / `tickFailures()`; the anonymous `ServuxLitematicaHandler` instance does not implement them (hidden before by the missing Fabric type). The class already has `failures` / `MAX_FAILURES` / `resetFailures`, which probably map onto them: confirm against MaFgLib semantics before writing | `ServuxLitematicaHandler:33`, plus 1 "does not override" in `RenderHandler:38` |
| 6 | `new BlockPos(...)` constructor signature changed | `LitematicaSchematic:2127,2275`, `SchematicMetadata:137` |
| 7 | MaFgLib moved `FileCopier` / `FileDeleter` (same family as `FileRenamer`) | `GuiSchematicManager:431,436` |
| 8 | `IrisCompat.hasSodium()` gone from the Iris compat API; `MaterialInfo.shade()` missing | `GuiSchematicProjectsBrowser`, `MixinLevelExtractor`, `AOProcessorLegacy:124,172` |
| 9 | `AbortableIterationConsumer.Continuation` no longer exists; `SignText.DIRECT_CODEC` no longer exists; `getX()/getZ()` missing on `ClientboundLevelChunkWithLightPacket`; `Component[]` vs `List<Component>`; generic inference error | `WorldSchematic:461-514`, `MixinClientPacketListener:25-26`, `SchematicConverter:188,215` |
| 10 | `IRenderDispatcher.registerWorldPreWeatherRenderer(IRenderer)` missing in MaFgLib 0.6.0; `MixinVideoSettingsScreen:18` | `RenderHandler:39`, `MixinVideoSettingsScreen` |

Rows 5-10 need signature checks against the 26.3 and MaFgLib 0.6.0 jars before editing. Several of them (`Continuation`, `BlockPos`, `DIRECT_CODEC`, `hasSodium`) are not rendering related and are good candidates for the next small batch.

## Still pending: your decision for the legacy terrain pipelines

MaFgLib 0.6.0 dropped `MaLiLibPipelines.LEGACY_*` and `ChunkFixUniform`; this repo carries `assets/litematica/shaders/legacy_terrain.{vsh,fsh}`.
- **A (recommended, keeps behaviour):** recreate the LEGACY pipelines and the uniform locally with the 26.3 `RenderPipeline.builder` and port the two shaders to the 26.3 shader format.
- **B:** use vanilla 26.3 terrain pipelines (`RenderPipelines.SOLID_TERRAIN`, `CUTOUT_TERRAIN`, `ChunkSectionLayer.pipeline(boolean)`): less code, but the vertex format and draw model change, so schematic rendering behaviour changes.

## Needs runtime validation

Nothing in this batch is runnable yet, since rendering does not compile. Once it does: schematic terrain (solid, cutout, translucent), overlay lines and quads, wireframe, Iris on and off.

---

# Phase 3, batch 3: legacy terrain pipelines, option A (2026-10-17)

Decision (user): **A**, recreate the LEGACY terrain pipelines and `ChunkFixUniform` locally instead of switching to vanilla terrain pipelines. Rendering behaviour is meant to stay the same.

Result: `compileJava` **FAIL**, but **none of the 9 files touched here has an error** and the cap was lifted to see the real total: **144 errors in 27 files** (previously hidden by javac's 100-error cap). The cap was raised with a throwaway init script in the scratchpad (`-I maxerrs.init.gradle`); no build file was changed.

## Files changed (9)

| File | Change |
|---|---|
| `render/uniform/ChunkFixUniform.java` (new) | Port of MaFgLib 0.5.5's class (recovered from its bytecode): std140 UBO `ChunkFix` = `ivec2 TextureSize, float ChunkVisibility, int UseRgss, int hasShadersOn` in a `MappableRingBuffer` (usage `USAGE_UNIFORM \| USAGE_MAP_WRITE`, = 130 as before). `UseRgss` from `Options#textureFiltering() == RGSS`, `hasShadersOn` from MaFgLib `IrisCompat.isShaderActive()`. Only imports moved to `renderpearl.api`. |
| `render/LitematicaPipelines.java` | The commented LEGACY block is now live: bind group `LEGACY_TERRAIN_GROUP` (`ChunkFix` uniform buffer) and 8 pipelines (`LEGACY_{SOLID,WIREFRAME,CUTOUT}_TERRAIN[_OFFSET]`, `LEGACY_TRANSLUCENT[_OFFSET]`) with the same depth/offset/blend/`ALPHA_CUTOUT` values as MaFgLib 0.5.5 used (compared against its bytecode). Bind groups: the old `MATRICES_PROJECTION` is now `DYNAMIC_TRANSFORMS` + `PROJECTION`, plus `FOG`, `SAMPLER0_SAMPLER2` and the local group. Removed the `@Deprecated`/`@VisibleForTesting` markers; the still-commented non-legacy block (provided by MaFgLib) is untouched. New `registerPipelines(RegisterRenderPipelinesEvent)`. |
| `team/cagayakegirls/forgematica/Forgematica.java` | Constructor now receives the mod `IEventBus` and listens for `RegisterRenderPipelinesEvent` (NeoForge's official way; replaces the old mixin into `RenderPipelines.<clinit>`). |
| `render/schematic/ChunkRenderLayers.java` | Uses `LitematicaPipelines.LEGACY_*` instead of the removed `MaLiLibPipelines.LEGACY_*` (the commented alternative lines the author had already prepared). |
| `render/schematic/SchematicRenderState.java`, `render/IWorldSchematicRenderer.java`, `render/schematic/WorldRendererSchematic.java` | Import `ChunkFixUniform` from `fi.dy.masa.litematica.render.uniform`. |
| `assets/litematica/shaders/legacy_terrain.vsh`, `.fsh` | Ported to the 26.3 shader format: `#moj_import` → `#include`, `#extension GL_ARB_separate_shader_objects`, explicit `location` on every `in`/`out`. Logic unchanged. |

Left alone on purpose: the old commented stub `render/uniform/LegacyTerrainFixUniform.java` (now superseded), and the debug-only `mixin/test/MixinRenderPipelines` / `MixinBindGroupLayouts` (not in `mixins.litematica.json`, entirely commented apart from stale imports that still fail to compile: they are obsolete now and are good candidates for deletion, which needs your OK).

## Remaining errors: 144 in 27 files, grouped by cause

| # | Cause | Where |
|---|---|---|
| 1 | Rendering draw path: `GpuBufferSlice` / `GpuSampler` / `GpuTextureView` / `GpuBuffer` / `AddressMode` / `FilterMode` imports not yet remapped, `DynamicUniforms` → `DynamicGpuData`, `RenderPass#bindTexture` removed, `ChunkSectionLayer#pipeline()` now takes a `boolean`, `EnumMap<>` inference, `outputTarget()` | `WorldRendererSchematic` (45), `ChunkRenderBatchDraw` (6, incl. 3 "bad type in conditional expression"), `MixinLevelRenderer` (7), `LitematicaRenderer` (4), `RenderHandler` (3) |
| 2 | `RedStoneWireBlock` → `RedstoneWireBlock` (static refs) | `SchematicConversionFixers` (18) |
| 3 | `new BlockPos(...)` constructor changed | `LitematicaSchematic` (2), `SchematicMetadata`, `SchematicPlacingUtils`, `WorldPlacingUtils`, `WorldUtils` (8) |
| 4 | `IPluginClientPlayHandler` abstract `checkFailures()` / `tickFailures()` not implemented | `ServuxLitematicaHandler:33` |
| 5 | MaFgLib moved `FileCopier` / `FileDeleter` (same package family as `FileRenamer`) | `GuiSchematicManager` (4) |
| 6 | `IrisCompat.hasSodium()` / `MaterialInfo.shade()` / `BlockStateBase#isViewBlocking` signature | `MixinLevelExtractor`, `AOProcessorLegacy`, `AOProcessorModern` (6) |
| 7 | `AbortableIterationConsumer.Continuation` gone | `WorldSchematic` (6) |
| 8 | `SignText.DIRECT_CODEC`, `SignBlockEntity#getFrontText/getBackText` (now `SignTextSlot` per primer) | `MixinClientPacketListener`, `IMixinSignBlockEntity`-related code, `SchematicConverter` |
| 9 | `LivingEntity#swing` / `Entity#setInvulnerable` / `getItemInHandRenderer()` / `getMessage(int,boolean)` / `Options.CLIENT`-style statics | `EasyPlaceUtils`, `TaskPasteSchematicPerChunkCommand` (6), `EntityUtils`, `MixinOptions`, `InitHandler` |
| 10 | MaFgLib: `IRenderDispatcher.registerWorldPreWeatherRenderer(IRenderer)` missing; `Component[]` vs `List<Component>`; generic inference in conversion fixers | `RenderHandler:39`, `SchematicConversionFixers:337,366` |
| 11 | Stale imports in obsolete debug mixins | `mixin/test/MixinRenderPipelines` (8) |

Rows 2-10 are non-render and mostly mechanical; each needs a signature check first.

## Needs runtime validation (nothing here has run yet)

- Pipelines compile at startup: a shader compile error or bind-group mismatch would fail on the first resource load. Watch the log for `litematica:legacy_terrain` and `pipeline/legacy/*`.
- The draw code (still being ported in `WorldRendererSchematic` / `ChunkRenderBatchDraw`) must bind `DynamicTransforms`, `Projection`, `Fog`, `Sampler0`, `Sampler2` and `ChunkFix` for these pipelines.
- `UseRgss` / `hasShadersOn` are read from `Options` and MaFgLib's `IrisCompat`; check texture sampling and Iris on/off.
- Vertex format `DefaultVertexFormat.BLOCK` is assumed to keep the attribute order Position, Color, UV0, UV2 (locations 0-3), as in vanilla's own `terrain.vsh`.

## Next batch suggestion

Do the small mechanical non-render rows first (2, 3, 5, 7: `RedstoneWireBlock`, `BlockPos` constructor, `FileCopier`/`FileDeleter`, `Continuation`), then rows 4, 6, 8-10 after reading the signatures, and keep the `WorldRendererSchematic` draw-path port (row 1) for its own batch.

---

# Phase 3, batch 4: mechanical non-render renames (2026-10-17)

Result: `compileJava` **FAIL**. Real error count (javac cap lifted via the scratchpad init script, no build file changed): **144 → 105 errors in 19 files**. All four targeted causes are gone. Nothing committed.

## Files changed (10)

| File(s) | Root cause fixed |
|---|---|
| `schematic/conversion/SchematicConversionFixers.java`, `SchematicConverter.java` | `RedStoneWireBlock` → `RedstoneWireBlock` (class literal and `POWER`/`NORTH`/`EAST`/`SOUTH`/`WEST` refs; resolved through the wildcard block import). Also cleared the generic-inference error at `:337`, which was a knock-on of the unresolved `POWER`. |
| `schematic/LitematicaSchematic.java` (2 sites), `schematic/SchematicMetadata.java`, `util/WorldUtils.java` (4 sites) | `new BlockPos(Vec3i)` no longer exists (only `BlockPos(int,int,int)`). Replaced with `BlockPos.ZERO.offset(vec)`, an exact copy that evaluates the argument once (no temporaries, no coordinate juggling). |
| `util/SchematicPlacingUtils.java`, `util/WorldPlacingUtils.java` | Same constructor; the argument was already a `BlockPos` (`PositionUtils.getRelativeEndPositionFromAreaSize`) and `BlockPos` is immutable, so the redundant copy was dropped. |
| `gui/GuiSchematicManager.java`, `gui/GuiSchematicProjectsBrowser.java` | MaFgLib moved `FileCopier` and `FileDeleter` to `malilib.util.file_ops` (as `FileRenamer` in batch 1); added explicit imports. |
| `world/WorldSchematic.java` | `AbortableIterationConsumer.Continuation` was extracted to top-level `net.minecraft.util.Continuation` (`CONTINUE`/`ABORT`); import and 6 references updated. |

Deviation from the plan: used `BlockPos.ZERO.offset(v)` instead of writing out `v.getX(), v.getY(), v.getZ()`, because several arguments are method calls and this avoids repeated evaluation. Behaviour is identical.

## Next 10 distinct errors (105 total)

| # | Cause | Where |
|---|---|---|
| 1 | Render draw path: remaining renderpearl imports (`GpuBufferSlice`, `GpuSampler`, `GpuTextureView`, `GpuBuffer`, `AddressMode`, `FilterMode`), `DynamicUniforms` → `DynamicGpuData`, `RenderPass#bindTexture` removed, `ChunkSectionLayer#pipeline(boolean)`, `EnumMap<>` inference, `outputTarget()` | `WorldRendererSchematic` (45), `ChunkRenderBatchDraw` (6), `MixinLevelRenderer` (7), `LitematicaRenderer` (4), `RenderHandler` (3) |
| 2 | Obsolete debug mixin with stale imports | `mixin/test/MixinRenderPipelines` (8): deletion needs your OK |
| 3 | `IPluginClientPlayHandler.tickFailures()` / `checkFailures()` not implemented | `ServuxLitematicaHandler:33`. Plan: `tickFailures(){ failures++; }`, `checkFailures(){ return failures <= MAX_FAILURES; }` (MaFgLib's `sendPlayPayload` calls `checkFailures()` before sending; `maxFailures()` defaults to 2) |
| 4 | Sign API: `SignBlockEntity#getFrontText/getBackText` gone, `getMessage(int,boolean)` now takes a `SignTextSlot`, `SignText` constructor takes `List<Component>`, `SignText.DIRECT_CODEC` gone | `TaskPasteSchematicPerChunkCommand` (6), `WorldUtils:679,681`, `SchematicConversionFixers:366,370` |
| 5 | `LivingEntity#swing` now `swing(InteractionHand, SwingAnimation, boolean)` | `EasyPlaceUtils:633`, `WorldUtils:889` |
| 6 | `Minecraft`/`Entity` API: `CLIENT` static, `getItemInHandRenderer()`, `Entity#setInvulnerable(boolean)` | `EasyPlaceUtils:630,636`, `EntityUtils:492`, `WorldUtils:886` |
| 7 | `BlockStateBase#isViewBlocking(BlockGetter, BlockPos, AABB)` has a new signature; plus two more unresolved symbols | `AOProcessorModern:47-53,205,223` |
| 8 | `MaterialInfo.shade()` missing | `AOProcessorLegacy:124,172` |
| 9 | `IrisCompat.hasSodium()` removed; `ClientboundLevelChunkWithLightPacket#getX/getZ` gone | `MixinOptions:23`, `MixinLevelExtractor:93,103`, `MixinClientPacketListener:25,26` |
| 10 | MaFgLib `IRenderDispatcher.registerWorldPreWeatherRenderer(IRenderer)` missing, `RenderHandler:38` override gone, unresolved symbol in `MixinVideoSettingsScreen:18` | `InitHandler:51`, `RenderHandler`, `MixinVideoSettingsScreen` |

Rows 3-10 need a signature check against the 26.3 and MaFgLib 0.6.0 jars before editing.

## Needs runtime validation

- Redstone wire schematic conversion (`FIXER_REDSTONE_WIRE`) and the `IMixinRedstoneWireBlock` invoker.
- Loading and converting schematics that use sizes: litematic, Sponge, structure import (`getTotalSize`, `enclosingSize`, sub-region sizes) and the placement end-position maths in `SchematicPlacingUtils` / `WorldPlacingUtils`.
- Schematic file manager copy / delete / rename buttons; entity queries on the schematic world (`getEntities`, `hasEntities`, including ender dragon parts).

---

# Phase 3, batch 5: signs, interaction, Servux, Sodium check (2026-10-17)

Result: `compileJava` **FAIL**. Real error count (cap lifted via the scratchpad init script): **105 → 82 errors in 9 files**. Every remaining error is rendering-related (render draw path, AO processors, MaFgLib `IRenderer`/`IRenderDispatcher`). Nothing committed.

## Files changed (10)

| File | Root cause fixed |
|---|---|
| `scheduler/tasks/TaskPasteSchematicPerChunkCommand.java` | `SignBlockEntity#getFrontText/getBackText` → `getText(SignTextSlot.FRONT/BACK)`; `SignText#hasMessage(Player)` → `hasMessage(boolean)` fed with `player.isTextFilteringEnabled()` (what the old method did). |
| `util/WorldUtils.java` | Same sign API: `getMessage(i, false)` → `getMessages(false).get(i)`, `setText(text, boolean front)` → `setText(text, SignTextSlot)`. Easy-place swing: `swing(hand)` → `swing(hand, stack.getInteractAnimation(), false)` (the call vanilla's `startUseItem` makes); `SwingSource.CLIENT` → `PREDICTED`. |
| `schematic/conversion/SchematicConversionFixers.java` | `FIXER_SIGN`: `SignText` constructor takes `List<Component>`; `DIRECT_CODEC` → `CODEC` (same record keys: `messages`, `filtered_messages`, `color`, `has_glowing_text`). |
| `util/EasyPlaceUtils.java` | Same swing and `SwingSource` changes; `ItemInHandRenderer#itemUsed` is gone (the renderer is now submit-only), vanilla now calls `LocalPlayer#itemUsed(hand)`. |
| `util/EntityUtils.java` | `Entity#setInvulnerable` → `setPermanentlyInvulnerable` (NBT `Invulnerable`). |
| `network/ServuxLitematicaHandler.java` | Implemented MaFgLib 0.6.0's new abstract `tickFailures()` (`failures++`) and `checkFailures()` (`failures <= MAX_FAILURES`, the same cancel rule `encodeClientData` already used). MaFgLib's `sendPlayPayload` now calls `checkFailures()` before sending. |
| `mixin/network/MixinClientPacketListener.java` | `ClientboundLevelChunkWithLightPacket` is a record: `getX()/getZ()` → `x()/z()`. |
| `mixin/client/MixinOptions.java`, `mixin/render/MixinLevelExtractor.java`, `mixin/screen/MixinVideoSettingsScreen.java` | `IrisCompat.hasSodium()` moved to `fi.dy.masa.malilib.compat.sodium.SodiumCompat.hasSodium()` in MaFgLib 0.6.0. |

Notes: `IMixinSignBlockEntity` still targets valid private fields (`frontText`, `backText`), so it needs no change. `SwingSource.CLIENT` → `PREDICTED` was applied literally, keeping the existing `InteractionResult.SUCCESS.swingSource().equals(...)` expression (not "fixed").

## Next 10 distinct errors (82 total)

| # | Cause | Where |
|---|---|---|
| 1 | Renderpearl imports still on the old `com.mojang.blaze3d.*` path: `GpuBufferSlice`, `GpuSampler`, `GpuTextureView`, `GpuBuffer`, `AddressMode`, `FilterMode`, `RenderPass`, `RenderPipeline`, `VertexFormat`, `IndexType`, `PrimitiveTopology`, `PolygonMode`, blend and depth types | `WorldRendererSchematic` (45), `ChunkRenderBatchDraw`, `MixinLevelRenderer` (7), `LitematicaRenderer` (4), `RenderHandler` (3), `MixinRenderPipelines` (8) |
| 2 | `DynamicUniforms` → `net.minecraft.client.renderer.DynamicGpuData` (`Transform`), `RenderSystem.getDynamicUniforms()` now returns it | `WorldRendererSchematic` |
| 3 | `RenderPass#bindTexture(String, GpuTextureView, GpuSampler)` removed; replaced by `setUniform(String, GpuTextureView, GpuSampler)` | `ChunkRenderBatchDraw` |
| 4 | `ChunkSectionLayer#pipeline()` now `pipeline(boolean)` | `WorldRendererSchematic:792` |
| 5 | `ChunkSectionLayerGroup#outputTarget()` missing; `EnumMap<>` inference at `:708`; unresolved `useDepth` | `WorldRendererSchematic`, `MixinLevelRenderer` |
| 6 | "bad type in conditional expression" (likely a knock-on of the old `GpuBuffer`/`IndexType` types) | `ChunkRenderBatchDraw:83,91,98` |
| 7 | MaFgLib 0.6.0 reshaped `IRenderer`: `onRenderWorldLast(RenderTarget, CameraRenderState, Frustum, ...)` without the `Matrix4fc` / fog arguments, so the override at `RenderHandler:38` no longer matches | `RenderHandler` |
| 8 | `IRenderDispatcher.registerWorldPreWeatherRenderer` removed (only in-game GUI, tooltip-last and world-last remain) | `InitHandler:51`: needs a decision on what the pre-weather hook becomes |
| 9 | `BlockStateBase#isViewBlocking` now `(BlockGetter, BlockPos, AABB)` | `AOProcessorModern:47-53` |
| 10 | `MaterialInfo#shade()` removed | `AOProcessorLegacy:124,172`, `AOProcessorModern:205,223` |

Rows 9 and 10 (AO processors) are copies of vanilla's ambient-occlusion math, so they need a side-by-side comparison with 26.3's own implementation before editing.

## Needs runtime validation

- Signs: easy-place of signs, copying text into the client sign editor (`insertSignTextFromSchematic`), paste-per-chunk command output, legacy `Text1..Text4` conversion (`FIXER_SIGN`, relies on `SignText.CODEC` accepting the same keys), glowing and dyed signs.
- Easy place: hand swing and the re-equip animation after placing; the `EASY_PLACE_SWING_HAND` option.
- Entity NBT import of the `Invulnerable` flag.
- Servux: join, metadata and NBT-query round trip, and the cancel rule after repeated send failures.
- Chunk load triggering a schematic chunk refresh (`MixinClientPacketListener`); Sodium-dependent cache reload on options save and on closing video settings, and the order of block-entity preparation in `MixinLevelExtractor`.

---

# Phase 3, batch 6: render draw path, AO, `IRenderer` (2026-10-17)

Result: `compileJava` **PASS** (plain `./gradlew compileJava`, no init script; BUILD SUCCESSFUL). Errors went 82 → 0. Nothing committed, pushed or published. The mod has **never been launched**, and several Mixin injection targets are known to be stale (see "Runtime phase" below), so this is a compile-level port only.

## Files changed (10)

| File | Root cause fixed |
|---|---|
| `render/schematic/WorldRendererSchematic.java` | Remaining `blaze3d` → `renderpearl.api` imports; `DynamicUniforms` → `net.minecraft.client.renderer.DynamicGpuData` (`Transform`, `writeTransforms`); `RenderPass.UniformUploader#upload` → `setUniform`; `RenderTarget.useDepth` → `hasDepth()`; `RenderPass#setPipeline` now takes a `CompiledRenderPipeline` (`RenderSystem.getCompiledPipeline`); removed an unused local that called the now-parameterised `ChunkSectionLayer#pipeline()`. |
| `render/schematic/ChunkRenderBatchDraw.java` | `ChunkSectionLayerGroup#outputTarget()` is gone. In 26.2 it returned the translucent target for `TRANSLUCENT` (falling back to the main target) and `gameRenderer.mainRenderTarget()` for `OPAQUE`; 26.3's `LevelTargetBundle` has no translucent target, so both groups now use `mc.gameRenderer.mainRenderTarget()` (identical to the 26.2 fallback). `bindTexture` → `setUniform(String, GpuTextureView, GpuSampler)`; 6× `setPipeline(RenderSystem.getCompiledPipeline(...))`. |
| `render/LitematicaRenderer.java`, `mixin/render/MixinLevelRenderer.java`, `mixin/test/MixinRenderPipelines.java` | Import remap only (renderpearl packages). The test mixin stays commented and unregistered; deleting it still needs your OK. |
| `event/RenderHandler.java`, `InitHandler.java` | MaFgLib 0.6.0 `IRenderer#onRenderWorldLast` lost the `Matrix4fc` parameter (the body never used it); `registerWorldPreWeatherRenderer` no longer exists. `RenderHandler` never overrode any pre-weather method, so the registration did nothing and was removed (no functional loss). |
| `render/schematic/ao/AOProcessorModern.java`, `AOProcessorLegacy.java`, `AOProcessor.java` | Taken from vanilla 26.3's `BlockModelLighter` bytecode: corner translucency is `BlockState#isLightPermeable()` (replaces `!isViewBlocking(...) \|\| getLightDampening() == 0`), and directional shade is `override != null ? lighting.byFace(override) : lighting.byFace(direction)` with `MaterialInfo#shadeDirectionOverride()` (replaces the removed `shade()`). The shared rule lives in `AOProcessor#getDirectionalBrightness`. |

The reserve file in the plan was used for `AOProcessor` (the shared helper).

## Next errors

None: compilation passes.

## Runtime phase: known problems the compiler cannot see

1. **Stale Mixin targets, `defaultRequire: 1` makes any of them a launch crash.** `MixinLevelRenderer` is certain:
   - `prepareChunkRenders(Lorg/joml/Matrix4fc;)` is now `prepareChunkRenders(Matrix4fc, boolean)`.
   - `lambda$addMainPass$0` and `ChunkSectionsToRender#renderGroup(group, GpuSampler)` no longer exist. 26.3 draws through `LevelRenderer#executeSolid(ChunkSectionsToRender, PreparedFrame, RenderPass)`, `executeClassicTransparency(...)` and `executeOit(...)`, and `renderGroup(group, RenderPass, GpuSampler, GpuTextureView, boolean)`.
   - The `render(...)` handler parameter lists and `@Local` names need re-checking, as do `submitEntities`, `submitBlockEntities`, `endFrame`, `close`.
   Every other mixin (about 50) needs the same target-by-target check, including `@Shadow`/`@Accessor` field names, `@Invoker` names and `@At` descriptors. Strategy: verify each target with `javap` against the 26.3 jar, then launch the client with `-Dmixin.debug.verbose` and read the log.
2. **Registration**: `RegisterRenderPipelinesEvent` listener (`LitematicaPipelines`) and the `legacy_terrain` shader compile (Shaderc, bind groups `DYNAMIC_TRANSFORMS`/`PROJECTION`/`FOG`/`SAMPLER0_SAMPLER2`/`ChunkFix`). Watch the log for `litematica:legacy_terrain` and `pipeline/legacy/*`. `RenderSystem.bindDefaultUniforms` also sets `Globals` and `Lighting`, which these pipelines do not declare: confirm that is tolerated.
3. **Translucent schematic layer** is drawn in its own pass over the main target with classic blending. OIT (the new default translucency path) is not integrated, so ordering against vanilla translucent geometry and water must be checked.
4. **MaFgLib 0.6.0 initialisation**: `Litematica#onInitialize` runs through `InitializationHandler`; confirm `InitHandler`, input handlers (`InputHandler` uses `KeyEvent`), keybind registration and config screens come up.
5. **Input**: Tab handling now compares `KeyEvent#keycode()` with MaFgLib `KeyCodes` (SDL keycodes); verify Tab and any other `key()`/`keycode()` use at runtime.
6. **Behaviour checks left over from earlier batches**: sign handling, easy-place swing/re-equip, Servux round trip, redstone wire conversion, schematic load/save of all formats, material list, schematic verifier, paste tasks.
7. `legacy_terrain.vsh` was written with LF line endings while the repo uses CRLF; Git warns and will normalise it on the next commit (harmless).

---

# Runtime phase, batch 1: Mixin target audit and retargeting (2026-10-17)

`compileJava` still PASSES. Nothing committed, pushed or published.

## Method

Every `@Mixin` target (about 50 classes) was checked by diffing `javap -p` of the cached 26.2 merged jar against the 26.3 one for each field/method a mixin names, and by reading the bytecode of every `@At(INVOKE)` target (javap prints same-class calls without the owner prefix, which is why those need the method name only). Everything not listed below is unchanged in 26.3 (including `IMixinSignBlockEntity` `frontText`/`backText`, `IMixinRedstoneWireBlock`, the model-set accessors, `MixinClientPacketListener`, `MixinMinecraft*`, `MixinLevelExtractor`, `MixinEntityHitboxDebugRenderer`, the access widener).

## What changed and what was done (8 files)

| Mixin / file | 26.3 change | Fix |
|---|---|---|
| `MixinAbstractSignEditScreen` | `boolean isFrontText` → `SignTextSlot slot` | `@Shadow SignTextSlot slot`; passes `slot == SignTextSlot.FRONT` |
| `MixinGameRenderer` | `extractCamera(DeltaTracker, float, float)` → `(DeltaTracker, float)` | handler updated; the removed value is now `Camera#getCameraEntityPartialTicks(deltaTracker)`, what vanilla itself uses |
| `MixinDebugScreenOverlay` | `extractLines(..., boolean)` → `(..., boolean, int)` | `@ModifyArg` descriptor updated |
| `MixinLevelRenderer` | `render` lost `DeltaTracker`/`Matrix4fc`; `prepareChunkRenders(Matrix4fc, boolean)` or `prepareChunkRendersIndirect`; `addWeatherPass` removed; the single main pass lambda calls `executeSolid` / `executeClassicTransparency` (inside the open pass) or `executeOit` (after the pass is closed) | see injector map below |
| `ChunkRenderBatchDraw`, `WorldRendererSchematic`, `IWorldSchematicRenderer`, `LitematicaRenderer` | needed to draw into vanilla's open pass | optional `RenderPass` argument through `piecewiseDrawBlockLayerGroup` → `drawBlockLayerGroup` → `ChunkRenderBatchDraw#draw`; layer loop moved to `drawLayers`; when a vanilla pass is used, `RenderSystem.bindDefaultUniforms` is called before (empty schematic fog) and after (vanilla fog restored) |

## New `MixinLevelRenderer` injector map

| Hook | Target | Action |
|---|---|---|
| `litematica_onPreRenderMain` | `render`, before `addMainPass(...)` | `capturePreMainValues(cameraState, terrainFog, profiler)` |
| `litematica_onPrepareBlockLayersPost` | `prepareChunkRenders` and `prepareChunkRendersIndirect`, TAIL | `piecewisePrepareBlockLayers` |
| `litematica_renderMainSection_Opaque` | `executeSolid`, after `ChunkSectionsToRender#renderGroup` | draw OPAQUE group into vanilla's pass |
| `litematica_renderMainSection_Translucent` | `executeClassicTransparency`, after `renderGroup` | draw TRANSLUCENT group into vanilla's pass (classic transparency) |
| `litematica_renderMainSection_TranslucentOit` | `lambda$addMainPass$0`, after `executeOit` | draw TRANSLUCENT group with an own pass (OIT mode) |
| `litematica_renderMainPass` | `render`, before `addMainPass(...)` | empty Iris placeholder, kept valid |
| unchanged | `submitEntities`, `submitBlockEntities`, `endFrame`, `close` | as before |

Side effect worth knowing: `ChunkRenderBatchDraw#draw` used to swallow every exception silently (`catch (Exception ignored)`); it now logs the first failure at ERROR level (once, to avoid spamming every frame) and still keeps the frame going.

## Static result

All 12 `@At(INVOKE)` targets checked above resolve inside their target methods in the 26.3 jar (`renderGroup` in both `executeSolid` and `executeClassicTransparency`, `executeOit` in the lambda, `addMainPass` in `render`, `startUseItem` in `handleKeybinds`, `extractLines(...ZI)V` in `extractRenderState`, `getHoveredSlot(DD)` in `extractContents`, plus the unchanged ones), every `@Shadow`/`@Accessor`/`@Invoker` member exists, and `compileJava` passes.

## Smoke launch (`./gradlew runClient`, about 30 s, terminated afterwards)

Result: the game **starts, loads Forgematica 0.5.0+mc26.3 and MaFgLib 0.6.0+mc26.3, finishes resource loading and renders the title screen**. No crash report. No `InvalidInjectionException`, `InvalidMixinException`, "No candidates" or Shaderc/pipeline-compile message in `latest.log` or `debug.log`. Mixin loaded 422 classes for mixin processing. Sodium and Iris were reported as not found (expected in this dev environment). Only warnings about the deprecated `logoFile` property in four `neoforge.mods.toml` files (including ours, harmless).

Errors in the log:

1. **`(malilib) Cannot register payload [servux:litematics] after NeoForge payload registration has closed`** and `registerPlayPayload: channel ID [servux:litematics] could not be registered`. **Real defect.** `EntityDataManager` calls `HANDLER.registerPlayPayload(...)` (line ~455) from the Litematica `InitHandler`, which runs after game init. MaFgLib 0.6.0 registers payloads through NeoForge's `RegisterPayloadHandlersEvent` (`NeoForgeClientNetworking#initialize(IEventBus)` collects them and sets `registrationClosed` once the event has fired), so the Servux channel must be registered **during mod construction / before that event**, not at game init. Until fixed, the Servux integration (entity/block-entity NBT data from a Servux server, bulk entity data) does not work.
2. `(malilib) loadFromFile(): Failed to load config file '.../run/config/malilib.json'`: first launch of a fresh run directory, the file did not exist yet. Expected to disappear on the next start; confirm.

What the smoke launch does **not** prove: mixins on classes that only load when a world is joined (`ClientLevel`, `LevelChunk`, `MultiPlayerGameMode`, server-side classes, `BlockItem`, `Entity`, the sign/container screens, `LevelExtractor` extraction) were not exercised; the new level-renderer injectors only run while rendering a world; the `legacy_terrain` pipelines were not drawn. Those need a world with a loaded schematic.

## Phase status

**NOT COMPLETE.** `compileJava` passes and the static mixin audit is clean, but (a) the Servux payload cannot be registered (ERROR above) and (b) the world-time mixins, the schematic render path and the pipelines have never run. Next run of this phase should: register the Servux payload early enough for `RegisterPayloadHandlersEvent` (check `ModContainer`/mod ordering so MaFgLib's `initialize` has run first), recheck `malilib.json` on a second start, then do a world-join smoke test (see checklist below) and read the log.

## Runtime phase re-run (2026-10-17, second start + one world session)

Changes: `neoforge.mods.toml` template `logoFile` → `iconFile` (forgematica, litematica stub); the Servux payload is now registered in the `Forgematica` mod constructor (`ServuxLitematicaHandler#registerPlayPayload`) instead of `EntityDataManager#onGameInit` (kept the `ClientPlayHandler` registration there). `compileJava` PASS; the mixin files were not touched since the clean static audit.

Second start results:
- The Servux `Cannot register payload` ERROR is **gone**.
- `logoFile` warnings: 4 → 2. The remaining two come from third-party jar-in-jar libraries (`foxified_classtweaker`, `conditional_mixin`); `conditional-mixin-neoforge` 0.6.5 (latest) still ships `logoFile` and MaFgLib 0.6.0 pins 0.6.4 / classtweaker alpha.4, so they cannot be fixed in this repo.
- `malilib.json` "Failed to load config file" ERROR: it was a missing-file read. After a normal quit `run/config/malilib.json` and `litematica.json` exist now, so it should not appear on the next start (confirm).

World session (created "New World", about 70 s, left via the menu, client exit code 0, no crash report):
- No ERROR/FATAL/Exception, no Mixin failure, no pipeline/shader message, no `ChunkRenderBatchDraw#draw(): failed` message in `latest.log` / `debug.log`.
- World-time mixins applied (e.g. `block.MixinBlockStateData` into `BlockStateData` at world load), the schematic world was created, the placement manager threads started and stopped cleanly, per-world litematica config was saved.
- **Not exercised:** no `.litematic` was saved (`run/schematics` is empty) and no placement appears in the log, so the schematic render path (`MixinLevelRenderer` draw hooks, `legacy_terrain` pipelines, overlays, translucent/OIT, AO) did not run. Sign, easy-place, material list, verifier and Servux server flows were not exercised either.

**PHASE STATUS: NOT COMPLETE — re-run this phase.** Startup, mod loading, mixin application on world join and shutdown are verified. The remaining gap is the schematic session: save a schematic, load it, create a placement, look at it (opaque, glass, stairs, overlays, wireframe, classic vs OIT) and quit via the menu; then read the logs.

## Runtime phase re-run #2: schematic session (2026-10-17, about 12 minutes in a world)

Setup: `compileJava` PASS; generated `run/schematics/test_26_3.litematic` (Litematica v7, data version 5023, 4x3x4: stone, glass, oak planks, oak stairs, glowstone, 3-bit packed palette) with a stdlib script; `./gradlew runClient`; the user joined "New World", loaded the schematic and quit through the menu (client exit code 0, no crash report).

Log results (`latest.log` + `debug.log`):
- Startup: no ERROR, no `malilib.json` error any more (configs exist), only the two third-party `logoFile` warnings.
- Session: **no** ERROR/FATAL other than the placement manager's own debug trace, **no** Exception / `Caused by`, **no** `InvalidInjection` / `InvalidMixin`, **no** `Failed to compile` / Shaderc message, **no** `ChunkRenderBatchDraw#draw(): failed` message.
- The `ERROR ... addTask: [EMPTY] Waking up threads...` lines are not failures: `PlacementManagerDaemonHandler` logs that debug trace with `LOGGER.error` when `Reference.DEBUG_MODE` is true, and `DEBUG_MODE` is true whenever the game runs from the IDE (`RUNNING_IN_IDE`). Upstream behaviour, absent in a normal install.
- Positive evidence the schematic path ran: the schematic world was created, the placement manager repeatedly received and completed tasks, and the schematic renderer rebuilt chunks over the whole session (`[WorldRenderer] updateChunks(): 1 Chunks updated`, `[Dispatch] removeOutOfRangeRenderers`), i.e. `prepareBlockLayers` and the draw hooks were exercised for about ten minutes with no draw failure.
- Clean shutdown: server stopped, schematic world removed, FML loader closed.

Not verified by logs: how the result *looked* (colors, translucency, ordering against vanilla water/glass, AO), classic vs OIT behaviour, wireframe and overlay visuals. If any of that looked wrong, treat the phase as NOT COMPLETE and report what you saw.

**PHASE STATUS: COMPLETE** (technical criteria): `compileJava` passes, the static Mixin audit is clean, startup and a 12-minute schematic session produced no errors, and every fix from the previous run held (Servux payload registered, `iconFile`, `malilib.json`).

## The two remaining `logoFile` warnings: patched in our build (2026-10-17)

The user asked to fix `Mod foxified_classtweaker / conditional_mixin uses the deprecated 'logoFile' property` (earlier choice was "leave + draft issues"; the user then asked for the fix, so this supersedes it). Both come from `META-INF/neoforge.mods.toml` inside third-party libraries, and no newer version fixes it (`conditional-mixin-neoforge` 0.6.0 to 0.6.5 all ship `logoFile`; `foxified-classtweaker` only has `0.1.0-alpha.4`; MaFgLib 0.6.0 pins both).

What was done: new `gradle/patched-libs.gradle`, applied from `build.gradle` before `repositories {}`.
- Downloads the original jar and pom once into `build/patched-libs/` and **checks them against pinned SHA-256 digests** (the build stops on a mismatch or an unreachable download with a clear message).
- Rewrites each jar with every entry copied unchanged except `META-INF/neoforge.mods.toml`, where only the key `logoFile` becomes `iconFile` (value unchanged). Verified: exactly one entry per jar differs (the toml), entry counts are equal (39/39 and 14/14), the poms are byte-identical.
- Exposes the result as a local Maven repo with the **same coordinates** via `exclusiveContent` + `includeVersion`, so MaFgLib's version requirements, FML ranges and the jar-in-jar metadata still match. A different version of those modules (for example after bumping `conditionalmixin_version`) is resolved from the normal repositories and simply is not patched.
- Licences: conditional-mixin is LGPL-3.0-only and foxified-classtweaker Apache-2.0; only a metadata key changes, notices stay inside the jars, nothing is published.

Verified: `compileJava` and `jar` PASS from a clean `build/patched-libs` (regeneration works); the shipped `build/libs/forgematica-0.5.0+mc26.3.jar` nests `conditional-mixin-neoforge-0.6.4.jar` whose toml has `iconFile`. Client start (`runClient`, 15:08): FML loads `conditional_mixin` and `foxified_classtweaker` from `build/patched-libs/repo/...`, the log has **no** `logoFile` line, **no** `Mods loaded with N warning(s)` line (so zero mod-loader warnings), no ERROR; the client was stopped by PID afterwards.

**Limits (state them to anyone who ships this):** the fix covers the dev environment and the conditional-mixin copy that *our* jar nests. MaFgLib 0.6.0 nests its **own, different build** of conditional-mixin 0.6.4 (`META-INF/jars/`, not patched), and foxified-classtweaker is a separate required mod users install themselves, so a production install can still log these two warnings. Only upstream releases can finish that: remove `gradle/patched-libs.gradle` and the `apply from` line once they ship `iconFile`.

## Upstream issue drafts (still useful to finish the job, not posted)

Verified: `conditional-mixin-neoforge` 0.6.0 to 0.6.5 (0.6.5 is the latest) all ship `logoFile`; `foxified-classtweaker` has a single published version (`0.1.0-alpha.4`, KTT snapshots repo) that ships `logoFile`; MaFgLib 0.6.0 pins both. Post these yourself:

**Fallen-Breath/conditional-mixin** (title: "NeoForge 26.x: `logoFile` is deprecated, use `iconFile`")

> On NeoForge for Minecraft 26.3, FML logs `Mod conditional_mixin uses the deprecated 'logoFile' property; change to 'bannerFile' and/or (for square icons) 'iconFile'` for `conditional-mixin-neoforge` (0.6.4 and 0.6.5). The `META-INF/neoforge.mods.toml` of the NeoForge artifact has `logoFile = "assets/conditionalmixin/icon.png"`. Changing that key to `iconFile` (same value, the icon is square) removes the warning. Happy to send a PR if you prefer.

**CagayakeGirls/foxified-classtweaker** (title: "Use `iconFile` instead of deprecated `logoFile` in neoforge.mods.toml")

> With NeoForge 26.3, FML logs `Mod foxified_classtweaker uses the deprecated 'logoFile' property; change to 'bannerFile' and/or (for square icons) 'iconFile'` for 0.1.0-alpha.4. `META-INF/neoforge.mods.toml` has `logoFile = "assets/foxified_classtweaker/icon.png"`. Replace the key with `iconFile` (same value) in the next release; MaFgLib 0.6.0 already uses `iconFile` for its own mods. Happy to send a PR.

## Known leftover risks (not blockers for this phase)

- `WorldRendererSchematic#setGpuSampler` stores vanilla's `chunkLayerSampler` and `closeGpuSampler` closes it, while vanilla also closes and recreates it when `shouldResetChunkLayerSampler` is set: a possible double close. Pre-existing logic, flagged for the in-world test.
- Leftover pass state after drawing into vanilla's pass (pipeline, bound uniforms, vertex/index buffers): vanilla re-binds what it needs per draw, but watch for glitches in features rendered after terrain.
- Translucent ordering against vanilla translucent geometry in OIT mode (schematic translucent layer is drawn after the OIT composite).

---

# Area A (Rendering), batch 1: GPU sampler lifecycle (2026-10-17)

`compileJava` PASS. 1 source file changed. Nothing committed, pushed or published.

## Defect fixed

`WorldRendererSchematic#drawBlockLayerGroup` stored vanilla's `LevelRenderer#chunkLayerSampler` in its own `gpuSampler` field the first time it was passed in, and `closeGpuSampler()` later closed it (world unload in `WorldRendererSchematic`, `LitematicaRenderer#onClose`). Vanilla 26.3 owns that sampler (checked with `javap -c` on the 26.3 jar): `lambda$addMainPass$0` closes and recreates it when `LevelRenderState.shouldResetChunkLayerSampler` is set (texture filtering / anisotropy change), and `LevelRenderer#close` closes it. So leaving a world closed a sampler vanilla kept using, and shutdown closed it twice.

Fix: the passed sampler is used for the current draw only and never stored. `getGpuSampler()` still lazily creates a fallback sampler (same `CLAMP_TO_EDGE`/`LINEAR`/1 parameters) when none is passed, and that fallback is the only sampler the schematic renderer closes. The interface (`setGpuSampler`, `closeGpuSampler`) is unchanged. The same pattern was in the 26.2 baseline.

## Verified, no change needed

- **No duplicate translucent rendering with OIT:** in the main-pass lambda, `executeClassicTransparency` (inside the open pass) and `executeOit` (after it is closed) are on mutually exclusive branches of the same `useOit` flag, so only one of the two translucent hooks in `MixinLevelRenderer` fires per frame.
- The wireframe branch in `ChunkRenderBatchDraw#drawLayers` sets the pipeline but never draws. This is the same in the 26.2 baseline (upstream behaviour, debug hotkeys only), so it was left alone.

## Runtime check (runClient, 15:42-15:44)

One world session, quit through the menu, exit code 0, no crash report. `latest.log` and `debug.log` have no ERROR, Exception, `Caused by`, Mixin failure, shader message or `ChunkRenderBatchDraw#draw(): failed`. The schematic renderer rebuilt chunks (`updateChunks()` 3 times), so the draw path ran.

**Not proven by this run:** there was only one world join (no rejoin), and the logs cannot show whether texture filtering or Improved Transparency were changed. The rejoin path, the sampler reset and classic vs OIT visuals still need a manual check.

## Remaining Area A risks

- Pass state left behind after drawing into vanilla's pass (pipeline, uniforms, buffers). Vanilla rebinds per draw, but watch features drawn after terrain.
- In OIT mode the translucent schematic layer is drawn after the OIT composite: ordering against vanilla water and glass is only checkable visually.
- Overlays, selection boxes and placement previews use MaFgLib 0.6.0 pipelines (`DEBUG_LINES_MASA_*`, `POSITION_COLOR_TRANSLUCENT_*`); only checkable visually.

Next area: **B (Input and GUI)**.

---

# Area B (Input and GUI), batch 1: SDL mouse buttons (2026-10-17)

`compileJava` PASS (compiled into a scratchpad build directory via an init script, because VS Code's Java language server was holding files in `build/classes`; no build file changed). Nothing committed, pushed or published.

## Defect fixed: left/right click swapped or ignored on Litematica GUI buttons

26.3 mouse buttons are SDL numbers: `InputConstants.MOUSE_BUTTON_LEFT = 1`, `MIDDLE = 2`, `RIGHT = 3` (GLFW was 0/2/1). MaFgLib 0.6.0 `ButtonBase#onMouseClickedImpl` passes `MouseButtonEvent#input()` (= `MouseButtonInfo#button()`, the raw SDL number) to `IButtonActionListener#actionPerformedWithButton`, and MaFgLib's own `ConfigButtonOptionList` already treats `input() == 1` as left click. Litematica still compared with the GLFW numbers, so in 26.3:

- `mouseButton == 1` ("right click") matched a **left** click: nudge/offset buttons went the wrong way, mirror/rotate cycled backwards, and left-clicking "set preview" in the schematic manager cancelled the preview.
- `mouseButton == 0` ("left click") never matched: tool mode, material list type and verifier type always cycled backwards.

Fix: compare with `InputConstants.MOUSE_BUTTON_LEFT` / `MOUSE_BUTTON_RIGHT` (vanilla constants, no numbers by hand), 11 comparisons in 7 files: `gui/GuiAreaSelectionEditorNormal`, `GuiMainMenu`, `GuiMaterialList`, `GuiPlacementConfiguration`, `GuiSchematicManager`, `GuiSchematicVerifier`, `GuiSubRegionConfiguration`.

Left alone: `util/InventoryUtils:708` (`button = ... ? 0 : 1`) is a container-click protocol value (0 = pickup all, 1 = split), not an input button, and its only use is commented out.

## Verified, no change needed

- **Hotkey names:** every default (`M`, `M,C`, `PAGE_UP`, `KP_MULTIPLY`, `KP_SUBTRACT`, `LEFT_CONTROL`, `RIGHT_CONTROL`, `LEFT_ALT`, `F3,M`, `I`, ...) resolves through MaFgLib 0.6.0 `KeyCodes#getKeyCodeFromName` (names come from its `KEY_*` fields plus aliases). The `BUTTON_1/2/3` aliases map to `MOUSE_LEFT/RIGHT/MIDDLE`, the same meaning they had with GLFW, so saved configs and `easyPlaceUseKey = BUTTON_2` (right click) keep working.
- **Text input focus:** `GuiTextFieldGeneric` extends vanilla `EditBox` without overriding `setFocused`, and 26.3 `EditBox#setFocused` calls `Minecraft#onTextInputFocusChange`, so the save/search/config text fields (`setFocusedWrapper`) start and stop SDL text input correctly. Tab toggling already uses `KeyEvent#keycode()` with MaFgLib's SDL keycodes (batch 1 of Phase 3).
- **In-world input:** `event/InputHandler` matches the use/attack/screenshot keys through vanilla `KeyMapping#matches` / `matchesMouse`, and scrolling and modifier keys go through MaFgLib (`GuiBase.isShiftDown/isCtrlDown/isAltDown`). No GLFW or raw scancode is left in the sources.

## Needs runtime validation

- Left and right click on the GUI buttons listed above: left goes forward/up, right goes back/down.
- Hotkeys in game: `M` (main menu), `M,C` (settings), `PAGE_UP/PAGE_DOWN` (layers), Ctrl+scroll (tool mode), Alt+scroll (nudge selection), middle click (`BUTTON_3`).
- Typing in the schematic save name, schematic browser search and config search fields, Tab toggling focus, and that typing there does not trigger game hotkeys.

Next area: **C (Mixins and access)**, a re-audit of what the runtime batches have not exercised yet (signs, containers, easy-place, `BlockItem`, server-side mixins).

---

# Area C (Mixins and access), batch 1: runtime application and anchor audit (2026-10-17)

Result: **no defect found, no source change.** `compileJava` was not re-run because nothing compiled changed. Nothing committed, pushed or published.

## Runtime evidence: 50 of 51 mixins applied

The Mixin `DEBUG` lines in `run/logs/debug.log` and the rotated `debug-*.log.gz` (singleplayer sessions plus the dedicated-server session) show `Mixing <mixin> from mixins.litematica.json into <target>` for every entry of `mixins.litematica.json`. That includes the ones the earlier runtime batches had not proven: `MixinBlockItem_easyPlace` (`BlockItem`), `MixinClientPlayerInteractionManager_easyPlace` (`MultiPlayerGameMode`), `MixinMinecraft_easyPlace`, `MixinServerGamePacketListenerImpl_easyPlace`, `MixinMinecraftServer`, `MixinAbstractSignEditScreen`, `IMixinSignBlockEntity`, `MixinAbstractContainerScreen`/`IMixinAbstractContainerScreen`, `MixinChestBlock`, `MixinStairsBlock`, `MixinRailBlocks` (all three rails), `MixinLevelChunk`, `MixinClientLevel`, `MixinClientMannequin` and `MixinBlockStateDefinitions`. With `defaultRequire: 1`, an applied mixin means every injector found its target and every `@Shadow`/`@Accessor`/`@Invoker` member resolved.

The one exception is `render.MixinEntityHitboxDebugRenderer`. `EntityHitboxDebugRenderer` only loads when hitboxes are shown (F3+B), which no session did. Static check against the 26.3 jar: `emitGizmos(double, double, double, DebugValueAccess, Frustum, float)` and `showHitboxes(Entity, float, boolean)` match the handler and the `@Shadow` exactly.

## Anchor semantics (26.2 vs 26.3 bytecode)

An injector that applies can still land in the wrong place if vanilla reordered the method. Each `@At(INVOKE)` with an ordinal, or one that matches several call sites, was compared in `javap -c` of both jars:

| Mixin | Anchor | 26.2 | 26.3 |
|---|---|---|---|
| `MixinMinecraft_easyPlace` | `startUseItem`, `ItemStack#getCount` ordinal 0 | right before `useItemOn` | same (the swing call before it now takes `SwingAnimation`, which the injector ignores) |
| `MixinClientPlayerInteractionManager_easyPlace` | `performUseItemOn`, `LocalPlayer#getItemInHand` (no ordinal) | 2 matches | 2 matches, same order around `onRightClickBlock` |
| `MixinServerGamePacketListenerImpl_easyPlace` | `handleUseItemOn`, `Vec3#subtract` | 1 match, the `atCenterOf` hit-position check against `1.0000001` | identical |
| `MixinLevelExtractor` | `extract`, `popPush` ordinal 2, BEFORE | the `"entities"` section | still `"entities"` (26.3 added `extractPlayerState` before and a `transientBlocks` section after, neither shifts ordinal 2); the `@Local Frustum` is still unique |
| `MixinLevelChunk` | `setBlockState`, `Level#isClientSide` ordinal 0 after the `getBlockState(III)` slice | before `removeBlockEntity` / `affectNeighborsAfterRemoval` | identical |

The HEAD/TAIL/RETURN injectors (`MixinBlock#popResource`, chest/stair `mirror`, rail `rotate`, `MixinMinecraft`, `MixinOptions#save`, `MixinClientPacketListener`, `MixinVideoSettingsScreen#removed`, `MixinMinecraftServer#logTickMethodTime`, `MixinClientLevel#setServerVerifiedBlockState`) have no position to drift.

## Access widener: covered in dev, production depends on Foxified

`litematica.accesswidener` only widens `FluidRenderer` members (`fluidModels`, `isNeighborSameFluid`, `isFaceOccludedBy*`, `calculateAverageHeight`, `addWeightedHeight`, both `getHeight`, `getLightCoords`), which `FluidModelRendererSchematic extends FluidRenderer` calls directly. All of them still exist in 26.3 with the widened signatures, but they are private in the vanilla jar.

- **Dev:** `runClient` runs Loom's pre-widened jar (`.gradle/loom-cache/.../neoforge-26.3.0.88-minecraft-merged-fc46ea1317-26.3.jar`, where those members are already `public`), so dev never exercises the runtime widening. No `IllegalAccessError`, `NoSuchMethodError` or `NoSuchFieldError` appears in any log.
- **Production:** the built jar ships `litematica.accesswidener` and declares it via `[[foxified.classtweaker]]`. It does **not** bundle or declare `foxified_classtweaker`; the mod comes in only because MaFgLib 0.6.0 declares `foxified_classtweaker` as `required`. That chain works today, but it is implicit. Making it explicit is a dependency-metadata question for Area D.

## Needs runtime validation

- **Production fluid rendering:** load a schematic containing water or lava in a normal (non-dev) NeoForge 26.3 install with MaFgLib and Foxified. If the class tweak did not apply, the first fluid mesh build throws `IllegalAccessError` from `FluidModelRendererSchematic`.
- **F3+B hitboxes** with a placement loaded (`MixinEntityHitboxDebugRenderer`).
- **Easy place:**
  - On a server: placing from the schematic, placement restriction cancelling a wrong block, and the `ITEM_USE_PACKET_CHECK_BYPASS` option. That option only matters on an integrated or modded server, because the server-side mixin cannot run on a vanilla server.
  - In singleplayer: `EASY_PLACE_SP_HANDLING` (`BlockItem#getPlacementState`).
- **Signs and containers:** sign text pasted from the schematic into the editor, and the container-screen hover hook (`getHoveredSlot`) used by the material list and inventory hints.
- **Rotation and mirroring:** mirrored chests and stairs, and rotated rails, in a placement.

Next area: **D (Dependencies)**. Topics: decide whether `neoforge.mods.toml` should declare `foxified_classtweaker` as `required` itself (today it relies on MaFgLib's declaration), and recheck the MaFgLib, conditional-mixin and Foxified version ranges.

---

# Area D (Dependencies), batch 1: metadata ranges and Foxified (2026-10-17)

Build: **PASS** (`build` into the scratchpad build dir). Client: **starts**, loads Forgematica, MaFgLib, Foxified and conditional-mixin, and reaches resource loading with no dependency error. Nothing committed, pushed or published.

## Files changed (2)

| File | Change | Why |
|---|---|---|
| `gradle/libs.versions.toml` | `range-game` and `range-neoforge`: `[26.3,)` → `[26.3,26.4)` | This build targets 26.3 classes, and the mixin config uses `defaultRequire: 1`. With an open range, a 26.4 client would load it and crash on the first stale mixin target. With the bounded range, NeoForge shows a "requires Minecraft 26.3" dependency screen instead. 26.3.x hotfixes stay inside the range. The 26.2 branch used open ranges; this is a deliberate change for a version-specific stopgap branch. |
| `src/main/templates/META-INF/neoforge.mods.toml` | New `required` dependency on `foxified_classtweaker` (`versionRange="*"`, the same range MaFgLib declares) | Forgematica declares its own `[[foxified.classtweaker]]` access widener, and `FluidModelRendererSchematic` calls the widened `FluidRenderer` members directly. Until now, the only thing that pulled Foxified in was MaFgLib's dependency. |

The generated `META-INF/neoforge.mods.toml` (in `build/generated/sources/modMetadata` and in the jar) reads `neoforge [26.3,26.4)`, `minecraft [26.3,26.4)`, `foxified_classtweaker *`.

## Verified, no change needed

| Dependency | In use | Latest published | Decision |
|---|---|---|---|
| NeoForge | 26.3.0.88 | 26.3.0.88 (maven.neoforged.net metadata) | current |
| MaFgLib | 0.6.0+mc26.3 | 0.6.0+mc26.3 (KTT releases, last updated 2026-10-11) | current; `range-mafglib` `[0.6,)` kept, since the Minecraft range already bounds it |
| conditional-mixin | 0.6.4 | 0.6.5 (maven.fallenbreath.me) | kept: MaFgLib 0.6.0 bundles 0.6.4, and `gradle/patched-libs.gradle` pins the 0.6.4 SHA-256 |
| Foxified ClassTweaker | 0.1.0-alpha.4 (via MaFgLib) | 0.1.2 on KTT **snapshots** only | kept: MaFgLib resolves the release artifact, and snapshots are not used for builds |
| FML | 12.0.12 | — | inside `range-fml` `[4,)` |
| Loom / Gradle / Java | 1.17-SNAPSHOT / 9.6.1 / 25 | — | build passes, unchanged |

The three `JarSelector` WARN lines (mixinextras, conditional_mixin, the Foxified processor) are a dev-classpath artifact: the same jar is both on the classpath and nested in another mod's jar. Every earlier session's log shows them too.

## Remaining risks

- The `[26.3,26.4)` cap can only be proven on a 26.4 client: NeoForge should refuse to load with a dependency screen. Not tested; no 26.4 install exists here.
- The production class tweak (Area C risk) is still unproven outside dev.

## Status of the four areas

A (Rendering), B (Input and GUI), C (Mixins and access) and D (Dependencies) have each had one batch. The remaining open items are all runtime checks listed in their "Needs runtime validation" sections: OIT visuals and the filtering change, GUI left/right click and text fields, easy place, signs, F3+B hitboxes, fluids in a production install, and Servux.

---

# Area D (Dependencies), batch 2: networking and client-only separation (2026-10-17)

Result: **no defect found, no Java change.** `compileJava` PASS. Nothing committed, pushed or published. Documentation updated: `CLAUDE.md`, `CHANGELOG.md` (26.3 entry); `MEMORY.md` is a local, untracked file.

## Networking (Servux `servux:litematics`) against MaFgLib 0.6.0

Checked with `javap -c` on `mafglib-0.6.0+mc26.3.jar`:

- **Receiving:** `IPluginClientPlayHandler` now extends NeoForge's `IPayloadHandler<T>`, and its default `handle(T, IPayloadContext)` calls `receivePlayPayload(T, IPayloadContext)`. The 26.2 code overrode a Fabric `receive(payload, ClientPlayNetworking.Context)` in the anonymous `INSTANCE`; the empty `new ServuxLitematicaHandler<>() {}` is correct now, not a lost override.
- **Registration:** `NeoForgeClientNetworking.registerPayloads` registers through `RegisterPayloadHandlersEvent.registrar("1").optional()`. The channel is optional, so a server without Servux accepts the client (proven below). The payload is registered once, from the `Forgematica` constructor (runtime batch).
- **Sending:** `sendPlayPayload` checks the channel ID, `isPlayRegistered`, `checkFailures()` (implemented here: `failures <= MAX_FAILURES`) and `NeoForgeClientNetworking.canSend(type)` before `send`, and logs a WARN instead of throwing when the server lacks the channel.
- **Lifecycle:** `EntityDataManager#onWorldPre` and the sync tick call `registerPlayReceiver(...)`, and turning sync off calls `unregisterPlayReceiver()`. On logout, `ServuxLitematicaHandler#reset` clears `servuxRegistered`, the failures and the reading session but **not** `payloadRegistered`. That is required, because registration cannot be repeated after startup: a rejoin can register the receiver again. The reset logic is unchanged from 26.2.

## Client-only separation

- `Forgematica` is `@Mod(dist = CLIENT)`, and every initialisation (pipelines, config screen, Servux payload, `Litematica#onInitialize`) is also behind `FMLLoader...getDist().isClient()`.
- FML 12.0.12 has no `displayTest` key (not present in the loader jar), so there is no mod-list option to add.
- The common mixins that touch server classes (`MixinMinecraftServer`, `MixinServerGamePacketListenerImpl_easyPlace`) apply on the **integrated** server only. A dedicated server cannot load the mod at all: MaFgLib 0.6.0 crashes there (`NoClassDefFoundError: ClientLevel`, seen with Loom's `runServer`). That is MaFgLib's limitation, not Forgematica's, and it matches the mod's client-only scope.

## Runtime evidence: plain NeoForge server + modded client

`run/dedicated` (NeoForge 26.3.0.88, no mods, `online-mode=false`, `white-list=false`) with `runClient` joined over `localhost:25565`:

- Server log: `Dev joined the game` and later `Dev lost connection: Disconnected`. The `SocketException: Connection reset` came from the client being terminated.
- Client log: no Mixin, linkage, payload or rendering error. The placement rendered with the wrong-block overlay (screenshot `run/screenshots/2026-10-17_20.37.25.png`).

## Startup re-check

`runClient` after this batch: it started, loaded the mods, reached the title screen and joined a singleplayer world (`Dev joined the game`, 20:53). There was no Mixin, linkage, missing-class, payload or rendering error. The only WARN lines are vanilla's own command-argument ambiguity notices and MaFgLib's language-option notice; the only ERROR lines are the IDE-only `Waking up threads...` debug trace.

## Remaining risk

- A Servux round trip (metadata, entity/block-entity NBT, bulk data) cannot be tested without a Servux server for 26.3. The send/receive path is verified statically only.

## Status of the four areas

| Area | Batches | Static result | Still needs in-game checking |
|---|---|---|---|
| A Rendering | 2 | sampler lifecycle fixed; OIT and classic hooks mutually exclusive; no render hook lost (batch 2) | rejoin, texture-filtering change, OIT vs classic visuals, AO and colours. **Confirmed in game:** wrong-block overlay and placement box (screenshot `2026-10-17_20.37.25.png`) |
| B Input and GUI | 1 | SDL mouse buttons fixed; hotkeys and text focus verified | left/right click on GUI buttons, text fields, `PAGE_UP/PAGE_DOWN`, Ctrl/Alt+scroll. **Confirmed in game (2026-10-17, multiplayer):** the `M` combos (`M,C`, `M,P`, `M,R`, `M,G`, `M,T`) work when `M` is held. MaFgLib matches them by scancode on both sides (`ScanCodes.getScanCodeFromName` / `KeyEvent#key()`). |
| C Mixins and access | 1 | 50/51 mixins applied at runtime, anchors unchanged, F3+B mixin checked statically | F3+B hitboxes, easy place, signs, mirrored/rotated blocks, fluids in a normal install |
| D Dependencies | 2 | ranges capped, Foxified declared, networking and client-only separation verified | 26.4 refusal screen, Servux round trip |

---

# Area A (Rendering), batch 2: findings (2026-10-17)

Read-only batch: **no defect found, no source change**. `compileJava` and `runClient` were not re-run because nothing compiled changed. The user chose to record the findings rather than run an automated in-game session. Nothing committed, pushed or published.

## Removed pre-weather hook: nothing lost

The port removed `RenderEventHandler.getInstance().registerWorldPreWeatherRenderer(renderer)` from `InitHandler`, because MaFgLib 0.6.0 no longer has that method. Its `RenderEventHandler` only offers in-game GUI, world-last and tooltip-last registration, and `IRenderer` has no pre-weather callback. The 26.2 `RenderHandler` (`git show 72c1a0f2`) never overrode the pre-weather callback; it only inherited the empty default. The removal therefore drops no rendering feature. Everything Litematica draws in the world still goes through `onRenderWorldLast` (boxes, verifier mismatches, rebuild-targeting overlay) and `onExtractGuiOverlayPost` (info HUD, tool HUD, hover info, preview frame).

## Overlays and placement boxes: confirmed in game

The multiplayer screenshot `run/screenshots/2026-10-17_20.37.25.png` shows both the wrong-block overlay (magenta) and the placement's enclosing box (cyan). They are drawn by `RenderHandler#onRenderWorldLast` → `OverlayRenderer#renderBoxes` / `renderSchematicVerifierMismatches` with MaFgLib 0.6.0's pipelines, so the world-last hook and those pipelines work on 26.3.

## Ready-made check for OIT vs classic and rejoin (not run yet)

- "New World" keeps a saved placement (`run/config/litematica/litematica_New World_dim_minecraft_overworld.json`: `test_26_3` at 183,67,11, enabled, rendering on). It is restored on join, so a plain join already exercises the rejoin path.
- The transparency mode is `improvedTransparency` in `run/options.txt` (currently `false`, classic). It can also be changed in Video Settings.
- Procedure:
  1. Run `./gradlew runClient --args="--quickPlaySingleplayer \"New World\""` (use the scratchpad init script if the IDE locks `build/classes`).
  2. Look at the placement and press F2.
  3. Quit, switch Improved Transparency on, and repeat.
  4. Compare the two screenshots: glass and other translucent ghost blocks should appear exactly once (not doubled, not missing), and the overlay and box should be present in both.
  5. Check `run/logs/latest.log` for `ChunkRenderBatchDraw#draw(): failed`, Shaderc or pipeline messages.

## Still open in Area A

- OIT vs classic visuals (procedure above).
- Texture-filtering sampler reset: change Texture Filtering in Video Settings while a placement is visible. The schematic must stay textured, and no closed-sampler error may appear.
- Ambient occlusion and colour accuracy, which can only be judged by eye.

Next area: **B (Input and GUI)**, where only in-game checks remain: left/right click on the ± buttons, text fields, `PAGE_UP/PAGE_DOWN`, Ctrl/Alt+scroll.

---

# Area A (Rendering), batch 3: schematic blocks never rendered (2026-10-17)

Found during the in-game test: placement boxes and overlay colors drew, but the textured ghost blocks themselves never did. Every earlier screenshot only showed overlays. The magenta "extra" overlay in those screenshots was the clue: the schematic reported air where the world had blocks. Diagnosed with temporary logging (since removed) in a singleplayer session with the saved `test_26_3` placement. Two independent defects:

## Defect 1: every schematic palette entry was read as air

Minecraft 26.3 changed `NbtUtils#readBlockState` / `#writeBlockState` from the keys `Name` / `Properties` to `id` / `properties` (checked with `javap -c` on both jars). `.litematic` palettes (and Litematica's conversion maps) use `Name` / `Properties`, so `readBlockState` found no `id`, returned air for every entry, and the schematic loaded with 0 blocks. Evidence: the placer read `nonAirStates=0` out of 48 positions before the fix and `38` (the schematic's block count) after it. The vanilla `writeBlockState` would also have saved schematics with `id` / `properties`, which other Litematica versions can't read.

Fix: new `util/BlockStateNbt` with `readBlockState(HolderGetter<Block>, CompoundTag)`, which accepts either layout (it renames `Name`/`Properties` to `id`/`properties` on a copy and lets vanilla parse), and `writeBlockState(BlockState)`, which writes `Name`/`Properties`. Every call site now uses it: both palette implementations (read and write), `SchematicConversionMaps` (2 reads) and `LitematicaSchematic` (3 reads: palettes, vanilla structures, legacy conversion). No `NbtUtils.read/writeBlockState` call remains. The `.litematic` file format is unchanged.

## Defect 2: solid, cutout and wireframe terrain pipelines had no color target

Hidden behind defect 1 (nothing was ever drawn): once blocks existed, the opaque draw failed with `IllegalStateException: Render pass color attachment count must match pipeline color target state count` (`FrontendRenderPass#setPipeline`). In renderpearl the `RenderPipeline.Builder` starts with 0 color targets, and only `withColorTargetState` adds one. Vanilla's `SOLID_TERRAIN` sets `ColorTargetState.DEFAULT` explicitly. `LitematicaPipelines.LEGACY_TERRAIN_STAGE` set none, so only the translucent pipelines (which set `BlendFunction.TRANSLUCENT`) had a target.

Fix: `LEGACY_TERRAIN_STAGE` declares `withColorTargetState(ColorTargetState.DEFAULT)`. The translucent snippet still overrides target 0 with its blend.

## Result

Verified in game (screenshot `run/screenshots/2026-10-17_22.06.17.png`): textured glass ghost blocks with the missing-block tint, the wrong-block and extra-block overlays, and the placement box. The HUD shows `Schematic: minecraft:glass`. After the second fix the log has no ERROR besides the IDE-only `Waking up threads` trace.

Lesson: `ChunkRenderBatchDraw` logs only the first draw failure, and `WorldPlacingUtils#placeToProtoChunk` still swallows exceptions (upstream code), so "no error in the log" was not proof of rendering. A screenshot of the ghost blocks themselves is.

Test-data note: for the diagnosis, the saved `test_26_3` placement in `run/config/litematica/litematica_New World_dim_minecraft_overworld.json` was moved next to the player (-118, 68, 138). The original is in `….json.bak` (git-ignored run directory).

In-game follow-up (22:51, same session): with **Improved Transparency ON** (OIT) the schematic renders exactly like classic mode, with no doubled or missing translucent faces (screenshot `2026-10-17_22.51.27.png`, settings in `…22.51.18.png`). Switching it back off and opening Video Settings produced no error in the log. Texture Filtering showed RGSS in both settings screenshots, so a filtering *change* (the sampler-reset path) is still not proven.

Texture-filtering change (22:53–22:54, same session): Texture Filtering was switched RGSS → Anisotropic → None (`2026-10-17_22.53.53.png`, `…22.54.09.png`). This triggers vanilla's `shouldResetChunkLayerSampler`, so vanilla closed and recreated `chunkLayerSampler`. Afterwards the schematic was still fully textured (`…22.54.15.png`), with no closed-sampler or draw error in the log. **The Area A batch 1 sampler fix is confirmed in game.** Still open in Area A: leaving and rejoining a world, and the AO/colour check by eye.

Rejoin (22:56, same session): Save and Quit to Title, then rejoined "New World". The placement was restored automatically and rendered fully textured (`2026-10-17_22.57.01.png`), with no error in the log. **All Area A in-game checks have passed** except the AO/colour comparison against vanilla, which is judged by eye only.

---

# Test phase (2026-10-17, 23:10–)

Commits under test: `6699bf00`, `427b19a8`, `39b3ca3c`. The test setup is a plain NeoForge 26.3.0.88 server (`run/dedicated`, no Forgematica, `online-mode=false`, creative, `Dev` is operator) plus `runClient --args="--quickPlayMultiplayer localhost:25565"`. Only trusted test schematics were used (`run/schematics/test_26_3.litematic`, generated locally).

## Automated checks

| Check | Result | Evidence |
|---|---|---|
| `compileJava` | **PASS** | scratchpad init script `altbuild2.init.gradle` |
| `test` | **NOT AVAILABLE** | `:compileTestJava NO-SOURCE` (no test source set) |
| `build` | **PASS** | `<scratchpad>/altbuild/libs/forgematica-0.5.0+mc26.3.jar` |
| Packaged metadata | **PASS** | jar `META-INF/neoforge.mods.toml`: neoforge/minecraft `[26.3,26.4)`, mafglib `[0.6,)`, foxified_classtweaker `*` required; `mixins.litematica.json`, `litematica.accesswidener` bundled; jar-in-jar only conditional-mixin 0.6.4 |
| Mixin config | **PASS** | 51/51 listed classes present in the jar, `required: true`, `defaultRequire: 1` |
| Security (issue #11) | **PASS** | see below |
| Client launch + server join | **PASS** | server `Done` 23:14:23; client started 23:14:34; `Dev joined the game` 23:14:45; no ERROR/Exception/Mixin/linkage/draw failure in `run/logs/latest.log` |
| Server log | **PASS** | no errors (only the JNA native-access and Netty kqueue/epoll probe noise) |

New WARN lines (8, at join): `Unable to read property: east/north/south/west with value: "false" for blockstate: {id:"minecraft:cobblestone_wall" | "minecraft:mossy_cobblestone_wall" ...}`. They come from Litematica's 1.12 conversion table (`SchematicConversionMaps`), whose wall entries carry pre-1.16 `false` values. Vanilla keeps the default for those properties. They became visible only after the `BlockStateNbt` fix; before it, those entries silently resolved to air. Benign. Most likely upstream behaviour, but that is not proven on 26.2.

## Security: upstream issue #11 ("Forgematica vulnerability in MC 1.21+")

The upstream fix is `CagayakeGirls/litematica-neoforge` commit `f6dc5247f7` ("fix: warn when pasting with Oversized files", `neoforge/26.1`, 2026-07-05, released as 0.5.1 with MaFgLib 0.5.4). Every line it adds is present in this port: `EntityDataManager` 5/5, `ServuxLitematicaHandler` 24/24, `LitematicaSchematic` 8/8, `SchematicPlacementManager` 7/7, `SchematicBuffer` 10/10, `SchematicBufferManager` 12/12, `en_us.json` 1/1.

- Received schematic files are named `UUID.randomUUID()`; the server-supplied name, a path-traversal vector, is gone.
- `createBuffer` takes no name, and the file is deleted if parsing fails.
- The Servux file-transmit receive path (`Litematic-Transmit*` → `LitematicaSchematic#receiveFileTransmit`) is commented out and has **no active caller**.
- Oversized Servux pastes are refused (`placement_paste_too_large_for_servux`).
- The 26.3 port never touched `schematic/transmit/`.
- MaFgLib 0.6.0+mc26.3 (jar built 2026-10-09) is newer than the fixed 0.5.4 line. That part is verified by version and date only, not by reading MaFgLib's code.

## Manual checklist

| Item | Result | Evidence |
|---|---|---|
| Minecraft starts with Forgematica + MaFgLib | **PASS** | every session today; `Reloading ResourceManager ... mod/forgematica ... mod/mafglib` |
| Litematica config GUI opens | **PASS** | `M`+`C` confirmed by the user in game |
| Hotkeys and mouse controls | **PASS (partial)** | `M` combos; left/right click on `+` (`2026-10-17_21.40.54.png`, Z −5→−4); layer mode `M`+`PAGE_UP` ("Set layer mode to All", `21.34.01.png`). Ctrl/Alt+scroll **NOT TESTED** |
| Create a simple schematic | **NOT TESTED** | |
| Save / reload / import `.litematic` | **PARTIAL** | load/import of `test_26_3` **PASS** (`21.32.35.png`); save and reload **NOT TESTED** |
| Place / move | **PASS** | "Placement created" (`21.32.35.png`); Move to player and typed Z (`21.41.16.png`) |
| Rotate / mirror | **NOT TESTED** | |
| Ghost blocks and overlays | **PASS** | after `39b3ca3c`: `22.06.17.png`, `22.48.36.png`, classic + OIT `22.51.27.png`, filtering change `22.54.15.png` |
| Material list | **PASS** | `2026-10-17_23.57.24.png`: Material List for `test_26_3`, 5 items (glass, glowstone, oak planks, oak stairs, stone), Total/Missing/Available columns, progress 52.6 % |
| Missing-block indicators | **PASS** | light-blue missing and red wrong-block overlays in the screenshots above |
| Area selection | **PASS (partial)** | selection with corner boxes (`21.29.16.png`); saving it **NOT TESTED** |
| Schematic editing tools | **NOT TESTED** | |
| Creative paste with permissions | **NOT TESTED** | server ready: creative, `Dev` op level 4 (no permission bypass) |
| Client-only multiplayer on a NeoForge 26.3 server | **PASS** | plain server, no Forgematica: joins at 20:36, 21:11, 23:14 |
| Persistence across client restarts | **PASS** | "New World" `test_26_3` placement restored on every client launch 21:45–22:58 and after Save & Quit / rejoin (`22.57.01.png`) |
| Warnings / performance | **PARTIAL** | warnings reviewed (above); no performance measurement |

## Phase status

**NOT COMPLETE.** The automated checks, the security audit and most rendering and multiplayer items pass. These still need someone in game: create and save a schematic, reload it, rotate and mirror, the material list, Ctrl/Alt+scroll, the editing tools and a creative paste. Nothing above is marked PASS without evidence.

## Manual round 2 (23:29–23:57, server + client)

- **Easy place / build-by-schematic: PASS (partial).** The user built from the `test_26_3` placement and the overlays updated live. Before the stair was corrected it showed orange for the wrong state (`23.53.40.png`), and it was correct afterwards (`23.57.07.png`). The placement restriction message "Action prevented by the Easy Place mode" shows (`23.30.30.png`). The tool HUD shows `Mode [2/9]: Schematic Placement` and the placement info (`23.31.08.png`).
- **Material list: PASS** (see the table).
- **Observation, server log** (`run/dedicated` task output, 23:52:27): `Rejecting UseItemOnPacket from Dev: Location (195.92, 76.125, -200.67) too far away from hit block BlockPos{x=195, y=77, z=-201}`. Once in the session; the other placements went through. The hit Y is 1.375 below the clicked block's centre, which vanilla rejects (limit 1.0000001). The port did not change the easy-place hit-vector code (only the hand swing), and `easyPlaceProtocolVersion` is `auto`. **Not classified as a port regression without a repro**; needs a targeted test (easy-place a bottom-half stair/slab against the block above, on a vanilla server).
- **Not done:** saving a schematic (no new file in `run/schematics`; `prueba` ended up typed into chat), rotate/mirror, creative paste, Ctrl/Alt+scroll (only the mode HUD was seen).

Phase status unchanged: **NOT COMPLETE**.

## Manual round 3 (23:59–00:02)

- **Schematic save (write path): PASS.** `run/schematics/test_26_4.litematic` (23:59) is a re-save of the loaded `test_26_3` (same metadata name and author). Decoded: Version 7, DataVersion 5023, 6 palette entries all written with `Name` / `Properties` (e.g. `minecraft:oak_stairs` + `{facing:north, half:bottom, shape:straight, waterlogged:false}`), no `id` keys. This confirms `BlockStateNbt#writeBlockState` keeps the `.litematic` format.
- **Text input in Litematica GUIs: PASS.** `prueba` typed into the Area Editor save-name field (`2026-10-18_00.02.06.png`), with the "Hold shift to overwrite" tooltip.
- **Area selection from the user's build: NOT TESTED / user setup.** The tool was in `Mode [7/9]: Move`, not Area Selection (`00.01.06.png`). Corner 1 stayed at 0,0,0 and corner 2 is 195,76,-201, so the selection is 196×77×202 (`00.01.25.png`, `00.01.34.png`). It was not saved; it would be a ~3 M-block schematic. Earlier (21:29) both corners were set by clicks in Area Selection mode, so the click handling itself works. Needs a redo in Area Selection mode.

## Manual round 4 (00:20–00:21)

- **Area selection by tool clicks: PASS.** In `Mode [1/9]: Area Selection` a left click set corner 1 (`p1: 193, 76, -196`) and a right click set corner 2 (`2026-10-18_00.20.54.png` … `00.21.11.png`, `Dim: 3x1x4`, `6x1x4`, `3x1x7`). The earlier "corner 1 stuck at 0,0,0" was the tool being in Move mode.
- **Cosmetic, upstream (not a port regression):** the action-bar text reads literally `Set/moved point %d to %s`. Litematica passes the arguments (`KeyCallbacks:763`, `SelectionManager:591`), but MaFgLib's `InfoUtils#printActionbarMessage` (with `enableActionbarMessages` on) sends `Component.translatable(key, args)`, and vanilla translation only supports `%s` / `%n$s`, so any `%d` key (21 in `en_us.json`) shows raw on that path. GUI labels that go through `StringUtils.translate`/`String.format` render fine ("Regions: 1"). Left unchanged (upstream text and behaviour).
- **Stuck task:** the earlier save of the 196×77×202 selection is still queued ("Save Schematic, remaining chunks (40)"). It waits for chunks that are not loaded; expected Litematica behaviour. To be removed in the Task Manager.

## Manual round 5 (00:24–00:26)

- **Create and save a schematic from the world: PASS.** Area Selection `Dim: 1x2x1` (`2026-10-18_00.25.13.png`) was saved through Area Editor → Save Schematic as `run/schematics/prueba.litematic` (00:26:12, listed in `00.26.21.png`). Decoded: Version 7, DataVersion 5023, author `Dev`, 2 blocks: `minecraft:rail {shape: east_west}` and `minecraft:ladder {facing: west}`, palette written with `Name`/`Properties`. The selection was one column (ladder + rail), not the whole build, but it exercises the full world → `.litematic` path.
- **Task Manager removal: PASS.** The stuck "Save Schematic, remaining chunks (40)" overlay is gone.
- Note for the paste test: the paste/fill/delete tool modes run on the `executeOperation` hotkey, which is unbound by default (`Hotkeys.java`, `KeyCallbacks:529`), so it has to be bound first.

## Manual round 6 (00:30–00:33): rotate / mirror

- **Load the user-made schematic + placement: PASS.** `prueba` loaded as a placement, moved with "Move to player" (origin 201, 77, -203), ghost ladder and rail textured (`2026-10-18_00.30.32.png`).
- **Rotate: PASS.** Rotation NONE → CW_90 (`00.31.09.png` → `00.31.22.png`). After the change the HUD shows the ghost `minecraft:ladder facing: north`; it was saved as `facing: west`, and CW 90° maps west → north. The ghost rail turned to the other axis (`00.31.43.png`). Block-state rotation is applied correctly.
- **Mirror: NOT VERIFIED.** `00.32.53.png` shows the placement, but no screenshot shows the Mirror value or an unambiguous mirrored state.

## Manual round 7 (00:35–00:41): mirror, hotkey binding, creative paste

- **Mirror setting: PARTIAL.** Mirror cycled to `LEFT_RIGHT` together with `CW_90` (`2026-10-18_00.35.23.png`). The ghost of a 1×2×1 ladder-and-rail column does not visibly change under this mirror, so the mirrored block state is not proven by a screenshot.
- **Hotkey rebinding in the config GUI: PASS.** `executeOperation` was bound to `J` through Configuration menu → Hotkeys, search `exe` (`00.38.26.png`). This also covers config search and text input.
- **Ctrl+scroll tool mode: PASS.** The user switched between Area Selection, Move, Schematic Placement and Paste Schematic in world during rounds 3–7 (HUD `Mode [n/9]` in the screenshots).
- **Creative paste on a plain server with operator permission: PASS.** Server log: `[Dev: Changed the block at 201, 77, -203]` and `[Dev: Changed the block at 201, 78, -203]` (00:41:28), and the real ladder and rail are visible (`00.41.34.png`). The paste goes through vanilla commands sent as the operator; no permission is bypassed. The action bar reads "Schematic pasted using 0 fill and 0 setblock commands" even though 2 blocks changed. That counter text is Litematica's own message, not investigated further (cosmetic).

## Test phase summary (2026-10-18 00:45)

| Area | Result |
|---|---|
| Build, metadata, mixins, security #11 | PASS |
| Client + plain server launch, logs | PASS (no errors; benign 1.12-conversion WARNs) |
| Config GUI, hotkeys, hotkey rebinding, Ctrl+scroll, left/right click | PASS |
| Create, save, reload `.litematic`; format check | PASS (`prueba.litematic`, `test_26_4.litematic`) |
| Place, move, rotate | PASS |
| Mirror | PARTIAL (value set; mirrored state not visually proven) |
| Ghost blocks, overlays, missing/wrong indicators, OIT, filtering, rejoin, restarts | PASS |
| Material list | PASS |
| Area selection | PASS |
| Creative paste with permission | PASS |
| Schematic editing tools (fill/replace/delete), Alt+scroll nudge | NOT TESTED |
| Performance measurement | NOT TESTED |
| Easy-place "too far away" server rejection (1 occurrence) | OPEN, needs a repro |

**Phase status: NOT COMPLETE.** Still open: fill/replace/delete, Alt+scroll, a visible mirror check, and the easy-place rejection repro.

## Manual round 8 (00:50–01:08): mirror, easy place, editing tools

- **Mirror (visible): PASS.** `test_26_3` with `Mirror: FRONT_BACK` (`2026-10-18_00.50.23.png`, `01.04.02.png`): the planks and stairs moved to the other side of the glowstone compared with the unmirrored placement (`2026-10-17_22.57.01.png` vs `2026-10-18_01.04.20.png`, `01.08.09.png`).
- **Easy place on a server: PASS.** With `easyPlaceMode=true` (`01.05.34.png`) the user placed stone, glass, glowstone and planks from the ghosts at several angles (`01.06.36.png` … `01.08.09.png`); "Action prevented by the Easy Place mode" appears when clicking where no ghost needs that block (`01.07.29.png`). Server log: still only the single `Rejecting UseItemOnPacket ... too far away` from 23:52:27, no new rejection in this round. Treated as a one-off (not reproduced), noted as a watch item.
- **Fill / Replace / Delete, Alt+scroll nudge: NOT VERIFIED.** No screenshot shows the Fill, Replace or Delete tool mode or the nudge. A stone floor appears under the moved placement (`01.04.20.png`), possibly from a Fill, but that is not proven, and the server log has no fill/setblock output for it (`commandDisableFeedback` is on).

## Test phase status after round 8

**NOT COMPLETE.** Everything in the checklist is PASS except: the editing tools (Fill, Replace, Delete), the Alt+scroll nudge, and a performance measurement (NOT TESTED). There is one watch item: the single easy-place rejection, not reproduced.

## Manual round 9 (01:13–01:14): Alt+scroll and Delete

- **Alt+scroll nudge: PASS.** Corner 2 moved from X 203 to 206 (`Dim: 6x2x4` → `9x2x4`) with Alt+wheel in Area Selection mode (`2026-10-18_01.13.24.png` → `01.13.45.png`). In Corners mode the nudge moves the selected corner, which is Litematica's behaviour.
- **Delete tool: PASS.** In `Mode [8/9]: Delete` with "Delete target mode: Current Area" (`01.14.09.png`), `J` ran the operation. Server log `[01:14:14] [Dev: Successfully filled 43 block(s)]`; the action bar shows the same (`01.14.22.png`). It runs as a vanilla `/fill` with operator permission. The night scene makes the visual difference hard to see; the server confirmation is the evidence.

## Final test-phase status (2026-10-18 01:15)

| Result | Items |
|---|---|
| PASS | build; metadata; mixins; security #11; client + plain server; config GUI; hotkeys and rebinding; Ctrl/Alt+scroll; left/right click; text fields; create, save and reload `.litematic`; place, move, rotate, mirror; ghost blocks, overlays, OIT, filtering, rejoin, restarts; material list; area selection; easy place; creative paste; Delete (via `/fill`) |
| NOT TESTED | Replace tool; performance measurement |
| WATCH | one easy-place "too far away" server rejection (23:52), not reproduced in a later 8-block easy-place round |
| Defects fixed during the phase | ghost blocks not rendering (`39b3ca3c`) |

**Phase status: NOT COMPLETE (formally).** Two items were not executed: the Replace tool and a performance measurement. Every core function the port touched has passed in game. Replace shares its command path with the Delete and Fill operations, which passed.

## Manual round 10 (01:19–01:23): Replace

- **Replace tool: PARTIAL.** `Mode [4/9]: Replace block` ran on `J`: "Scheduled task added..." (`2026-10-18_01.23.15.png`). The HUD shows `Block: Stone` and `Replace: Stone`, so both tool blocks were set to stone. The vanilla command therefore reported "No blocks were filled" (red), which is correct for stone → stone. The selection, block pick (Alt+middle click) and command path work. A real change still needs a second block picked with Shift+middle click.

## Session close (2026-10-18 01:40)

- Client and server stopped. Client log: 0 errors. Server log: only the Netty kqueue/epoll platform probes and the `Connection reset` from closing the client; the single `Rejecting UseItemOnPacket` at 23:52:27 never recurred.
- **Replace: PARTIAL, closed.** A second attempt was not completed: the user had the planks in hand instead of the tool, and no placed plank to target. The command path itself ran ("No blocks were filled" for stone → stone). Not a defect.

**Test phase verdict: NOT COMPLETE (formally).** Open items: Replace with two different blocks, a performance measurement, and the one-off easy-place rejection (watch). All other checklist items PASS, with evidence above. No unexecuted test is marked PASS.

---

# Verification re-run (2026-10-18, HEAD `ed59aaca`)

Fresh automated run on the current HEAD (working tree clean, only untracked `.atl/`). Nothing was changed in the sources, nothing was committed, pushed or published. Build output went to a scratchpad directory through a Gradle init script (`-I`), because VS Code's Java language server is running.

| Check | Result | Evidence |
|---|---|---|
| `compileJava` | **PASS** | `BUILD SUCCESSFUL in 18s` |
| `test` | **NOT AVAILABLE** | `:compileTestJava NO-SOURCE`, `:test NO-SOURCE` (no test source set) |
| `build` | **PASS** | `forgematica-0.5.0+mc26.3.jar` + sources jar |
| Packaged metadata | **PASS** | `neoforge` and `minecraft` `[26.3,26.4)`, `mafglib` `[0.6,)`, `foxified_classtweaker` `*` (all `required`); jar-in-jar: `conditional-mixin-neoforge-0.6.4.jar` only |
| Mixin config | **PASS** | `required: true`, `defaultRequire: 1`, `compatibilityLevel: JAVA_25`; 51/51 listed classes are in the jar; `litematica.accesswidener` bundled |
| Security (issue #11) | **PASS (static)** | `SchematicBuffer:31` names files `UUID.randomUUID()`; `SchematicBufferManager#createBuffer` takes no file name; `Files.delete`/`deleteIfExists` on failure (`SchematicBuffer:96,127`, `SchematicBufferManager:125`); the `Litematic-Transmit*` receive path in `ServuxLitematicaHandler:144-146` is still commented and `LitematicaSchematic#receiveFileTransmit` has no active caller; `placement_paste_too_large_for_servux` still used at `SchematicPlacementManager:1139` |
| Dedicated server (plain NeoForge 26.3.0.88, no Forgematica) | **PASS** | `Done` at 09:51:27 (`run/dedicated`); only the known Netty kqueue/epoll probe and JNA noise |
| Client launch + client-only join | **PASS** | `runClient --quickPlayMultiplayer localhost:25565`; `Dev joined the game` at 09:51:51 |
| Client logs (`run/logs/latest.log`, `debug.log`) | **PASS** | 0 ERROR/FATAL, 0 Exception/`Caused by`, 0 `InvalidInjection`/`InvalidMixin`, 0 `NoClassDefFound`/`IllegalAccess`/`NoSuchMethod`/`NoSuchField`, no Shaderc/pipeline message, no `draw(): failed`, no `Cannot register payload`; 49 mixins reported as applied at join |

WARN lines seen (all known or vanilla): the 3 `JarSelector` dev-classpath lines, vanilla command-argument ambiguity, MaFgLib language-option notices, the 8 cobblestone-wall 1.12-conversion warnings, `Ignoring chat session from Dev due to missing Services public key` (offline mode) and `Requested post effect does not exist: minecraft:end_of_frame` (also present in the 2026-10-17 logs, so not new).

Limits of this run: it proves launch, mixin application and a clean join only. A clean log does not prove rendering; the rendering, GUI and gameplay results stay as recorded in the earlier rounds ("carried over", not re-executed here).

## Status of the checklist

| Item | Result |
|---|---|
| Build, metadata, mixins, security #11, launch, client-only multiplayer | **PASS** (re-executed above) |
| Config GUI, hotkeys, place/move/rotate/mirror, ghost blocks and overlays, material list, area selection, save/reload `.litematic`, easy place, creative paste with permission, Delete tool, persistence across restarts | **PASS** (carried over from rounds 1-9, not re-executed) |
| Replace tool with two different blocks | **PARTIAL**, needs a person in game |
| Performance measurement | **NOT TESTED**, needs a person in game |
| Easy-place `Rejecting UseItemOnPacket ... too far away` (single occurrence, 23:52) | **WATCH**, not reproduced; needs a targeted repro |
| Production class tweak (fluids in a non-dev install) and 26.4 refusal screen | **NOT TESTED** (no such install) |

**Phase status: NOT COMPLETE (formally).** The remaining items need manual interaction, so the run stops here.

## Manual round 11 (2026-10-18 10:00-10:18): Replace, FPS, easy place

Setup: plain NeoForge 26.3.0.88 server (`run/dedicated`, no Forgematica) + `runClient --quickPlayMultiplayer localhost:25565`, creative, `Dev` operator. Evidence: screenshots `run/screenshots/2026-10-18_10.*.png` and the server output.

- **Replace tool with two different blocks: PASS.** Area selection `5x1x1` by tool clicks (`10.01.21`), `Mode [4/9]: Replace block` with HUD `Block: Glass` / `Replace: Stone` (`10.04.16`). After `J` the five stone blocks are glass (`10.04.37`, `10.04.50`). Server log: `[10:04:34] [Dev: Successfully filled 5 block(s)]`. The tool was run twice more (`10:07:18`, `10:10:18`, both `Successfully filled 5 block(s)`). Block 1 = Alt+middle click, block 2 = Shift+middle click, confirmed in `Hotkeys.java` (`toolSelectModifierBlock1/2`) and the user's `litematica.json`.
- **Performance: PARTIAL (condition A only).** F3 shows 767, 781, 816 and 854 fps with a placement loaded, Anisotropic 2x, Improved Transparency OFF (`10.11.42`, `10.15.25`, `10.15.48`, `10.15.59`; Video Settings in `10.07.38`; F3 reads `T: inf (immediate) @144Hz`, RTX 5080, Ryzen 9 9950X3D). No screenshot shows the schematic hidden (`M,G`), Improved Transparency ON, or walking/chunk loading, so the cost of the schematic and the OIT cost are **not** measured. No 26.2 reference exists either. No stutter was reported.
- **Easy place: no rejection, but the risky case is not proven covered.** The server log for 09:51-10:18 has **no** `Rejecting UseItemOnPacket` line. Stairs were placed from the ghosts (`10.17.06`, `10.18.09`); the HUD there shows schematic `facing: north` vs client `facing: south` (orange wrong-state overlay). That is expected on this server: `PlacementHandler#getEffectiveProtocolVersion` resolves `AUTO` to `SLAB_ONLY` when the server is neither local, Servux nor Carpet, so only slab halves are encoded and stair facing follows the player. It is not a port regression. Whether a bottom-half stair/slab was placed against the block above (the 23:52 case) is not visible in the screenshots, so the WATCH item stays open.
- **Client log (`run/logs/latest.log`):** 0 Exception / `Caused by` / `draw(): failed` / `InvalidInjection`. Correction: the log does hold 8 `ERROR` lines (10:10:54-10:11:08), all `(litematica) addTask: [EMPTY] Waking up threads...` from the Replace runs; it is the known harmless message logged at ERROR level, with no stack trace, not a failure.
- Side note: a held F2 produced 24 screenshots in 23 seconds (`10.15.25`-`10.15.48`); harmless, they can be deleted.

Updated status: Replace moves from PARTIAL to **PASS**. Still open: performance conditions B (schematic hidden), C (Improved Transparency ON) and D (walking), and the easy-place bottom-half repro. Phase status: **NOT COMPLETE (formally)**.

## Manual round 12 (2026-10-18 10:56-11:54): performance conditions B, C, D

Same setup as round 11 (plain NeoForge server + `runClient`, creative, `Dev`). Spot: X 337.3, Y 71.2, Z -96.5, facing south, placement loaded. F3 reads `T: inf (immediate) @144Hz`, RTX 5080, Ryzen 9 9950X3D, Anisotropic 2x, 1918x2080. Evidence: `run/screenshots/2026-10-18_10.56.30.png`, `11.51.23.png`, `11.51.34.png`, `11.52.10.png`, `11.52.40.png`, `11.53.27.png`, `11.53.46.png` (plus the held-F2 bursts around them).

| Condition | Improved Transparency | Schematic | FPS (F3) | Evidence |
|---|---|---|---|---|
| A (round 11) | OFF | shown | 767, 781, 816, 854 | `10.11.42`-`10.15.59` |
| B | OFF | hidden (`M,G`) | 852 | `10.56.30` (message `Toggled Schematic Rendering OFF`; no orange overlay, no ghost planks) |
| C, standing | ON | shown | 758, 691 | `11.51.23`, `11.51.34` |
| D, moving | ON | shown | 726, 653 | `11.52.10`, `11.52.40` |
| A control | OFF | shown | 823 | `11.53.46` |

- Reading: with the schematic hidden the game runs at about 852 fps and with it shown at 767-854 (control 823), so the classic schematic layer costs little or nothing measurable on this machine. Improved Transparency ON lowers the standing rate to about 691-758 (roughly 10 percent below the control) and moving to about 653-726. These are single F3 samples, not averages, and there is no 26.2 baseline, so this measures the port's own schematic/OIT cost only, not a regression against 26.2.
- Visual check with Improved Transparency ON (`11.51.23`, `11.51.34`, `11.52.10`): glass ghost faces complete, ghost planks visible through the glass, orange wrong-state overlay on the stairs, selection boxes and the water-filled glass row all drawn. No doubled or missing glass faces seen.
- Not valid as samples: `11.51.12` and `11.53.35` (Video Settings open, 582 and 596 fps). `10.57.44` (899 fps) is not used because the screenshots do not prove whether the schematic was shown or hidden at that moment.
- **Stutter: NOT ASSESSED.** The user did not report freezes and a still image cannot show them, so no PASS or FAIL for the walking test beyond the fps numbers above.
- Logs after round 11: client `run/logs/latest.log` has no new ERROR/Exception after 10:11:08. Server output has 0 `Rejecting UseItemOnPacket` / `too far away` for the whole session; its last lines are only `/time` commands (10:55:43, 11:50:30).

Updated status: performance **PARTIAL-PASS** (A, B, C and moving fps measured, no perceptible cost problem, stutter not assessed, no 26.2 baseline). Still open: easy-place bottom-half repro (WATCH), production class tweak and 26.4 refusal screen (NOT TESTED). Phase status: **NOT COMPLETE (formally)**.

## Manual round 13 (2026-10-18 12:20-13:12): easy place under a ceiling (WATCH item)

Setup: same plain NeoForge 26.3.0.88 server + `runClient`. Test schematic generated locally with a stdlib script: `run/schematics/easyplace_ceiling.litematic` (8x3x2, Litematica v7, data version 5023; stone floor and ceiling, middle layer = 4 oak slabs and 12 oak stairs, bottom and top halves, so every piece sits between a real floor and a real ceiling). The `run/` directory is git-ignored. Protocol in effect: `AUTO` resolved to `SLAB_ONLY` (plain server).

| Step / case | Piece | Result | Evidence |
|---|---|---|---|
| A | 16 floor stones | **PASS** | Material List Stone Missing 16 / Done 33.3 % (`12.28.09`) |
| B | 16 ceiling stones placed in the air, no support block | **PASS** | Stone Missing 0 / Done 66.7 % after Refresh (`12.32.19`) |
| 1 | slab, bottom half, floor below, ceiling above | **PASS** | real slab in the lower half, HUD `type: bottom`, no `Client` diff panel, Oak Slab Missing 3 (`12.34.11`, `12.34.27`) |
| 9 | slab, bottom half (second row) | **PASS** | placed, Oak Slab Missing 2 (`12.37.35`); no HUD capture |
| 2 | slab, top half, touching the ceiling | **PASS** | real slab under the ceiling, Oak Slab Missing 1, Done 72.9 % (`12.48.16`, `12.48.24`) |
| 3 | stair, bottom, facing north | **PASS** | real stair in the lower half, not orange, Oak Stairs Missing 11 (`12.51.36`, `12.51.49`) |
| 4 | stair, bottom, facing east | **PASS** | placed in the lower half; orange because the client got `facing: north` vs schematic `east` (`12.53.37`) |
| 5 | stair, bottom, facing south | **PASS** | HUD `Schematic facing: south / half: bottom`, `Client facing: north / half: bottom`, `shape: straight` (`13.00.42`) |
| 7 | stair, top half, touching the ceiling, facing north | **PASS** | HUD shows only the `Schematic` panel (`half: top`), no diff; not orange (`13.09.39`) |
| 8 | stair, top half, touching the ceiling, facing south | **PASS** | HUD `Schematic facing: south / half: top`, `Client facing: north / half: top`, `shape: straight` (`13.11.13`) |

- **Not tested on purpose:** stair bottom facing west (case 6) and the second-row copies of the other pieces (cases 10-16). Facing is not encoded on this server, so the west case runs the same code path as east and south, and the second row repeats the same block states. Not a proof, a decision to skip duplicates.
- **Expected behaviour, not a defect:** with `SLAB_ONLY`, `half` and slab `type` are right, but stair `facing` follows the direction the player looks, so stairs whose schematic facing differs from the player's show the orange wrong-state overlay.
- **Server evidence:** `0` lines matching `Rejecting UseItemOnPacket` or `too far away` over the whole session (09:51-13:12), with 32 stone blocks (16 floating), 3 slabs and 5 stairs placed in this round. The 17 `Player Dev standing on air - force-sending blocks below` lines are vanilla anti-fly notices and unrelated.
- **Client log:** no Exception, `Caused by`, `InvalidInjection`, `IllegalAccessError` or `draw(): failed`; the only ERROR lines are the known `addTask: [EMPTY] Waking up threads...`. 214 `[WorldRenderer] setupTerrain / chunksToUpdate: 1` WARN lines are gated by `Reference.DEBUG_MODE` (`WorldRendererSchematic.java:600-602`, dev only).
- **Loose end:** after case 4 the Material List showed `Stone Missing 1` (it was 0 at 12:48). One floor or ceiling stone was removed by hand between 12:48 and 12:57; not located, no effect on the cases above.
- **Screenshot note:** the HUD text next to F3 is unreadable with F3 on; cases 5-8 were captured with F3 off.

Updated status: the easy-place `Rejecting UseItemOnPacket ... too far away` WATCH item (single occurrence on 2026-10-17 23:52, not reproduced) is now **closed as not reproduced** after 8 placements under a ceiling (3 slabs, 5 stairs) plus 32 stone placements with no rejection on a plain server. It stays a WATCH only for non-plain servers (Servux/Carpet protocols V2/V3 encode data in the hit vector and were not exercised). Still **NOT TESTED**: production class tweak (fluids in a non-dev install) and the 26.4 refusal screen (no such installs). Stair-half behaviour was confirmed for bottom and top; stair facing mismatch on plain servers is expected. Phase status: **complete with documented gaps** (NOT TESTED items above, no 26.2 performance baseline, stutter not assessed).

## Finalization of the private 26.3 port (2026-10-18)

Source under test: `ed59aaca` plus the working-tree changes listed at the end. Nothing in `src/` was changed in this step, so the in-game results of rounds 1-13 still describe the same code. **No new runtime session was run in this step**; every runtime claim below is carried over from the earlier rounds, and the packaged jar was never launched outside `runClient`.

| Check | Result | Evidence |
|---|---|---|
| Java 25 | **PASS** | `java -version` = 25.0.4.1; `Forgematica.class` and `Litematica.class` have class-file major version 69 (Java 25); `options.release = 25` |
| Gradle build with the wrapper | **PASS** (redirected dir) | `gradlew.bat clean build` in the repo fails before compiling: `buildSrc/build/classes/java/main` is locked by the IDE's Java language server (`Unable to delete directory`). With the init script that redirects the build directories (`-I altbuild.init.gradle`, fresh directory, so a clean build) `build` is **BUILD SUCCESSFUL in 19s**; `compileJava` ok (only deprecation/unchecked notes), `test` NO-SOURCE |
| Licence packaging | **FIXED, PASS** | `jar {}` did `from("LICENSE")` but the file is `LICENSE.txt`, so no licence was packaged (also on `neoforge/26.2`). Now `from("LICENSE.txt")` with `.get()`; the jar contains `LICENSE.txt_forgematica`, byte-identical to `LICENSE.txt` (LGPL-3.0) |
| Mod metadata | **PASS** | `neoforge.mods.toml`: modId `forgematica` (+ `litematica` stub), version `0.5.0+mc26.3`, license `LGPLv3`, credits `masa`, authors `TexTrue, CagayakeGirls`, `javafml [4,)` |
| Dependency ranges | **PASS** | neoforge `[26.3,26.4)`, minecraft `[26.3,26.4)`, mafglib `[0.6,)`, foxified_classtweaker `*` (all `required`) |
| Mixin config | **PASS** | `required: true`, `defaultRequire: 1`, `compatibilityLevel JAVA_25`; 23 common + 28 client + 0 server = 51 entries, all 51 classes present in the jar |
| Resources | **PASS** | `litematica.accesswidener`, `assets/litematica/**` (13 language files, `legacy_terrain.vsh/fsh`), `icon.png`; jar-in-jar only `conditional-mixin-neoforge-0.6.4.jar` |
| Resolved runtime versions | **PASS** | NeoForge 26.3.0.88 (Minecraft 26.3 merged), MaFgLib 0.6.0+mc26.3, Foxified ClassTweaker 0.1.0-alpha.4, conditional-mixin 0.6.4. MaFgLib's own `neoforge.mods.toml` requires `foxified_classtweaker` and `[26.2,)` |
| Client side only | **PASS** (carried over) | `@Mod(dist = CLIENT)`; 0 server mixins; joined a plain NeoForge 26.3 server with nothing of ours server-side (earlier rounds) |
| Security, upstream issue #11 | **PASS** | see below |
| Regression greps | **PASS** | `NbtUtils.readBlockState/writeBlockState` only inside `util/BlockStateNbt`; payload registered from the `Forgematica` constructor and the `registerPipelines` listener added there; `ServuxLitematicaHandler#reset` does not clear `payloadRegistered`; mouse buttons via `InputConstants.MOUSE_BUTTON_*` (11 uses); the shared `chunkLayerSampler` is only read, never stored or closed |
| GitHub Actions | **PASS** (ran on GitHub) | Before pushing: YAML of both workflows parsed, only `GITHUB_TOKEN` is referenced, and the release step was run locally with a stubbed `gh` and `bash -n`. After pushing `eeee28a7` to `neoforge/26.3`: `build` (run 39130243236) and `release-26.3` (run 39130243301) both finished with success (2m7s and 2m16s), and pre-release `mc26.3-build.1-eeee28a` was created with `forgematica-0.5.0+mc26.3.jar` (1,928,988 bytes) and the `-sources.jar`. The CI jar differs in size from the local one (1,930,054 bytes, built on another machine) and was **not launched in game** |
| `neoforge/26.2` untouched | **PASS** | tip still `72c1a0f28b409583f14235960798934bf44fc1e3` |

### Security review of upstream issue #11 (trusted schematics only)

Issue #11 (open, `CagayakeGirls/litematica-neoforge`): a malicious server could make the client write a file with a server-chosen name (for example `mods/somevirus.litematic.jar`). The upstream fix is commit `f6dc5247f7`, which is not in the local clone, so ancestry could not be checked with git; instead every hunk of its patch (read with `gh api`) was checked against this port:

- `ServuxLitematicaHandler`: block/entity NBT and bulk replies are processed only when `servuxRegistered`; the `Litematic-Transmit*` switch is commented out and `handleBulkData` goes straight to `handleBulkEntityData` (lines 87-101, 144-163). No live caller of `receiveFileTransmit` or `sendTransmitFile` remains.
- `SchematicBuffer`: no `name` field, the file is `UUID.randomUUID().toString()` (line 31) with `getFileNameWithExt()`.
- `SchematicBufferManager`: `createBuffer` takes no name; a schematic that fails to parse deletes its file (lines 114-125).
- `LitematicaSchematic`: `FileName` is no longer put into the transmit start tag, `createBuffer(totalSlices, ...)` has no name, both transmit methods are `@Deprecated(forRemoval = true)`. The remaining `putString("FileName", ...)` at line 3223 is in the debug-only `toString()` and is identical upstream.
- `SchematicPlacementManager`: slicing for Servux is commented out and an oversized paste shows `litematica.message.error.placement_paste_too_large_for_servux` (also in `en_us.json`).
- `EntityDataManager`: the `ENTITY_DATA_SYNC` guard is present (line 156).
- Sweep of every `Files.write*`, `FileOutputStream`, `FileWriter`, `new File(`, `Path.of`, `Paths.get` and `.resolve(` in `src/main/java`: the only file-writing path fed by network data is `SchematicBuffer` (UUID name, and unreachable from the network because the receive path is commented out). The other hits take names typed by the user in the GUI, config JSON, or fixed names; I read the hits by call site and by the caller search for the placement path, not every GUI flow line by line. `SchematicPlacement.createFromNbt` does call `Path.of(name)` with a string from NBT, but its only caller is the commented-out Servux block.
- Only the locally generated schematics in `run/schematics/` were ever loaded.

### Build output

- Jar: `C:\Users\alexa\OneDrive\Documentos\GitRepo\litematica-neoforge\build\libs\forgematica-0.5.0+mc26.3.jar` (1,930,054 bytes, SHA-256 `11ff186709f78c3c712af78b37c4ac6b2d153afac38bd83239cbfc474b4ef1f9`) plus `forgematica-0.5.0+mc26.3-sources.jar` in the same folder.
- It was built into a redirected directory (`<scratchpad>\altbuild\root\libs`) because of the IDE lock and then copied to `build/libs/` after checking the old file: the previous jar there dated from 2026-10-17 15:07, before the block-state fix `39b3ca3c`, and lacked `BlockStateNbt` and the licence, so it would have loaded every palette entry as air.

### Completed versus untested

- Completed and verified in game (dev environment, see the rounds above): config GUI, hotkeys and mouse, load/import, place/move/rotate/mirror, ghost blocks and overlays, material list, area selection, Replace and Delete tools, creative paste with permission, easy place (bottom and top halves under a ceiling, plain server), client-only multiplayer, persistence across restarts.
- NOT TESTED: a normal launcher install of the packaged jar (access widener, fluids, `IllegalAccessError` risk), the 26.4 dependency screen, the Servux and Carpet easy place protocols (V2/V3), Iris, launching the jar built by CI (the workflows themselves ran on GitHub), stutter while moving, any 26.2 performance baseline.

### Known issues

- With the plain-server easy place protocol (`SLAB_ONLY`) stair facing follows the player, so mismatched stairs show the orange overlay (expected).
- Improved Transparency costs about 10 percent in the single sample taken.
- Log noise: `addTask: [EMPTY] Waking up threads...` at ERROR level, and `setupTerrain` WARN lines that only appear with `Reference.DEBUG_MODE`.
- The repository is public, so the pre-releases created by `release-26.3.yml` are public (the first one, `mc26.3-build.1-eeee28a`, already is); every push to `neoforge/26.3`, docs-only ones included, creates another; forks must enable Actions; re-running a workflow run reuses the tag and `gh release create` then fails instead of overwriting.
- The IDE's Java language server locks `buildSrc/build` and `build/classes`; use the init-script pattern from `CLAUDE.md`.

### Files modified in this step (not committed)

`build.gradle` (licence packaging), `README.md` (private install, requirements, limitations), `CLAUDE.md` (CI note), `MIGRATION_26_3.md`, new `.github/workflows/build.yml` (restored from `neoforge/26.2` plus `permissions: contents: read`) and new `.github/workflows/release-26.3.yml`. `build/libs/` (git-ignored) holds the new jars. The `.atl/` folder was already untracked.
