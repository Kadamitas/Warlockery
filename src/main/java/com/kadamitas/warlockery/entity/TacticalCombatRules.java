package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.Objects;

public final class TacticalCombatRules {
    private TacticalCombatRules() {
    }

    public static Profile profile(final CreatureKind kind) {
        Objects.requireNonNull(kind, "kind");
        final Doctrine doctrine = switch (kind) {
            case HEDGE_CRONE, CIRCLE_MAGE, ELDRITCH_WATCHER, THORNED_PURSUER, WEREWOLF_HUNTER ->
                Doctrine.RANGED;
            case HEX_BAT, BANSHEE, IMP, LOST_SOUL, OWL, POLTERGEIST, SPIRIT, STORM_SIMIAN ->
                Doctrine.AERIAL;
            case CAT, FAMILIAR, HELLHOUND, ILLUSION_SPIDER, LYCAN_VILLAGER, NIGHTMARE, PALE_STEED,
                WEREWOLF -> Doctrine.PACK;
            case GOBLIN, HOBGOBLIN -> Doctrine.SKIRMISHER;
            case BLOOD_THRALL, DEATH, ECHO_SHADE, GLASS_DOPPELGANGER, NAAMAH, SPECTRE, UMBRAL_SIGIL,
                VAMPIRE -> Doctrine.STALKER;
            case BRAMBLE_COLOSSUS, DREAMROOT, ENT, FORGEWARDEN, IRONBOUND_SENTINEL, STONEBROKER ->
                Doctrine.GUARD;
            case ABYSSAL_REGENT, CORPSE, DEMON, EMBERHORN_ARCHFIEND, ILLUSION_CREEPER,
                ILLUSION_ZOMBIE -> Doctrine.BRUTE;
            case LOUSE, MANDRAKE, TOAD -> Doctrine.TIMID;
        };
        return switch (doctrine) {
            case RANGED -> new Profile(doctrine, 12, 11.0, 7, 0.25F, 1.12, true, true);
            case AERIAL -> new Profile(doctrine, 10, 8.0, 6, 0.20F, 1.20, false, true);
            case PACK -> new Profile(doctrine, 8, 2.5, 5, 0.15F, 1.28, false, true);
            case SKIRMISHER -> new Profile(doctrine, 10, 4.5, 7, 0.30F, 1.16, true, true);
            case STALKER -> new Profile(doctrine, 10, 3.0, 6, 0.20F, 1.22, true, true);
            case GUARD -> new Profile(doctrine, 16, 2.5, 4, 0.10F, 1.00, false, false);
            case BRUTE -> new Profile(doctrine, 12, 2.0, 5, 0.10F, 1.15, false, true);
            case TIMID -> new Profile(doctrine, 10, 8.0, 8, 0.55F, 1.30, true, false);
        };
    }

    public static boolean shouldReconsider(final int tickCount, final int entityId, final int cadenceTicks) {
        if (cadenceTicks < 1) {
            throw new IllegalArgumentException("Tactical cadence must be positive");
        }
        return Math.floorMod(tickCount + entityId, cadenceTicks) == 0;
    }

    public static Maneuver choose(
        final Profile profile,
        final boolean directedThreat,
        final boolean visibleToTarget,
        final boolean routeReachable,
        final double distance,
        final float health,
        final float maximumHealth
    ) {
        Objects.requireNonNull(profile, "profile");
        if (!Double.isFinite(distance) || distance < 0.0 || maximumHealth <= 0.0F) {
            throw new IllegalArgumentException("Tactical decisions require finite distances and positive health capacity");
        }
        if (!routeReachable && visibleToTarget) {
            return Maneuver.DISENGAGE;
        }
        if (health / maximumHealth <= profile.retreatHealthFraction()) {
            return profile.usesCover() ? Maneuver.COVER : Maneuver.DISENGAGE;
        }
        if (directedThreat && visibleToTarget && profile.usesCover()) {
            return Maneuver.COVER;
        }
        return switch (profile.doctrine()) {
            case RANGED -> distance < profile.preferredDistance() * 0.65
                ? Maneuver.DISENGAGE
                : distance > profile.preferredDistance() * 1.35 ? Maneuver.PRESS : Maneuver.FLANK;
            case AERIAL, PACK, SKIRMISHER, STALKER -> profile.flanks() ? Maneuver.FLANK : Maneuver.PRESS;
            case GUARD -> distance > profile.preferredDistance() * 2.0 ? Maneuver.PRESS : Maneuver.HOLD;
            case BRUTE -> Maneuver.PRESS;
            case TIMID -> Maneuver.DISENGAGE;
        };
    }

    public static int flankSide(final int entityId) {
        return Math.floorMod(entityId, 2) == 0 ? -1 : 1;
    }

    public enum Doctrine {
        RANGED,
        AERIAL,
        PACK,
        SKIRMISHER,
        STALKER,
        GUARD,
        BRUTE,
        TIMID
    }

    public enum Maneuver {
        COVER,
        DISENGAGE,
        FLANK,
        PRESS,
        HOLD
    }

    public record Profile(
        Doctrine doctrine,
        int cadenceTicks,
        double preferredDistance,
        int coverSearchRadius,
        float retreatHealthFraction,
        double movementSpeed,
        boolean usesCover,
        boolean flanks
    ) {
        public Profile {
            Objects.requireNonNull(doctrine, "doctrine");
            if (cadenceTicks < 1 || preferredDistance <= 0.0 || coverSearchRadius < 1
                || retreatHealthFraction < 0.0F || retreatHealthFraction > 1.0F || movementSpeed <= 0.0) {
                throw new IllegalArgumentException("Invalid tactical combat profile");
            }
        }
    }
}
