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

## Networking and client code

Server code owns gameplay state. Menus, overlays, and floating diagnostics should display server-provided state instead of duplicating requirement checks on the client.

Register payloads in `ModNetwork`. Keep payload records immutable and validate positions, identifiers, counts, and permissions again on the server before changing the world.

Client event subscribers, renderers, screens, and HUD layers belong in the client package and use `Dist.CLIENT` registration. Start `runServer` after client work to catch accidental client class loading on a dedicated server.

## Compatibility

Warlockery uses Forge item and fluid capabilities for machine automation. Shared materials use established `c:` or vanilla tags. Magical concepts use Warlockery tags so integrations can opt in without claiming a global standard that Forge does not provide.

Altar power is its own gameplay resource, not Forge Energy. Integrating another mod's energy or mana system requires an optional adapter for that specific API.

The available material, equipment, creature, machine, and guide-book extension points are documented in [Cross-mod compatibility](docs/CROSS_MOD_COMPATIBILITY.md).

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

1. Update the version in `build.gradle` and the changelog.
2. Run `clean build`.
3. Run `runGameTestServer` for gameplay changes.
4. Test the built JAR in a clean Forge instance.
5. Create a matching Git tag and attach the JAR from `build/libs` to the release page.

Do not publish development caches, local run directories, IDE settings, test worlds, or access tokens.
