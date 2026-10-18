<center><div align="center">

<img height="100" src="src/main/resources/icon.png" width="100"/>

# Forgematica for NeoForge

Litematica unofficial NeoForge port.

<img alt="neoforge" height="56" src="https://raw.githubusercontent.com/KessokuTeaTime/badges-extra/main/assets/cozy/supported/neoforge_vector.svg">

</div></center>

Forgematica is a client-side Minecraft mod using NeoForge. It is more or less a re-creation of or a substitute for [Schematica](https://minecraft.curseforge.com/projects/schematica).

[Original Repo Readme](Original-README.md)

## Private 26.3 build (this branch)

This branch (`neoforge/26.3`) is an **unofficial, private port to Minecraft 26.3**. It is a stopgap until the official developers port 26.3, and it is not published on Modrinth, CurseForge or any Maven repository. The mod id is still `forgematica`.

Builds of this branch are attached to this repository as GitHub **pre-releases** (one per commit pushed to `neoforge/26.3`, see `.github/workflows/release-26.3.yml`), or you can build the jar yourself (see [Compiling](#compiling)).

### Requirements

| Component | Version | Notes |
|---|---|---|
| Minecraft | 26.3 | The mod refuses to load on other versions (`[26.3,26.4)`) |
| NeoForge | 26.3.0.88 | Declared range `[26.3,26.4)` |
| Java | 25 | Required by Minecraft 26.x |
| MaFgLib | 0.6.0+mc26.3 | Required, **not bundled**. Declared range `[0.6,)` |
| Foxified ClassTweaker | 0.1.0-alpha.4 | Required, **not bundled** (applies the access widener) |

### Installation

1. Install NeoForge 26.3.0.88 for Minecraft 26.3 with Java 25.
2. Put these three jars in the `mods` folder of your game directory:
   - `forgematica-0.5.0+mc26.3.jar` (this mod, from the pre-release or from your own build)
   - `mafglib-0.6.0+mc26.3.jar` from `https://maven.kessokuteatime.work/releases/team/cagayakegirls/mafglib/mafglib/0.6.0%2Bmc26.3/mafglib-0.6.0%2Bmc26.3.jar`
   - `foxified-classtweaker-0.1.0-alpha.4.jar` from `https://maven.kessokuteatime.work/snapshots/team/cagayakegirls/foxified/foxified-classtweaker/0.1.0-alpha.4/foxified-classtweaker-0.1.0-alpha.4.jar`
3. Start the game. Forgematica is **client side only**: do not install it on a server, and it does not need anything on the server. It can join a plain NeoForge 26.3 server.
4. Schematics go in the `schematics` folder of the game directory. The default hotkey for the main menu is `M`; see the [litematica wiki](https://github.com/maruohon/litematica/wiki) for the rest.

### Known limitations

- **Tested in a development environment only.** The mod was run with `runClient` (singleplayer and a plain NeoForge 26.3 server). A normal launcher install of the packaged jar has **not** been tested, so the access widener and fluids in a production install, and the dependency screen that should appear on Minecraft 26.4, are untested. The full audit and test log is in [MIGRATION_26_3.md](MIGRATION_26_3.md).
- Easy place on servers without Servux or Carpet only encodes slab halves; stair facing follows the way you look, so some stairs show the orange wrong-state overlay. The Servux and Carpet easy place protocols were not exercised.
- Improved Transparency (Video Settings) cost about 10 percent of the frame rate in one sample on an RTX 5080; there is no 26.2 baseline and stutter while moving was not assessed.
- Servux "Litematic-Transmit" (receiving schematic files from a server) is deliberately disabled because of the upstream security issue [#11](https://github.com/CagayakeGirls/litematica-neoforge/issues/11); do not re-enable it. Only load schematics you trust.
- Only the features recorded as PASS in `MIGRATION_26_3.md` were checked in game (config GUI, hotkeys, loading and placing schematics, ghost blocks and overlays, material list, area selection, Replace and Delete tools, creative paste, easy place). Anything not listed there should be treated as untested.

### Licence

Forgematica is licensed under the **GNU LGPL v3** (see `LICENSE.txt`, also packaged in the jar as `LICENSE.txt_forgematica`). It is a port of Litematica by masa (maruohon) and sakura-ryoko; see [Credits](#credits).

## How to use in Minecraft
See [litematica wiki](https://github.com/maruohon/litematica/wiki)

## Development

> The snippets below are for the officially published versions of Forgematica. This private 26.3 branch is not published to these repositories.

This mod use modrinth maven.

```gradle
repositories {
    maven { url 'https://api.modrinth.com/maven' }
}

dependencies {
    modImplementation "maven.modrinth:forgematica:${forgematica_version}"
}
```

or use KessokuTeaTime maven (only 0.4.1 and above)

```gradle
repositories {
    maven { url 'https://maven.kessokuteatime.work/releases' }
}

dependencies {
    modImplementation "team.cagayakegirls.forgematica:forgematica:${forgematica_version}"
}
```

> Note: "${forgematica_version}" can be found in [Modrinth](https://modrinth.com/mod/forgematica)

## Compiling
- Clone the repository
- Open a command prompt/terminal to the repository directory
- run 'gradlew build'
- The built jar file will be in build/libs/

## Credits
- [maruohon/litematica](https://github.com/maruohon/litematica)
- [sakura-ryoko/litematica](https://github.com/sakura-ryoko/litematica)