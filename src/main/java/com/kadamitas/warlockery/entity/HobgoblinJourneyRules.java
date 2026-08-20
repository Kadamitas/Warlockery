package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The complete loader-neutral F11 Hobgoblin decision surface. Every method here is a pure function
 * of already-observed facts: no {@code Level}, entity, path, registry, block state, container,
 * random source, or loader API is ever accepted, and nothing here mutates. The runtime observes,
 * this class decides, and the runtime executes exactly one decision.
 *
 * <p>Durations are remaining loaded-tick counts, never absolute world deadlines, so an unloaded
 * caravan pauses meaning rather than silently expiring it. Sentinels are zero-is-due, and the only
 * far-future cadence value is {@link #FAR_FUTURE_TICKS}, deliberately bounded well below
 * {@code Long.MAX_VALUE} so arithmetic on it can never overflow.</p>
 */
public final class HobgoblinJourneyRules {
    // ---------------------------------------------------------------- identity

    /** F11 owns exactly one kind. Every entry point re-checks this rather than trusting a caller. */
    public static final CreatureKind KIND = CreatureKind.HOBGOBLIN;

    /** Bounded far-future cadence sentinel; never {@code Long.MAX_VALUE}. */
    public static final int FAR_FUTURE_TICKS = 72_000;

    // ---------------------------------------------------------------- cadence

    public static final int DECISION_INTERVAL_TICKS = 20;
    public static final int URGENT_DECISION_INTERVAL_TICKS = 10;
    public static final int PERCEPTION_INTERVAL_TICKS = 40;
    public static final int GROUP_INTERVAL_TICKS = 40;
    public static final int WORK_SCAN_INTERVAL_TICKS = 40;
    public static final int VILLAGE_INTERVAL_TICKS = 40;
    public static final int AMBIENT_INTERVAL_TICKS = 100;
    public static final int RELATION_INTERVAL_TICKS = 200;
    public static final int CAMP_PROPOSAL_INTERVAL_TICKS = 400;
    public static final int NAVIGATION_INTERVAL_TICKS = 20;
    public static final int PROGRESS_INTERVAL_TICKS = 40;
    public static final int FEEDBACK_INTERVAL_TICKS = 40;
    public static final int MINING_INTERVAL_TICKS = 40;
    public static final int MAX_SCHEDULE_OFFSET_TICKS = 39;

    // ---------------------------------------------------------------- perception budgets

    public static final int PERCEPTION_RADIUS = 16;
    public static final int MEMBER_RADIUS = 24;
    public static final int LOOSE_ITEM_RADIUS = 12;
    public static final int DEPOSIT_RADIUS = 16;
    public static final int MAX_ENTITY_VISITS = 16;
    public static final int MAX_ENTITY_RETAINED = 4;
    public static final int MAX_MEMBER_VISITS = 16;
    public static final int MAX_MEMBER_RETAINED = 4;
    public static final int MAX_LOOSE_VISITS = 16;
    public static final int MAX_LOOSE_RETAINED = 4;

    // ---------------------------------------------------------------- charged read budgets

    public static final int MAX_VILLAGE_BLOCK_READS = 64;
    public static final int MAX_VILLAGE_CANDIDATES = 16;
    public static final int MAX_RETAINED_EXITS = 4;
    public static final int MAX_WORK_BLOCK_READS = 128;
    public static final int MAX_MINING_BLOCK_READS = 128;
    public static final int MAX_CAMP_BLOCK_READS = 128;
    public static final int MAX_CHILD_BLOCK_READS = 64;

    // ---------------------------------------------------------------- village exclusion

    public static final int VILLAGE_BUFFER = 32;
    public static final int VILLAGER_SIGNAL_RADIUS = 12;
    public static final int EXIT_MIN_DISTANCE = 12;
    public static final int EXIT_MAX_DISTANCE = 24;
    public static final int BLOCKED_EXIT_BACKOFF_TICKS = 100;
    public static final int MAX_BLOCKED_EXITS = 3;

    // ---------------------------------------------------------------- caravan

    public static final int MAX_CARAVAN_RECORDS = 128;
    public static final int MAX_CARAVAN_MEMBERS = 4;
    public static final int MEMBER_EXPIRY_TICKS = 1_200;
    public static final int COHESION_RADIUS = 16;
    public static final int REGROUP_RADIUS = 24;
    public static final int REGROUP_DEADLINE_TICKS = 400;
    public static final int LEADER_STABILIZE_TICKS = 20;
    public static final int CHILD_GUARD_RADIUS = 12;
    public static final int REGION_SIZE = 128;

    // ---------------------------------------------------------------- camp

    public static final int MAX_CAMP_RECORDS = 64;
    public static final int CAMP_FOOTPRINT_HORIZONTAL = 2;
    public static final int CAMP_FOOTPRINT_VERTICAL = 3;
    public static final int CAMP_MAX_EDITS = 32;
    public static final int CAMP_EDITS_PER_TICK = 4;
    public static final int CAMP_TEARDOWN_PER_TICK = 4;
    public static final int CAMP_EXPIRY_TICKS = FAR_FUTURE_TICKS;
    public static final int CAMP_EVENT_HOLD_TICKS = 12_000;
    public static final int CAMP_DIRT_COST = 18;
    public static final int CAMP_LOG_COST = 3;

    // ---------------------------------------------------------------- claims and work

    public static final int MAX_CLAIM_RECORDS = 128;
    public static final int CLAIM_LEASE_TICKS = 200;
    public static final int MAX_CONTRACT_UNITS = 8;
    public static final int CONTRACT_DURATION_TICKS = 24_000;

    // ---------------------------------------------------------------- relations

    public static final int MAX_RELATION_FACTS = 8;
    public static final int MIN_RELATION_SCORE = -4;
    public static final int MAX_RELATION_SCORE = 4;

    // ---------------------------------------------------------------- combat

    public static final int AGGRESSOR_MEMORY_TICKS = 120;
    public static final int DEFEND_RESCUE_RADIUS = 12;
    public static final int DISENGAGE_RADIUS = 16;
    public static final float ESCAPE_HEALTH_FRACTION = 0.35F;
    public static final int MAX_ALARM_MEMBERS = 4;

    // ---------------------------------------------------------------- navigation

    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;
    public static final int ROUTE_RETRY_TICKS = 40;
    public static final int MAX_LOCAL_ROUTE_BLOCKS = 32;

    // ---------------------------------------------------------------- merchant

    public static final int MIN_MERCHANT_LEVEL = 1;
    public static final int MAX_MERCHANT_LEVEL = 5;
    public static final int MAX_RESTOCKS_PER_DAY = 2;
    public static final int RESTOCK_SPACING_TICKS = 2_400;
    private static final int[] LEVEL_XP_THRESHOLDS = {0, 10, 70, 150, 250};

    // ---------------------------------------------------------------- family

    public static final int BIRTH_COOLDOWN_TICKS = FAR_FUTURE_TICKS;
    public static final int CHILD_GIFT_COOLDOWN_TICKS = 12_000;
    public static final int MIN_DANCE_CHILDREN = 3;
    public static final int MAX_DANCE_CHILDREN = 3;

    // ---------------------------------------------------------------- spawning

    public static final int MIN_HUMAN_VILLAGE_DISTANCE = VILLAGE_BUFFER;
    public static final int LOCAL_SPAWN_CAP = 6;
    public static final int LOCAL_SPAWN_CAP_RADIUS = 32;

    // ---------------------------------------------------------------- schema

    public static final int STATE_SCHEMA_VERSION = 1;
    public static final int DATA_SCHEMA_VERSION = 1;
    /**
      * The measured ceiling for a fully populated state. The approved design sketched 1 KiB before
      * the eight-entry relationship ledger existed; eight facts stored with string UUIDs cost about
      * 0.6 KiB on their own, so the honest declared bound is 2 KiB.
      */
    public static final int MAX_STATE_BYTES = 2_048;

    private HobgoblinJourneyRules() {
    }

    // ================================================================ enums

    /** The declared day division. Actual observed safety still overrides at execution time. */
    public enum Period {
        DAY, DUSK, NIGHT, DAWN
    }

    /**
     * The finite semantic mode vocabulary. Exactly one mode is live at a time and no other mode may
     * ever be committed.
     */
    public enum Mode {
        IDLE, TRADE_WAIT, VILLAGE_EXIT, TRAVEL, REGROUP, CAMP_PROPOSE, CAMP_BUILD, CAMP_REST,
        CAMP_TEARDOWN, WORK_APPROACH, WORK_COMMIT, DEFEND, FLEE, CHILD_PLAY;

        /** Only these two modes may ever mutate a block. */
        public boolean editsWorld() {
            return this == CAMP_BUILD || this == CAMP_TEARDOWN || this == WORK_COMMIT;
        }

        /** A mode that owns a worksite or a camp footprint must hold a live claim to run. */
        public boolean requiresClaim() {
            return editsWorld() || this == WORK_APPROACH;
        }


        /** Danger and mandatory-exit modes may pre-empt any committed transaction. */
        public boolean isUrgent() {
            return this == FLEE || this == DEFEND || this == VILLAGE_EXIT;
        }
    }

    /** The bounded work-contract vocabulary. */
    public enum ContractKind {
        NONE, GATHER, MINING, LEGACY_WORK
    }

    /** Exactly why an agreement ended. {@code ACTIVE} means it has not. */
    public enum ContractEnd {
        ACTIVE, COMPLETED, EXPIRED, DISMISSED, BETRAYED, INVALID
    }

    /**
     * The eight supported hospitality facts. Magnitudes are small on purpose: the score is clamped
     * to {@code [-4, 4]} so no amount of repetition can turn a traveler into a pet or a hunter.
     */
    public enum RelationFact {
        FAIR_TRADE(1, 12_000),
        AID(1, 12_000),
        ACCEPTED_FOOD(1, 6_000),
        WORK_COMPLETED(2, 24_000),
        ATTACK(-3, 24_000),
        COERCION(-2, 12_000),
        CHEATING(-2, 12_000),
        DISMISSAL_GIFT(-1, 6_000);

        private final int magnitude;
        private final int expiryTicks;

        RelationFact(final int magnitude, final int expiryTicks) {
            this.magnitude = magnitude;
            this.expiryTicks = expiryTicks;
        }

        public int magnitude() {
            return magnitude;
        }

        public int expiryTicks() {
            return expiryTicks;
        }

    }

    /** The camp lifecycle. Phase order is enforced by the runtime, not by the record. */
    public enum CampPhase {
        NONE, PROPOSE, RESERVE, VALIDATE, COMMIT, ACTIVE, SUSPEND, EXPIRE, TEARDOWN, RELEASE
    }

    /** How a candidate relates to this family for defensive purposes. */
    public enum TargetClass {
        PROTECTED, DIRECT_AGGRESSOR, NEUTRAL
    }

    /** The one defensive answer, chosen once per decision. */
    public enum DefensiveResponse {
        NONE, DEFEND, FLEE
    }

    public enum RouteFailure {
        NONE, NO_PATH, UNREACHABLE, STUCK
    }

    /**
     * What the last survey actually found. Every flag is a fact, never a plan, so the decision table
     * cannot invent a job the survey did not observe.
     */
    public record WorkAvailability(
        boolean mineable,
        boolean deposit,
        boolean looseItem,
        boolean campSite,
        boolean campMaterials,
        boolean childFlower,
        boolean childDance,
        boolean childGift
    ) {
        public static WorkAvailability none() {
            return new WorkAvailability(false, false, false, false, false, false, false, false);
        }

        public boolean anyAdultWork() {
            return mineable || deposit || looseItem;
        }

        public boolean anyChildPlay() {
            return childFlower || childDance || childGift;
        }
    }

    // ================================================================ identity and cadence

    public static boolean isExactHobgoblin(final CreatureKind kind) {
        return kind == KIND;
    }

    /**
     * Stable non-negative identity offset in {@code [0, bound)}. Uses both UUID halves so two
     * travelers created in the same tick still stagger, and never returns a negative modulus.
     */
    public static int stableOffset(final UUID id, final int bound) {
        if (id == null || bound <= 0) {
            return 0;
        }
        final long mixed = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        return (int) Math.floorMod(mixed, bound);
    }

    /** Clamps a remaining-tick counter into {@code [0, maximum]}. Zero always reads as due. */
    public static int clampRemaining(final int remaining, final int maximum) {
        return Math.clamp(remaining, 0, Math.max(0, maximum));
    }

    /** A zero sentinel is due; a negative one is treated as due rather than as far future. */
    public static boolean isDue(final int remainingTicks) {
        return remainingTicks <= 0;
    }

    /**
     * Overworld-style schedule with a stable identity offset of at most
     * {@link #MAX_SCHEDULE_OFFSET_TICKS}. Accepts any day time and normalizes it, so a level whose
     * clock has run far past one day still resolves.
     */
    public static Period period(final long dayTime, final int identityOffsetTicks) {
        final long offset = Math.clamp(identityOffsetTicks, 0, MAX_SCHEDULE_OFFSET_TICKS);
        final long normalized = Math.floorMod(dayTime + offset, 24_000L);
        if (normalized < 12_000L) {
            return Period.DAY;
        }
        if (normalized <= 14_000L) {
            return Period.DUSK;
        }
        if (normalized <= 22_000L) {
            return Period.NIGHT;
        }
        return Period.DAWN;
    }

    /** The decision cadence tightens only while the traveler is actually in danger or exiting. */
    public static int decisionInterval(final Mode mode) {
        return mode != null && mode.isUrgent()
            ? URGENT_DECISION_INTERVAL_TICKS
            : DECISION_INTERVAL_TICKS;
    }

    /** Region-and-kind caravan key. Two dimensions never share a key because data is per level. */
    public static long caravanKey(final int blockX, final int blockZ) {
        final long regionX = Math.floorDiv(blockX, REGION_SIZE);
        final long regionZ = Math.floorDiv(blockZ, REGION_SIZE);
        final long region = (regionX & 0x7FFF_FFFFL) << 32 | regionZ & 0xFFFF_FFFFL;
        return region * 31L + KIND.ordinal();
    }

    /** Exactly one camp per caravan, so the camp key is derived from the caravan key. */
    public static long campKey(final long caravan) {
        return caravan * 31L + 17L;
    }

    // ================================================================ spawning and persistence

    /**
     * Natural travelers refuse a village origin, refuse to appear inside the exclusion buffer of an
     * observed village, and refuse to stack past the local cap.
     */
    public static boolean canSpawnNaturally(
        final boolean insideVillage,
        final int distanceToHumanVillage,
        final int localTravelerCount
    ) {
        return !insideVillage
            && distanceToHumanVillage >= MIN_HUMAN_VILLAGE_DISTANCE
            && localTravelerCount < LOCAL_SPAWN_CAP;
    }

    /** Exactly one persistence reason, in a fixed precedence, or ordinary distance despawn. */
    public enum PersistenceReason {
        CARAVAN_MEMBER, CAMP_RESIDENT, CONTRACTED, EVENT_RESIDENT
    }

    public static Optional<PersistenceReason> persistenceReason(
        final boolean caravanMember,
        final boolean campResident,
        final boolean contracted,
        final boolean eventResident
    ) {
        if (eventResident) {
            return Optional.of(PersistenceReason.EVENT_RESIDENT);
        }
        if (contracted) {
            return Optional.of(PersistenceReason.CONTRACTED);
        }
        if (campResident) {
            return Optional.of(PersistenceReason.CAMP_RESIDENT);
        }
        return caravanMember ? Optional.of(PersistenceReason.CARAVAN_MEMBER) : Optional.empty();
    }

    /** An unanchored solitary traveler uses ordinary creature distance despawn. */
    public static boolean mayDespawn(
        final boolean caravanMember,
        final boolean campResident,
        final boolean contracted,
        final boolean eventResident
    ) {
        return persistenceReason(caravanMember, campResident, contracted, eventResident).isEmpty();
    }

    // ================================================================ village exclusion

    /**
     * The one village-exclusion predicate. It is deliberately a policy over already-observed facts:
     * an exact position inside a village, a loaded exact Villager inside the local signal radius, or
     * a candidate footprint that intersects the configured buffer all exclude the position equally.
     */
    public static boolean villageExcluded(
        final boolean positionInsideVillage,
        final boolean villagerWithinSignalRadius,
        final boolean footprintIntersectsBuffer
    ) {
        return positionInsideVillage || villagerWithinSignalRadius || footprintIntersectsBuffer;
    }

    /** An exit candidate must leave the village and land in the declared outward band. */
    public static boolean exitCandidateAccepted(
        final double horizontalDistanceFromSelf,
        final boolean candidateExcluded,
        final boolean candidateSafe
    ) {
        return candidateSafe
            && !candidateExcluded
            && horizontalDistanceFromSelf >= EXIT_MIN_DISTANCE
            && horizontalDistanceFromSelf <= EXIT_MAX_DISTANCE;
    }

    /**
     * Three blocked exits stop navigation and impose the declared wait. The traveler stays
     * non-hostile and never breaks a village block; it simply retries later.
     */
    public static boolean exitBlocked(final int consecutiveFailures) {
        return consecutiveFailures >= MAX_BLOCKED_EXITS;
    }

    // ================================================================ priority and mode

    /**
     * The one semantic priority contract. Exactly one mode is produced per decision and the ordering
     * is fixed:
     *
     * <ol>
     *   <li>immediate hazard or low-health escape;</li>
     *   <li>direct-aggressor defence of a child or a fallen caravan member;</li>
     *   <li>mandatory human-village exit;</li>
     *   <li>an already-committed atomic transaction;</li>
     *   <li>an open customer trade;</li>
     *   <li>camp lifecycle work that the group already owns;</li>
     *   <li>contract or schedule work;</li>
     *   <li>regroup, travel, and idle.</li>
     * </ol>
     *
     * <p>A child never receives an adult, camp, work, or combat mode.</p>
     */
    public static Mode selectMode(
        final boolean baby,
        final boolean hazardActive,
        final DefensiveResponse defence,
        final boolean insideExcludedSpace,
        final boolean transactionCommitted,
        final boolean trading,
        final CampPhase campPhase,
        final boolean campEventHeld,
        final boolean regrouping,
        final Period period,
        final boolean contractActive,
        final WorkAvailability work
    ) {
        if (hazardActive || defence == DefensiveResponse.FLEE) {
            return Mode.FLEE;
        }
        if (baby) {
            if (insideExcludedSpace) {
                return Mode.VILLAGE_EXIT;
            }
            return work != null && work.anyChildPlay() ? Mode.CHILD_PLAY : Mode.IDLE;
        }
        if (defence == DefensiveResponse.DEFEND) {
            return Mode.DEFEND;
        }
        if (insideExcludedSpace) {
            return Mode.VILLAGE_EXIT;
        }
        if (transactionCommitted) {
            return campPhase == CampPhase.TEARDOWN ? Mode.CAMP_TEARDOWN
                : campPhase == CampPhase.COMMIT ? Mode.CAMP_BUILD : Mode.WORK_COMMIT;
        }
        if (trading) {
            return Mode.TRADE_WAIT;
        }
        if (campPhase == CampPhase.TEARDOWN && !campEventHeld) {
            return Mode.CAMP_TEARDOWN;
        }
        if (campPhase == CampPhase.COMMIT) {
            return Mode.CAMP_BUILD;
        }
        if (regrouping) {
            return Mode.REGROUP;
        }
        final WorkAvailability facts = work == null ? WorkAvailability.none() : work;
        final boolean workPeriod = period == Period.NIGHT || period == Period.DUSK || contractActive;
        if (workPeriod && facts.anyAdultWork()) {
            return Mode.WORK_APPROACH;
        }
        if (campPhase == CampPhase.ACTIVE) {
            return Mode.CAMP_REST;
        }
        if (period == Period.DUSK && facts.campSite() && facts.campMaterials()) {
            return Mode.CAMP_PROPOSE;
        }
        if (facts.anyAdultWork() && contractActive) {
            return Mode.WORK_APPROACH;
        }
        return Mode.TRAVEL;
    }

    /**
     * A committed mode is only pre-empted by a strictly more urgent one. This is the single place
     * that decides whether an in-flight transaction may be abandoned.
     */
    public static boolean interrupts(final Mode current, final Mode candidate) {
        if (current == null || candidate == null || current == candidate) {
            return false;
        }
        if (candidate.isUrgent()) {
            return true;
        }
        return !current.editsWorld();
    }

    // ================================================================ contracts

    /**
     * Only an adult, unthreatened, non-trading, non-event traveler standing outside village space
     * and holding no other valid contractor may accept an agreement.
     */
    public static boolean canAcceptContract(
        final boolean adult,
        final boolean threatened,
        final boolean trading,
        final boolean eventControlled,
        final boolean insideExcludedSpace,
        final boolean alreadyContracted
    ) {
        return adult && !threatened && !trading && !eventControlled
            && !insideExcludedSpace && !alreadyContracted;
    }

    /** The agreement ends the moment any one of its declared terminators is true. */
    public static ContractEnd contractOutcome(
        final boolean contractorResolvable,
        final int remainingTicks,
        final int completedUnits,
        final boolean dismissed,
        final boolean betrayed
    ) {
        if (betrayed) {
            return ContractEnd.BETRAYED;
        }
        if (dismissed) {
            return ContractEnd.DISMISSED;
        }
        if (!contractorResolvable) {
            return ContractEnd.INVALID;
        }
        if (completedUnits >= MAX_CONTRACT_UNITS) {
            return ContractEnd.COMPLETED;
        }
        return isDue(remainingTicks) ? ContractEnd.EXPIRED : ContractEnd.ACTIVE;
    }

    /** Job preference is a profession fact, not a random roll. */
    public static ContractKind preferredWork(final GoblinProfession profession) {
        return profession == GoblinProfession.MINER ? ContractKind.MINING : ContractKind.GATHER;
    }

    // ================================================================ relations

    /**
     * The bounded relation score. Facts collapse by kind, the sum is clamped, and no amount of
     * repetition can widen the window.
     */
    public static int relationScore(final List<RelationFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (final RelationFact fact : facts.stream().limit(MAX_RELATION_FACTS).toList()) {
            if (fact != null) {
                total += fact.magnitude();
            }
        }
        return Math.clamp(total, MIN_RELATION_SCORE, MAX_RELATION_SCORE);
    }

    /** Negative reputation refuses trade outright; a direct attack is handled separately. */
    public static boolean tradeRefused(final int relationScore) {
        return relationScore < 0;
    }

    /** A modest, vanilla-safe price improvement. Item identities are never changed. */
    public static int priceImprovement(final int relationScore) {
        return Math.clamp(relationScore, 0, MAX_RELATION_SCORE);
    }

    /**
     * At capacity the weakest, then oldest, fact is evicted. Ordering is fully deterministic so two
     * servers with identical facts always evict the identical entry.
     */
    public static int evictionIndex(final List<RelationFact> facts, final List<Integer> ages) {
        if (facts == null || facts.isEmpty()) {
            return -1;
        }
        int worst = 0;
        for (int index = 1; index < facts.size(); index++) {
            final int currentStrength = Math.abs(facts.get(index).magnitude());
            final int bestStrength = Math.abs(facts.get(worst).magnitude());
            if (currentStrength < bestStrength) {
                worst = index;
                continue;
            }
            if (currentStrength == bestStrength
                && ages != null && index < ages.size() && worst < ages.size()
                && ages.get(index) > ages.get(worst)) {
                worst = index;
            }
        }
        return worst;
    }

    // ================================================================ caravan and family

    /**
     * The leader is the lowest unsigned adult UUID. Adults never yield a route to a child, and a
     * lone remaining adult is a valid solitary traveler rather than a leaderless group.
     */
    public static Optional<UUID> electLeader(final List<UUID> adultMembers) {
        if (adultMembers == null || adultMembers.isEmpty()) {
            return Optional.empty();
        }
        return adultMembers.stream()
            .filter(java.util.Objects::nonNull)
            .limit(MAX_CARAVAN_MEMBERS)
            .min(unsignedUuidOrder());
    }

    /** Deterministic unsigned UUID ordering; never relies on iteration or hash order. */
    public static Comparator<UUID> unsignedUuidOrder() {
        return Comparator
            .comparingLong((UUID id) -> id.getMostSignificantBits() + Long.MIN_VALUE)
            .thenComparingLong(id -> id.getLeastSignificantBits() + Long.MIN_VALUE);
    }

    public static boolean shouldRegroup(final double distanceToLeader) {
        return distanceToLeader > REGROUP_RADIUS;
    }

    public static boolean regroupSatisfied(final double distanceToLeader) {
        return distanceToLeader <= COHESION_RADIUS;
    }

    /** After the deadline a stranded member leaves the caravan safely; it is never teleported. */
    public static boolean regroupAbandoned(final int regroupRemainingTicks) {
        return isDue(regroupRemainingTicks);
    }

    /**
     * Conception needs two willing same-kind adults, a safe group context, accepted food, group
     * headroom, and an expired birth cooldown. Nothing else may create a Hobgoblin.
     */
    public static boolean canConceive(
        final int caravanPopulation,
        final boolean partnerPresent,
        final boolean safeContext,
        final boolean foodAccepted,
        final int birthCooldownTicks
    ) {
        return partnerPresent
            && safeContext
            && foodAccepted
            && isDue(birthCooldownTicks)
            && caravanPopulation < MAX_CARAVAN_MEMBERS;
    }

    /** The child inherits the lower deterministic parent UUID's profession. */
    public static GoblinProfession childProfession(
        final UUID firstParent,
        final GoblinProfession firstProfession,
        final UUID secondParent,
        final GoblinProfession secondProfession
    ) {
        final GoblinProfession first = firstProfession == null ? GoblinProfession.FALLBACK : firstProfession;
        final GoblinProfession second = secondProfession == null ? GoblinProfession.FALLBACK : secondProfession;
        if (firstParent == null) {
            return second;
        }
        if (secondParent == null) {
            return first;
        }
        return unsignedUuidOrder().compare(firstParent, secondParent) <= 0 ? first : second;
    }

    public static boolean canDance(final int sameCaravanChildren) {
        return sameCaravanChildren >= MIN_DANCE_CHILDREN;
    }

    public static boolean giftReady(final boolean holdingFlower, final int giftCooldownTicks) {
        return holdingFlower && isDue(giftCooldownTicks);
    }

    // ================================================================ camp lifecycle

    /**
     * Every camp precondition in one place. A physical shelter additionally requires
     * {@code mobGriefing}; a data-only camp is the declared safe fallback when it is false.
     */
    public static boolean campEligible(
        final int caravanPopulation,
        final boolean anyAdult,
        final boolean hazardFree,
        final boolean excludedSpace,
        final boolean footprintLoaded,
        final boolean footprintClear,
        final boolean withinWorldBorder,
        final boolean materialsReserved,
        final boolean caravanAlreadyHasCamp,
        final int dimensionCampRecords
    ) {
        return caravanPopulation >= 2
            && caravanPopulation <= MAX_CARAVAN_MEMBERS
            && anyAdult
            && hazardFree
            && !excludedSpace
            && footprintLoaded
            && footprintClear
            && withinWorldBorder
            && materialsReserved
            && !caravanAlreadyHasCamp
            && dimensionCampRecords < MAX_CAMP_RECORDS;
    }

    /** Physical shelter needs griefing permission; without it the record stays data-only. */
    public static boolean campMayPlaceBlocks(final boolean mobGriefing) {
        return mobGriefing;
    }

    public static int campEditsThisTick(final int remainingEdits) {
        return Math.clamp(remainingEdits, 0, CAMP_EDITS_PER_TICK);
    }

    public static int campTeardownThisTick(final int remainingJournalEntries) {
        return Math.clamp(remainingJournalEntries, 0, CAMP_TEARDOWN_PER_TICK);
    }

    /**
     * A camp expires on its own deadline, or immediately when its caravan is gone. A live matching
     * external event holds teardown, but only until the bounded stale-event deadline.
     */
    public static boolean campExpired(
        final int expiryRemainingTicks,
        final boolean caravanPresent,
        final boolean eventActive,
        final int eventHoldRemainingTicks
    ) {
        if (eventActive && !isDue(eventHoldRemainingTicks)) {
            return false;
        }
        return !caravanPresent || isDue(expiryRemainingTicks);
    }

    /**
     * The next phase, given only the current phase and the observed facts. The record never advances
     * itself: this table is called from the tick branch that owns the transition.
     */
    public static CampPhase nextCampPhase(
        final CampPhase current,
        final boolean eligible,
        final boolean allEditsCommitted,
        final boolean invalidated,
        final boolean expired,
        final boolean journalEmpty
    ) {
        if (current == null) {
            return CampPhase.NONE;
        }
        return switch (current) {
            case NONE -> eligible ? CampPhase.PROPOSE : CampPhase.NONE;
            case PROPOSE -> eligible ? CampPhase.RESERVE : CampPhase.NONE;
            case RESERVE -> eligible ? CampPhase.VALIDATE : CampPhase.RELEASE;
            case VALIDATE -> eligible ? CampPhase.COMMIT : CampPhase.RELEASE;
            case COMMIT -> invalidated ? CampPhase.SUSPEND
                : allEditsCommitted ? CampPhase.ACTIVE : CampPhase.COMMIT;
            case ACTIVE -> invalidated ? CampPhase.SUSPEND
                : expired ? CampPhase.EXPIRE : CampPhase.ACTIVE;
            case SUSPEND -> expired ? CampPhase.EXPIRE
                : invalidated ? CampPhase.SUSPEND : CampPhase.ACTIVE;
            case EXPIRE -> CampPhase.TEARDOWN;
            case TEARDOWN -> journalEmpty ? CampPhase.RELEASE : CampPhase.TEARDOWN;
            case RELEASE -> CampPhase.NONE;
        };
    }

    // ================================================================ defence

    /**
     * The one defensive answer. Escape always outranks the strike below the declared health
     * fraction, and there is no proactive branch: without a recorded direct aggressor the answer is
     * always {@code NONE}.
     */
    public static DefensiveResponse defensiveResponse(
        final boolean adult,
        final boolean aggressorRemembered,
        final boolean aggressorReachable,
        final boolean dependentNearby,
        final float healthFraction
    ) {
        if (!aggressorRemembered) {
            return DefensiveResponse.NONE;
        }
        if (!adult || healthFraction < ESCAPE_HEALTH_FRACTION) {
            return DefensiveResponse.FLEE;
        }
        return dependentNearby && aggressorReachable
            ? DefensiveResponse.DEFEND
            : DefensiveResponse.FLEE;
    }

    /**
     * Only a live, unprotected, same-dimension candidate that actually hit this traveler may be
     * targeted. There is no proactive prey list at all.
     */
    public static boolean canTarget(
        final boolean adult,
        final TargetClass classification,
        final boolean alive,
        final boolean sameDimension,
        final boolean creativeOrSpectator,
        final boolean invulnerable
    ) {
        return adult
            && classification == TargetClass.DIRECT_AGGRESSOR
            && alive
            && sameDimension
            && !creativeOrSpectator
            && !invulnerable;
    }

    /** Chase never extends past the declared disengage radius. */
    public static boolean shouldDisengage(final double distanceToAggressor) {
        return distanceToAggressor > DISENGAGE_RADIUS;
    }

    /** Alerts are depth one and capped; they never rebroadcast through a neighbouring caravan. */
    public static int alarmRecipients(final int nearbyMembers) {
        return Math.clamp(nearbyMembers, 0, MAX_ALARM_MEMBERS);
    }

    // ================================================================ navigation

    public static boolean shouldBackOff(final int routeFailures) {
        return routeFailures >= MAX_ROUTE_FAILURES;
    }

    public static int nextRouteFailure(final int current, final RouteFailure failure) {
        return failure == null || failure == RouteFailure.NONE
            ? 0
            : Math.clamp(current + 1, 0, MAX_ROUTE_FAILURES);
    }

    public static int backoffTicks(final int routeFailures) {
        return shouldBackOff(routeFailures) ? ROUTE_BACKOFF_TICKS : ROUTE_RETRY_TICKS;
    }

    /** Detailed local legs only; a long journey is a sequence of revalidated short legs. */
    public static boolean withinLocalRoute(final double horizontalDistance) {
        return horizontalDistance <= MAX_LOCAL_ROUTE_BLOCKS;
    }

    // ================================================================ claims

    public static boolean canGrantClaim(
        final int existingClaims,
        final boolean claimantAlreadyHolds,
        final boolean worksiteAlreadyClaimed
    ) {
        return existingClaims < MAX_CLAIM_RECORDS && !claimantAlreadyHolds && !worksiteAlreadyClaimed;
    }

    public static int leaseTicks() {
        return CLAIM_LEASE_TICKS;
    }

    // ================================================================ world edits

    /** The single block-mutation guard, shared by every survey and every pre-commit revalidation. */
    public static boolean canEditBlock(
        final boolean loaded,
        final boolean insideWorldBorder,
        final boolean mobGriefing,
        final boolean tagged,
        final boolean fluid,
        final boolean blockEntity,
        final float destroySpeed
    ) {
        return loaded && insideWorldBorder && mobGriefing && tagged && !fluid && !blockEntity
            && destroySpeed >= 0.0F;
    }

    /** Camp placements only ever claim genuinely empty space, so removal is exactly reversible. */
    public static boolean canPlaceCampBlock(
        final boolean loaded,
        final boolean insideWorldBorder,
        final boolean mobGriefing,
        final boolean priorStateAir,
        final boolean blockEntity,
        final boolean fluid
    ) {
        return loaded && insideWorldBorder && mobGriefing && priorStateAir && !blockEntity && !fluid;
    }

    // ================================================================ merchant

    public static int clampMerchantLevel(final int level) {
        return Math.clamp(level, MIN_MERCHANT_LEVEL, MAX_MERCHANT_LEVEL);
    }

    public static int levelForXp(final int xp) {
        int level = MIN_MERCHANT_LEVEL;
        for (int index = 1; index < LEVEL_XP_THRESHOLDS.length; index++) {
            if (xp >= LEVEL_XP_THRESHOLDS[index]) {
                level = index + 1;
            }
        }
        return clampMerchantLevel(level);
    }

    public static boolean canRestock(
        final int restocksToday,
        final int spacingRemainingTicks,
        final boolean anyOfferNeedsRestock,
        final boolean safeToTrade
    ) {
        return anyOfferNeedsRestock
            && safeToTrade
            && restocksToday < MAX_RESTOCKS_PER_DAY
            && isDue(spacingRemainingTicks);
    }


}
