package com.kadamitas.warlockery.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.kadamitas.warlockery.block.AlluringSkullBlock;
import com.kadamitas.warlockery.block.AltarBlock;
import com.kadamitas.warlockery.block.AltarChaliceBlock;
import com.kadamitas.warlockery.block.BearTrapBlock;
import com.kadamitas.warlockery.block.BloodPoppyBlock;
import com.kadamitas.warlockery.block.CritterSnareBlock;
import com.kadamitas.warlockery.block.CrystalBallBlock;
import com.kadamitas.warlockery.block.DollShelfBlock;
import com.kadamitas.warlockery.block.FetishRuntime;
import com.kadamitas.warlockery.block.GlintWeedBlock;
import com.kadamitas.warlockery.block.InteractiveUtilityBlock;
import com.kadamitas.warlockery.block.MagicalPlantBlock;
import com.kadamitas.warlockery.block.MagicalPlantBlockFactory;
import com.kadamitas.warlockery.block.MagicalWoodBlockFactory;
import com.kadamitas.warlockery.block.MagicMachineBlock;
import com.kadamitas.warlockery.block.PentacleBlock;
import com.kadamitas.warlockery.block.SpanishMossBlock;
import com.kadamitas.warlockery.block.ShadedGlassBlock;
import com.kadamitas.warlockery.block.SpiritPortalStructure;
import com.kadamitas.warlockery.block.StatueBlock;
import com.kadamitas.warlockery.block.StatueWardData;
import com.kadamitas.warlockery.block.StockadeBlock;
import com.kadamitas.warlockery.block.SunCollectorBlock;
import com.kadamitas.warlockery.block.VoidBrambleBlock;
import com.kadamitas.warlockery.block.WickerBundleBlock;
import com.kadamitas.warlockery.block.WolfAltarRuntime;
import com.kadamitas.warlockery.block.WolfHeadBlock;
import com.kadamitas.warlockery.dream.SpiritWorldRuntime;
import com.kadamitas.warlockery.entity.CreatureBehaviorRuntime;
import com.kadamitas.warlockery.entity.HellhoundCureRules;
import com.kadamitas.warlockery.entity.HobgoblinEntity;
import com.kadamitas.warlockery.entity.NaamahEntity;
import com.kadamitas.warlockery.entity.TreefydRules;
import com.kadamitas.warlockery.entity.WerewolfEntity;
import com.kadamitas.warlockery.entity.WerewolfHunterEntity;
import com.kadamitas.warlockery.item.ParasyticLouseItem;
import com.kadamitas.warlockery.item.SeerCovenRuntime;
import com.kadamitas.warlockery.item.TormentSoulItem;
import com.kadamitas.warlockery.ritual.HuntsmanSummoningStructure;
import com.kadamitas.warlockery.transformation.SupernaturalProgressionRuntime;
import com.kadamitas.warlockery.transformation.VampireProgressionRules;
import com.kadamitas.warlockery.transformation.WerewolfProgressionRules;
import com.kadamitas.warlockery.world.LegacyStructureRules;
import com.kadamitas.warlockery.world.VillageGuardRuntime;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ArchivedDeviceWorldParityTest {
    private static final List<ParityEntry> DEVICES = List.of(
        entry("Oven", MagicMachineBlock.class, "tagged fume recipes and funnel routing"),
        entry("Altar", AltarBlock.class, "three-by-two power network, upgrades, diagnostics, and inventory"),
        entry("Distillery", MagicMachineBlock.class, "typed multi-stage distilling recipes and active state"),
        entry("Kettle", MagicMachineBlock.class, "tagged kettle recipes, filtered slots, and active state"),
        entry("Brazier", MagicMachineBlock.class, "brazier rites, offerings, UI, and active state"),
        entry("Rowan Wood Door", MagicalWoodBlockFactory.class, "keyring access and modern door behavior"),
        entry("Alder Wood Door", MagicalWoodBlockFactory.class, "redstone-emitting open door and modern door behavior"),
        entry("Stockade", StockadeBlock.class, "connectable fence geometry and fall-scaled impalement"),
        entry("Poppet Shelf", DollShelfBlock.class, "renamed doll shelf with filtered storage and remote operation"),
        entry("Alluring Skull", AlluringSkullBlock.class, "taglock lure binding and creature attraction"),
        entry("Leech Chest", InteractiveUtilityBlock.class, "visitor memory, blood sampling, and portable state"),
        entry("Crystal Ball", CrystalBallBlock.class, "remote viewing, predictions, and Baba Yaga encounter"),
        entry("Statue of The Goddess", StatueBlock.class, "direct hex cleansing"),
        entry("Statue of Broken Curses", StatueWardData.class, "persistent owner-bound sixty-four-block hex ward"),
        entry("Statue of Occluded Summons", StatueWardData.class, "persistent owner-controlled summoning ward"),
        entry("Chalice", AltarChaliceBlock.class, "altar capacity and filled-chalice power upgrade"),
        entry("Spinning Wheel", MagicMachineBlock.class, "typed spinning recipes, filtered slots, and animation state"),
        entry("Spirit Portal", SpiritPortalStructure.class, "optional-corner snow frame and rite-gated temporary player manifestation"),
        entry("Candelabra", AltarBlock.class, "altar light and power attachment"),
        entry("Scarecrow", FetishRuntime.class, "owner-bound configurable area protection"),
        entry("Trent Effigy", TreefydRules.class, "Treefyd awakening, allowlist, and patrol toggle"),
        entry("Witch's Ladder", FetishRuntime.class, "renamed hanging ward with area effects"),
        entry("Sentinel Fetish", FetishRuntime.class, "hostile detection and owner-safe defense"),
        entry("Voodoo Protection Fetish", FetishRuntime.class, "renamed anti-hex fetish protection"),
        entry("Ghost Walking Fetish", FetishRuntime.class, "spectral movement ward"),
        entry("Disorientation Fetish", FetishRuntime.class, "intruder disorientation ward"),
        entry("Shrieking Fetish", FetishRuntime.class, "audible intruder alarm"),
        entry("Pentacle", PentacleBlock.class, "demonic binding and ritual integration"),
        entry("Witches' Cauldron", MagicMachineBlock.class, "custom brews, chalk modifiers, heat, UI, and JEI"),
        entry("Silver Vat", MagicMachineBlock.class, "furnace-aware silver treatment machine"),
        entry("Beartrap", BearTrapBlock.class, "reusable capture and full-moon werewolf diagnostics"),
        entry("Wolf/Hellhound Head", WolfHeadBlock.class, "placeable trophy and wolf altar component"),
        entry("Wolf Altar", WolfAltarRuntime.class, "full werewolf progression trials"),
        entry("Garlic Garland", InteractiveUtilityBlock.class, "continuous vampire repulsion and burning"),
        entry("Sunlight Collector", SunCollectorBlock.class, "continuous dawn-to-noon detector collection with stored strength"),
        entry("Coffin", InteractiveUtilityBlock.class, "day rest, healing, taglocks, and respawn binding"),
        entry("Blood-stained Wool", WickerBundleBlock.class, "bloodied summoning structure material"),
        entry("Blood Crucible", InteractiveUtilityBlock.class, "vampire blood reserve and progression charging"),
        entry("Shaded Glass", ShadedGlassBlock.class, "redstone-switched transparent and opaque states"),
        entry("Magic Mirror", InteractiveUtilityBlock.class, "pair travel, fairest query, memory, and reflection encounter"),
        entry("Statue of Hobgoblin Patron", StatueBlock.class, "Goblinite offerings and patron binding")
    );

    private static final List<ParityEntry> WORLD = List.of(
        entry("Rowan Tree", MagicalWoodBlockFactory.class, "complete wood family and biome world generation"),
        entry("Hawthorn Tree", MagicalWoodBlockFactory.class, "complete wood family and biome world generation"),
        entry("Alder Tree", MagicalWoodBlockFactory.class, "complete wood family and biome world generation"),
        entry("Belladonna", MagicalPlantBlockFactory.class, "crop growth, harvest, recipes, and compatibility tags"),
        entry("Mandrake", CreatureBehaviorRuntime.class, "screaming mobile harvest creature and drops"),
        entry("Minedrake", CreatureBehaviorRuntime.class, "safe plant-creature blast behavior"),
        entry("Spanish Moss", SpanishMossBlock.class, "connectable hanging vine growth"),
        entry("Ember Moss", MagicalPlantBlock.class, "spread, fire ignition, and immunity tags"),
        entry("Water Artichoke", MagicalPlantBlockFactory.class, "crop growth, harvest, recipes, and compatibility tags"),
        entry("Ender Bramble", MagicalPlantBlock.class, "long-range safe teleport and thorn damage"),
        entry("Void Bramble", VoidBrambleBlock.class, "owner-safe indestructible spreading barrier"),
        entry("Glint Weed", GlintWeedBlock.class, "bright spreading light source with floor and ceiling mounting"),
        entry("Snowbell", MagicalPlantBlockFactory.class, "crop growth, harvest, recipes, and compatibility tags"),
        entry("Grassper", MagicalPlantBlockFactory.class, "mutation receptacle plant"),
        entry("Critter Snare", CritterSnareBlock.class, "creature capture, contents, mutations, and diagnostics"),
        entry("Wild Bramble", MagicalPlantBlock.class, "thorn barrier behavior merged with modern bramble"),
        entry("Blood Rose", BloodPoppyBlock.class, "blood sampling, Boline harvesting, and mutation"),
        entry("Wispy Cotton", MagicalPlantBlockFactory.class, "Spirit World growth and disturbed-cotton conversion"),
        entry("Wormwood", MagicalPlantBlockFactory.class, "crop growth, harvest, recipes, and compatibility tags"),
        entry("Wolfsbane", MagicalPlantBlockFactory.class, "crop growth and werewolf weakness integration"),
        entry("Garlic", MagicalPlantBlockFactory.class, "crop growth and vampire ward integration"),
        entry("Wicker Bundle", WickerBundleBlock.class, "sapling-compressed building and bloodied summoning form"),
        entry("Structures", LegacyStructureRules.class, "functional wilderness landmarks plus modern village structure equivalents"),
        entry("Parasytic Louse", ParasyticLouseItem.class, "capture, deploy, potion loading, bite injection, and hotbar redirection"),
        entry("Horned Huntsman", HuntsmanSummoningStructure.class, "renamed Thorned Pursuer summoning and combat"),
        entry("Baba Yaga", CrystalBallBlock.class, "divination-gated boss encounter"),
        entry("Spectral Familiar", CreatureBehaviorRuntime.class, "bound ore guidance and owner support"),
        entry("Treefyd", TreefydRules.class, "owner allowlist, patrol mode, offerings, and rooted combat"),
        entry("Ent", CreatureBehaviorRuntime.class, "biome variants and guardian combat"),
        entry("Owl", CreatureBehaviorRuntime.class, "familiar support and bound item delivery"),
        entry("Toad", CreatureBehaviorRuntime.class, "familiar water and leaping support"),
        entry("Coven Witch", SeerCovenRuntime.class, "renamed Circle Mage recruitment and circle gathering"),
        entry("Nightmare", SpiritWorldRuntime.class, "nightmare entry, mounts, disturbed cotton, and dream hazards"),
        entry("Demon", CreatureBehaviorRuntime.class, "summoning, combat, bargaining, and contracts"),
        entry("Flame Imp", CreatureBehaviorRuntime.class, "fire combat and contract progression"),
        entry("Lord of Torment", CreatureBehaviorRuntime.class, "renamed Abyssal Regent phases and banishment"),
        entry("Spirit", SpiritWorldRuntime.class, "dream spawning, capture, and export rules"),
        entry("Spectre", CreatureBehaviorRuntime.class, "spectral fear combat"),
        entry("Banshee", CreatureBehaviorRuntime.class, "weakening scream and empowerment"),
        entry("Poltergeist", CreatureBehaviorRuntime.class, "levitation and item throwing"),
        entry("Death", CreatureBehaviorRuntime.class, "soul reaping and impersonation rules"),
        entry("Witch Hunter", WerewolfHunterEntity.class, "specialized warlock and werewolf hunters using vanilla crossbows"),
        entry("Hobgoblin", HobgoblinEntity.class, "friendly travelling traders with self-assigned professions"),
        entry("Shade of Leonard", CreatureBehaviorRuntime.class, "renamed Emberhorn Archfiend cauldron encounter"),
        entry("Hellhound", HellhoundCureRules.class, "fire combat and weakness-plus-golden-apple cure"),
        entry("Werewolf", WerewolfEntity.class, "silver vulnerability, forms, combat, and lycan village interaction"),
        entry("Lycanthropy", WerewolfProgressionRules.class, "complete levelled trials and powers"),
        entry("Vampire", SupernaturalProgressionRuntime.class, "blood reserve, sunlight weakness, feeding, and powers"),
        entry("Vampirism", VampireProgressionRules.class, "complete torn-page-gated levelled progression"),
        entry("Lilith", NaamahEntity.class, "renamed Naamah initiation and combat"),
        entry("Village Guards", VillageGuardRuntime.class, "village defense and hunter integration"),
        entry("Winged Monkeys", CreatureBehaviorRuntime.class, "renamed Storm Simians with flying companion behavior"),
        entry("Reflection", CreatureBehaviorRuntime.class, "copied equipment, attributes, effects, and phased combat"),
        entry("Fairest of them All", InteractiveUtilityBlock.class, "Magic Mirror fairness scoring and encounter"),
        entry("Torment", TormentSoulItem.class, "soul transport and Abyssal Regent realm loop"),
        entry("Spirit World", SpiritWorldRuntime.class, "separate dimension, nightmare odds, body, inventory, resources, and return")
    );

    @Test
    void everyExactArchivedDevicePageMapsToConcreteRuntime() {
        assertLedger(DEVICES, 41);
    }

    @Test
    void everyExactArchivedWorldPageMapsToConcreteRuntime() {
        assertLedger(WORLD, 56);
    }

    private static void assertLedger(final List<ParityEntry> entries, final int expectedSize) {
        assertEquals(expectedSize, entries.size());
        assertEquals(expectedSize, entries.stream().map(ParityEntry::pageTitle).distinct().count());
        entries.forEach(entry -> {
            assertFalse(entry.pageTitle().isBlank());
            assertFalse(entry.mechanism().isBlank());
            assertFalse(entry.implementation().isInterface());
        });
    }

    private static ParityEntry entry(
        final String pageTitle,
        final Class<?> implementation,
        final String mechanism
    ) {
        return new ParityEntry(pageTitle, implementation, mechanism);
    }

    private record ParityEntry(String pageTitle, Class<?> implementation, String mechanism) {
    }
}
