package com.kadamitas.warlockery.brew;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import java.util.Optional;

public enum BrewMarkerKind implements StringIdentified {
    ABSORB_MAGIC("absorb_magic", 2_400),
    ATTRACT_ARROWS("attract_arrows", 1_200),
    BREW_GAS_IMMUNITY("brew_gas_immunity", 3_600),
    ENDER_INHIBITION("ender_inhibition", 1_200),
    ILL_FITTING("ill_fitting", 1_200),
    KEEP_EFFECTS("keep_effects", 12_000),
    KEEP_INVENTORY("keep_inventory", 12_000),
    POISON_WEAPON("poison_weapon", 2_400),
    REFLECT_ARROWS("reflect_arrows", 1_200),
    REFLECT_DAMAGE("reflect_damage", 1_200),
    REINCARNATE("reincarnate", 12_000),
    REPEL_ATTACKER("repel_attacker", 1_200),
    RESIZING("resizing", 1_200),
    TINT_SKIN("tint_skin", 1_200),
    WEREWOLF_LOCK("werewolf_lock", 12_000),
    VOLATILITY("volatility", 12_000),
    DISEASE("disease", 1_200),
    INFECTION("infection", 1_200),
    SINKING("sinking", 1_200),
    SUNLIGHT_CURSE("sunlight_curse", 2_400),
    CURSED_LEAPING("cursed_leaping", 1_200),
    EROSION("erosion", 600),
    FEAR("fear", 1_200),
    GRUES_PREY("grues_prey", 1_200),
    OVERHEATING("overheating", 1_200),
    SLEEPING("sleeping", 6_000),
    SNOW_TRAIL("snow_trail", 1_200),
    DEPTHS("depths", 3_600),
    MOONSHINE("moonshine", 3_600),
    GROTESQUE("grotesque", 1_200);

    private static final EnumLookup<BrewMarkerKind> LOOKUP = EnumLookup.create("brew marker", values());

    private final String id;
    private final int defaultDuration;

    BrewMarkerKind(final String id, final int defaultDuration) {
        this.id = id;
        this.defaultDuration = defaultDuration;
    }

    public String id() {
        return id;
    }

    public int defaultDuration() {
        return defaultDuration;
    }

    public static Optional<BrewMarkerKind> find(final String id) {
        return LOOKUP.find(id);
    }
}
