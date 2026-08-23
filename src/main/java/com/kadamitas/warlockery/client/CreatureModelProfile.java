package com.kadamitas.warlockery.client;

import com.kadamitas.warlockery.entity.CreatureVisualProfile;
import com.kadamitas.warlockery.entity.CreatureVisualProfile.Archetype;
import java.util.Locale;
import java.util.Objects;

record CreatureModelProfile(
    String entityId,
    CreatureVisualProfile visual,
    Archetype bodyPlan,
    Variant variant,
    int textureWidth,
    int textureHeight
) {
    CreatureModelProfile {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(visual, "visual");
        Objects.requireNonNull(bodyPlan, "bodyPlan");
        Objects.requireNonNull(variant, "variant");
        if (!variant.id().equals(entityId)) {
            throw new IllegalArgumentException("Model variant does not match entity id: " + entityId);
        }
        if (textureWidth <= 0 || textureHeight <= 0) {
            throw new IllegalArgumentException("Texture atlas dimensions must be positive");
        }
    }

    static CreatureModelProfile forEntity(final String entityId, final CreatureVisualProfile visual) {
        final Variant variant = Variant.fromId(entityId);
        return new CreatureModelProfile(
            entityId,
            visual,
            variant.bodyPlan(),
            variant,
            variant.textureWidth(),
            variant.textureHeight()
        );
    }

    enum Variant {
        ABYSSAL_REGENT,
        BANSHEE,
        BLOOD_THRALL,
        BRAMBLE_COLOSSUS,
        CIRCLE_MAGE,
        CORPSE,
        NAAMAH,
        DEATH,
        DEMON,
        DREAMROOT,
        ECHO_SHADE,
        ELDRITCH_WATCHER,
        EMBERHORN_ARCHFIEND,
        ENT,
        FAMILIAR_CAT,
        FERAL_LYCAN,
        FORGEWARDEN,
        GLASS_DOPPELGANGER,
        GOBLIN,
        HEDGE_CRONE,
        HELLHOUND,
        HEX_BAT,
        HOBGOBLIN,
        ILLUSION_CREEPER,
        ILLUSION_SPIDER,
        ILLUSION_ZOMBIE,
        IMP,
        IRONBOUND_SENTINEL,
        LOST_SOUL,
        LYCAN_VILLAGER,
        MANDRAKE,
        NIGHTMARE,
        OWL,
        PALE_STEED,
        PARASYTIC_LOUSE,
        POLTERGEIST,
        SPECTRAL_FAMILIAR,
        SPECTRE,
        SPIRIT,
        STONEBROKER,
        STORM_SIMIAN,
        THORNED_PURSUER,
        TOAD,
        UMBRAL_SIGIL,
        VAMPIRE,
        WEREWOLF,
        WEREWOLF_HUNTER;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        Archetype bodyPlan() {
            return switch (this) {
                case CIRCLE_MAGE, CORPSE, BLOOD_THRALL, ILLUSION_ZOMBIE, GLASS_DOPPELGANGER,
                     ECHO_SHADE, VAMPIRE, WEREWOLF_HUNTER, HOBGOBLIN, GOBLIN -> Archetype.HUMANOID;
                case HEDGE_CRONE, DEATH, DEMON, FORGEWARDEN, STONEBROKER, THORNED_PURSUER,
                     EMBERHORN_ARCHFIEND, ABYSSAL_REGENT, IRONBOUND_SENTINEL -> Archetype.BOSS;
                case BANSHEE, UMBRAL_SIGIL, ELDRITCH_WATCHER, POLTERGEIST, SPECTRE, SPIRIT,
                     LOST_SOUL -> Archetype.SPIRIT;
                case FAMILIAR_CAT, SPECTRAL_FAMILIAR -> Archetype.FELINE;
                case OWL, HEX_BAT -> Archetype.AVIAN;
                case TOAD -> Archetype.AMPHIBIAN;
                case PALE_STEED, NIGHTMARE -> Archetype.MOUNT;
                case HELLHOUND, FERAL_LYCAN -> Archetype.CANINE;
                case PARASYTIC_LOUSE, ILLUSION_SPIDER, NAAMAH -> Archetype.ARTHROPOD;
                case ILLUSION_CREEPER -> Archetype.CREEPER;
                case MANDRAKE -> Archetype.PLANTLING;
                case DREAMROOT, BRAMBLE_COLOSSUS, ENT -> Archetype.PLANT_BRUTE;
                case WEREWOLF, LYCAN_VILLAGER -> Archetype.LYCAN;
                case IMP -> Archetype.IMP;
                case STORM_SIMIAN -> Archetype.SIMIAN;
            };
        }

        int textureWidth() {
            return this == MANDRAKE || this == DREAMROOT ? 128 : 64;
        }

        int textureHeight() {
            return 64;
        }

        static Variant fromId(final String entityId) {
            try {
                return valueOf(entityId.toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException exception) {
                throw new IllegalArgumentException("No creature model profile for " + entityId, exception);
            }
        }
    }
}
