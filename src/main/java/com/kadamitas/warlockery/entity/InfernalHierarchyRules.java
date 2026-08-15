package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class InfernalHierarchyRules {
    public static final int DECISION_INTERVAL_TICKS = 20;
    public static final int NAVIGATION_INTERVAL_TICKS = 20;
    public static final int FEEDBACK_INTERVAL_TICKS = 40;
    public static final int ATTRIBUTION_FRESHNESS_TICKS = 40;

    public static final long MEMBERSHIP_LEASE_TICKS = 400L;
    public static final long ARCHFIEND_ORDER_TICKS = 200L;
    public static final long REGENT_ORDER_TICKS = 300L;
    public static final int SQUAD_MEMBER_CAP = 4;
    public static final int COURT_MEMBER_CAP = 7;
    public static final int COURT_ARCHFIEND_CAP = 1;
    public static final int SQUAD_RANGE = 24;
    public static final int COURT_RANGE = 32;
    public static final int ARCHFIEND_GROUP_REFRESH_TICKS = 80;
    public static final int REGENT_GROUP_REFRESH_TICKS = 100;
    public static final int ARCHFIEND_GENERIC_CANDIDATE_CAP = 16;
    public static final int REGENT_GENERIC_CANDIDATE_CAP = 24;

    public static final int MORALE_MIN = 0;
    public static final int MORALE_MAX = 1000;
    public static final int MORALE_BASELINE = 650;
    public static final int MORALE_RETREAT_BELOW = 300;
    public static final int MORALE_REENTER_AT = 500;
    public static final int MORALE_DAMAGE_CAP = 200;
    public static final int MORALE_LEADER_LOSS_PENALTY = 300;
    public static final int MORALE_ALLY_LOSS_PENALTY = 120;
    public static final int MORALE_ALLY_LOSS_SPACING_TICKS = 40;
    public static final int MORALE_ROUTE_FAILURE_PENALTY = 80;
    public static final int MORALE_MELEE_REWARD = 40;
    public static final int MORALE_RALLY_REWARD = 200;
    public static final int MORALE_RALLY_SPACING_TICKS = 200;
    public static final int MORALE_RECOVERY_SPACING_TICKS = 40;
    public static final float RETREAT_HEALTH_FRACTION = 0.20F;

    public static final long TRUCE_TICKS = 200L;
    public static final int TRUCE_REFRESH_MIN_TICKS = 20;
    public static final int TRUCE_RANGE = 16;
    public static final long TRUCE_BREACH_TICKS = 600L;

    public static final long AGGRESSOR_TICKS = 600L;
    public static final long PROVOCATION_TICKS = 600L;

    public static final int EMBER_FRONT_TELEGRAPH_TICKS = 20;
    public static final int EMBER_FRONT_SPACING_TICKS = 120;
    public static final int EMBER_FRONT_RECOVERY_TICKS = 40;
    public static final int EMBER_FRONT_TARGET_CAP = 8;
    public static final int EMBER_FRONT_RANGE = 8;
    public static final float EMBER_FRONT_DAMAGE = 6.0F;
    public static final float EMBER_FRONT_FIRE_SECONDS = 4.0F;
    public static final long ANCHOR_CLAIM_TICKS = 400L;

    public static final int PHASE_TELEGRAPH_TICKS = 30;
    public static final int PHASE_EFFECT_TICKS = 240;
    public static final int PHASE_RECOVERY_TICKS = 60;
    public static final int PHASE_PLAYER_CAP = 32;
    public static final int PHASE_PLAYER_RADIUS = 24;
    public static final int PHASE_SUMMON_CAP = 2;
    public static final long SUMMON_LIFE_TICKS = 1_200L;
    public static final int SUMMON_COLLAPSE_TICKS = 100;

    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;
    public static final long MAX_FUTURE_HORIZON_TICKS = 20_000L;

    public static final int FEAR_PULSE_INTERVAL_TICKS = 80;
    public static final int FEAR_PULSE_RADIUS = 10;
    public static final int FEAR_PULSE_EFFECT_TICKS = 120;
    public static final int FEAR_PULSE_CANDIDATE_CAP = 16;

    public static final int CAULDRON_EVALUATION_INTERVAL_TICKS = 100;
    public static final int CAULDRON_CONTRIBUTOR_CAP = 4;
    public static final int CAULDRON_CONTRIBUTOR_RADIUS = 8;
    public static final int CAULDRON_SCAN_BLOCK_READS = 128;
    public static final int CAULDRON_BASE_REACH = 8;
    public static final int CAULDRON_MAX_REACH = 24;
    public static final int CAULDRON_LUCK_TICKS = 140;
    public static final int CAULDRON_WEAKNESS_TICKS = 200;
    public static final int CAULDRON_WEAKNESS_AMPLIFIER = 1;

    public static final int DEMON_TERRITORY_RADIUS = 12;
    public static final int DEMON_FOLLOW_RADIUS = 12;
    public static final int WARN_TICKS = 20;
    public static final int CLOSE_ATTACKER_RANGE = 3;
    public static final int MORALE_ALLY_LOSS_RADIUS = 16;
    public static final int SQUAD_PROVOCATION_RADIUS = 16;
    public static final int FEAR_TELEGRAPH_TICKS = 10;
    public static final int WITHDRAWAL_CAPTAIN_RADIUS = 24;

    public static final int DEMON_LINE_OF_SIGHT_CHECKS = 2;
    public static final int ARCHFIEND_LINE_OF_SIGHT_CHECKS = 6;
    public static final int REGENT_LINE_OF_SIGHT_CHECKS = 8;

    public static final int DEMON_OBSERVATION_INTERVAL_TICKS = 80;
    public static final int ARCHFIEND_OBSERVATION_INTERVAL_TICKS = 80;
    public static final int REGENT_OBSERVATION_INTERVAL_TICKS = 100;
    public static final int DEMON_OBSERVATION_RADIUS = 16;
    public static final int ARCHFIEND_OBSERVATION_RADIUS = 24;
    public static final int REGENT_OBSERVATION_RADIUS = 32;
    public static final int DEMON_RETAINED_CANDIDATES = 12;
    public static final int ARCHFIEND_RETAINED_CANDIDATES = 16;
    public static final int REGENT_RETAINED_CANDIDATES = 24;
    public static final int DEMON_ANCHOR_SEARCH_INTERVAL_TICKS = 100;
    public static final int ARCHFIEND_ANCHOR_SEARCH_INTERVAL_TICKS = 100;
    public static final int REGENT_ANCHOR_SEARCH_INTERVAL_TICKS = 120;
    public static final int DEMON_ANCHOR_RADIUS = 8;
    public static final int ARCHFIEND_ANCHOR_RADIUS = 12;
    public static final int REGENT_ANCHOR_RADIUS = 12;
    public static final int DEMON_ANCHOR_BLOCK_READS = 64;
    public static final int ARCHFIEND_ANCHOR_BLOCK_READS = 128;
    public static final int REGENT_ANCHOR_BLOCK_READS = 128;
    public static final int MAX_ANCHOR_BLOCK_READS_TOTAL = 2_048;

    private static final Comparator<UUID> UNSIGNED_UUID_ORDER = Comparator
        .comparingLong((UUID id) -> id.getMostSignificantBits() ^ Long.MIN_VALUE)
        .thenComparingLong(id -> id.getLeastSignificantBits() ^ Long.MIN_VALUE);

    private InfernalHierarchyRules() {
    }

    public enum Rank {
        DEMON, EMBERHORN_ARCHFIEND, ABYSSAL_REGENT
    }

    public static Optional<Rank> rankOf(final CreatureKind kind) {
        return switch (kind) {
            case DEMON -> Optional.of(Rank.DEMON);
            case EMBERHORN_ARCHFIEND -> Optional.of(Rank.EMBERHORN_ARCHFIEND);
            case ABYSSAL_REGENT -> Optional.of(Rank.ABYSSAL_REGENT);
            default -> Optional.empty();
        };
    }

    public enum AuthorityClass {
        HAZARD, DIRECT_PACT, ANIMUS, AUTHORED_OBJECTIVE, REGENT_ORDER, ARCHFIEND_ORDER, SELF_DEFENSE, AUTONOMY
    }

    public enum Intent {
        IDLE, POST_WATCH, APPRAISE, PACT_FOLLOW, PACT_GUARD, ORDERED_GUARD, WARN, PRESS, INTERCEPT,
        TRUCE, RETREAT, RETURN, SEEK_OFFICE, HOLD_OFFICE, MUSTER, FOCUS, EMBER_FRONT, RALLY,
        WITHDRAW, DISMISS, IDLE_COURT, SEEK_DEEP_ANCHOR, HOLD_COURT, COMMAND, FEAR_PULSE,
        DISPLACE, PHASE_TELEGRAPH, PHASE_COMMIT, PHASE_RECOVERY, SCREEN, DISSOLVE
    }

    public enum OrderKind {
        HOLD_POST, FOCUS_CHALLENGER, SCREEN, RALLY, WITHDRAW,
        HOLD_COURT, SCREEN_REGENT, WITHDRAW_TO_ANCHOR, DISSOLVE
    }

    public enum PhaseState {
        NONE, TELEGRAPH, COMMIT, RECOVERY, DONE
    }

    public static boolean acceptsPlayerAuthority(final Rank rank) {
        return rank == Rank.DEMON;
    }

    public static Optional<UUID> effectiveOwner(
        final Optional<UUID> directPactOwner,
        final Optional<UUID> animusOwner
    ) {
        return directPactOwner.or(() -> animusOwner);
    }

    public static boolean commandAccepted(
        final UUID commander,
        final Optional<UUID> directPactOwner,
        final Optional<UUID> animusOwner
    ) {
        return effectiveOwner(directPactOwner, animusOwner)
            .filter(commander::equals)
            .isPresent();
    }

    public static boolean animusMaySteal() {
        return false;
    }

    public static AuthorityClass resolveAuthority(
        final boolean hazardActive,
        final boolean directPactOwner,
        final boolean animusOwner,
        final boolean authoredObjective,
        final boolean validRegentOrder,
        final boolean validArchfiendOrder,
        final boolean recentDirectAggressor
    ) {
        if (hazardActive) return AuthorityClass.HAZARD;
        if (directPactOwner) return AuthorityClass.DIRECT_PACT;
        if (animusOwner) return AuthorityClass.ANIMUS;
        if (authoredObjective) return AuthorityClass.AUTHORED_OBJECTIVE;
        if (validRegentOrder) return AuthorityClass.REGENT_ORDER;
        if (validArchfiendOrder) return AuthorityClass.ARCHFIEND_ORDER;
        if (recentDirectAggressor) return AuthorityClass.SELF_DEFENSE;
        return AuthorityClass.AUTONOMY;
    }

    public static boolean mayIssueOrder(final Rank issuer, final Rank recipient, final OrderKind kind) {
        return switch (issuer) {
            case DEMON -> false;
            case EMBERHORN_ARCHFIEND -> recipient == Rank.DEMON && switch (kind) {
                case HOLD_POST, FOCUS_CHALLENGER, SCREEN, RALLY, WITHDRAW -> true;
                default -> false;
            };
            case ABYSSAL_REGENT -> recipient != Rank.ABYSSAL_REGENT && switch (kind) {
                case HOLD_COURT, SCREEN_REGENT, FOCUS_CHALLENGER, WITHDRAW_TO_ANCHOR, DISSOLVE -> true;
                default -> false;
            };
        };
    }

    public static long orderLifetimeTicks(final Rank issuer) {
        return issuer == Rank.ABYSSAL_REGENT ? REGENT_ORDER_TICKS : ARCHFIEND_ORDER_TICKS;
    }

    public static boolean orderValid(final long expiresAt, final long issuerEpoch, final long orderEpoch, final long now) {
        return expiresAt > now && issuerEpoch == orderEpoch;
    }

    public static boolean recursiveOrderCopyingAllowed() {
        return false;
    }

    public record MemberCandidate(
        UUID id,
        Rank rank,
        boolean currentMember,
        double distanceSqr,
        boolean playerBound,
        boolean otherwiseLeased,
        boolean sameDimension,
        boolean loaded
    ) {
    }

    public static boolean eligibleMember(final Rank leader, final MemberCandidate candidate) {
        if (candidate.playerBound() || candidate.otherwiseLeased()
            || !candidate.sameDimension() || !candidate.loaded()) {
            return false;
        }
        return switch (leader) {
            case DEMON -> false;
            case EMBERHORN_ARCHFIEND -> candidate.rank() == Rank.DEMON;
            case ABYSSAL_REGENT -> candidate.rank() != Rank.ABYSSAL_REGENT;
        };
    }

    public static int memberCap(final Rank leader) {
        return leader == Rank.ABYSSAL_REGENT ? COURT_MEMBER_CAP : SQUAD_MEMBER_CAP;
    }

    public static List<MemberCandidate> retainRoster(final Rank leader, final List<MemberCandidate> candidates) {
        final LinkedHashMap<UUID, MemberCandidate> unique = new LinkedHashMap<>();
        candidates.stream()
            .filter(candidate -> eligibleMember(leader, candidate))
            .sorted(Comparator
                .comparing((MemberCandidate candidate) -> !candidate.currentMember())
                .thenComparingDouble(MemberCandidate::distanceSqr)
                .thenComparing(MemberCandidate::id, UNSIGNED_UUID_ORDER))
            .forEach(candidate -> unique.putIfAbsent(candidate.id(), candidate));
        final int cap = memberCap(leader);
        final int archfiendCap = leader == Rank.ABYSSAL_REGENT ? COURT_ARCHFIEND_CAP : 0;
        final List<MemberCandidate> retained = new ArrayList<>();
        int archfiends = 0;
        for (final MemberCandidate candidate : unique.values()) {
            if (retained.size() >= cap) break;
            if (candidate.rank() == Rank.EMBERHORN_ARCHFIEND) {
                if (archfiends >= archfiendCap) continue;
                archfiends++;
            }
            retained.add(candidate);
        }
        return List.copyOf(retained);
    }

    public static int damageMoralePenalty(final float damage, final float maxHealth) {
        if (maxHealth <= 0.0F) return MORALE_DAMAGE_CAP;
        return Math.min(MORALE_DAMAGE_CAP, Math.round(300.0F * damage / maxHealth));
    }

    public static int clampMorale(final int morale) {
        return Math.clamp(morale, MORALE_MIN, MORALE_MAX);
    }

    public static boolean allyLossPenaltyDue(final long lastAllyLossAt, final long now) {
        return lastAllyLossAt <= 0L || now - lastAllyLossAt >= MORALE_ALLY_LOSS_SPACING_TICKS;
    }

    public static boolean rallyRewardDue(final long lastRallyAt, final long now) {
        return lastRallyAt <= 0L || now - lastRallyAt >= MORALE_RALLY_SPACING_TICKS;
    }

    public static int recoveredMorale(final int morale, final long lastRecoveryAt, final long now) {
        if (morale >= MORALE_BASELINE) return morale;
        final long elapsed = Math.max(0L, now - Math.max(0L, lastRecoveryAt));
        final long recovered = elapsed / MORALE_RECOVERY_SPACING_TICKS;
        return (int) Math.min(MORALE_BASELINE, morale + recovered);
    }

    public static boolean moraleRetreatRequired(final int morale, final float healthFraction) {
        return morale < MORALE_RETREAT_BELOW || healthFraction <= RETREAT_HEALTH_FRACTION;
    }

    public static boolean mayReenterPressure(
        final int morale,
        final boolean attackerWithinThreeBlocks,
        final boolean ownerDefenseRequired
    ) {
        return morale >= MORALE_REENTER_AT || attackerWithinThreeBlocks || ownerDefenseRequired;
    }

    public static int lineOfSightBudget(final Rank rank) {
        return switch (rank) {
            case DEMON -> DEMON_LINE_OF_SIGHT_CHECKS;
            case EMBERHORN_ARCHFIEND -> ARCHFIEND_LINE_OF_SIGHT_CHECKS;
            case ABYSSAL_REGENT -> REGENT_LINE_OF_SIGHT_CHECKS;
        };
    }

    public static int cauldronReach(final int contributors) {
        final int counted = Math.clamp(contributors, 0, CAULDRON_CONTRIBUTOR_CAP);
        return Math.min(CAULDRON_MAX_REACH, CAULDRON_BASE_REACH + 4 * counted);
    }

    /**
     * Deterministic bounded nearest selection. Ordering is distance first, then unsigned UUID, so a
     * caller can never let engine iteration order decide which actors receive a bounded effect.
     */
    public record RankedActor(UUID id, double distanceSqr) {
        public RankedActor {
            id = java.util.Objects.requireNonNull(id, "id");
        }
    }

    public static List<UUID> retainNearest(final List<RankedActor> candidates, final int cap) {
        if (cap <= 0) return List.of();
        final LinkedHashMap<UUID, RankedActor> unique = new LinkedHashMap<>();
        candidates.stream()
            .sorted(Comparator.comparingDouble(RankedActor::distanceSqr)
                .thenComparing(RankedActor::id, UNSIGNED_UUID_ORDER))
            .forEach(candidate -> unique.putIfAbsent(candidate.id(), candidate));
        return unique.keySet().stream().limit(cap).toList();
    }

    /**
     * Required identities are always inserted before generic candidates so truce refresh, challenger
     * stability, and order execution can never depend on entity iteration order.
     */
    public static List<UUID> retainObservation(
        final List<UUID> requiredIdentities,
        final List<UUID> genericCandidates,
        final int cap
    ) {
        if (cap <= 0) return List.of();
        final java.util.LinkedHashSet<UUID> retained = new java.util.LinkedHashSet<>();
        for (final UUID required : requiredIdentities) {
            if (required == null || retained.size() >= cap) continue;
            retained.add(required);
        }
        for (final UUID generic : genericCandidates) {
            if (generic == null || retained.size() >= cap) break;
            retained.add(generic);
        }
        return List.copyOf(retained);
    }

    public record DemonIntentFacts(
        boolean hazard,
        boolean healthCritical,
        boolean retreatRequired,
        boolean reenterAllowed,
        boolean unmoored,
        boolean truceActive,
        boolean playerAuthority,
        boolean ownerUnderAttack,
        boolean ownerBeyondFollowRange,
        boolean guardOrdered,
        boolean pressAuthorized,
        boolean intruderInTerritory,
        boolean warningWindowOpen,
        boolean postHeld,
        boolean daylight
    ) {
    }

    public static Intent selectDemonIntent(final DemonIntentFacts facts) {
        if (facts.hazard()) return Intent.RETREAT;
        if (facts.healthCritical()) return Intent.RETREAT;
        if (facts.retreatRequired() && !facts.reenterAllowed()) return Intent.RETREAT;
        if (facts.unmoored()) return Intent.RETURN;
        if (facts.truceActive()) return Intent.TRUCE;
        if (facts.playerAuthority() && facts.ownerUnderAttack()) return Intent.INTERCEPT;
        if (facts.guardOrdered()) return Intent.ORDERED_GUARD;
        if (facts.playerAuthority()) {
            return facts.ownerBeyondFollowRange() ? Intent.PACT_FOLLOW : Intent.PACT_GUARD;
        }
        if (facts.pressAuthorized()) return Intent.PRESS;
        if (facts.intruderInTerritory()) {
            return facts.warningWindowOpen() ? Intent.WARN : Intent.PRESS;
        }
        if (facts.postHeld()) return facts.daylight() ? Intent.POST_WATCH : Intent.APPRAISE;
        return Intent.IDLE;
    }

    public record ArchfiendIntentFacts(
        boolean hazard,
        boolean healthCritical,
        boolean rallyReady,
        boolean withdrawOrdered,
        boolean dismissDue,
        boolean challengerValid,
        boolean emberFrontReady,
        boolean intruderWarned,
        boolean rosterBelowCap,
        boolean anchorHeld,
        boolean anchorSearchDue
    ) {
    }

    public static Intent selectArchfiendIntent(final ArchfiendIntentFacts facts) {
        if (facts.hazard()) return Intent.WITHDRAW;
        if (facts.healthCritical()) return facts.rallyReady() ? Intent.RALLY : Intent.WITHDRAW;
        if (facts.withdrawOrdered()) return Intent.WITHDRAW;
        if (facts.dismissDue()) return Intent.DISMISS;
        if (facts.challengerValid()) {
            return facts.emberFrontReady() ? Intent.EMBER_FRONT : Intent.FOCUS;
        }
        if (facts.intruderWarned()) return Intent.WARN;
        if (facts.rosterBelowCap()) return Intent.MUSTER;
        if (facts.anchorHeld()) return Intent.HOLD_OFFICE;
        if (facts.anchorSearchDue()) return Intent.SEEK_OFFICE;
        return Intent.IDLE;
    }

    public record RegentIntentFacts(
        boolean hazard,
        boolean phaseTelegraph,
        boolean phaseCommit,
        boolean phaseRecovery,
        boolean dissolving,
        boolean displaceReady,
        boolean fearPulseDue,
        boolean commandDue,
        boolean screening,
        boolean appraising,
        boolean anchorHeld,
        boolean anchorSearchDue
    ) {
    }

    public static Intent selectRegentIntent(final RegentIntentFacts facts) {
        if (facts.hazard()) return Intent.RETURN;
        if (facts.phaseTelegraph()) return Intent.PHASE_TELEGRAPH;
        if (facts.phaseCommit()) return Intent.PHASE_COMMIT;
        if (facts.phaseRecovery()) return Intent.PHASE_RECOVERY;
        if (facts.dissolving()) return Intent.DISSOLVE;
        if (facts.displaceReady()) return Intent.DISPLACE;
        if (facts.fearPulseDue()) return Intent.FEAR_PULSE;
        if (facts.commandDue()) return Intent.COMMAND;
        if (facts.screening()) return Intent.SCREEN;
        if (facts.appraising()) return Intent.APPRAISE;
        if (facts.anchorHeld()) return Intent.HOLD_COURT;
        if (facts.anchorSearchDue()) return Intent.SEEK_DEEP_ANCHOR;
        return Intent.IDLE_COURT;
    }

    /**
     * The intents that clear target, navigation, and action generation before a safe return point is
     * chosen. Retreat, withdrawal, dissolution, truce, and hazard all cancel active execution.
     */
    public static boolean cancelsExecution(final Intent intent) {
        return switch (intent) {
            case RETREAT, RETURN, WITHDRAW, DISSOLVE, TRUCE -> true;
            default -> false;
        };
    }

    /**
     * The intents under which a rank may hold an engaged combat target claim. Warning, truce, retreat,
     * withdrawal, dissolution, idle, and pure routine intents never acquire a target, which preserves
     * the twenty-tick warning window and the restrained postures exactly.
     */
    public static boolean engagesTarget(final Intent intent) {
        return switch (intent) {
            case PRESS, INTERCEPT, PACT_GUARD, ORDERED_GUARD, FOCUS, EMBER_FRONT,
                 COMMAND, SCREEN, DISPLACE, FEAR_PULSE, APPRAISE -> true;
            default -> false;
        };
    }

    public static boolean mayFormTruce(
        final boolean validCharm,
        final boolean directAggressor,
        final long truceBreachUntil,
        final long now
    ) {
        return validCharm && !directAggressor && truceBreachUntil <= now;
    }

    public static boolean truceRefreshDue(final long refreshedAt, final long now) {
        return refreshedAt <= 0L || now - refreshedAt >= TRUCE_REFRESH_MIN_TICKS;
    }

    public static boolean truceValid(
        final long truceExpiresAt,
        final boolean playerLoaded,
        final boolean withinRange,
        final long now
    ) {
        return truceExpiresAt > now && playerLoaded && withinRange;
    }

    public static long truceBreachUntil(final long now) {
        return saturatingAdd(now, TRUCE_BREACH_TICKS);
    }

    public static boolean summonTransactionAllowed(
        final int courtSize,
        final boolean peacefulDifficulty,
        final int safePositions,
        final boolean phaseGroupAlreadyUsed
    ) {
        return !peacefulDifficulty
            && !phaseGroupAlreadyUsed
            && safePositions >= PHASE_SUMMON_CAP
            && courtSize + PHASE_SUMMON_CAP <= COURT_MEMBER_CAP;
    }

    public static int routeFailures(final int failures) {
        return Math.min(MAX_ROUTE_FAILURES, failures + 1);
    }

    public static long routeBackoffUntil(final int failures, final long now) {
        return failures >= MAX_ROUTE_FAILURES ? saturatingAdd(now, ROUTE_BACKOFF_TICKS) : 0L;
    }

    public static boolean due(final long deadline, final long now) {
        return deadline <= 0L || now >= deadline;
    }

    public static long clampDeadline(final long deadline, final long now, final long maxHorizonTicks) {
        if (deadline <= 0L) return 0L;
        final long horizon = Math.min(maxHorizonTicks, MAX_FUTURE_HORIZON_TICKS);
        return Math.min(deadline, saturatingAdd(now, horizon));
    }

    public static int stableOffset(final UUID id, final int modulus) {
        return (int) Math.floorMod(id.getLeastSignificantBits(), (long) modulus);
    }

    public static Comparator<UUID> unsignedUuidOrder() {
        return UNSIGNED_UUID_ORDER;
    }

    public static long saturatingAdd(final long base, final long addend) {
        final long sum = base + addend;
        return ((base ^ sum) & (addend ^ sum)) < 0L ? Long.MAX_VALUE : sum;
    }
}
