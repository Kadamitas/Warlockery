package com.kadamitas.warlockery.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.block.DreamWeaverRuntime;
import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.brew.custom.CustomBrewComponentDefinition;
import com.kadamitas.warlockery.brew.custom.CustomBrewDelivery;
import com.kadamitas.warlockery.crafting.BrazierEffectRuntime;
import com.kadamitas.warlockery.item.ManualProfile;
import com.kadamitas.warlockery.magic.MagicPathRuntime;
import com.kadamitas.warlockery.ritual.RitualManager;
import com.kadamitas.warlockery.ritual.hex.HexRuntime;
import com.kadamitas.warlockery.transformation.SupernaturalProgressionRuntime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class FtbBrewsRitualsProgressionGuidesParityLedgerTest {
    private static final List<PageCoverage> BREWS = parse("""
A|+1 Capacity
A|+2 Capacity
A|+4 Capacity
A|Air hike
A|Attract arrows
A|Bat burst
A|Brew gas immunity
A|Brew of Bats
A|Brew of Bodega
A|Brew of Combustion
A|Brew of Cursed Leaping
A|Brew of Endless Water
A|Brew of Erosion
A|Brew of Flowing Spirit
A|Brew of Frog's Tongue
A|Brew of Frost
A|Brew of Hollow Tears
A|Brew of Infection
A|Brew of Ink
A|Brew of Love
A|Brew of Raising
A|Brew of Revealing
A|Brew of Sleeping
A|Brew of Sprouting
A|Brew of Substitution
A|Brew of Thorns
A|Brew of Vines
A|Brew of Wasting
A|Brew of Webs
A|Brew of the Depths
A|Brew of the Grotesque
A|Combustion
A|Damage boost
A|Dissipate Brew Gas
A|Ender Inhibition
A|Erosion
A|Extinguish Fires
A|Fast movement
A|Fell tree
A|Fertilize
A|Floating
A|Flying Ointment
A|Freeze
A|Gas
A|Ghost of the Light
A|Grow flowers
A|Grow lily
A|Happenstance Oil
A|Harvest
A|Ice World
A|Infernal Animus
A|Infused Brew Base
A|Infused Brew of Soaring
A|Infused Brew of the Grave
A|Instant
A|Invisible
A|Jump
A|Level Land
A|Liquid (Witchery)
A|Love
A|Moonshine
A|Mystic Unguent
A|Night vision
A|Part lava
A|Part water
A|Planting
A|Poison (Witchery)
A|Poison weapon
A|Prune Leaves
A|Pull
A|Pulverize rock
A|Redstone Soup
A|Reflect arrows
A|Regeneration (Witchery)
A|Repel attacker
A|Resist fire
A|Slow fall
A|Slow movement
A|Snow Burst & Trail
A|Solidifying Brew (Dirt)
A|Solidifying Brew (Erosion)
A|Solidifying Brew (Sand)
A|Solidifying Brew (Sandstone)
A|Solidifying Brew (Stone)
A|Soul of Anguish Demon
A|Soul of Fear Demon
A|Soul of Hunger Demon
A|Soul of Torment Demon
A|Soul of the World
A|Spirit of Otherwhere
A|Swim Speed
A|Till land
A|Tint skin
A|Trigger (Witchery)
A|Universal Antidote
A|Water breathing
A|Weakness
A|Wolfsbane (effect)
R|Custom Brews
R|Webs
R|Vines & Flamable
R|Cactus & Thorned
R|Sprouting
R|Knockback (brew effect)
R|Undeadbane
R|Insectbane
R|Grow rowan
R|Grow alder
R|Grow hawthorn
R|Grow dark oak
R|Grow oak
R|Grow spruce
R|Grow birch
R|Grow jungle
R|Grow acacia
R|Remove Buffs
R|Remove debuffs
R|Endless Water
R|Flames
R|Fear
R|Blindness
R|Paralysis
R|Disease
R|Brew bottling
R|Insanity (Witchery)
R|Sinking
R|Overheating
R|Nightmare (Effect)
R|Frog's Leg
R|Absorbsion
R|Health boost
R|Wasting
R|Fullness
R|Revealing
R|Volatility
R|Stout belly
R|Blight
R|Transpose
R|Transpose ore
R|Raise dead
R|Raise land
R|Grue's Prey
R|Absorb magic
R|Wither (Witchery)
R|Harm Werewolves
R|Weaken Vampires
R|Animal attraction
R|Animal repulsion
R|Inferno
R|Blast
R|Poison Toad
R|Ice shell
R|Reflect damage
R|Demonbane
R|Undead's Curse
R|Ill Fitting
R|Reincarnate
R|Duration Boost
R|Resizing
R|Steal buffs
R|Fortune (Witchery)
R|Drain magic
R|Keep inventory
R|Shifting season
R|Spread debuffs
R|Keep effects
R|Summon Leonard
R|Level II
R|Level III
R|Level IV
R|2x Duration
R|4x Duration
R|6x Duration
R|No particles
R|Invert next effect
R|Skip block effects
R|Skip entity effects
R|Faster quaffing 1
R|Fasting quaffing 2
R|Faster quaffing 3
R|White color
R|Orange color
R|Magenta color
R|Light blue color
R|Yellow color
R|Lime color
R|Pink color
R|Gray color
R|Light gray color
R|Cyan color
R|Purple color
R|Blue color
R|Brown color
R|Green color
R|Red color
R|Black color
R|Level II Extent
R|Level III Extent
R|Level IV Extent
R|Level II Lingering
R|Level III Lingering
R|Level IV Lingering
        """, FtbBrewsRitualsProgressionGuidesParityLedgerTest::brewCoverage);
    private static final List<PageCoverage> RITUALS = parse("""
A|Anguish of the Dead
A|Creatures of the Night
A|Curse of Blight
A|Curse of Blindness (Witchery)
A|Curse of Corrupt Poppet
A|Curse of Hell on Earth
A|Curse of Insanity
A|Curse of Misfortune (Witchery)
A|Curse of Overheating
A|Curse of Raining Toads
A|Curse of Sinking (Witchery)
A|Curse of Waking Nightmare
A|Deathly Veil
A|Demonic Contract
A|Disorientation Fetish
A|Drain Growth
A|Evaporation
A|Fiery Tolerance
A|Fiery Touch
A|Fortification of the Corpse
A|Ghost Walking Fetish
A|Graveyard Mist
A|Imp Magic
A|Infernal Infusion
A|Infusion of Light
A|Infusion of Otherwhere
A|Infusion of the Overworld
A|Living Flame
A|Melting Touch
A|Rite of Banishing
A|Rite of Beastial Call
A|Rite of Binding
A|Rite of Broiling
A|Rite of Broken Earth
A|Rite of Charging
A|Rite of Earth's Wrath
A|Rite of Fertility
A|Rite of Glyphic Transformation
A|Rite of Icy Expansion
A|Rite of Imprisonment
A|Rite of Infusion
A|Rite of Manifestation
A|Rite of Moving Earth
A|Rite of Nature's Power
A|Rite of Necromancy
A|Rite of Prior Incarnation
A|Rite of Protection
A|Rite of Remove Curse
A|Rite of Sanctity
A|Rite of Shifting Seasons
A|Rite of Sky's Wrath
A|Rite of Summoning
A|Rite of Total Eclipse
A|Rite of Transposition
A|Rite of the Forest
A|Sentinel Fetish
A|Shrieking Fetish
A|Summon Banshee
A|Summon Poltergeist
A|Summon Spectre
A|Torment (Item)
A|Vampirism (Witchery)
A|Voodoo Protection Fetish
R|Bind Waystone
R|Blooded Waystone
R|Transpose
R|Find Structure
R|Curse of the Wolf
R|Cauldron Rituals
R|Waystones
R|Covens & Strength
R|Dispersal
R|Risks & Power
R|Lycanthropy
R|Awaken the Wolf
        """, FtbBrewsRitualsProgressionGuidesParityLedgerTest::ritualCoverage);
    private static final List<PageCoverage> OTHER = parse("""
A|Bat Swarm
A|Batswarm Form
A|Blood (Witchery)
A|Blood Power
A|Call Storm
A|Chosen Wolf Form Transformation
A|Chosen Wolfman Form Transformation
A|Create Vampires
A|Drink Blood
A|Feast
A|Forced Wolf Form Transformation
A|Instant Dig
A|Knockback (magical ability)
A|Level 10 Lycanthropy
A|Level 10 Vampirism
A|Level 1 Lycanthropy
A|Level 1 Vampirism
A|Level 2 Lycanthropy
A|Level 2 Vampirism
A|Level 3 Lycanthropy
A|Level 3 Vampirism
A|Level 4 Lycanthropy
A|Level 4 Vampirism
A|Level 5 Lycanthropy
A|Level 5 Vampirism
A|Level 6 Lycanthropy
A|Level 6 Vampirism
A|Level 7 Lycanthropy
A|Level 7 Vampirism
A|Level 8 Lycanthropy
A|Level 8 Vampirism
A|Level 9 Lycanthropy
A|Level 9 Vampirism
A|Mesmerize
A|Night Vision (Witchery)
A|Paralysed
A|Resist Sun
A|Sense Blood
A|Smash Stone
A|Speed (Witchery)
A|Spread Curse
A|Sprinting Damage
A|Stun Howl
A|Summon Howl
A|Teleport
A|Transfix
A|Vampirism (Witchery)
A|Wolf Form
A|Wolfman Armor Rending
A|Wolfman Form
A|Zombie Respect
R|Lycanthropy
        """, FtbBrewsRitualsProgressionGuidesParityLedgerTest::progressionCoverage);
    private static final List<PageCoverage> GUIDES = parse("""
A|Changes to Villages (Witchery)
A|Circle Magic and Infusions (Witchery)
A|Conjurations and Fetishes (Witchery)
A|Dream Weavers
A|Getting Started (Witchery)
A|Mutations (Witchery)
A|Symbol Magic (Witchery)
A|Vampirism (Witchery)
A|Voodoo (Witchery)
A|Witchery/Changelog
R|Lycanthropy
R|Becoming Village Mayor (Witchery)
        """, FtbBrewsRitualsProgressionGuidesParityLedgerTest::guideCoverage);

    @Test
    void everyNavboxEntryHasAnExecutableModernEquivalent() {
        assertSection(BREWS, 202, 98, 104);
        assertSection(RITUALS, 75, 63, 12);
        assertSection(OTHER, 52, 51, 1);
        assertSection(GUIDES, 12, 10, 2);
        assertEquals(341, Stream.of(BREWS, RITUALS, OTHER, GUIDES).mapToInt(List::size).sum());
        assertEquals(336, Stream.of(BREWS, RITUALS, OTHER, GUIDES)
            .flatMap(List::stream)
            .map(PageCoverage::title)
            .distinct()
            .count());
    }

    @Test
    void newlyExposedDispersalAndConjurationGapsHaveConcreteRuntimeCoverage() {
        assertEquals(
            Set.of("drinkable", "throwable", "gas", "liquid", "trigger"),
            Arrays.stream(CustomBrewDelivery.values()).map(CustomBrewDelivery::id).collect(Collectors.toSet())
        );
        assertTrue(Arrays.stream(BrazierEffectRuntime.Effect.values())
            .anyMatch(effect -> effect == BrazierEffectRuntime.Effect.SUMMON_POLTERGEIST));
    }

    private static void assertSection(
        final List<PageCoverage> entries,
        final int expected,
        final int articles,
        final int redlinks
    ) {
        assertEquals(expected, entries.size());
        assertEquals(expected, entries.stream().map(PageCoverage::title).distinct().count());
        assertEquals(articles, entries.stream().filter(PageCoverage::sourceArticleExists).count());
        assertEquals(redlinks, entries.stream().filter(entry -> !entry.sourceArticleExists()).count());
        entries.forEach(entry -> {
            assertFalse(entry.title().isBlank());
            assertNotNull(entry.anchor());
            assertFalse(entry.mechanism().isBlank());
        });
    }

    private static List<PageCoverage> parse(
        final String entries,
        final Function<String, CoverageAnchor> classifier
    ) {
        return entries.lines()
            .map(String::strip)
            .filter(line -> !line.isEmpty())
            .map(line -> line.split("\\|", 2))
            .map(parts -> {
                final CoverageAnchor coverage = classifier.apply(parts[1]);
                return new PageCoverage(parts[1], parts[0].equals("A"), coverage.anchor(), coverage.mechanism());
            })
            .toList();
    }

    private static CoverageAnchor brewCoverage(final String title) {
        if (title.endsWith("color")) {
            return anchor(CustomBrewComponentDefinition.class, "data-driven color component");
        }
        if (title.contains("Capacity") || title.contains("Duration") || title.contains("Extent")
            || title.contains("Lingering") || title.contains("quaffing") || title.startsWith("Level ")) {
            return anchor(CustomBrewComponentDefinition.class, "bounded formula modifier");
        }
        if (Set.of("Gas", "Liquid (Witchery)", "Trigger (Witchery)", "Instant").contains(title)) {
            return anchor(CustomBrewDelivery.class, "custom brew delivery");
        }
        if (title.startsWith("Brew of ") || title.startsWith("Solidifying Brew")
            || title.startsWith("Soul of ") || title.startsWith("Infused Brew")) {
            return anchor(BrewKind.class, "fixed brew or infused brew equivalent");
        }
        return anchor(CustomBrewComponentDefinition.class, "data-driven brew effect or modifier");
    }

    private static CoverageAnchor ritualCoverage(final String title) {
        if (title.startsWith("Curse of ")) {
            return anchor(HexRuntime.class, "modern hex runtime");
        }
        if (title.startsWith("Rite of ") || title.contains("Waystone") || title.equals("Transpose")
            || title.equals("Cauldron Rituals") || title.equals("Awaken the Wolf")) {
            return anchor(RitualManager.class, "circle or cauldron ritual runtime");
        }
        if (title.contains("Infusion") || title.equals("Imp Magic") || title.equals("Demonic Contract")
            || title.equals("Living Flame") || title.equals("Melting Touch") || title.equals("Fiery Touch")
            || title.equals("Fiery Tolerance") || title.equals("Evaporation")) {
            return anchor(MagicPathRuntime.class, "infusion or infernal contract runtime");
        }
        return anchor(BrazierEffectRuntime.class, "brazier conjuration or bound ward runtime");
    }

    private static CoverageAnchor progressionCoverage(final String title) {
        return anchor(SupernaturalProgressionRuntime.class, "vampire or werewolf progression runtime");
    }

    private static CoverageAnchor guideCoverage(final String title) {
        if (title.equals("Dream Weavers")) {
            return anchor(DreamWeaverRuntime.class, "interactive dream-weaver manual and runtime");
        }
        return anchor(ManualProfile.class, "searchable in-game manual chapter collection");
    }

    private static CoverageAnchor anchor(final Class<?> type, final String mechanism) {
        return new CoverageAnchor(type, mechanism);
    }

    private record PageCoverage(
        String title,
        boolean sourceArticleExists,
        Class<?> anchor,
        String mechanism
    ) {
    }

    private record CoverageAnchor(Class<?> anchor, String mechanism) {
    }
}
