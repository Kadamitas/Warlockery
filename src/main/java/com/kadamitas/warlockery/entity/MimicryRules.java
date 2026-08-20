package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.behavior.Cadence;
import com.kadamitas.warlockery.entity.behavior.RouteRequest;
import com.kadamitas.warlockery.entity.behavior.Ticks;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The one pure policy shared by all four Warlockery mimics: the three Illusion Copies of F34 and
 * the Glass Doppelganger of F35.
 *
 * <p>This class exists because the four kinds share <em>mechanism</em> and share no motive at all.
 * Every one of them binds exactly one observed subject rather than sweeping an envelope, charges
 * every perception read before a filter may reject it, arms its check cadence even when the check
 * qualifies nothing, gives up on a route after the same number of consecutive failures, reads a
 * zero cooldown as due rather than as recently fired, and refuses an attacker attribution older
 * than the same freshness bound. Writing those contracts once is the whole point: an earlier
 * two-kind family split them across two rules classes, duplicated roughly fifteen members verbatim,
 * and shipped one search defect twice inside a single package.</p>
 *
 * <p>What makes a Hollow Fuse a Hollow Fuse and a Presented Likeness a Presented Likeness is the
 * per-species column in {@link #next(Facts)} plus the per-species constants on {@link Species}, and
 * nothing else. {@link #permits(Species, Act)} partitions the act vocabulary so no species can ever
 * schedule another's act, and {@link #owns(Species, Phase)} partitions the phase vocabulary the
 * same way. Both are asserted directly, and a long deterministic trace per species asserts the four
 * traces differ.</p>
 *
 * <p>No world, entity, level, path, effect or random state may enter this class. Every input is a
 * scalar or an immutable record, so the whole contract is directly unit testable, and
 * {@link #next(Facts)} runs on a server AI step and therefore contains no stream pipeline.</p>
 */
public final class MimicryRules {

    /** Loaded-tick freshness bound on an accepted-damage attribution. Matches F05 and F06. */
    public static final int ATTRIBUTION_FRESHNESS_TICKS = 40;

    /** Every mimic re-checks its surroundings at this cadence, staggered by identity. */
    public static final int CHECK_CADENCE_TICKS = 20;

    /**
     * Raw entities <em>one bounded check</em> may visit, qualified or not. Charged before any
     * filter.
     *
     * <p>Named for the scope it bounds. The per-level, per-tick allowance is
     * {@link Quota#MAX_RAW_VISITS_PER_TICK}, four lines from the call site that reserves against it,
     * and while both were called {@code MAX_RAW_VISITS} the two were trivially confusable.</p>
     */
    public static final int MAX_RAW_VISITS_PER_CHECK = 8;

    /** Line-of-sight walks one bounded check may spend. Sensing caches these per tick. */
    public static final int MAX_SIGHT_RAYS = 2;

    /** A bound subject's sight is re-tested at most this often. */
    public static final int SIGHT_TEST_INTERVAL_TICKS = 10;

    /** Ordinary bounded movement speed for any mimic. */
    public static final double ROUTE_SPEED = 1.0D;

    /** Hazard escape speed. Faster than an ordinary approach, still an ordinary navigation. */
    public static final double ESCAPE_SPEED = 1.2D;

    public static final int PATH_INTERVAL_TICKS = 20;
    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;

    /** The shared escape destination box, expressed as an opt-in centre-out envelope. */
    public static final int ESCAPE_HORIZONTAL_RADIUS = 3;
    public static final int ESCAPE_VERTICAL_RADIUS = 1;
    /**
     * Honest worst-case charged cost of one destination candidate: the chunk presence read, then
     * the block state and fluid state of the candidate and of the block beneath it. Exactly the
     * reads {@code MimicryRuntime.safe} performs, and every one of them is charged, so
     * {@code ReadBudget.spent()} after a sweep is the real number of world reads rather than a
     * per-candidate tally that under-reports what the sweep cost.
     */
    public static final int READS_PER_DESTINATION_CANDIDATE = 8;
    public static final int MAX_DESTINATION_READS = 128;

    /**
     * How many destination candidates one escape sweep may afford. Derived from the two constants
     * above rather than written a second time at the call site, so the sweep window can never be
     * wider than the budget actually pays for.
     */
    public static int destinationCandidateCap() {
        return MAX_DESTINATION_READS / READS_PER_DESTINATION_CANDIDATE;
    }

    /** Representative encoded-state ceiling asserted by the state suite. */
    public static final int MAX_STATE_BYTES = 256;

    public static final int STATE_SCHEMA_VERSION = 1;

    /** Facing agreement required before an observer counts as looking straight at a mimic. */
    public static final double FACING_DOT = 0.85D;

    /** The one shared route failure policy. Third consecutive failure opens the backoff. */
    public static final RouteRequest.RouteBackoff ROUTE_BACKOFF =
        new RouteRequest.RouteBackoff(MAX_ROUTE_FAILURES, ROUTE_BACKOFF_TICKS, ROUTE_BACKOFF_TICKS);

    private MimicryRules() {
    }

    // ---------------------------------------------------------------- identity

    /**
     * The four mimic species. The enum exists so every internal switch is exhaustive over four
     * values with no default arm, rather than over the forty-six values of {@link CreatureKind}.
     *
     * <p>Every field here is a species constant. Nothing on this enum is shared mechanism, and
     * nothing in the rest of this class is a species constant.</p>
     */
    public enum Species {
        /** Illusion Creeper. A hollow fuse that telegraphs a blast it does not own. */
        HOLLOW_FUSE(
            CreatureKind.ILLUSION_CREEPER, Phase.LATENT, Phase.SPENT,
            10.0D, 16.0D, 600, 40, 200, 40
        ),
        /** Illusion Spider. A still shape at a threshold that closes once and lets go. */
        THRESHOLD_WEAVER(
            CreatureKind.ILLUSION_SPIDER, Phase.HIDDEN, Phase.SLACK,
            8.0D, 6.0D, 400, 40, 200, 20
        ),
        /** Illusion Zombie. A plausible body in someone else's line that answers nothing. */
        HOLLOW_DECOY(
            CreatureKind.ILLUSION_ZOMBIE, Phase.BLENDED, Phase.FADED,
            10.0D, 16.0D, 800, 60, 200, 40
        ),
        /** Glass Doppelganger. A presented likeness that shadows one subject until recognised. */
        PRESENTED_LIKENESS(
            CreatureKind.GLASS_DOPPELGANGER, Phase.UNBOUND, Phase.WITHDRAWN,
            16.0D, 24.0D, 1_200, 0, 200, 40
        );

        private final CreatureKind kind;
        private final Phase routine;
        private final Phase spent;
        private final double bindRadius;
        private final double retainRadius;
        private final int primaryCooldownTicks;
        private final int spentTicks;
        private final int episodeBudgetTicks;
        private final int sightGraceTicks;

        Species(
            final CreatureKind kind,
            final Phase routine,
            final Phase spent,
            final double bindRadius,
            final double retainRadius,
            final int primaryCooldownTicks,
            final int spentTicks,
            final int episodeBudgetTicks,
            final int sightGraceTicks
        ) {
            this.kind = kind;
            this.routine = routine;
            this.spent = spent;
            this.bindRadius = bindRadius;
            this.retainRadius = retainRadius;
            this.primaryCooldownTicks = primaryCooldownTicks;
            this.spentTicks = spentTicks;
            this.episodeBudgetTicks = episodeBudgetTicks;
            this.sightGraceTicks = sightGraceTicks;
        }

        public CreatureKind kind() {
            return kind;
        }

        /** The quiet phase a fresh, loaded or fully torn-down mimic of this species sits in. */
        public Phase routine() {
            return routine;
        }

        /** The phase a finished episode leaves behind, where the species perceives nothing. */
        public Phase spent() {
            return spent;
        }

        public double bindRadius() {
            return bindRadius;
        }

        public double bindRadiusSquared() {
            return bindRadius * bindRadius;
        }

        public double retainRadiusSquared() {
            return retainRadius * retainRadius;
        }

        /** Loaded ticks after an episode ends before another may start. */
        public int primaryCooldownTicks() {
            return primaryCooldownTicks;
        }

        /** Loaded ticks the spent phase lasts. Zero means the cooldown alone governs re-entry. */
        public int spentTicks() {
            return spentTicks;
        }

        /** Loaded ticks an episode may run before it ends itself. */
        public int episodeBudgetTicks() {
            return episodeBudgetTicks;
        }

        /** Continuous unseen loaded ticks tolerated before a bound subject is released. */
        public int sightGraceTicks() {
            return sightGraceTicks;
        }
    }

    // ---- Hollow Fuse constants
    public static final double FUSE_COMMIT_DISTANCE_SQUARED = 9.0D;
    public static final int FUSE_TELL_TICKS = 30;
    public static final int FUSE_HOLD_TICKS = 20;
    public static final int FUSE_COLLAPSE_TICKS = 10;
    public static final int FUSE_DISCOVERY_DWELL_TICKS = 20;

    // ---- Threshold Weaver constants
    public static final double WEAVER_INNER_RADIUS = 3.0D;
    public static final double WEAVER_INNER_RADIUS_SQUARED = WEAVER_INNER_RADIUS * WEAVER_INNER_RADIUS;
    public static final int WEAVER_ONSET_TICKS = 40;
    public static final int WEAVER_RESOLVE_TICKS = 10;
    public static final int WEAVER_SNARE_TICKS = 40;
    public static final int WEAVER_BREAK_TICKS = 10;
    /** Exactly the effect the weaver applies, and exactly the instance its guard will remove. */
    public static final int WEAVER_SNARE_AMPLIFIER = 0;
    public static final int WEAVER_SNARE_DURATION_TICKS = 40;

    // ---- Hollow Decoy constants
    public static final double DECOY_ANCHOR_RADIUS_SQUARED = 64.0D;
    public static final double DECOY_ANCHOR_RETAIN_SQUARED = 144.0D;
    public static final double DECOY_STATION_OFFSET = 2.0D;
    /** Distinct station slots around one anchor, so two decoys never claim the same block. */
    public static final int DECOY_STATION_SLOTS = 4;
    public static final double DECOY_STATION_ARRIVAL_SQUARED = 2.25D;
    public static final int DECOY_DRAW_TICKS = 100;
    public static final int DECOY_ABSORB_TICKS = 100;
    public static final int DECOY_ABSORB_REFRESHES = 2;
    public static final int DECOY_DECISIVE_HITS = 2;
    public static final int DECOY_UNMASK_TICKS = 20;

    // ---- Presented Likeness constants
    public static final int LIKENESS_BIND_TICKS = 40;
    public static final int LIKENESS_SETTLE_TICKS = 40;
    public static final int LIKENESS_SHADOW_TICKS = 600;
    public static final int LIKENESS_WITHDRAW_TICKS = 60;
    public static final int LIKENESS_CONFRONT_TICKS = 120;
    public static final int LIKENESS_ATTACK_RECOVERY_TICKS = 40;
    public static final double LIKENESS_BAND_INNER = 6.0D;
    public static final double LIKENESS_BAND_OUTER = 12.0D;
    public static final double LIKENESS_CONTACT_FLOOR = 4.0D;

    public enum LikenessBand { CONTACT, RETREAT, HOLD, APPROACH, OUTER, RELEASED }

    /** Exact distance classification shared by live shadow movement and boundary fixtures. */
    public static LikenessBand likenessBand(final double distance) {
        if (!Double.isFinite(distance) || distance < 0.0D || distance > 24.0D) return LikenessBand.RELEASED;
        if (distance <= LIKENESS_CONTACT_FLOOR) return LikenessBand.CONTACT;
        if (distance < LIKENESS_BAND_INNER) return LikenessBand.RETREAT;
        if (distance <= LIKENESS_BAND_OUTER) return LikenessBand.HOLD;
        if (distance <= Species.PRESENTED_LIKENESS.bindRadius()) return LikenessBand.APPROACH;
        return LikenessBand.OUTER;
    }
    public static final double LIKENESS_OBSERVER_RADIUS_SQUARED = 144.0D;
    public static final int RECOGNITION_MAX = 1_000;
    public static final int RECOGNITION_NOTICED = 200;
    public static final int RECOGNITION_DOUBTED = 600;
    public static final int RECOGNITION_CERTAIN = RECOGNITION_MAX;
    public static final int RECOGNITION_GAIN_WATCHED = 50;
    public static final int RECOGNITION_GAIN_BESIDE_SUBJECT = 500;
    public static final int RECOGNITION_DECAY = 20;

    /** Every phase any mimic may occupy. Disjoint per species apart from {@link #ESCAPE}. */
    public enum Phase {
        /** The single shared phase name. Hazard outranks every band for every species. */
        ESCAPE,
        LATENT, APPROACH, TELL, HOLD, COLLAPSE, SPENT,
        HIDDEN, LURE, RESOLVE, SNARE, BREAK, SLACK,
        BLENDED, STATION, DRAW, ABSORB, UNMASK, FADED,
        UNBOUND, BINDING, PRESENTING, SHADOWING, RECOGNISED, WITHDRAWING, WITHDRAWN, CONFRONT
    }

    /** Every act any mimic may schedule. Disjoint per species apart from the two shared arms. */
    public enum Act {
        IDLE, ESCAPE_HAZARD,
        OBSERVE, APPROACH_OBSERVER, TELEGRAPH, HOLD_STILL, COLLAPSE_QUIETLY,
        THRESHOLD_WATCH, LURE_STILL, RESOLVE_COMMIT, SNARE_HOLD, BREAK_SNARE,
        COMPANION_SCAN, TAKE_STATION, DRAW_ATTENTION, ABSORB_HIT, UNMASK_SELF,
        BIND_SUBJECT, SETTLE_PRESENTATION, HOLD_BAND, WITHDRAW, CONFRONT_ATTACKER
    }

    /** Why the scheduler chose what it chose. Recorded per transition, never persisted. */
    public enum Reason {
        HAZARD, ATTRIBUTED_DAMAGE, DISCOVERED, SUBJECT_ACTED, TIMER, CANDIDATE, RELEASED,
        COOLDOWN, ROUTE_EXHAUSTED, BUDGET, RECOGNITION, ROUTINE
    }

    public static Optional<Species> speciesOf(final CreatureKind kind) {
        if (kind == null) {
            return Optional.empty();
        }
        for (final Species species : Species.values()) {
            if (species.kind() == kind) {
                return Optional.of(species);
            }
        }
        return Optional.empty();
    }

    /**
     * The single identity gate. An act outside a species' own vocabulary is never scheduled and,
     * because the runtime re-checks this before executing, never runs either.
     */
    public static boolean permits(final Species species, final Act act) {
        Objects.requireNonNull(species, "species");
        Objects.requireNonNull(act, "act");
        return switch (act) {
            case IDLE, ESCAPE_HAZARD -> true;
            case OBSERVE, APPROACH_OBSERVER, TELEGRAPH, HOLD_STILL, COLLAPSE_QUIETLY ->
                species == Species.HOLLOW_FUSE;
            case THRESHOLD_WATCH, LURE_STILL, RESOLVE_COMMIT, SNARE_HOLD, BREAK_SNARE ->
                species == Species.THRESHOLD_WEAVER;
            case COMPANION_SCAN, TAKE_STATION, DRAW_ATTENTION, ABSORB_HIT, UNMASK_SELF ->
                species == Species.HOLLOW_DECOY;
            case BIND_SUBJECT, SETTLE_PRESENTATION, HOLD_BAND, WITHDRAW, CONFRONT_ATTACKER ->
                species == Species.PRESENTED_LIKENESS;
        };
    }

    /** The phase half of the same gate. {@link Phase#ESCAPE} is deliberately shared. */
    public static boolean owns(final Species species, final Phase phase) {
        Objects.requireNonNull(species, "species");
        Objects.requireNonNull(phase, "phase");
        return switch (phase) {
            case ESCAPE -> true;
            case LATENT, APPROACH, TELL, HOLD, COLLAPSE, SPENT -> species == Species.HOLLOW_FUSE;
            case HIDDEN, LURE, RESOLVE, SNARE, BREAK, SLACK -> species == Species.THRESHOLD_WEAVER;
            case BLENDED, STATION, DRAW, ABSORB, UNMASK, FADED -> species == Species.HOLLOW_DECOY;
            case UNBOUND, BINDING, PRESENTING, SHADOWING, RECOGNISED, WITHDRAWING, WITHDRAWN,
                 CONFRONT -> species == Species.PRESENTED_LIKENESS;
        };
    }

    /** Whether the phase is an episode phase rather than a routine or hazard one. */
    public static boolean inEpisode(final Species species, final Phase phase) {
        return owns(species, phase) && phase != Phase.ESCAPE
            && phase != species.routine() && phase != species.spent();
    }

    // ---------------------------------------------------------------- clocks

    /**
     * DC1. A stored cooldown of zero means <em>ready</em>, never "fired at world tick 0". Every
     * cadence and cooldown in this family is a remaining loaded-tick count, so no comparison
     * against absolute world time exists anywhere and a mimic created at world tick 0 behaves
     * exactly like one created at world tick 30000.
     */
    public static boolean due(final int remainingTicks) {
        return remainingTicks <= 0;
    }

    public static int decrementLoaded(final int remaining) {
        return Ticks.decrementLoaded(remaining);
    }

    public static int clampRemaining(final int stored, final int maximum) {
        return Ticks.clampRemaining(stored, maximum);
    }

    /** DC6. Deterministic per-identity stagger that never consults absolute world time. */
    public static int stagger(final UUID identity, final int span) {
        return Ticks.stableOffset(identity, span);
    }

    /** DC3. An attribution older than the shared bound may not mint a reaction. */
    public static boolean attributionFresh(final int ageTicks) {
        return ageTicks >= 0 && ageTicks <= ATTRIBUTION_FRESHNESS_TICKS;
    }

    /** The check cadence every species arms after a check <em>ran</em>, qualified or not. */
    public static Cadence checkCadence() {
        return Cadence.every(CHECK_CADENCE_TICKS);
    }

    /** The one route pacing shape. Failure policy is {@link #ROUTE_BACKOFF} for every species. */
    public static RouteRequest routeRequest() {
        return RouteRequest.every(PATH_INTERVAL_TICKS);
    }

    // ---------------------------------------------------------------- perception

    /**
     * One inspected entity reduced to the facts a mimic may order on. Never a live entity.
     *
     * <p>{@code charged} records that the read allowance was spent on this candidate <em>before</em>
     * any eligibility or visibility filter could reject it. A cap that bounds only accepted
     * candidates leaves rejected ones costing real world reads and being charged nothing, which is
     * the defect this record exists to make impossible to write by accident.</p>
     */
    public record Candidate(
        UUID identity,
        boolean eligible,
        boolean visible,
        double distanceSquared,
        boolean charged
    ) {
        public Candidate {
            identity = Objects.requireNonNull(identity, "identity");
            if (!charged) {
                throw new IllegalArgumentException(
                    "every inspected candidate must be charged before it can be filtered"
                );
            }
            if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0D) {
                throw new IllegalArgumentException("candidate distance must be finite and positive");
            }
        }

        public boolean qualifies() {
            return eligible && visible;
        }
    }

    /**
     * The one selector. Nearest first, then the unsigned identity as the sole tie break, so two
     * servers presented with identical facts always bind the identical entity and no outcome
     * depends on entity-section scan order.
     *
     * <p>Written as an explicit loop rather than a stream: this runs from a server AI step.</p>
     */
    public static Optional<Candidate> bind(final List<Candidate> inspected, final double radiusSquared) {
        Objects.requireNonNull(inspected, "inspected");
        Candidate best = null;
        for (int index = 0; index < inspected.size(); index++) {
            final Candidate candidate = inspected.get(index);
            if (!candidate.qualifies() || candidate.distanceSquared() > radiusSquared) {
                continue;
            }
            if (best == null || closer(candidate, best)) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean closer(final Candidate candidate, final Candidate best) {
        if (candidate.distanceSquared() != best.distanceSquared()) {
            return candidate.distanceSquared() < best.distanceSquared();
        }
        return unsignedCompare(candidate.identity(), best.identity()) < 0;
    }

    private static int unsignedCompare(final UUID left, final UUID right) {
        final int high = Long.compareUnsigned(
            left.getMostSignificantBits(), right.getMostSignificantBits()
        );
        return high != 0 ? high
            : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }

    /** Whether an observer's look direction agrees closely enough to count as looking at a mimic. */
    public static boolean facing(final double lookDot) {
        return Double.isFinite(lookDot) && lookDot >= FACING_DOT;
    }

    /** The clamped recognition scalar of the Presented Likeness. Never persisted. */
    public static int recognitionAfter(
        final int current,
        final boolean watched,
        final boolean besideSubject,
        final boolean acceptedHit
    ) {
        if (acceptedHit) {
            return RECOGNITION_CERTAIN;
        }
        final int delta = besideSubject ? RECOGNITION_GAIN_BESIDE_SUBJECT
            : watched ? RECOGNITION_GAIN_WATCHED
            : -RECOGNITION_DECAY;
        return Math.clamp(current + delta, 0, RECOGNITION_MAX);
    }

    // ---------------------------------------------------------------- per-level quota

    /**
     * The per-{@code ServerLevel} per-tick allowance, held as one primitive-only value. It holds no
     * reference to a level, entity, target, item, block, path or queue, so it can never keep any of
     * them alive.
     */
    public record Quota(
        int serverTick,
        int tokens,
        int pathRequests,
        int rawVisits,
        int sightRays,
        int feedback
    ) {
        public static final int MAX_TOKENS = 16;
        public static final int MAX_PATH_REQUESTS = 8;

        /**
         * The per-level, per-tick raw entity visit allowance, and the one cap that bounds real
         * world reads rather than the number of attempts.
         *
         * <p>It must stay <strong>strictly below</strong>
         * {@code MAX_TOKENS * MimicryRules.MAX_RAW_VISITS_PER_CHECK}. A check spends one token and
         * reserves {@link MimicryRules#MAX_RAW_VISITS_PER_CHECK} visits, and the token arm is tested
         * first, so an allowance of 128 against sixteen eight-visit checks put both thresholds on
         * exactly the same check and the visit arm could never be the one that denied. The member
         * was reachable and unfalsifiable, which is a worse place to be than unreachable. At 64 a
         * level affords eight scanning checks per tick, and checks nine to sixteen hold a token and
         * are turned away here.</p>
         */
        public static final int MAX_RAW_VISITS_PER_TICK = 64;
        public static final int MAX_SIGHT_RAYS = 32;
        public static final int MAX_FEEDBACK = 8;

        public static Quota fresh(final int serverTick) {
            return new Quota(serverTick, 0, 0, 0, 0, 0);
        }

        /** The record resets on the server tick counter, never on mutable world game time. */
        public Quota forServerTick(final int currentServerTick) {
            return currentServerTick == serverTick ? this : fresh(currentServerTick);
        }

        public boolean tokenAvailable() {
            return tokens < MAX_TOKENS;
        }

        public Quota spendToken() {
            return tokenAvailable() ? new Quota(
                serverTick, tokens + 1, pathRequests, rawVisits, sightRays, feedback
            ) : this;
        }

        public boolean pathAvailable() {
            return pathRequests < MAX_PATH_REQUESTS;
        }

        public Quota spendPath() {
            return pathAvailable() ? new Quota(
                serverTick, tokens, pathRequests + 1, rawVisits, sightRays, feedback
            ) : this;
        }

        public boolean visitsAvailable(final int count) {
            return rawVisits + Math.max(0, count) <= MAX_RAW_VISITS_PER_TICK;
        }

        public Quota spendVisits(final int count) {
            return visitsAvailable(count) ? new Quota(
                serverTick, tokens, pathRequests, rawVisits + Math.max(0, count), sightRays, feedback
            ) : this;
        }

        public boolean rayAvailable() {
            return sightRays < MAX_SIGHT_RAYS;
        }

        public Quota spendRay() {
            return rayAvailable() ? new Quota(
                serverTick, tokens, pathRequests, rawVisits, sightRays + 1, feedback
            ) : this;
        }

        public boolean feedbackAvailable() {
            return feedback < MAX_FEEDBACK;
        }

        public Quota spendFeedback() {
            return feedbackAvailable() ? new Quota(
                serverTick, tokens, pathRequests, rawVisits, sightRays, feedback + 1
            ) : this;
        }
    }

    // ---------------------------------------------------------------- the scheduler

    /**
     * Everything the scheduler is allowed to know, for every species. One shape, because four
     * shapes is how the same field drifts four ways.
     *
     * @param species              the mimic asking
     * @param phase                its current phase
     * @param phaseTicks           loaded ticks already spent in that phase
     * @param episodeTicks         loaded ticks already spent in the whole episode
     * @param hazard               a live fire, lava or contact hazard flag
     * @param boundPresent         a bound subject resolves to a legal, loaded, living entity
     * @param boundVisible         that subject passed the most recent sight test
     * @param boundDistanceSquared squared distance to that subject
     * @param freshAttribution     an accepted-damage attribution within the DC3 bound
     * @param acceptedHits         accepted hits taken inside the current episode
     * @param facingDwellTicks     continuous loaded ticks the bound observer has faced the mimic
     * @param recognition          the Presented Likeness recognition scalar, zero for the others
     * @param primaryCooldown      remaining loaded ticks before another episode may start
     * @param candidateFound       the due check qualified a candidate this tick
     * @param routeFailures        consecutive route failures already recorded
     * @param subjectActed         the bound subject committed a deliberate act within the DC3 bound
     */
    public record Facts(
        Species species,
        Phase phase,
        int phaseTicks,
        int episodeTicks,
        boolean hazard,
        boolean boundPresent,
        boolean boundVisible,
        double boundDistanceSquared,
        boolean freshAttribution,
        int acceptedHits,
        int facingDwellTicks,
        int recognition,
        int primaryCooldown,
        boolean candidateFound,
        int routeFailures,
        boolean subjectActed
    ) {
        public Facts {
            species = Objects.requireNonNull(species, "species");
            phase = Objects.requireNonNull(phase, "phase");
            if (!owns(species, phase)) {
                throw new IllegalArgumentException(
                    species + " cannot occupy the phase " + phase + " of another species"
                );
            }
            if (!Double.isFinite(boundDistanceSquared) || boundDistanceSquared < 0.0D) {
                throw new IllegalArgumentException("bound distance must be finite and positive");
            }
            phaseTicks = Math.max(0, phaseTicks);
            episodeTicks = Math.max(0, episodeTicks);
            acceptedHits = Math.max(0, acceptedHits);
            facingDwellTicks = Math.max(0, facingDwellTicks);
            recognition = Math.clamp(recognition, 0, RECOGNITION_MAX);
            primaryCooldown = Math.max(0, primaryCooldown);
            routeFailures = Math.clamp(routeFailures, 0, MAX_ROUTE_FAILURES);
        }
    }

    /** What the mimic should do this tick, the phase it should be in, and why. */
    public record Decision(Optional<Act> act, Phase phase, Reason reason) {
        public Decision {
            act = Objects.requireNonNull(act, "act");
            phase = Objects.requireNonNull(phase, "phase");
            reason = Objects.requireNonNull(reason, "reason");
        }

        static Decision of(final Act act, final Phase phase, final Reason reason) {
            return new Decision(Optional.of(act), phase, reason);
        }

        static Decision refused(final Phase phase, final Reason reason) {
            return new Decision(Optional.empty(), phase, reason);
        }
    }

    /**
     * The one scheduler. The shared priority ladder is written once here, and only the third band
     * differs by species. Hazard outranks combat, combat outranks the episode, and the episode
     * outranks routine; a higher band always tears the lower one down before it writes navigation.
     */
    public static Decision next(final Facts facts) {
        Objects.requireNonNull(facts, "facts");
        if (facts.hazard()) {
            return Decision.of(Act.ESCAPE_HAZARD, Phase.ESCAPE, Reason.HAZARD);
        }
        if (facts.phase() == Phase.ESCAPE) {
            return Decision.refused(facts.species().routine(), Reason.RELEASED);
        }
        return switch (facts.species()) {
            case HOLLOW_FUSE -> hollowFuse(facts);
            case THRESHOLD_WEAVER -> thresholdWeaver(facts);
            case HOLLOW_DECOY -> hollowDecoy(facts);
            case PRESENTED_LIKENESS -> presentedLikeness(facts);
        };
    }

    /**
     * Illusion Creeper. Recognition ends the deception: sustained facing with sight, or one
     * accepted hit, collapses the episode from any phase, and the collapse carries no payload.
     */
    private static Decision hollowFuse(final Facts facts) {
        final boolean discovered = facts.freshAttribution()
            || (facts.boundVisible() && facts.facingDwellTicks() >= FUSE_DISCOVERY_DWELL_TICKS);
        return switch (facts.phase()) {
            case LATENT -> {
                if (!due(facts.primaryCooldown())) {
                    yield Decision.refused(Phase.LATENT, Reason.COOLDOWN);
                }
                yield facts.candidateFound()
                    ? Decision.of(Act.APPROACH_OBSERVER, Phase.APPROACH, Reason.CANDIDATE)
                    : Decision.of(Act.OBSERVE, Phase.LATENT, Reason.ROUTINE);
            }
            case APPROACH -> {
                if (discovered) {
                    yield Decision.of(Act.COLLAPSE_QUIETLY, Phase.COLLAPSE, Reason.DISCOVERED);
                }
                if (!facts.boundPresent()) {
                    yield Decision.of(Act.COLLAPSE_QUIETLY, Phase.COLLAPSE, Reason.RELEASED);
                }
                if (facts.routeFailures() >= MAX_ROUTE_FAILURES) {
                    yield Decision.of(Act.COLLAPSE_QUIETLY, Phase.COLLAPSE, Reason.ROUTE_EXHAUSTED);
                }
                if (facts.episodeTicks() >= facts.species().episodeBudgetTicks()) {
                    yield Decision.of(Act.COLLAPSE_QUIETLY, Phase.COLLAPSE, Reason.BUDGET);
                }
                yield facts.boundDistanceSquared() <= FUSE_COMMIT_DISTANCE_SQUARED
                    ? Decision.of(Act.TELEGRAPH, Phase.TELL, Reason.TIMER)
                    : Decision.of(Act.APPROACH_OBSERVER, Phase.APPROACH, Reason.CANDIDATE);
            }
            case TELL -> {
                if (discovered) {
                    yield Decision.of(Act.COLLAPSE_QUIETLY, Phase.COLLAPSE, Reason.DISCOVERED);
                }
                yield facts.phaseTicks() >= FUSE_TELL_TICKS
                    ? Decision.of(Act.HOLD_STILL, Phase.HOLD, Reason.TIMER)
                    : Decision.of(Act.TELEGRAPH, Phase.TELL, Reason.TIMER);
            }
            case HOLD -> {
                if (discovered) {
                    yield Decision.of(Act.COLLAPSE_QUIETLY, Phase.COLLAPSE, Reason.DISCOVERED);
                }
                yield facts.phaseTicks() >= FUSE_HOLD_TICKS
                    ? Decision.of(Act.COLLAPSE_QUIETLY, Phase.COLLAPSE, Reason.TIMER)
                    : Decision.of(Act.HOLD_STILL, Phase.HOLD, Reason.TIMER);
            }
            case COLLAPSE -> facts.phaseTicks() >= FUSE_COLLAPSE_TICKS
                ? Decision.of(Act.IDLE, Phase.SPENT, Reason.TIMER)
                : Decision.of(Act.COLLAPSE_QUIETLY, Phase.COLLAPSE, Reason.TIMER);
            case SPENT -> facts.phaseTicks() >= facts.species().spentTicks()
                ? Decision.refused(Phase.LATENT, Reason.TIMER)
                : Decision.of(Act.IDLE, Phase.SPENT, Reason.ROUTINE);
            default -> throw new IllegalStateException("unreachable fuse phase " + facts.phase());
        };
    }

    /**
     * Illusion Spider. It never approaches and never pursues. It waits at a threshold, closes once
     * on a subject that crosses the inner radius, and lets go the moment the subject acts.
     */
    private static Decision thresholdWeaver(final Facts facts) {
        return switch (facts.phase()) {
            case HIDDEN -> {
                if (!due(facts.primaryCooldown())) {
                    yield Decision.refused(Phase.HIDDEN, Reason.COOLDOWN);
                }
                yield facts.candidateFound()
                    ? Decision.of(Act.LURE_STILL, Phase.LURE, Reason.CANDIDATE)
                    : Decision.of(Act.THRESHOLD_WATCH, Phase.HIDDEN, Reason.ROUTINE);
            }
            case LURE -> {
                if (facts.freshAttribution()) {
                    yield Decision.of(Act.BREAK_SNARE, Phase.BREAK, Reason.ATTRIBUTED_DAMAGE);
                }
                if (!facts.boundPresent()
                    || facts.boundDistanceSquared() > facts.species().bindRadiusSquared()) {
                    // Released before anything was applied, so no cooldown is consumed.
                    yield Decision.refused(Phase.HIDDEN, Reason.RELEASED);
                }
                if (facts.episodeTicks() >= facts.species().episodeBudgetTicks()) {
                    yield Decision.refused(Phase.HIDDEN, Reason.BUDGET);
                }
                final boolean crossed = facts.boundVisible()
                    && facts.boundDistanceSquared() <= WEAVER_INNER_RADIUS_SQUARED;
                yield crossed && facts.phaseTicks() >= WEAVER_ONSET_TICKS
                    ? Decision.of(Act.RESOLVE_COMMIT, Phase.RESOLVE, Reason.CANDIDATE)
                    : Decision.of(Act.LURE_STILL, Phase.LURE, Reason.TIMER);
            }
            case RESOLVE -> {
                if (facts.freshAttribution()) {
                    yield Decision.of(Act.BREAK_SNARE, Phase.BREAK, Reason.ATTRIBUTED_DAMAGE);
                }
                if (facts.phaseTicks() < WEAVER_RESOLVE_TICKS) {
                    yield Decision.of(Act.RESOLVE_COMMIT, Phase.RESOLVE, Reason.TIMER);
                }
                final boolean committable = facts.boundPresent() && facts.boundVisible()
                    && facts.boundDistanceSquared() <= WEAVER_INNER_RADIUS_SQUARED;
                // A commit that qualifies nothing still consumes the cooldown, so the weaver
                // cannot re-attempt on the very next tick forever.
                yield committable
                    ? Decision.of(Act.SNARE_HOLD, Phase.SNARE, Reason.CANDIDATE)
                    : Decision.of(Act.IDLE, Phase.SLACK, Reason.RELEASED);
            }
            case SNARE -> {
                if (facts.freshAttribution()) {
                    yield Decision.of(Act.BREAK_SNARE, Phase.BREAK, Reason.ATTRIBUTED_DAMAGE);
                }
                if (facts.subjectActed()) {
                    yield Decision.of(Act.BREAK_SNARE, Phase.BREAK, Reason.SUBJECT_ACTED);
                }
                if (!facts.boundPresent()
                    || facts.boundDistanceSquared() > facts.species().retainRadiusSquared()) {
                    yield Decision.of(Act.BREAK_SNARE, Phase.BREAK, Reason.RELEASED);
                }
                yield facts.phaseTicks() >= WEAVER_SNARE_TICKS
                    ? Decision.of(Act.BREAK_SNARE, Phase.BREAK, Reason.TIMER)
                    : Decision.of(Act.SNARE_HOLD, Phase.SNARE, Reason.TIMER);
            }
            case BREAK -> facts.phaseTicks() >= WEAVER_BREAK_TICKS
                ? Decision.of(Act.IDLE, Phase.SLACK, Reason.TIMER)
                : Decision.of(Act.BREAK_SNARE, Phase.BREAK, Reason.TIMER);
            case SLACK -> facts.phaseTicks() >= facts.species().spentTicks()
                ? Decision.refused(Phase.HIDDEN, Reason.TIMER)
                : Decision.of(Act.IDLE, Phase.SLACK, Reason.ROUTINE);
            default -> throw new IllegalStateException("unreachable weaver phase " + facts.phase());
        };
    }

    /**
     * Illusion Zombie. It stands where a hostile mob already stands, draws one observer's attention,
     * and answers a hit with nothing at all. The second accepted hit unmasks it.
     */
    private static Decision hollowDecoy(final Facts facts) {
        if (facts.freshAttribution() && facts.phase() != Phase.ABSORB
            && facts.phase() != Phase.UNMASK && facts.phase() != Phase.FADED) {
            return Decision.of(Act.ABSORB_HIT, Phase.ABSORB, Reason.ATTRIBUTED_DAMAGE);
        }
        return switch (facts.phase()) {
            case BLENDED -> {
                if (!due(facts.primaryCooldown())) {
                    yield Decision.refused(Phase.BLENDED, Reason.COOLDOWN);
                }
                if (!facts.candidateFound()) {
                    yield Decision.of(Act.COMPANION_SCAN, Phase.BLENDED, Reason.ROUTINE);
                }
                yield facts.boundDistanceSquared() <= DECOY_ANCHOR_RADIUS_SQUARED
                    ? Decision.of(Act.TAKE_STATION, Phase.STATION, Reason.CANDIDATE)
                    : Decision.of(Act.DRAW_ATTENTION, Phase.DRAW, Reason.CANDIDATE);
            }
            case STATION -> {
                if (!facts.boundPresent()
                    || facts.boundDistanceSquared() > DECOY_ANCHOR_RETAIN_SQUARED) {
                    yield Decision.refused(Phase.BLENDED, Reason.RELEASED);
                }
                if (facts.routeFailures() >= MAX_ROUTE_FAILURES) {
                    yield Decision.refused(Phase.BLENDED, Reason.ROUTE_EXHAUSTED);
                }
                yield facts.episodeTicks() >= facts.species().episodeBudgetTicks()
                    ? Decision.of(Act.DRAW_ATTENTION, Phase.DRAW, Reason.BUDGET)
                    : Decision.of(Act.TAKE_STATION, Phase.STATION, Reason.TIMER);
            }
            case DRAW -> {
                if (!facts.boundPresent() || facts.phaseTicks() >= DECOY_DRAW_TICKS) {
                    // Released with no cooldown consumed: drawing an eye costs the decoy nothing.
                    yield Decision.refused(Phase.BLENDED, Reason.RELEASED);
                }
                yield Decision.of(Act.DRAW_ATTENTION, Phase.DRAW, Reason.TIMER);
            }
            case ABSORB -> {
                if (facts.acceptedHits() >= DECOY_DECISIVE_HITS) {
                    yield Decision.of(Act.UNMASK_SELF, Phase.UNMASK, Reason.ATTRIBUTED_DAMAGE);
                }
                yield facts.phaseTicks() >= DECOY_ABSORB_TICKS
                    ? Decision.of(Act.UNMASK_SELF, Phase.UNMASK, Reason.TIMER)
                    : Decision.of(Act.ABSORB_HIT, Phase.ABSORB, Reason.TIMER);
            }
            case UNMASK -> facts.phaseTicks() >= DECOY_UNMASK_TICKS
                ? Decision.of(Act.IDLE, Phase.FADED, Reason.TIMER)
                : Decision.of(Act.UNMASK_SELF, Phase.UNMASK, Reason.TIMER);
            case FADED -> facts.phaseTicks() >= facts.species().spentTicks()
                ? Decision.refused(Phase.BLENDED, Reason.TIMER)
                : Decision.of(Act.IDLE, Phase.FADED, Reason.ROUTINE);
            default -> throw new IllegalStateException("unreachable decoy phase " + facts.phase());
        };
    }

    /**
     * Glass Doppelganger. It presents one bound subject's name and manner from a deliberate band,
     * and it is the only mimic that ever answers a hit with an ordinary attack.
     */
    private static Decision presentedLikeness(final Facts facts) {
        if (facts.freshAttribution() && facts.phase() != Phase.CONFRONT) {
            return Decision.of(Act.CONFRONT_ATTACKER, Phase.CONFRONT, Reason.ATTRIBUTED_DAMAGE);
        }
        return switch (facts.phase()) {
            case UNBOUND -> {
                if (!due(facts.primaryCooldown())) {
                    yield Decision.refused(Phase.UNBOUND, Reason.COOLDOWN);
                }
                yield facts.candidateFound()
                    ? Decision.of(Act.BIND_SUBJECT, Phase.BINDING, Reason.CANDIDATE)
                    : Decision.of(Act.IDLE, Phase.UNBOUND, Reason.ROUTINE);
            }
            case BINDING -> {
                if (!facts.boundPresent() || !facts.boundVisible()
                    || facts.boundDistanceSquared() > facts.species().bindRadiusSquared()) {
                    yield Decision.refused(Phase.UNBOUND, Reason.RELEASED);
                }
                yield facts.phaseTicks() >= LIKENESS_BIND_TICKS
                    ? Decision.of(Act.SETTLE_PRESENTATION, Phase.PRESENTING, Reason.TIMER)
                    : Decision.of(Act.BIND_SUBJECT, Phase.BINDING, Reason.TIMER);
            }
            case PRESENTING -> {
                if (!facts.boundPresent()
                    || facts.boundDistanceSquared() > facts.species().retainRadiusSquared()) {
                    yield Decision.refused(Phase.UNBOUND, Reason.RELEASED);
                }
                yield facts.phaseTicks() >= LIKENESS_SETTLE_TICKS
                    ? Decision.of(Act.HOLD_BAND, Phase.SHADOWING, Reason.TIMER)
                    : Decision.of(Act.SETTLE_PRESENTATION, Phase.PRESENTING, Reason.TIMER);
            }
            case SHADOWING -> {
                if (facts.recognition() >= RECOGNITION_CERTAIN) {
                    yield Decision.of(Act.WITHDRAW, Phase.RECOGNISED, Reason.RECOGNITION);
                }
                if (!facts.boundPresent()
                    || facts.boundDistanceSquared() > facts.species().retainRadiusSquared()) {
                    yield Decision.of(Act.WITHDRAW, Phase.RECOGNISED, Reason.RELEASED);
                }
                if (facts.routeFailures() >= MAX_ROUTE_FAILURES) {
                    yield Decision.of(Act.WITHDRAW, Phase.RECOGNISED, Reason.ROUTE_EXHAUSTED);
                }
                yield facts.phaseTicks() >= LIKENESS_SHADOW_TICKS
                    ? Decision.of(Act.WITHDRAW, Phase.RECOGNISED, Reason.TIMER)
                    : Decision.of(Act.HOLD_BAND, Phase.SHADOWING, Reason.TIMER);
            }
            case RECOGNISED -> Decision.of(Act.WITHDRAW, Phase.WITHDRAWING, Reason.RECOGNITION);
            case WITHDRAWING -> facts.phaseTicks() >= LIKENESS_WITHDRAW_TICKS
                || facts.routeFailures() >= MAX_ROUTE_FAILURES
                ? Decision.of(Act.IDLE, Phase.WITHDRAWN, Reason.TIMER)
                : Decision.of(Act.WITHDRAW, Phase.WITHDRAWING, Reason.TIMER);
            case WITHDRAWN -> due(facts.primaryCooldown())
                ? Decision.refused(Phase.UNBOUND, Reason.TIMER)
                : Decision.of(Act.IDLE, Phase.WITHDRAWN, Reason.COOLDOWN);
            case CONFRONT -> {
                if (!facts.boundPresent()
                    || facts.boundDistanceSquared() > facts.species().retainRadiusSquared()
                    || facts.phaseTicks() >= LIKENESS_CONFRONT_TICKS) {
                    yield Decision.of(Act.IDLE, Phase.WITHDRAWN, Reason.RELEASED);
                }
                yield Decision.of(Act.CONFRONT_ATTACKER, Phase.CONFRONT, Reason.TIMER);
            }
            default -> throw new IllegalStateException("unreachable likeness phase " + facts.phase());
        };
    }
}

