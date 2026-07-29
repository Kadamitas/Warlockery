package com.kadamitas.warlockery.entity;

public final class HobgoblinWorkRules {
    private HobgoblinWorkRules() {
    }

    public static boolean canWork(
        final boolean contracted,
        final boolean trading,
        final boolean noAi,
        final boolean mobGriefing
    ) {
        return contracted && !trading && !noAi && mobGriefing;
    }

    public static WorkAction nextAction(
        final boolean hasCargo,
        final boolean depositContainer,
        final boolean looseItem
    ) {
        if (hasCargo && depositContainer) {
            return WorkAction.DEPOSIT;
        }
        return looseItem ? WorkAction.COLLECT : WorkAction.IDLE;
    }

    public enum WorkAction {
        IDLE,
        COLLECT,
        DEPOSIT
    }
}
