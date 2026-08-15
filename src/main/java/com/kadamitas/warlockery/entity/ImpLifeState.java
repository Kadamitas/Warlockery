package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ImpLifeRules.Action;
import com.kadamitas.warlockery.entity.ImpLifeRules.Duty;
import com.kadamitas.warlockery.entity.ImpLifeRules.InfernalOrder;
import com.kadamitas.warlockery.entity.ImpLifeRules.Observation;
import com.kadamitas.warlockery.entity.ImpLifeRules.ObservationType;
import com.kadamitas.warlockery.entity.ImpLifeRules.OrderAction;
import com.kadamitas.warlockery.entity.ImpLifeRules.OrderRank;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public record ImpLifeState(
    int schemaVersion,
    Optional<Duty> steadyDuty,
    Optional<Duty> priorDuty,
    Action action,
    Optional<Anchor> anchor,
    Optional<Anchor> destination,
    Optional<Threat> threat,
    List<Observation> observations,
    Optional<InfernalOrder> order,
    int scoutLeg,
    boolean reportDelivered,
    long actionStartedAt,
    long actionTimeoutAt,
    Cadence cadence,
    Deadlines deadlines,
    int routeFailures,
    boolean retreatLatched,
    long actionEpoch
) {
    public static final int SCHEMA_VERSION = 1;

    public ImpLifeState {
        steadyDuty = Objects.requireNonNull(steadyDuty, "steadyDuty");
        priorDuty = Objects.requireNonNull(priorDuty, "priorDuty");
        action = Objects.requireNonNull(action, "action");
        anchor = Objects.requireNonNull(anchor, "anchor");
        destination = Objects.requireNonNull(destination, "destination");
        threat = Objects.requireNonNull(threat, "threat");
        observations = List.copyOf(Objects.requireNonNull(observations, "observations")).stream()
            .limit(ImpLifeRules.MAX_OBSERVATIONS).toList();
        order = Objects.requireNonNull(order, "order");
        cadence = Objects.requireNonNull(cadence, "cadence");
        deadlines = Objects.requireNonNull(deadlines, "deadlines");
        scoutLeg = Math.clamp(scoutLeg, 0, ImpLifeRules.SCOUT_LEGS);
        routeFailures = Math.clamp(routeFailures, 0, ImpLifeRules.MAX_ROUTE_FAILURES);
        actionEpoch = Math.max(0L, actionEpoch);
    }

    public record Anchor(String dimension, BlockPos position) {
        public Anchor {
            dimension = Objects.requireNonNull(dimension, "dimension");
            position = Objects.requireNonNull(position, "position").immutable();
        }

        public boolean valid() {
            return !dimension.isEmpty() && ImpLifeRules.validWorldPosition(position);
        }
    }

    public record Threat(UUID id, long expiresAt) {
        public boolean valid(final long now) {
            return expiresAt > now;
        }
    }

    public record Cadence(
        long nextDecisionAt,
        long nextOwnerAt,
        long nextDiscoveryAt,
        long nextCuriosityAt,
        long nextNavigationAt,
        long nextFeedbackAt
    ) {
        public static Cadence startingAt(final UUID impId, final long now) {
            return new Cadence(
                ImpLifeRules.saturatingAdd(Math.max(0L, now),
                    ImpLifeRules.stableOffset(impId, ImpLifeRules.IDLE_DECISION_TICKS)),
                0L, 0L, 0L, 0L, 0L
            );
        }
    }

    public record Deadlines(
        long recoveryUntil,
        long meleeRecoveryUntil,
        long curiosityBackoffUntil,
        long windupStartedAt,
        long lastShotAt
    ) {
        public static Deadlines none() {
            return new Deadlines(0L, 0L, 0L, 0L, 0L);
        }
    }

    public static ImpLifeState empty(final UUID impId, final long now) {
        return new ImpLifeState(
            SCHEMA_VERSION, Optional.empty(), Optional.empty(), Action.NONE,
            Optional.empty(), Optional.empty(), Optional.empty(), List.of(), Optional.empty(),
            0, false, 0L, 0L, Cadence.startingAt(impId, now), Deadlines.none(), 0, false, 0L
        );
    }

    public ImpLifeState withDuties(final Optional<Duty> steady, final Optional<Duty> prior) {
        return new ImpLifeState(schemaVersion, steady, prior, action, anchor, destination, threat,
            observations, order, scoutLeg, reportDelivered, actionStartedAt, actionTimeoutAt,
            cadence, deadlines, routeFailures, retreatLatched, actionEpoch);
    }

    public ImpLifeState withAction(final Action updated) {
        return new ImpLifeState(schemaVersion, steadyDuty, priorDuty, updated, anchor, destination,
            threat, observations, order, scoutLeg, reportDelivered, actionStartedAt, actionTimeoutAt,
            cadence, deadlines, routeFailures, retreatLatched,
            updated == action ? actionEpoch : ImpLifeRules.nextEpoch(actionEpoch));
    }

    public ImpLifeState withAnchor(final Optional<Anchor> updated) {
        return new ImpLifeState(schemaVersion, steadyDuty, priorDuty, action, updated, destination,
            threat, observations, order, scoutLeg, reportDelivered, actionStartedAt, actionTimeoutAt,
            cadence, deadlines, routeFailures, retreatLatched, actionEpoch);
    }

    public ImpLifeState withDestination(final Optional<Anchor> updated) {
        return new ImpLifeState(schemaVersion, steadyDuty, priorDuty, action, anchor, updated,
            threat, observations, order, scoutLeg, reportDelivered, actionStartedAt, actionTimeoutAt,
            cadence, deadlines, routeFailures, retreatLatched, actionEpoch);
    }

    public ImpLifeState withThreat(final Optional<Threat> updated) {
        return new ImpLifeState(schemaVersion, steadyDuty, priorDuty, action, anchor, destination,
            updated, observations, order, scoutLeg, reportDelivered, actionStartedAt, actionTimeoutAt,
            cadence, deadlines, routeFailures, retreatLatched, actionEpoch);
    }

    public ImpLifeState withObservations(final List<Observation> updated) {
        return new ImpLifeState(schemaVersion, steadyDuty, priorDuty, action, anchor, destination,
            threat, updated, order, scoutLeg, reportDelivered, actionStartedAt, actionTimeoutAt,
            cadence, deadlines, routeFailures, retreatLatched, actionEpoch);
    }

    public ImpLifeState withOrder(final Optional<InfernalOrder> updated) {
        return new ImpLifeState(schemaVersion, steadyDuty, priorDuty, action, anchor, destination,
            threat, observations, updated, scoutLeg, reportDelivered, actionStartedAt, actionTimeoutAt,
            cadence, deadlines, routeFailures, retreatLatched, actionEpoch);
    }

    public ImpLifeState withScout(final int leg, final boolean delivered) {
        return new ImpLifeState(schemaVersion, steadyDuty, priorDuty, action, anchor, destination,
            threat, observations, order, leg, delivered, actionStartedAt, actionTimeoutAt,
            cadence, deadlines, routeFailures, retreatLatched, actionEpoch);
    }

    public ImpLifeState withActionWindow(final long startedAt, final long timeoutAt) {
        return new ImpLifeState(schemaVersion, steadyDuty, priorDuty, action, anchor, destination,
            threat, observations, order, scoutLeg, reportDelivered, startedAt, timeoutAt,
            cadence, deadlines, routeFailures, retreatLatched, actionEpoch);
    }

    public ImpLifeState withCadence(final Cadence updated) {
        return new ImpLifeState(schemaVersion, steadyDuty, priorDuty, action, anchor, destination,
            threat, observations, order, scoutLeg, reportDelivered, actionStartedAt, actionTimeoutAt,
            updated, deadlines, routeFailures, retreatLatched, actionEpoch);
    }

    public ImpLifeState withDeadlines(final Deadlines updated) {
        return new ImpLifeState(schemaVersion, steadyDuty, priorDuty, action, anchor, destination,
            threat, observations, order, scoutLeg, reportDelivered, actionStartedAt, actionTimeoutAt,
            cadence, updated, routeFailures, retreatLatched, actionEpoch);
    }

    public ImpLifeState withRouteFailures(final int updated) {
        return new ImpLifeState(schemaVersion, steadyDuty, priorDuty, action, anchor, destination,
            threat, observations, order, scoutLeg, reportDelivered, actionStartedAt, actionTimeoutAt,
            cadence, deadlines, updated, retreatLatched, actionEpoch);
    }

    public ImpLifeState withRetreatLatched(final boolean updated) {
        return new ImpLifeState(schemaVersion, steadyDuty, priorDuty, action, anchor, destination,
            threat, observations, order, scoutLeg, reportDelivered, actionStartedAt, actionTimeoutAt,
            cadence, deadlines, routeFailures, updated, actionEpoch);
    }

    public ImpLifeState nextEpoch() {
        return new ImpLifeState(schemaVersion, steadyDuty, priorDuty, action, anchor, destination,
            threat, observations, order, scoutLeg, reportDelivered, actionStartedAt, actionTimeoutAt,
            cadence, deadlines, routeFailures, retreatLatched, ImpLifeRules.nextEpoch(actionEpoch));
    }

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        steadyDuty.ifPresent(duty -> tag.putString("Duty", duty.name().toLowerCase(Locale.ROOT)));
        priorDuty.ifPresent(duty -> tag.putString("PriorDuty", duty.name().toLowerCase(Locale.ROOT)));
        tag.putString("Action", action.name().toLowerCase(Locale.ROOT));
        writeAnchor(tag, "Anchor", anchor);
        writeAnchor(tag, "Destination", destination);
        threat.ifPresent(row -> {
            tag.putString("ThreatId", row.id().toString());
            tag.putLong("ThreatExpiresAt", row.expiresAt());
        });
        tag.putInt("ObservationCount", observations.size());
        for (int index = 0; index < observations.size(); index++) {
            final Observation row = observations.get(index);
            final CompoundTag stored = new CompoundTag();
            stored.putString("Type", row.type().name().toLowerCase(Locale.ROOT));
            stored.putLong("Pos", row.packedPosition());
            row.subjectId().ifPresent(id -> stored.putString("Subject", id.toString()));
            stored.putLong("FirstAt", row.firstObservedAt());
            stored.putLong("LastAt", row.lastObservedAt());
            stored.putInt("Confidence", row.confidence());
            stored.putLong("ExpiresAt", row.expiresAt());
            tag.put("Observation" + index, stored);
        }
        order.ifPresent(row -> {
            tag.putString("OrderIssuer", row.issuerId().toString());
            tag.putString("OrderRank", row.rank().name().toLowerCase(Locale.ROOT));
            tag.putString("OrderGroup", row.groupId().toString());
            tag.putLong("OrderEpoch", row.epoch());
            tag.putString("OrderAction", row.action().name().toLowerCase(Locale.ROOT));
            row.targetId().ifPresent(id -> tag.putString("OrderTarget", id.toString()));
            tag.putLong("OrderCreatedAt", row.createdAt());
            tag.putLong("OrderExpiresAt", row.expiresAt());
        });
        tag.putInt("ScoutLeg", scoutLeg);
        tag.putBoolean("ReportDelivered", reportDelivered);
        tag.putLong("ActionStartedAt", actionStartedAt);
        tag.putLong("ActionTimeoutAt", actionTimeoutAt);
        tag.putLong("RecoveryUntil", deadlines.recoveryUntil());
        tag.putLong("MeleeRecoveryUntil", deadlines.meleeRecoveryUntil());
        tag.putLong("CuriosityBackoffUntil", deadlines.curiosityBackoffUntil());
        tag.putInt("RouteFailures", routeFailures);
        tag.putBoolean("RetreatLatched", retreatLatched);
        tag.putLong("ActionEpoch", actionEpoch);
        return tag;
    }

    private static void writeAnchor(final CompoundTag tag, final String key, final Optional<Anchor> anchor) {
        anchor.ifPresent(row -> {
            tag.putString(key + "Dimension", row.dimension());
            tag.putLong(key + "Pos", row.position().asLong());
        });
    }

    public static ImpLifeState read(final CompoundTag tag, final UUID impId, final long now) {
        if (tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty(impId, now);
        }
        final Optional<Duty> steady = parseEnum(Duty.values(), tag.getStringOr("Duty", ""));
        final Optional<Duty> prior = parseEnum(Duty.values(), tag.getStringOr("PriorDuty", ""));
        final Optional<Anchor> anchor = readAnchor(tag, "Anchor");
        final Optional<Anchor> destination = readAnchor(tag, "Destination");

        Action action = parseEnum(Action.values(), tag.getStringOr("Action", ""))
            .filter(ImpLifeRules::actionResumableAfterLoad)
            .orElse(Action.NONE);
        if ((action == Action.SCOUT_OUT || action == Action.SCOUT_RETURN) && anchor.isEmpty()) {
            action = Action.NONE;
        }
        if ((action == Action.FOLLOW || action == Action.WATCH) && steady.isEmpty()) {
            action = Action.NONE;
        }
        final boolean scouting = action == Action.SCOUT_OUT || action == Action.SCOUT_RETURN;

        final Optional<Threat> threat = parseUuid(tag.getStringOr("ThreatId", ""))
            .map(id -> new Threat(id, ImpLifeRules.clampDeadline(
                tag.getLongOr("ThreatExpiresAt", 0L), now, ImpLifeRules.THREAT_EXPIRY_TICKS)))
            .filter(row -> row.valid(now));

        final int observationCount = Math.clamp(
            tag.getIntOr("ObservationCount", 0), 0, ImpLifeRules.MAX_OBSERVATIONS * 2
        );
        List<Observation> observations = List.of();
        final List<Observation> parsed = new ArrayList<>();
        for (int index = 0; index < observationCount; index++) {
            final Optional<CompoundTag> stored = tag.getCompound("Observation" + index);
            if (stored.isEmpty()) {
                continue;
            }
            final CompoundTag row = stored.orElseThrow();
            final Optional<ObservationType> type = parseEnum(
                ObservationType.values(), row.getStringOr("Type", ""));
            final long packed = row.getLongOr("Pos", Long.MIN_VALUE);
            if (type.isEmpty() || packed == Long.MIN_VALUE
                || !ImpLifeRules.validWorldPosition(BlockPos.of(packed))) {
                continue;
            }
            final Observation entry = new Observation(
                type.orElseThrow(),
                packed,
                parseUuid(row.getStringOr("Subject", "")),
                Math.max(0L, row.getLongOr("FirstAt", 0L)),
                Math.max(0L, row.getLongOr("LastAt", 0L)),
                row.getIntOr("Confidence", 0),
                ImpLifeRules.clampDeadline(row.getLongOr("ExpiresAt", 0L), now,
                    ImpLifeRules.OBSERVATION_EXPIRY_TICKS)
            );
            if (entry.valid(now)) {
                parsed.add(entry);
            }
        }
        for (final Observation entry : parsed) {
            observations = ImpLifeRules.recordObservation(observations, entry, now);
        }

        final Optional<InfernalOrder> order = readOrder(tag, now);

        return new ImpLifeState(
            SCHEMA_VERSION,
            steady,
            prior,
            action,
            anchor,
            scouting ? destination : Optional.empty(),
            threat,
            observations,
            order,
            tag.getIntOr("ScoutLeg", 0),
            tag.getBooleanOr("ReportDelivered", false),
            Math.max(0L, tag.getLongOr("ActionStartedAt", 0L)),
            ImpLifeRules.clampDeadline(tag.getLongOr("ActionTimeoutAt", 0L), now,
                scouting ? ImpLifeRules.SCOUT_TOTAL_TICKS : ImpLifeRules.MAX_FUTURE_HORIZON_TICKS),
            Cadence.startingAt(impId, now),
            new Deadlines(
                ImpLifeRules.clampDeadline(tag.getLongOr("RecoveryUntil", 0L), now,
                    ImpLifeRules.MAX_FUTURE_HORIZON_TICKS),
                ImpLifeRules.clampDeadline(tag.getLongOr("MeleeRecoveryUntil", 0L), now,
                    ImpLifeRules.MELEE_RECOVERY_TICKS),
                ImpLifeRules.clampDeadline(tag.getLongOr("CuriosityBackoffUntil", 0L), now,
                    ImpLifeRules.CURIOSITY_BACKOFF_TICKS),
                0L,
                0L
            ),
            tag.getIntOr("RouteFailures", 0),
            tag.getBooleanOr("RetreatLatched", false),
            Math.max(0L, tag.getLongOr("ActionEpoch", 0L))
        );
    }

    private static Optional<Anchor> readAnchor(final CompoundTag tag, final String key) {
        final String dimension = tag.getStringOr(key + "Dimension", "");
        final long packed = tag.getLongOr(key + "Pos", Long.MIN_VALUE);
        if (dimension.isEmpty() || packed == Long.MIN_VALUE) {
            return Optional.empty();
        }
        final Anchor anchor = new Anchor(dimension, BlockPos.of(packed));
        return anchor.valid() ? Optional.of(anchor) : Optional.empty();
    }

    private static Optional<InfernalOrder> readOrder(final CompoundTag tag, final long now) {
        final Optional<UUID> issuer = parseUuid(tag.getStringOr("OrderIssuer", ""));
        final Optional<OrderRank> rank = parseEnum(OrderRank.values(), tag.getStringOr("OrderRank", ""));
        final Optional<UUID> group = parseUuid(tag.getStringOr("OrderGroup", ""));
        final Optional<OrderAction> action = parseEnum(
            OrderAction.values(), tag.getStringOr("OrderAction", ""));
        if (issuer.isEmpty() || rank.isEmpty() || group.isEmpty() || action.isEmpty()) {
            return Optional.empty();
        }
        final long expiresAt = ImpLifeRules.clampDeadline(
            tag.getLongOr("OrderExpiresAt", 0L), now, ImpLifeRules.ORDER_MAX_TICKS);
        final InfernalOrder order = new InfernalOrder(
            issuer.orElseThrow(),
            rank.orElseThrow(),
            group.orElseThrow(),
            Math.max(0L, tag.getLongOr("OrderEpoch", 0L)),
            action.orElseThrow(),
            parseUuid(tag.getStringOr("OrderTarget", "")),
            Math.max(0L, tag.getLongOr("OrderCreatedAt", 0L)),
            expiresAt
        );
        return order.valid(now) ? Optional.of(order) : Optional.empty();
    }

    private static Optional<UUID> parseUuid(final String value) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (final IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static <T extends Enum<T>> Optional<T> parseEnum(final T[] values, final String value) {
        for (final T candidate : values) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
