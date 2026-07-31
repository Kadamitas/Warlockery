package com.kadamitas.warlockery.dream;

public final class SpiritManifestationRules {
    private SpiritManifestationRules() {
    }

    public static Decision enter(
        final boolean dreaming,
        final boolean inSpiritWorld,
        final boolean riteActive,
        final boolean alreadyManifested,
        final boolean destinationAvailable
    ) {
        if (!dreaming || !inSpiritWorld) {
            return Decision.NOT_IN_SPIRIT_WORLD;
        }
        if (!riteActive) {
            return Decision.MISSING_RITE;
        }
        if (alreadyManifested) {
            return Decision.ALREADY_MANIFESTED;
        }
        return destinationAvailable ? Decision.READY : Decision.DESTINATION_UNAVAILABLE;
    }

    public static boolean expired(final long serverTick, final long expiration) {
        return expiration <= serverTick;
    }

    public static long extend(final long currentExpiration, final long offeredExpiration) {
        return Math.max(currentExpiration, offeredExpiration);
    }

    public enum Decision {
        READY("ready"),
        NOT_IN_SPIRIT_WORLD("not_in_spirit_world"),
        MISSING_RITE("missing_rite"),
        ALREADY_MANIFESTED("already_manifested"),
        DESTINATION_UNAVAILABLE("destination_unavailable");

        private final String id;

        Decision(final String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public boolean ready() {
            return this == READY;
        }
    }
}
