package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.EldritchWatcherRules.ActionType;
import com.kadamitas.warlockery.entity.EldritchWatcherRules.EvidenceType;
import com.kadamitas.warlockery.entity.EldritchWatcherRules.Mode;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public record EldritchWatcherState(
    int schemaVersion,
    Optional<Site> anchor,
    Optional<TimedSite> focus,
    Mode mode,
    Optional<UUID> subjectId,
    Optional<EvidenceType> evidenceType,
    long evidenceExpiresAt,
    int attentionSamples,
    Optional<TimedSite> lastSeen,
    Optional<UUID> threatId,
    long threatExpiresAt,
    long warningDedupeUntil,
    ActionType action,
    Optional<UUID> actionTargetId,
    Optional<String> actionDimension,
    long actionExecuteAt,
    long actionRecoverUntil,
    Optional<TimedSite> lure,
    Optional<TimedSite> destination,
    int routeFailures,
    long retryAfter,
    Cadence cadence,
    long withdrawUntil
) {
    public static final int SCHEMA_VERSION = 1;

    public EldritchWatcherState {
        anchor = Objects.requireNonNull(anchor, "anchor");
        focus = Objects.requireNonNull(focus, "focus");
        mode = Objects.requireNonNull(mode, "mode");
        subjectId = Objects.requireNonNull(subjectId, "subjectId");
        evidenceType = Objects.requireNonNull(evidenceType, "evidenceType");
        lastSeen = Objects.requireNonNull(lastSeen, "lastSeen");
        threatId = Objects.requireNonNull(threatId, "threatId");
        action = Objects.requireNonNull(action, "action");
        actionTargetId = Objects.requireNonNull(actionTargetId, "actionTargetId");
        actionDimension = Objects.requireNonNull(actionDimension, "actionDimension");
        lure = Objects.requireNonNull(lure, "lure");
        destination = Objects.requireNonNull(destination, "destination");
        cadence = Objects.requireNonNull(cadence, "cadence");
        attentionSamples = Math.clamp(attentionSamples, 0, EldritchWatcherRules.ESCALATION_SAMPLES);
        routeFailures = Math.clamp(routeFailures, 0, EldritchWatcherRules.MAX_ROUTE_FAILURES);
        if (action != ActionType.NONE && (actionTargetId.isEmpty() || actionDimension.isEmpty())) {
            action = ActionType.NONE;
            actionTargetId = Optional.empty();
            actionDimension = Optional.empty();
            actionExecuteAt = 0L;
        }
    }

    public record Site(String dimension, BlockPos position) {
        public Site {
            dimension = Objects.requireNonNull(dimension, "dimension");
            position = Objects.requireNonNull(position, "position").immutable();
        }
    }

    public record TimedSite(String dimension, BlockPos position, long expiresAt) {
        public TimedSite {
            dimension = Objects.requireNonNull(dimension, "dimension");
            position = Objects.requireNonNull(position, "position").immutable();
        }

        public boolean valid(final long now) {
            return expiresAt > now;
        }
    }

    public record Cadence(
        long nextPerceptionAt,
        long nextFocusScanAt,
        long nextHazardScanAt,
        long nextMovementAt,
        long nextFeedbackAt
    ) {
        public static Cadence staggered(final UUID id, final long now) {
            final long base = Math.max(0L, now);
            return new Cadence(
                base + EldritchWatcherRules.stableOffset(id, EldritchWatcherRules.PERCEPTION_INTERVAL_TICKS),
                base + EldritchWatcherRules.stableOffset(id, EldritchWatcherRules.FOCUS_SCAN_INTERVAL_TICKS),
                0L,
                0L,
                0L
            );
        }
    }

    public static EldritchWatcherState empty(final UUID watcherId, final long now) {
        return new EldritchWatcherState(
            SCHEMA_VERSION,
            Optional.empty(), Optional.empty(), Mode.QUIET_VIGIL,
            Optional.empty(), Optional.empty(), 0L, 0,
            Optional.empty(), Optional.empty(), 0L, 0L,
            ActionType.NONE, Optional.empty(), Optional.empty(), 0L, 0L,
            Optional.empty(), Optional.empty(), 0, 0L,
            Cadence.staggered(watcherId, now), 0L
        );
    }

    public EldritchWatcherState withAnchor(final Optional<Site> updated) {
        return new EldritchWatcherState(schemaVersion, updated, focus, mode, subjectId, evidenceType,
            evidenceExpiresAt, attentionSamples, lastSeen, threatId, threatExpiresAt, warningDedupeUntil,
            action, actionTargetId, actionDimension, actionExecuteAt, actionRecoverUntil,
            lure, destination, routeFailures, retryAfter, cadence, withdrawUntil);
    }

    public EldritchWatcherState withFocus(final Optional<TimedSite> updated) {
        return new EldritchWatcherState(schemaVersion, anchor, updated, mode, subjectId, evidenceType,
            evidenceExpiresAt, attentionSamples, lastSeen, threatId, threatExpiresAt, warningDedupeUntil,
            action, actionTargetId, actionDimension, actionExecuteAt, actionRecoverUntil,
            lure, destination, routeFailures, retryAfter, cadence, withdrawUntil);
    }

    public EldritchWatcherState withMode(final Mode updated) {
        return new EldritchWatcherState(schemaVersion, anchor, focus, updated, subjectId, evidenceType,
            evidenceExpiresAt, attentionSamples, lastSeen, threatId, threatExpiresAt, warningDedupeUntil,
            action, actionTargetId, actionDimension, actionExecuteAt, actionRecoverUntil,
            lure, destination, routeFailures, retryAfter, cadence, withdrawUntil);
    }

    public EldritchWatcherState withSubject(
        final Optional<UUID> subject,
        final Optional<EvidenceType> type,
        final long expiresAt,
        final int samples
    ) {
        return new EldritchWatcherState(schemaVersion, anchor, focus, mode, subject, type,
            subject.isEmpty() ? 0L : expiresAt, subject.isEmpty() ? 0 : samples,
            lastSeen, threatId, threatExpiresAt, warningDedupeUntil,
            action, actionTargetId, actionDimension, actionExecuteAt, actionRecoverUntil,
            lure, destination, routeFailures, retryAfter, cadence, withdrawUntil);
    }

    public EldritchWatcherState withLastSeen(final Optional<TimedSite> updated) {
        return new EldritchWatcherState(schemaVersion, anchor, focus, mode, subjectId, evidenceType,
            evidenceExpiresAt, attentionSamples, updated, threatId, threatExpiresAt, warningDedupeUntil,
            action, actionTargetId, actionDimension, actionExecuteAt, actionRecoverUntil,
            lure, destination, routeFailures, retryAfter, cadence, withdrawUntil);
    }

    public EldritchWatcherState withThreat(final Optional<UUID> threat, final long expiresAt, final long dedupeUntil) {
        return new EldritchWatcherState(schemaVersion, anchor, focus, mode, subjectId, evidenceType,
            evidenceExpiresAt, attentionSamples, lastSeen, threat,
            threat.isEmpty() ? 0L : expiresAt, dedupeUntil,
            action, actionTargetId, actionDimension, actionExecuteAt, actionRecoverUntil,
            lure, destination, routeFailures, retryAfter, cadence, withdrawUntil);
    }

    public EldritchWatcherState withAction(
        final ActionType updated,
        final Optional<UUID> target,
        final Optional<String> dimension,
        final long executeAt,
        final long recoverUntil
    ) {
        return new EldritchWatcherState(schemaVersion, anchor, focus, mode, subjectId, evidenceType,
            evidenceExpiresAt, attentionSamples, lastSeen, threatId, threatExpiresAt, warningDedupeUntil,
            updated, target, dimension, executeAt, recoverUntil,
            lure, destination, routeFailures, retryAfter, cadence, withdrawUntil);
    }

    public EldritchWatcherState withLure(final Optional<TimedSite> updated) {
        return new EldritchWatcherState(schemaVersion, anchor, focus, mode, subjectId, evidenceType,
            evidenceExpiresAt, attentionSamples, lastSeen, threatId, threatExpiresAt, warningDedupeUntil,
            action, actionTargetId, actionDimension, actionExecuteAt, actionRecoverUntil,
            updated, destination, routeFailures, retryAfter, cadence, withdrawUntil);
    }

    public EldritchWatcherState withDestination(final Optional<TimedSite> updated) {
        return new EldritchWatcherState(schemaVersion, anchor, focus, mode, subjectId, evidenceType,
            evidenceExpiresAt, attentionSamples, lastSeen, threatId, threatExpiresAt, warningDedupeUntil,
            action, actionTargetId, actionDimension, actionExecuteAt, actionRecoverUntil,
            lure, updated, routeFailures, retryAfter, cadence, withdrawUntil);
    }

    public EldritchWatcherState withRouteFailures(final int failures, final long retryDeadline) {
        return new EldritchWatcherState(schemaVersion, anchor, focus, mode, subjectId, evidenceType,
            evidenceExpiresAt, attentionSamples, lastSeen, threatId, threatExpiresAt, warningDedupeUntil,
            action, actionTargetId, actionDimension, actionExecuteAt, actionRecoverUntil,
            lure, destination, failures, retryDeadline, cadence, withdrawUntil);
    }

    public EldritchWatcherState withCadence(final Cadence updated) {
        return new EldritchWatcherState(schemaVersion, anchor, focus, mode, subjectId, evidenceType,
            evidenceExpiresAt, attentionSamples, lastSeen, threatId, threatExpiresAt, warningDedupeUntil,
            action, actionTargetId, actionDimension, actionExecuteAt, actionRecoverUntil,
            lure, destination, routeFailures, retryAfter, updated, withdrawUntil);
    }

    public EldritchWatcherState withWithdrawUntil(final long updated) {
        return new EldritchWatcherState(schemaVersion, anchor, focus, mode, subjectId, evidenceType,
            evidenceExpiresAt, attentionSamples, lastSeen, threatId, threatExpiresAt, warningDedupeUntil,
            action, actionTargetId, actionDimension, actionExecuteAt, actionRecoverUntil,
            lure, destination, routeFailures, retryAfter, cadence, updated);
    }

    public EldritchWatcherState releasedEncounter() {
        return withSubject(Optional.empty(), Optional.empty(), 0L, 0)
            .withAction(EldritchWatcherRules.ActionType.NONE, Optional.empty(), Optional.empty(), 0L, actionRecoverUntil)
            .withDestination(Optional.empty())
            .withLastSeen(Optional.empty());
    }

    public EldritchWatcherState clearedForDimensionChange() {
        return releasedEncounter()
            .withFocus(Optional.empty())
            .withLure(Optional.empty())
            .withThreat(Optional.empty(), 0L, 0L)
            .withMode(Mode.QUIET_VIGIL);
    }

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        anchor.ifPresent(site -> {
            tag.putString("AnchorDimension", site.dimension());
            tag.putLong("AnchorPosition", site.position().asLong());
        });
        writeTimedSite(tag, "Focus", focus);
        tag.putString("Mode", mode.name().toLowerCase(Locale.ROOT));
        subjectId.ifPresent(id -> tag.putString("Subject", id.toString()));
        evidenceType.ifPresent(type -> tag.putString("Evidence", type.name().toLowerCase(Locale.ROOT)));
        tag.putLong("EvidenceExpiresAt", evidenceExpiresAt);
        tag.putInt("AttentionSamples", attentionSamples);
        writeTimedSite(tag, "LastSeen", lastSeen);
        threatId.ifPresent(id -> tag.putString("Threat", id.toString()));
        tag.putLong("ThreatExpiresAt", threatExpiresAt);
        tag.putLong("WarningDedupeUntil", warningDedupeUntil);
        tag.putString("Action", action.name().toLowerCase(Locale.ROOT));
        actionTargetId.ifPresent(id -> tag.putString("ActionTarget", id.toString()));
        actionDimension.ifPresent(dimension -> tag.putString("ActionDimension", dimension));
        tag.putLong("ActionExecuteAt", actionExecuteAt);
        tag.putLong("ActionRecoverUntil", actionRecoverUntil);
        writeTimedSite(tag, "Lure", lure);
        tag.putInt("RouteFailures", routeFailures);
        tag.putLong("RetryAfter", retryAfter);
        tag.putLong("WithdrawUntil", withdrawUntil);
        return tag;
    }

    private static void writeTimedSite(final CompoundTag tag, final String key, final Optional<TimedSite> site) {
        site.ifPresent(value -> {
            tag.putString(key + "Dimension", value.dimension());
            tag.putLong(key + "Position", value.position().asLong());
            tag.putLong(key + "ExpiresAt", value.expiresAt());
        });
    }

    public static EldritchWatcherState read(
        final CompoundTag tag,
        final UUID watcherId,
        final String currentDimension,
        final long now
    ) {
        final Optional<Site> anchor = readAnchor(tag);
        if (tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty(watcherId, now).withAnchor(anchor);
        }
        final Mode mode = parseEnum(Mode.values(), tag.getStringOr("Mode", "")).orElse(Mode.QUIET_VIGIL);
        Optional<UUID> subject = parseUuid(tag.getStringOr("Subject", ""));
        Optional<EvidenceType> evidence = parseEnum(EvidenceType.values(), tag.getStringOr("Evidence", ""));
        long evidenceExpiresAt = EldritchWatcherRules.clampDeadline(
            tag.getLongOr("EvidenceExpiresAt", 0L), now, EldritchWatcherRules.SEEN_EVIDENCE_TICKS
        );
        if (subject.isEmpty() || evidence.isEmpty() || evidenceExpiresAt <= now) {
            subject = Optional.empty();
            evidence = Optional.empty();
            evidenceExpiresAt = 0L;
        }
        Optional<UUID> threat = parseUuid(tag.getStringOr("Threat", ""));
        long threatExpiresAt = EldritchWatcherRules.clampDeadline(
            tag.getLongOr("ThreatExpiresAt", 0L), now, EldritchWatcherRules.SEEN_EVIDENCE_TICKS
        );
        if (threat.isEmpty() || threatExpiresAt <= now) {
            threat = Optional.empty();
            threatExpiresAt = 0L;
        }
        Optional<ActionType> action = parseEnum(ActionType.values(), tag.getStringOr("Action", ""));
        Optional<UUID> actionTarget = parseUuid(tag.getStringOr("ActionTarget", ""));
        final String actionDimension = tag.getStringOr("ActionDimension", "");
        long actionExecuteAt = EldritchWatcherRules.clampDeadline(
            tag.getLongOr("ActionExecuteAt", 0L), now,
            EldritchWatcherRules.REVELATION_WINDUP_TICKS + EldritchWatcherRules.REVELATION_RECOVERY_TICKS
        );
        long actionRecoverUntil = EldritchWatcherRules.clampDeadline(
            tag.getLongOr("ActionRecoverUntil", 0L), now, EldritchWatcherRules.REVELATION_RECOVERY_TICKS
        );
        final boolean actionValid = action.isPresent()
            && (action.orElseThrow() == ActionType.NONE
                || (actionTarget.isPresent()
                    && !actionDimension.isEmpty()
                    && actionDimension.equals(currentDimension)
                    && actionExecuteAt > now));
        if (!actionValid || action.orElseThrow() == ActionType.NONE) {
            if (action.isPresent() && action.orElseThrow() != ActionType.NONE) {
                actionRecoverUntil = EldritchWatcherRules.saturatingAdd(
                    Math.max(0L, now), EldritchWatcherRules.REVELATION_RECOVERY_TICKS
                );
            }
            action = Optional.of(ActionType.NONE);
            actionTarget = Optional.empty();
            actionExecuteAt = 0L;
        }
        return new EldritchWatcherState(
            SCHEMA_VERSION,
            anchor,
            readTimedSite(tag, "Focus", currentDimension, now, EldritchWatcherRules.FOCUS_RETENTION_TICKS),
            mode == Mode.INTERCEPTING || mode == Mode.EXTERNAL_LURE ? Mode.QUIET_VIGIL : mode,
            subject,
            evidence,
            evidenceExpiresAt,
            Math.clamp(tag.getIntOr("AttentionSamples", 0), 0, EldritchWatcherRules.ESCALATION_SAMPLES),
            readTimedSite(tag, "LastSeen", currentDimension, now, EldritchWatcherRules.LAST_SEEN_TICKS),
            threat,
            threatExpiresAt,
            EldritchWatcherRules.clampDeadline(
                tag.getLongOr("WarningDedupeUntil", 0L), now, EldritchWatcherRules.WARNING_DEDUPE_TICKS
            ),
            action.orElseThrow(),
            actionTarget,
            action.orElseThrow() == ActionType.NONE ? Optional.empty()
                : Optional.of(actionDimension),
            actionExecuteAt,
            actionRecoverUntil,
            readTimedSite(tag, "Lure", currentDimension, now, EldritchWatcherRules.LURE_TICKS),
            Optional.empty(),
            tag.getIntOr("RouteFailures", 0),
            EldritchWatcherRules.clampDeadline(
                tag.getLongOr("RetryAfter", 0L), now, EldritchWatcherRules.ROUTE_BACKOFF_TICKS
            ),
            Cadence.staggered(watcherId, now),
            EldritchWatcherRules.clampDeadline(
                tag.getLongOr("WithdrawUntil", 0L), now, EldritchWatcherRules.WITHDRAW_TICKS
            )
        );
    }

    private static Optional<Site> readAnchor(final CompoundTag tag) {
        final String dimension = tag.getStringOr("AnchorDimension", "");
        final long packed = tag.getLongOr("AnchorPosition", Long.MIN_VALUE);
        if (dimension.isEmpty() || packed == Long.MIN_VALUE) {
            return Optional.empty();
        }
        return Optional.of(new Site(dimension, BlockPos.of(packed)));
    }

    private static Optional<TimedSite> readTimedSite(
        final CompoundTag tag,
        final String key,
        final String currentDimension,
        final long now,
        final long horizonTicks
    ) {
        final String dimension = tag.getStringOr(key + "Dimension", "");
        final long packed = tag.getLongOr(key + "Position", Long.MIN_VALUE);
        final long expiresAt = EldritchWatcherRules.clampDeadline(
            tag.getLongOr(key + "ExpiresAt", 0L), now, horizonTicks
        );
        if (dimension.isEmpty() || packed == Long.MIN_VALUE || expiresAt <= now
            || !dimension.equals(currentDimension)) {
            return Optional.empty();
        }
        return Optional.of(new TimedSite(dimension, BlockPos.of(packed), expiresAt));
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
