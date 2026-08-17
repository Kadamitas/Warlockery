package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.HexBatRules.Action;
import com.kadamitas.warlockery.entity.HexBatRules.DestinationPurpose;
import com.kadamitas.warlockery.entity.HexBatRules.Mode;
import com.kadamitas.warlockery.entity.HexBatRules.Provenance;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Versioned bounded semantic state owned by exactly one Hex Bat.
 * The state contains no Entity, Level, Path, Navigation, AABB, collection,
 * graph, queue, inventory, block entity, chunk, or client object; every
 * semantic slot keeps at most one value.
 */
public record HexBatState(
    int schemaVersion,
    Provenance provenance,
    Optional<BlockPos> anchor,
    Optional<String> anchorDimension,
    Optional<BlockPos> roost,
    Optional<String> roostDimension,
    Mode mode,
    Action action,
    Optional<UUID> actionTargetId,
    Optional<String> actionTargetDimension,
    Optional<UUID> threatId,
    Optional<String> threatDimension,
    long threatExpiresAt,
    int threatHopCount,
    Optional<BlockPos> destination,
    DestinationPurpose destinationPurpose,
    Cadence cadence,
    Deadlines deadlines,
    int routeFailures
) {
    public static final int SCHEMA_VERSION = 1;

    public HexBatState {
        provenance = Objects.requireNonNull(provenance, "provenance");
        anchor = Objects.requireNonNull(anchor, "anchor").map(BlockPos::immutable);
        anchorDimension = Objects.requireNonNull(anchorDimension, "anchorDimension");
        roost = Objects.requireNonNull(roost, "roost").map(BlockPos::immutable);
        roostDimension = Objects.requireNonNull(roostDimension, "roostDimension");
        mode = Objects.requireNonNull(mode, "mode");
        action = Objects.requireNonNull(action, "action");
        actionTargetId = Objects.requireNonNull(actionTargetId, "actionTargetId");
        actionTargetDimension = Objects.requireNonNull(actionTargetDimension, "actionTargetDimension");
        threatId = Objects.requireNonNull(threatId, "threatId");
        threatDimension = Objects.requireNonNull(threatDimension, "threatDimension");
        destination = Objects.requireNonNull(destination, "destination").map(BlockPos::immutable);
        destinationPurpose = Objects.requireNonNull(destinationPurpose, "destinationPurpose");
        cadence = Objects.requireNonNull(cadence, "cadence");
        deadlines = Objects.requireNonNull(deadlines, "deadlines");
        routeFailures = Math.clamp(routeFailures, 0, HexBatRules.MAX_ROUTE_FAILURES);
        threatHopCount = Math.clamp(threatHopCount, 0, HexBatRules.MAX_CALL_HOPS);
        // Coupled validation: a position without its dimension is dropped, and
        // an action without a frozen target identity cannot exist.
        if (anchor.isPresent() && anchorDimension.isEmpty()) anchor = Optional.empty();
        if (anchorDimension.isPresent() && anchor.isEmpty()) anchorDimension = Optional.empty();
        if (roost.isPresent() && roostDimension.isEmpty()) roost = Optional.empty();
        if (roostDimension.isPresent() && roost.isEmpty()) roostDimension = Optional.empty();
        if (threatId.isPresent() && threatDimension.isEmpty()) threatId = Optional.empty();
        if (threatId.isEmpty()) {
            threatDimension = Optional.empty();
            threatExpiresAt = 0L;
            threatHopCount = 0;
        }
        if (action == Action.SWOOP && (actionTargetId.isEmpty() || actionTargetDimension.isEmpty())) {
            action = Action.NONE;
        }
        if (action == Action.NONE) {
            actionTargetId = Optional.empty();
            actionTargetDimension = Optional.empty();
        }
        if (destination.isEmpty()) {
            destinationPurpose = DestinationPurpose.NONE;
        }
        if (destinationPurpose == DestinationPurpose.NONE) {
            destination = Optional.empty();
        }
    }

    public record Cadence(
        long nextTargetScanAt,
        long nextPeerScanAt,
        long nextRoostSearchAt,
        long nextHazardScanAt,
        long nextNavigationAt,
        long nextSoundAt
    ) {
        public static Cadence due() {
            return new Cadence(0L, 0L, 0L, 0L, 0L, 0L);
        }
    }

    public record Deadlines(
        long actionWindupUntil,
        long actionExecuteUntil,
        long actionRecoverUntil,
        long swoopCooldownUntil,
        long withdrawUntil,
        long routeBackoffUntil,
        long callDedupeUntil,
        long sortieUntil
    ) {
        public static Deadlines none() {
            return new Deadlines(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }
    }

    public static HexBatState empty(final UUID batId, final long now) {
        final long offset = HexBatRules.stableOffset(batId, HexBatRules.TARGET_SCAN_INTERVAL_TICKS);
        return new HexBatState(
            SCHEMA_VERSION, Provenance.UNBOUND,
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(),
            Mode.SHELTER, Action.NONE,
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), 0L, 0,
            Optional.empty(), DestinationPurpose.NONE,
            new Cadence(Math.max(0L, now) + offset, 0L, 0L, 0L, 0L, 0L),
            Deadlines.none(), 0
        );
    }

    // ---- with-ers ----

    public HexBatState withProvenance(final Provenance updated) {
        return new HexBatState(schemaVersion, updated, anchor, anchorDimension, roost, roostDimension,
            mode, action, actionTargetId, actionTargetDimension, threatId, threatDimension,
            threatExpiresAt, threatHopCount, destination, destinationPurpose, cadence, deadlines, routeFailures);
    }

    public HexBatState withAnchor(final Optional<BlockPos> position, final Optional<String> dimension) {
        return new HexBatState(schemaVersion, provenance, position, dimension, roost, roostDimension,
            mode, action, actionTargetId, actionTargetDimension, threatId, threatDimension,
            threatExpiresAt, threatHopCount, destination, destinationPurpose, cadence, deadlines, routeFailures);
    }

    public HexBatState withRoost(final Optional<BlockPos> position, final Optional<String> dimension) {
        return new HexBatState(schemaVersion, provenance, anchor, anchorDimension, position, dimension,
            mode, action, actionTargetId, actionTargetDimension, threatId, threatDimension,
            threatExpiresAt, threatHopCount, destination, destinationPurpose, cadence, deadlines, routeFailures);
    }

    public HexBatState withMode(final Mode updated) {
        return new HexBatState(schemaVersion, provenance, anchor, anchorDimension, roost, roostDimension,
            updated, action, actionTargetId, actionTargetDimension, threatId, threatDimension,
            threatExpiresAt, threatHopCount, destination, destinationPurpose, cadence, deadlines, routeFailures);
    }

    public HexBatState withAction(
        final Action updated,
        final Optional<UUID> targetId,
        final Optional<String> targetDimension
    ) {
        return new HexBatState(schemaVersion, provenance, anchor, anchorDimension, roost, roostDimension,
            mode, updated, targetId, targetDimension, threatId, threatDimension,
            threatExpiresAt, threatHopCount, destination, destinationPurpose, cadence, deadlines, routeFailures);
    }

    public HexBatState withThreat(
        final Optional<UUID> id,
        final Optional<String> dimension,
        final long expiresAt,
        final int hopCount
    ) {
        return new HexBatState(schemaVersion, provenance, anchor, anchorDimension, roost, roostDimension,
            mode, action, actionTargetId, actionTargetDimension, id, dimension,
            expiresAt, hopCount, destination, destinationPurpose, cadence, deadlines, routeFailures);
    }

    public HexBatState withDestination(final Optional<BlockPos> position, final DestinationPurpose purpose) {
        return new HexBatState(schemaVersion, provenance, anchor, anchorDimension, roost, roostDimension,
            mode, action, actionTargetId, actionTargetDimension, threatId, threatDimension,
            threatExpiresAt, threatHopCount, position, purpose, cadence, deadlines, routeFailures);
    }

    public HexBatState withCadence(final Cadence updated) {
        return new HexBatState(schemaVersion, provenance, anchor, anchorDimension, roost, roostDimension,
            mode, action, actionTargetId, actionTargetDimension, threatId, threatDimension,
            threatExpiresAt, threatHopCount, destination, destinationPurpose, updated, deadlines, routeFailures);
    }

    public HexBatState withDeadlines(final Deadlines updated) {
        return new HexBatState(schemaVersion, provenance, anchor, anchorDimension, roost, roostDimension,
            mode, action, actionTargetId, actionTargetDimension, threatId, threatDimension,
            threatExpiresAt, threatHopCount, destination, destinationPurpose, cadence, updated, routeFailures);
    }

    public HexBatState withRouteFailures(final int updated) {
        return new HexBatState(schemaVersion, provenance, anchor, anchorDimension, roost, roostDimension,
            mode, action, actionTargetId, actionTargetDimension, threatId, threatDimension,
            threatExpiresAt, threatHopCount, destination, destinationPurpose, cadence, deadlines, updated);
    }

    /** Dimension transfer clears anchor, roost, destination, action, and report. */
    public HexBatState clearedForDimensionChange() {
        return withAnchor(Optional.empty(), Optional.empty())
            .withRoost(Optional.empty(), Optional.empty())
            .withDestination(Optional.empty(), DestinationPurpose.NONE)
            .withAction(Action.NONE, Optional.empty(), Optional.empty())
            .withThreat(Optional.empty(), Optional.empty(), 0L, 0);
    }

    // ---- persistence ----

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        tag.putString("Provenance", provenance.name().toLowerCase(Locale.ROOT));
        anchor.ifPresent(pos -> tag.putLong("Anchor", pos.asLong()));
        anchorDimension.ifPresent(dim -> tag.putString("AnchorDimension", dim));
        roost.ifPresent(pos -> tag.putLong("Roost", pos.asLong()));
        roostDimension.ifPresent(dim -> tag.putString("RoostDimension", dim));
        tag.putString("Mode", mode.name().toLowerCase(Locale.ROOT));
        tag.putString("Action", action.name().toLowerCase(Locale.ROOT));
        actionTargetId.ifPresent(id -> tag.putString("ActionTarget", id.toString()));
        actionTargetDimension.ifPresent(dim -> tag.putString("ActionTargetDimension", dim));
        threatId.ifPresent(id -> tag.putString("Threat", id.toString()));
        threatDimension.ifPresent(dim -> tag.putString("ThreatDimension", dim));
        tag.putLong("ThreatExpiresAt", threatExpiresAt);
        tag.putInt("ThreatHops", threatHopCount);
        destination.ifPresent(pos -> tag.putLong("Destination", pos.asLong()));
        tag.putString("DestinationPurpose", destinationPurpose.name().toLowerCase(Locale.ROOT));
        tag.putLong("ActionWindupUntil", deadlines.actionWindupUntil());
        tag.putLong("ActionExecuteUntil", deadlines.actionExecuteUntil());
        tag.putLong("ActionRecoverUntil", deadlines.actionRecoverUntil());
        tag.putLong("SwoopCooldownUntil", deadlines.swoopCooldownUntil());
        tag.putLong("WithdrawUntil", deadlines.withdrawUntil());
        tag.putLong("RouteBackoffUntil", deadlines.routeBackoffUntil());
        tag.putLong("CallDedupeUntil", deadlines.callDedupeUntil());
        tag.putLong("SortieUntil", deadlines.sortieUntil());
        tag.putInt("RouteFailures", routeFailures);
        return tag;
    }

    /**
     * Load parses safe defaults, clamps deadlines against current time,
     * cancels an in-progress action to bounded recovery so damage cannot
     * replay, clears volatile targets/calls, and preserves independently
     * valid durable facts when only volatile fields are malformed.
     */
    public static HexBatState read(final CompoundTag tag, final UUID batId, final long now) {
        if (tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty(batId, now);
        }
        final Provenance provenance = parseEnum(Provenance.values(), tag.getStringOr("Provenance", ""))
            .orElse(Provenance.UNBOUND);
        final Optional<BlockPos> anchor = readPos(tag, "Anchor");
        final Optional<String> anchorDimension = readDimension(tag, "AnchorDimension");
        final Optional<BlockPos> roost = readPos(tag, "Roost");
        final Optional<String> roostDimension = readDimension(tag, "RoostDimension");
        final Mode storedMode = parseEnum(Mode.values(), tag.getStringOr("Mode", "")).orElse(Mode.SHELTER);
        // Load cancellation: an interrupted swoop can never resume or replay.
        final Action storedAction = parseEnum(Action.values(), tag.getStringOr("Action", "")).orElse(Action.NONE);
        final long recoverUntil = storedAction == Action.SWOOP
            ? HexBatRules.saturatingAdd(now, HexBatRules.SWOOP_RECOVERY_TICKS)
            : clearExpired(tag.getLongOr("ActionRecoverUntil", 0L), now, HexBatRules.SWOOP_RECOVERY_TICKS);
        Optional<UUID> threat = parseUuid(tag.getStringOr("Threat", ""));
        Optional<String> threatDimension = readDimension(tag, "ThreatDimension");
        long threatExpiresAt = HexBatRules.clampDeadline(
            tag.getLongOr("ThreatExpiresAt", 0L), now, HexBatRules.CALL_EXPIRY_TICKS
        );
        if (threat.isEmpty() || threatDimension.isEmpty() || threatExpiresAt <= now) {
            threat = Optional.empty();
            threatDimension = Optional.empty();
            threatExpiresAt = 0L;
        }
        final long offset = HexBatRules.stableOffset(batId, HexBatRules.TARGET_SCAN_INTERVAL_TICKS);
        return new HexBatState(
            SCHEMA_VERSION,
            provenance,
            anchor, anchorDimension,
            roost, roostDimension,
            storedMode == Mode.INTERCEPT ? Mode.SHELTER : storedMode,
            Action.NONE, Optional.empty(), Optional.empty(),
            threat, threatDimension, threatExpiresAt,
            Math.clamp(tag.getIntOr("ThreatHops", 0), 0, HexBatRules.MAX_CALL_HOPS),
            Optional.empty(), DestinationPurpose.NONE,
            new Cadence(Math.max(0L, now) + offset, 0L, 0L, 0L, 0L, 0L),
            new Deadlines(
                0L,
                0L,
                recoverUntil,
                clearExpired(tag.getLongOr("SwoopCooldownUntil", 0L), now,
                    HexBatRules.SWOOP_RECOVERY_TICKS + HexBatRules.POST_CONTACT_WITHDRAW_TICKS),
                clearExpired(tag.getLongOr("WithdrawUntil", 0L), now, HexBatRules.WITHDRAW_TICKS),
                HexBatRules.clampDeadline(tag.getLongOr("RouteBackoffUntil", 0L), now,
                    HexBatRules.ROUTE_BACKOFF_TICKS),
                HexBatRules.clampDeadline(tag.getLongOr("CallDedupeUntil", 0L), now,
                    HexBatRules.CALL_DEDUPE_TICKS),
                clearExpired(tag.getLongOr("SortieUntil", 0L), now, HexBatRules.SORTIE_MAX_TICKS)
            ),
            tag.getIntOr("RouteFailures", 0)
        );
    }

    private static long clearExpired(final long deadline, final long now, final long maxHorizonTicks) {
        final long clamped = HexBatRules.clampDeadline(deadline, now, maxHorizonTicks);
        return clamped <= now ? 0L : clamped;
    }

    private static Optional<BlockPos> readPos(final CompoundTag tag, final String key) {
        final long stored = tag.getLongOr(key, Long.MIN_VALUE);
        return stored == Long.MIN_VALUE ? Optional.empty() : Optional.of(BlockPos.of(stored));
    }

    private static Optional<String> readDimension(final CompoundTag tag, final String key) {
        final String stored = tag.getStringOr(key, "");
        return stored.isEmpty() || net.minecraft.resources.Identifier.tryParse(stored) == null
            ? Optional.empty()
            : Optional.of(stored);
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
