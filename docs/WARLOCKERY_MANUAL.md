# Warlockery Player Manual

## Using the manual library

Use any complete Warlockery manual to open the shared manual library. Search by book title, chapter title, or text, select a manual in the left column, then select a chapter in the middle column. Previous and Next cycle through that manual without returning to the index.

The extended Book of Biomes keeps its special shortcut. Sneak-use it with paper to create a Biome Note. Book-collection mods can store the normal manual items and open this same library when they invoke the stored book. Complete manuals also work in chiseled bookshelves.

## Reading status displays

Warlockery systems use server-authored status data. Red lines identify conditions that are absent or incorrect. Yellow lines identify resources that are incomplete. A green check means every required condition is currently satisfied. The display updates when chalk, ingredients, altar power, weather, moon phase, fuel, heat, output space, or nearby entities change.

Golden Chalk places the heart of a ritual when used normally. Crouch while placing it to draw a golden ring glyph instead. A working circle has one heart, so only one ritual browser opens at its center.

## Chalk circles

Use Ritual, Infernal, and Veil Chalk to draw the selected pattern, with Golden Chalk marking its center. Use the center of a completed circle to open the ritual browser. A Seer Stone used on the golden center calls the living Circle Mages you recruited that remain loaded and gathers them around the circle. Recruited Circle Mages count alongside players for coven requirements, while unbound Circle Mages do not. Select a ritual to view the required number of each glyph, each ingredient, each nearby entity sacrifice, structure progress, current altar power, casting time, participants, time of day, moon phase, weather, and dimension. The server validates the circle again when casting begins and while the staged ritual is active.

To locate settlements, draw a hollow 3 by 3 ring with eight Ritual Chalk marks and leave its middle empty. Drop an Attuned Stone or Subdued Spirit into that space for three seconds. The released soul trail and message point toward the nearest village in the Overworld or Nether fortress in the Nether and report its distance.

Rite of Binding: Death requires a full-moon night and five tagged beings from each of the Banshee, Poltergeist, and Spectre families. The display counts all fifteen sacrifices before showing the green check. A nearby player redirects the successful summon only while wearing Death's hood, robe, and footwear and wielding the Hand of Death.

Rite of the Bloodied Effigy requires bloodied wicker bundles two blocks north, south, east, and west of the center. The ritual display reports how many of the four are correct. Success consumes the bundles and summons the Thorned Pursuer.

Rite of Binding: Fetish reads the distinct spectral beings inside the circle and writes the resulting Disorientation, Ghost Walking, Sentinel, Shrieking, or Voodoo Protection mode into the Scarecrow it creates. An unbound Scarecrow reports that it still needs a binding. Rite of Binding: Statue copies a nearby sympathetic sample into a Hobgoblin Patron statue. Its offerings bless the bound player and report when the binding, player, or offering is missing.

Rite of Total Eclipse holds the server clock at night for its listed duration and restores the prior clock plus elapsed time afterward. Overlapping eclipses extend the current window instead of losing the original time.

## Dolls and sympathetic links

Craft a blank Doll into the required specialized type, then use it on the creature it should represent. Right-click air with a protection, mending, or guard doll to bind it to yourself. A glint and lore line confirm the binding. Protection dolls spend durability when they stop their matching lethal hazard. The Death Guard is the general fallback. It sets health to one and applies Minecraft's Regeneration, Absorption, and Fire Resistance recovery bundle without sending the Totem animation event.

Earth Guard recognizes falls, falling blocks, anvils, pointed dripstone, Elytra collisions, and mace smashes. Water Guard restores air and Water Breathing. Hunger Guard restores both hunger and saturation. Fire Guard clears fire, grants Fire Resistance, and attempts to move the target from lava to nearby safe footing. Tool and Armor Mending Dolls exchange one point of doll durability for two points of equipment durability. Hex Guard spends durability to block hostile circle hexes, including Heat Metal. Hexing Doll retains prick, shove, ignite, and drown actions. Blood Link splits damage. Doll Guard blocks attempts to corrupt protective dolls.

Doll Shelves accept dolls and sympathetic containers through shared item tags. A shelf with a bound doll keeps its chunk available across restarts, so the doll can protect its owner or mend the online owner's equipment outside the shelf chunk. Shelved mending runs once per second and duplicate dolls do not multiply the repair rate. Removing the last bound doll releases that ticket.

Place a Ritual Knife, Candelabra, Chalice, or Pentacle item onto the top of an altar to attach it visibly and apply its supported altar effect. Each altar block holds four nonconflicting attachments. Shift-right-click removes the latest attachment without consuming it.

The compact left-side panel lists bound dolls and remaining charges. A green pulse marks an activation. Harmful hex and effect durations use the custom hex sigil below the doll entries.

## Vampire path

Bring an unmarried Nami into the Blood Audience on a full-moon night. The ritual browser lists the three chalk types, altar power, and four offerings. The rite awakens Naamah in Nami's flesh. Defeat Naamah to obtain Matriarch's Blood, then offer that blood to Naamah to begin vampire progression. Using the blood by itself does nothing, and a mortal cannot bypass this initiation by drinking an ordinary full Glass Goblet. Existing vampires can drink a full goblet for health and reserve. Rite of Hexbreaking: Vampirism reverses the form when its sympathetic target and circle requirements are complete.

## Werewolf path and capture

Hex of the Wolf grants lycanthropy under its listed full-moon and coven conditions. Its Wolf Token catalyst has a survival recipe using a charged Attuned Stone, Wolf Head, and common raw meat. A Silver Werewolf Trap captures a werewolf when it is armed beside a Wolf Altar, an adult sheep is within eight blocks, and the night has a full moon. The trap display reports all four conditions and shows a green check before capture. Wolf Altar trials advance the werewolf path to level ten, where the altar awards the Horn of the Hunt. Rite of Hexbreaking: Lycanthropy restores a bound werewolf to mortal form.

## Machines

Cauldrons and ritual machines compare their inventory and fluid tank with the closest known recipe. Their floating displays name missing ingredients or fluids, wrong or extra items, required heat or fuel, blocked outputs, and progress. A green check appears when the recipe and machine state can proceed. Forge item handlers are sided so pipes can insert inputs and fuel or extract outputs. Cauldrons, kettles, distilleries, and silver vats expose Forge fluid handlers.

Brew of Bodega requires a bound Owl familiar near the kettle. Its display names that missing familiar before processing. The completed brew summons owner-bound owls that attack tagged threats, hostile enemies, or the owner's current attacker, not unrelated passive creatures. A nearby Emberhorn Archfiend expands cauldron and Altar Power reach by eight blocks. Each Archfiend also adds a 12.5 percent chance that a completed fixed or custom cauldron brew visibly backfires on nearby players, with two Archfiends as the cap.

Recipes use ordinary Minecraft clay balls, glass bottles, paper, quartz blocks, and sugar. Materials with an established common family, such as silver, bones, feathers, and wooden chests, use `c:` tags so equivalent items from other mods can participate.

## Brews

Warlockery has 127 built-in brews. Standard status effects use Minecraft Potion Contents. Drinkable custom formulas apply their effects directly to the drinker, while throwable formulas scale by impact distance. Instant damage is capped at one standard health bar unless the Pufferfish uncapped-damage modifier is present, with a separate engine-safety ceiling. World brews share capped, tag-aware behavior for growth, fire removal, freezing, webs, ignition, explosions, movement, breeding, owl and bat swarms, blight, terrain shaping, snow trails, rising branches, block substitution, ore transposition, gas dispersal, equipment erosion, reversible fluid paths, biome seasons, fluid solidification, and targeted supernatural damage. Persistent brews track arrow attraction and reflection, magic absorption and reserve transfer, fear, dangerous leaps, darkness prey, overheating, sleeping manifestations, underwater dependence, creature repulsion, teleport inhibition, death preservation, contagion, sinking, sunlight vulnerability, and werewolf form locking.

The Runed Cauldron assembles ordered formulas from reloadable component definitions. Finished mixtures can be drunk, thrown, dispersed as gas with Wool of Bat, spread as liquid with Wormwood, or bound to a button, lever, door, or pressure plate with a Zombie Head. Device-bound mixtures store additional identical brews as charges and affect the creature that activates the device. The floating display reports order, capacity, modifier, container, water, fluid, heat, altar power, and output problems before showing a green ready check. Bottling produces three to eight filled carriers based on brew power. Insanity and Nightmare use the bounded hex runtime. Sleeping manifests one owner-bound dream spirit while the affected player sleeps. Tint Skin uses a synchronized colored outline and dust aura instead of rewriting a downloaded skin. Gas Immunity reads `warlockery:brew_gases`, and Absorb Magic reads `warlockery:magical_damage`. Other mods can opt into both private tags through data packs. Temporary water paths use vanilla Frosted Ice, temporary lava paths use saved restoration data, compatible plant items use Forge IPlantable, Combustion reports a normal Forge fuel value, and Endless Water returns its bottle at impact. Every registered built-in brew has a machine recipe and participates in the Warlockery and common potion tags.

Colored Brew Water and Erosion Brew are real source and flowing Forge fluids. Their functional buckets participate in `c:buckets`, while exact fluid matching uses `warlockery:colored_brew_water` and `warlockery:erosion_brews`. Redstone Soup supplies brief vanilla Regeneration, fills compatible chalices through a private tag, and remains an infusion base. Four Solidifying Brews convert Hollow Tears source blocks to stone, dirt, sand, or sandstone. The erosion variant removes the selected Hollow Tears columns with modern depth, block count, block entity, and unbreakable-block safety limits.

## Equipment integration

Icy Slippers carry Frost Walker II. Emberstep Slippers carry Fire Protection IV. Seeping Shoes carry Depth Strider III. These use Minecraft enchantments so they follow current movement, durability, and enchantment compatibility rules.

The complete ritual, doll, item, and ritual-like interaction catalogs are in `FEATURES.md`.

Circle Mages, Familiar Cats, Storm Simians, Forgewardens, Stonebrokers, Lost Souls, and Parasytic Lice each have survival ritual routes. Pale Steeds and Nightmares accept only their bound owner as controller and translate rider facing, strafe, and forward input into movement. Unbound Spirits spawn rarely in tagged eerie biomes and flee players. Contracted hobgoblins gather tagged loose resources and deposit them into tagged nearby chests or barrels while mob griefing is enabled.

## Advanced mutations

Use the Mutating Sprig on the central cobweb. The cobweb must sit directly over tagged water. Cardinal and diagonal pattern slots may be one to three blocks from the center. The action bar lists every absent block, stored ingredient, or creature and shows a green check when the mutation succeeds.

For Toads, place at least two slime-filled Critter Snares nearby. Put three Mutandis Extremis and one charged Attuned Stone into four diagonal Grasspers, then bring a cat or ocelot into range. Success empties the Grasspers and snares, consumes the host and cobweb, and creates one unbound Toad for every filled snare.

For Dreamroots, grow four mature Mandrakes on the cardinal rays. Put two Mutandis Extremis, one Focused Will, and one charged Attuned Stone into four diagonal Grasspers, then bring both a Creeper and a living Mandrake into range. Success consumes those ingredients and hosts and changes all four crops into Dreamroot plants.

A Dreamroot Bulb can be planted normally. A dropped bulb wakes after 60 ticks, creates one hostile Dreamroot per bulb in the stack, and prefers the player who dropped it. Dreamroot combat blasts can hurt nearby creatures but use the no-terrain-damage explosion mode.

## Goblinite

Goblinite has a sword, axe, pickaxe, shovel, hoe, helmet, chestplate, leggings, and boots. Its durability, mining speed, attack bonus, armor defense, toughness, knockback resistance, enchantability, fire resistance, and repair behavior match Netherite tier. Existing `delvealloy` registry IDs remain unchanged for saved worlds. Recipes and integrations should use the preferred `c:*/*/goblinite` tags.

Give a Goblinite Pickaxe to a Hobgoblin Miner to improve its mining speed and yield. It mines tagged ores once per second, has a 5 percent chance to uncover Goblinite Dust, and has a 50 percent chance to use the installed smelting recipe for one to three times its normal output. Data packs can extend the accepted tools, enhanced tools, mineable blocks, and auto-smelting ores through the documented private tags.

## Magical trees

Alder appears rarely in forests and swamps, Hawthorn in forests and meadows, and Rowan in taiga and groves. Their saplings grow the same configured trees used by world generation. Logs, planks, leaves, saplings, leaf decay, Fortune drops, Silk Touch, burning behavior, and oven essences follow modern Minecraft data rules.

## Utility items

The Soul of Torment drops from the Abyssal Regent and banishes a non-player target into a prepared arrival cell in the Abyss. Infernal Animus binds a tagged demon so it stops attacking its owner and follows the owner's current combat target. The Twisting Band weakens creatures in the wearer's gaze; players who stare at the wearer are turned aside and lose hunger and saturation. Carrying a Charm of Fanciful Thinking blocks the Darkness, Weakness, and Slowness side effects of Nightmare attacks. Compatible charms can opt into `warlockery:nightmare_guard_charms`. Hellhound Heads are rare Looting-aware trophies and wearable head armor. Woven Cruor is tagged magical cloth used by equipment recipes.
