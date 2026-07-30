package com.kadamitas.warlockery.brew;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum BrewBehavior {
    GROW("grow"),
    EXTINGUISH("extinguish"),
    FREEZE("freeze"),
    PLACE_WEB("place_web"),
    IGNITE("ignite"),
    EXPLODE("explode"),
    PUSH("push"),
    PULL("pull"),
    LIFT("lift"),
    ATTRACT_ANIMALS("attract_animals"),
    REPEL_ANIMALS("repel_animals"),
    FELL_LOGS("fell_logs"),
    PRUNE_LEAVES("prune_leaves"),
    HARVEST_CROPS("harvest_crops"),
    TILL_SOIL("till_soil"),
    REVEAL("reveal"),
    REMOVE_BENEFICIAL("remove_beneficial"),
    REMOVE_HARMFUL("remove_harmful"),
    REMOVE_NAUSEA("remove_nausea"),
    HARM_WEREWOLVES("harm_werewolves"),
    WEAKEN_VAMPIRES("weaken_vampires"),
    HARM_DEMONS("harm_demons"),
    SUMMON_BATS("summon_bats"),
    SUMMON_MURDEROUS_FLOCK("summon_murderous_flock"),
    BLIGHT("blight"),
    ERODE("erode"),
    FEAR("fear"),
    PULL_TO_OWNER("pull_to_owner"),
    PLACE_LILIES("place_lilies"),
    ICE_SHELL("ice_shell"),
    PLACE_SNOW("place_snow"),
    HARM_INSECTS("harm_insects"),
    LEVEL_LAND("level_land"),
    BREED_ANIMALS("breed_animals"),
    PULVERIZE_ROCK("pulverize_rock"),
    RAISE_LAND("raise_land"),
    BUFF_UNDEAD("buff_undead"),
    SPREAD_HARMFUL("spread_harmful"),
    STEAL_BENEFICIAL("steal_beneficial"),
    PLACE_THORNS("place_thorns"),
    RANDOM_TELEPORT("random_teleport"),
    TRANSPOSE_ORES("transpose_ores"),
    HARM_UNDEAD("harm_undead"),
    CURSE_UNDEAD("curse_undead"),
    PLACE_VINES("place_vines"),
    DISSIPATE_GAS("dissipate_gas"),
    DRAIN_RESERVES("drain_reserves"),
    EXTEND_EFFECTS("extend_effects"),
    PLACE_WATER("place_water"),
    DARKNESS_PREY("darkness_prey"),
    MOONLIGHT("moonlight"),
    PART_WATER("part_water"),
    PART_LAVA("part_lava"),
    PLANT_DROPS("plant_drops"),
    SUMMON_POISON_TOADS("summon_poison_toads"),
    RAISE_DEAD("raise_dead"),
    APPLY_ABSORB_MAGIC("apply_absorb_magic"),
    APPLY_ATTRACT_ARROWS("apply_attract_arrows"),
    BOTTLE_YIELD("bottle_yield"),
    APPLY_GAS_IMMUNITY("apply_gas_immunity"),
    APPLY_ENDER_INHIBITION("apply_ender_inhibition"),
    APPLY_ILL_FITTING("apply_ill_fitting"),
    APPLY_INSANITY("apply_insanity"),
    APPLY_KEEP_EFFECTS("apply_keep_effects"),
    APPLY_KEEP_INVENTORY("apply_keep_inventory"),
    APPLY_NIGHTMARE("apply_nightmare"),
    APPLY_POISON_WEAPON("apply_poison_weapon"),
    APPLY_REFLECT_ARROWS("apply_reflect_arrows"),
    APPLY_REFLECT_DAMAGE("apply_reflect_damage"),
    APPLY_REINCARNATE("apply_reincarnate"),
    APPLY_REPEL_ATTACKER("apply_repel_attacker"),
    APPLY_RESIZING("apply_resizing"),
    SHIFT_SEASONS("shift_seasons"),
    SUMMON_ABYSSAL_REGENT("summon_abyssal_regent"),
    APPLY_TINT_SKIN("apply_tint_skin"),
    APPLY_WEREWOLF_LOCK("apply_werewolf_lock"),
    APPLY_DISEASE("apply_disease"),
    APPLY_INFECTION("apply_infection"),
    APPLY_SINKING("apply_sinking"),
    APPLY_SUNLIGHT_CURSE("apply_sunlight_curse"),
    APPLY_VOLATILITY("apply_volatility"),
    SUMMON_OWLS("summon_owls"),
    APPLY_CURSED_LEAPING("apply_cursed_leaping"),
    APPLY_OVERHEATING("apply_overheating"),
    APPLY_SLEEPING("apply_sleeping"),
    APPLY_SNOW_TRAIL("apply_snow_trail"),
    SPROUT_BRANCHES("sprout_branches"),
    SUBSTITUTE_BLOCKS("substitute_blocks"),
    APPLY_DEPTHS("apply_depths"),
    APPLY_GROTESQUE("apply_grotesque"),
    SOLIDIFY_STONE("solidify_stone"),
    SOLIDIFY_DIRT("solidify_dirt"),
    SOLIDIFY_SAND("solidify_sand"),
    SOLIDIFY_SANDSTONE("solidify_sandstone"),
    SOLIDIFY_EROSION("solidify_erosion");

    private static final Map<String, BrewBehavior> BY_ID = Arrays.stream(values())
        .collect(Collectors.toUnmodifiableMap(BrewBehavior::id, Function.identity()));

    public static final Codec<BrewBehavior> CODEC = Codec.STRING.comapFlatMap(
        id -> find(id).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown brew behavior: " + id)),
        BrewBehavior::id
    );

    private final String id;

    BrewBehavior(final String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static java.util.Optional<BrewBehavior> find(final String id) {
        return java.util.Optional.ofNullable(BY_ID.get(id));
    }
}
