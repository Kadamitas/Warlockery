package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.behavior.ScanEnvelope;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The pure decision layer for {@code warlockery:spectral_familiar}.
 *
 * <h2>What this family is, and what it deliberately is not</h2>
 *
 * <p>F23/F25/F26 gave the Familiar Cat, the Owl and the Toad a <em>territorial</em> identity: each
 * claims a home, holds it, hunts inside it and rests in it on a waking clock. The Spectral Familiar
 * has none of that. It claims nothing, hunts nothing, rests nowhere and has no waking window. It is
 * an <em>episodic knowledge guide</em>: it holds one owner-provided ore sample, runs one bounded
 * survey of the loaded blocks around it, approaches a single safe guide point, signals exactly once,
 * returns to its owner's tether band and goes on cooldown. Every rung below exists to make that one
 * episode bounded and provable.</p>
 *
 * <h2>Reuse, stated exactly</h2>
 *
 * <p>Nothing in {@link AnimalFamiliarRules} is copied here. The members written against plain values
 * are <em>called</em>: {@link AnimalFamiliarRules#mayDefendAgainst}, {@link
 * AnimalFamiliarRules#recallRequired}, {@link AnimalFamiliarRules#selectHome}, {@link
 * AnimalFamiliarRules#recordSearch}, {@link AnimalFamiliarRules#mayRoute}, {@link
 * AnimalFamiliarRules#backoffTicks}, {@link AnimalFamiliarRules#saturatingAdd}, {@link
 * AnimalFamiliarRules#clampDeadline} and {@link AnimalFamiliarRules#stableOffset}, together with the
 * constants those depend on. {@code beyondTether} needed one additive overload against a plain
 * radius, because the shipped signature takes an {@code AnimalFamiliarSpecies} and the Spectral
 * Familiar is not one; that widening is recorded in this family's evidence file.
 * {@code selectPrey} is deliberately not called: this familiar has no prey.</p>
 *
 * <h2>No streams, no allocation, in {@link #decide}</h2>
 *
 * <p>{@code decide} runs every tick on every loaded spectral familiar. It allocates nothing: every
 * decision it can return is an interned constant below, and the ladder is a hand-rolled chain. The
 * shared {@code PriorityLadder.select} was declined for the reason four earlier families declined
 * it, and {@link #LADDER} states the same ordering as data so a test can pin it without the
 * per-tick cost.</p>
 */
public final class SpectralFamiliarRules {

    private SpectralFamiliarRules() {
    }

    /** Bumped only when a durable key's meaning changes. A mismatch discards rather than migrates. */
    public static final int STATE_SCHEMA_VERSION = 1;

    // ---- the bounded survey ----

    /** Horizontal half-extent of the survey box. */
    public static final int SURVEY_RADIUS_HORIZONTAL = 5;

    /** Vertical half-extent of the survey box. */
    public static final int SURVEY_RADIUS_VERTICAL = 3;

    /**
     * Block reads one survey may spend. Charged before a position is looked at, never after, so a
     * survey that qualifies nothing costs exactly this and not zero.
     */
    public static final int SURVEY_READ_CAP = 64;

    /** The approved exact cap: at most twelve loaded positions become candidates in one survey. */
    public static final int SURVEY_CANDIDATE_CAP = 12;

    /** Ticks between surveys, armed whether or not the survey qualified anything. */
    public static final int SURVEY_INTERVAL_TICKS = 200;

    /**
     * The centre-out survey box, built once. 11 x 11 x 7 = 847 offsets against a 64-read cap, which
     * is the case {@link ScanEnvelope} exists for: the near anchor is always read, the rotating page
     * covers the tail, and twenty-six successive surveys cover the whole envelope.
     */
    public static final ScanEnvelope SURVEY_ENVELOPE =
        ScanEnvelope.of(SURVEY_RADIUS_HORIZONTAL, SURVEY_RADIUS_VERTICAL);

    // ---- the one guide episode ----

    /** Ticks the approach leg may run before it is abandoned as failed. */
    public static final int APPROACH_DEADLINE_TICKS = 200;

    /** Ticks the single signal occupies. One signal per episode, and this is its whole life. */
    public static final int SIGNAL_TICKS = 20;

    /** Ticks the return leg may run before the familiar gives up and ends the episode anyway. */
    public static final int RETURN_DEADLINE_TICKS = 200;

    /** Squared distance at which the approach leg counts as arrived. */
    public static final double ARRIVAL_DISTANCE_SQUARED = 4.0;

    /** Ticks after an episode ends before another survey may open one. */
    public static final int GUIDE_COOLDOWN_TICKS = 600;

    // ---- tether and drift ----

    /**
     * Squared tether radius. Distinct from all three animal familiars on purpose: the Cat holds 144,
     * the Toad 64 and the Owl 576. A spectral guide ranges further than a household cat and less
     * than a hunting owl.
     */
    public static final double TETHER_RADIUS_SQUARED = 256.0;

    /**
     * Squared distance the return leg must reach before the familiar is back inside the band. Start
     * and stop are different numbers on purpose: one threshold makes a familiar sitting exactly on
     * the boundary flip its decision every tick.
     */
    public static final double TETHER_RELEASE_DISTANCE_SQUARED = 64.0;

    /** Hover offset above the owner's feet, in blocks. This body has no gravity and no footing. */
    public static final double HOVER_HEIGHT = 1.75;

    /** Drift speed handed to the move control. */
    public static final double DRIFT_SPEED = 1.0;

    /** The one signal's visible duration in ticks. Frozen: the 1.4 guidance used the same value. */
    public static final int SIGNAL_GLOW_TICKS = 80;

    /** What the familiar can decide to do. Fourteen fewer rungs than an animal familiar has. */
    public enum Action {
        /** Nothing at all. The rung an unbound familiar sits on: no movement, no target, no aura. */
        IDLE,
        /** Hold station near the owner. This body hovers; it never rests, roosts or curls up. */
        HOVER,
        /** The one bounded intercept of a legal direct attacker of the owner or of itself. */
        DEFEND_OWNER,
        /** Close the distance to a loaded owner that is outside the tether band. */
        TETHER_RETURN,
        /** Run one bounded survey of the loaded blocks for the sampled ore. */
        SURVEY,
        /** Drift toward the single remembered guide point. */
        APPROACH_GUIDE,
        /** Emit the single signal for this episode. */
        SIGNAL_FIND,
        /** The episode's return leg, back to the owner's tether band. */
        RETURN_TO_OWNER
    }

    /** Why the winning rung won. Distinct from the action so two shapes cannot be confused. */
    public enum Reason {
        OWNER_ATTACKED,
        EPISODE_RUNNING,
        BEYOND_TETHER,
        SURVEY_DUE,
        NO_SAMPLE,
        ON_COOLDOWN,
        IN_BACKOFF,
        NOTHING_TO_DO
    }

    /** The episode phase. {@code DORMANT} is the only one without a frozen guide identity. */
    public enum Phase {
        /** No episode in flight. */
        DORMANT,
        /** Drifting to the remembered guide point, under {@link #APPROACH_DEADLINE_TICKS}. */
        APPROACH,
        /** The single signal is in flight, under {@link #SIGNAL_TICKS}. */
        SIGNAL,
        /** Heading back inside the owner's tether band, under {@link #RETURN_DEADLINE_TICKS}. */
        RETURN
    }

    /**
     * One decision. Every value {@link #decide} can return is interned below, so the ladder that
     * runs twenty times a second on every loaded familiar allocates nothing at all.
     */
    public record Decision(Action action, Reason reason) {
        public Decision {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(reason, "reason");
        }
    }

    public static final Decision DEFEND = new Decision(Action.DEFEND_OWNER, Reason.OWNER_ATTACKED);
    public static final Decision APPROACHING =
        new Decision(Action.APPROACH_GUIDE, Reason.EPISODE_RUNNING);
    public static final Decision SIGNALLING =
        new Decision(Action.SIGNAL_FIND, Reason.EPISODE_RUNNING);
    public static final Decision RETURNING =
        new Decision(Action.RETURN_TO_OWNER, Reason.EPISODE_RUNNING);
    public static final Decision TETHERED =
        new Decision(Action.TETHER_RETURN, Reason.BEYOND_TETHER);
    public static final Decision SURVEYING = new Decision(Action.SURVEY, Reason.SURVEY_DUE);
    public static final Decision HOVER_UNSAMPLED = new Decision(Action.HOVER, Reason.NO_SAMPLE);
    public static final Decision HOVER_COOLING = new Decision(Action.HOVER, Reason.ON_COOLDOWN);
    public static final Decision HOVER_BACKED_OFF = new Decision(Action.HOVER, Reason.IN_BACKOFF);
    public static final Decision HOVER_IDLE = new Decision(Action.HOVER, Reason.NOTHING_TO_DO);
    public static final Decision UNBOUND = new Decision(Action.IDLE, Reason.NOTHING_TO_DO);

    /**
     * DECLARED TEST SEAM. The ladder's order, stated once as data.
     *
     * <p>This is the ordering {@code PriorityLadder.select} would have stated, without the list
     * copy, sort and stream it opens per call. {@link #decide} hand-rolls the same order as a chain;
     * {@code SpectralFamiliarRulesTest} walks the full combination space of {@link Facts} and proves
     * the chain and this list agree, which is the equivalence four earlier families had to prove the
     * same way. Nothing in the per-tick path reads this field.
     */
    public static final List<Action> LADDER = List.of(
        Action.DEFEND_OWNER,
        Action.APPROACH_GUIDE,
        Action.SIGNAL_FIND,
        Action.RETURN_TO_OWNER,
        Action.TETHER_RETURN,
        Action.SURVEY,
        Action.HOVER,
        Action.IDLE
    );

    /**
     * DECLARED TEST SEAM. The four actions that steer the move control. Nothing in the per-tick path
     * reads this; {@code SpectralFamiliarRulesTest} uses it to prove that the whole combination
     * space of {@link Facts} never elects two of them, which is the sole-writer property stated as
     * an assertion rather than as a comment.
     */
    public static final Set<Action> MOVEMENT_WRITERS =
        Set.of(Action.TETHER_RETURN, Action.APPROACH_GUIDE, Action.RETURN_TO_OWNER, Action.HOVER);

    /**
     * Everything the ladder is allowed to see.
     *
     * @param ownerLoaded          a bound owner exists and is alive in this level
     * @param ownerDistanceSquared distance to that owner, meaningless when {@code !ownerLoaded}
     * @param ownerUnderAttack     a legal direct attacker of the owner or of this familiar exists
     * @param phase                the episode phase, already advanced by the tick's single exit
     * @param sampleHeld           a sampled block identity is persisted on this familiar
     * @param surveyDue            the bounded survey cadence is due
     * @param guideReady           the post-episode cooldown has elapsed
     * @param surveyBackedOff      three fruitless surveys in a row have earned a backoff window
     */
    public record Facts(
        boolean ownerLoaded,
        double ownerDistanceSquared,
        boolean ownerUnderAttack,
        Phase phase,
        boolean sampleHeld,
        boolean surveyDue,
        boolean guideReady,
        boolean surveyBackedOff
    ) {
        public Facts {
            Objects.requireNonNull(phase, "phase");
        }
    }

    /**
     * The one ladder, hand rolled and allocation free.
     *
     * <p>Order: a legal direct-damage response, then whatever the running episode is doing, then
     * tether correction, then the bounded survey, then hover or idle. The episode outranks the
     * tether deliberately: an episode is bounded by
     * {@link #APPROACH_DEADLINE_TICKS} and its guide point is at most seven blocks away, and the
     * runtime invalidates the whole episode outright if the owner passes the frozen emergency recall
     * distance, so the episode cannot strand the familiar.</p>
     */
    public static Decision decide(final Facts facts) {
        if (facts.ownerUnderAttack()) {
            return DEFEND;
        }
        final Decision episode = switch (facts.phase()) {
            case APPROACH -> APPROACHING;
            case SIGNAL -> SIGNALLING;
            case RETURN -> RETURNING;
            case DORMANT -> null;
        };
        if (episode != null) {
            return episode;
        }
        if (!facts.ownerLoaded()) {
            // An unbound familiar, or one whose owner is not resolvable in this level, does nothing
            // at all: no movement, no target, no aura, and NO SURVEY. Surveying here would open an
            // episode that episodeInvalidated tears down on the very next tick, which would burn a
            // six-hundred-tick guide cooldown per survey forever and would be churn rather than
            // behaviour. The guide cycle exists for an owner; without one there is nothing to guide.
            return UNBOUND;
        }
        if (beyondTether(facts.ownerLoaded(), facts.ownerDistanceSquared())) {
            return TETHERED;
        }
        if (!facts.sampleHeld()) {
            return HOVER_UNSAMPLED;
        }
        if (!facts.guideReady()) {
            return HOVER_COOLING;
        }
        if (facts.surveyBackedOff()) {
            return HOVER_BACKED_OFF;
        }
        if (facts.surveyDue()) {
            return SURVEYING;
        }
        return HOVER_IDLE;
    }

    // ---- shared rules, taken by call rather than by copy ----

    /**
     * Whether the familiar must abandon what it is doing and close on its loaded owner.
     *
     * <p>This is {@link AnimalFamiliarRules#beyondTether(AnimalFamiliarSpecies, boolean, double)}
     * against this family's own radius. The shipped signature takes an {@code AnimalFamiliarSpecies}
     * to look up {@code profile(species).tetherRadiusSquared()}, and the Spectral Familiar is not a
     * member of that enum and must not become one, so the shared file gained one additive overload
     * against a plain radius and its species overload now delegates to it. Recorded as this family's
     * one widening.</p>
     */
    public static boolean beyondTether(final boolean ownerLoaded, final double ownerDistanceSquared) {
        return ownerLoaded
            && Double.isFinite(ownerDistanceSquared)
            && ownerDistanceSquared > TETHER_RADIUS_SQUARED;
    }

    /** Whether the return leg has got the familiar back inside the band. Hysteresis, not a flip. */
    public static boolean insideTetherBand(
        final boolean ownerLoaded,
        final double ownerDistanceSquared
    ) {
        return ownerLoaded && ownerDistanceSquared <= TETHER_RELEASE_DISTANCE_SQUARED;
    }

    /**
     * One survey candidate, named for what this family means by it.
     *
     * <p>The carrier is {@link AnimalFamiliarRules.HomeCandidate}, whose three predicates are the
     * bounded traversal's three filters and are not home specific. The mapping is stated here once,
     * so no call site has to remember it:</p>
     *
     * <ul>
     *   <li>{@code supported} is <em>this block is the sampled block</em></li>
     *   <li>{@code clear} is <em>this block is in the frozen spectral-ore habitat tag</em></li>
     *   <li>{@code speciesQualified} is <em>a safe, visible guide point exists beside it</em></li>
     * </ul>
     *
     * <p>Stated plainly rather than left to be discovered: this family's survey establishes the
     * first of the three <em>before</em> a candidate is built, because the candidate cap must bound
     * the description work rather than the traversal, so from this family {@code supported} always
     * arrives true. The other two still decide, and both reject real candidates here: a sampled
     * block outside the frozen habitat tag and a sampled block with no safe hover point above it are
     * both read, charged, inspected and refused.</p>
     */
    public static AnimalFamiliarRules.HomeCandidate guideCandidate(
        final long packedPosition,
        final double distanceSquared,
        final boolean matchesSample,
        final boolean taggedSpectralOre,
        final boolean guidePointReachable
    ) {
        return new AnimalFamiliarRules.HomeCandidate(
            packedPosition, distanceSquared, matchesSample, taggedSpectralOre, guidePointReachable);
    }

    /**
     * The bounded traversal, at this family's cap. Every candidate is charged the moment it is
     * reached and before any of the three filters can reject it, and the cap bounds inspected
     * candidates rather than qualifying ones.
     */
    public static AnimalFamiliarRules.HomeSelection selectGuideBlock(
        final List<AnimalFamiliarRules.HomeCandidate> candidates
    ) {
        return AnimalFamiliarRules.selectHome(candidates, SURVEY_CANDIDATE_CAP);
    }

    /**
     * Whether a candidate attacker may become this familiar's one defensive target. Delegated whole
     * to {@link AnimalFamiliarRules#mayDefendAgainst}; the legality list is the frozen one and this
     * family adds nothing to it and removes nothing from it.
     */
    public static boolean mayDefendAgainst(
        final Optional<UUID> owner,
        final UUID candidateId,
        final Optional<UUID> candidateOwner,
        final boolean candidateAlive,
        final boolean candidateIsSelf,
        final boolean candidateInvulnerablePlayer,
        final boolean attributedDirectAttack
    ) {
        return AnimalFamiliarRules.mayDefendAgainst(owner, candidateId, candidateOwner,
            candidateAlive, candidateIsSelf, candidateInvulnerablePlayer, attributedDirectAttack);
    }

    /** The frozen 1.4 emergency recall, unchanged distance and no new cadence. */
    public static boolean recallRequired(
        final boolean ownerLoaded,
        final double ownerDistanceSquared
    ) {
        return AnimalFamiliarRules.recallRequired(ownerLoaded, ownerDistanceSquared);
    }

    /**
     * Whether a running episode has stopped being this familiar's episode.
     *
     * <p>Four ways, and all four are identity rather than timing: the owner stopped being resolvable,
     * the owner passed the emergency recall distance, the sample was removed, or the sample was
     * replaced with a different one while the episode was in flight. The fourth is why the episode
     * freezes the sample identity it opened with instead of re-reading it every tick.</p>
     */
    public static boolean episodeInvalidated(
        final boolean ownerLoaded,
        final double ownerDistanceSquared,
        final Optional<String> episodeSample,
        final Optional<String> currentSample
    ) {
        Objects.requireNonNull(episodeSample, "episodeSample");
        Objects.requireNonNull(currentSample, "currentSample");
        return !ownerLoaded
            || recallRequired(true, ownerDistanceSquared)
            || currentSample.isEmpty()
            || !episodeSample.equals(currentSample);
    }

    /**
     * Whether a drift request may be issued now.
     *
     * <p>{@link AnimalFamiliarRules#mayRoute} takes a second window for route-failure backoff. This
     * chassis has no such window and the zero is explicit rather than accidental: it is steered
     * through the move control, which returns nothing and cannot refuse, so there is no per-request
     * failure to count. The failure this family really has is an episode that never reaches its
     * guide point, and that is recorded against the survey's own consecutive-failure count by
     * {@code SpectralFamiliarRuntime.endEpisode}, where three in a row earn the one real backoff.</p>
     */
    public static boolean mayDrift(final long now, final long nextDriftAt) {
        return AnimalFamiliarRules.mayRoute(now, nextDriftAt, 0L);
    }

    /** Ticks between drift requests. Shared with the animal familiars; twenty is twenty. */
    public static final int DRIFT_INTERVAL_TICKS = AnimalFamiliarRules.NAVIGATION_INTERVAL_TICKS;

    /** Consecutive fruitless surveys that earn a backoff window. */
    public static final int MAX_SURVEY_FAILURES = AnimalFamiliarRules.MAX_ROUTE_FAILURES;

    /** The backoff window a given consecutive-failure count has earned. */
    public static int backoffTicks(final int consecutiveFailures) {
        return AnimalFamiliarRules.backoffTicks(consecutiveFailures);
    }

    /** Whether three fruitless surveys in a row are currently holding a backoff window open. */
    public static boolean surveyBackedOff(final long now, final long backoffUntil) {
        return now < backoffUntil;
    }

    /** Closes out one survey, whatever it found: cadence re-armed, failure count recorded. */
    public static AnimalFamiliarRules.SearchOutcome recordSurvey(
        final long now,
        final boolean qualified,
        final int failuresBefore
    ) {
        return AnimalFamiliarRules.recordSearch(
            now, SURVEY_INTERVAL_TICKS, qualified, failuresBefore);
    }

    /**
     * How long the given phase is allowed to run. Zero for {@link Phase#DORMANT}.
     *
     * <p>The single source of every phase deadline, so the exhaustive switch is what a new phase
     * has to satisfy rather than three scattered constants at three call sites.</p>
     */
    public static int phaseDuration(final Phase phase) {
        return switch (phase) {
            case DORMANT -> 0;
            case APPROACH -> APPROACH_DEADLINE_TICKS;
            case SIGNAL -> SIGNAL_TICKS;
            case RETURN -> RETURN_DEADLINE_TICKS;
        };
    }

    static long saturatingAdd(final long base, final long delta) {
        return AnimalFamiliarRules.saturatingAdd(base, delta);
    }

    static long clampDeadline(final long stored, final long now, final long horizon) {
        return AnimalFamiliarRules.clampDeadline(stored, now, horizon);
    }

    static int stableOffset(final UUID identity, final int span) {
        return AnimalFamiliarRules.stableOffset(identity, span);
    }
}
