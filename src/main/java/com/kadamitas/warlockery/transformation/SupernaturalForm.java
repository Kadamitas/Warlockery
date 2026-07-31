package com.kadamitas.warlockery.transformation;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import java.util.Locale;

public enum SupernaturalForm implements StringIdentified {
    NONE(0.0, 0.0F, 0),
    VAMPIRE(8.0, 0.55F, 125),
    WEREWOLF(4.0, 0.7F, 100);

    private static final EnumLookup<SupernaturalForm> LOOKUP = EnumLookup.create("supernatural form", values());

    private final double damageReserveMultiplier;
    private final float maximumDamageReduction;
    private final int deathWardCost;

    SupernaturalForm(
        final double damageReserveMultiplier,
        final float maximumDamageReduction,
        final int deathWardCost
    ) {
        this.damageReserveMultiplier = damageReserveMultiplier;
        this.maximumDamageReduction = maximumDamageReduction;
        this.deathWardCost = deathWardCost;
    }

    public double damageReserveMultiplier() {
        return damageReserveMultiplier;
    }

    public float maximumDamageReduction() {
        return maximumDamageReduction;
    }

    public int deathWardCost() {
        return deathWardCost;
    }

    @Override
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SupernaturalForm parse(final String value) {
        return LOOKUP.findOrElse(value.toLowerCase(Locale.ROOT), NONE);
    }
}
