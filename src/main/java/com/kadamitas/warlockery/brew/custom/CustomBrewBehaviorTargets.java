package com.kadamitas.warlockery.brew.custom;

import com.kadamitas.warlockery.brew.BrewBehavior;
import java.util.Set;

public final class CustomBrewBehaviorTargets {
    private static final Set<BrewBehavior> BLOCKS = Set.of(
        BrewBehavior.GROW,
        BrewBehavior.EXTINGUISH,
        BrewBehavior.FREEZE,
        BrewBehavior.PLACE_WEB,
        BrewBehavior.IGNITE,
        BrewBehavior.EXPLODE,
        BrewBehavior.FELL_LOGS,
        BrewBehavior.PRUNE_LEAVES,
        BrewBehavior.HARVEST_CROPS,
        BrewBehavior.TILL_SOIL,
        BrewBehavior.PLACE_LILIES,
        BrewBehavior.ICE_SHELL,
        BrewBehavior.PLACE_SNOW,
        BrewBehavior.ERODE,
        BrewBehavior.LEVEL_LAND,
        BrewBehavior.PULVERIZE_ROCK,
        BrewBehavior.RAISE_LAND,
        BrewBehavior.PLACE_THORNS,
        BrewBehavior.TRANSPOSE_ORES,
        BrewBehavior.PLACE_VINES,
        BrewBehavior.DISSIPATE_GAS,
        BrewBehavior.PLACE_WATER,
        BrewBehavior.PART_WATER,
        BrewBehavior.PART_LAVA,
        BrewBehavior.PLANT_DROPS,
        BrewBehavior.SHIFT_SEASONS,
        BrewBehavior.APPLY_SNOW_TRAIL,
        BrewBehavior.SPROUT_BRANCHES,
        BrewBehavior.SUBSTITUTE_BLOCKS,
        BrewBehavior.SOLIDIFY_STONE,
        BrewBehavior.SOLIDIFY_DIRT,
        BrewBehavior.SOLIDIFY_SAND,
        BrewBehavior.SOLIDIFY_SANDSTONE,
        BrewBehavior.SOLIDIFY_EROSION
    );
    private static final Set<BrewBehavior> ENTITIES = Set.of(
        BrewBehavior.EXTINGUISH,
        BrewBehavior.FREEZE,
        BrewBehavior.IGNITE,
        BrewBehavior.EXPLODE,
        BrewBehavior.PUSH,
        BrewBehavior.PULL,
        BrewBehavior.LIFT,
        BrewBehavior.ATTRACT_ANIMALS,
        BrewBehavior.REPEL_ANIMALS,
        BrewBehavior.REVEAL,
        BrewBehavior.REMOVE_BENEFICIAL,
        BrewBehavior.REMOVE_HARMFUL,
        BrewBehavior.REMOVE_NAUSEA,
        BrewBehavior.HARM_WEREWOLVES,
        BrewBehavior.WEAKEN_VAMPIRES,
        BrewBehavior.HARM_DEMONS,
        BrewBehavior.SUMMON_BATS,
        BrewBehavior.BLIGHT,
        BrewBehavior.FEAR,
        BrewBehavior.PULL_TO_OWNER,
        BrewBehavior.HARM_INSECTS,
        BrewBehavior.BREED_ANIMALS,
        BrewBehavior.BUFF_UNDEAD,
        BrewBehavior.SPREAD_HARMFUL,
        BrewBehavior.STEAL_BENEFICIAL,
        BrewBehavior.RANDOM_TELEPORT,
        BrewBehavior.HARM_UNDEAD,
        BrewBehavior.CURSE_UNDEAD,
        BrewBehavior.DRAIN_RESERVES,
        BrewBehavior.EXTEND_EFFECTS,
        BrewBehavior.DARKNESS_PREY,
        BrewBehavior.MOONLIGHT,
        BrewBehavior.SUMMON_POISON_TOADS,
        BrewBehavior.RAISE_DEAD,
        BrewBehavior.APPLY_ABSORB_MAGIC,
        BrewBehavior.APPLY_ATTRACT_ARROWS,
        BrewBehavior.BOTTLE_YIELD,
        BrewBehavior.APPLY_GAS_IMMUNITY,
        BrewBehavior.APPLY_ENDER_INHIBITION,
        BrewBehavior.APPLY_ILL_FITTING,
        BrewBehavior.APPLY_INSANITY,
        BrewBehavior.APPLY_KEEP_EFFECTS,
        BrewBehavior.APPLY_KEEP_INVENTORY,
        BrewBehavior.APPLY_NIGHTMARE,
        BrewBehavior.APPLY_POISON_WEAPON,
        BrewBehavior.APPLY_REFLECT_ARROWS,
        BrewBehavior.APPLY_REFLECT_DAMAGE,
        BrewBehavior.APPLY_REINCARNATE,
        BrewBehavior.APPLY_REPEL_ATTACKER,
        BrewBehavior.APPLY_RESIZING,
        BrewBehavior.SUMMON_ABYSSAL_REGENT,
        BrewBehavior.APPLY_TINT_SKIN,
        BrewBehavior.APPLY_WEREWOLF_LOCK,
        BrewBehavior.APPLY_DISEASE,
        BrewBehavior.APPLY_INFECTION,
        BrewBehavior.APPLY_SINKING,
        BrewBehavior.APPLY_SUNLIGHT_CURSE,
        BrewBehavior.APPLY_VOLATILITY,
        BrewBehavior.SUMMON_OWLS,
        BrewBehavior.APPLY_CURSED_LEAPING,
        BrewBehavior.APPLY_OVERHEATING,
        BrewBehavior.APPLY_SLEEPING,
        BrewBehavior.APPLY_SNOW_TRAIL,
        BrewBehavior.APPLY_DEPTHS,
        BrewBehavior.APPLY_GROTESQUE
    );

    private CustomBrewBehaviorTargets() {
    }

    public static boolean affectsBlocks(final BrewBehavior behavior) {
        return BLOCKS.contains(behavior);
    }

    public static boolean affectsEntities(final BrewBehavior behavior) {
        return ENTITIES.contains(behavior);
    }

    public static boolean allows(
        final BrewBehavior behavior,
        final boolean skipBlocks,
        final boolean skipEntities
    ) {
        return !(skipBlocks && affectsBlocks(behavior))
            && !(skipEntities && affectsEntities(behavior));
    }
}
