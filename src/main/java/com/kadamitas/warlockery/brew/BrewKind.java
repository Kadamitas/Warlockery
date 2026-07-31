package com.kadamitas.warlockery.brew;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.PotionContents;

public record BrewKind(
    String id,
    int color,
    List<BrewEffectSpec> effects,
    List<BrewBehavior> behaviors,
    float radius,
    float potency
) {
    public static final Codec<BrewKind> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("id").forGetter(BrewKind::id),
        Codec.intRange(0, 0xFFFFFF).optionalFieldOf("color", 0x385A46).forGetter(BrewKind::color),
        BrewEffectSpec.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(BrewKind::effects),
        BrewBehavior.CODEC.listOf().optionalFieldOf("behaviors", List.of()).forGetter(BrewKind::behaviors),
        Codec.floatRange(0.5F, 12.0F).optionalFieldOf("radius", 4.0F).forGetter(BrewKind::radius),
        Codec.floatRange(0.1F, 8.0F).optionalFieldOf("potency", 1.0F).forGetter(BrewKind::potency)
    ).apply(instance, BrewKind::new));

    public static final BrewKind HEAL = effect("heal", 0xF82423, "minecraft:instant_health", 1, 0);
    public static final BrewKind HARM = effect("harm", 0x430A09, "minecraft:instant_damage", 1, 0);
    public static final BrewKind ABSORPTION = effect("absorption", 0x2552A5, "minecraft:absorption", 1_800, 1);
    public static final BrewKind HEALTH_BOOST = effect("health_boost", 0xF87D23, "minecraft:health_boost", 3_600, 1);
    public static final BrewKind REGENERATION = effect("regeneration", 0xCD5CAB, "minecraft:regeneration", 900, 1);
    public static final BrewKind DAMAGE_BOOST = effect("damage_boost", 0x932423, "minecraft:strength", 3_600, 1);
    public static final BrewKind FAST_MOVEMENT = effect("fast_movement", 0x7CAFC6, "minecraft:speed", 3_600, 1);
    public static final BrewKind SLOW_MOVEMENT = effect("slow_movement", 0x5A6C81, "minecraft:slowness", 1_800, 1);
    public static final BrewKind JUMP = effect("jump", 0x786297, "minecraft:jump_boost", 3_600, 1);
    public static final BrewKind FLOATING = effect("floating", 0xCEFFFF, "minecraft:levitation", 300, 0);
    public static final BrewKind SLOW_FALL = effect("slow_fall", 0xF3CFB9, "minecraft:slow_falling", 2_400, 0);
    public static final BrewKind SOARING = effect("soaring", 0x79D8E4, "warlockery:soaring", 144_000, 0);
    public static final BrewKind BLINDNESS = effect("blindness", 0x1F1F23, "minecraft:blindness", 1_200, 0);
    public static final BrewKind INVISIBLE = effect("invisible", 0x7F8392, "minecraft:invisibility", 3_600, 0);
    public static final BrewKind NIGHT_VISION = effect("night_vision", 0x1F1FA1, "minecraft:night_vision", 3_600, 0);
    public static final BrewKind WATER_BREATHING = effect("water_breathing", 0x2E5299, "minecraft:water_breathing", 3_600, 0);
    public static final BrewKind SWIM_SPEED = effect("swim_speed", 0x88A3BE, "minecraft:dolphins_grace", 1_800, 0);
    public static final BrewKind FIRE_RESISTANCE = effect("fire_resistance", 0xE49A3A, "minecraft:fire_resistance", 3_600, 0);
    public static final BrewKind POISON = effect("poison", 0x4E9331, "minecraft:poison", 900, 1);
    public static final BrewKind WITHER = effect("wither", 0x352A27, "minecraft:wither", 600, 1);
    public static final BrewKind WEAKNESS = effect("weakness", 0x484D48, "minecraft:weakness", 1_800, 1);
    public static final BrewKind FULLNESS = effect("fullness", 0xE0A23A, "minecraft:saturation", 1, 1);
    public static final BrewKind PARALYSIS = effects("paralysis", 0x232F3D, List.of(
        new BrewEffectSpec("minecraft:slowness", 600, 5),
        new BrewEffectSpec("minecraft:mining_fatigue", 600, 3)
    ));
    public static final BrewKind AIR_HIKE = world("air_hike", 0xA8E6E6, 4.0F, 1.2F, BrewBehavior.LIFT);
    public static final BrewKind FERTILIZE = world("fertilize", 0x66A33D, 4.0F, 1.0F, BrewBehavior.GROW);
    public static final BrewKind GROW_FLOWERS = world("grow_flowers", 0xDA70D6, 4.0F, 1.0F, BrewBehavior.GROW);
    public static final BrewKind GROW_SAPLING = world("grow_sapling", 0x4F8B3A, 4.0F, 1.0F, BrewBehavior.GROW);
    public static final BrewKind EXTINGUISH_FIRES = world(
        "extinguish_fires", 0x3B7FD1, 4.0F, 1.0F, BrewBehavior.EXTINGUISH
    );
    public static final BrewKind FREEZE = hybrid("freeze", 0x9AD6DF,
        List.of(new BrewEffectSpec("minecraft:slowness", 600, 2)), 4.0F, 1.0F, BrewBehavior.FREEZE);
    public static final BrewKind WEBS = world("webs", 0xD8D8D8, 3.0F, 1.0F, BrewBehavior.PLACE_WEB);
    public static final BrewKind FLAMES = world("flames", 0xE65A24, 3.0F, 1.0F, BrewBehavior.IGNITE);
    public static final BrewKind BLAST = world("blast", 0x3D3D3D, 4.0F, 1.5F, BrewBehavior.EXPLODE);
    public static final BrewKind PUSH = world("push", 0x62C5E8, 4.0F, 1.0F, BrewBehavior.PUSH);
    public static final BrewKind PULL = world("pull", 0x674EA7, 4.0F, 1.0F, BrewBehavior.PULL);
    public static final BrewKind ANIMAL_ATTRACTION = world("animal_attraction", 0xE98AA5, 8.0F, 1.0F,
        BrewBehavior.ATTRACT_ANIMALS);
    public static final BrewKind ANIMAL_REPULSION = world("animal_repulsion", 0x776655, 8.0F, 1.0F,
        BrewBehavior.REPEL_ANIMALS);
    public static final BrewKind FELL_TREE = world("fell_tree", 0x75502B, 3.0F, 1.0F, BrewBehavior.FELL_LOGS);
    public static final BrewKind PRUNE_LEAVES = world(
        "prune_leaves", 0x3F7F38, 5.0F, 1.0F, BrewBehavior.PRUNE_LEAVES
    );
    public static final BrewKind HARVEST = world("harvest", 0xD7A83E, 5.0F, 1.0F, BrewBehavior.HARVEST_CROPS);
    public static final BrewKind TILL_LAND = world("till_land", 0x805B3A, 4.0F, 1.0F, BrewBehavior.TILL_SOIL);
    public static final BrewKind REVEALING = world("revealing", 0xF1E36B, 5.0F, 1.0F, BrewBehavior.REVEAL);
    public static final BrewKind REMOVE_BUFFS = world("remove_buffs", 0x8A6D9E, 4.0F, 1.0F,
        BrewBehavior.REMOVE_BENEFICIAL);
    public static final BrewKind REMOVE_DEBUFFS = world("remove_debuffs", 0x79B5A3, 4.0F, 1.0F,
        BrewBehavior.REMOVE_HARMFUL);
    public static final BrewKind STOUT_BELLY = world(
        "stout_belly", 0xC8984A, 4.0F, 1.0F, BrewBehavior.REMOVE_NAUSEA
    );
    public static final BrewKind HARM_WEREWOLVES = world("harm_werewolves", 0xC6CED6, 4.0F, 1.0F,
        BrewBehavior.HARM_WEREWOLVES);
    public static final BrewKind WEAKEN_VAMPIRES = world("weaken_vampires", 0x7B1723, 4.0F, 1.0F,
        BrewBehavior.WEAKEN_VAMPIRES);
    public static final BrewKind DEMONBANE = world("demonbane", 0xD9BE64, 4.0F, 1.0F, BrewBehavior.HARM_DEMONS);
    public static final BrewKind BATS = hybrid("bats", 0x3B3245, List.of(
        new BrewEffectSpec("minecraft:slowness", 400, 1),
        new BrewEffectSpec("minecraft:weakness", 400, 0)
    ), 5.0F, 1.0F, BrewBehavior.SUMMON_BATS);
    public static final BrewKind BLIGHT = world("blight", 0x51422B, 5.0F, 1.0F, BrewBehavior.BLIGHT);
    public static final BrewKind EROSION = world("erosion", 0xA1C84C, 4.0F, 1.0F, BrewBehavior.ERODE);
    public static final BrewKind FEAR = hybrid("fear", 0x252033, List.of(
        new BrewEffectSpec("minecraft:weakness", 600, 0)
    ), 6.0F, 1.4F, BrewBehavior.FEAR);
    public static final BrewKind FROGS_TONGUE = world(
        "frogs_tongue", 0x6A9A42, 6.0F, 1.5F, BrewBehavior.PULL_TO_OWNER
    );
    public static final BrewKind FROST = hybrid("frost", 0xA8E5EF, List.of(
        new BrewEffectSpec("minecraft:slowness", 800, 2)
    ), 5.0F, 1.0F, BrewBehavior.FREEZE, BrewBehavior.ICE_SHELL);
    public static final BrewKind GROW_LILY = world(
        "grow_lily", 0x4D8F4D, 5.0F, 1.0F, BrewBehavior.PLACE_LILIES
    );
    public static final BrewKind ICE_SHELL = world(
        "ice_shell", 0xB6E7F2, 4.0F, 1.0F, BrewBehavior.ICE_SHELL
    );
    public static final BrewKind ICE_WORLD = world(
        "ice_world", 0xD0F3FA, 7.0F, 1.0F, BrewBehavior.FREEZE, BrewBehavior.PLACE_SNOW
    );
    public static final BrewKind INFECTION = hybrid("infection", 0x507A2F, List.of(
        new BrewEffectSpec("minecraft:poison", 900, 1),
        new BrewEffectSpec("minecraft:weakness", 900, 0)
    ), 4.0F, 1.0F, BrewBehavior.SPREAD_HARMFUL, BrewBehavior.APPLY_INFECTION);
    public static final BrewKind INFERNO = world(
        "inferno", 0xF04418, 7.0F, 2.0F, BrewBehavior.IGNITE, BrewBehavior.EXPLODE
    );
    public static final BrewKind INK = effects("ink", 0x171522, List.of(
        new BrewEffectSpec("minecraft:blindness", 600, 0),
        new BrewEffectSpec("minecraft:slowness", 300, 1)
    ));
    public static final BrewKind INSECT_BANE = world(
        "insect_bane", 0xB99437, 4.0F, 1.0F, BrewBehavior.HARM_INSECTS
    );
    public static final BrewKind LEVEL_LAND = world(
        "level_land", 0x82705C, 5.0F, 1.0F, BrewBehavior.LEVEL_LAND
    );
    public static final BrewKind LOVE = world(
        "love", 0xE78AAE, 7.0F, 1.0F, BrewBehavior.ATTRACT_ANIMALS, BrewBehavior.BREED_ANIMALS
    );
    public static final BrewKind OVERHEATING = hybrid("overheating", 0xE86C24, List.of(
        new BrewEffectSpec("minecraft:weakness", 800, 1)
    ), 4.0F, 1.0F, BrewBehavior.IGNITE, BrewBehavior.APPLY_OVERHEATING);
    public static final BrewKind PULVERIZE_ROCK = world(
        "pulverize_rock", 0x918A80, 4.0F, 1.0F, BrewBehavior.PULVERIZE_ROCK
    );
    public static final BrewKind RAISE_LAND = world(
        "raise_land", 0x745135, 4.0F, 1.0F, BrewBehavior.RAISE_LAND
    );
    public static final BrewKind RAISING = world(
        "raising", 0x4D3B62, 6.0F, 1.0F, BrewBehavior.RAISE_DEAD, BrewBehavior.BUFF_UNDEAD
    );
    public static final BrewKind SINKING = hybrid("sinking", 0x314B58, List.of(
        new BrewEffectSpec("minecraft:slowness", 1_200, 2),
        new BrewEffectSpec("minecraft:mining_fatigue", 1_200, 1)
    ), 4.0F, 1.0F, BrewBehavior.APPLY_SINKING);
    public static final BrewKind SNOW_BURST = world(
        "snow_burst", 0xF4FAFF, 5.0F, 1.0F, BrewBehavior.PLACE_SNOW, BrewBehavior.APPLY_SNOW_TRAIL
    );
    public static final BrewKind SPREAD_DEBUFFS = world(
        "spread_debuffs", 0x67516F, 6.0F, 1.0F, BrewBehavior.SPREAD_HARMFUL
    );
    public static final BrewKind STEAL_BUFFS = world(
        "steal_buffs", 0xC19BD5, 5.0F, 1.0F, BrewBehavior.STEAL_BENEFICIAL
    );
    public static final BrewKind THORNS = world(
        "thorns", 0x367A35, 4.0F, 1.0F, BrewBehavior.PLACE_THORNS
    );
    public static final BrewKind TRANSPOSE = world(
        "transpose", 0x6442A4, 5.0F, 1.0F, BrewBehavior.RANDOM_TELEPORT
    );
    public static final BrewKind TRANSPOSE_ORE = world(
        "transpose_ore", 0x956FAE, 6.0F, 1.0F, BrewBehavior.TRANSPOSE_ORES
    );
    public static final BrewKind UNDEAD_BANE = world(
        "undead_bane", 0xE8D690, 5.0F, 1.0F, BrewBehavior.HARM_UNDEAD
    );
    public static final BrewKind UNDEADS_CURSE = world(
        "undeads_curse", 0xD68B35, 5.0F, 1.0F,
        BrewBehavior.CURSE_UNDEAD, BrewBehavior.APPLY_SUNLIGHT_CURSE
    );
    public static final BrewKind VINES = world(
        "vines", 0x3F7C42, 5.0F, 1.0F, BrewBehavior.PLACE_VINES
    );
    public static final BrewKind WASTING = world(
        "wasting", 0x4D4B22, 5.0F, 1.0F, BrewBehavior.WASTE
    );
    public static final BrewKind BAT_BURST = world(
        "bat_burst", 0x29222F, 5.0F, 1.5F, BrewBehavior.SUMMON_BATS
    );
    public static final BrewKind MURDEROUS_FLOCK = world(
        "murderous_flock", 0x24162F, 6.0F, 1.5F, BrewBehavior.SUMMON_MURDEROUS_FLOCK
    );
    public static final BrewKind CACTUS_THORNED = world(
        "cactus_thorned", 0x43832F, 4.0F, 1.0F, BrewBehavior.PLACE_THORNS
    );
    public static final BrewKind COMBUSTION = world(
        "combustion", 0xFF6A18, 4.0F, 1.0F, BrewBehavior.IGNITE
    );
    public static final BrewKind DISEASE = hybrid("disease", 0x657A28, List.of(
        new BrewEffectSpec("minecraft:poison", 1_200, 1),
        new BrewEffectSpec("minecraft:weakness", 1_200, 1)
    ), 5.0F, 1.0F, BrewBehavior.SPREAD_HARMFUL, BrewBehavior.APPLY_DISEASE);
    public static final BrewKind DISSIPATE_GAS = world(
        "dissipate_gas", 0xD9F3EA, 6.0F, 1.0F, BrewBehavior.DISSIPATE_GAS
    );
    public static final BrewKind DRAIN_MAGIC = world(
        "drain_magic", 0x46335F, 5.0F, 1.0F, BrewBehavior.DRAIN_RESERVES
    );
    public static final BrewKind DURATION_BOOST = world(
        "duration_boost", 0xA986D3, 5.0F, 1.0F, BrewBehavior.EXTEND_EFFECTS
    );
    public static final BrewKind ENDLESS_WATER = world(
        "endless_water", 0x3484D2, 3.0F, 1.0F, BrewBehavior.PLACE_WATER
    );
    public static final BrewKind FORTUNE = effect(
        "fortune", 0x7CB342, "minecraft:luck", 3_600, 1
    );
    public static final BrewKind FROGS_LEG = effect(
        "frogs_leg", 0x7AAE39, "minecraft:jump_boost", 1_200, 3
    );
    public static final BrewKind GRUES_PREY = world(
        "grues_prey", 0x191925, 5.0F, 1.0F, BrewBehavior.DARKNESS_PREY
    );
    public static final BrewKind MOONSHINE = hybrid(
        "moonshine",
        0xCCD7F4,
        List.of(new BrewEffectSpec("minecraft:nausea", 3_600, 0)),
        6.0F,
        1.0F,
        BrewBehavior.MOONLIGHT,
        BrewBehavior.APPLY_MOONSHINE
    );
    public static final BrewKind PART_LAVA = world(
        "part_lava", 0xD5531B, 5.0F, 1.0F, BrewBehavior.PART_LAVA
    );
    public static final BrewKind PART_WATER = world(
        "part_water", 0x55A9E8, 5.0F, 1.0F, BrewBehavior.PART_WATER
    );
    public static final BrewKind PLANTING = world(
        "planting", 0x63A641, 6.0F, 1.0F, BrewBehavior.PLANT_DROPS
    );
    public static final BrewKind POISON_TOAD = world(
        "poison_toad", 0x698D31, 5.0F, 1.0F, BrewBehavior.SUMMON_POISON_TOADS
    );
    public static final BrewKind RAISE_DEAD = world(
        "raise_dead", 0x393044, 6.0F, 1.0F, BrewBehavior.RAISE_DEAD
    );
    public static final BrewKind VINES_FLAMMABLE = world(
        "vines_flammable", 0x4A8031, 5.0F, 1.0F, BrewBehavior.PLACE_VINES
    );
    public static final BrewKind VOLATILITY = world(
        "volatility", 0xE57D31, 4.0F, 1.25F, BrewBehavior.APPLY_VOLATILITY
    );
    public static final BrewKind ABSORB_MAGIC = world(
        "absorb_magic", 0x75559A, 4.0F, 1.0F, BrewBehavior.APPLY_ABSORB_MAGIC
    );
    public static final BrewKind ATTRACT_ARROWS = world(
        "attract_arrows", 0xB85C45, 4.0F, 1.0F, BrewBehavior.APPLY_ATTRACT_ARROWS
    );
    public static final BrewKind BOTTLING = world(
        "bottling", 0x9BC6D8, 3.0F, 1.0F, BrewBehavior.BOTTLE_YIELD
    );
    public static final BrewKind GAS_IMMUNITY = world(
        "gas_immunity", 0xC4E3BC, 4.0F, 1.0F, BrewBehavior.APPLY_GAS_IMMUNITY
    );
    public static final BrewKind ENDER_INHIBITION = world(
        "ender_inhibition", 0x382C58, 4.0F, 1.0F, BrewBehavior.APPLY_ENDER_INHIBITION
    );
    public static final BrewKind ILL_FITTING = world(
        "ill_fitting", 0x73625B, 4.0F, 1.0F, BrewBehavior.APPLY_ILL_FITTING
    );
    public static final BrewKind INSANITY = world(
        "insanity", 0x5D426D, 4.0F, 1.0F, BrewBehavior.APPLY_INSANITY
    );
    public static final BrewKind KEEP_EFFECTS = world(
        "keep_effects", 0xE6AFD6, 4.0F, 1.0F, BrewBehavior.APPLY_KEEP_EFFECTS
    );
    public static final BrewKind KEEP_INVENTORY = world(
        "keep_inventory", 0xE7C874, 4.0F, 1.0F, BrewBehavior.APPLY_KEEP_INVENTORY
    );
    public static final BrewKind NIGHTMARE = world(
        "nightmare", 0x1A142B, 4.0F, 1.0F, BrewBehavior.APPLY_NIGHTMARE
    );
    public static final BrewKind POISON_WEAPON = world(
        "poison_weapon", 0x4C7B2B, 4.0F, 1.0F, BrewBehavior.APPLY_POISON_WEAPON
    );
    public static final BrewKind REFLECT_ARROWS = world(
        "reflect_arrows", 0xCBD7E6, 4.0F, 1.0F, BrewBehavior.APPLY_REFLECT_ARROWS
    );
    public static final BrewKind REFLECT_DAMAGE = world(
        "reflect_damage", 0xB66C8C, 4.0F, 1.0F, BrewBehavior.APPLY_REFLECT_DAMAGE
    );
    public static final BrewKind REINCARNATE = world(
        "reincarnate", 0x9CCB68, 4.0F, 1.0F, BrewBehavior.APPLY_REINCARNATE
    );
    public static final BrewKind REPEL_ATTACKER = world(
        "repel_attacker", 0x72B4C9, 4.0F, 1.0F, BrewBehavior.APPLY_REPEL_ATTACKER
    );
    public static final BrewKind RESIZING = world(
        "resizing", 0xDB9D68, 4.0F, 1.0F, BrewBehavior.APPLY_RESIZING
    );
    public static final BrewKind SHIFTING_SEASONS = world(
        "shifting_seasons", 0xA5B86B, 6.0F, 1.0F, BrewBehavior.SHIFT_SEASONS
    );
    public static final BrewKind SUMMON_ABYSSAL_REGENT = world(
        "summon_abyssal_regent", 0x4B173B, 5.0F, 1.0F, BrewBehavior.SUMMON_ABYSSAL_REGENT
    );
    public static final BrewKind TINT_SKIN = hybrid("tint_skin", 0x7FBAB4, List.of(
        new BrewEffectSpec("minecraft:glowing", 1_200, 0)
    ), 4.0F, 1.0F, BrewBehavior.APPLY_TINT_SKIN);
    public static final BrewKind WEREWOLF_LOCK = world(
        "werewolf_lock", 0xA7A9B0, 4.0F, 1.0F, BrewBehavior.APPLY_WEREWOLF_LOCK
    );
    public static final BrewKind BODEGA = world(
        "bodega", 0x6D5946, 7.0F, 1.0F, BrewBehavior.SUMMON_OWLS
    );
    public static final BrewKind CURSED_LEAPING = hybrid("cursed_leaping", 0x78A64A, List.of(
        new BrewEffectSpec("minecraft:jump_boost", 1_200, 2)
    ), 4.0F, 1.0F, BrewBehavior.APPLY_CURSED_LEAPING);
    public static final BrewKind SLEEPING = hybrid("sleeping", 0x433A72, List.of(
        new BrewEffectSpec("minecraft:slowness", 1_200, 1),
        new BrewEffectSpec("minecraft:darkness", 1_200, 0)
    ), 4.0F, 1.0F, BrewBehavior.APPLY_SLEEPING);
    public static final BrewKind SPROUTING = world(
        "sprouting", 0x668B3D, 5.0F, 1.0F, BrewBehavior.SPROUT_BRANCHES
    );
    public static final BrewKind SUBSTITUTION = world(
        "substitution", 0xA17B55, 5.0F, 1.0F, BrewBehavior.SUBSTITUTE_BLOCKS
    );
    public static final BrewKind DEPTHS = hybrid("depths", 0x214D78, List.of(
        new BrewEffectSpec("minecraft:water_breathing", 3_600, 0)
    ), 4.0F, 1.0F, BrewBehavior.APPLY_DEPTHS);
    public static final BrewKind GROTESQUE = hybrid("grotesque", 0x73566B, List.of(
        new BrewEffectSpec("minecraft:resistance", 1_200, 0)
    ), 4.0F, 1.0F, BrewBehavior.APPLY_GROTESQUE);
    public static final BrewKind SOLIDIFY_STONE = world(
        "solidify_stone", 0x777777, 4.0F, 1.0F, BrewBehavior.SOLIDIFY_STONE
    );
    public static final BrewKind SOLIDIFY_DIRT = world(
        "solidify_dirt", 0x79553A, 4.0F, 1.0F, BrewBehavior.SOLIDIFY_DIRT
    );
    public static final BrewKind SOLIDIFY_SAND = world(
        "solidify_sand", 0xD8C786, 4.0F, 1.0F, BrewBehavior.SOLIDIFY_SAND
    );
    public static final BrewKind SOLIDIFY_SANDSTONE = world(
        "solidify_sandstone", 0xC9AF70, 4.0F, 1.0F, BrewBehavior.SOLIDIFY_SANDSTONE
    );
    public static final BrewKind SOLIDIFY_EROSION = world(
        "solidify_erosion", 0x8E8B82, 4.0F, 1.0F, BrewBehavior.SOLIDIFY_EROSION
    );

    private static final List<BrewKind> BUILT_INS = List.of(
        HEAL, HARM, ABSORPTION, HEALTH_BOOST, REGENERATION, DAMAGE_BOOST, FAST_MOVEMENT, SLOW_MOVEMENT,
        JUMP, FLOATING, SLOW_FALL, BLINDNESS, INVISIBLE, NIGHT_VISION, WATER_BREATHING, SWIM_SPEED,
        FIRE_RESISTANCE, POISON, WITHER, WEAKNESS, FULLNESS, PARALYSIS, AIR_HIKE, FERTILIZE, GROW_FLOWERS,
        GROW_SAPLING, EXTINGUISH_FIRES, FREEZE, WEBS, FLAMES, BLAST, PUSH, PULL, ANIMAL_ATTRACTION,
        ANIMAL_REPULSION, FELL_TREE, PRUNE_LEAVES, HARVEST, TILL_LAND, REVEALING, REMOVE_BUFFS,
        REMOVE_DEBUFFS, STOUT_BELLY, HARM_WEREWOLVES, WEAKEN_VAMPIRES, DEMONBANE,
        BATS, BLIGHT, EROSION, FEAR, FROGS_TONGUE, FROST, GROW_LILY, ICE_SHELL, ICE_WORLD,
        INFECTION, INFERNO, INK, INSECT_BANE, LEVEL_LAND, LOVE, OVERHEATING, PULVERIZE_ROCK,
        RAISE_LAND, RAISING, SINKING, SNOW_BURST, SPREAD_DEBUFFS, STEAL_BUFFS, THORNS, TRANSPOSE,
        TRANSPOSE_ORE, UNDEAD_BANE, UNDEADS_CURSE, VINES, WASTING,
        BAT_BURST, MURDEROUS_FLOCK, CACTUS_THORNED, COMBUSTION, DISEASE, DISSIPATE_GAS, DRAIN_MAGIC,
        DURATION_BOOST, ENDLESS_WATER, FORTUNE, FROGS_LEG, GRUES_PREY, MOONSHINE,
        PART_LAVA, PART_WATER, PLANTING, POISON_TOAD, RAISE_DEAD, VINES_FLAMMABLE, VOLATILITY,
        ABSORB_MAGIC, ATTRACT_ARROWS, BOTTLING, GAS_IMMUNITY, ENDER_INHIBITION, ILL_FITTING,
        INSANITY, KEEP_EFFECTS, KEEP_INVENTORY, NIGHTMARE, POISON_WEAPON, REFLECT_ARROWS,
        REFLECT_DAMAGE, REINCARNATE, REPEL_ATTACKER, RESIZING, SHIFTING_SEASONS,
        SUMMON_ABYSSAL_REGENT, TINT_SKIN, WEREWOLF_LOCK, BODEGA, CURSED_LEAPING, SLEEPING,
        SPROUTING, SUBSTITUTION, DEPTHS, GROTESQUE, SOLIDIFY_STONE, SOLIDIFY_DIRT,
        SOLIDIFY_SAND, SOLIDIFY_SANDSTONE, SOLIDIFY_EROSION
    );
    private static final Map<String, BrewKind> BY_ID = BUILT_INS.stream()
        .collect(Collectors.toUnmodifiableMap(BrewKind::id, Function.identity()));

    public BrewKind {
        id = Objects.requireNonNull(id, "id").strip();
        if (id.isEmpty() || !id.matches("[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid brew id: " + id);
        }
        if (color < 0 || color > 0xFFFFFF) {
            throw new IllegalArgumentException("Brew color must be a 24-bit RGB value");
        }
        effects = List.copyOf(effects);
        behaviors = List.copyOf(behaviors);
        if (radius < 0.5F || radius > 12.0F) {
            throw new IllegalArgumentException("Brew radius must be from 0.5 to 12.0");
        }
        if (potency < 0.1F || potency > 8.0F) {
            throw new IllegalArgumentException("Brew potency must be from 0.1 to 8.0");
        }
    }

    public static List<BrewKind> builtIns() {
        return BUILT_INS;
    }

    public static Optional<BrewKind> find(final String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static BrewKind require(final String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown brew: " + id));
    }

    public boolean hasPotionEffects() {
        return !effects.isEmpty();
    }

    public int fuelBurnTime() {
        return this == COMBUSTION ? 2_400 : 0;
    }

    public boolean returnsAfterImpact() {
        return this == ENDLESS_WATER;
    }

    public boolean recoversOnMiss() {
        return this == VINES || this == THORNS || this == BODEGA;
    }

    public PotionContents potionContents() {
        final List<MobEffectInstance> resolved = effects.stream().map(BrewEffectSpec::resolve).toList();
        return new PotionContents(Optional.empty(), Optional.of(color), resolved, Optional.empty());
    }

    private static BrewKind effect(
        final String id,
        final int color,
        final String effect,
        final int duration,
        final int amplifier
    ) {
        return effects(id, color, List.of(new BrewEffectSpec(effect, duration, amplifier)));
    }

    private static BrewKind effects(final String id, final int color, final List<BrewEffectSpec> effects) {
        return hybrid(id, color, effects, 4.0F, 1.0F);
    }

    private static BrewKind world(
        final String id,
        final int color,
        final float radius,
        final float potency,
        final BrewBehavior... behaviors
    ) {
        return new BrewKind(id, color, List.of(), List.of(behaviors), radius, potency);
    }

    private static BrewKind hybrid(
        final String id,
        final int color,
        final List<BrewEffectSpec> effects,
        final float radius,
        final float potency,
        final BrewBehavior... behaviors
    ) {
        return new BrewKind(id, color, effects, List.of(behaviors), radius, potency);
    }
}
