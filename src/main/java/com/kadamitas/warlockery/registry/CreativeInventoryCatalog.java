package com.kadamitas.warlockery.registry;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class CreativeInventoryCatalog {
    private static final Set<String> INTERNAL_IDS = Set.of(
        "abyssal_portal",
        "alchemical_oven_lit",
        "barrier",
        "brew",
        "brewgas",
        "brewliquid",
        "biomenote",
        "disease",
        "distilleryburning",
        "erosionbrew",
        "force",
        "hexwooddoubleslab",
        "hollowtears",
        "icedoubleslab",
        "light",
        "mirrorblock2",
        "placeditem",
        "ingredient",
        "potion",
        "snowdoubleslab",
        "spiritflowing",
        "wallgen"
    );
    private static final Set<String> MANUALS = Set.of(
        "cauldronbook", "vampirebook", "bookbiomes2",
        "ingredient_book_biomes", "ingredient_book_burning", "ingredient_book_circle_magic",
        "ingredient_book_distilling", "ingredient_book_herbology", "ingredient_book_infusions",
        "ingredient_book_oven", "ingredient_book_wands"
    );
    private static final Set<String> MACHINES = Set.of(
        "alchemical_oven", "altar", "bloodcrucible", "brazier", "cauldron", "daylightcollector",
        "distilleryidle", "doll_shelf", "filteredfumefunnel", "fumefunnel", "kettle", "silvervat",
        "spinningwheel", "wolfaltar"
    );
    private static final Set<String> STARTER_TOOLS = Set.of("arcane_focus", "ritual_knife", "boline");
    private static final Set<String> BREW_CONTAINERS = Set.of("brewbag", "bucketbrew");
    private static final Set<String> RITUAL_OBJECTS = Set.of(
        "alluringskull", "broken_hexes_statue", "candelabra", "chalice", "circle", "circleglyph_veil",
        "circleglyphinfernal", "circleglyphritual", "crystalball", "demonheart", "dreamcatcher",
        "occluded_summons_statue", "pentacle", "scarecrow", "statuegoddess", "statueofworship",
        "trent", "wickerbundle", "wolfhead", "wolftrap"
    );
    private static final Set<String> BLOCK_IDS = ContentCatalog.BLOCKS.stream()
        .map(ContentCatalog::modernize)
        .collect(Collectors.toUnmodifiableSet());
    private static final List<String> EQUIPMENT_TERMS = List.of(
        "sword", "knife", "boline", "axe", "pickaxe", "shovel", "hoe", "spear", "staff", "branch",
        "hat", "helm", "helmet", "robe", "coat", "boot", "legging", "slipper", "shoe",
        "belt", "girdle", "quiver"
    );
    private static final List<String> NATURE_TERMS = List.of(
        "sapling", "leaves", "log", "planks", "artichoke", "belladonna", "bloodrose", "bramble",
        "dreamroot", "embermoss", "garlicplant", "glintweed", "grassper", "leapinglily", "mandrake",
        "snowbell", "somniancotton", "spanishmoss", "vine", "voidbramble", "wolfsbane", "wormwood"
    );
    private static final List<SectionRule> RULES = List.of(
        rule(Section.GETTING_STARTED, MANUALS::contains),
        rule(Section.GETTING_STARTED, STARTER_TOOLS::contains),
        rule(Section.RITUALS, RITUAL_OBJECTS::contains),
        rule(Section.RITUALS, id -> id.startsWith("chalk") || id.contains("talisman")),
        rule(Section.MACHINES, MACHINES::contains),
        rule(Section.DOLLS, id -> id.contains("doll")),
        rule(Section.BREWS, id -> id.startsWith("brew_") || id.startsWith("ingredient_brew_")
            || BREW_CONTAINERS.contains(id)),
        rule(Section.EQUIPMENT, CreativeInventoryCatalog::isEquipment),
        rule(Section.NATURE, CreativeInventoryCatalog::isNatural),
        rule(Section.BUILDING, BLOCK_IDS::contains),
        rule(Section.MATERIALS, id -> id.startsWith("ingredient_") || id.startsWith("raw_")
            || id.endsWith("_ore") || id.endsWith("_block")),
        rule(Section.CREATURES, id -> id.endsWith("_spawn_egg"))
    );

    private CreativeInventoryCatalog() {
    }

    public static boolean isVisible(final String id) {
        return !INTERNAL_IDS.contains(id);
    }

    public static Section section(final String id) {
        return RULES.stream()
            .filter(rule -> rule.matches().test(id))
            .map(SectionRule::section)
            .findFirst()
            .orElse(Section.OTHER);
    }

    public static List<String> sortedIds(final Collection<String> ids) {
        return ids.stream()
            .filter(CreativeInventoryCatalog::isVisible)
            .sorted(Comparator.comparing(CreativeInventoryCatalog::section).thenComparing(String::compareTo))
            .toList();
    }

    private static boolean isEquipment(final String id) {
        return EQUIPMENT_TERMS.stream().anyMatch(id::contains);
    }

    private static boolean isNatural(final String id) {
        return id.startsWith("seeds") || NATURE_TERMS.stream().anyMatch(id::contains);
    }

    private static SectionRule rule(final Section section, final Predicate<String> matches) {
        return new SectionRule(section, matches);
    }

    public enum Section {
        GETTING_STARTED,
        RITUALS,
        MACHINES,
        DOLLS,
        BREWS,
        EQUIPMENT,
        NATURE,
        BUILDING,
        MATERIALS,
        OTHER,
        CREATURES
    }

    private record SectionRule(Section section, Predicate<String> matches) {
    }
}
