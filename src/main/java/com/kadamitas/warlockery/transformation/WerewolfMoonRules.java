package com.kadamitas.warlockery.transformation;

public final class WerewolfMoonRules {
    private WerewolfMoonRules() {
    }

    public static boolean forcesWolfForm(
        final int werewolfLevel,
        final boolean fullMoonNight,
        final boolean moonCharm
    ) {
        if (werewolfLevel < 0 || werewolfLevel > WerewolfProgressionRules.MAX_LEVEL) {
            throw new IllegalArgumentException("Werewolf level must be between 0 and 10");
        }
        return werewolfLevel >= 1 && fullMoonNight && !moonCharm;
    }
}
