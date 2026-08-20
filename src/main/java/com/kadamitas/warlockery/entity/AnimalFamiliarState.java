package com.kadamitas.warlockery.entity;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * One versioned durable state for all three bound animal familiars.
 *
 * <p>There is deliberately not a {@code FamiliarCatState}, an {@code OwlState} and a
 * {@code ToadState}. Every field below except {@link #signature()} means the same thing for a cat,
 * an owl and a toad, and three parallel records is exactly how a previous two-entity family shipped
 * the same normalisation defect twice. What is genuinely per-species is the sealed
 * {@link Signature} payload, and it holds only facts the other two species do not have.</p>
 *
 * <h2>Constructor reconciliation, classified</h2>
 *
 * <p>Every reconciliation in the compact constructor is the <em>identity</em> shape: a dependent
 * field is meaningless without the identity it hangs off, so the two cannot be allowed to disagree.
 * A home position without its dimension is not a home; a running phase without a frozen target is
 * not a phase; a defence lease without a defender is not a lease; a landmark dimension without a
 * landmark is noise.</p>
 *
 * <p>There is no <em>timer</em> reconciliation anywhere, and that absence is load bearing. This
 * constructor never says "the deadline passed, therefore the phase ended". Deciding that a phase
 * ended is a tick branch's job, because ending a phase also arms a cooldown, releases a target and
 * records an epoch; a constructor that quietly zeroed the phase would skip all three. Expiry is
 * only ever <em>reported</em>, by {@link #phaseElapsed(long)}, {@link #defenceElapsed(long)} and
 * {@link #signatureOffCooldown(long)}, and {@code AnimalFamiliarRuntime.advanceLoadedPhases} is the
 * single place that acts on those reports.</p>
 */
public record AnimalFamiliarState(
    int schemaVersion,
    AnimalFamiliarSpecies species,
    Optional<BlockPos> home,
    Optional<String> homeDimension,
    Phase phase,
    Optional<UUID> phaseTargetId,
    long phaseEndsAt,
    long signatureCooldownUntil,
    Optional<UUID> defenceTargetId,
    long defenceLeaseUntil,
    long defenceCooldownUntil,
    AnimalFamiliarRules.SearchOutcome homeSearch,
    AnimalFamiliarRules.SearchOutcome preySearch,
    long nextNavigationAt,
    long routeBackoffUntil,
    int routeFailures,
    long actionEpoch,
    Signature signature
) {

    /** The signature action's phase. Shared shape; the action it belongs to is per-species. */
    public enum Phase {
        /** No signature action in flight. */
        NONE,
        /** The visible wind-up: crouch, listen, crouch. Nothing has committed yet. */
        TELEGRAPH,
        /** The one committed approach and single ordinary melee opportunity. */
        COMMIT
    }

    /**
     * The only per-species durable data. Each arm holds a fact the other two species genuinely do
     * not have, which is the test for whether something belongs here rather than above.
     */
    public sealed interface Signature {

        /** Which species this payload belongs to. Guards a corrupt or migrated pairing. */
        AnimalFamiliarSpecies species();

        /**
         * Cat: which of the deterministic patrol points around the claimed household is next.
         * An owl has no patrol ring and a toad hops to a landmark, not around a territory.
         */
        record Territory(int patrolIndex) implements Signature {
            public Territory {
                patrolIndex = Math.floorMod(patrolIndex, PATROL_POINTS);
            }

            @Override
            public AnimalFamiliarSpecies species() {
                return AnimalFamiliarSpecies.CAT;
            }

            public Territory advanced() {
                return new Territory(patrolIndex + 1);
            }
        }

        /**
         * Owl: consecutive missed pounces. An owl that keeps missing from this perch stops
         * launching and holds its watch; a cat that misses simply walks home and a toad that
         * misses hops back under its shelter, so neither needs the counter.
         */
        record Hunt(int consecutiveMisses) implements Signature {
            public Hunt {
                consecutiveMisses = Math.clamp(consecutiveMisses, 0, MAX_TRACKED_MISSES);
            }

            @Override
            public AnimalFamiliarSpecies species() {
                return AnimalFamiliarSpecies.OWL;
            }

            public Hunt missed() {
                return new Hunt(consecutiveMisses + 1);
            }

            public Hunt connected() {
                return new Hunt(0);
            }

            public boolean discouraged() {
                return consecutiveMisses >= MAX_TRACKED_MISSES;
            }
        }

        /**
         * Toad: the one retained herb landmark its forage envelope is anchored to. A cat forages
         * nowhere and an owl drops from a perch, so neither has a landmark.
         */
        record Forage(Optional<BlockPos> landmark, Optional<String> landmarkDimension)
            implements Signature {
            public Forage {
                // Identity coupling: a landmark position without its dimension is not a landmark,
                // and a dimension with nothing in it is noise.
                landmark = Objects.requireNonNull(landmark, "landmark").map(BlockPos::immutable);
                landmarkDimension = Objects.requireNonNull(landmarkDimension, "landmarkDimension");
                if (landmark.isEmpty()) {
                    landmarkDimension = Optional.empty();
                }
                if (landmarkDimension.isEmpty()) {
                    landmark = Optional.empty();
                }
            }

            @Override
            public AnimalFamiliarSpecies species() {
                return AnimalFamiliarSpecies.TOAD;
            }
        }
    }

    /** How many deterministic patrol points a claimed cat household has. */
    public static final int PATROL_POINTS = 4;
    /** The owl's discouragement threshold. */
    public static final int MAX_TRACKED_MISSES = 3;

    private static final String KEY_VERSION = "Version";
    private static final String KEY_SPECIES = "Species";
    private static final String KEY_HOME = "Home";
    private static final String KEY_HOME_DIMENSION = "HomeDimension";
    private static final String KEY_PHASE = "Phase";
    private static final String KEY_PHASE_TARGET = "PhaseTarget";
    private static final String KEY_PHASE_ENDS_AT = "PhaseEndsAt";
    private static final String KEY_SIGNATURE_COOLDOWN = "SignatureCooldownUntil";
    private static final String KEY_DEFENCE_TARGET = "DefenceTarget";
    private static final String KEY_DEFENCE_LEASE = "DefenceLeaseUntil";
    private static final String KEY_DEFENCE_COOLDOWN = "DefenceCooldownUntil";
    private static final String KEY_NEXT_HOME_SEARCH = "NextHomeSearchAt";
    private static final String KEY_NEXT_PREY_SEARCH = "NextPreySearchAt";
    private static final String KEY_PREY_SEARCH_FAILURES = "PreySearchFailures";
    private static final String KEY_NEXT_NAVIGATION = "NextNavigationAt";
    private static final String KEY_ROUTE_BACKOFF = "RouteBackoffUntil";
    private static final String KEY_ROUTE_FAILURES = "RouteFailures";
    private static final String KEY_HOME_SEARCH_FAILURES = "HomeSearchFailures";
    private static final String KEY_ACTION_EPOCH = "ActionEpoch";
    private static final String KEY_PATROL_INDEX = "PatrolIndex";
    private static final String KEY_MISSES = "ConsecutiveMisses";
    private static final String KEY_LANDMARK = "Landmark";
    private static final String KEY_LANDMARK_DIMENSION = "LandmarkDimension";

    public AnimalFamiliarState {
        species = Objects.requireNonNull(species, "species");
        home = Objects.requireNonNull(home, "home").map(BlockPos::immutable);
        homeDimension = Objects.requireNonNull(homeDimension, "homeDimension");
        phase = Objects.requireNonNull(phase, "phase");
        phaseTargetId = Objects.requireNonNull(phaseTargetId, "phaseTargetId");
        defenceTargetId = Objects.requireNonNull(defenceTargetId, "defenceTargetId");
        signature = Objects.requireNonNull(signature, "signature");
        homeSearch = Objects.requireNonNull(homeSearch, "homeSearch");
        preySearch = Objects.requireNonNull(preySearch, "preySearch");
        routeFailures = Math.clamp(routeFailures, 0, AnimalFamiliarRules.MAX_ROUTE_FAILURES);

        // Identity: a position without its dimension is not a place.
        if (home.isEmpty()) {
            homeDimension = Optional.empty();
        }
        if (homeDimension.isEmpty()) {
            home = Optional.empty();
        }
        // Identity: a running phase is a phase *against something*. Note what this is not: it does
        // not consult phaseEndsAt at all, so a phase whose deadline has passed is still running
        // here and the tick branch that owns ending it still gets to run.
        if (phase != Phase.NONE && phaseTargetId.isEmpty()) {
            phase = Phase.NONE;
        }
        if (phase == Phase.NONE) {
            phaseTargetId = Optional.empty();
            phaseEndsAt = 0L;
        }
        // Identity: a lease with no defender is not a lease.
        if (defenceTargetId.isEmpty()) {
            defenceLeaseUntil = 0L;
        }
        // Identity: the payload must belong to this species, or it is not this familiar's payload.
        if (signature.species() != species) {
            signature = emptySignature(species);
        }
    }

    public static Signature emptySignature(final AnimalFamiliarSpecies species) {
        return switch (species) {
            case CAT -> new Signature.Territory(0);
            case OWL -> new Signature.Hunt(0);
            case TOAD -> new Signature.Forage(Optional.empty(), Optional.empty());
        };
    }

    public static AnimalFamiliarState empty(
        final AnimalFamiliarSpecies species,
        final UUID identity,
        final long now
    ) {
        final AnimalFamiliarRules.Profile profile = AnimalFamiliarRules.profile(species);
        final long base = Math.max(0L, now);
        return new AnimalFamiliarState(
            AnimalFamiliarRules.STATE_SCHEMA_VERSION,
            species,
            Optional.empty(),
            Optional.empty(),
            Phase.NONE,
            Optional.empty(),
            0L,
            0L,
            Optional.empty(),
            0L,
            0L,
            new AnimalFamiliarRules.SearchOutcome(
                base + AnimalFamiliarRules.stableOffset(identity, profile.homeSearchIntervalTicks()), 0),
            new AnimalFamiliarRules.SearchOutcome(
                base + AnimalFamiliarRules.stableOffset(identity, profile.signatureCooldownTicks()), 0),
            0L,
            0L,
            0,
            0L,
            emptySignature(species)
        );
    }

    // ---- reports. Nothing here ends anything; the runtime decides that. ----

    /** True when a running phase's deadline has passed. Reported only. */
    public boolean phaseElapsed(final long now) {
        return phase != Phase.NONE && now >= phaseEndsAt;
    }

    /** True when a held defence lease has run out. Reported only. */
    public boolean defenceElapsed(final long now) {
        return defenceTargetId.isPresent() && now >= defenceLeaseUntil;
    }

    /** True when the one-defence-per-window gate has reopened. Reported only. */
    public boolean defenceReady(final long now) {
        return now >= defenceCooldownUntil;
    }

    public boolean signatureOffCooldown(final long now) {
        return now >= signatureCooldownUntil;
    }

    public boolean homeSearchDue(final long now) {
        return now >= homeSearch.nextDueAt();
    }

    public boolean preySearchDue(final long now) {
        return now >= preySearch.nextDueAt();
    }

    // ---- withers ----

    public AnimalFamiliarState withHome(final Optional<BlockPos> position, final Optional<String> dimension) {
        return new AnimalFamiliarState(schemaVersion, species, position, dimension, phase, phaseTargetId,
            phaseEndsAt, signatureCooldownUntil, defenceTargetId, defenceLeaseUntil, defenceCooldownUntil, homeSearch, preySearch,
            nextNavigationAt, routeBackoffUntil, routeFailures, actionEpoch, signature);
    }

    public AnimalFamiliarState withPhase(
        final Phase nextPhase,
        final Optional<UUID> target,
        final long endsAt
    ) {
        return new AnimalFamiliarState(schemaVersion, species, home, homeDimension, nextPhase, target,
            endsAt, signatureCooldownUntil, defenceTargetId, defenceLeaseUntil, defenceCooldownUntil, homeSearch, preySearch,
            nextNavigationAt, routeBackoffUntil, routeFailures, actionEpoch, signature);
    }

    public AnimalFamiliarState withSignatureCooldown(final long until) {
        return new AnimalFamiliarState(schemaVersion, species, home, homeDimension, phase, phaseTargetId,
            phaseEndsAt, until, defenceTargetId, defenceLeaseUntil, defenceCooldownUntil, homeSearch, preySearch,
            nextNavigationAt, routeBackoffUntil, routeFailures, actionEpoch, signature);
    }

    public AnimalFamiliarState withDefence(final Optional<UUID> target, final long until) {
        return new AnimalFamiliarState(schemaVersion, species, home, homeDimension, phase, phaseTargetId,
            phaseEndsAt, signatureCooldownUntil, target, until, defenceCooldownUntil, homeSearch, preySearch,
            nextNavigationAt, routeBackoffUntil, routeFailures, actionEpoch, signature);
    }

    public AnimalFamiliarState withDefenceCooldown(final long until) {
        return new AnimalFamiliarState(schemaVersion, species, home, homeDimension, phase, phaseTargetId,
            phaseEndsAt, signatureCooldownUntil, defenceTargetId, defenceLeaseUntil, until, homeSearch,
            preySearch, nextNavigationAt, routeBackoffUntil, routeFailures, actionEpoch, signature);
    }

    public AnimalFamiliarState withHomeSearch(final AnimalFamiliarRules.SearchOutcome outcome) {
        return new AnimalFamiliarState(schemaVersion, species, home, homeDimension, phase, phaseTargetId,
            phaseEndsAt, signatureCooldownUntil, defenceTargetId, defenceLeaseUntil, defenceCooldownUntil, outcome, preySearch,
            nextNavigationAt, routeBackoffUntil, routeFailures, actionEpoch, signature);
    }

    public AnimalFamiliarState withPreySearch(final AnimalFamiliarRules.SearchOutcome outcome) {
        return new AnimalFamiliarState(schemaVersion, species, home, homeDimension, phase, phaseTargetId,
            phaseEndsAt, signatureCooldownUntil, defenceTargetId, defenceLeaseUntil, defenceCooldownUntil, homeSearch, outcome,
            nextNavigationAt, routeBackoffUntil, routeFailures, actionEpoch, signature);
    }

    public AnimalFamiliarState withRoute(
        final long nextNavigationTime,
        final long backoffUntil,
        final int failures
    ) {
        return new AnimalFamiliarState(schemaVersion, species, home, homeDimension, phase, phaseTargetId,
            phaseEndsAt, signatureCooldownUntil, defenceTargetId, defenceLeaseUntil, defenceCooldownUntil, homeSearch, preySearch,
            nextNavigationTime, backoffUntil, failures, actionEpoch, signature);
    }

    public AnimalFamiliarState withActionEpoch(final long epoch) {
        return new AnimalFamiliarState(schemaVersion, species, home, homeDimension, phase, phaseTargetId,
            phaseEndsAt, signatureCooldownUntil, defenceTargetId, defenceLeaseUntil, defenceCooldownUntil, homeSearch, preySearch,
            nextNavigationAt, routeBackoffUntil, routeFailures, epoch, signature);
    }

    public AnimalFamiliarState withSignature(final Signature payload) {
        return new AnimalFamiliarState(schemaVersion, species, home, homeDimension, phase, phaseTargetId,
            phaseEndsAt, signatureCooldownUntil, defenceTargetId, defenceLeaseUntil, defenceCooldownUntil, homeSearch, preySearch,
            nextNavigationAt, routeBackoffUntil, routeFailures, actionEpoch, payload);
    }

    // ---- persistence ----

    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_VERSION, schemaVersion);
        tag.putString(KEY_SPECIES, species.name().toLowerCase(Locale.ROOT));
        home.ifPresent(position -> tag.putLong(KEY_HOME, position.asLong()));
        homeDimension.ifPresent(dimension -> tag.putString(KEY_HOME_DIMENSION, dimension));
        tag.putString(KEY_PHASE, phase.name().toLowerCase(Locale.ROOT));
        phaseTargetId.ifPresent(id -> tag.putString(KEY_PHASE_TARGET, id.toString()));
        tag.putLong(KEY_PHASE_ENDS_AT, phaseEndsAt);
        tag.putLong(KEY_SIGNATURE_COOLDOWN, signatureCooldownUntil);
        defenceTargetId.ifPresent(id -> tag.putString(KEY_DEFENCE_TARGET, id.toString()));
        tag.putLong(KEY_DEFENCE_LEASE, defenceLeaseUntil);
        tag.putLong(KEY_DEFENCE_COOLDOWN, defenceCooldownUntil);
        tag.putLong(KEY_NEXT_HOME_SEARCH, homeSearch.nextDueAt());
        tag.putInt(KEY_HOME_SEARCH_FAILURES, homeSearch.consecutiveFailures());
        tag.putLong(KEY_NEXT_PREY_SEARCH, preySearch.nextDueAt());
        tag.putInt(KEY_PREY_SEARCH_FAILURES, preySearch.consecutiveFailures());
        tag.putLong(KEY_NEXT_NAVIGATION, nextNavigationAt);
        tag.putLong(KEY_ROUTE_BACKOFF, routeBackoffUntil);
        tag.putInt(KEY_ROUTE_FAILURES, routeFailures);
        tag.putLong(KEY_ACTION_EPOCH, actionEpoch);
        switch (signature) {
            case Signature.Territory(final int patrolIndex) -> tag.putInt(KEY_PATROL_INDEX, patrolIndex);
            case Signature.Hunt(final int consecutiveMisses) -> tag.putInt(KEY_MISSES, consecutiveMisses);
            case Signature.Forage(final var landmark, final var landmarkDimension) -> {
                landmark.ifPresent(position -> tag.putLong(KEY_LANDMARK, position.asLong()));
                landmarkDimension.ifPresent(dimension -> tag.putString(KEY_LANDMARK_DIMENSION, dimension));
            }
        }
        return tag;
    }

    /**
     * Load. Clamps every number against the current clock, drops any malformed identity together
     * with the fields that depended on it, cancels an in-flight signature phase so no attack can
     * replay across the reload seam, releases the defence lease, and clears the route so a stale
     * path cannot resume. It performs no attack, aura pulse, home claim or catch-up loop merely
     * because time elapsed while the chunk was unloaded.
     */
    public static AnimalFamiliarState read(
        final CompoundTag tag,
        final AnimalFamiliarSpecies species,
        final UUID identity,
        final long now
    ) {
        if (tag.getIntOr(KEY_VERSION, 0) != AnimalFamiliarRules.STATE_SCHEMA_VERSION) {
            return empty(species, identity, now);
        }
        if (!species.name().toLowerCase(Locale.ROOT).equals(tag.getStringOr(KEY_SPECIES, ""))) {
            // A payload written by a different species is not a migration, it is corruption.
            return empty(species, identity, now);
        }
        final Optional<BlockPos> home = readPosition(tag, KEY_HOME);
        final Optional<String> homeDimension = readText(tag, KEY_HOME_DIMENSION);
        final AnimalFamiliarRules.Profile profile = AnimalFamiliarRules.profile(species);
        final long base = Math.max(0L, now);
        // Both cadences restagger from the mixing hash rather than resuming a stored due time, so a
        // familiar that unloads more often than one interval cannot starve either search and a
        // crowd that reloads together does not pulse in lockstep. The two failure counts DO survive
        // the seam, deliberately: an episode boundary resets accumulators but must preserve an open
        // backoff window, and these counts are what the backoff window is computed from.
        final AnimalFamiliarRules.SearchOutcome homeSearch = new AnimalFamiliarRules.SearchOutcome(
            base + AnimalFamiliarRules.stableOffset(identity, profile.homeSearchIntervalTicks()),
            Math.clamp(tag.getIntOr(KEY_HOME_SEARCH_FAILURES, 0), 0, AnimalFamiliarRules.MAX_ROUTE_FAILURES)
        );
        final AnimalFamiliarRules.SearchOutcome preySearch = new AnimalFamiliarRules.SearchOutcome(
            base + AnimalFamiliarRules.stableOffset(identity, profile.signatureCooldownTicks()),
            Math.clamp(tag.getIntOr(KEY_PREY_SEARCH_FAILURES, 0), 0, AnimalFamiliarRules.MAX_ROUTE_FAILURES)
        );
        return new AnimalFamiliarState(
            AnimalFamiliarRules.STATE_SCHEMA_VERSION,
            species,
            home,
            homeDimension,
            Phase.NONE,
            Optional.empty(),
            0L,
            AnimalFamiliarRules.clampDeadline(
                tag.getLongOr(KEY_SIGNATURE_COOLDOWN, 0L), now, profile.signatureCooldownTicks()),
            Optional.empty(),
            0L,
            AnimalFamiliarRules.clampDeadline(
                tag.getLongOr(KEY_DEFENCE_COOLDOWN, 0L), now, AnimalFamiliarRules.DEFENSE_LEASE_TICKS),
            homeSearch,
            preySearch,
            0L,
            AnimalFamiliarRules.clampDeadline(
                tag.getLongOr(KEY_ROUTE_BACKOFF, 0L), now, AnimalFamiliarRules.ROUTE_BACKOFF_TICKS),
            0,
            Math.max(0L, tag.getLongOr(KEY_ACTION_EPOCH, 0L)),
            readSignature(tag, species)
        );
    }

    private static Signature readSignature(final CompoundTag tag, final AnimalFamiliarSpecies species) {
        return switch (species) {
            case CAT -> new Signature.Territory(tag.getIntOr(KEY_PATROL_INDEX, 0));
            case OWL -> new Signature.Hunt(tag.getIntOr(KEY_MISSES, 0));
            case TOAD -> new Signature.Forage(
                readPosition(tag, KEY_LANDMARK),
                readText(tag, KEY_LANDMARK_DIMENSION)
            );
        };
    }

    private static Optional<BlockPos> readPosition(final CompoundTag tag, final String key) {
        return tag.contains(key)
            ? Optional.of(BlockPos.of(tag.getLongOr(key, 0L)))
            : Optional.empty();
    }

    private static Optional<String> readText(final CompoundTag tag, final String key) {
        final String value = tag.getStringOr(key, "");
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }
}
