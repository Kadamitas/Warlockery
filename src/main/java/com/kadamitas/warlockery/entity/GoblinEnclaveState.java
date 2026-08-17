package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.GoblinEnclaveRules.CombatRole;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.Intent;
import com.kadamitas.warlockery.entity.GoblinEnclaveRules.RouteFailure;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Versioned, fixed-cardinality F10 Goblin semantics. Exactly one enclave anchor, one patron, one
 * job claim, one target, one destination, one shelter point, one retreat point, and one combat role
 * may exist at a time. Nothing here stores a path, {@code Level}, {@code Entity}, {@code Container},
 * block-state snapshot, behavior queue, Brain, nearby-entity list, settlement member object, or
 * per-tick history.
 *
 * <p>Every duration is a remaining loaded-tick count rather than an absolute world deadline, so an
 * unloaded Goblin pauses its meaning instead of expiring it and no missed work is replayed on load.
 * Zero always reads as due.</p>
 */
public record GoblinEnclaveState(
    int schemaVersion,
    GoblinProfession profession,
    Merchant merchant,
    Anchor anchor,
    Patron patron,
    Action action,
    Combat combat,
    Cadence cadence,
    int foodPoints,
    int childGiftCooldownTicks
) {
    public static final int SCHEMA_VERSION = GoblinEnclaveRules.STATE_SCHEMA_VERSION;

    public GoblinEnclaveState {
        profession = profession == null ? GoblinProfession.FALLBACK : profession;
        merchant = Objects.requireNonNull(merchant, "merchant");
        anchor = Objects.requireNonNull(anchor, "anchor");
        patron = Objects.requireNonNull(patron, "patron");
        action = Objects.requireNonNull(action, "action");
        combat = Objects.requireNonNull(combat, "combat");
        cadence = Objects.requireNonNull(cadence, "cadence");
        foodPoints = GoblinEnclaveRules.clampFoodPoints(foodPoints);
        childGiftCooldownTicks = GoblinEnclaveRules.clampRemaining(
            childGiftCooldownTicks, GoblinEnclaveRules.CHILD_GIFT_COOLDOWN_TICKS
        );
        schemaVersion = SCHEMA_VERSION;
    }

    /** Merchant progression owned by the Goblin body, never by a human Villager data component. */
    public record Merchant(int level, int xp, int restocksToday, int restockSpacingTicks) {
        public Merchant {
            level = GoblinEnclaveRules.clampMerchantLevel(level);
            xp = Math.max(0, xp);
            restocksToday = Math.clamp(restocksToday, 0, GoblinEnclaveRules.MAX_RESTOCKS_PER_DAY);
            restockSpacingTicks = GoblinEnclaveRules.clampRemaining(
                restockSpacingTicks, GoblinEnclaveRules.RESTOCK_SPACING_TICKS
            );
        }

        public static Merchant initial() {
            return new Merchant(GoblinEnclaveRules.MIN_MERCHANT_LEVEL, 0, 0, 0);
        }

        public Merchant withXp(final int updated) {
            final int safe = Math.max(0, updated);
            return new Merchant(GoblinEnclaveRules.levelForXp(safe), safe, restocksToday, restockSpacingTicks);
        }

        public Merchant afterRestock() {
            return new Merchant(level, xp, restocksToday + 1, GoblinEnclaveRules.RESTOCK_SPACING_TICKS);
        }

        public Merchant onNewDay() {
            return new Merchant(level, xp, 0, 0);
        }
    }

    /** At most one enclave key plus its dimension-qualified anchor position. */
    public record Anchor(Optional<Long> enclaveKey, Optional<BlockPos> position, Optional<String> dimension) {
        public Anchor {
            enclaveKey = Objects.requireNonNull(enclaveKey, "enclaveKey");
            position = Objects.requireNonNull(position, "position").map(BlockPos::immutable);
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            if (enclaveKey.isEmpty() || position.isEmpty() || dimension.isEmpty()) {
                enclaveKey = Optional.empty();
                position = Optional.empty();
                dimension = Optional.empty();
            }
        }

        public static Anchor none() {
            return new Anchor(Optional.empty(), Optional.empty(), Optional.empty());
        }

        public static Anchor at(final long key, final BlockPos position, final String dimension) {
            return new Anchor(Optional.of(key), Optional.of(position), Optional.of(dimension));
        }

        public boolean present() {
            return enclaveKey.isPresent() && position.isPresent() && dimension.isPresent();
        }
    }

    /**
     * One bound player patron plus at most one loaded deposit preference and one work preference.
     * The saved patron UUID deliberately survives a patron logout or dimension mismatch; only the
     * active preferences expire, so returning to the same dimension restores finite loaded behavior.
     */
    public record Patron(
        Optional<UUID> id,
        Optional<BlockPos> depositPreference,
        Optional<String> preferenceDimension,
        int preferenceRemainingTicks
    ) {
        public Patron {
            id = Objects.requireNonNull(id, "id");
            depositPreference = Objects.requireNonNull(depositPreference, "depositPreference")
                .map(BlockPos::immutable);
            preferenceDimension = Objects.requireNonNull(preferenceDimension, "preferenceDimension")
                .filter(value -> !value.isBlank());
            if (id.isEmpty() || depositPreference.isEmpty() || preferenceDimension.isEmpty()
                || GoblinEnclaveRules.isDue(preferenceRemainingTicks)) {
                depositPreference = Optional.empty();
                preferenceDimension = Optional.empty();
                preferenceRemainingTicks = 0;
            }
            preferenceRemainingTicks = GoblinEnclaveRules.clampRemaining(
                preferenceRemainingTicks, (int) GoblinEnclaveRules.FAR_FUTURE_TICKS
            );
        }

        public static Patron none() {
            return new Patron(Optional.empty(), Optional.empty(), Optional.empty(), 0);
        }

        public static Patron bound(final UUID id) {
            return new Patron(Optional.of(id), Optional.empty(), Optional.empty(), 0);
        }

        public boolean bound() {
            return id.isPresent();
        }

        /** Expires only the active preference; the bound patron identity is deliberately kept. */
        public Patron expirePreference() {
            return new Patron(id, Optional.empty(), Optional.empty(), 0);
        }
    }

    /**
     * The single committed semantic action: one intent, at most one enclave claim, at most one
     * target, at most one destination, and the material reservation summary backing it.
     */
    public record Action(
        Intent intent,
        Optional<UUID> claimId,
        Optional<UUID> targetId,
        Optional<BlockPos> destination,
        Optional<String> destinationDimension,
        int commitRemainingTicks,
        int recoveryRemainingTicks,
        int reservedDirt,
        int reservedLogs,
        int reservedPlanks
    ) {
        public Action {
            intent = intent == null ? Intent.IDLE : intent;
            claimId = Objects.requireNonNull(claimId, "claimId");
            targetId = Objects.requireNonNull(targetId, "targetId");
            destination = Objects.requireNonNull(destination, "destination").map(BlockPos::immutable);
            destinationDimension = Objects.requireNonNull(destinationDimension, "destinationDimension")
                .filter(value -> !value.isBlank());
            if (destination.isEmpty() || destinationDimension.isEmpty()) {
                destination = Optional.empty();
                destinationDimension = Optional.empty();
            }
            commitRemainingTicks = GoblinEnclaveRules.clampRemaining(
                commitRemainingTicks, GoblinEnclaveRules.CLAIM_LEASE_TICKS
            );
            recoveryRemainingTicks = GoblinEnclaveRules.clampRemaining(
                recoveryRemainingTicks, GoblinEnclaveRules.ROUTE_BACKOFF_TICKS
            );
            reservedDirt = Math.clamp(reservedDirt, 0, GoblinEnclaveRules.HUT_DIRT_COST);
            reservedLogs = Math.clamp(reservedLogs, 0, GoblinEnclaveRules.HUT_LOG_COST);
            reservedPlanks = Math.clamp(reservedPlanks, 0, GoblinEnclaveRules.HUT_MAX_EDITS);
            // Deliberately no expiry side effect here. Clearing the claim from the canonical
            // constructor would silently end something a tick branch owns: the intent would survive,
            // holdsClaim() would report false, the runtime's execute guard would be skipped, and two
            // Goblins past their lease could mutate the same worksite. Lease expiry is a tick
            // transition in GoblinEnclaveRuntime.advanceLoadedTimers, which cancels the action and
            // releases the real lease from saved data.
        }

        public static Action idle() {
            return new Action(Intent.IDLE, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), 0, 0, 0, 0, 0);
        }

        public boolean holdsClaim() {
            return claimId.isPresent();
        }

        /** True on the tick the lease runs out; the runtime turns this into an explicit cancel. */
        public boolean leaseExpired() {
            return claimId.isPresent() && GoblinEnclaveRules.isDue(commitRemainingTicks);
        }
    }

    /** One combat role, one alarm epoch, and one retreat flag. No target list, no threat graph. */
    public record Combat(
        CombatRole role,
        long alarmEpoch,
        boolean retreating,
        Optional<BlockPos> shelter,
        Optional<BlockPos> retreat
    ) {
        public Combat {
            role = role == null ? CombatRole.NONE : role;
            alarmEpoch = Math.max(0L, alarmEpoch);
            shelter = Objects.requireNonNull(shelter, "shelter").map(BlockPos::immutable);
            retreat = Objects.requireNonNull(retreat, "retreat").map(BlockPos::immutable);
            if (role == CombatRole.NONE) {
                retreating = false;
            }
        }

        public static Combat none() {
            return new Combat(CombatRole.NONE, 0L, false, Optional.empty(), Optional.empty());
        }
    }

    /**
     * Route-failure classification and the persisted retry backoff. Perception, work, member, and
     * feedback cadences deliberately live in the runtime's transient scratch: losing them can delay
     * work by at most one interval but can never replay an action.
     */
    public record Cadence(RouteFailure lastFailure, int routeFailures, int retryRemainingTicks, boolean stuck) {
        public Cadence {
            lastFailure = lastFailure == null ? RouteFailure.NONE : lastFailure;
            routeFailures = Math.clamp(routeFailures, 0, GoblinEnclaveRules.MAX_ROUTE_FAILURES);
            retryRemainingTicks = GoblinEnclaveRules.clampRemaining(
                retryRemainingTicks, GoblinEnclaveRules.ROUTE_BACKOFF_TICKS
            );
            if (routeFailures == 0) {
                lastFailure = RouteFailure.NONE;
                stuck = false;
            }
        }

        public static Cadence none() {
            return new Cadence(RouteFailure.NONE, 0, 0, false);
        }
    }

    public static GoblinEnclaveState empty() {
        return new GoblinEnclaveState(SCHEMA_VERSION, GoblinProfession.FALLBACK, Merchant.initial(),
            Anchor.none(), Patron.none(), Action.idle(), Combat.none(), Cadence.none(), 0, 0);
    }

    public GoblinEnclaveState withProfession(final GoblinProfession updated) {
        return new GoblinEnclaveState(schemaVersion, updated, merchant, anchor, patron, action,
            combat, cadence, foodPoints, childGiftCooldownTicks);
    }

    public GoblinEnclaveState withMerchant(final Merchant updated) {
        return new GoblinEnclaveState(schemaVersion, profession, updated, anchor, patron, action,
            combat, cadence, foodPoints, childGiftCooldownTicks);
    }

    public GoblinEnclaveState withAnchor(final Anchor updated) {
        return new GoblinEnclaveState(schemaVersion, profession, merchant, updated, patron, action,
            combat, cadence, foodPoints, childGiftCooldownTicks);
    }

    public GoblinEnclaveState withPatron(final Patron updated) {
        return new GoblinEnclaveState(schemaVersion, profession, merchant, anchor, updated, action,
            combat, cadence, foodPoints, childGiftCooldownTicks);
    }

    public GoblinEnclaveState withAction(final Action updated) {
        return new GoblinEnclaveState(schemaVersion, profession, merchant, anchor, patron, updated,
            combat, cadence, foodPoints, childGiftCooldownTicks);
    }

    public GoblinEnclaveState withCombat(final Combat updated) {
        return new GoblinEnclaveState(schemaVersion, profession, merchant, anchor, patron, action,
            updated, cadence, foodPoints, childGiftCooldownTicks);
    }

    public GoblinEnclaveState withCadence(final Cadence updated) {
        return new GoblinEnclaveState(schemaVersion, profession, merchant, anchor, patron, action,
            combat, updated, foodPoints, childGiftCooldownTicks);
    }

    public GoblinEnclaveState withFoodPoints(final int updated) {
        return new GoblinEnclaveState(schemaVersion, profession, merchant, anchor, patron, action,
            combat, cadence, updated, childGiftCooldownTicks);
    }

    public GoblinEnclaveState withChildGiftCooldown(final int updated) {
        return new GoblinEnclaveState(schemaVersion, profession, merchant, anchor, patron, action,
            combat, cadence, foodPoints, updated);
    }

    /** Cancels the current action and every reservation without touching identity or progression. */
    public GoblinEnclaveState releaseAction() {
        return withAction(Action.idle());
    }

    /** Compact fixed-cardinality encoding; populated states must stay below the declared ceiling. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        tag.putString("Profession", profession.id());
        tag.putInt("Level", merchant.level());
        tag.putInt("Xp", merchant.xp());
        tag.putInt("Restocks", merchant.restocksToday());
        tag.putInt("RestockGap", merchant.restockSpacingTicks());
        anchor.enclaveKey().ifPresent(key -> tag.putLong("EnclaveKey", key));
        anchor.position().ifPresent(position -> tag.putLong("AnchorPos", position.asLong()));
        anchor.dimension().ifPresent(dimension -> tag.putString("AnchorDim", dimension));
        patron.id().ifPresent(id -> tag.putString("PatronId", id.toString()));
        patron.depositPreference().ifPresent(position -> tag.putLong("PatronDeposit", position.asLong()));
        patron.preferenceDimension().ifPresent(dimension -> tag.putString("PatronDim", dimension));
        tag.putInt("PatronPref", patron.preferenceRemainingTicks());
        tag.putString("Intent", action.intent().name().toLowerCase(Locale.ROOT));
        action.claimId().ifPresent(id -> tag.putString("ClaimId", id.toString()));
        action.targetId().ifPresent(id -> tag.putString("TargetId", id.toString()));
        action.destination().ifPresent(position -> tag.putLong("DestPos", position.asLong()));
        action.destinationDimension().ifPresent(dimension -> tag.putString("DestDim", dimension));
        tag.putInt("Commit", action.commitRemainingTicks());
        tag.putInt("Recovery", action.recoveryRemainingTicks());
        tag.putInt("ResDirt", action.reservedDirt());
        tag.putInt("ResLogs", action.reservedLogs());
        tag.putInt("ResPlanks", action.reservedPlanks());
        tag.putString("Role", combat.role().name().toLowerCase(Locale.ROOT));
        tag.putLong("AlarmEpoch", combat.alarmEpoch());
        tag.putBoolean("Retreating", combat.retreating());
        combat.shelter().ifPresent(position -> tag.putLong("Shelter", position.asLong()));
        combat.retreat().ifPresent(position -> tag.putLong("Retreat", position.asLong()));
        tag.putString("RouteFail", cadence.lastFailure().name().toLowerCase(Locale.ROOT));
        tag.putInt("RouteFails", cadence.routeFailures());
        tag.putInt("RouteRetry", cadence.retryRemainingTicks());
        tag.putBoolean("Stuck", cadence.stuck());
        tag.putInt("Food", foodPoints);
        tag.putInt("GiftCooldown", childGiftCooldownTicks);
        return tag;
    }

    /**
     * Reads schema version 1. A missing, malformed, or unknown-future schema resets to a safe
     * solitary Goblin rather than guessing. A committed action, claim, target, and material
     * reservation are deliberately dropped on load: a transaction never spans a tick, so it can
     * never span an unload either. The bound patron identity and the enclave anchor survive; a
     * cross-dimension anchor does not.
     */
    public static GoblinEnclaveState read(final CompoundTag tag, final String currentDimension) {
        if (tag == null || tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty();
        }
        final Anchor anchor = new Anchor(
            readLong(tag, "EnclaveKey"),
            readPosition(tag, "AnchorPos"),
            readDimension(tag, "AnchorDim").filter(dimension -> dimension.equals(currentDimension))
        );
        final Patron patron = new Patron(
            readUuid(tag, "PatronId"),
            readPosition(tag, "PatronDeposit"),
            readDimension(tag, "PatronDim").filter(dimension -> dimension.equals(currentDimension)),
            tag.getIntOr("PatronPref", 0)
        );
        final Combat combat = new Combat(
            parseRole(tag.getStringOr("Role", "")),
            tag.getLongOr("AlarmEpoch", 0L),
            tag.getBooleanOr("Retreating", false),
            readPosition(tag, "Shelter"),
            readPosition(tag, "Retreat")
        );
        final Cadence cadence = new Cadence(
            parseFailure(tag.getStringOr("RouteFail", "")),
            tag.getIntOr("RouteFails", 0),
            tag.getIntOr("RouteRetry", 0),
            tag.getBooleanOr("Stuck", false)
        );
        return new GoblinEnclaveState(
            SCHEMA_VERSION,
            GoblinProfession.byId(tag.getStringOr("Profession", GoblinProfession.FALLBACK.id())),
            new Merchant(
                tag.getIntOr("Level", GoblinEnclaveRules.MIN_MERCHANT_LEVEL),
                tag.getIntOr("Xp", 0),
                tag.getIntOr("Restocks", 0),
                tag.getIntOr("RestockGap", 0)
            ),
            anchor,
            patron,
            Action.idle(),
            combat,
            cadence,
            tag.getIntOr("Food", 0),
            tag.getIntOr("GiftCooldown", 0)
        );
    }

    /**
     * Conservative 1.4 entity migration. The old custom profession is authoritative, old Villager
     * merchant XP becomes a bounded merchant-XP fallback, and the old child-gift deadline becomes a
     * remaining cooldown. No old Brain, POI, gossip, golem, native-raid, human-Villager food, or
     * conversion state is read, and nothing is invented: an old Goblin without enclave state starts
     * solitary.
     */
    public static GoblinEnclaveState migrateLegacy(
        final String legacyProfessionId,
        final int legacyVillagerXp,
        final long legacyNextGiftGameTime,
        final long currentGameTime,
        final Optional<UUID> legacyOwner
    ) {
        final int giftRemaining = legacyNextGiftGameTime <= currentGameTime
            ? 0
            : (int) Math.min(
                GoblinEnclaveRules.CHILD_GIFT_COOLDOWN_TICKS,
                legacyNextGiftGameTime - currentGameTime
            );
        return empty()
            .withProfession(GoblinProfession.byId(
                legacyProfessionId == null ? GoblinProfession.FALLBACK.id() : legacyProfessionId
            ))
            .withMerchant(Merchant.initial().withXp(Math.max(0, legacyVillagerXp)))
            .withPatron(legacyOwner.map(Patron::bound).orElseGet(Patron::none))
            .withChildGiftCooldown(giftRemaining);
    }

    private static CombatRole parseRole(final String value) {
        for (final CombatRole candidate : CombatRole.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        return CombatRole.NONE;
    }

    private static RouteFailure parseFailure(final String value) {
        for (final RouteFailure candidate : RouteFailure.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        return RouteFailure.NONE;
    }

    private static Optional<BlockPos> readPosition(final CompoundTag tag, final String key) {
        final long stored = tag.getLongOr(key, Long.MIN_VALUE);
        return stored == Long.MIN_VALUE ? Optional.empty() : Optional.of(BlockPos.of(stored));
    }

    private static Optional<Long> readLong(final CompoundTag tag, final String key) {
        final long stored = tag.getLongOr(key, Long.MIN_VALUE);
        return stored == Long.MIN_VALUE ? Optional.empty() : Optional.of(stored);
    }

    private static Optional<String> readDimension(final CompoundTag tag, final String key) {
        final String stored = tag.getStringOr(key, "");
        return stored.isBlank() ? Optional.empty() : Optional.of(stored);
    }

    private static Optional<UUID> readUuid(final CompoundTag tag, final String key) {
        final String stored = tag.getStringOr(key, "");
        if (stored.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(stored));
        } catch (final IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
