package com.kadamitas.warlockery.entity;

public final class FamiliarRecallRules {
    private FamiliarRecallRules() {
    }

    public static boolean eligible(
        final boolean familiarType,
        final boolean alive,
        final boolean ownedByCaster
    ) {
        return familiarType && alive && ownedByCaster;
    }
}
