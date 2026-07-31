package com.kadamitas.warlockery.entity;

public final class TreefydRules {
    private TreefydRules() {
    }

    public static boolean canAttack(
        final boolean owner,
        final boolean allowlisted,
        final boolean anotherTreefyd
    ) {
        return !owner && !allowlisted && !anotherTreefyd;
    }
}
