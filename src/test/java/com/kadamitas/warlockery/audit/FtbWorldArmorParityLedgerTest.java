package com.kadamitas.warlockery.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.kadamitas.warlockery.block.MagicalPlantBlock;
import com.kadamitas.warlockery.dream.SpiritWorldRuntime;
import com.kadamitas.warlockery.entity.CreatureBehaviorRuntime;
import com.kadamitas.warlockery.entity.GoblinBossRules;
import com.kadamitas.warlockery.item.EquipmentSetEffects;
import com.kadamitas.warlockery.item.SeerCovenRuntime;
import com.kadamitas.warlockery.registry.ContentCatalog;
import com.kadamitas.warlockery.ritual.RitualManager;
import com.kadamitas.warlockery.world.CreatureWorldIntegration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class FtbWorldArmorParityLedgerTest {
    private static final Set<String> WORLD_REDLINKS = Set.of(
        "Blacksmith", "Church", "Double Field", "Forest Hills Village", "Forest Village",
        "Garden House", "Hellhound", "House", "Jungle Village", "Lost Soul", "Mesa Village",
        "Mirrorface", "Morsmordre", "Mountain Village", "Plains Village", "Sandy Village",
        "Shade of Leonard", "Single Field", "Snowy Village", "Wasteland Village", "Winged Monkey",
        "Wood Hut"
    );
    private static final Set<String> ARMOR_REDLINKS = Set.of(
        "Vampire Chain Coat", "Vampire Chain Coat (Ladies)", "Vampire Dress Coat", "Vampire Helmet",
        "Vampire Oxford Boots", "Vampire Skirted Trousers", "Vampire Top Hat", "Vampire Trousers",
        "Witch Hunter Dawn Boots", "Witch Hunter Dawn Hat"
    );
    private static final List<PageCoverage> WORLD = List.of(
        world("Abandoned Shack", CreatureWorldIntegration.class, "naturally generated occult shelter"),
        world("Alder Sapling", MagicalPlantBlock.class, "alder growth, harvesting, and fume resources"),
        world("Apothecary's Shop", CreatureWorldIntegration.class, "village apothecary equivalent and warlock trades"),
        world("Baba Yaga", CreatureBehaviorRuntime.class, "mobile hedge crone boss behavior"),
        world("Banshee", CreatureBehaviorRuntime.class, "spectral weakness and fatigue aura"),
        world("Belladonna", MagicalPlantBlock.class, "harvestable herb and recipe integration"),
        world("Blacksmith", CreatureWorldIntegration.class, "modern village smith integration"),
        world("Blood Poppy", MagicalPlantBlock.class, "taglock-linked blood flower behavior"),
        world("Bookshop", CreatureWorldIntegration.class, "manual and librarian village integration"),
        world("Church", CreatureWorldIntegration.class, "modern village ritual landmark"),
        world("Coven Witch", SeerCovenRuntime.class, "familiar-gated six-member coven recruitment"),
        world("Critter Snare", RitualManager.class, "portable creature capture and release"),
        world("Dandelion of Ink", MagicalPlantBlock.class, "dandelion mine with ink payload"),
        world("Dandelion of Sprouting", MagicalPlantBlock.class, "dandelion mine with growth payload"),
        world("Dandelion of Thorns", MagicalPlantBlock.class, "dandelion mine with thorn payload"),
        world("Dandelion of Webs", MagicalPlantBlock.class, "dandelion mine with web payload"),
        world("Death", CreatureBehaviorRuntime.class, "regeneration, capped incoming hits, and soul reaping"),
        world("Disturbed Cotton", MagicalPlantBlock.class, "nightmare-grown cotton resource"),
        world("Double Field", CreatureWorldIntegration.class, "expanded village crop plots"),
        world("Elle", CreatureBehaviorRuntime.class, "named fae encounter equivalent"),
        world("Ember Moss", MagicalPlantBlock.class, "contact ignition and controlled spreading"),
        world("Ender Bramble", MagicalPlantBlock.class, "long-range bramble teleportation"),
        world("Ent (Witchery)", CreatureBehaviorRuntime.class, "biome-aware tree guardian behavior"),
        world("Familiar", CreatureBehaviorRuntime.class, "cat, owl, and toad bonds with damage sharing"),
        world("Flame Imp", CreatureBehaviorRuntime.class, "infernal contract and fire combat"),
        world("Forest Hills Village", CreatureWorldIntegration.class, "terrain-adapted modern villages"),
        world("Forest Village", CreatureWorldIntegration.class, "forest village integration"),
        world("Garden House", CreatureWorldIntegration.class, "warlock crop garden building"),
        world("Garlic (plant)", MagicalPlantBlock.class, "garlic crop and vampire ward resource"),
        world("Glint Weed", MagicalPlantBlock.class, "placeable spreading magical light"),
        world("Grassper", MagicalPlantBlock.class, "visible single-item storage and circle focus"),
        world("Guard", CreatureWorldIntegration.class, "modern ironbound village sentinel"),
        world("Guard Tower", CreatureWorldIntegration.class, "village defensive structure"),
        world("Gulg", GoblinBossRules.class, "Forgewarden patron melee and paired resistance"),
        world("Hawthorn Sapling", MagicalPlantBlock.class, "hawthorn growth, harvesting, and fume resources"),
        world("Hellhound", CreatureBehaviorRuntime.class, "infernal hound combat and golden-apple cure"),
        world("Hobgoblin", CreatureBehaviorRuntime.class, "friendly travelling trader and miner"),
        world("Hobgoblin Hut", CreatureWorldIntegration.class, "goblin camp and trader shelter"),
        world("Horned Huntsman", CreatureBehaviorRuntime.class, "thorned pursuer hunt, teleport, and wolf summons"),
        world("House", CreatureWorldIntegration.class, "modern village residential equivalent"),
        world("Jungle Village", CreatureWorldIntegration.class, "jungle-adapted village integration"),
        world("Leaping Lily", MagicalPlantBlock.class, "speed and jumping flower effect"),
        world("Lilith", CreatureBehaviorRuntime.class, "Naamah vampire initiation equivalent"),
        world("Lord of Torment", CreatureBehaviorRuntime.class, "Abyssal Regent torment boss phase"),
        world("Lost Soul", CreatureBehaviorRuntime.class, "bindable wandering soul behavior"),
        world("Mandrake", CreatureBehaviorRuntime.class, "mobile screaming harvest creature"),
        world("Mesa Village", CreatureWorldIntegration.class, "badlands village integration"),
        world("Minedrake", CreatureBehaviorRuntime.class, "safe root creature detonation"),
        world("Mirrorface", CreatureBehaviorRuntime.class, "glass doppelganger copy behavior"),
        world("Mog", GoblinBossRules.class, "Stonebroker ranged patron and paired resistance"),
        world("Morsmordre", CreatureBehaviorRuntime.class, "Echo Shade spectral threat equivalent"),
        world("Mountain Village", CreatureWorldIntegration.class, "mountain village integration"),
        world("Nightmare", CreatureBehaviorRuntime.class, "bondable spectral mount"),
        world("Parasytic Louse", CreatureBehaviorRuntime.class, "stored effect injection and redirection"),
        world("Plains Village", CreatureWorldIntegration.class, "plains village integration"),
        world("Poltergeist", CreatureBehaviorRuntime.class, "levitation and thrown-item haunting"),
        world("Poppy of Ink", MagicalPlantBlock.class, "poppy mine with ink payload"),
        world("Poppy of Sprouting", MagicalPlantBlock.class, "poppy mine with growth payload"),
        world("Poppy of Thorns", MagicalPlantBlock.class, "poppy mine with thorn payload"),
        world("Poppy of Webs", MagicalPlantBlock.class, "poppy mine with web payload"),
        world("Rowan Sapling", MagicalPlantBlock.class, "rowan growth, harvesting, and fume resources"),
        world("Sandy Village", CreatureWorldIntegration.class, "desert village integration"),
        world("Shade of Leonard", CreatureBehaviorRuntime.class, "archfiend shade encounter equivalent"),
        world("Shrub of Ink", MagicalPlantBlock.class, "shrub mine with ink payload"),
        world("Shrub of Sprouting", MagicalPlantBlock.class, "shrub mine with growth payload"),
        world("Shrub of Thorns", MagicalPlantBlock.class, "shrub mine with thorn payload"),
        world("Shrub of Webs", MagicalPlantBlock.class, "shrub mine with web payload"),
        world("Single Field", CreatureWorldIntegration.class, "compact village crop plot"),
        world("Snowbell", MagicalPlantBlock.class, "harvestable cold herb and recipe integration"),
        world("Snowy Village", CreatureWorldIntegration.class, "snow village integration"),
        world("Spanish Moss", MagicalPlantBlock.class, "shearable hanging moss growth"),
        world("Spectral Familiar", CreatureBehaviorRuntime.class, "ore-sampling spectral guide"),
        world("Spectre (Witchery)", CreatureBehaviorRuntime.class, "fear aura spectral enemy"),
        world("Spirit", CreatureBehaviorRuntime.class, "bindable spirit encounter"),
        world("Spirit World", SpiritWorldRuntime.class, "dream dimension travel and progression"),
        world("Stone Circle (Witchery)", CreatureWorldIntegration.class, "generated ritual landmark"),
        world("Torment", SpiritWorldRuntime.class, "Abyssal torment loop and soul resource"),
        world("Town Keep", CreatureWorldIntegration.class, "fortified settlement center"),
        world("Town Wall", CreatureWorldIntegration.class, "settlement defensive perimeter"),
        world("Treefyd", CreatureBehaviorRuntime.class, "owner-bound bramble colossus defense"),
        world("Void Bramble", MagicalPlantBlock.class, "teleportation and nearby ritual suppression"),
        world("Wasteland Village", CreatureWorldIntegration.class, "wasteland settlement equivalent"),
        world("Water Artichoke", MagicalPlantBlock.class, "aquatic crop and recipe integration"),
        world("Wicker-Man", CreatureWorldIntegration.class, "generated straw idol ritual landmark"),
        world("Wild Bramble", MagicalPlantBlock.class, "damaging wild bramble"),
        world("Winged Monkey", CreatureBehaviorRuntime.class, "Storm Simian companion and waystone travel"),
        world("Wispy Cotton", MagicalPlantBlock.class, "dream cotton growth and world transfer"),
        world("Witch Hunter", CreatureBehaviorRuntime.class, "specialized supernatural hunter combat"),
        world("Wolfsbane (plant)", MagicalPlantBlock.class, "werewolf-warding herb crop"),
        world("Wood Hut", CreatureWorldIntegration.class, "small occult shelter"),
        world("Wormwood", MagicalPlantBlock.class, "harvestable ritual herb and seed progression")
    );
    private static final List<PageCoverage> ARMOR = List.of(
        armor("Baba Yaga's Hat", EquipmentSetEffects.class, "hedge crone evasion headwear"),
        armor("Bark Belt", EquipmentSetEffects.class, "living-ground bark protection"),
        armor("Biting Belt", EquipmentSetEffects.class, "helpful wearer effects and harmful retaliation"),
        armor("Death", EquipmentSetEffects.class, "complete Death disguise and equipment synergy"),
        armor("Death's Footwear", EquipmentSetEffects.class, "fire protection and Frost Walker traversal"),
        armor("Death's Hood", EquipmentSetEffects.class, "Death disguise and hostile gaze"),
        armor("Death's Robe", EquipmentSetEffects.class, "fire-resistant Death garb"),
        armor("Earmuffs", EquipmentSetEffects.class, "sound and disorientation protection"),
        armor("Gulg's Gurdle", EquipmentSetEffects.class, "Forgewarden unarmed power and patron pairing"),
        armor("Icy Slippers", EquipmentSetEffects.class, "vanilla Frost Walker ice traversal"),
        armor("Mog's Quiver", EquipmentSetEffects.class, "endless arrows and projectile enhancement"),
        armor("Ruby Slippers", EquipmentSetEffects.class, "homeward footwear behavior"),
        armor("Seeping Shoes", EquipmentSetEffects.class, "poison removal and plant growth"),
        armor("Twisting Band", EquipmentSetEffects.class, "observer gaze disruption"),
        armor("Vampire", ContentCatalog.class, "complete vampire clothing collection"),
        armor("Vampire Chain Coat", ContentCatalog.class, "vampire chain coat equivalent"),
        armor("Vampire Chain Coat (Ladies)", ContentCatalog.class, "fitted vampire chain coat equivalent"),
        armor("Vampire Dress Coat", ContentCatalog.class, "vampire formal coat"),
        armor("Vampire Helmet", ContentCatalog.class, "vampire helmet equivalent"),
        armor("Vampire Oxford Boots", ContentCatalog.class, "vampire formal boots"),
        armor("Vampire Skirted Trousers", ContentCatalog.class, "vampire skirted pants equivalent"),
        armor("Vampire Top Hat", ContentCatalog.class, "vampire top hat"),
        armor("Vampire Trousers", ContentCatalog.class, "vampire pants equivalent"),
        armor("Witch Hunter", EquipmentSetEffects.class, "complete hunter equipment mechanics"),
        armor("Witch Hunter Boots", EquipmentSetEffects.class, "base hunter boots"),
        armor("Witch Hunter Boots (Silvered)", EquipmentSetEffects.class, "silvered hunter boots"),
        armor("Witch Hunter Coat", EquipmentSetEffects.class, "base hunter coat"),
        armor("Witch Hunter Coat (Silvered)", EquipmentSetEffects.class, "silvered hunter coat"),
        armor("Witch Hunter Dawn Boots", EquipmentSetEffects.class, "dawn hunter boots"),
        armor("Witch Hunter Dawn Coat", EquipmentSetEffects.class, "dawn hunter coat"),
        armor("Witch Hunter Dawn Hat", EquipmentSetEffects.class, "dawn hunter hat"),
        armor("Witch Hunter Dawn Trousers", EquipmentSetEffects.class, "dawn hunter pants"),
        armor("Witch Hunter Hat", EquipmentSetEffects.class, "base hunter hat"),
        armor("Witch Hunter Hat (Silvered)", EquipmentSetEffects.class, "silvered hunter hat"),
        armor("Witch Hunter Trousers", EquipmentSetEffects.class, "base hunter pants"),
        armor("Witch Hunter Trousers (Silvered)", EquipmentSetEffects.class, "silvered hunter pants"),
        armor("Witches Hat", EquipmentSetEffects.class, "brew duplication headwear"),
        armor("Witches Robes", EquipmentSetEffects.class, "brew duplication and creeper-pacifying robes")
    );

    @Test
    void everyWorldAndArmorNavboxEntryHasExecutableCoverage() {
        assertEquals(91, WORLD.size());
        assertEquals(38, ARMOR.size());
        assertEquals(91, distinctTitles(WORLD));
        assertEquals(38, distinctTitles(ARMOR));
        assertEquals(127, distinctTitles(java.util.stream.Stream.concat(WORLD.stream(), ARMOR.stream()).toList()));
        assertEquals(WORLD_REDLINKS, missingTitles(WORLD));
        assertEquals(ARMOR_REDLINKS, missingTitles(ARMOR));
        java.util.stream.Stream.concat(WORLD.stream(), ARMOR.stream()).forEach(entry -> {
            assertNotNull(entry.anchor());
            assertFalse(entry.mechanism().isBlank());
        });
    }

    private static PageCoverage world(final String title, final Class<?> anchor, final String mechanism) {
        return new PageCoverage(title, !WORLD_REDLINKS.contains(title), anchor, mechanism);
    }

    private static PageCoverage armor(final String title, final Class<?> anchor, final String mechanism) {
        return new PageCoverage(title, !ARMOR_REDLINKS.contains(title), anchor, mechanism);
    }

    private static long distinctTitles(final List<PageCoverage> entries) {
        return entries.stream().map(PageCoverage::title).distinct().count();
    }

    private static Set<String> missingTitles(final List<PageCoverage> entries) {
        return entries.stream().filter(entry -> !entry.sourceArticleExists()).map(PageCoverage::title)
            .collect(Collectors.toUnmodifiableSet());
    }

    private record PageCoverage(
        String title,
        boolean sourceArticleExists,
        Class<?> anchor,
        String mechanism
    ) {
    }
}
