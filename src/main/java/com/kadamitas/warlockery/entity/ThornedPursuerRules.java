package com.kadamitas.warlockery.entity;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ThornedPursuerRules {
    public static final int MAX_SAFE_READS = 128;
    public static final int QUARRY_SCAN_CADENCE = 40;
    public static final double QUARRY_SCAN_RADIUS = 16.0D;
    public static final int MAX_SCAN_VISITS = 8;
    public static final int MAX_SCAN_SIGHT_RAYS = 2;
    public static final int OWNER_HINT_TICKS = 1_200;
    public static final int BAY_TICKS = 40;
    public static final int EPISODE_BUDGET = 1_200;
    public static final int EPISODE_COOLDOWN = 200;
    public static final int TRAIL_CAPACITY = 4;
    public static final int TRAIL_CADENCE = 20;
    public static final int TRAIL_EXPIRY = 200;
    public static final int SIGHT_CADENCE = 10;
    public static final double RETENTION_DISTANCE_SQR = 32.0D * 32.0D;
    public static final double LEASH_DISTANCE_SQR = 48.0D * 48.0D;
    public static final double HOLD_DISTANCE_SQR = 9.0D;
    public static final int HOLD_TELEGRAPH_TICKS = 20;
    public static final int HOLD_EFFECT_TICKS = 60;
    public static final int SNARE_COOLDOWN = 400;
    public static final int PRESS_CADENCE = 20;
    public static final int PRESS_TIMEOUT = 200;
    public static final int BREAK_CADENCE = 20;
    public static final double RECOVER_DISTANCE_SQR = 4.0D;
    public static final int RECOVER_TIMEOUT = 400;
    public static final int PATH_CADENCE = 20;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;
    public static final int ATTRIBUTION_FRESHNESS_TICKS = 40;
    public static final int RETALIATION_WINDOW = 60;
    public static final int RETALIATION_COOLDOWN = 20;
    public static final int MAX_ESCORTS = 2;
    public static final int ESCORT_COOLDOWN = 1_200;
    public static final int BOUNDED_FUTURE_SENTINEL = 20_000;

    private static final Comparator<UUID> UUID_ORDER = Comparator
        .comparingLong((UUID id) -> id.getMostSignificantBits() ^ Long.MIN_VALUE)
        .thenComparingLong(id -> id.getLeastSignificantBits() ^ Long.MIN_VALUE);

    private ThornedPursuerRules() {}

    public enum Phase { ANCHORED, BAY, COURSE, SET, PRESS, BREAK, RECOVER, ESCAPE }
    public enum Priority { HAZARD, COMBAT, EPISODE, ROUTINE }
    public enum BreakReason {
        QUARRY_DEAD, QUARRY_REMOVED, QUARRY_ILLEGAL, QUARRY_UNLOADED, QUARRY_DIMENSION,
        QUARRY_OUT_OF_RETENTION, TRAIL_EXPIRED, LEASH_EXCEEDED, BUDGET, ROUTE_FAILED,
        HAZARD, CANCELLED
    }
    public enum Work {
        EXPENSIVE(16), PATH(8), ENTITY_VISIT(128), SIGHT_RAY(32), READ(512),
        SAFE_ENTITY_VISIT(128), HOLD(4), MELEE(8), RETALIATION(8), ESCORT(4), FEEDBACK(8);
        final int limit;
        Work(int limit) { this.limit = limit; }
    }

    public record QuarryCandidate(UUID id, double distanceSqr, boolean ownerHint) {}
    public record QuarryFacts(boolean alive, boolean self, boolean sameKind,
                              boolean creativeOrSpectator, boolean sleeping, boolean trading,
                              boolean raid, boolean panic, boolean breeding, boolean relationLegal) {}
    public static boolean eligibleQuarry(QuarryFacts facts) {
        return facts.alive() && !facts.self() && !facts.sameKind() && !facts.creativeOrSpectator()
            && !facts.sleeping() && !facts.trading() && !facts.raid() && !facts.panic()
            && !facts.breeding() && facts.relationLegal();
    }
    public record Offset(int x, int y, int z) {}
    public record SafeFacts(boolean haloLoaded, boolean borderInside, boolean collisionFree,
                            boolean stableSupport, boolean unoccupied,
                            int currentHazards, int candidateHazards) {}
    public record BreakFacts(boolean budgetExhausted, boolean leashExceeded,
                             boolean retentionExceeded, boolean trailExpired,
                             boolean routeFailed) {}
    public static Optional<BreakReason> scheduledBreakReason(BreakFacts facts) {
        if (facts.budgetExhausted()) return Optional.of(BreakReason.BUDGET);
        if (facts.leashExceeded()) return Optional.of(BreakReason.LEASH_EXCEEDED);
        if (facts.retentionExceeded()) return Optional.of(BreakReason.QUARRY_OUT_OF_RETENTION);
        if (facts.trailExpired()) return Optional.of(BreakReason.TRAIL_EXPIRED);
        if (facts.routeFailed()) return Optional.of(BreakReason.ROUTE_FAILED);
        return Optional.empty();
    }
    public record ReleaseFacts(boolean sameDimension, boolean resolved, boolean alive,
                               boolean removed, boolean legal) {}
    public static Optional<BreakReason> immediateReleaseReason(ReleaseFacts facts) {
        if (!facts.sameDimension()) return Optional.of(BreakReason.QUARRY_DIMENSION);
        if (!facts.resolved()) return Optional.of(BreakReason.QUARRY_UNLOADED);
        if (facts.removed()) return Optional.of(BreakReason.QUARRY_REMOVED);
        if (!facts.alive()) return Optional.of(BreakReason.QUARRY_DEAD);
        if (!facts.legal()) return Optional.of(BreakReason.QUARRY_ILLEGAL);
        return Optional.empty();
    }

    private static final List<Offset> SAFE_OFFSETS = List.of(
        new Offset(2, 0, 0), new Offset(-2, 0, 0), new Offset(0, 0, 2), new Offset(0, 0, -2),
        new Offset(3, 1, 0), new Offset(-3, 1, 0), new Offset(0, 1, 3), new Offset(0, 1, -3),
        new Offset(4, 0, 2), new Offset(4, 0, -2), new Offset(-4, 0, 2), new Offset(-4, 0, -2),
        new Offset(6, -1, 0), new Offset(-6, -1, 0), new Offset(0, 2, 6), new Offset(0, 2, -6));

    public static List<Offset> safeOffsets() { return SAFE_OFFSETS; }
    public static boolean safeDestination(SafeFacts facts) {
        return facts.haloLoaded() && facts.borderInside() && facts.collisionFree()
            && facts.stableSupport() && facts.unoccupied()
            && facts.candidateHazards() < facts.currentHazards();
    }

    public static Priority priority(boolean hazard, boolean combat, boolean episode) {
        if (hazard) return Priority.HAZARD;
        if (combat) return Priority.COMBAT;
        return episode ? Priority.EPISODE : Priority.ROUTINE;
    }

    public static Optional<UUID> selectQuarry(List<QuarryCandidate> candidates) {
        return candidates.stream().min(Comparator
            .comparingDouble(QuarryCandidate::distanceSqr)
            .thenComparing(candidate -> !candidate.ownerHint())
            .thenComparing(QuarryCandidate::id, UUID_ORDER)).map(QuarryCandidate::id);
    }

    public static boolean withinQuarryScan(double distanceSqr) {
        return distanceSqr <= QUARRY_SCAN_RADIUS * QUARRY_SCAN_RADIUS;
    }
    public static boolean withinRetention(double distanceSqr) { return distanceSqr <= RETENTION_DISTANCE_SQR; }
    public static boolean withinLeash(double distanceSqr) { return distanceSqr <= LEASH_DISTANCE_SQR; }

    public static boolean bayElapsed(int ticks) { return ticks >= BAY_TICKS; }
    public static boolean cooldownDue(int remaining) { return remaining <= 0; }
    public static boolean cadenceDue(int tickCount, int entityId, int cadence) {
        return cadence > 0 && Math.floorMod(tickCount + entityId, cadence) == 0;
    }
    public static boolean attributionFresh(int age) {
        return age >= 0 && age <= ATTRIBUTION_FRESHNESS_TICKS;
    }
    public static boolean mayEnterSet(double distanceSqr, boolean sight, int cooldown, boolean hazard) {
        return !hazard && sight && cooldownDue(cooldown) && distanceSqr <= HOLD_DISTANCE_SQR;
    }
    public static boolean holdMayCommit(int elapsed, boolean legal, boolean sight, double distanceSqr) {
        return elapsed >= HOLD_TELEGRAPH_TICKS && legal && sight && distanceSqr <= HOLD_DISTANCE_SQR;
    }
    public static boolean recoverComplete(double distanceSqr, int failures, int elapsed) {
        return distanceSqr <= RECOVER_DISTANCE_SQR || failures >= MAX_ROUTE_FAILURES || elapsed >= RECOVER_TIMEOUT;
    }
    public static boolean episodeBudgetReached(Phase phase, int elapsed) {
        return switch (phase) {
            case BAY, COURSE, SET, PRESS -> elapsed >= EPISODE_BUDGET;
            default -> false;
        };
    }
    public static boolean trailExpired(int age) { return age >= TRAIL_EXPIRY; }
    public static int recordRouteFailure(int failures) { return Math.min(MAX_ROUTE_FAILURES, Math.max(0, failures) + 1); }
    public record RouteFailure(int failures, int backoffTicks) {}
    public static RouteFailure recordRouteFailure(int failures, int backoffTicks) {
        if (backoffTicks > 0) return new RouteFailure(Math.max(0, failures), backoffTicks);
        int next = recordRouteFailure(failures);
        return next >= MAX_ROUTE_FAILURES
            ? new RouteFailure(0, ROUTE_BACKOFF_TICKS) : new RouteFailure(next, 0);
    }
    public static boolean routeAttemptDeferred(int backoffTicks) { return backoffTicks > 0; }
    public static int tickRouteBackoff(int backoffTicks) { return Math.max(0, backoffTicks - 1); }
    public static boolean routeBackoffRequired(int failures) { return failures >= MAX_ROUTE_FAILURES; }
    public static int escortSlots(int alive) { return Math.max(0, MAX_ESCORTS - Math.max(0, alive)); }
    public static int nextLadderStep(boolean consecutive, int previous) {
        return consecutive ? Math.min(2, Math.max(0, previous) + 1) : 0;
    }
    public static float retaliationDamage(float acceptedDamage, int ladderStep,
                                          double distanceSqr, boolean fresh, int cooldown) {
        if (acceptedDamage <= 0.0F || distanceSqr > HOLD_DISTANCE_SQR || !fresh || cooldown > 0) return 0.0F;
        float base = Math.min(6.0F, 2.0F + acceptedDamage * 0.25F);
        float multiplier = switch (Math.clamp(ladderStep, 0, 2)) {
            case 1 -> 1.25F;
            case 2 -> 1.5F;
            default -> 1.0F;
        };
        return Math.min(6.0F, base * multiplier);
    }

    public static float acceptedEffectiveLoss(float beforeHealth, float beforeAbsorption,
                                              float afterHealth, float afterAbsorption) {
        return Math.max(0.0F, beforeHealth + beforeAbsorption - afterHealth - afterAbsorption);
    }

    public static final class RetaliationLedger {
        private final int capacity;
        private final LinkedHashMap<UUID, RetaliationEntry> entries = new LinkedHashMap<>();

        public RetaliationLedger(int capacity) { this.capacity = Math.max(1, capacity); }
        public boolean mayRetaliate(UUID attacker) {
            RetaliationEntry entry = entries.get(attacker);
            return entry == null || entry.cooldown == 0;
        }
        public int ladderStep(UUID attacker) {
            RetaliationEntry entry = entries.get(attacker);
            return entry == null ? 0 : entry.ladderStep;
        }
        public int nextLadderStep(UUID attacker) {
            RetaliationEntry entry = entries.get(attacker);
            return ThornedPursuerRules.nextLadderStep(entry != null && entry.window > 0,
                entry == null ? 0 : entry.ladderStep);
        }
        public int recordRetaliation(UUID attacker) {
            RetaliationEntry entry = entries.get(attacker);
            int next = nextLadderStep(attacker);
            if (entry == null) {
                if (entries.size() >= capacity) entries.remove(entries.keySet().iterator().next());
                entry = new RetaliationEntry();
                entries.put(attacker, entry);
            }
            entry.ladderStep = next;
            entry.cooldown = RETALIATION_COOLDOWN;
            entry.window = RETALIATION_WINDOW;
            return next;
        }
        public void tick() {
            for (RetaliationEntry entry : entries.values()) {
                entry.cooldown = Math.max(0, entry.cooldown - 1);
                entry.window = Math.max(0, entry.window - 1);
                if (entry.window == 0) entry.ladderStep = 0;
            }
        }
        public int size() { return entries.size(); }
        public boolean contains(UUID attacker) { return entries.containsKey(attacker); }
        public void clear() { entries.clear(); }

        private static final class RetaliationEntry {
            private int cooldown;
            private int window;
            private int ladderStep;
        }
    }

    public record LevelBudget(int serverTick, int expensive, int paths, int entityVisits,
                              int sightRays, int reads, int safeEntityVisits, int holds,
                              int melees, int retaliations, int escorts, int feedback) {
        public static LevelBudget empty(int serverTick) {
            return new LevelBudget(serverTick, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        public Optional<LevelBudget> take(Work work) {
            int current = switch (work) {
                case EXPENSIVE -> expensive; case PATH -> paths; case ENTITY_VISIT -> entityVisits;
                case SIGHT_RAY -> sightRays; case READ -> reads; case SAFE_ENTITY_VISIT -> safeEntityVisits;
                case HOLD -> holds; case MELEE -> melees; case RETALIATION -> retaliations;
                case ESCORT -> escorts; case FEEDBACK -> feedback;
            };
            if (current >= work.limit) return Optional.empty();
            return Optional.of(new LevelBudget(serverTick,
                expensive + (work == Work.EXPENSIVE ? 1 : 0), paths + (work == Work.PATH ? 1 : 0),
                entityVisits + (work == Work.ENTITY_VISIT ? 1 : 0), sightRays + (work == Work.SIGHT_RAY ? 1 : 0),
                reads + (work == Work.READ ? 1 : 0), safeEntityVisits + (work == Work.SAFE_ENTITY_VISIT ? 1 : 0),
                holds + (work == Work.HOLD ? 1 : 0), melees + (work == Work.MELEE ? 1 : 0),
                retaliations + (work == Work.RETALIATION ? 1 : 0), escorts + (work == Work.ESCORT ? 1 : 0),
                feedback + (work == Work.FEEDBACK ? 1 : 0)));
        }
    }
}
