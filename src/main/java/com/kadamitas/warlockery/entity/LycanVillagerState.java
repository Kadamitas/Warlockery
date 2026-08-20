package com.kadamitas.warlockery.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public record LycanVillagerState(
    int schemaVersion,
    List<Familiarity> familiarity,
    Optional<UUID> recentAggressor,
    Optional<UUID> protectedResident,
    LycanVillagerRules.Intent intent,
    Optional<Anchor> anchor,
    long nextDecisionAt,
    long nextNearbyObservationAt,
    long nextLunarObservationAt,
    long lastNavigationAt,
    long warningDeadline,
    long pursuitExpiry,
    long withdrawalExpiry,
    int routeFailures,
    long retryAfter,
    int generation
) {
    public static final int SCHEMA_VERSION = 1;

    public LycanVillagerState {
        familiarity = List.copyOf(Objects.requireNonNull(familiarity, "familiarity"));
        recentAggressor = Objects.requireNonNull(recentAggressor, "recentAggressor");
        protectedResident = Objects.requireNonNull(protectedResident, "protectedResident");
        intent = Objects.requireNonNull(intent, "intent");
        anchor = Objects.requireNonNull(anchor, "anchor");
    }

    public record Familiarity(UUID id, int points, long lastObservedAt,
                              LycanVillagerRules.RelationshipSource source) {
        public Familiarity {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(source, "source");
        }
    }

    public record Anchor(String dimension, long packedPosition) {
        public Anchor { Objects.requireNonNull(dimension, "dimension"); }
    }

    public static LycanVillagerState fresh(final UUID stableId, final long now) {
        int decisionOffset = LycanVillagerRules.stagger(stableId, LycanVillagerRules.DECISION_CADENCE_TICKS);
        return new LycanVillagerState(SCHEMA_VERSION, List.of(), Optional.empty(), Optional.empty(),
            LycanVillagerRules.Intent.ROUTINE, Optional.empty(), now + decisionOffset,
            now + LycanVillagerRules.stagger(stableId, LycanVillagerRules.NEARBY_OBSERVATION_TICKS),
            now + LycanVillagerRules.stagger(stableId, LycanVillagerRules.LUNAR_OBSERVATION_TICKS),
            0, 0, 0, 0, 0, 0, 0);
    }

    public int points(final UUID id) {
        return familiarity.stream().filter(row -> row.id.equals(id)).mapToInt(Familiarity::points).findFirst().orElse(0);
    }

    public LycanVillagerState observe(final UUID id, final LycanVillagerRules.RelationshipSource source,
                                      final int points, final long now) {
        if (points <= 0) return this;
        List<Familiarity> rows = new ArrayList<>(familiarity);
        for (int index = 0; index < rows.size(); index++) {
            Familiarity row = rows.get(index);
            if (row.id.equals(id)) {
                rows.set(index, new Familiarity(id, Math.min(LycanVillagerRules.MAX_FAMILIARITY, row.points + points), now, source));
                return withFamiliarity(rows);
            }
        }
        Familiarity incoming = new Familiarity(id, Math.min(LycanVillagerRules.MAX_FAMILIARITY, points), now, source);
        if (rows.size() < LycanVillagerRules.FAMILIARITY_CAP) {
            rows.add(incoming);
            return withFamiliarity(rows);
        }
        Comparator<Familiarity> weakestFirst = Comparator.comparingInt(Familiarity::points)
            .thenComparingLong(Familiarity::lastObservedAt)
            .thenComparing(Familiarity::id, Comparator.reverseOrder());
        Familiarity victim = rows.stream().min(weakestFirst).orElseThrow();
        if (incoming.points <= victim.points) return this;
        rows.remove(victim);
        rows.add(incoming);
        return withFamiliarity(rows);
    }

    public LycanVillagerState decay(final long now) {
        List<Familiarity> rows = familiarity.stream()
            .map(row -> now - row.lastObservedAt >= LycanVillagerRules.FAMILIARITY_DECAY_TICKS
                ? new Familiarity(row.id, row.points - 1, now, row.source) : row)
            .filter(row -> row.points > 0).toList();
        return withFamiliarity(rows);
    }

    public LycanVillagerState routeFailed(final long now) {
        int failures = Math.min(LycanVillagerRules.MAX_ROUTE_FAILURES, routeFailures + 1);
        long retry = failures >= LycanVillagerRules.MAX_ROUTE_FAILURES ? now + LycanVillagerRules.ROUTE_RETRY_TICKS : 0;
        return copy(familiarity, intent, now, failures, retry, generation);
    }

    public LycanVillagerState routeSucceeded(final long now) {
        return copy(familiarity, intent, now, 0, 0, generation);
    }

    public LycanVillagerState withCombat(final UUID aggressor, final UUID resident, final LycanVillagerRules.Intent next,
                                         final long warning, final long pursuit) {
        return new LycanVillagerState(schemaVersion, familiarity, Optional.ofNullable(aggressor), Optional.ofNullable(resident),
            next, anchor, nextDecisionAt, nextNearbyObservationAt, nextLunarObservationAt, lastNavigationAt,
            warning, pursuit, withdrawalExpiry, routeFailures, retryAfter, generation + 1);
    }

    public LycanVillagerState withIntent(final LycanVillagerRules.Intent next, final long now) {
        return new LycanVillagerState(schemaVersion, familiarity, recentAggressor, protectedResident, next, anchor,
            now + LycanVillagerRules.DECISION_CADENCE_TICKS, nextNearbyObservationAt, nextLunarObservationAt,
            lastNavigationAt, warningDeadline, pursuitExpiry,
            next == LycanVillagerRules.Intent.WITHDRAW ? now + LycanVillagerRules.WITHDRAW_TICKS : withdrawalExpiry,
            routeFailures, retryAfter, generation + 1);
    }

    public LycanVillagerState withCadence(final long decision, final long nearby, final long lunar) {
        return new LycanVillagerState(schemaVersion, familiarity, recentAggressor, protectedResident, intent, anchor,
            decision, nearby, lunar, lastNavigationAt, warningDeadline, pursuitExpiry, withdrawalExpiry,
            routeFailures, retryAfter, generation);
    }

    public LycanVillagerState withAnchor(final String dimension, final long position) {
        return new LycanVillagerState(schemaVersion, familiarity, recentAggressor, protectedResident, intent,
            Optional.of(new Anchor(dimension, position)), nextDecisionAt, nextNearbyObservationAt, nextLunarObservationAt,
            lastNavigationAt, warningDeadline, pursuitExpiry, withdrawalExpiry, routeFailures, retryAfter, generation);
    }

    public LycanVillagerState cancel(final long now) {
        return new LycanVillagerState(schemaVersion, familiarity, Optional.empty(), Optional.empty(),
            LycanVillagerRules.Intent.ROUTINE, anchor, now + LycanVillagerRules.DECISION_CADENCE_TICKS,
            nextNearbyObservationAt, nextLunarObservationAt, 0, 0, 0, 0, 0, 0, generation + 1);
    }

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        tag.putString("Intent", intent.name());
        tag.putLong("NextDecisionAt", nextDecisionAt);
        tag.putLong("NextNearbyObservationAt", nextNearbyObservationAt);
        tag.putLong("NextLunarObservationAt", nextLunarObservationAt);
        tag.putInt("Generation", generation);
        recentAggressor.ifPresent(value -> tag.putString("RecentAggressor", value.toString()));
        protectedResident.ifPresent(value -> tag.putString("ProtectedResident", value.toString()));
        tag.putLong("LastNavigationAt", lastNavigationAt);
        tag.putLong("WarningDeadline", warningDeadline);
        tag.putLong("PursuitExpiry", pursuitExpiry);
        tag.putLong("WithdrawalExpiry", withdrawalExpiry);
        tag.putInt("RouteFailures", routeFailures);
        tag.putLong("RetryAfter", retryAfter);
        anchor.ifPresent(value -> { tag.putString("AnchorDimension", value.dimension); tag.putLong("AnchorPosition", value.packedPosition); });
        final ListTag rows = new ListTag();
        for (final Familiarity row : familiarity.stream().limit(LycanVillagerRules.FAMILIARITY_CAP).toList()) {
            final CompoundTag encoded = new CompoundTag();
            encoded.putString("Id", row.id.toString()); encoded.putInt("Points", row.points);
            encoded.putLong("LastObservedAt", row.lastObservedAt); encoded.putString("Source", row.source.name());
            rows.add(encoded);
        }
        tag.put("Familiarity", rows);
        return tag;
    }

    public static LycanVillagerState read(final CompoundTag tag, final UUID stableId, final long now) {
        // Unknown/legacy schemas are decoded field-by-field: independently valid
        // bounded familiarity and anchor data survive, while transient authority is reset.
        final List<Familiarity> rows = new ArrayList<>();
        final ListTag list = tag.getListOrEmpty("Familiarity");
        for (int index = 0; index < Math.min(list.size(), FAMILIARITY_LIMIT()); index++) {
            final CompoundTag row = list.getCompoundOrEmpty(index);
            try {
                final UUID id = UUID.fromString(row.getStringOr("Id", ""));
                final LycanVillagerRules.RelationshipSource source = Enum.valueOf(
                    LycanVillagerRules.RelationshipSource.class, row.getStringOr("Source", ""));
                rows.add(new Familiarity(id, row.getIntOr("Points", 0), row.getLongOr("LastObservedAt", now), source));
            } catch (RuntimeException ignored) { }
        }
        final String dimension = tag.getStringOr("AnchorDimension", "");
        final Optional<Anchor> anchor = dimension.contains(":")
            ? Optional.of(new Anchor(dimension, tag.getLongOr("AnchorPosition", 0L))) : Optional.empty();
        final Optional<UUID> aggressor = uuid(tag.getStringOr("RecentAggressor", ""));
        final Optional<UUID> resident = uuid(tag.getStringOr("ProtectedResident", ""));
        final LycanVillagerRules.Intent encodedIntent = intent(tag.getStringOr("Intent", "ROUTINE"));
        return new LycanVillagerState(SCHEMA_VERSION, rows, aggressor, resident, encodedIntent, anchor,
            tag.getLongOr("NextDecisionAt", now), tag.getLongOr("NextNearbyObservationAt", now),
            tag.getLongOr("NextLunarObservationAt", now), tag.getLongOr("LastNavigationAt", 0L),
            tag.getLongOr("WarningDeadline", 0L), tag.getLongOr("PursuitExpiry", 0L),
            tag.getLongOr("WithdrawalExpiry", 0L), Math.clamp(tag.getIntOr("RouteFailures", 0), 0, LycanVillagerRules.MAX_ROUTE_FAILURES),
            tag.getLongOr("RetryAfter", 0L),
            tag.getIntOr("Generation", 0)).normalizeAfterLoad(now);
    }

    private static Optional<UUID> uuid(final String value) {
        try { return Optional.of(UUID.fromString(value)); } catch (RuntimeException ignored) { return Optional.empty(); }
    }

    private static LycanVillagerRules.Intent intent(final String value) {
        try { return LycanVillagerRules.Intent.valueOf(value); }
        catch (RuntimeException ignored) { return LycanVillagerRules.Intent.ROUTINE; }
    }

    private static int FAMILIARITY_LIMIT() { return LycanVillagerRules.FAMILIARITY_CAP; }

    public LycanVillagerState normalizeAfterLoad(final long now) {
        List<Familiarity> rows = familiarity.stream()
            .filter(Objects::nonNull)
            .map(row -> new Familiarity(row.id, Math.clamp(row.points, 0, LycanVillagerRules.MAX_FAMILIARITY),
                Math.max(0, Math.min(row.lastObservedAt, now)), row.source))
            .filter(row -> row.points > 0)
            .sorted(Comparator.comparingInt(Familiarity::points).reversed().thenComparing(Familiarity::id))
            .limit(LycanVillagerRules.FAMILIARITY_CAP).toList();
        return new LycanVillagerState(SCHEMA_VERSION, rows, Optional.empty(), Optional.empty(),
            LycanVillagerRules.Intent.ROUTINE, validAnchor(anchor), clamp(nextDecisionAt, now, LycanVillagerRules.DECISION_CADENCE_TICKS),
            clamp(nextNearbyObservationAt, now, LycanVillagerRules.NEARBY_OBSERVATION_TICKS),
            clamp(nextLunarObservationAt, now, LycanVillagerRules.LUNAR_OBSERVATION_TICKS), 0,
            clamp(warningDeadline, now, LycanVillagerRules.WARNING_TICKS),
            clamp(pursuitExpiry, now, LycanVillagerRules.PURSUIT_TICKS),
            clamp(withdrawalExpiry, now, LycanVillagerRules.WITHDRAW_TICKS), 0, 0, generation + 1);
    }

    private static Optional<Anchor> validAnchor(final Optional<Anchor> value) {
        return value.filter(anchor -> !anchor.dimension.isBlank() && anchor.dimension.contains(":"));
    }

    private static long clamp(final long value, final long now, final long horizon) {
        return Math.max(now, Math.min(value, now + horizon));
    }

    private LycanVillagerState withFamiliarity(final List<Familiarity> rows) {
        return copy(rows, intent, lastNavigationAt, routeFailures, retryAfter, generation);
    }

    private LycanVillagerState copy(final List<Familiarity> rows, final LycanVillagerRules.Intent nextIntent,
                                    final long navigationAt, final int failures, final long retry, final int nextGeneration) {
        return new LycanVillagerState(schemaVersion, rows, recentAggressor, protectedResident, nextIntent, anchor,
            nextDecisionAt, nextNearbyObservationAt, nextLunarObservationAt, navigationAt, warningDeadline,
            pursuitExpiry, withdrawalExpiry, failures, retry, nextGeneration);
    }
}
