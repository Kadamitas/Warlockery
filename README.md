# Warlockery

[Releases](https://github.com/Kadamitas/Warlockery/releases) | [Issue tracker](https://github.com/Kadamitas/Warlockery/issues) | [MIT license](LICENSE)

Warlockery 1.5.0 is a standalone ritual magic mod by Kadamitas for Minecraft 26.2, Java 25, and Fabric.

It adds interactive chalk-circle rites, custom brewing, ritual machines, dolls and links, vampire and werewolf progression, magical creatures, biome-aware Ents, goblin village enclaves, silver hunting equipment, and Netherite-tier Goblinite gear.

## Installation

1. Install Minecraft 26.2 with Java 25 and Fabric Loader 0.19.3 or newer.
2. Install Fabric API 0.158.0+26.2 or newer for Minecraft 26.2.
3. Download the Fabric Warlockery release and copy its JAR into the instance or server `mods` folder.
4. Install the same Warlockery and Fabric API versions on the client and server.
5. Start Minecraft and confirm that Warlockery loaded successfully.

JEI integration is optional and activates when a compatible Fabric JEI build is installed. The integration targets JEI 30.7.0.41 or newer through its Fabric API.

Normal 1.5.0 releases are published for Forge 65.1.2, NeoForge 26.2.0.64, and Fabric Loader 0.19.3 with Fabric API 0.158.0+26.2. The `1.5.0-LlaGuiT0-26.2.0.45` supporter build remains NeoForge-only and requires NeoForge `[26.2.0.45-beta,26.2.0.46-beta)`.

## Languages

Warlockery includes English, French, Spanish, Brazilian Portuguese, German, Polish, Japanese, Korean, Russian, Turkish, Simplified Chinese, and Traditional Chinese for Taiwan. Minecraft uses the English text as a fallback when a translated entry is unavailable.

## Custom textures

Standard Minecraft resource packs can replace Warlockery textures, item and block models, and sounds without modifying the mod JAR. Keep the original path under `assets/warlockery`, then place the resource pack above Warlockery's built-in resources. The [developer guide](DEV.md#custom-resource-packs) includes a working folder layout and pack metadata example.

## Documentation

- [Features](FEATURES.md) lists rituals, dolls, important items, and progression interactions.
- [Developer guide](DEV.md) explains building, testing, data files, configuration, resource packs, and compatibility contracts.
- [Player manual](docs/WARLOCKERY_MANUAL.md) provides a practical progression reference.
- [Creature and world guide](docs/CREATURES_AND_WORLD.md) describes mobs, variants, villages, and encounters.
- [Cross-mod compatibility](docs/CROSS_MOD_COMPATIBILITY.md) documents tags, capabilities, and extension points.

## Support

Report bugs through the [issue tracker](https://github.com/Kadamitas/Warlockery/issues). Include the Warlockery, Minecraft, Fabric Loader, Fabric API, and optional-mod versions, a clear reproduction, and the relevant `latest.log` or crash report. Confirm the problem with the latest Warlockery release before filing an issue.

## Repository structure

- `src/main/java` contains the Fabric runtime and client implementation.
- `src/main/resources` contains assets, recipes, tags, loot, world generation, manuals, and other data-driven content.
- `src/test/java` contains unit, integration, and resource-contract tests.
- `docs` contains player and compatibility documentation.
- `tools` contains reproducible content and original-asset generators.
- `gradle` and the wrapper scripts provide the pinned build entry point.

## License and modpacks

Warlockery is available under the [MIT license](LICENSE). It may be included in modpacks and redistributed under that license.
