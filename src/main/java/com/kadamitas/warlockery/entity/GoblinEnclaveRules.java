package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The complete loader-neutral F10 Goblin decision surface. Every method here is a pure function of
 * already-observed facts: no {@code Level}, entity, path, registry, block state, container, random
 * source, or loader API is ever accepted, and nothing here mutates. The runtime observes, this class
 * decides, and the runtime executes exactly one decision.
 *
 * <p>Durations are remaining loaded-tick counts, never absolute world deadlines, so an unloaded
 * enclave pauses meaning rather than silently expiring it. Sentinels are zero-is-due, and the only
 * far-future cadence value is {@link #FAR_FUTURE_TICKS}, deliberately bounded well below
 * {@code Long.MAX_VALUE} so arithmetic on it can never overflow.</p>
 */
public final class GoblinEnclaveRules {
    // ---------------------------------------------------------------- identity

    /** F10 owns exactly one kind. Every entry point re-checks this rather than trusting a caller. */
    public static final CreatureKind KIND = CreatureKind.GOBLIN;

    /** Bounded far-future cadence sentinel; never {@code Long.MAX_VALUE}. */
    public static final long FAR_FUTURE_TICKS = 20_000L;

    // ---------------------------------------------------------------- cadence

    public static final int DECISION_INTERVAL_TICKS = 20;
    public static final int PERCEPTION_INTERVAL_TICKS = 40;
    public static final int MEMBER_INTERVAL_TICKS = 100;
    public static final int WORK_SCAN_INTERVAL_TICKS = 100;
    public static final int SITE_SCAN_INTERVAL_TICKS = 200;
    public static final int NAVIGATION_INTERVAL_TICKS = 20;
    public static final int FEEDBACK_INTERVAL_TICKS = 40;
    public static final int MAX_SCHEDULE_OFFSET_TICKS = 39;

    // ---------------------------------------------------------------- budgets

    public static final int PERCEPTION_RADIUS = 24;
    public static final int MEMBER_RADIUS = 24;
    public static final int LOOSE_ITEM_RADIUS = 8;
    public static final int ALARM_RECRUIT_RADIUS = 24;
    public static final int MAX_ENTITY_VISITS = 32;
    public static final int MAX_ENTITY_RETAINED = 16;
    public static final int MAX_MEMBER_VISITS = 16;
    public static final int MAX_LOOSE_VISITS = 16;
    public static final int MAX_LOOSE_RETAINED = 8;
    public static final int MAX_WORK_BLOCK_READS = 128;
    public static final int MAX_SITE_BLOCK_READS = 256;
    public static final int MAX_MINING_BLOCK_READS = 128;
    public static final int MAX_CHILD_BLOCK_READS = 64;
    public static final int MAX_HUT_BLOCK_READS = 256;

    // ---------------------------------------------------------------- enclave caps

    public static final int MAX_RECORDS_PER_DIMENSION = 256;
    public static final int REGION_SIZE = 128;
    public static final int MAX_MEMBERS = 8;
    /**
     * A membership entry is a lease, not a permanent roll. A loaded member refreshes it on its own
     * {@link #MEMBER_INTERVAL_TICKS} reconciliation, so this is twelve heartbeats of slack; a member
     * that died, was removed, or unloaded simply stops refreshing and ages out.
     */
    public static final int MEMBER_EXPIRY_TICKS = 1_200;
    public static final int MAX_HUTS = 3;
    public static final int MAX_TUNNELS = 1;
    public static final int MAX_OWNED_EDITS = 128;
    public static final int MAX_CLAIMS = 8;
    public static final int MAX_THREATS = 4;
    public static final int MAX_RELATIONS = 8;
    public static final int MAX_DEFENDERS = 4;
    public static final int CLAIM_LEASE_TICKS = 200;
    public static final int PROVISIONAL_EXPIRY_TICKS = 24_000;

    // ---------------------------------------------------------------- construction

    public static final int HUT_DIRT_COST = 18;
    public static final int HUT_LOG_COST = 3;
    public static final int HUT_PLANKS_PER_LOG = 4;
    public static final int HUT_MIN_PLANKS = 12;
    public static final int HUT_MAX_EDITS = 32;
    public static final int TUNNEL_MIN_EDITS = 4;
    public static final int TUNNEL_MAX_EDITS = 10;

    // ---------------------------------------------------------------- family

    public static final int MAX_FOOD_POINTS = 24;
    public static final int BREEDING_FOOD_COST = 12;
    public static final int CHILD_GIFT_COOLDOWN_TICKS = 12_000;
    public static final int MAX_DANCE_PARTICIPANTS = 4;
    public static final double DANCE_RADIUS = 2.25D;
    public static final double CHILD_GIFT_RADIUS = 8.0D;

    // ---------------------------------------------------------------- merchant

    public static final int MIN_MERCHANT_LEVEL = 1;
    public static final int MAX_MERCHANT_LEVEL = 5;
    public static final int MAX_RESTOCKS_PER_DAY = 2;
    public static final int RESTOCK_SPACING_TICKS = 2_400;
    private static final int[] LEVEL_XP_THRESHOLDS = {0, 10, 70, 150, 250};

    // ---------------------------------------------------------------- combat

    public static final float RETREAT_HEALTH_FRACTION = 0.30F;
    public static final float RECOVER_HEALTH_FRACTION = 0.50F;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;
    public static final int ALARM_DEPTH = 1;
    public static final int HURT_ATTRIBUTION_TICKS = 40;

    // ---------------------------------------------------------------- spawning

    public static final int MAX_NATURAL_SPAWN_LIGHT = 7;
    public static final int MIN_HUMAN_VILLAGE_DISTANCE = 48;
    public static final int LOCAL_SPAWN_CAP = 8;
    public static final int LOCAL_SPAWN_CAP_RADIUS = 32;

    // ---------------------------------------------------------------- state schema

    public static final int STATE_SCHEMA_VERSION = 1;
    public static final int DATA_SCHEMA_VERSION = 1;
    public static final int MAX_STATE_BYTES = 1_024;

    private GoblinEnclaveRules() {
    }

    // ================================================================ enums

    /** The declared day division. Actual local sunlight still overrides safety at execution time. */
    public enum Period {
        DAWN, DAY, DUSK, NIGHT
    }

    /** The finite job vocabulary. No other intent may ever be claimed. */
    public enum Intent {
        SEEK_SHELTER, PATROL, GATHER_LOOSE, GATHER_LOG, MINE, DEPOSIT, BUILD_HUT, DIG_TUNNEL,
        TRADE_HOLD, FAMILY, CHILD_FLOWER, CHILD_DANCE, CHILD_GIFT, ALARM_WARD, ALARM_HARRY,
        ALARM_PRESS, ALARM_RESERVE, ASSAULT, IDLE;

        public boolean isChildIntent() {
            return this == CHILD_FLOWER || this == CHILD_DANCE || this == CHILD_GIFT;
        }

        public boolean isAlarmIntent() {
            return this == ALARM_WARD || this == ALARM_HARRY || this == ALARM_PRESS
                || this == ALARM_RESERVE;
        }

        public boolean editsWorld() {
            return this == BUILD_HUT || this == DIG_TUNNEL || this == MINE || this == GATHER_LOG
                || this == CHILD_FLOWER;
        }
    }

    /** Temporary defensive task leases. Never a permanent caste. */
    public enum CombatRole {
        NONE, WARDER, HARRIER, PRESS, RESERVE;

        public Intent intent() {
            return switch (this) {
                case WARDER -> Intent.ALARM_WARD;
                case HARRIER -> Intent.ALARM_HARRY;
                case PRESS -> Intent.ALARM_PRESS;
                case RESERVE -> Intent.ALARM_RESERVE;
                case NONE -> Intent.IDLE;
            };
        }
    }

    /** The classified observation categories a candidate target can fall into. */
    public enum TargetClass {
        HUMAN_VILLAGER, DIRECT_ATTACKER, CHILD_ATTACKER, PATRON_ATTACKER, ALARM_COPY,
        PLAYER, PATRON, SAME_PATRON_ALLY, OWN_CHILD, ENCLAVE_MEMBER, OUTSIDER_GOBLIN,
        HOBGOBLIN, PATRON_BOSS, ANIMAL, GOLEM, OTHER_FAMILY
    }

    /** Explicit relation inputs. Nothing else may move a player relation score. */
    public enum RelationEvent {
        TRADE_COMPLETED, CONTRACT_ACCEPTED, GIFT_RECEIVED, DIRECT_ATTACK, MEMBER_KILLED
    }

    /** Why a Goblin refuses ordinary hostile despawn. Absent means it may despawn. */
    public enum PersistenceReason {
        ANCHORED_RESIDENT, CONTRACTED, ASSAULT_MEMBER
    }

    /** Classified route-failure causes; all three count toward the same bounded backoff. */
    public enum RouteFailure {
        NONE, NO_PATH, UNREACHABLE, STUCK
    }

    // ================================================================ identity and cadence

    public static boolean isExactGoblin(final CreatureKind kind) {
        return kind == KIND;
    }

    /**
     * Stable non-negative identity offset in {@code [0, bound)}. Uses both UUID halves so two
     * Goblins created in the same tick still stagger, and never returns a negative modulus.
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
     * clock has run past one day still resolves.
     */
    public static Period period(final long dayTime, final int identityOffsetTicks) {
        final long offset = Math.clamp(identityOffsetTicks, 0, MAX_SCHEDULE_OFFSET_TICKS);
        final long normalized = Math.floorMod(dayTime + offset, 24_000L);
        if (normalized < 11_000L) {
            return Period.DAY;
        }
        if (normalized < 13_000L) {
            return Period.DUSK;
        }
        if (normalized < 22_000L) {
            return Period.NIGHT;
        }
        return Period.DAWN;
    }

    // ================================================================ spawning and persistence

    /**
     * Natural spawn is night, low light, away from a human village, and locally capped. Every input
     * is an already-observed loaded fact; this method never reaches a level.
     */
    public static boolean canSpawnNaturally(
        final boolean night,
        final int lightLevel,
        final int distanceToHumanVillage,
        final int localGoblinCount
    ) {
        return night
            && lightLevel <= MAX_NATURAL_SPAWN_LIGHT
            && distanceToHumanVillage >= MIN_HUMAN_VILLAGE_DISTANCE
            && localGoblinCount < LOCAL_SPAWN_CAP;
    }

    /**
     * Exactly one persistence reason, in a fixed precedence, or empty when ordinary hostile despawn
     * applies. An anchored resident only counts while its anchor is still valid in this dimension.
     */
    public static Optional<PersistenceReason> persistenceReason(
        final boolean validAnchoredResident,
        final boolean contracted,
        final boolean assaultMember
    ) {
        if (assaultMember) {
            return Optional.of(PersistenceReason.ASSAULT_MEMBER);
        }
        if (contracted) {
            return Optional.of(PersistenceReason.CONTRACTED);
        }
        return validAnchoredResident
            ? Optional.of(PersistenceReason.ANCHORED_RESIDENT)
            : Optional.empty();
    }

    public static boolean mayDespawn(
        final boolean validAnchoredResident,
        final boolean contracted,
        final boolean assaultMember
    ) {
        return persistenceReason(validAnchoredResident, contracted, assaultMember).isEmpty();
    }

    /** Region-and-kind enclave key; identical arithmetic to the 1.4 settlement key it migrates. */
    public static long enclaveKey(final int blockX, final int blockZ, final CreatureKind kind) {
        final long regionX = Math.floorDiv(blockX, REGION_SIZE);
        final long regionZ = Math.floorDiv(blockZ, REGION_SIZE);
        final long region = (regionX & 0x7FFF_FFFFL) << 32 | regionZ & 0xFFFF_FFFFL;
        return region * 31L + kind.ordinal();
    }

    // ================================================================ priority and intent

    /**
     * The one semantic priority contract. Exactly one intent is produced per decision, and the
     * ordering is fixed: hazard shelter, assault, alarm role, trade hold, then schedule work.
     * A child never receives an adult or combat intent.
     */
    public static Intent selectIntent(
        final boolean baby,
        final boolean hazardExposed,
        final boolean assaultMarked,
        final CombatRole role,
        final boolean trading,
        final Period period,
        final boolean sheltered,
        final GoblinProfession profession,
        final WorkAvailability work
    ) {
        final WorkAvailability available = work == null ? WorkAvailability.none() : work;
        if (baby) {
            return selectChildIntent(hazardExposed, assaultMarked, period, available);
        }
        if (hazardExposed) {
            return Intent.SEEK_SHELTER;
        }
        if (assaultMarked) {
            return Intent.ASSAULT;
        }
        if (role != null && role != CombatRole.NONE) {
            return role.intent();
        }
        if (trading) {
            return Intent.TRADE_HOLD;
        }
        return switch (period) {
            case DAWN -> sheltered ? Intent.IDLE : Intent.SEEK_SHELTER;
            case DAY -> sheltered ? Intent.IDLE : Intent.SEEK_SHELTER;
            case DUSK -> Intent.PATROL;
            case NIGHT -> nightWork(profession, available);
        };
    }

    private static Intent selectChildIntent(
        final boolean hazardExposed,
        final boolean assaultMarked,
        final Period period,
        final WorkAvailability work
    ) {
        if (hazardExposed || assaultMarked || period == Period.DAWN || period == Period.DAY) {
            return Intent.SEEK_SHELTER;
        }
        if (work.giftReady()) {
            return Intent.CHILD_GIFT;
        }
        if (work.danceReady()) {
            return Intent.CHILD_DANCE;
        }
        return work.flowerAvailable() ? Intent.CHILD_FLOWER : Intent.IDLE;
    }

    /**
     * Night work by profession preference with a fixed deterministic fallback chain. Preference is
     * a preference only: a Miner with no eligible ore still gathers, deposits, or patrols.
     */
    private static Intent nightWork(final GoblinProfession profession, final WorkAvailability work) {
        final List<Intent> preferred = new ArrayList<>(preferredJobs(profession));
        preferred.addAll(List.of(
            Intent.DEPOSIT, Intent.GATHER_LOOSE, Intent.BUILD_HUT, Intent.MINE,
            Intent.GATHER_LOG, Intent.DIG_TUNNEL, Intent.FAMILY, Intent.PATROL
        ));
        return preferred.stream().filter(work::supports).findFirst().orElse(Intent.IDLE);
    }

    /** The declared per-profession preferred job order. Never a privilege, only an ordering. */
    public static List<Intent> preferredJobs(final GoblinProfession profession) {
        return switch (profession == null ? GoblinProfession.FALLBACK : profession) {
            case MINER -> List.of(Intent.MINE, Intent.DEPOSIT, Intent.GATHER_LOOSE);
            case SMITH -> List.of(Intent.BUILD_HUT, Intent.GATHER_LOG, Intent.DEPOSIT);
            case SHAMAN -> List.of(Intent.FAMILY, Intent.PATROL, Intent.GATHER_LOOSE);
            case PROSPECTOR -> List.of(Intent.GATHER_LOOSE, Intent.DIG_TUNNEL, Intent.PATROL);
        };
    }

    /** Bounded observation of which jobs are currently executable. All fields default to false. */
    public record WorkAvailability(
        boolean mineable,
        boolean depositable,
        boolean looseItem,
        boolean naturalLog,
        boolean hutSite,
        boolean tunnelSite,
        boolean familyReady,
        boolean flowerAvailable,
        boolean danceReady,
        boolean giftReady
    ) {
        public static WorkAvailability none() {
            return new WorkAvailability(false, false, false, false, false, false, false,
                false, false, false);
        }

        public boolean supports(final Intent intent) {
            return switch (intent) {
                case MINE -> mineable;
                case DEPOSIT -> depositable;
                case GATHER_LOOSE -> looseItem;
                case GATHER_LOG -> naturalLog;
                case BUILD_HUT -> hutSite;
                case DIG_TUNNEL -> tunnelSite;
                case FAMILY -> familyReady;
                case CHILD_FLOWER -> flowerAvailable;
                case CHILD_DANCE -> danceReady;
                case CHILD_GIFT -> giftReady;
                case PATROL, IDLE -> true;
                case SEEK_SHELTER, TRADE_HOLD, ASSAULT, ALARM_WARD, ALARM_HARRY, ALARM_PRESS,
                    ALARM_RESERVE -> false;
            };
        }
    }

    /**
     * A committed intent is only interrupted by a strictly more urgent class. This is the single
     * commitment/interruption contract; a runtime that does not consult it is a defect.
     */
    public static boolean interrupts(final Intent current, final Intent candidate) {
        return urgency(candidate) > urgency(current);
    }

    private static int urgency(final Intent intent) {
        return switch (intent) {
            case SEEK_SHELTER -> 5;
            case ASSAULT -> 4;
            case ALARM_WARD, ALARM_HARRY, ALARM_PRESS, ALARM_RESERVE -> 3;
            case TRADE_HOLD -> 2;
            case MINE, DEPOSIT, GATHER_LOOSE, GATHER_LOG, BUILD_HUT, DIG_TUNNEL, FAMILY,
                CHILD_FLOWER, CHILD_DANCE, CHILD_GIFT, PATROL -> 1;
            case IDLE -> 0;
        };
    }

    // ================================================================ hostility

    /**
     * The complete target truth table. Every exclusion is checked before any inclusion, so a patron
     * who happens to be a human villager, or an allied Goblin copied from an alarm, is still safe.
     */
    public static boolean canTarget(
        final boolean selfIsAdult,
        final TargetClass candidate,
        final boolean alive,
        final boolean loaded,
        final boolean sameDimension,
        final boolean creativeOrSpectator,
        final boolean invulnerable
    ) {
        if (!selfIsAdult || !alive || !loaded || !sameDimension || creativeOrSpectator
            || invulnerable || candidate == null) {
            return false;
        }
        return switch (candidate) {
            case HUMAN_VILLAGER, DIRECT_ATTACKER, CHILD_ATTACKER, PATRON_ATTACKER, ALARM_COPY -> true;
            case PLAYER, PATRON, SAME_PATRON_ALLY, OWN_CHILD, ENCLAVE_MEMBER, OUTSIDER_GOBLIN,
                HOBGOBLIN, PATRON_BOSS, ANIMAL, GOLEM, OTHER_FAMILY -> false;
        };
    }

    /** A retained hurt-by attribution is only fresh for {@link #HURT_ATTRIBUTION_TICKS}. */
    public static boolean isFreshAttribution(final int ticksSinceHurt) {
        return ticksSinceHurt >= 0 && ticksSinceHurt <= HURT_ATTRIBUTION_TICKS;
    }

    // ================================================================ alarm and roles

    /**
     * Fills at most {@link #MAX_DEFENDERS} unique roles from the responder list. Profession is the
     * preference; ties break deterministically on urgency, then distance, then UUID so two servers
     * given identical facts assign identical roles. Extra responders receive {@link CombatRole#NONE}
     * and stay on survival work rather than joining an all-entity swarm.
     */
    public static List<RoleAssignment> assignRoles(final List<Responder> responders) {
        if (responders == null || responders.isEmpty()) {
            return List.of();
        }
        final List<Responder> ordered = new ArrayList<>(responders);
        ordered.sort(Comparator
            .comparingInt(Responder::urgency).reversed()
            .thenComparingDouble(Responder::distanceSquared)
            .thenComparing(Responder::id));
        final List<CombatRole> open = new ArrayList<>(
            List.of(CombatRole.PRESS, CombatRole.WARDER, CombatRole.HARRIER, CombatRole.RESERVE)
        );
        final List<RoleAssignment> assignments = new ArrayList<>();
        for (final Responder responder : ordered) {
            if (open.isEmpty()) {
                assignments.add(new RoleAssignment(responder.id(), CombatRole.NONE));
                continue;
            }
            final CombatRole preferred = preferredRole(responder.profession());
            final CombatRole granted = open.remove(open.contains(preferred)
                ? open.indexOf(preferred)
                : 0);
            assignments.add(new RoleAssignment(responder.id(), granted));
        }
        return List.copyOf(assignments);
    }

    public static CombatRole preferredRole(final GoblinProfession profession) {
        return switch (profession == null ? GoblinProfession.FALLBACK : profession) {
            case MINER -> CombatRole.PRESS;
            case SMITH -> CombatRole.WARDER;
            case PROSPECTOR -> CombatRole.HARRIER;
            case SHAMAN -> CombatRole.RESERVE;
        };
    }

    public record Responder(UUID id, GoblinProfession profession, int urgency, double distanceSquared) {
        public Responder {
            id = id == null ? new UUID(0L, 0L) : id;
            profession = profession == null ? GoblinProfession.FALLBACK : profession;
            urgency = Math.clamp(urgency, 0, 3);
            distanceSquared = Math.max(0.0D, distanceSquared);
        }
    }

    public record RoleAssignment(UUID id, CombatRole role) {
    }

    /** An alarm copy is depth one only; a copied threat may never be rebroadcast. */
    public static boolean canRelayAlarm(final int depth) {
        return depth < ALARM_DEPTH;
    }

    public static int recruitCap(final int loadedSameEnclaveAdults) {
        return Math.clamp(loadedSameEnclaveAdults, 0, MAX_DEFENDERS);
    }

    /**
     * Retreat hysteresis. A wounded defender releases its aggressive role below
     * {@link #RETREAT_HEALTH_FRACTION} unless an attacker is in immediate reach, and only leaves
     * retreat at or above {@link #RECOVER_HEALTH_FRACTION} or once the alarm has ended.
     */
    public static boolean shouldRetreat(
        final boolean currentlyRetreating,
        final float healthFraction,
        final boolean attackerInReach,
        final boolean alarmActive
    ) {
        if (currentlyRetreating) {
            return alarmActive && healthFraction < RECOVER_HEALTH_FRACTION;
        }
        return healthFraction <= RETREAT_HEALTH_FRACTION && !attackerInReach;
    }

    /** Relief: a wounded defender is replaced by a reserve rather than leaving a role empty. */
    public static boolean needsRelief(final float healthFraction, final CombatRole role) {
        return role != CombatRole.NONE
            && role != CombatRole.RESERVE
            && healthFraction <= RETREAT_HEALTH_FRACTION;
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
        return shouldBackOff(routeFailures) ? ROUTE_BACKOFF_TICKS : 0;
    }

    // ================================================================ claims

    public static boolean canGrantClaim(
        final int existingClaims,
        final boolean claimantAlreadyHolds,
        final boolean worksiteAlreadyClaimed
    ) {
        return existingClaims < MAX_CLAIMS && !claimantAlreadyHolds && !worksiteAlreadyClaimed;
    }

    public static boolean claimExpired(final int remainingLeaseTicks) {
        return isDue(remainingLeaseTicks);
    }

    public static int leaseTicks() {
        return CLAIM_LEASE_TICKS;
    }

    // ================================================================ construction transactions

    public static boolean canAffordHut(final int carriedDirt, final int carriedLogs, final int carriedPlanks) {
        final int plankEquivalent = Math.max(0, carriedPlanks)
            + Math.max(0, carriedLogs) * HUT_PLANKS_PER_LOG;
        return carriedDirt >= HUT_DIRT_COST
            && carriedLogs >= HUT_LOG_COST
            && plankEquivalent >= HUT_MIN_PLANKS;
    }

    public static boolean canReserveHut(final int huts, final int ownedEdits) {
        return huts < MAX_HUTS && fitsEditBudget(ownedEdits, HUT_MAX_EDITS);
    }

    public static boolean canReserveTunnel(final int tunnels, final int ownedEdits, final int edits) {
        return tunnels < MAX_TUNNELS
            && edits >= TUNNEL_MIN_EDITS
            && edits <= TUNNEL_MAX_EDITS
            && fitsEditBudget(ownedEdits, edits);
    }

    public static boolean canRecordEdit(final int ownedEdits, final int edits) {
        return fitsEditBudget(ownedEdits, edits);
    }

    private static boolean fitsEditBudget(final int current, final int requested) {
        return current >= 0 && requested >= 0 && current <= MAX_OWNED_EDITS - requested;
    }

    /** A block is editable only when every world guard passes at the same instant. */
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

    // ================================================================ family

    public static int clampFoodPoints(final int points) {
        return Math.clamp(points, 0, MAX_FOOD_POINTS);
    }

    /**
     * Explicit conception rules. Every parent condition, the bed, the population cap, and the single
     * family claim must hold together; no inherited human Villager willingness participates.
     */
    public static boolean canConceive(
        final boolean bothExactGoblinAdults,
        final boolean sameEnclave,
        final boolean bothIdle,
        final int enclavePopulation,
        final boolean unclaimedBedInCommittedHut,
        final int firstParentFood,
        final int secondParentFood,
        final boolean familyClaimAvailable
    ) {
        return bothExactGoblinAdults
            && sameEnclave
            && bothIdle
            && enclavePopulation + 1 <= MAX_MEMBERS
            && unclaimedBedInCommittedHut
            && clampFoodPoints(firstParentFood) >= BREEDING_FOOD_COST
            && clampFoodPoints(secondParentFood) >= BREEDING_FOOD_COST
            && familyClaimAvailable;
    }

    /**
     * Deterministic child profession: the parent whose UUID sorts first supplies it, so a save and
     * reload of the same pair can never produce a different child.
     */
    public static GoblinProfession childProfession(
        final UUID firstParent,
        final GoblinProfession firstProfession,
        final UUID secondParent,
        final GoblinProfession secondProfession
    ) {
        final GoblinProfession first = firstProfession == null ? GoblinProfession.FALLBACK : firstProfession;
        final GoblinProfession second = secondProfession == null ? GoblinProfession.FALLBACK : secondProfession;
        if (firstParent == null || secondParent == null) {
            return first;
        }
        return firstParent.compareTo(secondParent) <= 0 ? first : second;
    }

    public static boolean canDance(final int sameEnclaveChildren) {
        return sameEnclaveChildren >= 2 && sameEnclaveChildren <= MAX_DANCE_PARTICIPANTS;
    }

    public static boolean giftReady(final boolean holdingFlower, final int remainingCooldownTicks) {
        return holdingFlower && isDue(remainingCooldownTicks);
    }

    // ================================================================ relations

    public static int relationDelta(final RelationEvent event) {
        return switch (event == null ? RelationEvent.TRADE_COMPLETED : event) {
            case TRADE_COMPLETED -> 5;
            case CONTRACT_ACCEPTED -> 10;
            case GIFT_RECEIVED -> 3;
            case DIRECT_ATTACK -> -20;
            case MEMBER_KILLED -> -40;
        };
    }

    public static int applyRelation(final int current, final RelationEvent event) {
        return clampRelation(current + relationDelta(event));
    }

    public static int clampRelation(final int score) {
        return Math.clamp(score, -100, 100);
    }

    /**
     * Deterministic eviction when the relation cap is reached: the oldest interaction leaves first,
     * then the least extreme score, then the lowest UUID. Never a random or insertion-order drop.
     */
    public static Optional<UUID> relationToEvict(final List<RelationFact> facts) {
        if (facts == null || facts.size() < MAX_RELATIONS) {
            return Optional.empty();
        }
        return facts.stream()
            .min(Comparator
                .comparingInt(RelationFact::lastInteractionAgeTicks).reversed()
                .thenComparingInt(fact -> Math.abs(fact.score()))
                .thenComparing(RelationFact::id))
            .map(RelationFact::id);
    }

    public record RelationFact(UUID id, int score, int lastInteractionAgeTicks, int remainingTicks) {
        public RelationFact {
            id = id == null ? new UUID(0L, 0L) : id;
            score = clampRelation(score);
            lastInteractionAgeTicks = Math.max(0, lastInteractionAgeTicks);
            remainingTicks = Math.max(0, remainingTicks);
        }

        public boolean expired() {
            return isDue(remainingTicks);
        }
    }

    // ================================================================ merchant progression

    public static int clampMerchantLevel(final int level) {
        return Math.clamp(level, MIN_MERCHANT_LEVEL, MAX_MERCHANT_LEVEL);
    }

    public static int levelForXp(final int xp) {
        final int safe = Math.max(0, xp);
        int level = MIN_MERCHANT_LEVEL;
        for (int index = 1; index < LEVEL_XP_THRESHOLDS.length; index++) {
            if (safe >= LEVEL_XP_THRESHOLDS[index]) {
                level = index + 1;
            }
        }
        return clampMerchantLevel(level);
    }

    /**
     * At most two restocks per game day, spaced by {@link #RESTOCK_SPACING_TICKS} loaded ticks. The
     * spacing is a remaining counter, so an unloaded merchant does not accumulate free restocks.
     */
    public static boolean canRestock(
        final int restocksToday,
        final int remainingSpacingTicks,
        final boolean offersNeedRestock,
        final boolean safeToTrade
    ) {
        return offersNeedRestock
            && safeToTrade
            && restocksToday < MAX_RESTOCKS_PER_DAY
            && (restocksToday == 0 || isDue(remainingSpacingTicks));
    }

    /** Stable per-Goblin offer seed. Identical inputs must always select identical offers. */
    public static long offerSeed(final UUID id, final GoblinProfession profession, final int level) {
        final UUID safe = id == null ? new UUID(0L, 0L) : id;
        final GoblinProfession role = profession == null ? GoblinProfession.FALLBACK : profession;
        return safe.getMostSignificantBits()
            ^ safe.getLeastSignificantBits()
            ^ ((long) role.ordinal() << 32)
            ^ KIND.ordinal()
            ^ clampMerchantLevel(level);
    }

    // ================================================================ assault

    /** An assault marker suspends every enclave-initiated activity listed in the approved design. */
    public static boolean assaultSuspends(final Intent intent) {
        return switch (intent) {
            case FAMILY, GATHER_LOOSE, GATHER_LOG, BUILD_HUT, DIG_TUNNEL, MINE, DEPOSIT, TRADE_HOLD,
                CHILD_FLOWER, CHILD_DANCE, CHILD_GIFT -> true;
            case SEEK_SHELTER, PATROL, IDLE, ASSAULT, ALARM_WARD, ALARM_HARRY, ALARM_PRESS,
                ALARM_RESERVE -> false;
        };
    }
}
