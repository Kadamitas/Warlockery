package com.kadamitas.warlockery.transformation;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.dimension.DimensionType;

public final class VampireSunlightRules {
    public static final TagKey<DimensionType> SUNLIGHT = TagKey.create(
        Registries.DIMENSION_TYPE, Identifier.fromNamespaceAndPath("warlockery", "vampire_sunlight")
    );
    public static final TagKey<DimensionType> SUNLIGHT_EXEMPT = TagKey.create(
        Registries.DIMENSION_TYPE, Identifier.fromNamespaceAndPath("warlockery", "vampire_sunlight_exempt")
    );
    private VampireSunlightRules() {
    }

    public static boolean exposed(final Exposure exposure) {
        return exposure.sunlitDimension()
            && !exposure.exemptDimension()
            && exposure.localSkyVisible()
            && exposure.brightEnough()
            && exposure.headUncovered()
            && !exposure.wet()
            && !exposure.raining()
            && !exposure.inPowderSnow();
    }

    public static Protection protection(
        final int vampireLevel,
        final int blood,
        final int maximumBlood,
        final boolean chargeDue
    ) {
        if (!SupernaturalAbilityRules.resistsSun(vampireLevel) || blood <= 0) {
            return new Protection(false, 0);
        }
        if (!chargeDue) {
            return new Protection(true, 0);
        }
        final int cost = SupernaturalAbilityRules.sunlightBloodCost(vampireLevel, maximumBlood);
        return blood >= cost ? new Protection(true, cost) : new Protection(false, 0);
    }

    public record Exposure(
        boolean sunlitDimension,
        boolean exemptDimension,
        boolean localSkyVisible,
        boolean brightEnough,
        boolean headUncovered,
        boolean wet,
        boolean raining,
        boolean inPowderSnow
    ) {
    }

    public record Protection(boolean preventsDamage, int bloodCost) {
    }
}
