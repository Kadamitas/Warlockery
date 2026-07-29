package com.kadamitas.warlockery.block;

final class BearTrapRules {
    private BearTrapRules() {
    }

    static BearTrapState nextState(final BearTrapState current) {
        return switch (current) {
            case DISARMED -> BearTrapState.ARMED;
            case ARMED -> BearTrapState.DISARMED;
            case SPRUNG -> BearTrapState.ARMED;
        };
    }

    static boolean canTrigger(
        final BearTrapState state,
        final boolean living,
        final boolean alive,
        final boolean immune,
        final boolean spectator
    ) {
        return state == BearTrapState.ARMED && eligible(living, alive, immune, spectator);
    }

    static boolean canRestrain(
        final BearTrapState state,
        final boolean living,
        final boolean alive,
        final boolean immune,
        final boolean spectator
    ) {
        return state == BearTrapState.SPRUNG && eligible(living, alive, immune, spectator);
    }

    private static boolean eligible(
        final boolean living,
        final boolean alive,
        final boolean immune,
        final boolean spectator
    ) {
        return living && alive && !immune && !spectator;
    }
}
