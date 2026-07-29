package com.kadamitas.warlockery.ritual.hex;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

public final class HexEntityMarkers {
    private static final String THREAT_KIND = "WarlockeryHexThreatKind";
    private static final String THREAT_TARGET = "WarlockeryHexThreatTarget";
    private static final String THREAT_EXPIRATION = "WarlockeryHexThreatExpiration";
    private static final String TOAD_ROLE = "WarlockeryHexToadRole";
    private static final String TOAD_EXPIRATION = "WarlockeryHexToadExpiration";
    private static final String TOAD_DETONATION = "WarlockeryHexToadDetonation";

    private HexEntityMarkers() {
    }

    public static void markThreat(
        final Entity entity,
        final HexKind kind,
        final UUID targetId,
        final long expiration
    ) {
        final CompoundTag data = entity.getPersistentData();
        data.putString(THREAT_KIND, kind.id());
        data.putString(THREAT_TARGET, targetId.toString());
        data.putLong(THREAT_EXPIRATION, expiration);
    }

    public static Optional<ThreatMarker> threat(final Entity entity) {
        final CompoundTag data = entity.getPersistentData();
        final String kindId = data.getStringOr(THREAT_KIND, "");
        final String targetId = data.getStringOr(THREAT_TARGET, "");
        if (kindId.isBlank() || targetId.isBlank()) {
            return Optional.empty();
        }
        try {
            return HexKind.find(kindId).map(kind -> new ThreatMarker(
                kind,
                UUID.fromString(targetId),
                data.getLongOr(THREAT_EXPIRATION, 0L)
            ));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static void markToad(
        final Entity entity,
        final ToadRainRules.ToadRole role,
        final long expiration,
        final long detonation
    ) {
        final CompoundTag data = entity.getPersistentData();
        data.putString(TOAD_ROLE, role.name());
        data.putLong(TOAD_EXPIRATION, expiration);
        data.putLong(TOAD_DETONATION, detonation);
    }

    public static Optional<ToadMarker> toad(final Entity entity) {
        final CompoundTag data = entity.getPersistentData();
        final String role = data.getStringOr(TOAD_ROLE, "");
        if (role.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ToadMarker(
                ToadRainRules.ToadRole.valueOf(role.toUpperCase(Locale.ROOT)),
                data.getLongOr(TOAD_EXPIRATION, 0L),
                data.getLongOr(TOAD_DETONATION, Long.MAX_VALUE)
            ));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static boolean isTemporary(final Entity entity) {
        return threat(entity).isPresent() || toad(entity).isPresent();
    }

    public record ThreatMarker(HexKind kind, UUID targetId, long expiration) {
    }

    public record ToadMarker(ToadRainRules.ToadRole role, long expiration, long detonation) {
    }
}
