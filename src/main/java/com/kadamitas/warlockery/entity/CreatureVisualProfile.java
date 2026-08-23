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
            case HEX_BAT -> profile(0.5F, 0.63F, Archetype.SPIRIT);
            case BANSHEE -> profile(0.65F, 1.98F, Archetype.SPIRIT);
            case UMBRAL_SIGIL -> profile(0.7F, 1.44F, Archetype.SPIRIT);
            case ELDRITCH_WATCHER -> profile(0.8F, 1.35F, Archetype.SPIRIT);
            case FAMILIAR -> profile(0.6F, 1.08F, Archetype.SPIRIT);
            case IMP -> profile(0.65F, 0.99F, Archetype.IMP);
            case POLTERGEIST -> profile(0.6F, 1.62F, Archetype.SPIRIT);
            case SPECTRE -> profile(0.65F, 2.07F, Archetype.SPIRIT);
            case SPIRIT -> profile(0.55F, 1.8F, Archetype.SPIRIT);
            case LOST_SOUL -> profile(0.55F, 0.99F, Archetype.SPIRIT);
            case STORM_SIMIAN -> profile(0.85F, 0.9F, Archetype.SIMIAN);
            case CAT -> profile(0.6F, 0.81F, Archetype.FELINE);
            case OWL -> profile(0.65F, 0.81F, Archetype.AVIAN);
            case TOAD -> profile(0.55F, 0.45F, Archetype.AMPHIBIAN);
            case PALE_STEED -> profile(1.4F, 2.43F, Archetype.MOUNT);
            case NIGHTMARE -> profile(1.4F, 2.25F, Archetype.MOUNT);
            case HELLHOUND -> profile(0.8F, 1.35F, Archetype.CANINE);
            case LOUSE -> profile(0.45F, 0.36F, Archetype.ARTHROPOD);
            case ILLUSION_SPIDER -> profile(1.4F, 0.9F, Archetype.ARTHROPOD);
            case ILLUSION_CREEPER -> profile(0.6F, 1.71F, Archetype.CREEPER);
            case MANDRAKE -> profile(0.55F, 0.81F, Archetype.PLANTLING);
            case DREAMROOT -> profile(0.9F, 1.62F, Archetype.PLANT_BRUTE);
            case BRAMBLE_COLOSSUS -> profile(1.3F, 2.61F, Archetype.PLANT_BRUTE);
            case ENT -> profile(1.35F, 2.88F, Archetype.PLANT_BRUTE);
            case WEREWOLF, LYCAN_VILLAGER -> profile(0.85F, 1.8F, Archetype.LYCAN);
            case HEDGE_CRONE -> profile(0.65F, 2.7F, Archetype.BOSS);
            case DEATH -> profile(0.75F, 2.7F, Archetype.BOSS);
            case DEMON -> profile(0.8F, 2.7F, Archetype.BOSS);
            case FORGEWARDEN, STONEBROKER -> profile(0.8F, 2.7F, Archetype.BOSS);
            case THORNED_PURSUER -> profile(0.9F, 2.7F, Archetype.BOSS);
            case EMBERHORN_ARCHFIEND, ABYSSAL_REGENT -> profile(0.9F, 2.7F, Archetype.BOSS);
            case NAAMAH -> profile(0.7F, 2.7F, Archetype.BOSS);
            case IRONBOUND_SENTINEL -> profile(1.0F, 2.7F, Archetype.BOSS);
            case GOBLIN -> profile(0.45F, 1.08F, Archetype.HUMANOID);
            case HOBGOBLIN -> profile(0.5F, 1.26F, Archetype.HUMANOID);
            case WEREWOLF_HUNTER -> profile(0.6F, 1.8F, Archetype.HUMANOID);
            case CORPSE -> profile(0.6F, 1.71F, Archetype.HUMANOID);
            case CIRCLE_MAGE, ILLUSION_ZOMBIE, GLASS_DOPPELGANGER,
                 ECHO_SHADE -> profile(0.6F, 1.8F, Archetype.HUMANOID);
            case BLOOD_THRALL -> profile(0.6F, 1.62F, Archetype.HUMANOID);
            case VAMPIRE -> profile(0.6F, 1.89F, Archetype.HUMANOID);
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
        IMP,
        SIMIAN,
        SPIRIT
    }
}
