package com.kadamitas.warlockery.block;

import java.util.Optional;

final class PlantMineRules {
    private PlantMineRules() {
    }

    static Diagnostic diagnostic(
        final PlantMinePayload current,
        final Optional<PlantMinePayload> offered,
        final boolean emptyHand
    ) {
        if (emptyHand) {
            return current.isArmed() ? Diagnostic.READY : Diagnostic.UNARMED;
        }
        if (offered.isEmpty()) {
            return Diagnostic.WRONG;
        }
        return !current.isArmed() || offered.orElseThrow() == current ? Diagnostic.READY : Diagnostic.WRONG;
    }

    static boolean canTrigger(
        final PlantMinePayload payload,
        final boolean living,
        final boolean alive,
        final boolean immune,
        final boolean spectator
    ) {
        return payload.isArmed() && canAffect(living, alive, immune, spectator);
    }

    static boolean canAffect(
        final boolean living,
        final boolean alive,
        final boolean immune,
        final boolean spectator
    ) {
        return living && alive && !immune && !spectator;
    }

    static boolean canGrowVegetation(
        final boolean tagged,
        final boolean bonemealable,
        final boolean validTarget,
        final boolean successful
    ) {
        return tagged && bonemealable && validTarget && successful;
    }

    static boolean canPlaceTerrain(
        final boolean replaceable,
        final boolean dry,
        final boolean unoccupied,
        final boolean withinRadius
    ) {
        return replaceable && dry && unoccupied && withinRadius;
    }

    static boolean canPlaceThorn(
        final boolean replaceable,
        final boolean taggedGround,
        final boolean survives,
        final boolean dry,
        final boolean unoccupied
    ) {
        return replaceable && taggedGround && survives && dry && unoccupied;
    }

    static boolean canPlaceWeb(final boolean replaceable, final boolean dry, final boolean blockEntityFree) {
        return replaceable && dry && blockEntityFree;
    }

    enum Diagnostic {
        UNARMED,
        WRONG,
        READY
    }
}
