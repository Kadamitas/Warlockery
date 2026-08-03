# Developing Warlockery

Warlockery is a Forge mod for Minecraft 26.2. This guide covers local setup, the project layout, common development tasks, testing, and release builds.

## Requirements

- JDK 25
- Git
- An IDE with Gradle support
- At least 4 GB of free memory for development runs

The repository includes the Gradle wrapper, so a separate Gradle installation is not needed.

## Setting up the project

Clone the repository and import its `build.gradle` as a Gradle project. Configure the IDE to use JDK 25 for both Gradle and Java compilation.

Run a compile before opening a development client:

```powershell
.\gradlew.bat --no-daemon classes testClasses
```

On Linux or macOS, use `./gradlew` in place of `.\gradlew.bat`.

## Build from source

Create a clean development build with:

```powershell
.\gradlew.bat --no-daemon clean build
```

The distributable JAR is written to `build/libs`. The `build` directory is generated and should not be committed.

Create a versioned publishing bundle with:

```powershell
.\gradlew.bat --no-daemon releaseBundle
```

The task writes the binary JAR, source JAR, license, changelog, and SHA-256 checksum files to `release/<version>`. Upload the binary JAR without the `-sources` suffix to a mod hosting site.

## Running Minecraft

ForgeGradle provides the development launch tasks:

```powershell
.\gradlew.bat --no-daemon runClient
.\gradlew.bat --no-daemon runServer
.\gradlew.bat --no-daemon runGameTestServer
.\gradlew.bat --no-daemon runData
```

Client and server instances use the `run` directory. Data generation writes to `src/generated/resources` and reads the handwritten resources in `src/main/resources`.

Delete a development world before retesting world generation changes. Keep test worlds, logs, and generated launch files out of commits.

## Project structure

| Path | Contents |
| --- | --- |
| `src/main/java/com/kadamitas/warlockery` | Mod initialization, gameplay code, registries, networking, and client code |
| `src/main/resources/assets/warlockery` | Textures, models, sounds, translations, and item definitions |
| `src/main/resources/data/warlockery/recipe` | Crafting, smelting, and cooking recipes |
| `src/main/resources/data/warlockery/ritual` | Circle ritual definitions |
| `src/main/resources/data/warlockery/warlockery_machine` | Machine and fixed-brew recipes |
| `src/main/resources/data/warlockery/tags` | Mod-specific extension tags |
| `src/main/resources/data/c/tags` | Common cross-mod material tags |
| `src/main/resources/data/minecraft/tags` | Vanilla behavior integration |
| `src/test/java/com/kadamitas/warlockery` | JUnit tests |
| `tools` | Asset and content generation utilities |

The entry point is `Warlockery.java`. Registry classes live under `registry`, while larger gameplay systems are divided into packages such as `ritual`, `brew`, `crafting`, `entity`, `item`, and `transformation`. Client-only screens and renderers belong under `client` and must not be referenced from dedicated-server code.

## Adding game content

Register new content through the appropriate class in `registry`. A normal block or item also needs a translation, model or item definition, texture, recipe or loot path, and any applicable tags.

Use vanilla item identifiers for exact vanilla ingredients such as paper, glass bottles, sugar, clay balls, and quartz blocks. Use `c:` tags when an established common material family exists, such as silver ingots, wooden chests, feathers, or bones. Use `warlockery:` tags for magical roles that other data packs may extend.

Ritual definitions belong in `data/warlockery/ritual`. Machine recipes belong in `data/warlockery/warlockery_machine`. Both loaders accept namespaced data supplied by other mods and data packs.

When a data format changes, update its Codec, validation, reload listener, and tests together. Invalid data should produce a useful log message instead of failing later during gameplay.

## Localization

English in `assets/warlockery/lang/en_us.json` is the source language. French, Spanish, Brazilian Portuguese, German, Polish, Japanese, Korean, Russian, Turkish, Simplified Chinese, and Traditional Chinese for Taiwan use `fr_fr`, `es_es`, `pt_br`, `de_de`, `pl_pl`, `ja_jp`, `ko_kr`, `ru_ru`, `tr_tr`, `zh_cn`, and `zh_tw` files in the same directory.

Use `Component.translatable` for player-facing Java text. Data-driven rituals store `title_key` and `description_key` values rather than embedded English sentences. Keep every locale's key set identical to `en_us.json`, save files as UTF-8, preserve formatting placeholders such as `%s`, and choose vocabulary by gameplay meaning. Run the localization integrity test after changing any locale:

```powershell
.\gradlew.bat --no-daemon test --tests com.kadamitas.warlockery.localization.LocalizationIntegrityTest
```

## Networking and client code

Server code owns gameplay state. Menus, overlays, and floating diagnostics should display server-provided state instead of duplicating requirement checks on the client.

Register payloads in `ModNetwork`. Keep payload records immutable and validate positions, identifiers, counts, and permissions again on the server before changing the world.

Client event subscribers, renderers, screens, and HUD layers belong in the client package and use `Dist.CLIENT` registration. Start `runServer` after client work to catch accidental client class loading on a dedicated server.

## Compatibility

Warlockery uses Forge item and fluid capabilities for machine automation. Shared materials use established `c:` or vanilla tags. Magical concepts use Warlockery tags so integrations can opt in without claiming a global standard that Forge does not provide.

Altar power is its own gameplay resource, not Forge Energy. Integrating another mod's energy or mana system requires an optional adapter for that specific API.

The optional JEI integration compiles against JEI 30.15.0's common API. It loads only when a compatible Forge JEI runtime is present, so Warlockery remains usable without JEI. Do not use a NeoForge-only JEI file in a Forge installation.

The available material, equipment, creature, machine, and guide-book extension points are documented in [Cross-mod compatibility](docs/CROSS_MOD_COMPATIBILITY.md).

## Server configuration

Warlockery creates `warlockery-server.toml` in a world's `serverconfig` directory. Server owners can enable or disable hobgoblin settlements, settlement fortifications, village assaults, silver hunts, and automatic silver equipment for pillagers. The same file controls event chances, check intervals, and the multiplier used for time between village assaults. New worlds use the gameplay defaults defined in `WarlockeryConfig`.

Keep server configuration with the world or modpack configuration. Do not place it in a resource pack or data pack.

## Custom resource packs

Warlockery assets use normal namespaced Minecraft resource locations. A resource pack can replace textures, sounds, translations, item models, block models, and other client resources without changing the mod JAR.

Start with this layout:

```text
My Warlockery Pack/
  pack.mcmeta
  assets/
    warlockery/
      sounds.json
      textures/
        block/
        entity/
        item/
        gui/
      models/
        block/
        item/
      lang/
      sounds/
```

For Minecraft 26.2, a minimal `pack.mcmeta` is:

```json
{
  "pack": {
    "description": "Custom Warlockery resources",
    "max_format": 107,
    "min_format": [107, 1]
  }
}
```

Copy only the assets being replaced and keep their paths and file names identical to the originals in `src/main/resources/assets/warlockery`. Put the custom pack above other packs that replace the same asset. Preserve a texture's companion `.png.mcmeta` file when replacing an animated texture. A pack that introduces custom OGG files must also supply matching entries in `assets/warlockery/sounds.json`. Resource packs can replace creature textures, but creature geometry implemented in Java requires a code-level renderer or model integration.

## Tests

Run all JUnit tests with:

```powershell
.\gradlew.bat --no-daemon test
```

HTML reports are written to `build/reports/tests/test`. Machine-readable results are written to `build/test-results/test`.

Run Forge GameTests after changing world interactions, entities, item use, capabilities, networking, rituals, or machines:

```powershell
.\gradlew.bat --no-daemon runGameTestServer
```

Keep small rules and serialization tests in JUnit. Use GameTests when the behavior needs a real level, registry access, ticking, entities, inventories, or block entities.

## Troubleshooting

If a resource fails to load, check `run/logs/latest.log` for the first recipe, tag, model, or Codec error. Later messages are often consequences of that first failure.

If Gradle uses the wrong Java version, compare `java -version` with `.\gradlew.bat --version` and set `JAVA_HOME` to a JDK 25 installation.

If a client feature crashes a dedicated server, inspect common classes for imports from `net.minecraft.client` and move the client registration behind a client-only event subscriber.

## Preparing a release

1. Update the version in `build.gradle`, `update.json`, and `changelog.txt`.
2. Run `clean build` and inspect the JUnit report.
3. Run `runGameTestServer` for gameplay changes.
4. Run `releaseBundle` to collect the binary JAR, source JAR, license, changelog, and SHA-256 checksums in `release/<version>`.
5. Test the binary JAR in clean client and dedicated-server Forge instances.
6. Check both logs for missing translations, models, textures, tags, recipes, and optional-integration errors.
7. Create a matching Git tag and attach the binary JAR from `release` to the release page.
8. Upload the same binary JAR and changelog to the supported mod hosting sites with matching game and loader metadata.

Do not publish development caches, local run directories, IDE settings, test worlds, or access tokens.
