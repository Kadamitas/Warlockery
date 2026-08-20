package com.kadamitas.warlockery.entity;

public final class DreamrootRules {
    public enum Phase { ROOTED, STIR, THRESHOLD, DREAM, SUBSIDE, ESCAPE }
    public static final int THRESHOLD_CADENCE_TICKS = 20;
    public static final double OUTER_RADIUS = 6.0D;
    public static final double INNER_RADIUS = 3.0D;
    public static final int RAW_CANDIDATE_CAP = 8;
    public static final int SIGHT_CAP = 2;
    public static final int ONSET_TICKS = 30;
    public static final int DREAM_COOLDOWN_TICKS = 400;
    public static final int SUBSIDE_TICKS = 40;
    public static final int SUSTAIN_CADENCE_TICKS = 40;
    public static final int BULBS_PER_WAKE = 4;
    public static final int HAZARD_CADENCE_TICKS=20, HAZARD_FOOTPRINT_READ_CAP=18, SAFE_CANDIDATE_CAP=16,
        SAFE_READ_CAP=128, OCCUPANCY_VISITS_PER_CANDIDATE=8, OCCUPANCY_VISITS_PER_SEARCH=32,
        COMBAT_WINDOW_TICKS=100, MELEE_CADENCE_TICKS=20;
    private DreamrootRules() {}
    public static boolean clearEscapeDestination(int failures){return failures>=3;}

    public static int compareCandidate(double leftDistance, java.util.UUID hint, java.util.UUID left,
            double rightDistance, java.util.UUID right) {
        final int distance = Double.compare(leftDistance, rightDistance);
        if (distance != 0) return distance;
        final int hinted = Boolean.compare(!left.equals(hint), !right.equals(hint));
        return hinted != 0 ? hinted : left.compareTo(right);
    }
    public static int dreamDuration(int empowerment) { return Math.clamp(100 + 20 * empowerment, 100, 200); }
    public static float sustainAmount(int empowerment) { return 1.0F + Math.clamp(empowerment, 0, 5) * 0.5F; }
    public static int bulbsThisWake(int remaining, int quota) { return Math.min(Math.max(0, remaining), Math.min(BULBS_PER_WAKE, Math.max(0, quota))); }
    public static boolean freshAttribution(long age) { return LivingRootsRules.fresh(age); }
    public static Phase afterDreamToken(boolean granted) { return granted ? Phase.DREAM : Phase.THRESHOLD; }
}
