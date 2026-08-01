# Cross-mod compatibility

Warlockery targets Fabric Loader 0.19.3 and Fabric API 0.155.2+26.2 for Minecraft 26.2. Codecs serialize data. They do not replace shared material contracts. Cross-mod substitution uses canonical `c:` tags, vanilla behavior tags, data-driven recipes, Fabric Transfer API storage, and Fabric API lookups.

## Tag strategy

Warlockery publishes shared materials through established `c:` tags and vanilla behavior tags. Mod-specific magical roles use `warlockery:` tags so data packs can extend them without assigning a misleading global meaning.

The compatibility catalog is stored in `src/main/resources/data/warlockery/compatibility/catalog.json`. It describes the tag and recipe contracts used by the mod.

## Published common contracts

| Family | Published contracts |
| --- | --- |
| Silver | `c:ores/silver`, raw material, ingot, dust, and storage block families |
| Goblinite | Preferred `c:*/*/goblinite` tags with save-compatible `delvealloy` aliases for ore, raw material, ingot, nugget, dust, and storage forms |
| Armor and tools | Vanilla armor slot and tool-family tags, `c:armors/humanoid`, `c:tools`, and the relevant enchantable tags |
| Crops and seeds | `minecraft:crops`, `c:crops`, `c:seeds`, and `minecraft:villager_plantable_seeds` |
| Alder, hawthorn, and rowan | Vanilla logs, burning logs, planks, leaves, saplings, shaped wood tags, and `c:natural_logs/overworld` |
| Hexwood | Vanilla and common log membership, burning-log membership, and vanilla plank membership |
| Food | `c:foods` subtypes and `minecraft:meat`; the Sleeping Apple also publishes `c:foods/fruit` |
| Drinkable ingredient brews | `c:drinks/magic` and `c:drink_containing/bottle` through `warlockery:drinkable_brews` |
| Fixed throwable brews | `c:potions/bottle` through `warlockery:throwable_brews` |
| Magical fluid buckets | `c:buckets`; Spirit, Hollow Tears, Colored Brew Water, and Erosion Brew remain in their honest private fluid tags |
| Navigation and storage tools | `minecraft:compasses` for both compasses and `minecraft:bundles` for the Brew Satchel |
| Guide books | `warlockery:guide_books` for the eleven complete manuals and `minecraft:bookshelf_books` for chiseled bookshelf storage |
| Bolts and crossbows | `minecraft:arrows` and Minecraft's vanilla crossbow |
| Building forms | Common fence, wooden fence, fence gate, glass block, and furnace-workstation tags where the live block behavior matches |
| Oven upgrades | `warlockery:machine_upgrades/fume_funnels`, its filtered tier, and `warlockery:alchemical_fumes` for compatible blocks and fume outputs |
| Brazier conjurations | Reloadable machine recipes, `warlockery:brazier/*` ingredient tags, `warlockery:brazier_igniters`, sided Transfer API automation, redstone ignition, and comparator output |
| Ritual tools and creatures | `warlockery:arthanas`, `warlockery:altar_range_foci`, specialist-drop entity tags, `warlockery:nightmares`, and creature-role tags |

The eight Warlockery crop seeds are real `CropBlock` planting items. The villager seed tag also flows into vanilla's `villager_picks_up` tag, so farmer AI can collect and plant compatible magical crops.

## Recipe substitution

Material-substitutable recipes consume established tags. This includes bones, wooden chests, candles, feathers, fertilizers, flint, gunpowder, lava buckets, colorless glass, milk buckets, redstone dust, ender pearls, and empty buckets. Exact vanilla ingredients such as clay balls, glass bottles, paper, quartz blocks, and sugar use their `minecraft:` item identifiers. Species-specific or effect-specific inputs also remain direct item IDs when accepting a broader family would change the recipe's meaning.

The `ritual` and `warlockery_machine` reload listeners accept valid definitions from every namespace. Other mods and data packs can therefore add namespaced rites and machine recipes without writing files into the `warlockery` namespace or replacing built-in definitions.

## Manual collection compatibility

Every complete manual remains a normal registered item whose use action opens the shared searchable library. Book-collection mods that store an item and later invoke that real item, including Akashic Tome and Eccentric Tome style systems, therefore retain the Warlockery screen without a hard dependency or special API adapter.

Complete manuals are published in `warlockery:guide_books` and `minecraft:bookshelf_books`. The torn page stays in the broader `warlockery:manuals` tag but is intentionally excluded from the complete guide list. Fabric's conventional tags have no canonical `c:books` contract, so Warlockery does not invent one.

Magical damage and brew gas participation use the private extension tags `warlockery:magical_damage` and `warlockery:brew_gases`. Other mods can add their damage types or gas blocks through a data pack without relying on invented global `c:` conventions.

## Projectiles and fuel

Silver, holy, stake, splitting, and anti-magic bolts are `ArrowItem` instances in `minecraft:arrows` and are fired by Minecraft's vanilla crossbow. Throwing Rocks extend vanilla `SnowballItem`. Fixed brews extend `SplashPotionItem`.

The fixed Brew of Combustion supplies 2,400 burn ticks through Fabric's fuel registry event. The inactive `brew.fuel` registry placeholder is not advertised as fuel.

## Fabric Transfer API

All nine machine block variants expose sided `ItemStorage.SIDED` views. Top, bottom, and horizontal access use the machine slot layout. Distilleries, kettles, cauldrons, and silver vats also expose `FluidStorage.SIDED` because their profiles store transferable fluids. Inserts and extracts participate in Fabric transactions, and machines without a tank do not publish fake fluid storage.

Machine interaction uses Fabric's fluid-container bridge, so vanilla buckets and compatible containers from other mods can move liquid without Warlockery recognizing each container class. Internal recipes retain their documented millibucket amounts and convert only at the Transfer API boundary to Fabric droplets.

Living and player progression state uses Fabric Data Attachments with persistent codecs. Network synchronization uses registered Fabric payload types, and biome additions use Fabric biome modifications rather than loader-specific data patches.

The Silver Vat recognizes adjacent furnace recipes whose inputs use `c:ores/gold` and whose outputs use `c:ingots/gold`. Each completed smelt adds a Silver Deposit to the vat's ordinary output inventory, so a hopper or compatible item pipe can extract it from below.

Fabric API has no universal energy or mana storage. Warlockery altar power is not labeled as another mod's mana. Items from an optional integration can expose `warlockery:energy_reserve` through `FabricEnergyCompatibility.ITEM`; integrations for incompatible mana systems still require a soft adapter for that API.

## Advanced mutation extension points

The Mutating Sprig is published through `c:tools` and `minecraft:enchantable/durability`. Minedrake Bulbs remain normal `c:seeds` and villager-plantable seeds.

Structure roles use private tags so other mods can opt in without claiming that a magical reagent is a common material. Shared structure tags are `warlockery:mutation/cobwebs`, `warlockery:mutation/grasspers`, and the fluid tag `warlockery:mutation/water`. Toad mutations add `warlockery:mutation/toad/slime_snares` and `warlockery:mutation/toad/hosts`. Minedrake mutations add `warlockery:mutation/minedrake/mandrake_crops`, `warlockery:mutation/minedrake/creeper_hosts`, and `warlockery:mutation/minedrake/living_mandrakes`.

Stored ingredients use `warlockery:mutation/mutandis_extremis`, `warlockery:mutation/focused_will`, and `warlockery:mutation/charged_attuned_stones`. A tagged external Grassper block should expose `ItemStorage.SIDED` on at least one face. A tagged external slime-snare block represents an already filled snare and is consumed on success. Warlockery Critter Snares instead keep their block and return to the empty payload state.

## Ritual tools and creature roles

Any item in `warlockery:arthanas` receives the specialist-harvest behavior. External mobs can join the exact harvest families through `warlockery:arthana_bat_sources`, `arthana_dog_sources`, `arthana_owl_sources`, `arthana_frog_sources`, `arthana_heart_sources`, and `arthana_spectral_sources`. Looting scales these additional drops through the same server-authoritative runtime.

An item in `warlockery:altar_range_foci` can be installed on an Altar to double its power-distribution range. `warlockery:brazier_igniters` accepts compatible ignition tools without treating every tool or fuel as an igniter. `warlockery:nightmares` lets third-party supernatural mounts participate in Icy Needle banishment and dream-resource behavior.

Ritual entity requirements accept either registry IDs or entity-type tags. Rite of Binding: Death uses `warlockery:death_binding/banshees`, `warlockery:death_binding/poltergeists`, and `warlockery:death_binding/spectres`, so compatible spectral creatures can participate without a code dependency. Creature calling rites retain their direct result entity because substituting a different output would change the rite, while their material inputs use common or private role tags where appropriate.

Contracted hobgoblin automation is extended through `warlockery:creature_interactions/hobgoblin_contracts`, `warlockery:creature_interactions/hobgoblin_collectibles`, and `warlockery:creature_habitats/hobgoblin_deposit_containers`. The default collectible group nests canonical `c:gems`, `c:ingots`, `c:ores`, and `c:raw_materials`. Passive Spirit spawning reads `warlockery:spirit_habitats` rather than hard-coding every compatible biome.

The magical tree oven routes use `warlockery:alder_saplings`, `warlockery:hawthorn_saplings`, and `warlockery:rowan_saplings`. These private family tags let other mods opt their related saplings into the matching essence while avoiding a false claim that all vanilla saplings have the same magical output.

## Intentional exclusions

- Unique magical reagents stay in private Warlockery tags unless a canonical material, food, container, tool, or equipment meaning applies.
- Spirit is not water, milk, experience, or another canonical common fluid, so it remains in `warlockery:spirit`.
- Colored Brew Water and Erosion Brew use `warlockery:colored_brew_water` and `warlockery:erosion_brews`. Their source and flowing forms work with Fabric fluid storage, while `bucketbrew` and `bucketerosionbrew` are functional `BucketItem` containers published through `c:buckets`.
- Shadedglass is a glass building block but does not implement vanilla tinted-glass light behavior, so it is not in `c:glass_blocks/tinted`.
- Hexwood's current `hex_leaves` and `hex_sapling` registrations are static legacy blocks, not live leaf and sapling implementations. They are not placed in vanilla leaf or sapling tags.
- Inactive registry placeholders such as `brew.fuel` and `brew.water` are not assigned container, potion, fuel, or fluid contracts. All four functional magical fluid buckets participate in `c:buckets`.
- Warlockery does not invent a global pipe, furnace, mana, or magic-reagent tag when Fabric API or the cross-loader `c:` namespace has no matching semantic contract.
