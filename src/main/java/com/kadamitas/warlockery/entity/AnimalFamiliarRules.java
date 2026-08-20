package com.kadamitas.warlockery.entity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Every decision the three bound animal familiars share, stated exactly once, plus the per-species
 * tuning table that is the whole of their difference.
 *
 * <p>Pure: no Minecraft world, entity, level, navigation or block object crosses this boundary.
 * Callers pass immutable facts and receive one intent. This is what stops the copy-paste defect the
 * coordinator called out -- a previous two-entity family put its two kinds in two rules classes,
 * duplicated roughly fifteen members verbatim and shipped one search defect twice. Here the ladder,
 * the candidate selection, the budget discipline, the route backoff and the state normalisation
 * exist once and all three species run the same code.</p>
 *
 * <h2>What is genuinely shared</h2>
 *
 * <p>{@link #decide(Facts)} is one ladder for all three. The species never changes the
 * <em>order</em>; it changes which vocabulary word each rung emits and which preconditions the
 * rung's facts had to satisfy. {@link #selectPrey(List, int)} and {@link #selectHome(List, int)} are
 * one traversal each, charged before any filter can reject.</p>
 *
 * <h2>What is identity</h2>
 *
 * <p>{@link Profile} is the tuning table: active window, tether, caps, cooldowns, telegraphs and the
 * three vocabulary words. {@link #permits(AnimalFamiliarSpecies, Action)} is the gate, and
 * {@code AnimalFamiliarRulesTest} proves the three action vocabularies intersect in exactly the
 * five shared rungs.</p>
 */
public final class AnimalFamiliarRules {

    // ---- persistence ----

    public static final int STATE_SCHEMA_VERSION = 1;
    /** Deadlines further out than this are treated as corrupt and clamped back to the horizon. */
    public static final long FAR_FUTURE_TICKS = 20_000L;

    // ---- shared routing discipline ----

    /** A navigation request is issued at most this often, whichever rung won. */
    public static final int NAVIGATION_INTERVAL_TICKS = 20;
    /** Three consecutive failures release the destination. Shared by all three species. */
    public static final int MAX_ROUTE_FAILURES = 3;
    /** The release window after the third failure. */
    public static final int ROUTE_BACKOFF_TICKS = 100;
    /** Owner-aura pulse period. Frozen: the 1.4 companion aura pulsed every 20 ticks. */
    public static final int AURA_PULSE_INTERVAL_TICKS = 20;
    /** A defensive lease is one bounded intercept, never a standing target. */
    public static final int DEFENSE_LEASE_TICKS = 100;
    /** The emergency owner recall distance, frozen from {@code CreatureBehaviorRules}. */
    public static final double OWNER_RECALL_DISTANCE_SQUARED = 1_024.0;

    // ---- shared schedule ----

    public static final long NIGHT_START_TIME = 13_000L;
    public static final long NIGHT_END_TIME = 23_000L;

    private static final Set<Action> SHARED_ACTIONS = Set.of(
        Action.IDLE,
        Action.DEFEND_OWNER,
        Action.TETHER_RETURN,
        Action.HOME_SEARCH,
        Action.HOME_RETURN
    );

    private static final Profile CAT_PROFILE = new Profile(
        AnimalFamiliarSpecies.CAT,
        ActiveWindow.DAYLIGHT,
        144.0,
        Action.PATROL_TERRITORY,
        Action.STALK_VERMIN,
        Action.CURL_AT_HOME,
        5,
        2,
        66,
        12,
        0,
        30,
        400,
        300
    );
    private static final Profile OWL_PROFILE = new Profile(
        AnimalFamiliarSpecies.OWL,
        ActiveWindow.NIGHT,
        576.0,
        Action.GLIDE_SURVEY,
        Action.POUNCE_PREY,
        Action.ROOST_WATCH,
        5,
        3,
        66,
        8,
        4,
        16,
        300,
        200
    );
    private static final Profile TOAD_PROFILE = new Profile(
        AnimalFamiliarSpecies.TOAD,
        ActiveWindow.NIGHT_OR_RAIN,
        64.0,
        Action.HOP_TO_LANDMARK,
        Action.FORAGE_INSECT,
        Action.SHELTER_REST,
        5,
        2,
        66,
        8,
        4,
        12,
        500,
        400
    );

    private AnimalFamiliarRules() {
    }

    // ---- vocabulary ----

    /**
     * The complete action vocabulary of the family. The first five rungs are shared by all three
     * species; the remaining nine are three disjoint triples, one per species.
     */
    public enum Action {
        /** Nothing to do this decision. Shared. */
        IDLE,
        /** One bounded intercept of a direct, legal attacker of the owner. Shared. */
        DEFEND_OWNER,
        /** The owner is outside this species' tether; close the distance. Shared. */
        TETHER_RETURN,
        /** A bounded, budgeted search for a home this species can hold. Shared. */
        HOME_SEARCH,
        /** Travel back to an already-claimed, still-valid home. Shared. */
        HOME_RETURN,

        /** Cat: one deterministic patrol point around a claimed household. */
        PATROL_TERRITORY,
        /** Cat: creep onto one tagged vermin and take one ordinary melee opportunity. */
        STALK_VERMIN,
        /** Cat: settle on the claimed household and stay there. */
        CURL_AT_HOME,

        /** Owl: a quiet powered survey circuit inside the tether, in the air. */
        GLIDE_SURVEY,
        /** Owl: a perch-launched drop onto one tagged prey, then back to the perch. */
        POUNCE_PREY,
        /** Owl: hold the perch and watch. */
        ROOST_WATCH,

        /** Toad: a spaced hop toward one retained herb landmark. */
        HOP_TO_LANDMARK,
        /** Toad: a short crouch and lunge at one tagged insect beside a landmark. */
        FORAGE_INSECT,
        /** Toad: sit under the claimed shelter. */
        SHELTER_REST
    }

    /** Why the ladder produced the action it produced. Diagnostic only; never drives behaviour. */
    public enum Reason {
        BODY_INVALID,
        OWNER_ATTACKED,
        OWNER_TOO_FAR,
        ACTION_RUNNING,
        PREY_QUALIFIED,
        HOME_SEARCH_DUE,
        HOME_LOST,
        AWAY_FROM_HOME,
        ACTIVE_WINDOW,
        QUIET_WINDOW,
        NOTHING_TO_DO
    }

    /** How a species decides it is awake. Three genuinely different schedules, not one inverted. */
    public enum ActiveWindow {
        /** Cat. Awake in daylight, curled at home through the night. */
        DAYLIGHT,
        /** Owl. Awake at night only; weather is irrelevant to an owl's hunting window. */
        NIGHT,
        /** Toad. Awake at night, and also by day while it is raining. */
        NIGHT_OR_RAIN
    }

    /** Why a bounded traversal stopped. */
    public enum SelectionReason {
        SELECTED,
        NO_CANDIDATE,
        BUDGET_EXHAUSTED
    }

    /**
     * The complete per-species tuning table.
     *
     * @param species                the discriminator
     * @param activeWindow           when this species is awake
     * @param tetherRadiusSquared    how far from a loaded owner it will work before returning
     * @param activeAction           what it does while awake with nothing better to do
     * @param signatureAction        its one signature hunt or forage
     * @param restAction             what it does while quiet, at home
     * @param homeRadiusHorizontal   the horizontal radius of its home-search envelope
     * @param homeRadiusVertical     the vertical radius of its home-search envelope
     * @param homePositionsPerScan   envelope offsets evaluated per home search. This is a count of
     *                               <em>positions</em>, not of reads; the read allowance is
     *                               {@link #homeReadCap()}, which multiplies it by what one position
     *                               actually costs. Conflating the two is the defect this field
     *                               exists to make impossible to write again
     * @param preyCandidateCap       loaded entities inspected per prey search
     * @param preyLineOfSightCap     line-of-sight traces charged per prey search
     * @param telegraphTicks         the visible wind-up before the signature action commits
     * @param signatureCooldownTicks the cooldown armed after any signature outcome
     * @param homeSearchIntervalTicks the cadence of the bounded home search
     */
    public record Profile(
        AnimalFamiliarSpecies species,
        ActiveWindow activeWindow,
        double tetherRadiusSquared,
        Action activeAction,
        Action signatureAction,
        Action restAction,
        int homeRadiusHorizontal,
        int homeRadiusVertical,
        int homePositionsPerScan,
        int preyCandidateCap,
        int preyLineOfSightCap,
        int telegraphTicks,
        int signatureCooldownTicks,
        int homeSearchIntervalTicks
    ) {
        public Profile {
            Objects.requireNonNull(species, "species");
            Objects.requireNonNull(activeWindow, "activeWindow");
            Objects.requireNonNull(activeAction, "activeAction");
            Objects.requireNonNull(signatureAction, "signatureAction");
            Objects.requireNonNull(restAction, "restAction");
            if (tetherRadiusSquared <= 0.0
                || homeRadiusHorizontal <= 0
                || homeRadiusVertical <= 0
                || homePositionsPerScan <= 0
                || preyCandidateCap <= 0
                || preyLineOfSightCap < 0
                || telegraphTicks <= 0
                || signatureCooldownTicks <= 0
                || homeSearchIntervalTicks <= 0) {
                throw new IllegalArgumentException("A familiar profile must be positively bounded");
            }
        }

        /**
         * The read allowance for one home search: every position in the window, at what a position
         * of this species actually costs.
         *
         * <p>Derived rather than declared, so the two numbers cannot drift apart. The delivered
         * code sized the {@code ScanEnvelope} window with the read cap as its <em>length</em>, as
         * if a position cost one read; because a position really costs
         * {@link #homeReadsPerPosition(AnimalFamiliarSpecies)}, the budget ran out after
         * {@code cap / cost} positions and the advertised 11x11x5 and 11x11x7 envelopes collapsed
         * to a radius of one to two. This product is the number that makes the window walkable to
         * its end in the worst case, so an exhausted budget can no longer truncate a scan.</p>
         */
        public int homeReadCap() {
            return homePositionsPerScan * homeReadsPerPosition(species);
        }
    }

    /**
     * The worst case number of world reads one scanned position can charge, per species.
     *
     * <p>Source derived from {@code AnimalFamiliarRuntime}'s charging sites rather than measured.
     * Every species pays one read for the position itself and one for its footing; the rest is its
     * own home predicate, and the three differ by a factor of four because the three questions
     * genuinely differ.</p>
     *
     * <ul>
     *   <li>Cat: 1 position + 1 footing + 1 predicate + 4 horizontal neighbours in
     *       {@code adjacentTo} = 7.</li>
     *   <li>Owl: 1 + 1 + 1 + 1 tagged support above + 1 block of clearance below = 5.</li>
     *   <li>Toad: 1 + 1 + 1 + 16 water probes (four horizontals, four distances) + 1 tagged cover
     *       overhead = 20. The toad's is the expensive one, which is why its window is the
     *       shortest of the three.</li>
     * </ul>
     */
    public static int homeReadsPerPosition(final AnimalFamiliarSpecies species) {
        return switch (species) {
            case CAT -> 7;
            case OWL -> 5;
            case TOAD -> 20;
        };
    }

    public static Profile profile(final AnimalFamiliarSpecies species) {
        return switch (species) {
            case CAT -> CAT_PROFILE;
            case OWL -> OWL_PROFILE;
            case TOAD -> TOAD_PROFILE;
        };
    }

    /** The exact vocabulary this species may ever emit. */
    public static Set<Action> vocabulary(final AnimalFamiliarSpecies species) {
        final Profile profile = profile(species);
        return Set.of(
            Action.IDLE,
            Action.DEFEND_OWNER,
            Action.TETHER_RETURN,
            Action.HOME_SEARCH,
            Action.HOME_RETURN,
            profile.activeAction(),
            profile.signatureAction(),
            profile.restAction()
        );
    }

    /**
     * The five rungs every species owns.
     *
     * <p>Declared test seam, honestly labelled: production never calls this. It exists so
     * {@code AnimalFamiliarRulesTest} asserts the disjointness contract against the set the ladder
     * is actually built from, rather than against a second list in the test that could drift.</p>
     */
    public static Set<Action> sharedVocabulary() {
        return SHARED_ACTIONS;
    }

    public static boolean permits(final AnimalFamiliarSpecies species, final Action action) {
        return vocabulary(species).contains(action);
    }

    // ---- schedule ----

    public static boolean isNight(final long dayTime) {
        final long clock = Math.floorMod(dayTime, 24_000L);
        return clock >= NIGHT_START_TIME && clock <= NIGHT_END_TIME;
    }

    /**
     * Whether this species is awake. Three distinct answers to the same world: at midday in clear
     * weather only the Cat is awake; at midday in rain the Cat and the Toad are; at night the Owl
     * and the Toad are.
     */
    public static boolean awake(
        final AnimalFamiliarSpecies species,
        final long dayTime,
        final boolean raining
    ) {
        final boolean night = isNight(dayTime);
        return switch (profile(species).activeWindow()) {
            case DAYLIGHT -> !night;
            case NIGHT -> night;
            case NIGHT_OR_RAIN -> night || raining;
        };
    }

    // ---- ownership and target legality, shared by all three and reusable by F24 ----

    /**
     * Whether a candidate attacker may become this familiar's one defensive target.
     *
     * <p>Written against plain values so that any owner-bound creature can use it, F24 included.
     * The rejected set is the frozen one: the owner, any creature bound to the same owner, a
     * creative or spectating player, an invalid or dead entity, the familiar itself, and any
     * irritation with no attributable direct attacker.</p>
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
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(candidateOwner, "candidateOwner");
        Objects.requireNonNull(candidateId, "candidateId");
        if (!attributedDirectAttack || !candidateAlive || candidateIsSelf
            || candidateInvulnerablePlayer) {
            return false;
        }
        if (owner.isPresent() && owner.orElseThrow().equals(candidateId)) {
            return false;
        }
        return !(owner.isPresent() && candidateOwner.isPresent()
            && owner.orElseThrow().equals(candidateOwner.orElseThrow()));
    }

    /** Whether the familiar must abandon work and close the distance to its loaded owner. */
    public static boolean beyondTether(
        final AnimalFamiliarSpecies species,
        final boolean ownerLoaded,
        final double ownerDistanceSquared
    ) {
        return ownerLoaded && ownerDistanceSquared > profile(species).tetherRadiusSquared();
    }

    /** Whether the frozen 1.4 emergency recall applies. Unchanged distance, no new cadence. */
    public static boolean recallRequired(
        final boolean ownerLoaded,
        final double ownerDistanceSquared
    ) {
        return ownerLoaded && ownerDistanceSquared >= OWNER_RECALL_DISTANCE_SQUARED;
    }

    // ---- the one ladder ----

    /**
     * Everything the ladder is allowed to see. All three species pass the same shape; the species
     * field selects the vocabulary and the preconditions that produced these booleans.
     *
     * @param species              which familiar is deciding
     * @param bodyInvalid          dead, removed, or not on a server level
     * @param ownerLoaded          a bound owner exists and is alive in this level
     * @param ownerDistanceSquared distance to that owner, meaningless when {@code !ownerLoaded}
     * @param ownerUnderAttack     a legal direct attacker of the owner or of this familiar exists
     * @param signatureRunning     a signature action is already in flight
     * @param signatureReady       the cooldown has elapsed
     * @param preyQualified        a legal prey passed every species precondition this decision
     * @param homeClaimed          a home position is persisted
     * @param homeValid            that home still satisfies this species' validity predicate
     * @param atHome               the body is standing on or in its claimed home
     * @param homeSearchDue        the bounded home-search cadence is due
     * @param awake                {@link #awake(AnimalFamiliarSpecies, long, boolean)}
     */
    public record Facts(
        AnimalFamiliarSpecies species,
        boolean bodyInvalid,
        boolean ownerLoaded,
        double ownerDistanceSquared,
        boolean ownerUnderAttack,
        boolean signatureRunning,
        boolean signatureReady,
        boolean preyQualified,
        boolean homeClaimed,
        boolean homeValid,
        boolean atHome,
        boolean homeSearchDue,
        boolean awake
    ) {
        public Facts {
            Objects.requireNonNull(species, "species");
        }
    }

    /** One intent and the rung that produced it. */
    public record Decision(Action action, Reason reason) {
        public Decision {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * The single priority ladder. Per-tick: no streams, no allocation beyond the returned record.
     *
     * <p>Order, and it is the same order for all three: invalid body, direct owner defence, tether
     * correction, an already-running signature action, a newly qualified signature action, a due
     * home search, a return to a claimed home, the awake action, the quiet rest action, idle.</p>
     */
    public static Decision decide(final Facts facts) {
        final Profile profile = profile(facts.species());
        if (facts.bodyInvalid()) {
            return new Decision(Action.IDLE, Reason.BODY_INVALID);
        }
        if (facts.ownerUnderAttack()) {
            return new Decision(Action.DEFEND_OWNER, Reason.OWNER_ATTACKED);
        }
        if (beyondTether(facts.species(), facts.ownerLoaded(), facts.ownerDistanceSquared())) {
            return new Decision(Action.TETHER_RETURN, Reason.OWNER_TOO_FAR);
        }
        if (facts.signatureRunning()) {
            return new Decision(profile.signatureAction(), Reason.ACTION_RUNNING);
        }
        if (facts.awake() && facts.signatureReady() && facts.preyQualified()) {
            return new Decision(profile.signatureAction(), Reason.PREY_QUALIFIED);
        }
        if (!facts.homeClaimed() && facts.homeSearchDue()) {
            return new Decision(Action.HOME_SEARCH, Reason.HOME_SEARCH_DUE);
        }
        if (facts.homeClaimed() && !facts.homeValid()) {
            return new Decision(Action.HOME_SEARCH, Reason.HOME_LOST);
        }
        if (facts.homeClaimed() && !facts.atHome() && !facts.awake()) {
            return new Decision(Action.HOME_RETURN, Reason.AWAY_FROM_HOME);
        }
        if (facts.awake()) {
            return new Decision(profile.activeAction(), Reason.ACTIVE_WINDOW);
        }
        if (facts.homeClaimed()) {
            return new Decision(profile.restAction(), Reason.QUIET_WINDOW);
        }
        return new Decision(Action.IDLE, Reason.NOTHING_TO_DO);
    }

    // ---- bounded candidate traversal, charged before any filter can reject ----

    /** One inspected prey candidate. Distance is squared; identity breaks every tie. */
    public record PreyCandidate(
        UUID id,
        double distanceSquared,
        boolean tagged,
        boolean alive,
        boolean protectedFromFamiliars,
        boolean insideEnvelope,
        boolean visible
    ) {
        public PreyCandidate {
            Objects.requireNonNull(id, "id");
        }
    }

    /** One inspected home candidate. */
    public record HomeCandidate(
        long packedPosition,
        double distanceSquared,
        boolean supported,
        boolean clear,
        boolean speciesQualified
    ) {
    }

    public record PreySelection(Optional<UUID> prey, SelectionReason reason, int inspected,
                                int lineOfSightChecks) {
        public PreySelection {
            Objects.requireNonNull(prey, "prey");
            Objects.requireNonNull(reason, "reason");
        }
    }

    public record HomeSelection(Optional<Long> home, SelectionReason reason, int inspected) {
        public HomeSelection {
            Objects.requireNonNull(home, "home");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * One prey traversal for all three species.
     *
     * <p>The candidate is charged the moment it is reached and before any filter can reject it, so
     * a scene of forty dead or untagged entities costs exactly the cap and not zero. Line-of-sight
     * is charged separately and only for candidates that survived the cheap filters, because a
     * trace is the expensive read; a species with a zero line-of-sight cap (the Cat, which stalks
     * by scent) never spends one and never rejects on visibility.</p>
     */
    public static PreySelection selectPrey(
        final List<PreyCandidate> candidates,
        final int candidateCap,
        final int lineOfSightCap
    ) {
        Objects.requireNonNull(candidates, "candidates");
        int inspected = 0;
        int traces = 0;
        Optional<UUID> best = Optional.empty();
        double bestDistance = Double.MAX_VALUE;
        for (int index = 0; index < candidates.size(); index++) {
            if (inspected >= candidateCap) {
                return new PreySelection(best, best.isPresent()
                    ? SelectionReason.SELECTED
                    : SelectionReason.BUDGET_EXHAUSTED, inspected, traces);
            }
            final PreyCandidate candidate = candidates.get(index);
            inspected++;
            if (!candidate.tagged() || !candidate.alive() || candidate.protectedFromFamiliars()
                || !candidate.insideEnvelope()) {
                continue;
            }
            if (lineOfSightCap > 0) {
                if (traces >= lineOfSightCap) {
                    return new PreySelection(best, best.isPresent()
                        ? SelectionReason.SELECTED
                        : SelectionReason.BUDGET_EXHAUSTED, inspected, traces);
                }
                traces++;
                if (!candidate.visible()) {
                    continue;
                }
            }
            if (candidate.distanceSquared() < bestDistance
                || candidate.distanceSquared() == bestDistance
                    && best.isPresent()
                    && compareUnsigned(candidate.id(), best.orElseThrow()) < 0) {
                bestDistance = candidate.distanceSquared();
                best = Optional.of(candidate.id());
            }
        }
        return new PreySelection(best, best.isPresent()
            ? SelectionReason.SELECTED
            : SelectionReason.NO_CANDIDATE, inspected, traces);
    }

    /**
     * One home traversal for all three species. Identical charging discipline; the difference is
     * entirely in {@link HomeCandidate#speciesQualified()}, which the runtime computes from the
     * species' own world predicate before handing the value in.
     */
    public static HomeSelection selectHome(final List<HomeCandidate> candidates, final int cap) {
        Objects.requireNonNull(candidates, "candidates");
        final int candidateCap = Math.max(0, cap);
        int inspected = 0;
        Optional<Long> best = Optional.empty();
        double bestDistance = Double.MAX_VALUE;
        for (int index = 0; index < candidates.size(); index++) {
            if (inspected >= candidateCap) {
                return new HomeSelection(best, best.isPresent()
                    ? SelectionReason.SELECTED
                    : SelectionReason.BUDGET_EXHAUSTED, inspected);
            }
            final HomeCandidate candidate = candidates.get(index);
            inspected++;
            if (!candidate.supported() || !candidate.clear() || !candidate.speciesQualified()) {
                continue;
            }
            if (candidate.distanceSquared() < bestDistance
                || candidate.distanceSquared() == bestDistance
                    && best.isPresent()
                    && Long.compareUnsigned(candidate.packedPosition(), best.orElseThrow()) < 0) {
                bestDistance = candidate.distanceSquared();
                best = Optional.of(candidate.packedPosition());
            }
        }
        return new HomeSelection(best, best.isPresent()
            ? SelectionReason.SELECTED
            : SelectionReason.NO_CANDIDATE, inspected);
    }

    private static int compareUnsigned(final UUID left, final UUID right) {
        final int high = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return high != 0
            ? high
            : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }

    // ---- cadence and routing ----

    /** What one completed search costs: a re-armed cadence and a recorded failure count. */
    public record SearchOutcome(long nextDueAt, int consecutiveFailures) {
    }

    /**
     * Closes out one search, whatever it found.
     *
     * <p>Both halves are unconditional by construction. The cadence is re-armed on the same line
     * for both outcomes, so a search that qualifies nothing cannot leave itself due and spin every
     * tick; and the failure count is recorded rather than discarded, so three fruitless searches in
     * a row are visible to {@link #backoffTicks(int)}. A caller that wanted to arm only on success
     * would have to delete the arming, not merely skip a branch.</p>
     */
    public static SearchOutcome recordSearch(
        final long now,
        final int intervalTicks,
        final boolean qualified,
        final int failuresBefore
    ) {
        final int interval = Math.max(1, intervalTicks);
        final long nextDueAt = saturatingAdd(Math.max(0L, now), interval);
        final int failures = qualified
            ? 0
            : Math.min(MAX_ROUTE_FAILURES, Math.max(0, failuresBefore) + 1);
        return new SearchOutcome(nextDueAt, failures);
    }

    /** Whether a navigation request may be issued now. */
    public static boolean mayRoute(final long now, final long nextNavigationAt, final long backoffUntil) {
        return now >= nextNavigationAt && now >= backoffUntil;
    }

    /** The backoff window a given consecutive-failure count has earned. */
    public static int backoffTicks(final int consecutiveFailures) {
        return consecutiveFailures >= MAX_ROUTE_FAILURES ? ROUTE_BACKOFF_TICKS : 0;
    }

    // ---- clamps shared by the state record ----

    public static long saturatingAdd(final long base, final long delta) {
        final long sum = base + delta;
        return sum < base ? Long.MAX_VALUE : sum;
    }

    /** Clamps a loaded absolute deadline into {@code [now, now + horizon]}, zero meaning unset. */
    public static long clampDeadline(final long stored, final long now, final long horizon) {
        if (stored <= 0L) {
            return 0L;
        }
        final long ceiling = saturatingAdd(Math.max(0L, now), Math.min(horizon, FAR_FUTURE_TICKS));
        return Math.clamp(stored, 0L, ceiling);
    }

    /** A stable per-entity schedule offset so a crowd of familiars does not pulse in lockstep. */
    public static int stableOffset(final UUID identity, final int span) {
        Objects.requireNonNull(identity, "identity");
        if (span <= 1) {
            return 0;
        }
        long mixed = identity.getMostSignificantBits() ^ identity.getLeastSignificantBits();
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        return (int) Math.floorMod(mixed, span);
    }
}
