# Creatures and modern world integration

Warlockery registers 47 living creature types. Every type has a spawn egg, translation, loot table, renderer, and custom texture. Specialized creatures use dedicated entity classes, while simpler creatures share ground, flying, villager, illager, golem, or spirit behavior where appropriate.

## Natural ecology

- Ents appear very rarely across forests, jungles, taigas, savannas, swamps, cherry groves, and pale gardens. Their persisted Oak, Birch, Spruce, Jungle, Dark Oak, Acacia, Mangrove, Cherry, or Pale Oak variant has a visible foliage tint and a bounded biome-themed health, damage, speed, and armor profile.
- Goblins occur in small forest, swamp, and plains groups. They raid ordinary villagers when they encounter them.
- Hobgoblins are friendly travelling traders. A rare server event can gather a temporary hobgoblin enclave around a loaded village bell without turning them hostile to its residents.
- Spirits and mandrakes have intentionally low Overworld spawn weights. Hellhounds are restricted to Nether biomes.
- Bosses, demons, imps, and dangerous one-off entities are mainly ritual or spawn-egg content so ordinary nights are not flooded by the roster.

## Hobgoblin travellers

Hobgoblins use Minecraft's villager brain, allowing travel, beds, trading, experience, demand, and restocking to work through native systems. Their profession is assigned when they appear, so naturally wandering traders do not require a permanent custom village.

| Workstation | Career | Economy |
| --- | --- | --- |
| Stonecutter | Miner | Coal, emeralds, and raw Goblinite |
| Blast furnace | Smith | Raw Goblinite and Goblinite tools |
| Brewing stand | Shaman | Redstone, magical fumes, and attuned stones |
| Cartography table/default | Prospector | Raw Silver, emeralds, and Goblinite nuggets |

## Werewolf hunts and crossbows

Werewolf Hunters extend Pillager crossbow combat. On rare full-moon nights, a Werewolf Hunter and an ordinary Pillager can engage a spawned Werewolf near a player. Pillagers that encounter an existing Werewolf can receive a vanilla Crossbow and Silver Bolts before targeting it.

The vanilla Crossbow supplies charging, Quick Charge, Multishot, durability, animation, and projectile selection. Silver Bolts bypass Werewolf and Vampire mitigation and deal bonus damage. Stakes punish vampiric creatures, Holy Bolts punish spirits and vampires, and Anti-Magic Bolts strip active effects.

## Material interoperability

Silver publishes canonical `c:ingots/silver`, `c:raw_materials/silver`, `c:ores/silver`, `c:dusts/silver`, and private supernatural-weapon tags. Goblinite publishes matching canonical tags for ingots, raw materials, ores, nuggets, dusts, and storage blocks, with deprecated `delvealloy` aliases retained for saved data packs. Both metals generate as stone and deepslate ores, drop raw forms with modern Silk Touch and Fortune rules, smelt or blast into ingots, and have raw and refined storage blocks.

Goblinite equipment matches Netherite tier and includes all five tool types plus four armor pieces. Hunter crossbows and supernatural combat query private entity and weakness tags, so other mods can opt creatures and silver items into the same rules without class-name checks.
