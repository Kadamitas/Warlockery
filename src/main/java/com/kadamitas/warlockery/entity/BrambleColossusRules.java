package com.kadamitas.warlockery.entity;

import java.util.UUID;

public final class BrambleColossusRules {
    public static final double HELD_RADIUS = 10.0D;
    public static final double HELD_VERTICAL = 5.0D;
    public static final double LEASH_RADIUS = 14.0D;
    public static final double CORRUPT_POST_DISTANCE = 48.0D;
    public static final int EVIDENCE_FRESHNESS_TICKS = 40;
    public static final int DISPLAY_COOLDOWN_TICKS = 600;
    public static final int CIRCUIT_COOLDOWN_TICKS = 2400;
    public static final int MAX_CADENCE_SENTINEL = 20_000;
    public static final int MAX_NERVE = 100;
    public static final int FALTER_THRESHOLD = 25;
    public static final int BIND_THRESHOLD = 26;
    public static final int RECOVERY_THRESHOLD = 50;
    public static final int SWEEP_CADENCE = 40;
    public static final int ALARM_OFFSET = 20;
    public static final int MELEE_CADENCE = 20;
    public static final int SUSTAIN_CADENCE = 40;
    public static final int NERVE_RECOVERY_CADENCE = 60;
    public static final int PATH_CADENCE = 20;
    public static final int ROUTE_FAILURE_LIMIT = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;
    public static final int RETURN_TIMEOUT_TICKS = 300;
    public static final int LEVEL_EXPENSIVE_LIMIT = 16;
    public static final int LEVEL_PATH_LIMIT = 8;
    public static final int LEVEL_SWEEP_LIMIT = 8;
    public static final int LEVEL_RAW_VISIT_LIMIT = 96;
    public static final int LEVEL_RESOLVE_LIMIT = 32;
    public static final int LEVEL_RAY_LIMIT = 32;
    public static final int LEVEL_READ_LIMIT = 1024;
    public static final int LEVEL_OCCUPANCY_LIMIT = 128;
    public static final int LEVEL_DISPLAY_LIMIT = 4;
    public static final int LEVEL_MELEE_LIMIT = 8;
    public static final int LEVEL_THORN_LIMIT = 8;
    public static final int LEVEL_FEEDBACK_LIMIT = 8;
    public static final int HAZARD_OBSERVATION_READS = 18;
    public static final int SAFE_SEARCH_READS = 128;
    public static final int SAFE_SEARCH_VISITS = 32;
    public static final int SAFE_VISITS_PER_CANDIDATE = 8;
    public static final int[][] SAFE_CANDIDATES = {
        {1,0,0},{-1,0,0},{0,0,1},{0,0,-1},
        {2,0,0},{-2,0,0},{0,0,2},{0,0,-2},
        {3,1,0},{-3,1,0},{0,1,3},{0,1,-3},
        {6,2,0},{-6,2,0},{0,2,6},{0,2,-6}
    };

    private BrambleColossusRules() {}

    public enum Stance { PLANTED, PACING;
        public boolean allowsMovement() { return compareTo(PLANTED) > 0; }
    }
    public enum Phase { KEEPING, CIRCUIT, MARK, DISPLAY, THRESH, FALTER, WITHDRAW }
    public enum DisplayGate { WAIT_FOR_QUOTA, EMIT, ADVANCE }
    public enum Band { HAZARD, COMBAT, EPISODE, NERVE, ROUTINE }
    public enum Cancellation { REMOVAL, TELEPORT, DIMENSION_CHANGE, DEATH, DISCARD, TRADE, SLEEP, RAID, PANIC, BREEDING, HAZARD }

    public static boolean insideHeldVolume(double dx, double dy, double dz) {
        return dx * dx + dz * dz <= HELD_RADIUS * HELD_RADIUS && Math.abs(dy) <= HELD_VERTICAL;
    }
    public static boolean insideLeash(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz <= LEASH_RADIUS * LEASH_RADIUS;
    }
    public static double[] waypointOffset(int leg) {
        return switch (Math.floorMod(leg, 4)) { case 0 -> new double[]{6,0}; case 1 -> new double[]{0,6}; case 2 -> new double[]{-6,0}; default -> new double[]{0,-6}; };
    }
    public static double[] waypointApproachOffset(int leg) {
        double[] waypoint = waypointOffset(leg);
        return new double[] {waypoint[0] * (4.0D / 6.0D), waypoint[1] * (4.0D / 6.0D)};
    }
    public static int nextLeg(int leg) { return Math.floorMod(leg + 1, 4); }
    public static int clampNerve(int nerve) { return Math.clamp(nerve, 0, MAX_NERVE); }
    public static int loseNerve(int nerve) { return clampNerve(nerve - 8); }
    public static int recoverNerve(int nerve) { return clampNerve(nerve + 1); }
    public static boolean falterAt(int nerve) { return nerve <= FALTER_THRESHOLD; }
    public static boolean mayBindAt(int nerve) { return nerve >= BIND_THRESHOLD; }
    public static boolean recoveredAt(int nerve) { return nerve >= RECOVERY_THRESHOLD; }
    public static boolean staysFaltered(Phase phase, int nerve) {
        return phase == Phase.FALTER && !recoveredAt(nerve);
    }
    public static boolean fresh(int age) { return age >= 0 && age <= EVIDENCE_FRESHNESS_TICKS; }
    public static boolean acceptedAttribution(float acceptedDamage, int evidenceAge, boolean visible) {
        return acceptedDamage > 0.0F && fresh(evidenceAge) && visible;
    }
    public static boolean due(int remaining) { return remaining <= 0; }
    public static int boundedSentinel(int value) { return Math.clamp(value, 0, MAX_CADENCE_SENTINEL); }
    public static boolean shouldPulse(int tick, int entityId, int cadence, int offset) {
        return Math.floorMod(tick + entityId - offset, cadence) == 0;
    }
    public static boolean shouldSweep(int tick, int entityId) { return shouldPulse(tick, entityId, SWEEP_CADENCE, 0); }
    public static boolean shouldAlarm(int tick, int entityId) { return shouldPulse(tick, entityId, SWEEP_CADENCE, ALARM_OFFSET); }
    public static boolean legal(boolean owner, boolean listed, boolean sameKind, boolean ordinary, boolean visible, int nerve) {
        return ordinary && visible && mayBindAt(nerve) && TreefydRules.canAttack(owner, listed, sameKind);
    }
    public static int compareCandidate(double leftDistance, UUID left, double rightDistance, UUID right) {
        int distance = Double.compare(leftDistance, rightDistance);
        return distance != 0 ? distance : left.compareTo(right);
    }
    public static Phase afterMark(boolean legal, boolean visible, int displayCooldown) {
        if (!legal || !visible) return Phase.KEEPING;
        return due(displayCooldown) ? Phase.DISPLAY : Phase.THRESH;
    }
    public static DisplayGate displayGate(int phaseTicks, boolean quotaGranted) {
        if (phaseTicks != 40) return DisplayGate.ADVANCE;
        return quotaGranted ? DisplayGate.EMIT : DisplayGate.WAIT_FOR_QUOTA;
    }
    public static Band priority(boolean hazard, boolean combat, boolean episode, boolean nerve) {
        if (hazard) return Band.HAZARD;
        if (combat) return Band.COMBAT;
        if (episode) return Band.EPISODE;
        if (nerve) return Band.NERVE;
        return Band.ROUTINE;
    }
    public static Phase cancel(Phase ignored) { return Phase.KEEPING; }
    public static Phase afterAcceptedDamage(Phase phase) { return phase == Phase.DISPLAY ? Phase.THRESH : Phase.MARK; }
    public static boolean returnTimedOut(int loadedReturnTicks) { return loadedReturnTicks >= RETURN_TIMEOUT_TICKS; }
    public static int routeBackoffSentinel() { return ROUTE_BACKOFF_TICKS + 1; }
    public static boolean pathAccepted(boolean complete, boolean reachesDestination) { return complete && reachesDestination; }
    public static boolean thirdFailure(int failures) { return failures >= ROUTE_FAILURE_LIMIT; }
    public static float thornDamage(float acceptedDamage) { return Math.min(6.0F, 2.0F + acceptedDamage * 0.25F); }
    public static boolean safeImproves(double currentScore, double candidateScore) { return candidateScore < currentScore; }
    public static boolean safeDestination(boolean insideLeash, double currentScore, double candidateScore) {
        return insideLeash && safeImproves(currentScore, candidateScore);
    }
    public static boolean thornContact(boolean owner, boolean listed, boolean sameKind,
            double distanceSquared, float acceptedDamage) {
        return acceptedDamage > 0.0F && distanceSquared <= 9.0D
            && TreefydRules.canAttack(owner, listed, sameKind);
    }
    public static int clampCoordinate(int value, double borderMinimum, double borderMaximum) {
        int minimum = (int)Math.ceil(borderMinimum) + 1;
        int maximum = (int)Math.floor(Math.nextDown(borderMaximum)) - 1;
        return Math.clamp(value, minimum, Math.max(minimum, maximum));
    }
    public static int clampBuildY(int value, int minimumY, int maximumYExclusive) {
        return Math.clamp(value, minimumY, Math.max(minimumY, maximumYExclusive - 1));
    }
    public static boolean safeSearchAffordable(int reads, int visits, int additionalReads, int additionalVisits) {
        return reads >= 0 && visits >= 0 && additionalReads >= 0 && additionalVisits >= 0
            && reads + additionalReads <= SAFE_SEARCH_READS
            && visits + additionalVisits <= SAFE_SEARCH_VISITS;
    }
    public static boolean ordinarySubject(boolean dead, boolean removed, boolean invulnerable,
            boolean wrongLevel, boolean creativeOrSpectator, boolean sleeping, boolean tradingOrBreeding,
            boolean panicking, boolean raiding) {
        return !(dead || removed || invulnerable || wrongLevel || creativeOrSpectator || sleeping
            || tradingOrBreeding || panicking || raiding);
    }
    public static boolean retainSubject(boolean alive, boolean legal, boolean sameLevel,
            double leashDistance, double colossusDistance, int unseenTicks, int nerve) {
        return alive && legal && sameLevel && leashDistance <= LEASH_RADIUS && colossusDistance <= 16.0D
            && unseenTicks < 40 && mayBindAt(nerve);
    }
}
