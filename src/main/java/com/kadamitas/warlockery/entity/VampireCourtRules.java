package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class VampireCourtRules {
    public static final int MIN_PRESSURE = 0;
    public static final int MAX_PRESSURE = 1_000;
    public static final int DEFAULT_PRESSURE = 350;
    public static final int WATCH_PRESSURE = 500;
    public static final int HUNT_PRESSURE = 700;
    public static final int PRESSURE_INTERVAL_TICKS = 20;
    public static final int FEEDING_REDUCTION = 240;
    public static final long REPORT_EXPIRY_TICKS = 24_000L;
    public static final int MAX_REPORTS = 4;
    public static final int DECISION_INTERVAL_TICKS = 20;
    public static final int ENTITY_SCAN_INTERVAL_TICKS = 80;
    public static final double ENTITY_SCAN_RADIUS = 24.0D;
    public static final double ENTITY_RETAIN_RADIUS = 16.0D;
    public static final int MAX_CANDIDATES = 16;
    public static final int SHELTER_SCAN_INTERVAL_TICKS = 100;
    public static final int MAX_SHELTER_BLOCKS = 256;
    public static final int NAVIGATION_INTERVAL_TICKS = 20;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_RETRY_TICKS = 100;
    public static final int MAX_CLAIM_LEASE_TICKS = 200;
    public static final int MAX_COURT_MEMBERS = 8;
    public static final int MAX_ALERT_DEPTH = 1;
    public static final int FEEDBACK_INTERVAL_TICKS = 40;
    public static final int WAVERING_TICKS = 200;

    private VampireCourtRules() {
    }

    public static int reconcilePressure(
        final CreatureKind kind,
        final int pressure,
        final long lastUpdate,
        final long now
    ) {
        if (pressure < MIN_PRESSURE || pressure > MAX_PRESSURE) {
            throw new IllegalArgumentException("Pressure must be between 0 and 1000");
        }
        if (kind != CreatureKind.VAMPIRE) return 0;
        if (now <= lastUpdate) return pressure;
        final long quanta = (now - lastUpdate) / PRESSURE_INTERVAL_TICKS;
        return (int) Math.min(MAX_PRESSURE, pressure + Math.min(MAX_PRESSURE, quanta));
    }

    public static int afterOrdinaryFeed(final int pressure) {
        return Math.max(MIN_PRESSURE, Math.min(MAX_PRESSURE, pressure) - FEEDING_REDUCTION);
    }

    public static int afterAssaultFeed(final int pressure) {
        return Math.min(250, Math.max(MIN_PRESSURE, pressure));
    }

    public static Intent chooseIntent(
        final CreatureKind kind,
        final long dayTime,
        final boolean urgentShelter,
        final boolean directThreat,
        final boolean assaultLeader,
        final int pressure,
        final boolean hasMaster
    ) {
        if (urgentShelter) return Intent.SEEK_SHELTER;
        if (directThreat) return Intent.INTERCEPT;
        if (kind == CreatureKind.BLOOD_THRALL) {
            if (!hasMaster) return Intent.UNBOUND;
            return Intent.THRESHOLD_GUARD;
        }
        if (assaultLeader) return Intent.ASSAULT_LEAD;
        final long clock = Math.floorMod(dayTime, 24_000L);
        if (clock < 13_000L || clock > 23_000L) return Intent.SEEK_SHELTER;
        if (pressure >= HUNT_PRESSURE) return Intent.STALK;
        if (pressure >= WATCH_PRESSURE) return Intent.WATCH;
        return Intent.ROOST;
    }

    public static int scheduleOffset(final UUID id) {
        return Math.floorMod(id.hashCode(), 40);
    }

    public static boolean eligibleOrdinaryPrey(
        final boolean mortalPlayer,
        final boolean vampirePlayer,
        final boolean ownerProtected,
        final boolean family,
        final boolean excludedNpc,
        final boolean directAggressor
    ) {
        if (ownerProtected || family || excludedNpc || !mortalPlayer) return false;
        return !vampirePlayer || directAggressor;
    }

    public static boolean mayAttack(
        final boolean ordinarilyEligible,
        final boolean ownerProtected,
        final boolean family,
        final boolean directAggressor
    ) {
        return !ownerProtected && (directAggressor || ordinarilyEligible && !family);
    }

    public static boolean validMaster(
        final boolean loaded,
        final boolean livingFullVampire,
        final boolean sameDimension,
        final boolean masterIsThrall,
        final boolean cycle,
        final boolean courtMismatch
    ) {
        return loaded && livingFullVampire && sameDimension && !masterIsThrall && !cycle && !courtMismatch;
    }

    public static List<VictimReport> pruneReports(final List<VictimReport> reports, final long now) {
        return reports.stream()
            .filter(report -> now - report.encounteredAt() <= REPORT_EXPIRY_TICKS)
            .limit(MAX_REPORTS)
            .toList();
    }

    public static List<VictimReport> rememberVictim(
        final List<VictimReport> reports,
        final VictimReport incoming,
        final long now
    ) {
        final ArrayList<VictimReport> bounded = new ArrayList<>(pruneReports(reports, now));
        bounded.removeIf(report -> report.victimId().equals(incoming.victimId()));
        bounded.add(incoming);
        if (bounded.size() > MAX_REPORTS) {
            bounded.remove(bounded.stream().min(Comparator
                .comparingInt(VictimReport::importance)
                .thenComparingLong(VictimReport::encounteredAt)
                .thenComparing(VictimReport::victimId, Comparator.reverseOrder())).orElseThrow());
        }
        return List.copyOf(bounded);
    }

    public static AssaultComposition assaultComposition(final int wave) {
        return switch (wave) {
            case 1 -> new AssaultComposition(1, 1);
            case 2 -> new AssaultComposition(1, 3);
            case 3 -> new AssaultComposition(1, 4);
            default -> throw new IllegalArgumentException("Vampire assaults have exactly three waves");
        };
    }

    public static AssaultRole assaultRole(final boolean leader) {
        return leader ? AssaultRole.PREDATOR_LEADER : AssaultRole.BOUND_GUARD;
    }

    public static boolean mayAdvanceObjective(final CreatureKind kind, final AssaultRole role) {
        return kind == CreatureKind.VAMPIRE && role == AssaultRole.PREDATOR_LEADER;
    }

    public static boolean mayAttackAssaultObjective(
        final CreatureKind kind,
        final AssaultRole role,
        final boolean markedLeader,
        final boolean assignedTarget
    ) {
        return markedLeader && assignedTarget && mayAdvanceObjective(kind, role);
    }

    public static boolean decisionDue(
        final long nextDecisionAt,
        final long now,
        final boolean urgentShelter,
        final Intent currentIntent
    ) {
        return now >= nextDecisionAt || urgentShelter && currentIntent != Intent.SEEK_SHELTER;
    }

    public static boolean requiresUrgentShelter(
        final boolean exposedDay,
        final boolean fireResistant,
        final boolean onFire,
        final boolean inLava,
        final boolean unsafeContact,
        final boolean drowning,
        final boolean lowHealth
    ) {
        return exposedDay && !fireResistant || onFire || inLava || unsafeContact || drowning || lowHealth;
    }

    public static boolean feedbackDue(final long nextFeedbackAt, final long now, final boolean transition) {
        return transition || now >= nextFeedbackAt;
    }

    public static Intent afterWavering(
        final long now,
        final long waveringUntil,
        final boolean assaultMember
    ) {
        if (now < waveringUntil) return Intent.WAVERING;
        return assaultMember ? Intent.RETREAT : Intent.UNBOUND;
    }

    public static boolean navigationDue(final long lastNavigationAt, final long now) {
        return lastNavigationAt == 0L || now - lastNavigationAt >= NAVIGATION_INTERVAL_TICKS;
    }

    public static RouteRetry routeFailure(final int failures, final long now) {
        final int next = Math.min(MAX_ROUTE_FAILURES, Math.max(0, failures) + 1);
        return new RouteRetry(next, next == MAX_ROUTE_FAILURES ? saturatingAdd(now, ROUTE_RETRY_TICKS) : 0L);
    }

    public static RouteRetry routeSuccess() {
        return new RouteRetry(0, 0L);
    }

    public static long claimExpiry(final long now, final int requestedTicks) {
        return saturatingAdd(now, Math.max(0, Math.min(MAX_CLAIM_LEASE_TICKS, requestedTicks)));
    }

    public static int boundedMemberCount(final int members) {
        return Math.max(0, Math.min(MAX_COURT_MEMBERS, members));
    }

    public static int boundedAlertDepth(final int depth) {
        return Math.max(0, Math.min(MAX_ALERT_DEPTH, depth));
    }

    public static long saturatingAdd(final long value, final long increment) {
        if (increment <= 0L) return value;
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    public enum Intent {
        UNBOUND,
        ROOST,
        WATCH,
        STALK,
        FEED,
        SEEK_SHELTER,
        VEILED_REST,
        THRESHOLD_GUARD,
        INTERCEPT,
        WAVERING,
        RETREAT,
        ASSAULT_LEAD,
        RECOVER
    }

    public enum AssaultRole {
        PREDATOR_LEADER,
        BOUND_GUARD,
        UNBOUND
    }

    public enum ReportOutcome {
        FED,
        ESCAPED,
        RESISTED,
        LOST
    }

    public record VictimReport(
        UUID victimId,
        int x,
        int y,
        int z,
        long encounteredAt,
        ReportOutcome outcome,
        int importance
    ) {
        public VictimReport {
            Objects.requireNonNull(victimId);
            Objects.requireNonNull(outcome);
            importance = Math.max(0, Math.min(1_000, importance));
        }
    }

    public record AssaultComposition(int leaders, int guards) {
        public AssaultComposition {
            if (leaders != 1 || guards < 1 || leaders + guards > MAX_COURT_MEMBERS) {
                throw new IllegalArgumentException("Invalid bounded Vampire assault composition");
            }
        }

        public int total() {
            return leaders + guards;
        }
    }

    public record RouteRetry(int failures, long retryAfter) {
    }
}
