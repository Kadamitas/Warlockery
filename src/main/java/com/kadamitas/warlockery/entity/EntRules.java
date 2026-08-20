package com.kadamitas.warlockery.entity;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class EntRules {
    public static final double MAX_HEALTH = 200.0D;
    public static final float ORDINARY_DAMAGE_CAP = 15.0F;
    public static final float WEAKNESS_MULTIPLIER = 3.0F;
    public static final int FERTILIZE_INTERVAL_TICKS = 100;
    public static final int FERTILIZE_RADIUS = 2;
    public static final int MAX_FERTILIZED_BLOCKS = 8;
    public static final int MIN_HORIZONTAL_SPAWN_DISTANCE = 8;
    public static final int MAX_HORIZONTAL_SPAWN_DISTANCE = 16;
    public static final int MAX_VERTICAL_SPAWN_OFFSET = 6;
    public static final double CLAIM_HORIZONTAL_RADIUS = 12.0D;
    public static final double CLAIM_VERTICAL_RADIUS = 8.0D;
    public static final double LEASH_RADIUS = 24.0D;
    public static final double CORRUPT_ANCHOR_RADIUS = 64.0D;
    public static final int FELLING_GRIEVANCE = 20;
    public static final int DAMAGE_GRIEVANCE = 10;
    public static final int MAX_GRIEVANCE = 100;
    public static final int STRIKE_GRIEVANCE = 60;
    public static final int RELEASE_GRIEVANCE = 20;
    public static final int EVIDENCE_FRESHNESS_TICKS = 40;
    public static final int ORIENTATION_TICKS = 20;
    public static final int WARNING_TICKS = 40;
    public static final int WARN_COOLDOWN_TICKS = 600;
    public static final int STRIKE_TICKS = 200;
    public static final int SETTLE_TICKS = 300;
    public static final int PATH_CADENCE_TICKS = 20;
    public static final int ROUTE_BACKOFF_TICKS = 100;
    public static final int MAX_LOADED_CADENCE_TICKS = 20_000;
    public static final int MAX_NOTICE_RESULTS = 4;
    public static final int MAX_NOTICES_PER_BREAK = 2;
    public static final int MAX_EXPENSIVE_TOKENS_PER_LEVEL_TICK = 16;
    public static final int MAX_PATHS_PER_LEVEL_TICK = 8;
    public static final int MAX_RAW_ENTITY_VISITS_PER_LEVEL_TICK = 128;
    public static final int MAX_SIGHT_RAYS_PER_LEVEL_TICK = 32;
    public static final int MAX_CHARGED_READS_PER_LEVEL_TICK = 1024;

    public enum Phase { WARDING, ROUSED, WARN, STRIKE, SETTLE, TEND, ESCAPE }
    public enum Band { HAZARD, COMBAT, EPISODE, ROUTINE }
    public enum Cancellation { REMOVAL, TELEPORT, DIMENSION_CHANGE, DEATH, DISCARD, TRADE, SLEEP, RAID, PANIC, BREEDING, HAZARD, UNLOAD, TOKEN_DENIED }

    private EntRules() {
    }

    public static float incomingDamage(
        final float amount,
        final boolean axeAttack,
        final boolean nonPlayerMobAttack
    ) {
        final float safeAmount = Float.isFinite(amount) ? Math.max(0.0F, amount) : 0.0F;
        return axeAttack || nonPlayerMobAttack
            ? safeAmount * WEAKNESS_MULTIPLIER
            : Math.min(safeAmount, ORDINARY_DAMAGE_CAP);
    }

    public static double logBreakSpawnChance(final int neighboringLogs) {
        return Math.clamp(neighboringLogs, 0, 100) / 100.0D;
    }

    public static boolean shouldSpawn(final int neighboringLogs, final double roll) {
        return Double.isFinite(roll) && roll >= 0.0D && roll < logBreakSpawnChance(neighboringLogs);
    }

    public static int horizontalOffset(final int distanceRoll, final boolean positive) {
        if (distanceRoll < 0 || distanceRoll > MAX_HORIZONTAL_SPAWN_DISTANCE - MIN_HORIZONTAL_SPAWN_DISTANCE) {
            throw new IllegalArgumentException("Horizontal Ent spawn roll is outside its supported range");
        }
        final int distance = MIN_HORIZONTAL_SPAWN_DISTANCE + distanceRoll;
        return positive ? distance : -distance;
    }

    public static int verticalOffset(final int heightRoll) {
        if (heightRoll < 0 || heightRoll > MAX_VERTICAL_SPAWN_OFFSET) {
            throw new IllegalArgumentException("Vertical Ent spawn roll is outside its supported range");
        }
        return heightRoll;
    }

    public static boolean shouldFertilizeGround(final int tickCount, final int entityId) {
        return Math.floorMod(tickCount + entityId, FERTILIZE_INTERVAL_TICKS) == 0;
    }

    public static boolean insideClaim(double ax, double ay, double az, double x, double y, double z) {
        double dx = x - ax, dz = z - az;
        return dx * dx + dz * dz <= CLAIM_HORIZONTAL_RADIUS * CLAIM_HORIZONTAL_RADIUS
            && Math.abs(y - ay) <= CLAIM_VERTICAL_RADIUS;
    }

    public static boolean insideLeash(double ax, double ay, double az, double x, double y, double z) {
        double dx = x - ax, dz = z - az;
        return dx * dx + dz * dz <= LEASH_RADIUS * LEASH_RADIUS;
    }

    public static boolean anchorCorrupt(double ax, double ay, double az, double x, double y, double z) {
        double dx = x - ax, dy = y - ay, dz = z - az;
        return dx * dx + dy * dy + dz * dz > CORRUPT_ANCHOR_RADIUS * CORRUPT_ANCHOR_RADIUS;
    }

    public static int addFellingGrievance(int grievance) { return Math.min(MAX_GRIEVANCE, Math.max(0, grievance) + FELLING_GRIEVANCE); }
    public static int addDamageGrievance(int grievance) { return Math.min(MAX_GRIEVANCE, Math.max(0, grievance) + DAMAGE_GRIEVANCE); }
    public static int decayGrievance(int grievance, int loadedTicks) { return Math.max(0, Math.min(MAX_GRIEVANCE, grievance) - Math.max(0, loadedTicks) / 100); }
    public static int decrementLoaded(int remaining) { return Math.max(0, remaining - 1); }
    public static boolean remainingDue(int remaining) { return remaining <= 0; }
    public static boolean evidenceFresh(int age) { return age >= 0 && age <= EVIDENCE_FRESHNESS_TICKS; }
    public static boolean staggeredDue(int tickCount, int entityId, int cadence) { return cadence > 0 && Math.floorMod(tickCount + entityId, cadence) == 0; }
    public static boolean pathDue(int tickCount, int entityId) { return staggeredDue(tickCount, entityId, PATH_CADENCE_TICKS); }
    public static int routeFailuresAfter(int failures) { return Math.min(3, Math.max(0, failures) + 1); }
    public static boolean routeExhausted(int failures) { return failures >= 3; }
    public record RouteFailure(int failures, int backoff, boolean reanchor) {}
    public static RouteFailure routeFailure(int failures) {
        int next = routeFailuresAfter(failures);
        return routeExhausted(next) ? new RouteFailure(0, ROUTE_BACKOFF_TICKS, true)
            : new RouteFailure(next, 0, false);
    }
    public record WarningTransition(Phase phase, int cooldown, boolean emitted) {}
    public static WarningTransition warningTransition(boolean tokenGranted, int currentCooldown) {
        return tokenGranted ? new WarningTransition(Phase.WARN, WARN_COOLDOWN_TICKS, true)
            : new WarningTransition(Phase.ROUSED, currentCooldown, false);
    }
    public static boolean warningExpired(int ticks) { return ticks >= WARNING_TICKS; }
    public static boolean strikeExpired(int ticks) { return ticks >= STRIKE_TICKS; }
    public static boolean settleExpired(int ticks) { return ticks >= SETTLE_TICKS; }
    public static Band priority(boolean hazard, boolean combat, boolean episode) { return hazard ? Band.HAZARD : combat ? Band.COMBAT : episode ? Band.EPISODE : Band.ROUTINE; }
    public static Phase afterOrientation(int grievance, int warnCooldown, boolean visible) {
        if (!visible) return Phase.SETTLE;
        return grievance >= STRIKE_GRIEVANCE || warnCooldown > 0 ? Phase.STRIKE : Phase.WARN;
    }
    public static boolean subjectLegal(boolean alive,boolean sameLevel,boolean ordinaryRelation,
        boolean notSpecialState,boolean notCreativeOrSpectator,boolean vulnerable,boolean notRaid,
        boolean notPanic,boolean notBreeding){return alive&&sameLevel&&ordinaryRelation&&notSpecialState
            &&notCreativeOrSpectator&&vulnerable&&notRaid&&notPanic&&notBreeding;}
    public static boolean reactionAllowed(boolean effectivePositiveLoss,int attributionAge,
        boolean legal,boolean visible){return effectivePositiveLoss&&evidenceFresh(attributionAge)&&legal&&visible;}

    public record RouteResolution(int failures,int backoff,Phase phase,boolean reanchor){}
    public static RouteResolution strikeRouteFailure(int failures){int next=routeFailuresAfter(failures);return routeExhausted(next)
        ?new RouteResolution(0,ROUTE_BACKOFF_TICKS,Phase.SETTLE,false):new RouteResolution(next,0,Phase.STRIKE,false);}
    public static RouteResolution settleRouteFailure(int failures){int next=routeFailuresAfter(failures);return routeExhausted(next)
        ?new RouteResolution(0,ROUTE_BACKOFF_TICKS,Phase.WARDING,true):new RouteResolution(next,0,Phase.SETTLE,false);}

    public record NoticeCandidate(UUID uuid, double distanceSquared) {}
    public static List<NoticeCandidate> selectNotices(List<NoticeCandidate> candidates) {
        return candidates.stream().sorted(Comparator.comparingDouble(NoticeCandidate::distanceSquared)
            .thenComparing(NoticeCandidate::uuid)).limit(MAX_NOTICES_PER_BREAK).toList();
    }

    public static final class Quota {
        private final int serverTick;
        private int expensive,paths,raw,sight,reads,safeVisits,noticeScans,notices,warnings,melee,tendJobs,blockEdits,feedback;
        private Quota(int serverTick) { this.serverTick = serverTick; }
        public static Quota fresh(int serverTick) { return new Quota(serverTick); }
        public int serverTick() { return serverTick; }
        public boolean tryPath(boolean serverThread) {
            if (!serverThread || paths >= MAX_PATHS_PER_LEVEL_TICK) return false;
            paths++;
            return true;
        }
        private boolean take(boolean serverThread,int current,int amount,int cap){return serverThread&&amount>=0&&current<=cap-amount;}
        public boolean tryExpensive(boolean s){if(!take(s,expensive,1,16))return false;expensive++;return true;}
        public boolean tryRawEntityVisit(boolean s){if(!take(s,raw,1,128))return false;raw++;return true;}
        public boolean trySightRay(boolean s){if(!take(s,sight,1,32))return false;sight++;return true;}
        public boolean tryChargedReads(int amount,boolean s){if(!take(s,reads,amount,1024))return false;reads+=amount;return true;}
        public boolean trySafeDestinationVisit(boolean s){if(!take(s,safeVisits,1,128))return false;safeVisits++;return true;}
        public boolean tryNoticeScan(boolean s){if(!take(s,noticeScans,1,2))return false;noticeScans++;return true;}
        public boolean tryNotice(boolean s){if(!take(s,notices,1,4))return false;notices++;return true;}
        public boolean tryWarning(boolean s){if(!take(s,warnings,1,4))return false;warnings++;return true;}
        public boolean tryMelee(boolean s){if(!take(s,melee,1,8))return false;melee++;return true;}
        public boolean tryTendJob(boolean s){if(!take(s,tendJobs,1,1))return false;tendJobs++;return true;}
        public boolean tryBlockEdit(boolean s){if(!take(s,blockEdits,1,1))return false;blockEdits++;return true;}
        public boolean tryFeedback(boolean s){if(!take(s,feedback,1,8))return false;feedback++;return true;}
    }
}
