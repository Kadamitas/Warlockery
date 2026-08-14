package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.WerewolfHunterRules.Confidence;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.Evidence;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.EvidenceType;
import com.kadamitas.warlockery.entity.WerewolfHunterRules.Intent;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public record WerewolfHunterState(
    int schemaVersion,
    List<Evidence> evidence,
    Optional<UUID> quarryId,
    Optional<UUID> huntId,
    long huntExpiresAt,
    Anchors anchors,
    Intent intent,
    Cadence cadence,
    Deadlines deadlines,
    int routeFailures,
    long intentGeneration
) {
    public static final int SCHEMA_VERSION = 1;

    public WerewolfHunterState {
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence")).stream()
            .limit(WerewolfHunterRules.MAX_EVIDENCE_RECORDS).toList();
        quarryId = Objects.requireNonNull(quarryId, "quarryId");
        huntId = Objects.requireNonNull(huntId, "huntId");
        intent = Objects.requireNonNull(intent, "intent");
        anchors = Objects.requireNonNull(anchors, "anchors");
        cadence = Objects.requireNonNull(cadence, "cadence");
        deadlines = Objects.requireNonNull(deadlines, "deadlines");
        routeFailures = Math.clamp(routeFailures, 0, WerewolfHunterRules.MAX_ROUTE_FAILURES);
        intentGeneration = Math.max(0L, intentGeneration);
    }

    public record Anchors(
        Optional<BlockPos> settlement,
        Optional<BlockPos> event,
        Optional<BlockPos> lane,
        Optional<BlockPos> search,
        Optional<BlockPos> returnPoint
    ) {
        public Anchors {
            settlement = Objects.requireNonNull(settlement, "settlement").map(BlockPos::immutable);
            event = Objects.requireNonNull(event, "event").map(BlockPos::immutable);
            lane = Objects.requireNonNull(lane, "lane").map(BlockPos::immutable);
            search = Objects.requireNonNull(search, "search").map(BlockPos::immutable);
            returnPoint = Objects.requireNonNull(returnPoint, "returnPoint").map(BlockPos::immutable);
        }

        public static Anchors none() {
            return new Anchors(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
        }

        public Anchors withoutTransientClaims() {
            return new Anchors(settlement, event, Optional.empty(), Optional.empty(), returnPoint);
        }
    }

    public record Cadence(
        long nextDecisionAt,
        long nextObservationAt,
        long nextScheduleAt,
        long nextFeedbackAt,
        long nextNavigationAt
    ) {
    }

    public record Deadlines(
        long warnedAt,
        long engageUntil,
        long lostSightUntil,
        long searchUntil,
        long retreatUntil,
        long actionBackoffUntil
    ) {
        public static Deadlines none() {
            return new Deadlines(0L, 0L, 0L, 0L, 0L, 0L);
        }
    }

    public static WerewolfHunterState empty(final UUID hunterId, final long now) {
        final long offset = WerewolfHunterRules.stableOffset(
            hunterId, WerewolfHunterRules.DECISION_INTERVAL_TICKS
        );
        return new WerewolfHunterState(
            SCHEMA_VERSION, List.of(), Optional.empty(), Optional.empty(), 0L,
            Anchors.none(), Intent.IDLE,
            new Cadence(Math.max(0L, now) + offset, 0L, 0L, 0L, 0L),
            Deadlines.none(), 0, 0L
        );
    }

    public WerewolfHunterState withEvidence(final List<Evidence> ledger) {
        return new WerewolfHunterState(schemaVersion, ledger, quarryId, huntId, huntExpiresAt,
            anchors, intent, cadence, deadlines, routeFailures, intentGeneration);
    }

    public WerewolfHunterState withQuarry(final Optional<UUID> quarry) {
        return new WerewolfHunterState(schemaVersion, evidence, quarry, huntId, huntExpiresAt,
            anchors, intent, cadence, deadlines, routeFailures, intentGeneration);
    }

    public WerewolfHunterState withHunt(final Optional<UUID> hunt, final long expiresAt) {
        return new WerewolfHunterState(schemaVersion, evidence, quarryId, hunt,
            hunt.isEmpty() ? 0L : expiresAt,
            anchors, intent, cadence, deadlines, routeFailures, intentGeneration);
    }

    public WerewolfHunterState withAnchors(final Anchors updated) {
        return new WerewolfHunterState(schemaVersion, evidence, quarryId, huntId, huntExpiresAt,
            updated, intent, cadence, deadlines, routeFailures, intentGeneration);
    }

    public WerewolfHunterState withIntent(final Intent updated) {
        return new WerewolfHunterState(schemaVersion, evidence, quarryId, huntId, huntExpiresAt,
            anchors, updated, cadence, deadlines, routeFailures,
            updated == intent ? intentGeneration : intentGeneration + 1L);
    }

    public WerewolfHunterState withCadence(final Cadence updated) {
        return new WerewolfHunterState(schemaVersion, evidence, quarryId, huntId, huntExpiresAt,
            anchors, intent, updated, deadlines, routeFailures, intentGeneration);
    }

    public WerewolfHunterState withDeadlines(final Deadlines updated) {
        return new WerewolfHunterState(schemaVersion, evidence, quarryId, huntId, huntExpiresAt,
            anchors, intent, cadence, updated, routeFailures, intentGeneration);
    }

    public WerewolfHunterState withRouteFailures(final int updated) {
        return new WerewolfHunterState(schemaVersion, evidence, quarryId, huntId, huntExpiresAt,
            anchors, intent, cadence, deadlines, updated, intentGeneration);
    }

    public WerewolfHunterState nextGeneration() {
        return new WerewolfHunterState(schemaVersion, evidence, quarryId, huntId, huntExpiresAt,
            anchors, intent, cadence, deadlines, routeFailures, intentGeneration + 1L);
    }

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        tag.putInt("EvidenceCount", evidence.size());
        for (int index = 0; index < evidence.size(); index++) {
            final Evidence entry = evidence.get(index);
            final CompoundTag row = new CompoundTag();
            row.putString("Type", entry.type().name().toLowerCase(Locale.ROOT));
            row.putString("Confidence", entry.confidence().name().toLowerCase(Locale.ROOT));
            entry.sourceId().ifPresent(id -> row.putString("Source", id.toString()));
            entry.targetId().ifPresent(id -> row.putString("Target", id.toString()));
            entry.packedPosition().ifPresent(packed -> row.putLong("Position", packed));
            entry.dimension().ifPresent(dimension -> row.putString("Dimension", dimension));
            row.putLong("ObservedAt", entry.observedAt());
            row.putLong("ExpiresAt", entry.expiresAt());
            row.putBoolean("Consumed", entry.consumed());
            tag.put("Evidence" + index, row);
        }
        quarryId.ifPresent(id -> tag.putString("Quarry", id.toString()));
        huntId.ifPresent(id -> tag.putString("Hunt", id.toString()));
        tag.putLong("HuntExpiresAt", huntExpiresAt);
        writeAnchor(tag, "SettlementAnchor", anchors.settlement());
        writeAnchor(tag, "EventAnchor", anchors.event());
        writeAnchor(tag, "ReturnAnchor", anchors.returnPoint());
        tag.putString("Intent", intent.name().toLowerCase(Locale.ROOT));
        tag.putLong("WarnedAt", deadlines.warnedAt());
        tag.putLong("EngageUntil", deadlines.engageUntil());
        tag.putLong("LostSightUntil", deadlines.lostSightUntil());
        tag.putLong("SearchUntil", deadlines.searchUntil());
        tag.putLong("RetreatUntil", deadlines.retreatUntil());
        tag.putLong("ActionBackoffUntil", deadlines.actionBackoffUntil());
        tag.putInt("RouteFailures", routeFailures);
        tag.putLong("IntentGeneration", intentGeneration);
        return tag;
    }

    private static void writeAnchor(final CompoundTag tag, final String key, final Optional<BlockPos> anchor) {
        anchor.ifPresent(pos -> tag.putLong(key, pos.asLong()));
    }

    public static WerewolfHunterState read(final CompoundTag tag, final UUID hunterId, final long now) {
        if (tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty(hunterId, now);
        }
        final int evidenceCount = Math.clamp(
            tag.getIntOr("EvidenceCount", 0), 0, WerewolfHunterRules.MAX_EVIDENCE_RECORDS
        );
        final java.util.ArrayList<Evidence> evidence = new java.util.ArrayList<>();
        for (int index = 0; index < evidenceCount; index++) {
            final Optional<CompoundTag> stored = tag.getCompound("Evidence" + index);
            if (stored.isEmpty()) continue;
            final CompoundTag row = stored.orElseThrow();
            final Optional<EvidenceType> type = parseEnum(EvidenceType.values(), row.getStringOr("Type", ""));
            final Optional<Confidence> confidence = parseEnum(Confidence.values(), row.getStringOr("Confidence", ""));
            if (type.isEmpty() || confidence.isEmpty()) continue;
            final long expiresAt = WerewolfHunterRules.clampDeadline(
                row.getLongOr("ExpiresAt", 0L), now,
                WerewolfHunterRules.evidenceLifetimeTicks(type.orElseThrow())
            );
            final Evidence entry = new Evidence(
                type.orElseThrow(), confidence.orElseThrow(),
                parseUuid(row.getStringOr("Source", "")),
                parseUuid(row.getStringOr("Target", "")),
                row.getLongOr("Position", Long.MIN_VALUE) == Long.MIN_VALUE
                    || row.getStringOr("Dimension", "").isEmpty()
                    ? Optional.empty()
                    : Optional.of(row.getLongOr("Position", Long.MIN_VALUE)),
                row.getStringOr("Dimension", "").isEmpty()
                    ? Optional.empty()
                    : Optional.of(row.getStringOr("Dimension", "")),
                row.getLongOr("ObservedAt", 0L),
                expiresAt,
                row.getBooleanOr("Consumed", false)
            );
            if (entry.valid(now) && evidence.size() < WerewolfHunterRules.MAX_EVIDENCE_RECORDS) {
                evidence.add(entry);
            }
        }
        final Optional<UUID> storedQuarry = parseUuid(tag.getStringOr("Quarry", ""));
        final Optional<UUID> quarry = storedQuarry.isPresent() && evidence.stream().anyMatch(entry ->
            entry.targetId().equals(storedQuarry) || entry.sourceId().equals(storedQuarry))
            ? storedQuarry : Optional.empty();
        Optional<UUID> hunt = parseUuid(tag.getStringOr("Hunt", ""));
        long huntExpiresAt = WerewolfHunterRules.clampDeadline(
            tag.getLongOr("HuntExpiresAt", 0L), now, WerewolfHunterRules.HUNT_RECORD_TICKS
        );
        if (hunt.isPresent() && huntExpiresAt <= now) {
            hunt = Optional.empty();
            huntExpiresAt = 0L;
        }
        final Anchors anchors = new Anchors(
            readAnchor(tag, "SettlementAnchor"),
            readAnchor(tag, "EventAnchor"),
            Optional.empty(),
            Optional.empty(),
            readAnchor(tag, "ReturnAnchor")
        );
        final Intent intent = parseEnum(Intent.values(), tag.getStringOr("Intent", ""))
            .orElse(Intent.IDLE);
        final long offset = WerewolfHunterRules.stableOffset(
            hunterId, WerewolfHunterRules.DECISION_INTERVAL_TICKS
        );
        return new WerewolfHunterState(
            SCHEMA_VERSION,
            List.copyOf(evidence),
            quarry,
            hunt,
            huntExpiresAt,
            anchors,
            intent == Intent.WARN || intent == Intent.ENGAGE || intent == Intent.REPOSITION
                ? Intent.IDLE : intent,
            new Cadence(Math.max(0L, now) + offset, 0L, 0L, 0L, 0L),
            new Deadlines(
                0L,
                clearExpired(tag.getLongOr("EngageUntil", 0L), now, WerewolfHunterRules.ENGAGE_TICKS),
                clearExpired(tag.getLongOr("LostSightUntil", 0L), now, WerewolfHunterRules.LOST_SIGHT_TICKS),
                clearExpired(tag.getLongOr("SearchUntil", 0L), now, WerewolfHunterRules.SEARCH_TICKS),
                clearExpired(tag.getLongOr("RetreatUntil", 0L), now, WerewolfHunterRules.RETREAT_TICKS),
                WerewolfHunterRules.clampDeadline(
                    tag.getLongOr("ActionBackoffUntil", 0L), now, WerewolfHunterRules.ROUTE_BACKOFF_TICKS
                )
            ),
            tag.getIntOr("RouteFailures", 0),
            Math.max(0L, tag.getLongOr("IntentGeneration", 0L))
        );
    }

    private static long clearExpired(final long deadline, final long now, final long maxHorizonTicks) {
        final long clamped = WerewolfHunterRules.clampDeadline(deadline, now, maxHorizonTicks);
        return clamped <= now ? 0L : clamped;
    }

    private static Optional<BlockPos> readAnchor(final CompoundTag tag, final String key) {
        final long stored = tag.getLongOr(key, Long.MIN_VALUE);
        return stored == Long.MIN_VALUE ? Optional.empty() : Optional.of(BlockPos.of(stored));
    }

    private static Optional<UUID> parseUuid(final String value) {
        if (value.isEmpty()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(value));
        } catch (final IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static <T extends Enum<T>> Optional<T> parseEnum(final T[] values, final String value) {
        for (final T candidate : values) {
            if (candidate.name().equalsIgnoreCase(value)) return Optional.of(candidate);
        }
        return Optional.empty();
    }
}
