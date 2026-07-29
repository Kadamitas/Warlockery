package com.kadamitas.warlockery.brew;

final class BrewWorldRestorationRules {
    private BrewWorldRestorationRules() {
    }

    static ExpiredCellAction decide(final boolean loaded, final boolean temporaryStatePresent) {
        if (!loaded) {
            return ExpiredCellAction.RETAIN;
        }
        return temporaryStatePresent ? ExpiredCellAction.RESTORE : ExpiredCellAction.DROP;
    }

    enum ExpiredCellAction {
        RETAIN,
        DROP,
        RESTORE
    }
}
