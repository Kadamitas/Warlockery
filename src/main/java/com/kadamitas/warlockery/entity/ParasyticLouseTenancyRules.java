package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.behavior.RouteRequest;
import java.util.Optional;

/**
 * Pure F31 tenancy policy. Every constant, inclusive boundary, clamp, eligibility rule, transition
 * predicate, nourishment step, payload ceiling and redirect gate lives here, and nothing here can
 * touch a {@code Level}, mutate an entity, request a path, read a clock or allocate a mutable
 * collection.
 *
 * <p>Named {@code ParasyticLouseTenancyRules} rather than {@code ParasyticLouseRules} because
 * {@link com.kadamitas.warlockery.item.ParasyticLouseRules} already exists and its pure
 * {@code target} contract is a protected surface this family does not touch.</p>
 *
 * <h2>Determinism constraints this class enforces</h2>
 *
 * <p><strong>Remaining ticks, never stamps.</strong> Every scalar below is a count of remaining
 * <em>loaded</em> ticks where zero means due. No comparison against absolute world time exists in
 * this family at all, so a louse placed at world tick 0 and one placed at world tick 30000 behave
 * identically and unloaded time performs no catch-up. The one deliberate exception is stated and
 * bounded: {@link #seekCooldownOnLoad} floors the reloaded cooldown at a constant so a reload cannot
 * immediately restart a tenancy.</p>
 *
 * <p><strong>Attribution carries its own freshness bound.</strong> {@link #attributionFresh} is the
 * mod's own 40-tick window, matching the value already committed as
 * {@code EldritchWatcherRules.ATTRIBUTION_FRESHNESS_TICKS}, {@code HexBatRules}, {@code ImpLifeRules}
 * and {@code HellhoundLifeRules}, so the redirect stops depending on an unread vanilla expiry.</p>
 */
public final class ParasyticLouseTenancyRules {

    // ---------------------------------------------------------------- the host scan

    /** At most one host scan per this many loaded ticks, staggered per entity. */
    public static final int SCAN_CADENCE_TICKS = 40;
    /** Host scan reach, as an inflation of the louse's own bounding box. */
    public static final double SCAN_RADIUS = 8.0D;
    /** Raw entity visits one scan may make, charged before any filter can reject a candidate. */
    public static final int MAX_SCAN_VISITS = 6;
    /** Sight traces one scan may spend. A scan that cannot see its first two yields nothing. */
    public static final int MAX_SCAN_SIGHT_RAYS = 2;

    // ---------------------------------------------------------------- the tenancy

    public static final double MARK_ENTRY_RANGE_SQUARED = 9.0D;
    public static final int MARK_TICKS = 30;
    public static final int MAX_MARK_PARTICLES = 8;
    public static final double ATTACH_RANGE_SQUARED = 4.0D;
    public static final int MAX_OCCUPANCY_VISITS = 4;
    public static final double OCCUPANCY_PROBE_INFLATION = 1.0D;
    public static final int RESIDENCE_TERM_TICKS = 1200;
    public static final double RETENTION_RANGE_SQUARED = 256.0D;
    public static final int SIGHT_CHECK_CADENCE_TICKS = 10;
    public static final int SIGHT_LOSS_RELEASE_TICKS = 60;
    public static final int EVICT_CADENCE_TICKS = 20;

    // ---------------------------------------------------------------- the feed

    public static final int FEED_CADENCE_TICKS = 40;
    public static final double FEED_RANGE_SQUARED = 2.25D;
    public static final int MAX_NOURISHMENT = 4;
    public static final int NOURISHMENT_DECAY_TICKS = 400;

    // ---------------------------------------------------------------- the payload

    /** The single ceiling every delivery route shares. */
    public static final int PAYLOAD_CEILING_TICKS = 600;
    public static final int REDIRECT_CADENCE_TICKS = 40;
    public static final double REDIRECT_RANGE_SQUARED = 256.0D;
    public static final int ATTRIBUTION_FRESHNESS_TICKS = 40;

    // ---------------------------------------------------------------- disengagement

    public static final int WITHDRAWAL_TICKS = 60;
    public static final int SEEK_COOLDOWN_TICKS = 600;
    public static final int LOAD_SEEK_COOLDOWN_FLOOR_TICKS = 200;
    public static final int RELEASED_HOST_GRACE_TICKS = 600;

    // ---------------------------------------------------------------- movement and hazard

    public static final int PATH_CADENCE_TICKS = 20;
    public static final double SEEK_SPEED = 1.2D;
    public static final double ESCAPE_SPEED = 1.35D;
    public static final int HAZARD_CADENCE_TICKS = 20;
    /** Charged block reads one hazard observation may spend over the contact neighbourhood. */
    public static final int MAX_HAZARD_READS = 18;
    public static final int ESCAPE_HORIZONTAL_RADIUS = 3;
    public static final int ESCAPE_VERTICAL_RADIUS = 1;
    /** Escape candidates one search may evaluate, each charged before it may be rejected. */
    public static final int MAX_ESCAPE_CANDIDATES = 16;
    public static final int ROUTE_FAILURES_BEFORE_BACKOFF = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;

    /**
     * Three consecutive failures open a backoff of at least a hundred loaded ticks, and the geometric
     * growth is clamped there too, so a louse in permanently unusable terrain neither spins nor
     * escalates without bound.
     */
    public static final RouteRequest.RouteBackoff ROUTE_BACKOFF = new RouteRequest.RouteBackoff(
        ROUTE_FAILURES_BEFORE_BACKOFF, ROUTE_BACKOFF_TICKS, ROUTE_BACKOFF_TICKS
    );

    private ParasyticLouseTenancyRules() {
    }

    /**
     * The tenancy states. {@code ATTACH}, {@code SATED}, {@code EVICT} and {@code DETACH} are
     * deliberately absent: each is a single-pass transition executed to completion inside one tick
     * by the runtime method that owns it, never a state a later tick can observe. Making them
     * observable phases would let a tick boundary fall between an eviction decision and its unwind,
     * which is exactly the half-cancelled tenancy this family must not have.
     */
    public enum Phase {
        FREE,
        SEEK,
        MARK,
        FEED,
        ESCAPE
    }

    /** The closed reason set. Every ending is one of these and every one of these is counted. */
    public enum EvictReason {
        SATED,
        TERM_EXPIRED,
        ATTACKED,
        GROOMED,
        IMMERSED,
        HOST_DEAD,
        HOST_REMOVED,
        HOST_ILLEGAL,
        HOST_UNLOADED,
        HOST_DIMENSION,
        HOST_OUT_OF_RETENTION,
        HOST_SIGHT_LOST,
        HOST_SLEEPING,
        HOST_TRADING,
        HOST_BREEDING,
        HOST_RAID,
        HOST_PANIC,
        OCCUPIED,
        ROUTE_FAILED,
        HAZARD,
        CANCELLED
    }

    /** Which route delivered a payload. A payload has exactly one delivery across both. */
    public enum DeliveryRoute {
        SATIATION,
        REDIRECT
    }

    /** Why one redirect evaluation delivered nothing. Every rejection is counted by reason. */
    public enum RedirectRejection {
        NO_PAYLOAD,
        NO_OWNER,
        OWNER_OUT_OF_RANGE,
        NO_ARMOR,
        NO_ATTACKER,
        ATTACKER_OUT_OF_RANGE,
        STALE_ATTRIBUTION,
        NO_SIGHT
    }

    /**
     * Everything the policy is allowed to know about one candidate or bound host. Deliberately no
     * entity, no level and no position: an eligibility decision that could reach the world could
     * make an unbudgeted read.
     */
    public record HostFacts(
        boolean alive,
        boolean sameLevel,
        boolean sameDimension,
        boolean self,
        boolean anotherLouse,
        boolean owner,
        boolean inGrace,
        boolean diseaseImmune,
        boolean creativeOrSpectator,
        boolean sleeping,
        boolean trading,
        boolean breeding,
        boolean raiding,
        boolean panicking,
        double distanceSquared
    ) {
    }

    /** What retention needs beyond {@link HostFacts}: the term, sight history and route ledger. */
    public record TenancyFacts(
        boolean hostResolved,
        int residenceRemainingTicks,
        int continuousSightLossTicks,
        int consecutiveRouteFailures
    ) {
    }

    /** Everything the redirect gate is allowed to know. */
    public record RedirectFacts(
        boolean payloadPresent,
        boolean ownerResolved,
        double ownerDistanceSquared,
        boolean redirectingArmor,
        boolean attackerResolved,
        double attackerDistanceSquared,
        int attributionAgeTicks,
        boolean attackerSighted
    ) {
    }

    // ---------------------------------------------------------------- acquisition

    /**
     * The acquisition gate, evaluated exactly twice per tenancy: once when the candidate is chosen
     * and once again at the {@code MARK} commit, never per tick.
     *
     * <p>It is a deny list over the already populated {@code warlockery:disease_immune} tag plus an
     * explicit closed set of positive conditions. A missing tag therefore denies nothing but cannot
     * silently turn the whole gate into allow-everything, which is the known failure mode of a
     * whitelist-shaped eligibility check.</p>
     */
    public static boolean eligibleHost(final HostFacts facts) {
        return facts.alive()
            && facts.sameLevel()
            && facts.sameDimension()
            && !facts.self()
            && !facts.anotherLouse()
            && !facts.owner()
            && !facts.inGrace()
            && !facts.diseaseImmune()
            && !facts.creativeOrSpectator()
            && !facts.sleeping()
            && !facts.trading()
            && !facts.breeding()
            && !facts.raiding()
            && !facts.panicking();
    }

    /** Whether a candidate is close enough and visible enough for the mark telegraph to open. */
    public static boolean markOpens(final double distanceSquared, final boolean sighted) {
        return sighted && distanceSquared <= MARK_ENTRY_RANGE_SQUARED;
    }

    /**
     * Whether the elapsed telegraph may commit. A second unobstructed sight test is required here
     * because bounding-box membership alone must never mint a commit.
     */
    public static boolean attachCommits(
        final double distanceSquared,
        final boolean sighted,
        final boolean hazard
    ) {
        return !hazard && sighted && distanceSquared <= ATTACH_RANGE_SQUARED;
    }

    /** A candidate that merely drifted out of the commit band resumes closing rather than ending. */
    public static boolean markLapses(final double distanceSquared, final boolean sighted) {
        return !sighted || distanceSquared > ATTACH_RANGE_SQUARED;
    }

    // ---------------------------------------------------------------- retention

    /**
     * The single retention decision. Ordered so the cheapest and most absolute reasons answer first,
     * and total: any tenancy state that is no longer legal produces exactly one named reason.
     */
    public static Optional<EvictReason> evictReason(
        final HostFacts facts,
        final TenancyFacts tenancy
    ) {
        if (!tenancy.hostResolved()) {
            return Optional.of(EvictReason.HOST_UNLOADED);
        }
        if (!facts.alive()) {
            return Optional.of(EvictReason.HOST_DEAD);
        }
        if (!facts.sameLevel()) {
            return Optional.of(EvictReason.HOST_REMOVED);
        }
        if (!facts.sameDimension()) {
            return Optional.of(EvictReason.HOST_DIMENSION);
        }
        if (tenancy.residenceRemainingTicks() <= 0) {
            return Optional.of(EvictReason.TERM_EXPIRED);
        }
        if (facts.distanceSquared() > RETENTION_RANGE_SQUARED) {
            return Optional.of(EvictReason.HOST_OUT_OF_RETENTION);
        }
        if (tenancy.continuousSightLossTicks() >= SIGHT_LOSS_RELEASE_TICKS) {
            return Optional.of(EvictReason.HOST_SIGHT_LOST);
        }
        if (facts.sleeping()) {
            return Optional.of(EvictReason.HOST_SLEEPING);
        }
        if (facts.trading()) {
            return Optional.of(EvictReason.HOST_TRADING);
        }
        if (facts.breeding()) {
            return Optional.of(EvictReason.HOST_BREEDING);
        }
        if (facts.raiding()) {
            return Optional.of(EvictReason.HOST_RAID);
        }
        if (facts.panicking()) {
            return Optional.of(EvictReason.HOST_PANIC);
        }
        if (facts.creativeOrSpectator() || facts.diseaseImmune() || facts.anotherLouse()
            || facts.owner()) {
            return Optional.of(EvictReason.HOST_ILLEGAL);
        }
        if (tenancy.consecutiveRouteFailures() >= ROUTE_FAILURES_BEFORE_BACKOFF) {
            return Optional.of(EvictReason.ROUTE_FAILED);
        }
        return Optional.empty();
    }

    /** A SEEK candidate is retained on the same rules, minus the term it has not yet started. */
    public static Optional<EvictReason> candidateReleaseReason(
        final HostFacts facts,
        final TenancyFacts tenancy
    ) {
        return evictReason(facts, new TenancyFacts(
            tenancy.hostResolved(),
            RESIDENCE_TERM_TICKS,
            tenancy.continuousSightLossTicks(),
            tenancy.consecutiveRouteFailures()
        ));
    }

    // ---------------------------------------------------------------- the feed

    public static boolean feedAllowed(final double distanceSquared, final boolean hazard) {
        return !hazard && distanceSquared <= FEED_RANGE_SQUARED;
    }

    /**
     * A truthy melee call with no effective positive loss is not a feed. Absorbed, cancelled and
     * Forge-zeroed damage all land here and raise nothing.
     */
    public static boolean effectiveFeed(
        final boolean hurt,
        final float before,
        final float after
    ) {
        return hurt && after < before;
    }

    /** The ladder clamps before anything else is done with the value. Never five. */
    public static int nourishmentAfter(final int current, final boolean fed) {
        return Math.clamp(fed ? current + 1 : current, 0, MAX_NOURISHMENT);
    }

    public static boolean satiated(final int nourishment) {
        return nourishment >= MAX_NOURISHMENT;
    }

    // ---------------------------------------------------------------- the payload

    /** The one ceiling, applied identically on the satiation route and the redirect route. */
    public static int payloadDuration(final int storedTicks) {
        return Math.min(PAYLOAD_CEILING_TICKS, Math.max(1, storedTicks));
    }

    public static boolean payloadClamped(final int storedTicks) {
        return storedTicks > PAYLOAD_CEILING_TICKS;
    }

    /** Inclusive on both ends: zero and forty are fresh, forty one is not, negative is not. */
    public static boolean attributionFresh(final int ageTicks) {
        return ageTicks >= 0 && ageTicks <= ATTRIBUTION_FRESHNESS_TICKS;
    }

    /**
     * The redirect gate. Ordered so the reason reported is the first thing that actually failed,
     * which is what the rejection counters have to mean for a fixture to distinguish a missing boot
     * from a stale attribution.
     */
    public static Optional<RedirectRejection> redirectRejection(final RedirectFacts facts) {
        if (!facts.payloadPresent()) {
            return Optional.of(RedirectRejection.NO_PAYLOAD);
        }
        if (!facts.ownerResolved()) {
            return Optional.of(RedirectRejection.NO_OWNER);
        }
        if (facts.ownerDistanceSquared() > REDIRECT_RANGE_SQUARED) {
            return Optional.of(RedirectRejection.OWNER_OUT_OF_RANGE);
        }
        if (!facts.redirectingArmor()) {
            return Optional.of(RedirectRejection.NO_ARMOR);
        }
        if (!facts.attackerResolved()) {
            return Optional.of(RedirectRejection.NO_ATTACKER);
        }
        if (facts.attackerDistanceSquared() > REDIRECT_RANGE_SQUARED) {
            return Optional.of(RedirectRejection.ATTACKER_OUT_OF_RANGE);
        }
        if (!attributionFresh(facts.attributionAgeTicks())) {
            return Optional.of(RedirectRejection.STALE_ATTRIBUTION);
        }
        if (!facts.attackerSighted()) {
            return Optional.of(RedirectRejection.NO_SIGHT);
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- bands and lifecycle

    /** Hazard preempts every state including a running tenancy, and is the only band that may. */
    public static boolean hazardPreempts(final boolean hazardActive) {
        return hazardActive;
    }

    /**
     * The one deliberate departure from pure remaining-tick arithmetic, and it is a constant rather
     * than a clock read: a louse that unloads while attached wakes free, so without this floor the
     * cycle of unload and reload would restart its residence term indefinitely.
     */
    public static int seekCooldownOnLoad(final int stored) {
        return Math.max(Math.clamp(stored, 0, SEEK_COOLDOWN_TICKS), LOAD_SEEK_COOLDOWN_FLOOR_TICKS);
    }

    /** A louse may begin a tenancy only from FREE, only off cooldown and only outside a withdrawal. */
    public static boolean tenancyMayStart(
        final Phase phase,
        final int seekCooldownRemaining,
        final int withdrawalRemaining
    ) {
        return phase == Phase.FREE && seekCooldownRemaining <= 0 && withdrawalRemaining <= 0;
    }
}
