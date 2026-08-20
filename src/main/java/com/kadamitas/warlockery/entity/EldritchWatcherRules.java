package com.kadamitas.warlockery.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class EldritchWatcherRules {
    public static final int PERCEPTION_INTERVAL_TICKS = 20;
    public static final int MAX_ENTITIES_VISITED = 16;
    public static final int MAX_RETAINED_CANDIDATES = 8;
    public static final int MAX_LINE_OF_SIGHT_CLIPS = 8;
    public static final int PERCEPTION_RADIUS = 16;
    public static final int FOCUS_SCAN_INTERVAL_TICKS = 80;
    public static final int MAX_FOCUS_BLOCK_READS = 128;
    public static final int FOCUS_HORIZONTAL_RADIUS = 8;
    public static final int FOCUS_VERTICAL_RADIUS = 4;
    public static final int FOCUS_RETENTION_TICKS = 240;
    public static final int HAZARD_SCAN_INTERVAL_TICKS = 20;
    public static final int MAX_HAZARD_BLOCK_READS = 27;
    public static final int MAX_DESTINATION_CANDIDATES = 24;
    public static final int MAX_SAFETY_BLOCK_READS = 256;
    public static final int MOVEMENT_INTERVAL_TICKS = 20;
    public static final int DESTINATION_RADIUS = 8;
    public static final int DESTINATION_EXPIRY_TICKS = 60;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;
    public static final int ANCHOR_HOLD_RADIUS = 12;
    public static final int ANCHOR_CHASE_RADIUS = 20;
    public static final int THRESHOLD_RADIUS = 5;
    public static final double RECIPROCAL_GAZE_DOT = 0.94D;
    public static final int ESCALATION_SAMPLES = 2;
    public static final int SEEN_EVIDENCE_TICKS = 100;
    public static final int LAST_SEEN_TICKS = 100;
    public static final int REPORTED_HARM_TICKS = 80;
    public static final int WARNING_DEDUPE_TICKS = 40;
    public static final int WARNING_RADIUS = 12;
    public static final int MAX_WARNING_VISITS = 8;
    public static final int MAX_WARNING_RECIPIENTS = 3;
    public static final int ATTACK_RANGE = 14;
    public static final int REVELATION_WINDUP_TICKS = 20;
    public static final int REVELATION_RECOVERY_TICKS = 50;
    public static final float REVELATION_DAMAGE = 3.0F;
    public static final int GLOWING_TICKS = 100;
    public static final int DARKNESS_TICKS = 40;
    public static final double WITHDRAW_HEALTH_FRACTION = 0.25D;
    public static final int WITHDRAW_TICKS = 100;
    public static final int LURE_TICKS = 40;
    public static final int LURE_RADIUS = 16;
    public static final int OWNER_GUARD_RADIUS = 12;
    public static final int ATTRIBUTION_FRESHNESS_TICKS = 40;
    public static final long MAX_DEADLINE_HORIZON_TICKS = 20_000L;

    private EldritchWatcherRules() {
    }

    public enum Mode {
        QUIET_VIGIL,
        FOCUS_INSPECTION,
        OBSERVING,
        INTERCEPTING,
        EXPOSED_WITHDRAWAL,
        EXTERNAL_LURE,
        RETURNING
    }

    public enum EvidenceType {
        SEEN,
        RECIPROCAL_GAZE,
        THRESHOLD_BREACH,
        DIRECT_HARM,
        REPORTED_HARM
    }

    public enum ActionType {
        NONE,
        REVELATION
    }

    public static int stableOffset(final UUID id, final int modulus) {
        if (modulus <= 1) {
            return 0;
        }
        return (int) Math.floorMod(id.getLeastSignificantBits() ^ id.getMostSignificantBits(), (long) modulus);
    }

    public static long saturatingAdd(final long base, final long delta) {
        final long sum = base + delta;
        return sum < base ? Long.MAX_VALUE : sum;
    }

    public static long clampDeadline(final long deadline, final long now, final long horizonTicks) {
        if (deadline <= 0L) {
            return 0L;
        }
        final long horizon = Math.min(Math.max(0L, horizonTicks), MAX_DEADLINE_HORIZON_TICKS);
        return Math.min(deadline, saturatingAdd(Math.max(0L, now), horizon));
    }

    public static boolean due(final long deadline, final long now) {
        return deadline <= 0L || now >= deadline;
    }

    public static long evidenceLifetimeTicks(final EvidenceType type) {
        return switch (type) {
            case REPORTED_HARM -> REPORTED_HARM_TICKS;
            default -> SEEN_EVIDENCE_TICKS;
        };
    }

    public static boolean reciprocalGaze(final double normalizedDot, final boolean lineOfSight) {
        return lineOfSight && normalizedDot >= RECIPROCAL_GAZE_DOT;
    }

    public static boolean thresholdBreach(final double distanceSqrToAnchorOrFocus) {
        return distanceSqrToAnchorOrFocus <= (double) THRESHOLD_RADIUS * THRESHOLD_RADIUS;
    }

    public static boolean escalationReady(
        final EvidenceType type,
        final int consecutiveSamples,
        final boolean independentlyVisible
    ) {
        return switch (type) {
            case DIRECT_HARM -> true;
            case RECIPROCAL_GAZE, THRESHOLD_BREACH -> consecutiveSamples >= ESCALATION_SAMPLES;
            case REPORTED_HARM -> independentlyVisible;
            case SEEN -> false;
        };
    }

    public static boolean withdrawRequired(final double healthFraction) {
        return healthFraction <= WITHDRAW_HEALTH_FRACTION;
    }

    public static boolean withinAttackRange(final double distanceSqr) {
        return distanceSqr <= (double) ATTACK_RANGE * ATTACK_RANGE;
    }

    public static boolean withinChaseEnvelope(final double watcherToAnchorSqr, final double subjectToAnchorSqr) {
        final double limit = (double) ANCHOR_CHASE_RADIUS * ANCHOR_CHASE_RADIUS;
        return watcherToAnchorSqr <= limit && subjectToAnchorSqr <= limit;
    }

    public static Mode selectMode(
        final boolean urgentHazard,
        final boolean withdrawing,
        final boolean escalatedSubject,
        final boolean lureActive,
        final boolean observedSubject,
        final boolean focusHeld,
        final boolean nearAnchor
    ) {
        if (urgentHazard) {
            return Mode.EXPOSED_WITHDRAWAL;
        }
        if (escalatedSubject) {
            return Mode.INTERCEPTING;
        }
        if (withdrawing) {
            return Mode.EXPOSED_WITHDRAWAL;
        }
        if (lureActive) {
            return Mode.EXTERNAL_LURE;
        }
        if (observedSubject) {
            return Mode.OBSERVING;
        }
        if (focusHeld) {
            return Mode.FOCUS_INSPECTION;
        }
        return nearAnchor ? Mode.QUIET_VIGIL : Mode.RETURNING;
    }

    public record CandidateFacts(
        boolean directThreat,
        boolean validReport,
        boolean currentSubject,
        boolean thresholdBreach,
        boolean reciprocalGaze,
        double distanceSqr,
        UUID id
    ) {
    }

    public static Comparator<CandidateFacts> candidateOrder() {
        return Comparator
            .comparing((CandidateFacts facts) -> !facts.directThreat())
            .thenComparing(facts -> !facts.validReport())
            .thenComparing(facts -> !facts.thresholdBreach())
            .thenComparing(facts -> !facts.reciprocalGaze())
            .thenComparing(facts -> !facts.currentSubject())
            .thenComparingDouble(CandidateFacts::distanceSqr)
            .thenComparing(CandidateFacts::id);
    }

    public static boolean warningCompatible(
        final Optional<UUID> senderOwner,
        final Optional<UUID> recipientOwner
    ) {
        if (senderOwner.isPresent()) {
            return recipientOwner.isPresent()
                && senderOwner.orElseThrow().equals(recipientOwner.orElseThrow());
        }
        return recipientOwner.isEmpty();
    }

    public static boolean warningDeduped(final long dedupeUntil, final long now) {
        return dedupeUntil > now;
    }

    public record RevelationFacts(
        boolean intercepting,
        boolean targetLoadedAlive,
        boolean sameDimension,
        boolean relationValid,
        boolean withinAttackRange,
        boolean withinChaseEnvelope,
        boolean lineOfSight,
        boolean urgentHazard,
        boolean withdrawing,
        boolean inRecoveryOrBackoff
    ) {
    }

    public static boolean mayStartRevelation(final RevelationFacts facts) {
        return facts.intercepting()
            && facts.targetLoadedAlive()
            && facts.sameDimension()
            && facts.relationValid()
            && facts.withinAttackRange()
            && facts.withinChaseEnvelope()
            && facts.lineOfSight()
            && !facts.urgentHazard()
            && !facts.withdrawing()
            && !facts.inRecoveryOrBackoff();
    }

    public static boolean lureOutranked(
        final boolean urgentHazard,
        final boolean executingAction,
        final boolean interceptingDirectHarm
    ) {
        return urgentHazard || executingAction || interceptingDirectHarm;
    }

    public static int routeFailures(final int current) {
        return Math.min(MAX_ROUTE_FAILURES, Math.max(0, current) + 1);
    }

    public static long routeBackoffUntil(final int failures, final long now) {
        return failures >= MAX_ROUTE_FAILURES ? saturatingAdd(Math.max(0L, now), ROUTE_BACKOFF_TICKS) : 0L;
    }

    public static int[] focusLayers(final long scanPhase) {
        final int[] secondary = {1, -1, 2, -2, 3, -3, 4, -4};
        return new int[] {0, secondary[(int) Math.floorMod(scanPhase, secondary.length)]};
    }

    public static int focusLayerStart(final long scanPhase, final int layerSize) {
        if (layerSize <= 1) {
            return 0;
        }
        return (int) Math.floorMod(scanPhase * 37L, (long) layerSize);
    }

    public static boolean ownerGuardEvidence(
        final boolean warlockeryOwnerBound,
        final boolean freshAttribution,
        final boolean attackerValid,
        final boolean ownerNearAnchor,
        final boolean lineOfSightToAttacker
    ) {
        return warlockeryOwnerBound
            && freshAttribution
            && attackerValid
            && ownerNearAnchor
            && lineOfSightToAttacker;
    }

    public static List<long[]> destinationOffsets(final UUID id) {
        final int rotation = stableOffset(id, 8);
        final List<long[]> offsets = new ArrayList<>(MAX_DESTINATION_CANDIDATES);
        final int[] rings = {3, 5, 8};
        final int[] heights = {1, 0, -1};
        for (final int ring : rings) {
            for (int step = 0; step < 8; step++) {
                final int direction = (step + rotation) % 8;
                final double angle = direction * Math.PI / 4.0D;
                final int dx = (int) Math.round(Math.cos(angle) * ring);
                final int dz = (int) Math.round(Math.sin(angle) * ring);
                final int dy = heights[step % heights.length];
                offsets.add(new long[] {dx, dy, dz});
                if (offsets.size() >= MAX_DESTINATION_CANDIDATES) {
                    return List.copyOf(offsets);
                }
            }
        }
        return List.copyOf(offsets);
    }
}
