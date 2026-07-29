package com.kadamitas.warlockery.mutation;

public enum AdvancedMutationKind {
    TOAD("Toad"),
    MINEDRAKE("Dreamroot");

    private final String displayName;

    AdvancedMutationKind(final String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String translationKey() {
        return "message.warlockery.advanced_mutation.kind." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
