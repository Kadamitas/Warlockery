# Warlockery

[Releases](https://github.com/Kadamitas/Warlockery/releases) | [Issue tracker](https://github.com/Kadamitas/Warlockery/issues) | [MIT license](LICENSE)

Warlockery is a standalone ritual magic mod by Kadamitas for Minecraft 26.2 and Forge 65.1.0.

It adds interactive chalk-circle rites, custom brewing, ritual machines, dolls and links, vampire and werewolf progression, magical creatures, biome-aware Ents, kobold village enclaves, silver hunting equipment, and Netherite-tier Koboldite gear.

## Installation

1. Install Minecraft 26.2 with Forge 65.1.0 or newer.
2. Download the matching Warlockery release and copy its JAR into the instance or server `mods` folder.
3. Install the same Warlockery version on the client and server.
4. Start Minecraft and confirm that Warlockery appears in the Mods screen.

JEI integration is optional and activates when a compatible Forge JEI build is installed. The integration targets JEI 30.15.0 or newer through its common API.

## Languages

Warlockery includes English, French, Spanish, Brazilian Portuguese, German, Polish, Japanese, and Traditional Chinese for Taiwan. Minecraft uses the English text as a fallback when a translated entry is unavailable.

## Custom textures

Standard Minecraft resource packs can replace Warlockery textures, item and block models, and sounds without modifying the mod JAR. Keep the original path under `assets/warlockery`, then place the resource pack above Warlockery's built-in resources. The [developer guide](DEV.md#custom-resource-packs) includes a working folder layout and pack metadata example.

## Documentation

- [Features](FEATURES.md) lists rituals, dolls, important items, and progression interactions.
- [Developer guide](DEV.md) explains building, testing, data files, configuration, resource packs, and compatibility contracts.
- [Player manual](docs/WARLOCKERY_MANUAL.md) provides a practical progression reference.
- [Creature and world guide](docs/CREATURES_AND_WORLD.md) describes mobs, variants, villages, and encounters.
- [Cross-mod compatibility](docs/CROSS_MOD_COMPATIBILITY.md) documents tags, capabilities, and extension points.

## Support

Report bugs through the [issue tracker](https://github.com/Kadamitas/Warlockery/issues). Include the Warlockery, Minecraft, Forge, and optional-mod versions, a clear reproduction, and the relevant `latest.log` or crash report. Confirm the problem with the latest Warlockery release before filing an issue.

## Repository structure

- `src/main/java` contains the Forge runtime and client implementation.
- `src/main/resources` contains assets, recipes, tags, loot, world generation, manuals, and other data-driven content.
- `src/test/java` contains unit, integration, and resource-contract tests.
- `docs` contains player and compatibility documentation.
- `tools` contains reproducible content and original-asset generators.
- `gradle` and the wrapper scripts provide the pinned build entry point.

## License and modpacks

Warlockery is available under the [MIT license](LICENSE). It may be included in modpacks and redistributed under that license.
