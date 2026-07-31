package com.kadamitas.warlockery.util;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;

public final class DataParsing {
    private DataParsing() {
    }

    public static Optional<UUID> uuid(final String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<Identifier> identifier(final String value) {
        return value == null || value.isBlank()
            ? Optional.empty()
            : Optional.ofNullable(Identifier.tryParse(value));
    }
}
