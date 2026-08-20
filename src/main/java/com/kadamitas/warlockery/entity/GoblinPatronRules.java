package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The complete loader-neutral F12 Goblin Patron decision surface, shared by Stonebroker and
 * Forgewarden. Every method is a pure function of already-observed facts: no {@code Level}, entity,
 * path, registry, block state, container, random source, or loader API is ever accepted, and nothing
 * here mutates. {@link GoblinPatronRuntime} observes, this class decides, and the runtime executes
 * exactly one decision.
 *
 * <p>Shared machinery lives here exactly once. The two patrons differ by <em>data</em>: separate
 * action vocabularies ({@link #permits}), separate schedules ({@link #nextAction}), separate scan
 * subjects, and separate feedback. They never differ by a duplicated copy of the same rule, because
 * a duplicated rule is how the same search defect has shipped twice in one family before.</p>
 *
 * <p>Durations are remaining loaded-tick counts, never absolute world deadlines, so an unloaded
 * patron pauses meaning rather than silently expiring it. Zero always reads as due, and the only
 * far-future sentinel is {@link #FAR_FUTURE_TICKS}, bounded well below {@code Long.MAX_VALUE}.</p>
 */
public final class GoblinPatronRules {
    /** Bounded far-future cadence sentinel; never {@code Long.MAX_VALUE}. */
    public static final long FAR_FUTURE_TICKS = 20_000L;

    // ---------------------------------------------------------------- cadence

    public static final int PERCEPTION_INTERVAL_TICKS = 40;
    public static final int COUNTERPART_INTERVAL_TICKS = 40;
    public static final int DIRECTIVE_INTERVAL_TICKS = 40;
    public static final int BLOCK_SCAN_INTERVAL_TICKS = 80;
    public static final int HAZARD_INTERVAL_TICKS = 20;
    public static final int ACTION_INTERVAL_TICKS = 20;
    public static final int NAVIGATION_INTERVAL_TICKS = 20;
    public static final int FEEDBACK_INTERVAL_TICKS = 40;
    public static final int TRADE_INTERVAL_TICKS = 20;
    public static final int MAX_SCHEDULE_OFFSET_TICKS = 39;

    // ---------------------------------------------------------------- perception budgets

    public static final int CHALLENGER_RADIUS = 24;
    public static final int MAX_CHALLENGER_INSPECTIONS = 16;
    public static final int COUNTERPART_RADIUS = 32;
    public static final int MAX_COUNTERPART_INSPECTIONS = 8;
    public static final int DIRECTIVE_RADIUS = 24;
    public static final int MAX_DIRECTIVE_INSPECTIONS = 16;
    public static final int SURGE_RADIUS = 4;
    public static final int MAX_SURGE_INSPECTIONS = 16;

    // ---------------------------------------------------------------- block budgets

    public static final int SCAN_HORIZONTAL = 12;
    public static final int SCAN_VERTICAL = 4;
    public static final int MAX_SCAN_BLOCK_READS = 256;
    public static final int MAX_RETAINED_CANDIDATES = 8;

    // ---------------------------------------------------------------- navigation

    public static final int MAX_ROUTE_FAILURES = 3;
    public static final int ROUTE_BACKOFF_TICKS = 100;
    public static final int ROUTE_RETRY_TICKS = 20;

    // ---------------------------------------------------------------- combat framing

    public static final float PHASE_TWO_FRACTION = 0.67F;
    public static final float PHASE_THREE_FRACTION = 0.34F;
    public static final float WITHDRAW_HEALTH_FRACTION = 0.20F;
    public static final float RECOVER_HEALTH_FRACTION = 0.40F;

    public static final int VOLLEY_TELL_TICKS = 16;
    public static final int VOLLEY_RECOVERY_TICKS = 40;
    public static final int VOLLEY_ARROW_SPACING_TICKS = 4;
    public static final int HAMMER_TELL_TICKS = 14;
    public static final int HAMMER_RECOVERY_TICKS = 30;
    public static final int SURGE_TELL_TICKS = 24;
    public static final int SURGE_RECOVERY_TICKS = 100;
    public static final int SHIFT_TELL_TICKS = 8;
    public static final int SHIFT_RECOVERY_TICKS = 20;
    public static final int WARD_STANCE_TICKS = 60;
    public static final int WARD_STANCE_GAP_TICKS = 80;

    /** Per-phase minimum spacing for the two expensive signature actions, in loaded ticks. */
    public static final int VOLLEY_PHASE_ONE_GAP_TICKS = 80;
    public static final int VOLLEY_PHASE_TWO_GAP_TICKS = 60;
    public static final int VOLLEY_PHASE_THREE_GAP_TICKS = 80;
    public static final int SURGE_PHASE_TWO_GAP_TICKS = 100;
    public static final int SURGE_PHASE_THREE_GAP_TICKS = 140;

    public static final double SHIFT_MIN_DISTANCE = 7.0D;
    public static final double SHIFT_MAX_DISTANCE = 11.0D;
    public static final double VOLLEY_MAX_DISTANCE_SQUARED = 256.0D;

    // ---------------------------------------------------------------- offering

    public static final int MAX_EMPOWERMENT = 5;
    public static final double OFFERING_HEALTH_DELTA = 4.0D;
    public static final double OFFERING_ATTACK_DELTA = 1.0D;
    public static final int PARLEY_TICKS = 200;
    public static final int COMMISSION_TICKS = 100;
    public static final int MAX_OFFERING_FACTS = 8;
    public static final int MIN_STANDING = -5;
    public static final int MAX_STANDING = 5;
    public static final int FACT_EXPIRY_TICKS = 12_000;

    // ---------------------------------------------------------------- accord

    public static final double ACCORD_MAINTENANCE_DISTANCE = 32.0D;
    public static final double ACCORD_EFFECT_DISTANCE = 16.0D;
    public static final double WARD_PROTECTION_DISTANCE = 12.0D;
    public static final int ACCORD_EXPIRY_TICKS = 600;
    public static final float SHARED_CHALLENGER_BONUS = 4.0F;
    public static final float WARD_DAMAGE_REDUCTION = 0.25F;

    // ---------------------------------------------------------------- anchors and directives

    public static final int ANCHOR_EXPIRY_TICKS = 2_400;
    public static final int DIRECTIVE_EXPIRY_TICKS = 1_200;
    public static final double WATCH_RADIUS = 12.0D;

    // ---------------------------------------------------------------- merchant

    public static final int MIN_MERCHANT_LEVEL = 1;
    public static final int MAX_MERCHANT_LEVEL = 5;
    public static final int MAX_RESTOCKS_PER_DAY = 2;
    public static final int RESTOCK_SPACING_TICKS = 2_400;
    private static final int[] LEVEL_XP_THRESHOLDS = {0, 10, 70, 150, 250};

    // ---------------------------------------------------------------- schema

    public static final int STATE_SCHEMA_VERSION = 1;
    /**
     * The declared encoded ceiling for one fully populated patron state, asserted directly by
     * {@code GoblinPatronStateTest}. It is larger than F10's 1,024 because a patron carries an
     * accord, a published result, and up to eight relationship facts that a Goblin does not; a fully
     * loaded state measures 1,837 bytes, so this leaves headroom without hiding growth.
     */
    public static final int MAX_STATE_BYTES = 2_048;

    private GoblinPatronRules() {
    }

    // ================================================================ identity

    /** The two exact kinds F12 owns. Every entry point re-checks rather than trusting a caller. */
    public static boolean isPatron(final CreatureKind kind) {
        return kind == CreatureKind.STONEBROKER || kind == CreatureKind.FORGEWARDEN;
    }

    /**
     * The counterpart mapping is deliberately delegated to the existing {@link GoblinBossRules}
     * rather than restated here. Two copies of one table is exactly how a family shipped the same
     * defect in both of its rule classes.
     */
    public static Optional<CreatureKind> counterpartOf(final CreatureKind kind) {
        return GoblinBossRules.counterpart(kind).filter(GoblinPatronRules::isPatron);
    }

    public static boolean areOppositeKinds(final CreatureKind self, final CreatureKind other) {
        return counterpartOf(self).filter(expected -> expected == other).isPresent();
    }

    // ================================================================ vocabularies

    /**
     * The finite action vocabulary of the whole family. {@link #permits} is what makes the two
     * identities separate: no patron may ever commit an action outside its own column.
     */
    public enum Action {
        IDLE,
        TRADE_HOLD,
        APPRAISE_CONTEXT,
        QUIET_LEDGER,
        PARLEY,
        WATCH_CLAIM,
        LEDGER_VOLLEY,
        CLAIM_SHIFT,
        ORDERLY_WITHDRAWAL,
        INSPECT_FORGE,
        COMMISSION,
        WARD_STANCE,
        INTERPOSE,
        HAMMER_COMMIT,
        FORGE_SURGE,
        REGROUP
    }

    /** Combat phase, derived from health fraction only. Shared so one boss bar reads the same. */
    public enum Phase {
        PHASE_ONE, PHASE_TWO, PHASE_THREE
    }

    /** Every decision, acceptance, and rejection names one of these. Tests assert the code. */
    public enum Reason {
        OK,
        NOT_A_PATRON,
        WRONG_KIND,
        ACTION_NOT_PERMITTED,
        BUSY_WITH_ACTION,
        IN_RECOVERY,
        CADENCE_NOT_DUE,
        NO_CANDIDATE,
        NO_LINE_OF_SIGHT,
        OUT_OF_RANGE,
        TOO_CLOSE,
        TARGET_INVALID,
        TARGET_PROTECTED,
        HAZARD_PREEMPTS,
        TRADING,
        WINDOW_OPEN,
        WINDOW_EXPIRED,
        BREACHED,
        EMPOWERMENT_FULL,
        ACCORD_NOT_MUTUAL,
        ACCORD_EXPIRED,
        ACCORD_TOO_FAR,
        EPOCH_MISMATCH,
        DIMENSION_MISMATCH,
        BUDGET_EXHAUSTED,
        ROUTE_BACKOFF,
        RESTOCK_CAPPED,
        WITHDRAWING
    }

    /** The published local result kinds. One patron publishes exactly one of these at a time. */
    public enum DirectiveKind {
        BROKERED_WORK, FORGE_WARD
    }

    /** Classified route failures. Three of them establish {@link #ROUTE_BACKOFF_TICKS}. */
    public enum RouteFailure {
        NONE, NO_PATH, REJECTED, UNREACHABLE, STUCK
    }

    /** How the last challenger left. Persisted so a reload can explain itself. */
    public enum ReleaseReason {
        NONE, DIED, INVALID, OUT_OF_RANGE, TRADE, PARLEY, CANCELLED
    }

    /** Bounded relationship events. One per fact; no history list is ever stored. */
    public enum OfferingEvent {
        NONE, OFFERED, TRADED, BREACHED
    }

    public static boolean permits(final CreatureKind kind, final Action action) {
        if (!isPatron(kind)) {
            return false;
        }
        return switch (action) {
            case IDLE, TRADE_HOLD -> true;
            case APPRAISE_CONTEXT, QUIET_LEDGER, PARLEY, WATCH_CLAIM, LEDGER_VOLLEY, CLAIM_SHIFT,
                 ORDERLY_WITHDRAWAL -> kind == CreatureKind.STONEBROKER;
            case INSPECT_FORGE, COMMISSION, WARD_STANCE, INTERPOSE, HAMMER_COMMIT, FORGE_SURGE,
                 REGROUP -> kind == CreatureKind.FORGEWARDEN;
        };
    }

    /** The exact action set of one patron, in declaration order. Used by identity assertions. */
    public static List<Action> vocabulary(final CreatureKind kind) {
        final List<Action> actions = new ArrayList<>();
        for (final Action action : Action.values()) {
            if (permits(kind, action)) {
                actions.add(action);
            }
        }
        return List.copyOf(actions);
    }

    public static DirectiveKind directiveKind(final CreatureKind kind) {
        return kind == CreatureKind.FORGEWARDEN
            ? DirectiveKind.FORGE_WARD
            : DirectiveKind.BROKERED_WORK;
    }

    /** Only Forgewarden is intrinsically immune to fire and lava; both escape every other hazard. */
    public static boolean immuneToFireHazard(final CreatureKind kind) {
        return kind == CreatureKind.FORGEWARDEN;
    }

    // ================================================================ timing primitives

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

    // ================================================================ phases

    public static Phase phase(final float health, final float maxHealth) {
        if (maxHealth <= 0.0F) {
            return Phase.PHASE_THREE;
        }
        final float fraction = Math.clamp(health / maxHealth, 0.0F, 1.0F);
        if (fraction > PHASE_TWO_FRACTION) {
            return Phase.PHASE_ONE;
        }
        return fraction > PHASE_THREE_FRACTION ? Phase.PHASE_TWO : Phase.PHASE_THREE;
    }

    /**
     * Withdrawal and regroup use hysteresis so a patron hovering on the threshold cannot oscillate.
     * Entering needs {@link #WITHDRAW_HEALTH_FRACTION}; leaving needs {@link #RECOVER_HEALTH_FRACTION}.
     */
    public static boolean withdrawing(
        final boolean alreadyWithdrawing,
        final float health,
        final float maxHealth,
        final boolean safePointAvailable
    ) {
        if (maxHealth <= 0.0F) {
            return false;
        }
        final float fraction = Math.clamp(health / maxHealth, 0.0F, 1.0F);
        if (alreadyWithdrawing) {
            return fraction < RECOVER_HEALTH_FRACTION;
        }
        return fraction <= WITHDRAW_HEALTH_FRACTION && safePointAvailable;
    }

    // ================================================================ challenger selection

    /**
     * One candidate as the runtime already observed it. Plain values only: the rules never see an
     * entity, so a rule test needs no world.
     */
    public record Candidate(
        UUID id,
        boolean alive,
        boolean protectedTarget,
        boolean recentAttacker,
        boolean currentChallenger,
        double distanceSquared
    ) {
        public Candidate {
            id = id == null ? new UUID(0L, 0L) : id;
            distanceSquared = Math.max(0.0D, distanceSquared);
        }
    }

    /** The named outcome of one selection pass, including how much budget it actually spent. */
    public record Selection(Optional<UUID> challenger, Reason reason, int inspected) {
        public Selection {
            challenger = challenger == null ? Optional.empty() : challenger;
            reason = reason == null ? Reason.NO_CANDIDATE : reason;
            inspected = Math.max(0, inspected);
        }
    }

    /**
     * Stable challenger selection with recent-attacker priority.
     *
     * <p>The inspection budget is charged for <em>every</em> candidate the pass looks at, before any
     * filter can reject it. A budget that only counted accepted candidates would let a crowd of
     * rejected ones cost real reads while the declared cap never bound, which is the fourth of the
     * recurring defect classes this family was warned about.</p>
     *
     * <p>Preseeding is a reordering, not an exemption: the recent attacker and the stable current
     * challenger are evaluated first so a 16-candidate cap can never hide them behind a crowd, and
     * both still consume budget.</p>
     */
    public static Selection selectChallenger(final List<Candidate> candidates, final int inspectionCap) {
        final int cap = Math.max(0, inspectionCap);
        if (candidates == null || candidates.isEmpty()) {
            return new Selection(Optional.empty(), Reason.NO_CANDIDATE, 0);
        }
        if (cap == 0) {
            return new Selection(Optional.empty(), Reason.BUDGET_EXHAUSTED, 0);
        }
        final List<Candidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator
            .comparingInt((Candidate candidate) -> candidate.recentAttacker() ? 0 : 1)
            .thenComparingInt(candidate -> candidate.currentChallenger() ? 0 : 1)
            .thenComparingDouble(Candidate::distanceSquared)
            .thenComparing(candidate -> candidate.id().toString()));
        Candidate best = null;
        int inspected = 0;
        for (final Candidate candidate : ordered) {
            if (inspected >= cap) {
                break;
            }
            // Charged before the filter, never after it.
            inspected++;
            if (!candidate.alive() || candidate.protectedTarget()) {
                continue;
            }
            // The order above already put the highest-priority candidate first, so the first
            // acceptable one is the answer and the pass stops there. Everything it actually looked
            // at, accepted or rejected, has already been charged.
            best = candidate;
            break;
        }
        if (best == null) {
            return new Selection(
                Optional.empty(),
                inspected >= cap ? Reason.BUDGET_EXHAUSTED : Reason.NO_CANDIDATE,
                inspected
            );
        }
        return new Selection(Optional.of(best.id()), Reason.OK, inspected);
    }

    /**
     * A stable challenger is retained until it is genuinely gone. Retention beats a marginally
     * closer newcomer so a patron cannot be pulled apart by a crowd.
     */
    public static ReleaseReason releaseReason(
        final boolean stillPresent,
        final boolean alive,
        final boolean withinRange,
        final boolean trading,
        final boolean parleyWithChallenger
    ) {
        if (!alive) {
            return ReleaseReason.DIED;
        }
        if (!stillPresent) {
            return ReleaseReason.INVALID;
        }
        if (trading) {
            return ReleaseReason.TRADE;
        }
        if (parleyWithChallenger) {
            return ReleaseReason.PARLEY;
        }
        if (!withinRange) {
            return ReleaseReason.OUT_OF_RANGE;
        }
        return ReleaseReason.NONE;
    }

    // ================================================================ action scheduling

    /** Everything one scheduling decision is allowed to see. Plain values, no world. */
    public record CombatFacts(
        CreatureKind kind,
        Phase phase,
        boolean hasChallenger,
        boolean lineOfSight,
        double distanceSquared,
        boolean withinMeleeReach,
        int signatureGapRemaining,
        int secondaryGapRemaining,
        boolean withdrawing,
        boolean accordSubjectThreatened
    ) {
        public CombatFacts {
            phase = phase == null ? Phase.PHASE_ONE : phase;
            distanceSquared = Math.max(0.0D, distanceSquared);
            signatureGapRemaining = Math.max(0, signatureGapRemaining);
            secondaryGapRemaining = Math.max(0, secondaryGapRemaining);
        }
    }

    /** A scheduled action plus the reason it was chosen or refused. */
    public record Decision(Optional<Action> action, Reason reason) {
        public Decision {
            action = action == null ? Optional.empty() : action;
            reason = reason == null ? Reason.NO_CANDIDATE : reason;
        }

        public static Decision of(final Action action) {
            return new Decision(Optional.of(action), Reason.OK);
        }

        public static Decision refused(final Reason reason) {
            return new Decision(Optional.empty(), reason);
        }
    }

    /**
     * The one combat scheduler. Its two columns are deliberately different traces, not one trace
     * with a reskin: Stonebroker keeps distance and spends its budget on {@code LEDGER_VOLLEY} and
     * {@code CLAIM_SHIFT}, Forgewarden closes and spends its budget on {@code HAMMER_COMMIT} and
     * {@code FORGE_SURGE}. Neither ever schedules the other's action, because {@link #permits}
     * gates every return.
     */
    public static Decision nextAction(final CombatFacts facts) {
        if (facts == null || !isPatron(facts.kind())) {
            return Decision.refused(Reason.NOT_A_PATRON);
        }
        if (!facts.hasChallenger()) {
            return Decision.refused(Reason.NO_CANDIDATE);
        }
        return facts.kind() == CreatureKind.STONEBROKER
            ? scheduleStonebroker(facts)
            : scheduleForgewarden(facts);
    }

    private static Decision scheduleStonebroker(final CombatFacts facts) {
        if (facts.withdrawing()) {
            return Decision.of(Action.ORDERLY_WITHDRAWAL);
        }
        if (!facts.lineOfSight()) {
            // A blocked line of sight is a reposition problem, never a deadlock. Phase 2 owns the
            // enumerated shift; the other phases hand the problem back to ordinary approach.
            return facts.phase() == Phase.PHASE_TWO && isDue(facts.secondaryGapRemaining())
                ? Decision.of(Action.CLAIM_SHIFT)
                : Decision.refused(Reason.NO_LINE_OF_SIGHT);
        }
        if (facts.distanceSquared() > VOLLEY_MAX_DISTANCE_SQUARED) {
            return Decision.refused(Reason.OUT_OF_RANGE);
        }
        if (isDue(facts.signatureGapRemaining())) {
            return Decision.of(Action.LEDGER_VOLLEY);
        }
        if (facts.phase() == Phase.PHASE_TWO
            && isDue(facts.secondaryGapRemaining())
            && facts.distanceSquared() < SHIFT_MIN_DISTANCE * SHIFT_MIN_DISTANCE) {
            return Decision.of(Action.CLAIM_SHIFT);
        }
        // Phase 3 deliberately keeps real melee windows rather than starving them behind the volley.
        return Decision.refused(facts.withinMeleeReach() ? Reason.OK : Reason.CADENCE_NOT_DUE);
    }

    private static Decision scheduleForgewarden(final CombatFacts facts) {
        if (facts.withdrawing()) {
            return Decision.of(Action.REGROUP);
        }
        if (facts.accordSubjectThreatened() && facts.phase() != Phase.PHASE_THREE) {
            return Decision.of(Action.INTERPOSE);
        }
        if (facts.phase() != Phase.PHASE_ONE && isDue(facts.signatureGapRemaining())) {
            return Decision.of(Action.FORGE_SURGE);
        }
        if (!facts.lineOfSight()) {
            return Decision.refused(Reason.NO_LINE_OF_SIGHT);
        }
        if (facts.phase() != Phase.PHASE_ONE
            && isDue(facts.secondaryGapRemaining())
            && facts.withinMeleeReach()) {
            return Decision.of(Action.HAMMER_COMMIT);
        }
        // Phase 1 is direct pressure: ordinary melee windows, plus the ward stance after a damaged
        // accord subject. Nothing here starves the plain attack executor.
        if (facts.phase() == Phase.PHASE_ONE && facts.accordSubjectThreatened()
            && isDue(facts.secondaryGapRemaining())) {
            return Decision.of(Action.WARD_STANCE);
        }
        return Decision.refused(facts.withinMeleeReach() ? Reason.OK : Reason.CADENCE_NOT_DUE);
    }

    /** The declared minimum spacing of the signature action of one kind in one phase. */
    public static int signatureGapTicks(final CreatureKind kind, final Phase phase) {
        if (kind == CreatureKind.STONEBROKER) {
            return switch (phase) {
                case PHASE_ONE -> VOLLEY_PHASE_ONE_GAP_TICKS;
                case PHASE_TWO -> VOLLEY_PHASE_TWO_GAP_TICKS;
                case PHASE_THREE -> VOLLEY_PHASE_THREE_GAP_TICKS;
            };
        }
        if (kind == CreatureKind.FORGEWARDEN) {
            return switch (phase) {
                // Phase 1 never surges at all, so its gap is the phase-2 value plus its own tell:
                // the scheduler refuses the action outright rather than relying on this number.
                case PHASE_ONE, PHASE_TWO -> SURGE_PHASE_TWO_GAP_TICKS;
                case PHASE_THREE -> SURGE_PHASE_THREE_GAP_TICKS;
            };
        }
        return 0;
    }

    /** The number of arrows one volley retains in one phase. Never more than the declared count. */
    public static int volleyArrows(final Phase phase) {
        return switch (phase) {
            case PHASE_ONE -> 1;
            case PHASE_TWO -> 2;
            case PHASE_THREE -> 3;
        };
    }

    public static int tellTicks(final Action action) {
        return switch (action) {
            case LEDGER_VOLLEY -> VOLLEY_TELL_TICKS;
            case HAMMER_COMMIT -> HAMMER_TELL_TICKS;
            case FORGE_SURGE -> SURGE_TELL_TICKS;
            case CLAIM_SHIFT -> SHIFT_TELL_TICKS;
            default -> 0;
        };
    }

    public static int recoveryTicks(final Action action) {
        return switch (action) {
            case LEDGER_VOLLEY -> VOLLEY_RECOVERY_TICKS;
            case HAMMER_COMMIT -> HAMMER_RECOVERY_TICKS;
            case FORGE_SURGE -> SURGE_RECOVERY_TICKS;
            case CLAIM_SHIFT -> SHIFT_RECOVERY_TICKS;
            case WARD_STANCE -> WARD_STANCE_GAP_TICKS;
            default -> 0;
        };
    }

    /** True only for the actions that must show a tell before any effect can commit. */
    public static boolean isTelegraphed(final Action action) {
        return tellTicks(action) > 0;
    }

    /**
     * True only for the actions a trade window would actually interrupt.
     *
     * <p>Committed-ness alone is the wrong test and was a live defect: the offering window is
     * itself a committed action, so treating every committed action as busy made the parley and
     * the commission close the very trade they exist to open. A telegraphed action, an interpose,
     * and a withdrawal genuinely cannot be interrupted; an ambient watch, a ward stance, a ledger
     * pose, and the two offering windows can and must be.</p>
     */
    public static boolean blocksTrade(final Action action) {
        if (action == null) {
            return false;
        }
        return isTelegraphed(action)
            || action == Action.INTERPOSE
            || action == Action.ORDERLY_WITHDRAWAL
            || action == Action.REGROUP;
    }

    // ================================================================ offering and relationship

    /** One bounded relationship fact. At most {@link #MAX_OFFERING_FACTS} are ever kept. */
    public record OfferingFact(UUID player, int standing, OfferingEvent event, int remainingTicks) {
        public OfferingFact {
            player = player == null ? new UUID(0L, 0L) : player;
            standing = Math.clamp(standing, MIN_STANDING, MAX_STANDING);
            event = event == null ? OfferingEvent.NONE : event;
            remainingTicks = clampRemaining(remainingTicks, FACT_EXPIRY_TICKS);
        }

        public boolean expired() {
            return isDue(remainingTicks);
        }
    }

    /** The named outcome of one heart offering. */
    public record OfferingResult(
        boolean accepted,
        int empowermentAfter,
        double healthDelta,
        double attackDelta,
        int windowTicks,
        Reason reason
    ) {
    }

    /**
     * Levels 0 to 4 accept exactly one item, add exactly four maximum health and one attack damage,
     * and open the kind-specific window. Level 5 refuses without consuming anything. The window is
     * the only behavioural difference between the two patrons here: Stonebroker parleys for
     * {@link #PARLEY_TICKS}, Forgewarden prepares a commission for {@link #COMMISSION_TICKS}.
     */
    public static OfferingResult offerHeart(final CreatureKind kind, final int empowermentBefore) {
        if (!isPatron(kind)) {
            return new OfferingResult(false, empowermentBefore, 0.0D, 0.0D, 0, Reason.NOT_A_PATRON);
        }
        final int before = Math.clamp(empowermentBefore, 0, MAX_EMPOWERMENT);
        if (before >= MAX_EMPOWERMENT) {
            return new OfferingResult(false, before, 0.0D, 0.0D, 0, Reason.EMPOWERMENT_FULL);
        }
        return new OfferingResult(
            true,
            before + 1,
            OFFERING_HEALTH_DELTA,
            OFFERING_ATTACK_DELTA,
            windowTicks(kind),
            Reason.OK
        );
    }

    public static int windowTicks(final CreatureKind kind) {
        return kind == CreatureKind.FORGEWARDEN ? COMMISSION_TICKS : PARLEY_TICKS;
    }

    public static Action windowAction(final CreatureKind kind) {
        return kind == CreatureKind.FORGEWARDEN ? Action.COMMISSION : Action.PARLEY;
    }

    /**
     * Bounded fact storage with deterministic eviction. Neutral expired entries are pruned first,
     * then the oldest remaining entry by remaining ticks and UUID. No arrival order is trusted.
     */
    public static List<OfferingFact> recordFact(
        final List<OfferingFact> existing,
        final UUID player,
        final OfferingEvent event
    ) {
        final List<OfferingFact> facts = new ArrayList<>();
        int standing = 0;
        for (final OfferingFact fact : existing == null ? List.<OfferingFact>of() : existing) {
            if (fact.player().equals(player)) {
                standing = fact.standing();
                continue;
            }
            if (!fact.expired()) {
                facts.add(fact);
            }
        }
        facts.add(new OfferingFact(player, standing + standingDelta(event), event, FACT_EXPIRY_TICKS));
        if (facts.size() <= MAX_OFFERING_FACTS) {
            return List.copyOf(facts);
        }
        facts.sort(Comparator
            .comparingInt((OfferingFact fact) -> fact.standing() == 0 ? 0 : 1)
            .thenComparingInt(OfferingFact::remainingTicks)
            .thenComparing(fact -> fact.player().toString()));
        return List.copyOf(facts.subList(facts.size() - MAX_OFFERING_FACTS, facts.size()));
    }

    private static int standingDelta(final OfferingEvent event) {
        return switch (event) {
            case OFFERED -> 2;
            case TRADED -> 1;
            case BREACHED -> -3;
            case NONE -> 0;
        };
    }

    /**
     * A direct attack by the window holder breaches the window in the same tick and makes the
     * attacker eligible again under ordinary priority.
     */
    public static boolean breaches(final Optional<UUID> windowPlayer, final UUID attacker) {
        return windowPlayer.filter(player -> player.equals(attacker)).isPresent();
    }

    // ================================================================ counterpart accord

    /** One counterpart candidate as observed. */
    public record CounterpartCandidate(
        UUID id,
        CreatureKind kind,
        boolean alive,
        boolean sameDimension,
        double distanceSquared,
        Optional<UUID> chooses
    ) {
        public CounterpartCandidate {
            id = id == null ? new UUID(0L, 0L) : id;
            chooses = chooses == null ? Optional.empty() : chooses;
            distanceSquared = Math.max(0.0D, distanceSquared);
        }
    }

    public record AccordSelection(Optional<UUID> counterpart, Reason reason, int inspected) {
        public AccordSelection {
            counterpart = counterpart == null ? Optional.empty() : counterpart;
            reason = reason == null ? Reason.NO_CANDIDATE : reason;
            inspected = Math.max(0, inspected);
        }
    }

    /**
     * Mutual, one-to-one accord selection. Candidate order is current valid counterpart, squared
     * distance, then UUID, and every inspected candidate is charged before it can be rejected.
     * An accord forms only when both patrons choose each other.
     */
    public static AccordSelection selectCounterpart(
        final UUID self,
        final CreatureKind selfKind,
        final Optional<UUID> currentCounterpart,
        final List<CounterpartCandidate> candidates,
        final int inspectionCap
    ) {
        if (!isPatron(selfKind)) {
            return new AccordSelection(Optional.empty(), Reason.NOT_A_PATRON, 0);
        }
        final int cap = Math.max(0, inspectionCap);
        if (candidates == null || candidates.isEmpty() || cap == 0) {
            return new AccordSelection(
                Optional.empty(),
                cap == 0 ? Reason.BUDGET_EXHAUSTED : Reason.NO_CANDIDATE,
                0
            );
        }
        final Optional<UUID> current = currentCounterpart == null ? Optional.empty() : currentCounterpart;
        final List<CounterpartCandidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator
            .comparingInt((CounterpartCandidate candidate) ->
                current.filter(candidate.id()::equals).isPresent() ? 0 : 1)
            .thenComparingDouble(CounterpartCandidate::distanceSquared)
            .thenComparing(candidate -> candidate.id().toString()));
        int inspected = 0;
        Reason lastRejection = Reason.NO_CANDIDATE;
        for (final CounterpartCandidate candidate : ordered) {
            if (inspected >= cap) {
                break;
            }
            inspected++;
            if (!candidate.alive() || !areOppositeKinds(selfKind, candidate.kind())) {
                lastRejection = Reason.WRONG_KIND;
                continue;
            }
            if (!candidate.sameDimension()) {
                lastRejection = Reason.DIMENSION_MISMATCH;
                continue;
            }
            if (candidate.distanceSquared() > ACCORD_MAINTENANCE_DISTANCE * ACCORD_MAINTENANCE_DISTANCE) {
                lastRejection = Reason.ACCORD_TOO_FAR;
                continue;
            }
            if (candidate.chooses().filter(chosen -> chosen.equals(self)).isEmpty()) {
                lastRejection = Reason.ACCORD_NOT_MUTUAL;
                continue;
            }
            return new AccordSelection(Optional.of(candidate.id()), Reason.OK, inspected);
        }
        return new AccordSelection(
            Optional.empty(),
            inspected >= cap && lastRejection == Reason.NO_CANDIDATE
                ? Reason.BUDGET_EXHAUSTED
                : lastRejection,
            inspected
        );
    }

    /** Every gate an accord effect must clear before it may apply anything at all. */
    public static Reason accordUsable(
        final boolean bothLoaded,
        final boolean sameDimension,
        final boolean epochsMatch,
        final int remainingTicks,
        final double distanceSquared,
        final double maximumDistance
    ) {
        if (!bothLoaded) {
            return Reason.TARGET_INVALID;
        }
        if (!sameDimension) {
            return Reason.DIMENSION_MISMATCH;
        }
        if (!epochsMatch) {
            return Reason.EPOCH_MISMATCH;
        }
        if (isDue(remainingTicks)) {
            return Reason.ACCORD_EXPIRED;
        }
        if (distanceSquared > maximumDistance * maximumDistance) {
            return Reason.ACCORD_TOO_FAR;
        }
        return Reason.OK;
    }

    /**
     * The shared challenger mark supplies at most {@link #SHARED_CHALLENGER_BONUS} extra damage and
     * only against that exact challenger. It supplies no target, no path, and no effect.
     */
    public static float sharedChallengerBonus(
        final CreatureKind kind,
        final Optional<UUID> markedChallenger,
        final UUID actualTarget,
        final Reason accordState
    ) {
        if (kind != CreatureKind.FORGEWARDEN || accordState != Reason.OK) {
            return 0.0F;
        }
        return markedChallenger.filter(marked -> marked.equals(actualTarget)).isPresent()
            ? SHARED_CHALLENGER_BONUS
            : 0.0F;
    }

    /**
     * Ward stance reduces accepted damage to the accorded Stonebroker by exactly 25 percent. It
     * never reduces Forgewarden's own damage and never recurses, because the subject kind is
     * checked explicitly rather than inferred from the accord alone.
     */
    public static float wardReduction(
        final CreatureKind subjectKind,
        final boolean stanceActive,
        final Reason accordState,
        final boolean attackerValid
    ) {
        if (subjectKind != CreatureKind.STONEBROKER || !stanceActive
            || accordState != Reason.OK || !attackerValid) {
            return 0.0F;
        }
        return WARD_DAMAGE_REDUCTION;
    }

    // ================================================================ trading

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
     * A trade opens only when the patron is doing nothing that a trade window could interrupt. No
     * job-site block is required or claimed: patrons own no workstation.
     */
    public static Reason tradeEligibility(
        final boolean alive,
        final boolean alreadyTrading,
        final boolean hazardActive,
        final boolean actionCommitted,
        final boolean withdrawing
    ) {
        if (!alive) {
            return Reason.TARGET_INVALID;
        }
        if (alreadyTrading) {
            return Reason.TRADING;
        }
        if (hazardActive) {
            return Reason.HAZARD_PREEMPTS;
        }
        if (actionCommitted) {
            return Reason.BUSY_WITH_ACTION;
        }
        if (withdrawing) {
            return Reason.WITHDRAWING;
        }
        return Reason.OK;
    }

    public static Reason restockEligibility(
        final int restocksToday,
        final int spacingRemaining,
        final boolean safe,
        final boolean trading,
        final boolean actionCommitted
    ) {
        if (restocksToday >= MAX_RESTOCKS_PER_DAY) {
            return Reason.RESTOCK_CAPPED;
        }
        if (!isDue(spacingRemaining)) {
            return Reason.CADENCE_NOT_DUE;
        }
        if (!safe || actionCommitted) {
            return Reason.BUSY_WITH_ACTION;
        }
        if (trading) {
            return Reason.TRADING;
        }
        return Reason.OK;
    }

    /** Deterministic offer seed from identity, exact kind, level, and restock epoch. */
    public static long offerSeed(
        final UUID id,
        final CreatureKind kind,
        final int level,
        final long restockEpoch
    ) {
        final UUID safe = id == null ? new UUID(0L, 0L) : id;
        return safe.getMostSignificantBits()
            ^ safe.getLeastSignificantBits()
            ^ ((long) (kind == null ? 0 : kind.ordinal()) << 32)
            ^ (clampMerchantLevel(level) * 0x9E3779B97F4A7C15L)
            ^ Math.max(0L, restockEpoch);
    }

    // ================================================================ navigation

    /** A route request is refused entirely while the classified backoff is still running. */
    public static Reason routeEligibility(final int cadenceRemaining, final int backoffRemaining) {
        if (!isDue(backoffRemaining)) {
            return Reason.ROUTE_BACKOFF;
        }
        if (!isDue(cadenceRemaining)) {
            return Reason.CADENCE_NOT_DUE;
        }
        return Reason.OK;
    }

    /** Three classified failures establish the long backoff; anything less keeps the short retry. */
    public static int backoffTicks(final int failureCount) {
        return failureCount >= MAX_ROUTE_FAILURES ? ROUTE_BACKOFF_TICKS : ROUTE_RETRY_TICKS;
    }

    public static int nextFailureCount(final int current, final RouteFailure failure) {
        if (failure == RouteFailure.NONE) {
            return 0;
        }
        return Math.clamp(current + 1, 0, MAX_ROUTE_FAILURES);
    }

    // ================================================================ scans

    /**
     * A scan that qualified nothing still has to arm its own cadence and record the miss, or the
     * patron retries the same failed scan on every single tick forever. The runtime calls this for
     * both outcomes so the two paths cannot drift apart.
     */
    public static int nextScanCadence(final boolean qualified, final int interval) {
        return Math.max(1, interval);
    }

    /** The declared per-scan read budget of one patron block scan. */
    public static int scanReadCap() {
        return MAX_SCAN_BLOCK_READS;
    }

    /** Retention is capped independently of traversal; both caps are asserted by the fixtures. */
    public static int retentionCap() {
        return MAX_RETAINED_CANDIDATES;
    }

    // ================================================================ save normalization

    /**
     * Coupled-field normalization for a same-schema payload. A malformed save resets the coupled
     * action window, the invalid challenger and accord fields, and any extreme future deadline
     * together rather than one at a time, so no half-valid combination survives.
     */
    public static boolean actionFieldsCoupled(
        final Action action,
        final Optional<UUID> target,
        final int tellRemaining,
        final int commitRemaining,
        final int recoveryRemaining
    ) {
        if (action == Action.IDLE) {
            return target.isEmpty() && tellRemaining == 0 && commitRemaining == 0
                && recoveryRemaining == 0;
        }
        if (isTelegraphed(action)) {
            return tellRemaining <= tellTicks(action)
                && recoveryRemaining <= recoveryTicks(action)
                && commitRemaining <= tellTicks(action) + recoveryTicks(action);
        }
        return true;
    }

    /** Deadlines are clamped rather than trusted, so a hostile save cannot pin a patron forever. */
    public static int clampDeadline(final int remaining) {
        return clampRemaining(remaining, (int) FAR_FUTURE_TICKS);
    }
}
