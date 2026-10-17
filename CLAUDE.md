# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Forgematica: an unofficial **client-side NeoForge port of Litematica** (masa / sakura-ryoko), maintained by CagayakeGirls. It depends on **MaFgLib**, a NeoForge port of malilib (`fi.dy.masa.malilib.*`), which is not part of this repo. One branch per Minecraft version (`neoforge/26.2`, `neoforge/26.3`, ...); `upstream` is `CagayakeGirls/litematica-neoforge`.

## Commands

Windows: use `gradlew.bat`; elsewhere `./gradlew`. Requires **JDK 25** (`options.release = 25`, Mixin `JAVA_25`).

- Build: `./gradlew build` → jar in `build/libs/` (`forgematica-<mod_version>+mc<mc>.jar`, plus sources jar).
- Run client: Loom-generated run configs (`./gradlew runClient`).
- There is **no test source set and no lint config**. Verification is compile + launching the client.
- If an IDE's Java language server locks `build/classes` (`Unable to delete directory`), build into another directory with a Gradle init script passed via `-I` that sets `layout.buildDirectory` on the root project (skip `buildSrc`), instead of editing build files or killing the IDE.
- Multiplayer testing: the mod and MaFgLib are client-only, and MaFgLib crashes on a dedicated server (`NoClassDefFoundError: ClientLevel`), so Loom's `runServer` cannot work. Use a plain NeoForge server (`online-mode=false`; 26.3 defaults to `white-list=true`) and join it from `runClient`.
- The `neoforge/26.3` branch is a stopgap until the official developers port 26.3, so `.github/` (CI and release workflows) was removed here. The `publisher {}` block in `build.gradle` and the `publishing {}` block still exist; do not run `publish`/`publishMods` without explicit authorization.

## Version bumps (Minecraft upgrade)

Versions live in several places that must change together:
- `gradle.properties`: `minecraft_version`, `minecraft_version_out`, `neoforge_version`, `malilib_version` (MaFgLib coordinates `team.cagayakegirls.mafglib:mafglib:<ver>+mc<mc>`), `mod_version`.
- `gradle/libs.versions.toml`: `range-game`, `range-neoforge`, `range-mafglib` (injected into `neoforge.mods.toml` via the `generateModMetadata` task).
- `build.gradle`: `setGameVersions("...")` in the `publisher` block (hardcoded).
- `gradle/libs.versions.toml` pins Architectury Loom `1.17-SNAPSHOT` (`dev.architectury.loom-no-remap`; Mojang names, no remapping). It works with MC 26.3 / NeoForge 26.3.0.88.
- Current 26.3 values: NeoForge `26.3.0.88`, MaFgLib `0.6.0+mc26.3`, conditional-mixin `0.6.4` (must match what MaFgLib requires). The 26.3 port builds, launches and passed singleplayer and multiplayer schematic sessions with every mixin applied; `MIGRATION_26_3.md` holds the audit and the remaining in-game checklist.
- `range-game` and `range-neoforge` are capped (`[26.3,26.4)`) on purpose: with `defaultRequire: 1`, a newer game would load the jar and crash on the first stale mixin instead of showing a dependency screen. Move both bounds when porting.

Maven repos: `buildSrc` (`CagayakeGirlsGradlePlugin`) adds NeoForge, KTT (kessokuteatime.work) snapshots/releases and Sinytra to every project; MaFgLib resolves from KTT releases.

## Architecture

Two roots in `src/main/java`: `team.cagayakegirls.forgematica` (the `@Mod`, `dist = CLIENT` entry that bridges NeoForge to Litematica) and `fi.dy.masa.litematica` (the ported Litematica code, kept in its original package so it tracks upstream masa/sakura-ryoko code and malilib APIs).

Startup flow: `Forgematica` → `Litematica.onInitialize` registers `InitHandler` with malilib's `InitializationHandler` → `InitHandler` registers configs (`config/Configs`, `Hotkeys`), input handlers (`event/InputHandler`, `KeyCallbacks`), render/world-load hooks (`event/RenderHandler`, `WorldLoadListener`) and `DataManager`.

Core subsystems (each spans several packages):
- **Schematic data**: `schematic/` (file formats, `LitematicaSchematic`), `selection/` (areas/boxes), `schematic/placement/`, `data/DataManager` + `SchematicHolder` (loaded schematics).
- **Schematic world**: `world/WorldSchematic` is a fake client `Level` holding the loaded blocks; `SchematicWorldHandler` owns it. Chunk/section classes in `world/` back it.
- **Schematic rendering** (the most version-fragile code): `render/schematic/` is a custom world renderer — `WorldRendererSchematic` drives `ChunkRendererSchematicVbo` (per-section mesh building), `ChunkRenderDispatcher*`/`ChunkRenderWorkerLitematica` (async rebuilds), `BufferBuilderCache`/`ChunkMeshCache`/`ChunkRenderGpuUploader`/`GpuBufferCache` (GPU buffers), `OverlayRenderType` and `render/LitematicaPipelines` (render pipelines/shaders), plus `BlockModelRendererSchematic`/`FluidModelRendererSchematic`/`ao/`. `render/OverlayRenderer`, `RenderUtils` and `render/infohud` draw overlays and HUD. `LitematicaRenderer` is the façade invoked from `MixinLevelRenderer`/`MixinGameRenderer`. Iris shader compat is in `compat/iris`.
- **Tools / placement**: `tool/`, `util/` (easy-place, paste, placing helpers), `scheduler/` (tick-based tasks for paste/verify), `materials/` (material list + HUD), `gui/` (screens and `gui/widgets`).
- **Networking**: `network/` (Servux/litematic packet + handler) with `mixin/network/*` hooks.

Mixins: `src/main/resources/mixins.litematica.json` (package `fi.dy.masa.litematica.mixin`) split into common vs `client` lists; a new mixin class must be listed there. `compat/mixin/LitematicaMixinConfigPlugin` extends `conditional-mixin` (bundled via `include`) so mixins can be disabled by restriction checks. `defaultRequire: 1` means a failed injection crashes at launch. `mixin/test/` (`MixinRenderPipelines`, `MixinBindGroupLayouts`) are debug mixins not registered in the JSON. Naming: `IMixin*` are accessors/invokers, `Mixin*` are injectors.

Access widening: `src/main/resources/litematica.accesswidener` (accessWidener v2 `official` names), applied through Foxified ClassTweaker declared in `neoforge.mods.toml` (`[[foxified.classtweaker]]`) and `loom.accessWidenerPath`. `neoforge.mods.toml` is a template in `src/main/templates/META-INF/` expanded by `generateModMetadata`; it declares a second `litematica` stub mod id for compatibility, and a `required` dependency on `foxified_classtweaker`. `runClient` runs Loom's pre-widened Minecraft jar, so a widener entry that Foxified fails to apply only shows up in a normal install (as `IllegalAccessError`), never in dev.

Assets: `src/main/resources/assets/litematica/` (lang files, plus `*_fallback` glass blockstates/models used by the schematic renderer).

## Input (26.3)

`KeyEvent.key()` is an SDL scancode and `KeyEvent.keycode()` an SDL keycode. MaFgLib's `fi.dy.masa.malilib.util.input.KeyCodes` constants are keycodes, so compare them with `keycode()`; use `InputConstants.KEY_*` (scancodes) with `key()`. Do not swap GLFW numbers for SDL numbers by hand. Mouse buttons are SDL numbers too (left 1, middle 2, right 3; GLFW was 0/2/1): MaFgLib passes `MouseButtonEvent#input()` to `IButtonActionListener#actionPerformedWithButton`, so compare with `InputConstants.MOUSE_BUTTON_LEFT/RIGHT/MIDDLE`, never with `0`/`1`.

## Rendering API (26.3)

GPU/pipeline types (`GpuBuffer`, `RenderPipeline`, `RenderPass`, `GpuDevice`, `VertexFormat`, `GpuSampler`, ...) live in `com.mojang.renderpearl.api.*`, not `com.mojang.blaze3d.*`. `RenderSystem`, `MeshData`, `BufferBuilder` and `PoseStack` stayed in `blaze3d`. MaFgLib 0.6.0 no longer ships the `LEGACY_*` terrain pipelines or `ChunkFixUniform`, so this repo defines them: `render/LitematicaPipelines` (registered via NeoForge's `RegisterRenderPipelinesEvent` from the `Forgematica` constructor), `render/uniform/ChunkFixUniform`, and the `legacy_terrain` shaders under `assets/litematica/shaders/`. Shaders use the 26.3 format (`#include`, explicit `location`s). To inspect Minecraft/MaFgLib signatures, `javap -cp <jar>` the merged 26.3 jar in the Gradle cache (`fabric-loom/minecraftMaven/.../neoforge-26.3.0.88-minecraft-merged-deobf-26.3.jar`); raise javac's 100-error cap with a scratchpad init script (`-Xmaxerrs`) rather than editing build files.

## 26.3 world rendering structure

Vanilla now draws the level through `LevelRenderer#executeSolid`, `executeClassicTransparency` and `executeOit`, each handed a `RenderPass`, and calls `ChunkSectionsToRender#renderGroup(group, pass, sampler, atlasView, wireframe)`. There is no separate translucent target any more (`LevelTargetBundle` has `main` plus OIT targets), and `RenderPass#setPipeline` takes a `CompiledRenderPipeline` (use `RenderSystem.getCompiledPipeline(pipeline)`). `MixinLevelRenderer` draws the opaque and (classic mode) translucent schematic groups *into the pass vanilla passes to* `executeSolid` / `executeClassicTransparency`; in OIT mode the translucent group is drawn after `executeOit` with a pass of our own on `gameRenderer.mainRenderTarget()` (`ChunkRenderBatchDraw`, optional `RenderPass` argument). The schematic layer never uses OIT itself. The sampler handed to these hooks is vanilla's `LevelRenderer#chunkLayerSampler`, which vanilla closes and recreates when texture filtering changes and closes on shutdown: use it for the current draw only, never store or close it (`WorldRendererSchematic#getGpuSampler` owns a separate fallback sampler). For ambient occlusion, vanilla's `BlockModelLighter` is the reference: corner translucency is `BlockState#isLightPermeable()`, and directional shade uses `BakedQuad.MaterialInfo#shadeDirectionOverride()` (see `AOProcessor#getDirectionalBrightness`).

## Patched third-party libraries

`gradle/patched-libs.gradle` (applied first thing in `build.gradle`) builds patched copies of `conditional-mixin-neoforge:0.6.4` and `foxified-classtweaker:0.1.0-alpha.4` in `build/patched-libs/`, only changing `logoFile` to `iconFile` in their `neoforge.mods.toml` to silence an FML deprecation warning. The originals are SHA-256 pinned, the coordinates are unchanged, and the patch only applies to exactly those versions. Bumping either library means updating the pinned digests, or deleting the script once upstream ships `iconFile`. It does not cover MaFgLib's own nested conditional-mixin copy or the foxified mod users install, so production logs can still show the warning.

## Networking payloads (MaFgLib 0.6.0)

MaFgLib registers payloads through NeoForge's `RegisterPayloadHandlersEvent`, which has already fired by the time the malilib init handler runs. Register any custom payload (the Servux `servux:litematics` channel) in the `Forgematica` mod constructor (`ServuxLitematicaHandler#registerPlayPayload`), never from `InitHandler` / `onGameInit`, otherwise it logs `Cannot register payload ... after NeoForge payload registration has closed`. Because that registration happens only once, `ServuxLitematicaHandler#reset` must not clear `payloadRegistered` (only the per-server state), or receivers can no longer be registered after a rejoin. `IPluginClientPlayHandler` extends NeoForge's `IPayloadHandler`; its default `handle` forwards to `receivePlayPayload(T, IPayloadContext)`, and MaFgLib registers channels as `optional()`, so servers without Servux still accept the client.

## Security (schematic transfer)

Upstream issue #11 (`CagayakeGirls/litematica-neoforge`, fix commit `f6dc5247f7`): a server could make the client write a file with a server-chosen name (path traversal, e.g. `*.litematic.jar` in `mods/`). Keep the fix: `schematic/transmit/SchematicBuffer` names received files with `UUID.randomUUID()`, `SchematicBufferManager#createBuffer` takes no name and deletes the file when parsing fails, and the Servux `Litematic-Transmit*` receive path in `ServuxLitematicaHandler` stays commented out. Never build a file name or path from network payload data, and re-check these files after every upstream merge.

## Block state NBT (26.3)

26.3 `NbtUtils#readBlockState`/`writeBlockState` use `id`/`properties`, and the vanilla DataFixer `BlockStateFieldNamesFix` renames `Name`/`Properties` to them. `.litematic` palettes and Litematica's conversion maps use `Name`/`Properties`. Always go through `util/BlockStateNbt` (reads both layouts, writes `Name`/`Properties`), never `NbtUtils` directly; with the vanilla methods every palette entry silently becomes air.

## Testing in game

A log with no errors does not prove rendering: the ghost blocks were invisible for a whole day while the logs were clean. Ask for a screenshot of the textured ghost blocks themselves. Manual test sessions run the dedicated server (`run/dedicated`, plain NeoForge, creative, `Dev` op) together with `runClient --args="--quickPlayMultiplayer localhost:25565"`; stop any previous server task first, or the world lock makes the new server fail to start. If the IDE locks `buildSrc/build`, the init script must redirect the `buildSrc` build directory as well as the root project's.

## Auditing Mixin targets after a Minecraft bump

The compiler does not check Mixin target strings, `@Shadow`/`@Accessor` names or handler signatures, and the config is `required` with `defaultRequire: 1`, so a stale target crashes at launch. Gradle caches the merged Minecraft jar of every version it has built (`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/neoforge-<ver>-minecraft-merged-deobf/<mc>/`). Run `javap -p` on a target class in the old and new jar and diff the lines for each member a mixin names (fields and methods); for `@At(INVOKE)` targets use `javap -c -p` and look inside the target method (note javap prints calls to a method of the *same* class without the owner prefix). The 26.3 audit result and injector map are in `MIGRATION_26_3.md`.

## Conventions

- Keep ported code in the `fi.dy.masa.litematica` package and avoid unrelated refactors, so merges from upstream stay tractable.
- Source style is Allman braces with tabs, as in the original Litematica.
- The repo mixes English README/CHANGELOG with Weblate-managed translations; commit messages follow conventional commits.
