package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.CampPhase;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.ContractEnd;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.ContractKind;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.Mode;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.RelationFact;
import com.kadamitas.warlockery.entity.HobgoblinJourneyRules.RouteFailure;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Versioned, fixed-cardinality F11 Hobgoblin semantics. Exactly one mode, one contractor, one
 * caravan reference, one camp reference, one job claim, one direct aggressor, and at most eight
 * player relationship facts may exist at a time. Nothing here stores a path, {@code Level},
 * {@code Entity}, {@code Container}, block snapshot, navigation object, capability, chunk, Java
 * clock, or unbounded collection.
 *
 * <p>Every duration is a remaining loaded-tick count rather than an absolute world deadline, so an
 * unloaded traveler pauses its meaning instead of expiring it and no missed work is replayed on
 * load. Zero always reads as due.</p>
 *
 * <p><strong>Reconcile contract.</strong> Two constructor shapes are possible here and only one is
 * legitimate, so every reconciling constructor below is classified explicitly.</p>
 *
 * <ul>
 *   <li><strong>Timer shape</strong> - {@code if (timer <= 0) zero the dependents} - is the defect,
 *       and this file contains none of it. It decides that something <em>ended</em>, which is a tick
 *       branch's job, and it steals exactly the branch that would have armed the cooldown, released
 *       the real claim, cleared the anchor, or emitted the completion feedback. A lapsed job lease,
 *       an expired agreement, and a lapsed direct aggressor are therefore <em>reported</em> here
 *       ({@link Job#leaseExpired()}, {@link Contract#expired()}, {@link Combat#aggressorLapsed()})
 *       and ended by {@code HobgoblinJourneyRuntime.advanceLoadedTimers}, which is the single exit.</li>
 *   <li><strong>Identity shape</strong> - {@code if (identity absent) zero the dependents} - is a
 *       coupled invariant the type should enforce, not a stolen branch: it only asserts that two
 *       fields cannot disagree. {@link Contract}, {@link Caravan} (twice), {@link Camp}, {@link Job},
 *       and {@link Combat} each carry exactly one such assertion and are listed at their site.</li>
 * </ul>
 */
public record HobgoblinJourneyState(
    int schemaVersion,
    GoblinProfession profession,
    Merchant merchant,
    Mode mode,
    Contract contract,
    Caravan caravan,
    Camp camp,
    Job job,
    Combat combat,
    List<Relation> relations,
    Cadence cadence,
    int childGiftCooldownTicks,
    int birthCooldownTicks
) {
    public static final int SCHEMA_VERSION = HobgoblinJourneyRules.STATE_SCHEMA_VERSION;

    public HobgoblinJourneyState {
        profession = profession == null ? GoblinProfession.FALLBACK : profession;
        merchant = Objects.requireNonNull(merchant, "merchant");
        mode = mode == null ? Mode.IDLE : mode;
        contract = Objects.requireNonNull(contract, "contract");
        caravan = Objects.requireNonNull(caravan, "caravan");
        camp = Objects.requireNonNull(camp, "camp");
        job = Objects.requireNonNull(job, "job");
        combat = Objects.requireNonNull(combat, "combat");
        relations = normalizeRelations(relations);
        cadence = Objects.requireNonNull(cadence, "cadence");
        childGiftCooldownTicks = HobgoblinJourneyRules.clampRemaining(
            childGiftCooldownTicks, HobgoblinJourneyRules.CHILD_GIFT_COOLDOWN_TICKS
        );
        birthCooldownTicks = HobgoblinJourneyRules.clampRemaining(
            birthCooldownTicks, HobgoblinJourneyRules.BIRTH_COOLDOWN_TICKS
        );
        schemaVersion = SCHEMA_VERSION;
    }

    // ---------------------------------------------------------------- components

    /** Merchant progression owned by the traveler body, never by a human Villager data component. */
    public record Merchant(int level, int xp, int restocksToday, int restockSpacingTicks) {
        public Merchant {
            level = HobgoblinJourneyRules.clampMerchantLevel(level);
            xp = Math.max(0, xp);
            restocksToday = Math.clamp(restocksToday, 0, HobgoblinJourneyRules.MAX_RESTOCKS_PER_DAY);
            restockSpacingTicks = HobgoblinJourneyRules.clampRemaining(
                restockSpacingTicks, HobgoblinJourneyRules.RESTOCK_SPACING_TICKS
            );
        }

        public static Merchant initial() {
            return new Merchant(HobgoblinJourneyRules.MIN_MERCHANT_LEVEL, 0, 0, 0);
        }

        public Merchant withXp(final int updated) {
            final int safe = Math.max(0, updated);
            return new Merchant(
                HobgoblinJourneyRules.levelForXp(safe), safe, restocksToday, restockSpacingTicks
            );
        }

        public Merchant afterRestock() {
            return new Merchant(
                level, xp, restocksToday + 1, HobgoblinJourneyRules.RESTOCK_SPACING_TICKS
            );
        }

        public Merchant onNewDay() {
            return new Merchant(level, xp, 0, 0);
        }
    }

    /**
     * One voluntary, expiring work agreement. The constructor never converts an exhausted timer into
     * an ended contract: {@link #expired()} reports it and the tick branch that owns termination
     * also emits the completion feedback and releases the job.
     */
    public record Contract(
        Optional<UUID> contractor,
        ContractKind kind,
        Optional<BlockPos> target,
        int remainingTicks,
        int completedUnits,
        ContractEnd end
    ) {
        public Contract {
            contractor = Objects.requireNonNull(contractor, "contractor");
            kind = kind == null ? ContractKind.NONE : kind;
            target = Objects.requireNonNull(target, "target").map(BlockPos::immutable);
            remainingTicks = HobgoblinJourneyRules.clampRemaining(
                remainingTicks, HobgoblinJourneyRules.CONTRACT_DURATION_TICKS
            );
            completedUnits = Math.clamp(completedUnits, 0, HobgoblinJourneyRules.MAX_CONTRACT_UNITS);
            end = end == null ? ContractEnd.ACTIVE : end;
            // Identity shape: an agreement without a contractor has no kind, target, clock, or
            // unit count. Nothing here decides that a live agreement ended.
            if (contractor.isEmpty()) {
                kind = ContractKind.NONE;
                target = Optional.empty();
                remainingTicks = 0;
                completedUnits = 0;
            }
        }

        public static Contract none() {
            return new Contract(
                Optional.empty(), ContractKind.NONE, Optional.empty(), 0, 0, ContractEnd.ACTIVE
            );
        }

        public static Contract accepted(
            final UUID contractor,
            final ContractKind kind,
            final Optional<BlockPos> target
        ) {
            return new Contract(
                Optional.ofNullable(contractor),
                kind,
                target == null ? Optional.empty() : target,
                HobgoblinJourneyRules.CONTRACT_DURATION_TICKS,
                0,
                ContractEnd.ACTIVE
            );
        }

        public boolean active() {
            return contractor.isPresent() && end == ContractEnd.ACTIVE;
        }

        /** Reported, never applied here: ending the agreement belongs to the tick branch. */
        public boolean expired() {
            return contractor.isPresent()
                && end == ContractEnd.ACTIVE
                && HobgoblinJourneyRules.isDue(remainingTicks);
        }

        public boolean unitsExhausted() {
            return contractor.isPresent()
                && completedUnits >= HobgoblinJourneyRules.MAX_CONTRACT_UNITS;
        }

        public Contract withUnit() {
            return new Contract(contractor, kind, target, remainingTicks, completedUnits + 1, end);
        }

        public Contract ended(final ContractEnd reason) {
            return new Contract(
                Optional.empty(), ContractKind.NONE, Optional.empty(), 0, 0,
                reason == null ? ContractEnd.INVALID : reason
            );
        }
    }

    /** At most one caravan reference plus its dimension-qualified shared route waypoint. */
    public record Caravan(
        Optional<Long> key,
        Optional<UUID> leader,
        Optional<BlockPos> waypoint,
        Optional<String> dimension,
        int routeRemainingTicks,
        int regroupRemainingTicks
    ) {
        public Caravan {
            key = Objects.requireNonNull(key, "key");
            leader = Objects.requireNonNull(leader, "leader");
            waypoint = Objects.requireNonNull(waypoint, "waypoint").map(BlockPos::immutable);
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            routeRemainingTicks = HobgoblinJourneyRules.clampRemaining(
                routeRemainingTicks, HobgoblinJourneyRules.FAR_FUTURE_TICKS
            );
            regroupRemainingTicks = HobgoblinJourneyRules.clampRemaining(
                regroupRemainingTicks, HobgoblinJourneyRules.REGROUP_DEADLINE_TICKS
            );
            // Identity shape: a route clock is meaningless without the waypoint it counts down to.
            if (waypoint.isEmpty() || dimension.isEmpty()) {
                waypoint = Optional.empty();
                dimension = Optional.empty();
                routeRemainingTicks = 0;
            }
            // Identity shape: no caravan means no leader and no regroup deadline to run.
            if (key.isEmpty()) {
                leader = Optional.empty();
                regroupRemainingTicks = 0;
            }
        }

        public static Caravan none() {
            return new Caravan(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 0, 0
            );
        }

        public boolean present() {
            return key.isPresent();
        }

        public Caravan withKey(final long updated) {
            return new Caravan(
                Optional.of(updated), leader, waypoint, dimension,
                routeRemainingTicks, regroupRemainingTicks
            );
        }

        public Caravan withLeader(final Optional<UUID> updated) {
            return new Caravan(
                key, updated == null ? Optional.empty() : updated, waypoint, dimension,
                routeRemainingTicks, regroupRemainingTicks
            );
        }

        public Caravan withWaypoint(final BlockPos updated, final String updatedDimension, final int ticks) {
            return new Caravan(
                key, leader, Optional.ofNullable(updated), Optional.ofNullable(updatedDimension),
                ticks, regroupRemainingTicks
            );
        }

        public Caravan clearWaypoint() {
            return new Caravan(key, leader, Optional.empty(), Optional.empty(), 0, regroupRemainingTicks);
        }

        public Caravan withRegroup(final int ticks) {
            return new Caravan(key, leader, waypoint, dimension, routeRemainingTicks, ticks);
        }
    }

    /** At most one camp reference plus the phase this traveler expects that camp to be in. */
    public record Camp(Optional<Long> key, CampPhase phase) {
        public Camp {
            key = Objects.requireNonNull(key, "key");
            phase = phase == null ? CampPhase.NONE : phase;
            // Identity shape: a phase without a camp key names nothing.
            if (key.isEmpty()) {
                phase = CampPhase.NONE;
            }
        }

        public static Camp none() {
            return new Camp(Optional.empty(), CampPhase.NONE);
        }

        public static Camp at(final long key, final CampPhase phase) {
            return new Camp(Optional.of(key), phase);
        }

        public boolean present() {
            return key.isPresent() && phase != CampPhase.NONE;
        }
    }

    /**
     * The single job claim. Like {@link Contract}, an exhausted lease is reported rather than
     * silently ended, so the tick branch that releases the real lease from saved data always runs.
     */
    public record Job(
        Optional<UUID> claimId,
        Optional<BlockPos> target,
        Optional<String> dimension,
        int commitRemainingTicks
    ) {
        public Job {
            claimId = Objects.requireNonNull(claimId, "claimId");
            target = Objects.requireNonNull(target, "target").map(BlockPos::immutable);
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            commitRemainingTicks = HobgoblinJourneyRules.clampRemaining(
                commitRemainingTicks, HobgoblinJourneyRules.CLAIM_LEASE_TICKS
            );
            // Identity shape only. The lease clock reaching zero is deliberately NOT handled here:
            // see leaseExpired(), which the tick branch reads before releasing the real claim.
            if (target.isEmpty() || dimension.isEmpty()) {
                target = Optional.empty();
                dimension = Optional.empty();
            }
        }

        public static Job none() {
            return new Job(Optional.empty(), Optional.empty(), Optional.empty(), 0);
        }

        public boolean holdsClaim() {
            return claimId.isPresent();
        }

        /** Reported, never applied here. */
        public boolean leaseExpired() {
            return claimId.isPresent() && HobgoblinJourneyRules.isDue(commitRemainingTicks);
        }
    }

    /**
     * Exactly one remembered direct aggressor. The identity deliberately survives its own timer
     * reaching zero so the tick branch that clears it can also stop the chase and re-arm travel.
     */
    public record Combat(Optional<UUID> aggressor, int aggressorRemainingTicks, boolean retreating) {
        public Combat {
            aggressor = Objects.requireNonNull(aggressor, "aggressor");
            aggressorRemainingTicks = HobgoblinJourneyRules.clampRemaining(
                aggressorRemainingTicks, HobgoblinJourneyRules.AGGRESSOR_MEMORY_TICKS
            );
            // Identity shape only. The memory clock reaching zero is deliberately NOT handled
            // here: see aggressorLapsed(), which the tick branch reads before clearing the target.
            if (aggressor.isEmpty()) {
                aggressorRemainingTicks = 0;
            }
        }

        public static Combat none() {
            return new Combat(Optional.empty(), 0, false);
        }

        public static Combat aggressor(final UUID id) {
            return new Combat(
                Optional.ofNullable(id), HobgoblinJourneyRules.AGGRESSOR_MEMORY_TICKS, false
            );
        }

        public boolean remembersAggressor() {
            return aggressor.isPresent() && !HobgoblinJourneyRules.isDue(aggressorRemainingTicks);
        }

        /** Reported, never applied here. */
        public boolean aggressorLapsed() {
            return aggressor.isPresent() && HobgoblinJourneyRules.isDue(aggressorRemainingTicks);
        }

        public Combat withRetreating(final boolean updated) {
            return new Combat(aggressor, aggressorRemainingTicks, updated);
        }
    }

    /** One bounded hospitality fact about one player. */
    public record Relation(UUID id, RelationFact kind, int remainingTicks) {
        public Relation {
            Objects.requireNonNull(id, "id");
            kind = kind == null ? RelationFact.FAIR_TRADE : kind;
            remainingTicks = HobgoblinJourneyRules.clampRemaining(remainingTicks, kind.expiryTicks());
        }

        public static Relation of(final UUID id, final RelationFact kind) {
            return new Relation(id, kind, kind == null ? 0 : kind.expiryTicks());
        }

        public boolean live() {
            return !HobgoblinJourneyRules.isDue(remainingTicks);
        }
    }

    /** Route failure bookkeeping and the blocked-exit counter that arms the village backoff. */
    public record Cadence(
        RouteFailure lastFailure,
        int routeFailures,
        int retryRemainingTicks,
        boolean stuck,
        int blockedExits
    ) {
        public Cadence {
            lastFailure = lastFailure == null ? RouteFailure.NONE : lastFailure;
            routeFailures = Math.clamp(routeFailures, 0, HobgoblinJourneyRules.MAX_ROUTE_FAILURES);
            retryRemainingTicks = HobgoblinJourneyRules.clampRemaining(
                retryRemainingTicks, HobgoblinJourneyRules.ROUTE_BACKOFF_TICKS
            );
            blockedExits = Math.clamp(blockedExits, 0, HobgoblinJourneyRules.MAX_BLOCKED_EXITS);
        }

        public static Cadence none() {
            return new Cadence(RouteFailure.NONE, 0, 0, false, 0);
        }

        public Cadence withBlockedExits(final int updated) {
            return new Cadence(lastFailure, routeFailures, retryRemainingTicks, stuck, updated);
        }
    }

    // ---------------------------------------------------------------- factories

    public static HobgoblinJourneyState empty() {
        return new HobgoblinJourneyState(
            SCHEMA_VERSION,
            GoblinProfession.FALLBACK,
            Merchant.initial(),
            Mode.IDLE,
            Contract.none(),
            Caravan.none(),
            Camp.none(),
            Job.none(),
            Combat.none(),
            List.of(),
            Cadence.none(),
            0,
            0
        );
    }

    // ---------------------------------------------------------------- copy-on-write

    public HobgoblinJourneyState withProfession(final GoblinProfession updated) {
        return new HobgoblinJourneyState(schemaVersion, updated, merchant, mode, contract, caravan,
            camp, job, combat, relations, cadence, childGiftCooldownTicks, birthCooldownTicks);
    }

    public HobgoblinJourneyState withMerchant(final Merchant updated) {
        return new HobgoblinJourneyState(schemaVersion, profession, updated, mode, contract, caravan,
            camp, job, combat, relations, cadence, childGiftCooldownTicks, birthCooldownTicks);
    }

    public HobgoblinJourneyState withMode(final Mode updated) {
        return new HobgoblinJourneyState(schemaVersion, profession, merchant, updated, contract,
            caravan, camp, job, combat, relations, cadence, childGiftCooldownTicks, birthCooldownTicks);
    }

    public HobgoblinJourneyState withContract(final Contract updated) {
        return new HobgoblinJourneyState(schemaVersion, profession, merchant, mode, updated, caravan,
            camp, job, combat, relations, cadence, childGiftCooldownTicks, birthCooldownTicks);
    }

    public HobgoblinJourneyState withCaravan(final Caravan updated) {
        return new HobgoblinJourneyState(schemaVersion, profession, merchant, mode, contract, updated,
            camp, job, combat, relations, cadence, childGiftCooldownTicks, birthCooldownTicks);
    }

    public HobgoblinJourneyState withCamp(final Camp updated) {
        return new HobgoblinJourneyState(schemaVersion, profession, merchant, mode, contract, caravan,
            updated, job, combat, relations, cadence, childGiftCooldownTicks, birthCooldownTicks);
    }

    public HobgoblinJourneyState withJob(final Job updated) {
        return new HobgoblinJourneyState(schemaVersion, profession, merchant, mode, contract, caravan,
            camp, updated, combat, relations, cadence, childGiftCooldownTicks, birthCooldownTicks);
    }

    public HobgoblinJourneyState withCombat(final Combat updated) {
        return new HobgoblinJourneyState(schemaVersion, profession, merchant, mode, contract, caravan,
            camp, job, updated, relations, cadence, childGiftCooldownTicks, birthCooldownTicks);
    }

    public HobgoblinJourneyState withRelations(final List<Relation> updated) {
        return new HobgoblinJourneyState(schemaVersion, profession, merchant, mode, contract, caravan,
            camp, job, combat, updated, cadence, childGiftCooldownTicks, birthCooldownTicks);
    }

    public HobgoblinJourneyState withCadence(final Cadence updated) {
        return new HobgoblinJourneyState(schemaVersion, profession, merchant, mode, contract, caravan,
            camp, job, combat, relations, updated, childGiftCooldownTicks, birthCooldownTicks);
    }

    public HobgoblinJourneyState withChildGiftCooldown(final int ticks) {
        return new HobgoblinJourneyState(schemaVersion, profession, merchant, mode, contract, caravan,
            camp, job, combat, relations, cadence, ticks, birthCooldownTicks);
    }

    public HobgoblinJourneyState withBirthCooldown(final int ticks) {
        return new HobgoblinJourneyState(schemaVersion, profession, merchant, mode, contract, caravan,
            camp, job, combat, relations, cadence, childGiftCooldownTicks, ticks);
    }

    /** Drops the job claim, the target, and the mode without touching contract or caravan meaning. */
    public HobgoblinJourneyState releaseJob() {
        return withJob(Job.none()).withMode(Mode.IDLE);
    }

    // ---------------------------------------------------------------- relations

    /**
     * Records one fact, collapsing by player and kind, and evicting the weakest then oldest entry at
     * capacity. Ordering is deterministic; no hash-map iteration decides which fact survives.
     */
    public HobgoblinJourneyState withRelation(final UUID player, final RelationFact fact) {
        if (player == null || fact == null) {
            return this;
        }
        final List<Relation> updated = new ArrayList<>(relations);
        for (int index = 0; index < updated.size(); index++) {
            final Relation existing = updated.get(index);
            if (existing.id().equals(player) && existing.kind() == fact) {
                updated.set(index, Relation.of(player, fact));
                return withRelations(updated);
            }
        }
        if (updated.size() >= HobgoblinJourneyRules.MAX_RELATION_FACTS) {
            final int victim = HobgoblinJourneyRules.evictionIndex(
                updated.stream().map(Relation::kind).toList(),
                updated.stream()
                    .map(relation -> relation.kind().expiryTicks() - relation.remainingTicks())
                    .toList()
            );
            if (victim >= 0) {
                updated.remove(victim);
            }
        }
        updated.add(Relation.of(player, fact));
        return withRelations(updated);
    }

    /** The clamped bounded score for one player, computed only from live facts. */
    public int relationScore(final UUID player) {
        if (player == null) {
            return 0;
        }
        return HobgoblinJourneyRules.relationScore(relations.stream()
            .filter(relation -> relation.id().equals(player))
            .filter(Relation::live)
            .map(Relation::kind)
            .toList());
    }

    // ---------------------------------------------------------------- persistence

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        tag.putString("Profession", profession.id());
        tag.putInt("Level", merchant.level());
        tag.putInt("Xp", merchant.xp());
        tag.putInt("Restocks", merchant.restocksToday());
        tag.putInt("RestockGap", merchant.restockSpacingTicks());
        tag.putString("Mode", lower(mode.name()));
        contract.contractor().ifPresent(id -> tag.putString("Contractor", id.toString()));
        tag.putString("ContractKind", lower(contract.kind().name()));
        contract.target().ifPresent(position -> tag.putLong("ContractTarget", position.asLong()));
        tag.putInt("ContractLeft", contract.remainingTicks());
        tag.putInt("ContractUnits", contract.completedUnits());
        tag.putString("ContractEnd", lower(contract.end().name()));
        caravan.key().ifPresent(key -> tag.putLong("CaravanKey", key));
        caravan.leader().ifPresent(id -> tag.putString("CaravanLeader", id.toString()));
        caravan.waypoint().ifPresent(position -> tag.putLong("Waypoint", position.asLong()));
        caravan.dimension().ifPresent(dimension -> tag.putString("WaypointDim", dimension));
        tag.putInt("RouteLeft", caravan.routeRemainingTicks());
        tag.putInt("RegroupLeft", caravan.regroupRemainingTicks());
        camp.key().ifPresent(key -> tag.putLong("CampKey", key));
        tag.putString("CampPhase", lower(camp.phase().name()));
        job.claimId().ifPresent(id -> tag.putString("ClaimId", id.toString()));
        job.target().ifPresent(position -> tag.putLong("JobTarget", position.asLong()));
        job.dimension().ifPresent(dimension -> tag.putString("JobDim", dimension));
        tag.putInt("JobLeft", job.commitRemainingTicks());
        combat.aggressor().ifPresent(id -> tag.putString("Aggressor", id.toString()));
        tag.putInt("AggressorLeft", combat.aggressorRemainingTicks());
        tag.putBoolean("Retreating", combat.retreating());
        final ListTag facts = new ListTag();
        for (final Relation relation : relations) {
            final CompoundTag entry = new CompoundTag();
            entry.putString("Id", relation.id().toString());
            entry.putString("Kind", lower(relation.kind().name()));
            entry.putInt("Left", relation.remainingTicks());
            facts.add(entry);
        }
        tag.put("Relations", facts);
        tag.putString("RouteFail", lower(cadence.lastFailure().name()));
        tag.putInt("RouteFails", cadence.routeFailures());
        tag.putInt("RouteRetry", cadence.retryRemainingTicks());
        tag.putBoolean("Stuck", cadence.stuck());
        tag.putInt("BlockedExits", cadence.blockedExits());
        tag.putInt("GiftCooldown", childGiftCooldownTicks);
        tag.putInt("BirthCooldown", birthCooldownTicks);
        return tag;
    }

    /**
     * Reads schema version 1. A missing, malformed, or unknown-future schema resets to a safe
     * solitary traveler rather than guessing, while the public entity identity, inventory, offers,
     * profession, and age are decoded by the merchant body itself and are never discarded here.
     *
     * <p>A committed job claim and mode are deliberately dropped on load: a transaction never spans
     * a tick, so it can never span an unload. The contractor, the caravan and camp references, the
     * relationship facts, and every deadline survive; a cross-dimension waypoint does not.</p>
     */
    public static HobgoblinJourneyState read(final CompoundTag tag, final String currentDimension) {
        if (tag == null || tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty();
        }
        final Contract contract = new Contract(
            readUuid(tag, "Contractor"),
            parseEnum(ContractKind.values(), tag.getStringOr("ContractKind", ""), ContractKind.NONE),
            readPosition(tag, "ContractTarget"),
            tag.getIntOr("ContractLeft", 0),
            tag.getIntOr("ContractUnits", 0),
            parseEnum(ContractEnd.values(), tag.getStringOr("ContractEnd", ""), ContractEnd.ACTIVE)
        );
        final Caravan caravan = new Caravan(
            readLong(tag, "CaravanKey"),
            readUuid(tag, "CaravanLeader"),
            readPosition(tag, "Waypoint"),
            readDimension(tag, "WaypointDim").filter(dimension -> dimension.equals(currentDimension)),
            tag.getIntOr("RouteLeft", 0),
            tag.getIntOr("RegroupLeft", 0)
        );
        final Camp camp = new Camp(
            readLong(tag, "CampKey"),
            parseEnum(CampPhase.values(), tag.getStringOr("CampPhase", ""), CampPhase.NONE)
        );
        final Combat combat = new Combat(
            readUuid(tag, "Aggressor"),
            tag.getIntOr("AggressorLeft", 0),
            tag.getBooleanOr("Retreating", false)
        );
        final List<Relation> relations = new ArrayList<>();
        final ListTag encodedRelations = tag.getListOrEmpty("Relations");
        final int factCount = Math.min(
            encodedRelations.size(), HobgoblinJourneyRules.MAX_RELATION_FACTS
        );
        for (int index = 0; index < factCount; index++) {
            final CompoundTag entry = encodedRelations.getCompoundOrEmpty(index);
            readUuidValue(entry.getStringOr("Id", "")).ifPresent(id -> relations.add(new Relation(
                id,
                parseEnum(RelationFact.values(), entry.getStringOr("Kind", ""), RelationFact.FAIR_TRADE),
                entry.getIntOr("Left", 0)
            )));
        }
        final Cadence cadence = new Cadence(
            parseEnum(RouteFailure.values(), tag.getStringOr("RouteFail", ""), RouteFailure.NONE),
            tag.getIntOr("RouteFails", 0),
            tag.getIntOr("RouteRetry", 0),
            tag.getBooleanOr("Stuck", false),
            tag.getIntOr("BlockedExits", 0)
        );
        return new HobgoblinJourneyState(
            SCHEMA_VERSION,
            GoblinProfession.byId(tag.getStringOr("Profession", GoblinProfession.FALLBACK.id())),
            new Merchant(
                tag.getIntOr("Level", HobgoblinJourneyRules.MIN_MERCHANT_LEVEL),
                tag.getIntOr("Xp", 0),
                tag.getIntOr("Restocks", 0),
                tag.getIntOr("RestockGap", 0)
            ),
            Mode.IDLE,
            contract,
            caravan,
            camp,
            Job.none(),
            combat,
            relations,
            cadence,
            tag.getIntOr("GiftCooldown", 0),
            tag.getIntOr("BirthCooldown", 0)
        );
    }

    /**
     * Conservative 1.4 migration. Only the custom profession, the Villager XP as bounded merchant
     * XP, the child-gift deadline, and the existing owner UUID are read. The owner becomes a
     * bounded {@code LEGACY_WORK} agreement rather than permanent ownership; an unresolvable owner
     * simply expires without any world mutation. No Brain, POI, gossip, golem, native raid, human
     * food, settlement, or conversion state is imported, and nothing is invented.
     */
    public static HobgoblinJourneyState migrateLegacy(
        final String legacyProfessionId,
        final int legacyVillagerXp,
        final long legacyNextGiftGameTime,
        final long currentGameTime,
        final Optional<UUID> legacyOwner
    ) {
        final int giftRemaining = legacyNextGiftGameTime <= currentGameTime
            ? 0
            : (int) Math.min(
                HobgoblinJourneyRules.CHILD_GIFT_COOLDOWN_TICKS,
                legacyNextGiftGameTime - currentGameTime
            );
        final Contract migrated = legacyOwner
            .map(owner -> Contract.accepted(owner, ContractKind.LEGACY_WORK, Optional.empty()))
            .orElseGet(Contract::none);
        return empty()
            .withProfession(GoblinProfession.byId(
                legacyProfessionId == null ? GoblinProfession.FALLBACK.id() : legacyProfessionId
            ))
            .withMerchant(Merchant.initial().withXp(Math.max(0, legacyVillagerXp)))
            .withContract(migrated)
            .withChildGiftCooldown(giftRemaining);
    }

    // ---------------------------------------------------------------- helpers

    private static List<Relation> normalizeRelations(final List<Relation> supplied) {
        if (supplied == null || supplied.isEmpty()) {
            return List.of();
        }
        // Deterministic collapse by (player, kind); insertion order decides which duplicate wins.
        final Map<String, Relation> collapsed = new LinkedHashMap<>();
        for (final Relation relation : supplied) {
            if (relation == null) {
                continue;
            }
            collapsed.put(relation.id() + "/" + relation.kind().name(), relation);
        }
        return List.copyOf(collapsed.values().stream()
            .limit(HobgoblinJourneyRules.MAX_RELATION_FACTS)
            .toList());
    }

    private static <T extends Enum<T>> T parseEnum(final T[] values, final String raw, final T fallback) {
        for (final T candidate : values) {
            if (candidate.name().equalsIgnoreCase(raw)) {
                return candidate;
            }
        }
        return fallback;
    }

    private static String lower(final String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static Optional<Long> readLong(final CompoundTag tag, final String key) {
        final long stored = tag.getLongOr(key, Long.MIN_VALUE);
        return stored == Long.MIN_VALUE ? Optional.empty() : Optional.of(stored);
    }

    private static Optional<BlockPos> readPosition(final CompoundTag tag, final String key) {
        return readLong(tag, key).map(BlockPos::of);
    }

    private static Optional<String> readDimension(final CompoundTag tag, final String key) {
        final String stored = tag.getStringOr(key, "");
        return stored.isBlank() ? Optional.empty() : Optional.of(stored);
    }

    private static Optional<UUID> readUuid(final CompoundTag tag, final String key) {
        return readUuidValue(tag.getStringOr(key, ""));
    }

    private static Optional<UUID> readUuidValue(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (final IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
