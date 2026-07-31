package com.kadamitas.warlockery.entity;

public final class FamiliarDeliveryRules {
    private FamiliarDeliveryRules() {
    }

    public static Diagnostic diagnose(
        final boolean owned,
        final boolean destinationBound,
        final boolean cargoPresent,
        final boolean destinationAvailable
    ) {
        if (!owned) {
            return Diagnostic.OWNER_REQUIRED;
        }
        if (!destinationBound) {
            return Diagnostic.MISSING_DESTINATION;
        }
        if (!cargoPresent) {
            return Diagnostic.MISSING_CARGO;
        }
        return destinationAvailable ? Diagnostic.READY : Diagnostic.DESTINATION_UNAVAILABLE;
    }

    public enum Diagnostic {
        OWNER_REQUIRED,
        MISSING_DESTINATION,
        MISSING_CARGO,
        DESTINATION_UNAVAILABLE,
        READY
    }
}
