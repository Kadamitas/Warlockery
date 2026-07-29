# Warlockery

Warlockery is a standalone ritual magic mod by Kadamitas for Minecraft 26.2 and Forge 65.1.0.

It adds interactive chalk-circle rites, custom brewing, ritual machines, dolls and sympathetic links, vampire and werewolf progression, magical creatures, biome-aware Ents, kobold village enclaves, silver hunting equipment, and Netherite-tier Koboldite gear. Rituals, machines, traps, and progression systems provide visible diagnostics when a requirement is missing.

## Installation

1. Install Minecraft 26.2 with Forge 65.1.0.
2. Copy the Warlockery JAR into the instance or server `mods` folder.
3. Install the same Warlockery version on the client and server.
4. Start Minecraft and confirm that Warlockery appears in the Mods screen.

Warlockery has no required content-mod dependency beyond Forge.

## Documentation

- [Features](FEATURES.md) lists rituals, dolls, important items, and progression interactions.
- [Developer guide](DEV.md) explains building, testing, data files, and compatibility contracts.
- [Player manual](docs/WARLOCKERY_MANUAL.md) provides a practical progression reference.
- [Creature and world guide](docs/CREATURES_AND_WORLD.md) describes mobs, variants, villages, and encounters.
- [Cross-mod compatibility](docs/CROSS_MOD_COMPATIBILITY.md) documents tags, capabilities, and extension points.

## Repository structure

- `src/main/java` contains the Forge runtime and client implementation.
- `src/main/resources` contains assets, recipes, tags, loot, world generation, manuals, and other data-driven content.
- `src/test/java` contains unit, integration, and resource-contract tests.
- `docs` contains player and compatibility documentation.
- `tools` contains reproducible content and original-asset generators.
- `gradle` and the wrapper scripts provide the pinned build entry point.
