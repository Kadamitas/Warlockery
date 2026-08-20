package com.kadamitas.warlockery.entity;

public final class MandrakeRules {
    public enum Phase { SEEDED, DISTURBED, WAIL, FLAIL, RESETTLE, ESCAPE }
    public static final double WAIL_RADIUS = 8.0D;
    public static final int RAW_CANDIDATE_CAP = 8;
    public static final int WAIL_RECIPIENT_CAP = 4;
    public static final int WAIL_SIGHT_CAP = 4;
    public static final int WAIL_DURATION_TICKS = 120;
    public static final int WAIL_AMPLIFIER = 0;
    public static final int WAIL_COOLDOWN_TICKS = 600;
    public static final int EPISODE_TICKS = 200;
    public static final int TELEGRAPH_TICKS = 20;
    public static final int FLAIL_TICKS = 100;
    public static final int MELEE_CADENCE_TICKS = 20;
    public static final int ROUTE_CADENCE_TICKS = 20;
    public static final int ROUTE_BACKOFF_TICKS = 100;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final double ARRIVAL_DISTANCE_SQUARED = 2.25D;
    public static final int HAZARD_CADENCE_TICKS=20, HAZARD_FOOTPRINT_READ_CAP=18, SAFE_CANDIDATE_CAP=16,
        SAFE_READ_CAP=128, OCCUPANCY_VISITS_PER_CANDIDATE=8, OCCUPANCY_VISITS_PER_SEARCH=32;
    private MandrakeRules() {}
    public static boolean flailComplete(int ticks,boolean subjectRequired,boolean subjectPresent){return ticks>=FLAIL_TICKS||(subjectRequired&&!subjectPresent);}
    public static boolean startsDamageEpisode(Phase phase){return phase==Phase.SEEDED||phase==Phase.RESETTLE;}
    public static boolean mayBindDamageSubject(Phase phase){return startsDamageEpisode(phase)||phase==Phase.DISTURBED||phase==Phase.FLAIL;}
    public static Phase afterAcceptedDamage(Phase phase){return startsDamageEpisode(phase)?Phase.DISTURBED:phase;}
    public static boolean clearEscapeDestination(int failures){return failures>=MAX_ROUTE_FAILURES;}
    public static int clampRemaining(int value, int maximum) { return Math.clamp(value, 0, maximum); }
    public static boolean freshAttribution(long age) { return LivingRootsRules.fresh(age); }
    public static boolean routeDue(int remaining) { return remaining <= 0; }
    public static boolean thirdRouteFailure(int failures) { return failures >= MAX_ROUTE_FAILURES; }
    public static long boundedCadenceSentinel() { return LivingRootsRules.MAX_CADENCE_SENTINEL; }
    public static Phase afterWailToken(boolean granted) { return granted ? Phase.FLAIL : Phase.WAIL; }
    public static int backoffAfterFailure(int priorFailures) { return priorFailures + 1 >= MAX_ROUTE_FAILURES ? ROUTE_BACKOFF_TICKS : 0; }
}
