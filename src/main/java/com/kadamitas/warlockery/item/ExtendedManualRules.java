package com.kadamitas.warlockery.item;

public final class ExtendedManualRules {
    private ExtendedManualRules() {
    }

    public static Diagnostic diagnose(
        final boolean extendedBiomeEdition,
        final boolean crouching,
        final boolean hasPaper
    ) {
        if (!extendedBiomeEdition || !crouching) {
            return Diagnostic.READ_MANUAL;
        }
        return hasPaper ? Diagnostic.CREATE_BIOME_NOTE : Diagnostic.MISSING_PAPER;
    }

    public enum Diagnostic {
        READ_MANUAL,
        MISSING_PAPER,
        CREATE_BIOME_NOTE
    }
}
