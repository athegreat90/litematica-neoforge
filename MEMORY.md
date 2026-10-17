# MEMORY.md

Decisions taken during the Phase 1 assessment of the Minecraft 26.2 → 26.3 migration (2026-10-17).

- Branch `neoforge/26.3` created locally from `neoforge/26.2` (`72c1a0f2`). The tree had an untracked `.atl/` directory; the user explicitly approved creating the branch anyway. Nothing pushed or committed.
- Upstream (`CagayakeGirls/litematica-neoforge`) has no 26.3 branch; its latest is `neoforge/26.2` at the same SHA as this fork, so there is nothing to reuse.
- MaFgLib `0.6.0+mc26.3` exists on `https://maven.kessokuteatime.work/releases` (jar, pom, module all HTTP 200) and is already resolvable via `buildSrc`. Treated as usable.
- Verified targets: Minecraft `26.3`, NeoForge `26.3.0.88`, Java 25, Gradle 9.6.1 (current wrapper). Architectury Loom `1.17-SNAPSHOT` support for 26.3 is unverified until a build is run.
- No security advisories found for upstream or malilib (empty advisory lists; Dependabot disabled).
- Biggest risk: the 26.3 primer replaces Blaze3d with `com.mojang.renderpearl` and GLFW with SDL; rendering code (~36 files) and input code (~9 files) will need rework.
- Phase 1 changed no code, dependencies or build config. Phase 2 has not started.
- Created per user request: `CLAUDE.md`, `AGENT.md`, `MEMORY.md`, `MIGRATION_26_3.md` (all untracked).

## Phase 2: build configuration (2026-10-17)

- Bumped only build files/metadata: `gradle.properties` (MC 26.3, NeoForge `26.3.0.88`, MaFgLib `0.6.0+mc26.3`), `gradle/libs.versions.toml` (ranges `[26.3,)`, `[26.3,)`, `[0.6,)`), `build.gradle` (`setGameVersions("26.3")`). No Java, mixin, access widener, license, mod ID or author changes.
- NeoForge `26.3.0.88` chosen because it is the latest release in the official NeoForged Maven metadata (POM verified, FML 12.0.12, Mixin 0.17.4). Java stays 25.
- MaFgLib taken from the KessokuTeaTime Maven (standard artifact exists, jar checksum verified). Curse Maven (`curse.maven:mafglib-910766:9107274`) deliberately **not** added: it works but is a redundant second source.
- Left unchanged: Architectury Loom `1.17-SNAPSHOT` (set up MC 26.3 fine), Gradle 9.6.1, conditional-mixin `0.6.4` (matches MaFgLib's requirement; 0.6.5 exists but is unrelated), `range-fml` `[4,)`, `buildSrc`, `neoforge.mods.toml`.
- User decision: `.github/` removed (`git rm -r`, staged, not committed) because this branch is a bypass until the official developers port 26.3. This also removes the release workflow; `CLAUDE.md` updated accordingly.
- Result: dependency resolution OK for compile and runtime classpaths. `compileJava` fails with 100+ errors (javac cap): Blaze3d→renderpearl types, MaFgLib 0.6.0 package moves (`KeyCodes`, `FileRenamer`, `ChunkFixUniform` removed), MC class moves (`PotionBrewing`, `FuelValues`, `RedStoneWireBlock`), and missing `ClientPlayNetworking`. Recorded in `MIGRATION_26_3.md`; no source was fixed.
- Next (not started): Phase 3 = Java source porting.

## Phase 3, batch 1: non-render API moves (2026-10-17)

- Fixed 7 files (see `MIGRATION_26_3.md`). All non-rendering compile errors are gone; `compileJava` still FAILS, only on rendering (Blaze3d → renderpearl) plus `ChunkFixUniform`.
- Removed `WorldSchematic.potionBrewing()` / `fuelValues()` instead of stubbing them: `Level` no longer declares those methods in 26.3, and nothing called them.
- Tab handling: compare `KeyEvent.keycode()` with MaFgLib's `KeyCodes` (both SDL keycodes). `KeyEvent.key()` is a scancode, so merely fixing the import would have compiled but silently broken Tab. Rule for later input work: never mix `key()` (scancode) with MaFgLib `KeyCodes` (keycodes); do not swap numeric constants blindly.
- Networking: MaFgLib 0.6.0 handlers are plain NeoForge `IPayloadHandler`s. Fabric `ClientPlayNetworking` is gone; use `IPayloadContext`. The anonymous `receive(...)` override was dropped because `handle` already delegates.
- Rendering deliberately deferred to its own batch: most missing types are plain moves into `com.mojang.renderpearl.api.*` (buffers, pipeline, textures, device, commands, vertex), but `ChunkFixUniform` was removed from MaFgLib and needs a design decision.
- Editor diagnostics still show 26.2-based errors until the IDE reloads Gradle; Gradle is authoritative.
- Still no commit, push or publish. `.github/` removal is staged only.

## Phase 3, batch 2: renderpearl import relocations (2026-10-17)

- Remapped 29 imports in 10 files (import-only, via a scripted regex over an explicit 19-type table; script kept in the scratchpad, not in the repo). 26.3 moved GPU/pipeline types from `com.mojang.blaze3d.*` to `com.mojang.renderpearl.api.{buffers,pipeline,textures,device,commands,vertex}`; `RenderSystem`, `MeshData`, `BufferBuilder`, `PoseStack` etc. stayed in `blaze3d`.
- `compileJava` still FAILS. javac's 100-error cap hid non-render errors until now: `RedStoneWireBlock` static refs (missed in batch 1), `ServuxLitematicaHandler` missing `checkFailures()` / `tickFailures()`, `BlockPos` constructor, `FileCopier` / `FileDeleter` moves, `IrisCompat.hasSodium()`, `AbortableIterationConsumer.Continuation`, `SignText.DIRECT_CODEC`.
- Open design decision for the user: MaFgLib 0.6.0 dropped `LEGACY_*` terrain pipelines and `ChunkFixUniform`. Recommendation A (recreate locally and port `legacy_terrain` shaders) over B (vanilla terrain pipelines, changes behaviour). Not decided yet.
- Suggested next batch: the small non-render fixes (rows 5-10 in `MIGRATION_26_3.md`) before the heavy `WorldRendererSchematic` port.

## Phase 3, batch 3: legacy terrain pipelines (2026-10-17)

- User decision: **option A**. Recreated the LEGACY terrain pipelines and `ChunkFixUniform` locally instead of using vanilla 26.3 terrain pipelines, to keep rendering behaviour.
- Source of truth for the port: MaFgLib 0.5.5+mc26.2 bytecode (downloaded into the scratchpad, inspected with `javap` only, never executed). Pipeline values (depth func, offsets, blend, `ALPHA_CUTOUT` 0.5/0.1) and the UBO layout were compared against it, not guessed.
- Registration: NeoForge `RegisterRenderPipelinesEvent` on the mod bus (`Forgematica` constructor now takes `IEventBus`), instead of the old mixin into `RenderPipelines.<clinit>`.
- Bind groups: 26.2's `MATRICES_PROJECTION` became `DYNAMIC_TRANSFORMS` + `PROJECTION` in 26.3; pipelines also use `FOG`, `SAMPLER0_SAMPLER2` and the local `LEGACY_TERRAIN_GROUP` (`ChunkFix` UBO). The uniform name stays `ChunkFix` (not the `LegacyTerrainFix` name used in the dead stub).
- Shaders ported to the 26.3 format with logic unchanged. Not runtime validated.
- javac's 100-error cap was lifted via a throwaway init script in the scratchpad (`-I maxerrs.init.gradle`, adds `-Xmaxerrs 5000`); repo build files untouched. Real total is 144 errors in 27 files, none in the files touched this batch.
- Left in place (needs user OK to delete): obsolete `mixin/test/MixinRenderPipelines`, `MixinBindGroupLayouts` and the commented `LegacyTerrainFixUniform` stub.
- Next: mechanical non-render fixes first (`RedstoneWireBlock` refs, `BlockPos` ctor, `FileCopier`/`FileDeleter`, `Continuation`), then the `WorldRendererSchematic` draw-path batch.

## Phase 3, batch 4: mechanical renames (2026-10-17)

- 10 files; errors 144 → 105 (19 files). `RedstoneWireBlock`, `BlockPos` copy constructor, `FileCopier`/`FileDeleter` imports and `net.minecraft.util.Continuation` are done.
- `new BlockPos(Vec3i)` was removed in 26.3. Decision: use `BlockPos.ZERO.offset(vec)` (exact copy, single evaluation) rather than spelling out coordinates; where the argument is already a `BlockPos` (immutable) drop the copy.
- `ServuxLitematicaHandler` failure counters deliberately deferred (10-file cap and needs a Servux runtime check). Agreed mapping when done: `tickFailures()` increments, `checkFailures()` returns `failures <= MAX_FAILURES`, because MaFgLib's `sendPlayPayload` calls `checkFailures()` before sending.
- The remaining 105 errors: render draw path (~65), signs, `swing`, entity/Minecraft statics, AO processors, Iris/Sodium, chunk packet getters, MaFgLib render dispatcher. Obsolete `mixin/test/*` still waiting for the user's OK to delete.

## Phase 3, batch 5: signs, interaction, Servux, Sodium (2026-10-17)

- 10 files; errors 105 → 82. Everything left is rendering: draw path, AO processors, MaFgLib `IRenderer` reshape.
- Sign API decisions: `hasMessage(Player)` became `hasMessage(boolean)`, fed with `player.isTextFilteringEnabled()` to preserve the old behaviour. `SignText.DIRECT_CODEC` replaced by `SignText.CODEC` after checking the codec keys (`messages`, `filtered_messages`, `color`, `has_glowing_text`) are the same; legacy-sign conversion needs a runtime check. `IMixinSignBlockEntity` untouched because the private fields still exist.
- Swing: vanilla's `startUseItem` calls `swing(hand, stack.getInteractAnimation(), false)`, so easy-place does the same. `SwingSource.CLIENT` was renamed `PREDICTED` and applied literally; the odd `InteractionResult.SUCCESS.swingSource()` check was kept as is on purpose (not a place to change behaviour).
- `ItemInHandRenderer#itemUsed` no longer exists; `LocalPlayer#itemUsed(hand)` is the replacement vanilla uses.
- Servux: `tickFailures()` increments, `checkFailures()` is `failures <= MAX_FAILURES`; `maxFailures()` left at MaFgLib's default (2) because nothing local uses it and it is MaFgLib's own contract.
- Sodium check now comes from `SodiumCompat.hasSodium()` (MaFgLib 0.6.0 split it out of `IrisCompat`). Behaviour of the three mixins is unchanged.
- Deferred on purpose: AO processors (need comparison with vanilla 26.3 AO), `RenderHandler`/`InitHandler` (MaFgLib `IRenderer` has a new `onRenderWorldLast` signature and no pre-weather hook: needs a design decision), draw path. Still waiting for the user's OK to delete the obsolete `mixin/test/*`.

## Phase 3, batch 6: render draw path (2026-10-17)

- **`compileJava` PASSES** (82 → 0 errors, plain wrapper). Compile-level port only; the mod has never been launched.
- `ChunkSectionLayerGroup#outputTarget()` replaced by `mc.gameRenderer.mainRenderTarget()`: I disassembled the cached 26.2 jar to confirm 26.2 returned the main target whenever there was no translucent target, and 26.3's `LevelTargetBundle` has none. Same behaviour, not a guess.
- AO ported from vanilla 26.3 `BlockModelLighter` bytecode, not invented: `isLightPermeable()` for corner translucency, `shadeDirectionOverride()` for directional shade. Shared helper `AOProcessor#getDirectionalBrightness`.
- `registerWorldPreWeatherRenderer` removed from `InitHandler` because `RenderHandler` never overrode any pre-weather hook, so nothing changes in behaviour.
- The schematic translucent layer keeps classic blending in its own pass; no OIT integration. Flagged for runtime.
- Known runtime blocker for the next phase: `MixinLevelRenderer` injection targets (`prepareChunkRenders`, `lambda$addMainPass$0`, `renderGroup`) are stale and `defaultRequire: 1` turns that into a launch crash; every other mixin target must be verified with `javap` before launching. Full checklist is in `MIGRATION_26_3.md`.
- Still no commit, push or publish. `.github/` removal is staged only. Obsolete `mixin/test/*` still waiting for the user's OK to delete.

## Runtime phase, batch 1: mixin retargeting + smoke launch (2026-10-17)

- Audit method: diff `javap -p` of the cached 26.2 and 26.3 merged jars for every member a mixin names, plus bytecode check of each `@At(INVOKE)` (javap omits the owner for same-class calls). Only 4 mixin groups changed: sign edit screen (`slot`), `GameRenderer#extractCamera`, `DebugScreenOverlay#extractLines(…ZI)`, and `LevelRenderer`.
- `MixinLevelRenderer` injector decisions: capture before `addMainPass`; prepare layers on both `prepareChunkRenders` and `prepareChunkRendersIndirect`; draw opaque and classic-translucent **into the RenderPass vanilla passes to** `executeSolid`/`executeClassicTransparency` (preserves 26.2 ordering, avoids a nested pass on the same attachments); in OIT mode draw the translucent group after `executeOit` with our own pass. The Iris placeholder injector was re-anchored before `addMainPass`.
- `ChunkRenderBatchDraw` no longer swallows exceptions silently: the first failure is logged at ERROR (once per session).
- `./gradlew runClient` smoke launch (opens a real window; I stopped the client and its Gradle wrapper afterwards by PID, leaving the IDE's java processes alone): reaches the title screen, no Mixin/pipeline/shader errors. One real ERROR: the Servux payload `servux:litematics` is registered too late (MaFgLib 0.6.0 registers through `RegisterPayloadHandlersEvent`; `EntityDataManager` registers from the init handler). The `malilib.json` load error is a first-run artefact.
- **Phase verdict: NOT COMPLETE.** To finish: register the Servux payload before the event closes, then a world-join smoke test with a loaded schematic (world-time mixins and the legacy-terrain pipelines have never run).

## Runtime phase re-run (2026-10-17)

- Fixed: Servux payload now registered in the `Forgematica` constructor (MaFgLib 0.6.0 closes registration after `RegisterPayloadHandlersEvent`); `logoFile` → `iconFile` in our `neoforge.mods.toml`. The other two `logoFile` warnings belong to third-party jar-in-jar libs (conditional-mixin 0.6.4/0.6.5, foxified-classtweaker) and are left on purpose.
- `malilib.json` "Failed to load" was just a missing file; configs are written on a normal quit.
- A world session (about 70 s, clean exit) showed no errors and world-time mixins applying, but no schematic was ever saved/placed, so the schematic render path is still unverified. Quick-play cannot create worlds and the server EULA is not mine to accept, so the in-world test is user-driven.
- **Phase verdict: NOT COMPLETE — re-run with an actual schematic placement session.** The client was started by me with `runClient` and exited by itself (the user quit), so no process cleanup was needed.

## Runtime phase re-run #2 (2026-10-17)

- Test schematic generated by a stdlib Python script in the scratchpad (`make_test_litematic.py` → `run/schematics/test_26_3.litematic`, git-ignored), loaded fine by the game. Reusable for future smoke tests.
- The user ran a ~12 minute session: no errors, no mixin/pipeline messages, schematic renderer updating chunks, clean exit. The only `ERROR` lines are Litematica's own `DEBUG_MODE` trace (`addTask: [EMPTY] Waking up threads...`), expected when running from the IDE.
- Third-party `logoFile` warnings (conditional_mixin, foxified_classtweaker): user chose "leave + draft upstream issues"; drafts are in `MIGRATION_26_3.md`, nothing posted.
- **Phase verdict: COMPLETE on the technical criteria** (compile, static audit, clean startup and schematic session). Visual correctness (colors, translucency, OIT, AO) was not confirmed by the user in the conversation; if something looked wrong the phase reopens.
- Housekeeping: monitors should filter the `Waking up threads` trace, otherwise every line becomes a notification.

## Patched third-party libs (2026-10-17)

- The user first chose "leave + draft upstream issues" for the last two `logoFile` warnings, then asked again to fix them (and picked "implement the patch" when asked). Implemented `gradle/patched-libs.gradle`: SHA-256 pinned download of conditional-mixin 0.6.4 and foxified-classtweaker 0.1.0-alpha.4, rewrite of only `META-INF/neoforge.mods.toml` (`logoFile` → `iconFile`), served from `build/patched-libs/repo` with the same coordinates through `exclusiveContent` + `includeVersion`.
- Result: dev client start has zero mod-loader warnings; our shipped jar nests the patched conditional-mixin.
- Limits recorded in `MIGRATION_26_3.md` and `CLAUDE.md`: MaFgLib nests its own unpatched conditional-mixin build and foxified is a separate user-installed mod, so production logs can still show the warnings; delete the script when upstream ships `iconFile`. Upstream issue drafts kept in `MIGRATION_26_3.md`.
- Process note: I killed only the client and its Gradle wrapper by PID (the other java.exe processes are the IDE's).

## Areas A–D and multiplayer test (2026-10-17)

- The user runs the same `/plan` prompt repeatedly: one area (A Rendering, B Input/GUI, C Mixins/access, D Dependencies) per run, at most 8 files, then update `MIGRATION_26_3.md` and stop. Every area has had at least one batch; D had two (ranges and Foxified, then networking and client-only separation).
- Decisions:
  - Vanilla's `chunkLayerSampler` is no longer stored or closed by the schematic renderer (A).
  - GUI click handlers use `InputConstants.MOUSE_BUTTON_*` (B).
  - No mixin changes were needed (C).
  - `range-game`/`range-neoforge` were capped to `[26.3,26.4)` and `foxified_classtweaker` declared `required` (D). The cap diverges from the 26.2 branch's open ranges on purpose.
- Multiplayer test setup:
  - A clean NeoForge 26.3.0.88 server lives in `run/dedicated` (git-ignored; installer in `run/dedicated-installer`).
  - Settings: `eula=true` (the user asked for a running server), `online-mode=false`, `white-list=false`.
  - Launch with `java @user_jvm_args.txt @libraries/net/neoforged/neoforge/26.3.0.88/win_args.txt nogui`.
  - The dev client joins with `runClient --args="--quickPlayMultiplayer localhost:25565"`.
- Commit `6699bf00` holds the whole port (the user asked for it). `.atl/`, `AGENT.md`, `CLAUDE.md` and `MEMORY.md` were deliberately left untracked as agent files. No commit attribution lines, per the user's global rule.
- The user is new to Litematica; a usage walkthrough was given in chat (`M` menu, stick tool, Ctrl+scroll modes, easy place, `M,L` material list, `M,V` verifier).

## Area A batch 2 (2026-10-17, read-only)

- Findings:
  - The `registerWorldPreWeatherRenderer` removal lost nothing: 26.2 `RenderHandler` never overrode the callback, and MaFgLib 0.6.0 has no pre-weather hook.
  - Overlays and placement boxes are confirmed by the multiplayer screenshot `run/screenshots/2026-10-17_20.37.25.png`.
- The user declined the automated in-game session (an F2 keystroke sent to the game window through PowerShell `SendKeys`) and asked to record the results in the docs instead. Prefer proposing in-game checks as a procedure for the user, rather than sending input to their desktop.
- Ready test: quick-play "New World" (its saved `test_26_3` placement is restored on join), F2, then toggle `improvedTransparency` (`run/options.txt` or Video Settings) and repeat. The procedure is in `MIGRATION_26_3.md` → Area A batch 2.
- The `M,C`/`M,P`/`M,R`/`M,G`/`M,T` combos were confirmed working in game. The user first pressed `M` and released it, which opens the menu; combos need `M` held.

## Ghost blocks never rendered: fixed (2026-10-17, evening)

- The in-game test showed that only overlays and boxes ever rendered; the schematic blocks themselves never did. Every earlier "clean log" verdict had missed this.
- Cause 1: 26.3 `NbtUtils.readBlockState/writeBlockState` use the keys `id`/`properties`, while `.litematic` palettes use `Name`/`Properties`, so every block loaded as air. Fixed with the new `util/BlockStateNbt` (it accepts both layouts and writes `Name`/`Properties`).
- Cause 2: `LitematicaPipelines.LEGACY_TERRAIN_STAGE` declared no color target (26.3 builders start with 0), so the opaque draw threw. Fixed with `withColorTargetState(ColorTargetState.DEFAULT)`.
- Verified in game: classic and OIT transparency, a Texture Filtering change, and a rejoin all render fully. Diagnosed with temporary `[TEMP-DEBUG]` logging, now removed.
- Lesson: always ask for a screenshot of the ghost blocks themselves; a log with no errors is not proof.
- The test placement in "New World" was moved to (-118, 68, 138); the original is in `run/config/litematica/...overworld.json.bak`.
- The server `run/dedicated` is in creative with `Dev` as operator (`ops.json`); `server.properties.bak` holds the original settings.

## Test phase (2026-10-17, 23:10–)

- Automated checks: `compileJava`, `build`, jar metadata, the mixin config (51/51 classes) and the client launch plus server join all PASS. `test` is not available (no test source set).
- Security, upstream issue #11: the fix commit `f6dc5247f7` (26.1, 0.5.1) is fully present. Received schematics get UUID file names, and the Servux file-transmit receive path is commented out with no active caller. MaFgLib 0.6.0 (built 2026-10-09) is newer than the fixed 0.5.4, checked by version and date only.
- 26.3 also has a vanilla DataFixer, `BlockStateFieldNamesFix` (`Name`→`id`, `Properties`→`properties`), so older schematics run through the DataFixer come out with the new keys. `BlockStateNbt` reads both layouts.
- New benign WARN at join: 8 `Unable to read property ... cobblestone_wall` lines from the 1.12 conversion table.
- Phase NOT COMPLETE. Still to be done in game: create/save/reload a schematic, rotate/mirror, the material list, Ctrl/Alt+scroll, the editing tools and a creative paste.
- User rules confirmed this session: every user test launches the dedicated server and the client; in-game instructions go in short Spanish steps; never send keystrokes to the game.
- Setup:
  - The `altbuild2.init.gradle` init script redirects the build dirs of both the root project and `buildSrc` to the scratchpad, needed when the IDE locks `buildSrc/build`.
  - The server task from the 21:10 session was left running and locked the world. Always stop old server tasks before starting a new one.

## Test phase closed (2026-10-18 01:40)

- The user ran rounds 1–10 in game on the server + client setup.
- PASS: everything except Replace (partial: both blocks ended up stone), performance (not measured) and one easy-place server rejection (never reproduced).
- Verdict: NOT COMPLETE (formally); all core functions verified.
- Docs updated in `MIGRATION_26_3.md`; not committed yet.
- Test artifacts:
  - `run/schematics/prueba.litematic` and `test_26_4.litematic`;
  - `run/dedicated` in creative with `Dev` as operator (`server.properties.bak` holds the original);
  - the "New World" placement was moved (`...overworld.json.bak` holds the original).
- Instruction style: the user found the Replace steps (Alt/Shift + middle click) too hard. Say "con el PALO en la mano" explicitly and keep one action per step.
