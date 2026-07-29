package com.kadamitas.warlockery.item;

public final class HunterArmorRules {
    private HunterArmorRules() {
    }

    public static Resolution resolve(
        final boolean dawnSet,
        final boolean silveredSet,
        final boolean baseSet,
        final boolean magicalDamage,
        final boolean werewolfAttacker,
        final boolean vampireAttacker
    ) {
        if (dawnSet && (werewolfAttacker || vampireAttacker)) {
            return new Resolution(0.25F, werewolfAttacker);
        }
        if (silveredSet && werewolfAttacker) {
            return new Resolution(0.4F, true);
        }
        if ((baseSet || silveredSet || dawnSet) && magicalDamage) {
            return new Resolution(0.5F, false);
        }
        return Resolution.NONE;
    }

    public static boolean blocksHex(final boolean completeHunterSet) {
        return completeHunterSet;
    }

    public record Resolution(float damageMultiplier, boolean burnsAttacker) {
        public static final Resolution NONE = new Resolution(1.0F, false);

        public Resolution {
            if (damageMultiplier < 0.0F || damageMultiplier > 1.0F) {
                throw new IllegalArgumentException("Damage multiplier must be between zero and one");
            }
        }

        public boolean protectedDamage() {
            return damageMultiplier < 1.0F;
        }
    }
}
