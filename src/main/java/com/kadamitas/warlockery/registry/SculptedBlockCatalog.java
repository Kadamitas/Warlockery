package com.kadamitas.warlockery.registry;

import java.util.Set;

public final class SculptedBlockCatalog {
    private static final Set<String> BLOCKS = Set.of(
        "alchemical_oven",
        "alchemical_oven_lit",
        "alluringskull",
        "altar",
        "beartrap",
        "bloodcrucible",
        "brazier",
        "broken_hexes_statue",
        "candelabra",
        "cauldron",
        "chalice",
        "circle",
        "circleglyphgolden",
        "circleglyph_veil",
        "circleglyphinfernal",
        "circleglyphritual",
        "coffinblock",
        "crystalball",
        "daylightcollector",
        "demonheart",
        "distilleryburning",
        "distilleryidle",
        "doll_shelf",
        "dreamcatcher",
        "filteredfumefunnel",
        "fumefunnel",
        "garlicgarland",
        "glowglobe",
        "kettle",
        "leechchest",
        "mirrorblock",
        "mirrorblock2",
        "mirrorwall",
        "occluded_summons_statue",
        "paradox_egg",
        "refillingchest",
        "scarecrow",
        "silvervat",
        "spinningwheel",
        "spiritportal",
        "statuegoddess",
        "statueofworship",
        "trent",
        "voidbramble",
        "web",
        "wolfaltar",
        "wolfhead",
        "wolftrap"
    );

    private SculptedBlockCatalog() {
    }

    public static boolean contains(final String id) {
        return BLOCKS.contains(id);
    }

    public static Set<String> ids() {
        return BLOCKS;
    }
}
