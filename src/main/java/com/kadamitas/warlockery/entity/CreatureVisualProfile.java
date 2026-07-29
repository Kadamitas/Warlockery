package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.Objects;

public record CreatureVisualProfile(
    float width,
    float height,
    Archetype archetype
) {
    public CreatureVisualProfile {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0.0F || height <= 0.0F) {
            throw new IllegalArgumentException("Creature dimensions must be finite and positive");
        }
        Objects.requireNonNull(archetype, "archetype");
    }

    public static CreatureVisualProfile forKind(final CreatureKind kind) {
        return switch (kind) {
            case HEX_BAT -> profile(0.5F, 0.45F, Archetype.SPIRIT);
            case BANSHEE -> profile(0.65F, 1.8F, Archetype.SPIRIT);
            case UMBRAL_SIGIL -> profile(0.7F, 0.7F, Archetype.SPIRIT);
            case ELDRITCH_WATCHER -> profile(0.8F, 1.1F, Archetype.SPIRIT);
            case FAMILIAR -> profile(0.6F, 0.8F, Archetype.SPIRIT);
            case IMP, POLTERGEIST -> profile(0.6F, 0.9F, Archetype.SPIRIT);
            case SPECTRE -> profile(0.65F, 1.8F, Archetype.SPIRIT);
            case SPIRIT, LOST_SOUL -> profile(0.55F, 0.9F, Archetype.SPIRIT);
            case STORM_SIMIAN -> profile(0.8F, 0.9F, Archetype.SPIRIT);
            case CAT -> profile(0.6F, 0.7F, Archetype.FELINE);
            case OWL -> profile(0.65F, 0.8F, Archetype.AVIAN);
            case TOAD -> profile(0.55F, 0.45F, Archetype.AMPHIBIAN);
            case PALE_STEED, NIGHTMARE -> profile(1.4F, 1.6F, Archetype.MOUNT);
            case HELLHOUND -> profile(0.8F, 0.95F, Archetype.CANINE);
            case LOUSE -> profile(0.45F, 0.35F, Archetype.ARTHROPOD);
            case ILLUSION_SPIDER -> profile(1.4F, 0.9F, Archetype.ARTHROPOD);
            case ILLUSION_CREEPER -> profile(0.6F, 1.7F, Archetype.CREEPER);
            case MANDRAKE -> profile(0.55F, 0.85F, Archetype.PLANTLING);
            case DREAMROOT -> profile(0.9F, 1.7F, Archetype.PLANT_BRUTE);
            case BRAMBLE_COLOSSUS -> profile(1.3F, 2.6F, Archetype.PLANT_BRUTE);
            case ENT -> profile(1.35F, 2.9F, Archetype.PLANT_BRUTE);
            case WEREWOLF, LYCAN_VILLAGER -> profile(0.85F, 2.15F, Archetype.LYCAN);
            case HEDGE_CRONE -> profile(0.65F, 2.0F, Archetype.BOSS);
            case DEATH -> profile(0.75F, 2.3F, Archetype.BOSS);
            case DEMON -> profile(0.8F, 2.2F, Archetype.BOSS);
            case FORGEWARDEN, STONEBROKER -> profile(0.8F, 2.1F, Archetype.BOSS);
            case THORNED_PURSUER -> profile(0.9F, 2.4F, Archetype.BOSS);
            case EMBERHORN_ARCHFIEND, ABYSSAL_REGENT -> profile(0.9F, 2.4F, Archetype.BOSS);
            case CRIMSON_MATRIARCH -> profile(0.7F, 2.1F, Archetype.BOSS);
            case IRONBOUND_SENTINEL -> profile(1.0F, 2.5F, Archetype.BOSS);
            case HOBGOBLIN -> profile(0.55F, 1.55F, Archetype.HUMANOID);
            case WEREWOLF_HUNTER -> profile(0.6F, 1.95F, Archetype.HUMANOID);
            case CORPSE, CIRCLE_MAGE, BLOOD_THRALL, ILLUSION_ZOMBIE, GLASS_DOPPELGANGER,
                 ECHO_SHADE, VAMPIRE -> profile(0.6F, 1.95F, Archetype.HUMANOID);
        };
    }

    private static CreatureVisualProfile profile(
        final float width,
        final float height,
        final Archetype archetype
    ) {
        return new CreatureVisualProfile(width, height, archetype);
    }

    public enum Archetype {
        HUMANOID,
        BOSS,
        FELINE,
        AVIAN,
        AMPHIBIAN,
        MOUNT,
        CANINE,
        PLANTLING,
        PLANT_BRUTE,
        ARTHROPOD,
        CREEPER,
        LYCAN,
        SPIRIT
    }
}
