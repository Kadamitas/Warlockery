package com.kadamitas.warlockery.item;

public final class VampiricDollRules {
    private VampiricDollRules() {
    }

    public static TransferPlan plan(
        final float incomingDamage,
        final boolean victimAvailable,
        final boolean victimGuarded
    ) {
        if (incomingDamage <= 0.0F || !victimAvailable) {
            return new TransferPlan(incomingDamage, 0.0F, Diagnostic.NO_VICTIM);
        }
        if (victimGuarded) {
            return new TransferPlan(incomingDamage, 0.0F, Diagnostic.BLOCKED);
        }
        final float transferred = incomingDamage * 0.5F;
        return new TransferPlan(incomingDamage - transferred, transferred, Diagnostic.TRANSFERRED);
    }

    public enum Diagnostic {
        NO_VICTIM("no_victim"),
        BLOCKED("blocked"),
        TRANSFERRED("transferred");

        private final String id;

        Diagnostic(final String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public record TransferPlan(float protectedDamage, float victimDamage, Diagnostic diagnostic) {
        public String messageKey() {
            return "message.warlockery.doll.vampiric." + diagnostic.id();
        }
    }
}
