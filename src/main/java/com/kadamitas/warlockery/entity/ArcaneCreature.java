package com.kadamitas.warlockery.entity;

public interface ArcaneCreature {
    CreatureKind creatureKind();

    enum CreatureKind {
        HEX_BAT, HEDGE_CRONE, BANSHEE, CAT, CORPSE, CIRCLE_MAGE, UMBRAL_SIGIL, DEATH,
        PALE_STEED, DEMON, ENT, ELDRITCH_WATCHER, FAMILIAR, BLOOD_THRALL, FORGEWARDEN, HELLHOUND,
        HOBGOBLIN, THORNED_PURSUER, ILLUSION_CREEPER, ILLUSION_SPIDER,
        ILLUSION_ZOMBIE, IMP, EMBERHORN_ARCHFIEND, CRIMSON_MATRIARCH, ABYSSAL_REGENT, LOST_SOUL, LOUSE,
        MANDRAKE, DREAMROOT, GLASS_DOPPELGANGER, STONEBROKER, NIGHTMARE, OWL, POLTERGEIST,
        ECHO_SHADE, SPECTRE, SPIRIT, TOAD, BRAMBLE_COLOSSUS, VAMPIRE, IRONBOUND_SENTINEL,
        LYCAN_VILLAGER, WEREWOLF, STORM_SIMIAN, WEREWOLF_HUNTER;

        public boolean isSupernatural() {
            return isVampiric() || this == WEREWOLF || this == LYCAN_VILLAGER;
        }

        public boolean isVampiric() {
            return this == VAMPIRE || this == CRIMSON_MATRIARCH || this == BLOOD_THRALL;
        }

        public boolean isDemonic() {
            return switch (this) {
                case DEMON, IMP, EMBERHORN_ARCHFIEND, ABYSSAL_REGENT, HELLHOUND -> true;
                default -> false;
            };
        }

        public boolean isUndead() {
            return switch (this) {
                case BANSHEE, CORPSE, DEATH, LOST_SOUL, POLTERGEIST, ECHO_SHADE, SPECTRE, SPIRIT -> true;
                default -> isVampiric();
            };
        }

        public boolean isWoodenVulnerable() {
            return this == ENT || this == BRAMBLE_COLOSSUS || this == THORNED_PURSUER;
        }
    }
}
