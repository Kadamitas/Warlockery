package com.kadamitas.warlockery.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure F13 Circle Mage policy. Deliberately not a shared practitioner controller: the Circle Mage
 * owns recruitment, owner formation, aura election, peer reports, conclave study, and the arcane
 * bolt, none of which the Hedge Crone has, and it never inherits the Crone's boundary motive.
 *
 * <p>No rule here infers membership from species, proximity, ritual attendance, golden circle,
 * target, workstation, or rank. Formal membership is exactly an owner-bound roster entry.</p>
 */
public final class CircleMageRules {
    public static final int MAX_COVEN_MAGES = FamiliarBondRules.MAX_COVEN_MAGES;

    public static final int OWNER_CHECK_INTERVAL_TICKS = 10;
    public static final int FORMATION_RADIUS = 9;
    public static final double FORMATION_RADIUS_SQUARED = (double) FORMATION_RADIUS * FORMATION_RADIUS;
    public static final int FORMATION_QUERY_RADIUS = 16;
    public static final int MAX_FORMATION_PEERS_VISITED = 8;

    public static final int SAFE_STEP_DISTANCE = 32;
    public static final double SAFE_STEP_DISTANCE_SQUARED = (double) SAFE_STEP_DISTANCE * SAFE_STEP_DISTANCE;
    public static final int SAFE_STEP_INTERVAL_TICKS = 100;
    public static final int MAX_SAFE_STEP_CANDIDATES = 25;
    public static final int MAX_SAFE_STEP_READS = 128;

    public static final int AURA_INTERVAL_TICKS = 20;
    public static final int AURA_DURATION_TICKS = 60;
    public static final int AURA_RADIUS = 16;
    public static final double AURA_RADIUS_SQUARED = (double) AURA_RADIUS * AURA_RADIUS;

    public static final int PEER_SCAN_INTERVAL_TICKS = 40;
    public static final int PEER_RADIUS = 16;
    public static final int MAX_PEERS_VISITED = 8;
    public static final int MAX_PEERS_NOTIFIED = 2;
    public static final int REPORT_EXPIRY_TICKS = 80;

    public static final int BOLT_WINDUP_TICKS = 12;
    public static final int BOLT_RECOVERY_TICKS = 50;
    public static final float BOLT_DAMAGE = 5.0F;
    public static final float BOLT_FOCUSED_DAMAGE = 7.0F;
    public static final int BOLT_MIN_RANGE = 3;
    public static final int BOLT_MAX_RANGE = 16;
    public static final double BOLT_MIN_RANGE_SQUARED = (double) BOLT_MIN_RANGE * BOLT_MIN_RANGE;
    public static final double BOLT_MAX_RANGE_SQUARED = (double) BOLT_MAX_RANGE * BOLT_MAX_RANGE;

    public static final int STUDY_SEARCH_INTERVAL_TICKS = 120;
    public static final int STUDY_COOLDOWN_TICKS = 1_200;
    public static final int REHEARSAL_TICKS = 60;
    public static final int SESSION_TIMEOUT_TICKS = 100;
    public static final int MAX_SESSION_SIZE = 3;
    public static final int MAX_ACCEPTED_PEERS = MAX_SESSION_SIZE - 1;
    public static final int CONCLAVE_RADIUS = 12;
    public static final double CONCLAVE_RADIUS_SQUARED = (double) CONCLAVE_RADIUS * CONCLAVE_RADIUS;
    public static final int MAX_WORKSTATION_CANDIDATES = 64;
    public static final int MAX_WORKSTATION_READS = 128;
    public static final int WORKSTATION_HORIZONTAL_RADIUS = 8;
    public static final int WORKSTATION_VERTICAL_RADIUS = 2;
    public static final double WORKSTATION_ARRIVAL_DISTANCE_SQUARED = 6.25D;

    public static final int TARGET_SCAN_INTERVAL_TICKS = 20;
    /** Defensive ceilings inside {@link #rank}; the scan itself is bounded by the motive count. */
    public static final int MAX_CANDIDATES_VISITED = 16;
    public static final int MAX_RETAINED_CANDIDATES = 8;
    public static final int TARGET_RADIUS = 16;
    public static final double TARGET_RADIUS_SQUARED = (double) TARGET_RADIUS * TARGET_RADIUS;

    public static final int HAZARD_INTERVAL_TICKS = 20;
    public static final int MAX_HAZARD_READS = 27;
    public static final int MAX_SAFE_CANDIDATES = 24;
    public static final int MAX_CHARGED_READS = 256;

    public static final int PATH_INTERVAL_TICKS = 20;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;

    public static final float WITHDRAW_HEALTH_FRACTION = 0.20F;
    public static final int WITHDRAW_TICKS = 100;

    public static final int MAX_FEEDBACK_PARTICLES = 8;
    public static final int MAX_STUDY_PARTICLES = 4;
    public static final int STUDY_PARTICLE_INTERVAL_TICKS = 20;

    /** Representative encoded-state ceiling asserted by the state tests and the live save fixture. */
    public static final int MAX_STATE_BYTES = 768;

    private CircleMageRules() {
    }

    public enum Mode {
        IDLE,
        FOLLOWING,
        DEFENDING,
        STUDYING,
        WITHDRAWING
    }

    public enum Action {
        NONE,
        BOLT,
        STUDY
    }

    public enum TargetSource {
        NONE,
        DIRECT,
        OWNER,
        PEER_REPORT
    }

    /** Strictly ordered recruitment outcomes; only RECRUITED ever consumes an offering. */
    public enum RecruitmentResult {
        NOT_AN_OFFERING,
        BOUND_ELSEWHERE,
        ALREADY_BOUND_TO_PLAYER,
        COVEN_FULL,
        FAMILIAR_REQUIRED,
        RECRUITED;

        /** Only a brand new admission consumes exactly one offering. */
        public boolean consumesOffering() {
            return this == RECRUITED;
        }

        public boolean succeeded() {
            return this == RECRUITED || this == ALREADY_BOUND_TO_PLAYER;
        }
    }

    /** Directly observed relationship facts about one already-inspected candidate. */
    public record RelationFacts(
        boolean living,
        boolean alive,
        boolean sameDimension,
        boolean self,
        boolean invulnerableOrUnattackable,
        boolean owner,
        boolean sameOwnerCreature,
        boolean circleMage,
        boolean creativeOrSpectatorPlayer,
        boolean protectedPassive,
        boolean familiarOrOwnedElsewhere,
        boolean canAttackAccepted
    ) {
    }

    public record Candidate(
        UUID id,
        TargetSource source,
        double distanceSquared,
        boolean visible
    ) {
        public Candidate {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(source, "source");
        }
    }

    public record RouteResult(boolean pathFound, boolean reachable, boolean accepted) {
        public boolean success() {
            return pathFound && reachable && accepted;
        }
    }

    public record SearchOffset(int dx, int dy, int dz) {
    }

    public record SafeCandidate(
        double separationSquared,
        boolean hazardFree,
        double displacementSquared,
        long packedPosition
    ) {
    }

    // ---------------------------------------------------------------- recruitment

    /**
     * Exact ordering of the existing recruitment contract with the audited duplicate-consumption
     * defect fixed: a same-owner repeat is idempotent success that consumes nothing, and a
     * different owner can neither steal the Mage nor spend an offering trying.
     */
    public static RecruitmentResult recruitmentDecision(
        final Optional<UUID> currentOwner,
        final UUID player,
        final boolean offeringMatches,
        final boolean familiarPresent,
        final int rosterCount
    ) {
        Objects.requireNonNull(currentOwner, "currentOwner");
        Objects.requireNonNull(player, "player");
        if (!offeringMatches) {
            return RecruitmentResult.NOT_AN_OFFERING;
        }
        if (currentOwner.isPresent()) {
            return currentOwner.get().equals(player)
                ? RecruitmentResult.ALREADY_BOUND_TO_PLAYER
                : RecruitmentResult.BOUND_ELSEWHERE;
        }
        if (!FamiliarBondRules.canRecruitCovenMage(rosterCount)) {
            return RecruitmentResult.COVEN_FULL;
        }
        if (!familiarPresent) {
            return RecruitmentResult.FAMILIAR_REQUIRED;
        }
        return RecruitmentResult.RECRUITED;
    }

    // ---------------------------------------------------------------- relationship legality

    /**
     * Absolute exclusions. An unbound Mage never proactively targets survival players and a bound
     * Mage never inherits its owner's arbitrary target: only an actual accepted attacker relation
     * can propose one, and that proposal must still pass this legality gate.
     */
    public static boolean relationLegal(final RelationFacts facts) {
        Objects.requireNonNull(facts, "facts");
        if (!facts.living() || !facts.alive() || !facts.sameDimension()) {
            return false;
        }
        if (facts.self() || facts.owner() || facts.invulnerableOrUnattackable()) {
            return false;
        }
        if (facts.sameOwnerCreature() || facts.circleMage() || facts.creativeOrSpectatorPlayer()) {
            return false;
        }
        if (facts.protectedPassive() || facts.familiarOrOwnedElsewhere()) {
            return false;
        }
        return facts.canAttackAccepted();
    }

    /** Only these three motives may propose a target, in this exact order. */
    public static int motivePriority(final TargetSource source) {
        return switch (source) {
            case DIRECT -> 0;
            case OWNER -> 1;
            case PEER_REPORT -> 2;
            case NONE -> Integer.MAX_VALUE;
        };
    }

    public static List<Candidate> rank(final List<Candidate> inspected) {
        final List<Candidate> ordered = new ArrayList<>(
            Objects.requireNonNull(inspected, "inspected").stream()
                .filter(candidate -> candidate.source() != TargetSource.NONE)
                .limit(MAX_CANDIDATES_VISITED)
                .toList()
        );
        ordered.sort(Comparator
            .comparingInt((Candidate candidate) -> motivePriority(candidate.source()))
            .thenComparingDouble(Candidate::distanceSquared)
            .thenComparing(candidate -> candidate.id().toString()));
        return List.copyOf(ordered.stream().limit(MAX_RETAINED_CANDIDATES).toList());
    }

    public static Optional<Candidate> select(final List<Candidate> inspected) {
        return rank(inspected).stream().filter(Candidate::visible).findFirst();
    }

    // ---------------------------------------------------------------- owner and formation

    public static boolean withinFormation(final double distanceSquared) {
        return distanceSquared <= FORMATION_RADIUS_SQUARED;
    }

    public static boolean safeStepAllowed(final double distanceSquared, final int cooldownRemainingTicks) {
        return distanceSquared > SAFE_STEP_DISTANCE_SQUARED && cooldownRemainingTicks <= 0;
    }

    /**
     * A stable owner-relative arc slot derived from the sorted UUID order of the eligible loaded
     * same-owner Mages that were actually inspected. Ordering, never rank or authority.
     */
    public static int formationSlot(final UUID self, final List<UUID> inspectedPeers) {
        return stableIndex(self, inspectedPeers, MAX_COVEN_MAGES);
    }

    /** The lowest UUID inside the inspected set is the temporary aura provider for this cadence. */
    public static boolean auraProvider(final UUID self, final List<UUID> inspectedPeers) {
        return stableIndex(self, inspectedPeers, MAX_COVEN_MAGES) == 0;
    }

    public static boolean auraEligible(
        final boolean ownerResolved,
        final boolean ownerAlive,
        final boolean sameLevel,
        final double distanceSquared
    ) {
        return ownerResolved && ownerAlive && sameLevel && distanceSquared <= AURA_RADIUS_SQUARED;
    }

    private static int stableIndex(final UUID self, final List<UUID> inspectedPeers, final int cap) {
        Objects.requireNonNull(self, "self");
        final LinkedHashSet<UUID> inspected = new LinkedHashSet<>();
        inspected.add(self);
        for (final UUID peer : Objects.requireNonNull(inspectedPeers, "inspectedPeers")) {
            if (inspected.size() >= Math.min(cap, MAX_FORMATION_PEERS_VISITED)) {
                break;
            }
            if (peer != null) {
                inspected.add(peer);
            }
        }
        final List<UUID> sorted = new ArrayList<>(inspected);
        sorted.sort(Comparator.comparing(UUID::toString));
        return Math.max(0, sorted.indexOf(self));
    }

    // ---------------------------------------------------------------- peer report

    /** A report may be emitted only after an accepted direct or owner threat, and never recursively. */
    public static boolean mayEmitReport(
        final TargetSource source,
        final int cooldownRemainingTicks,
        final boolean receivedThisTargetAsReport
    ) {
        return cooldownRemainingTicks <= 0
            && !receivedThisTargetAsReport
            && (source == TargetSource.DIRECT || source == TargetSource.OWNER);
    }

    /**
     * At most two recipients in stable distance then UUID order, drawn from at most eight visited
     * same-owner peers. One hop only.
     */
    public static List<UUID> reportRecipients(final List<Candidate> inspectedPeers) {
        final List<Candidate> ordered = new ArrayList<>(
            Objects.requireNonNull(inspectedPeers, "inspectedPeers").stream()
                .limit(MAX_PEERS_VISITED)
                .toList()
        );
        ordered.sort(Comparator
            .comparingDouble(Candidate::distanceSquared)
            .thenComparing(candidate -> candidate.id().toString()));
        final LinkedHashSet<UUID> recipients = new LinkedHashSet<>();
        for (final Candidate peer : ordered) {
            if (recipients.size() >= MAX_PEERS_NOTIFIED) {
                break;
            }
            recipients.add(peer.id());
        }
        return List.copyOf(recipients);
    }

    /** A receiver always revalidates independently; an expired or illegal report is discarded. */
    public static boolean reportAcceptable(
        final boolean sameOwner,
        final boolean targetLegal,
        final boolean sameDimension,
        final int remainingTicks
    ) {
        return sameOwner && targetLegal && sameDimension && remainingTicks > 0;
    }

    // ---------------------------------------------------------------- study and conclave

    public static boolean studySearchAllowed(
        final int searchCooldownTicks,
        final int studyCooldownTicks,
        final boolean calm,
        final boolean targetless,
        final boolean safe,
        final boolean urgentFollow
    ) {
        return searchCooldownTicks <= 0 && studyCooldownTicks <= 0
            && calm && targetless && safe && !urgentFollow;
    }

    /** Eligible peers are loaded, alive, same owner, exact Circle Mage, calm, and near the site. */
    public static boolean conclaveAdmits(
        final boolean sameOwner,
        final boolean exactCircleMage,
        final boolean targetless,
        final boolean safe,
        final boolean alreadyInAnotherSession,
        final double distanceSquaredToSite
    ) {
        return sameOwner && exactCircleMage && targetless && safe
            && !alreadyInAnotherSession
            && distanceSquaredToSite <= CONCLAVE_RADIUS_SQUARED;
    }

    /** At most two accepted peers, so the total session is at most three. */
    public static List<UUID> acceptPeers(final List<UUID> eligible) {
        final List<UUID> visited = new ArrayList<>(
            Objects.requireNonNull(eligible, "eligible").stream().limit(MAX_PEERS_VISITED).toList()
        );
        visited.sort(Comparator.comparing(UUID::toString));
        return List.copyOf(visited.stream().distinct().limit(MAX_ACCEPTED_PEERS).toList());
    }

    /** Lowest UUID coordinates that one session only. Ordering, not rank, authority, or succession. */
    public static UUID coordinator(final UUID self, final List<UUID> accepted) {
        Objects.requireNonNull(self, "self");
        UUID lowest = self;
        for (final UUID peer : Objects.requireNonNull(accepted, "accepted")) {
            if (peer != null && peer.toString().compareTo(lowest.toString()) < 0) {
                lowest = peer;
            }
        }
        return lowest;
    }

    public static int sessionSlot(final UUID self, final UUID coordinator, final List<UUID> accepted) {
        final List<UUID> members = new ArrayList<>();
        members.add(Objects.requireNonNull(coordinator, "coordinator"));
        for (final UUID peer : Objects.requireNonNull(accepted, "accepted")) {
            if (peer != null && !members.contains(peer) && members.size() < MAX_SESSION_SIZE) {
                members.add(peer);
            }
        }
        if (!members.contains(self) && members.size() < MAX_SESSION_SIZE) {
            members.add(self);
        }
        members.sort(Comparator.comparing(UUID::toString));
        return Math.clamp(members.indexOf(self), 0, MAX_SESSION_SIZE - 1);
    }

    /**
     * Invalidation only. A no-show never blocks another participant and no missed session is
     * replayed. The session TIMEOUT is deliberately not a parameter: tick dispatch ends timed-out
     * phases in exactly one place, so folding the timer in here would give the transition two
     * owners and leave one of them unreachable.
     */
    public static boolean sessionReleased(
        final boolean coordinatorValid,
        final boolean workstationValid,
        final boolean safe,
        final boolean targetless
    ) {
        return !coordinatorValid || !workstationValid || !safe || !targetless;
    }

    // ---------------------------------------------------------------- bolt

    public static boolean boltEligible(
        final boolean lineOfSight,
        final boolean relationLegal,
        final double distanceSquared
    ) {
        return lineOfSight && relationLegal
            && distanceSquared >= BOLT_MIN_RANGE_SQUARED
            && distanceSquared <= BOLT_MAX_RANGE_SQUARED;
    }

    public static float boltDamage(final boolean focusReserved) {
        return focusReserved ? BOLT_FOCUSED_DAMAGE : BOLT_DAMAGE;
    }

    /** Focus is consumed only after the focused hit was actually accepted. */
    public static boolean consumesFocus(final boolean focusReserved, final boolean hurtAccepted) {
        return focusReserved && hurtAccepted;
    }

    public static boolean mayRetarget(
        final Action current,
        final UUID frozenTarget,
        final UUID candidate
    ) {
        if (current == Action.NONE) {
            return true;
        }
        return frozenTarget != null && frozenTarget.equals(candidate);
    }

    // ---------------------------------------------------------------- health and priority

    public static float healthFraction(final float health, final float maxHealth) {
        if (!Float.isFinite(health) || !Float.isFinite(maxHealth) || maxHealth <= 0.0F) {
            return 1.0F;
        }
        return Math.clamp(health / maxHealth, 0.0F, 1.0F);
    }

    public static boolean shouldWithdraw(final float healthFraction) {
        return healthFraction <= WITHDRAW_HEALTH_FRACTION;
    }

    public enum Priority {
        RECALL,
        HAZARD,
        ACTION,
        DEFENSE,
        WITHDRAW,
        OWNER_FOLLOW,
        PEER_DEFENSE,
        ACTIVE_STUDY,
        STUDY_PROPOSAL,
        ANCHOR_RETURN
    }

    /** The frozen strict order. Explicit Seer recall reconciliation preempts everything. */
    public static Priority priority(
        final boolean recallPending,
        final boolean urgentHazard,
        final boolean immutableActionPending,
        final boolean directOrOwnerDefense,
        final boolean lowHealth,
        final boolean ownerFollow,
        final boolean peerDefense,
        final boolean activeStudy,
        final boolean studyProposal
    ) {
        if (recallPending) {
            return Priority.RECALL;
        }
        if (urgentHazard) {
            return Priority.HAZARD;
        }
        if (immutableActionPending) {
            return Priority.ACTION;
        }
        if (directOrOwnerDefense) {
            return Priority.DEFENSE;
        }
        if (lowHealth) {
            return Priority.WITHDRAW;
        }
        if (ownerFollow) {
            return Priority.OWNER_FOLLOW;
        }
        if (peerDefense) {
            return Priority.PEER_DEFENSE;
        }
        if (activeStudy) {
            return Priority.ACTIVE_STUDY;
        }
        return studyProposal ? Priority.STUDY_PROPOSAL : Priority.ANCHOR_RETURN;
    }

    // ---------------------------------------------------------------- movement lease

    public static boolean pathRequestAllowed(
        final int remainingPathTicks,
        final int backoffRemainingTicks
    ) {
        return remainingPathTicks <= 0 && backoffRemainingTicks <= 0;
    }

    public static int routeFailuresAfter(final int failures, final RouteResult result) {
        return result.success() ? 0 : Math.clamp(failures + 1, 0, MAX_ROUTE_FAILURES);
    }

    public static boolean routeExhausted(final int failures) {
        return failures >= MAX_ROUTE_FAILURES;
    }

    public static int routeBackoffAfter(final int failures) {
        return routeExhausted(failures) ? ROUTE_BACKOFF_TICKS : 0;
    }

    // ---------------------------------------------------------------- durations

    public static int clampRemaining(final int stored, final int maximum) {
        if (stored < 0) {
            return 0;
        }
        return Math.min(stored, Math.max(0, maximum));
    }

    public static int decrementLoaded(final int remaining) {
        return Math.max(0, remaining - 1);
    }

    public static int stableOffset(final UUID id, final int span) {
        if (id == null || span <= 0) {
            return 0;
        }
        return (int) Math.floorMod(
            id.getMostSignificantBits() ^ id.getLeastSignificantBits(), (long) span
        );
    }

    // ---------------------------------------------------------------- bounded search envelopes

    /**
     * All three Circle Mage envelopes delegate to the single shared geometric primitive in
     * {@link HedgeCroneRules#sweepEnvelope}. That primitive carries no motive, target policy, or
     * cadence: it is pure geometry, exactly the kind of common safety shape the approved design
     * permits the two practitioners to reuse. The previous verbatim copy of the enumeration was
     * the direct cause of an unreachable vertical envelope, so it is deliberately not duplicated.
     */
    private static List<SearchOffset> envelope(
        final UUID id,
        final int horizontalRadius,
        final int verticalRadius,
        final int budget,
        final int hardCap
    ) {
        return HedgeCroneRules.sweepEnvelope(id, horizontalRadius, verticalRadius, budget, hardCap)
            .stream()
            .map(offset -> new SearchOffset(offset.dx(), offset.dy(), offset.dz()))
            .toList();
    }

    public static List<SearchOffset> workstationOffsets(
        final UUID id,
        final int horizontalRadius,
        final int verticalRadius,
        final int budget
    ) {
        return envelope(id, horizontalRadius, verticalRadius, budget, MAX_WORKSTATION_CANDIDATES);
    }

    public static List<SearchOffset> safeSearchOffsets(
        final UUID id,
        final int horizontalRadius,
        final int verticalRadius,
        final int budget
    ) {
        return envelope(id, horizontalRadius, verticalRadius, budget, MAX_SAFE_CANDIDATES);
    }

    /** The owner-relative safe-step envelope: at most twenty-five deterministic candidates. */
    public static List<SearchOffset> safeStepOffsets(final UUID id, final int radius) {
        return envelope(id, radius, 1, MAX_SAFE_STEP_CANDIDATES, MAX_SAFE_STEP_CANDIDATES);
    }

    public static Comparator<SafeCandidate> safeCandidatePreference() {
        return Comparator.comparingDouble(SafeCandidate::separationSquared).reversed()
            .thenComparingInt(candidate -> candidate.hazardFree() ? 0 : 1)
            .thenComparingDouble(SafeCandidate::displacementSquared)
            .thenComparingLong(SafeCandidate::packedPosition);
    }
}
