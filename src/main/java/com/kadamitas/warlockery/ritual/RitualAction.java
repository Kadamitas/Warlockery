package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import java.util.Optional;

public enum RitualAction implements StringIdentified {
    EFFECT("effect", Outcome.EFFECT),
    STORM("storm", Outcome.WEATHER),
    CLEAR_WEATHER("clear_weather", Outcome.WEATHER),
    FERTILITY("fertility", Outcome.GROWTH),
    FORESTATION("forestation", Outcome.GROWTH),
    NATURES_POWER("natures_power", Outcome.GROWTH),
    BLIGHT("blight", Outcome.DECAY),
    TOAD_RAIN("toad_rain", Outcome.ENTITY_SUMMON),
    BANISH("banish", Outcome.BANISHMENT),
    CALL_BEASTS("call_beasts", Outcome.ENTITY_MOVEMENT),
    CALL_FAMILIAR("call_familiar", Outcome.ENTITY_MOVEMENT),
    ANGUISH_UNDEAD("anguish_undead", Outcome.UNDEAD_CONTROL),
    DRAIN_GROWTH("drain_growth", Outcome.GROWTH_TRANSFER),
    FORTIFY_UNDEAD("fortify_undead", Outcome.UNDEAD_CONTROL),
    GRAVEYARD_MIST("graveyard_mist", Outcome.UNDEAD_CONTROL),
    SUMMON_ENTITY("summon_entity", Outcome.ENTITY_SUMMON),
    SUMMON_HUNTSMAN("summon_huntsman", Outcome.ENTITY_SUMMON),
    SUMMON_ITEM("summon_item", Outcome.ITEM_SUMMON),
    RAISE_COLUMN("raise_column", Outcome.TERRAIN),
    CRATER("crater", Outcome.TERRAIN),
    BROKEN_EARTH("broken_earth", Outcome.TERRAIN),
    EARTHS_WRATH("earths_wrath", Outcome.TERRAIN),
    SKYS_WRATH("skys_wrath", Outcome.WEATHER_AND_EFFECT),
    HELL_ON_EARTH("hell_on_earth", Outcome.ENTITY_SUMMON),
    COOK("cook", Outcome.ITEM_CONVERSION),
    ECLIPSE("eclipse", Outcome.WEATHER_AND_EFFECT),
    REMOVE_VAMPIRISM("remove_vampirism", Outcome.SUPERNATURAL_CURE),
    TRANSFORM_NAMI("transform_nami", Outcome.SUPERNATURAL_TRANSFORMATION),
    TRANSFORM_WEREWOLF("transform_werewolf", Outcome.SUPERNATURAL_TRANSFORMATION),
    REMOVE_WEREWOLF("remove_werewolf", Outcome.SUPERNATURAL_CURE),
    HEX("hex", Outcome.HEX),
    CLEANSE("cleanse", Outcome.CLEANSE),
    BIND_CIRCLE("bind_circle", Outcome.ITEM_BINDING),
    BIND_WAYSTONE("bind_waystone", Outcome.ITEM_BINDING),
    COPY_WAYSTONE("copy_waystone", Outcome.ITEM_BINDING),
    TELEPORT_WAYSTONE("teleport_waystone", Outcome.TELEPORTATION),
    TELEPORT_ENTITY("teleport_entity", Outcome.TELEPORTATION),
    TRANSPOSE_ORE("transpose_ore", Outcome.ITEM_CONVERSION),
    ICE_SPHERE("ice_sphere", Outcome.TERRAIN),
    MANIFEST("manifest", Outcome.EFFECT),
    IMPRISONMENT_WARD("imprisonment_ward", Outcome.WARD),
    PROTECTION_WARD("protection_ward", Outcome.WARD),
    SANCTITY_WARD("sanctity_ward", Outcome.WARD),
    CLIMATE_SHIFT("climate_shift", Outcome.BIOME),
    PRIOR_INCARNATION("prior_incarnation", Outcome.ITEM_SUMMON),
    INFUSE_PATH("infuse_path", Outcome.INFUSION),
    RECHARGE_PATH("recharge_path", Outcome.INFUSION),
    BIND_ENTITY("bind_entity", Outcome.ITEM_BINDING),
    BIND_FETISH("bind_fetish", Outcome.ITEM_BINDING),
    BIND_ITEM("bind_item", Outcome.ITEM_BINDING),
    MARRIAGE("marriage", Outcome.BOND),
    DIVORCE("divorce", Outcome.BOND),
    GLYPH_TRANSFORM("glyph_transform", Outcome.TERRAIN);

    private static final EnumLookup<RitualAction> LOOKUP = EnumLookup.create("ritual action", values());

    private final String id;
    private final Outcome outcome;

    RitualAction(final String id, final Outcome outcome) {
        this.id = id;
        this.outcome = outcome;
    }

    public String id() {
        return id;
    }

    public Outcome outcome() {
        return outcome;
    }

    public static Optional<RitualAction> find(final String id) {
        return LOOKUP.find(id);
    }

    public static RitualAction require(final String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown ritual action: " + id));
    }

    public enum Outcome {
        EFFECT,
        WEATHER,
        GROWTH,
        DECAY,
        BANISHMENT,
        ENTITY_SUMMON,
        ITEM_SUMMON,
        TERRAIN,
        ITEM_CONVERSION,
        WEATHER_AND_EFFECT,
        SUPERNATURAL_CURE,
        SUPERNATURAL_TRANSFORMATION,
        HEX,
        CLEANSE,
        ITEM_BINDING,
        TELEPORTATION,
        ENTITY_MOVEMENT,
        UNDEAD_CONTROL,
        GROWTH_TRANSFER,
        WARD,
        BIOME,
        INFUSION,
        BOND
    }
}
