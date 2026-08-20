package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.GoblinPatronRules.Action;
import com.kadamitas.warlockery.entity.GoblinPatronRules.OfferingEvent;
import com.kadamitas.warlockery.entity.GoblinPatronRules.OfferingFact;
import com.kadamitas.warlockery.entity.GoblinPatronRules.ReleaseReason;
import com.kadamitas.warlockery.entity.GoblinPatronRules.RouteFailure;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Versioned, fixed-cardinality F12 patron semantics, shared by Stonebroker and Forgewarden.
 *
 * <p>One state type serves both patrons on purpose. The durable <em>shape</em> is identical: one
 * anchor, one engagement window, one challenger, one accord, one published result, one action. Only
 * the meaning of the anchor and the window differs by kind, and {@link #kind} is the guard that
 * keeps them apart. Two parallel state records would have been fifteen duplicated members and one
 * duplicated defect.</p>
 *
 * <p>Every duration is a remaining loaded-tick count rather than an absolute world deadline, so an
 * unloaded patron pauses its meaning instead of expiring it. Zero always reads as due.</p>
 *
 * <p><strong>Constructor reconciliation here is the identity shape only, never the timer shape.</strong>
 * The two are different things and only one is a defect:</p>
 *
 * <ul>
 *   <li><em>Timer shape,</em> {@code if (timer <= 0) zero the dependents}: the constructor decides
 *       that a phase <em>ended</em>, which is a tick branch's job. The branch then never runs and
 *       whatever it should have armed, a cooldown, a backoff, a counter, or an anchor release, never
 *       happens. Not one constructor in this file does this. Expiry is only ever <em>reported</em>,
 *       by {@code expired()}, {@code actionElapsed()}, and {@code windowElapsed()}, and
 *       {@link GoblinPatronRuntime#advanceLoadedTimers} is the single exit.</li>
 *   <li><em>Identity shape,</em> {@code if (identity absent) zero the dependents}: the constructor
 *       asserts that two fields cannot disagree, which is the type's job. An anchor with no
 *       position has no dimension and no remaining ticks; an accord with no counterpart has no
 *       epoch and no shared challenger; a route with no destination has no dimension. Every
 *       coupling below is this shape and is deliberate.</li>
 * </ul>
 */
public record GoblinPatronState(
    int schemaVersion,
    CreatureKind kind,
    long authorityEpoch,
    long actionEpoch,
    Merchant merchant,
    Empowerment empowerment,
    Anchor anchor,
    Engagement engagement,
    Combat combat,
    Accord accord,
    Published published,
    Route route
) {
    public static final int SCHEMA_VERSION = GoblinPatronRules.STATE_SCHEMA_VERSION;

    public GoblinPatronState {
        kind = GoblinPatronRules.isPatron(kind) ? kind : CreatureKind.STONEBROKER;
        authorityEpoch = Math.max(0L, authorityEpoch);
        actionEpoch = Math.max(0L, actionEpoch);
        merchant = Objects.requireNonNull(merchant, "merchant");
        empowerment = Objects.requireNonNull(empowerment, "empowerment");
        anchor = Objects.requireNonNull(anchor, "anchor");
        engagement = Objects.requireNonNull(engagement, "engagement");
        combat = Objects.requireNonNull(combat, "combat");
        accord = Objects.requireNonNull(accord, "accord");
        published = Objects.requireNonNull(published, "published");
        route = Objects.requireNonNull(route, "route");
        schemaVersion = SCHEMA_VERSION;
    }

    // ---------------------------------------------------------------- merchant

    /** Merchant progression owned by the patron body, never by a human Villager data component. */
    public record Merchant(
        int level,
        int xp,
        int restocksToday,
        int restockSpacingTicks,
        long restockEpoch
    ) {
        public Merchant {
            level = GoblinPatronRules.clampMerchantLevel(level);
            xp = Math.max(0, xp);
            restocksToday = Math.clamp(restocksToday, 0, GoblinPatronRules.MAX_RESTOCKS_PER_DAY);
            restockSpacingTicks = GoblinPatronRules.clampRemaining(
                restockSpacingTicks, GoblinPatronRules.RESTOCK_SPACING_TICKS
            );
            restockEpoch = Math.max(0L, restockEpoch);
        }

        public static Merchant initial() {
            return new Merchant(GoblinPatronRules.MIN_MERCHANT_LEVEL, 0, 0, 0, 0L);
        }

        public Merchant withXp(final int updated) {
            final int safe = Math.max(0, updated);
            return new Merchant(
                GoblinPatronRules.levelForXp(safe), safe, restocksToday, restockSpacingTicks, restockEpoch
            );
        }

        public Merchant afterRestock() {
            return new Merchant(
                level, xp, restocksToday + 1, GoblinPatronRules.RESTOCK_SPACING_TICKS, restockEpoch + 1L
            );
        }

        public Merchant onNewDay() {
            return new Merchant(level, xp, 0, restockSpacingTicks, restockEpoch);
        }
    }

    // ---------------------------------------------------------------- empowerment

    /** Empowerment level 0 to 5 plus at most eight bounded relationship facts. */
    public record Empowerment(int level, List<OfferingFact> facts) {
        public Empowerment {
            level = Math.clamp(level, 0, GoblinPatronRules.MAX_EMPOWERMENT);
            final List<OfferingFact> safe = new ArrayList<>(
                facts == null ? List.<OfferingFact>of() : facts
            );
            if (safe.size() > GoblinPatronRules.MAX_OFFERING_FACTS) {
                facts = List.copyOf(safe.subList(0, GoblinPatronRules.MAX_OFFERING_FACTS));
            } else {
                facts = List.copyOf(safe);
            }
        }

        public static Empowerment none() {
            return new Empowerment(0, List.of());
        }

        public Optional<OfferingFact> factFor(final UUID player) {
            return facts.stream().filter(fact -> fact.player().equals(player)).findFirst();
        }
    }

    // ---------------------------------------------------------------- anchor

    /**
     * One expiring local anchor. Its meaning is the patron's kind: a Stonebroker claim anchor or a
     * Forgewarden ward anchor. It never loads a chunk and never marks a block as owned.
     *
     * <p>Expiry is reported by {@link #expired()}, never applied here.</p>
     */
    public record Anchor(Optional<BlockPos> position, Optional<String> dimension, int remainingTicks) {
        public Anchor {
            position = Objects.requireNonNull(position, "position").map(BlockPos::immutable);
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            if (position.isEmpty() || dimension.isEmpty()) {
                position = Optional.empty();
                dimension = Optional.empty();
                remainingTicks = 0;
            }
            remainingTicks = GoblinPatronRules.clampRemaining(
                remainingTicks, GoblinPatronRules.ANCHOR_EXPIRY_TICKS
            );
        }

        public static Anchor none() {
            return new Anchor(Optional.empty(), Optional.empty(), 0);
        }

        public static Anchor at(final BlockPos position, final String dimension) {
            return new Anchor(
                Optional.of(position), Optional.of(dimension), GoblinPatronRules.ANCHOR_EXPIRY_TICKS
            );
        }

        public boolean present() {
            return position.isPresent() && dimension.isPresent();
        }

        /** True on the tick the anchor runs out. The runtime turns this into an explicit release. */
        public boolean expired() {
            return present() && GoblinPatronRules.isDue(remainingTicks);
        }

        public Anchor tick() {
            return new Anchor(position, dimension, Math.max(0, remainingTicks - 1));
        }
    }

    // ---------------------------------------------------------------- engagement window

    /**
     * The single offering window: a Stonebroker parley or a Forgewarden commission. It carries no
     * ownership, no immunity, no follow, and no permanent peace.
     */
    public record Engagement(Optional<UUID> player, int remainingTicks, boolean breached) {
        public Engagement {
            player = Objects.requireNonNull(player, "player");
            if (player.isEmpty()) {
                remainingTicks = 0;
                breached = false;
            }
            remainingTicks = GoblinPatronRules.clampRemaining(
                remainingTicks, GoblinPatronRules.PARLEY_TICKS
            );
        }

        public static Engagement none() {
            return new Engagement(Optional.empty(), 0, false);
        }

        public static Engagement opened(final UUID player, final int ticks) {
            return new Engagement(Optional.of(player), ticks, false);
        }

        public boolean open() {
            return player.isPresent() && !GoblinPatronRules.isDue(remainingTicks);
        }

        /** True on the tick the window runs out. The runtime owns closing it. */
        public boolean windowElapsed() {
            return player.isPresent() && GoblinPatronRules.isDue(remainingTicks);
        }

        public Engagement tick() {
            return new Engagement(player, Math.max(0, remainingTicks - 1), breached);
        }
    }

    // ---------------------------------------------------------------- combat

    /**
     * One committed action, one action target, one stable challenger, and one recent direct
     * attacker. No target list, no threat graph, no missed-tick queue.
     */
    public record Combat(
        Action action,
        Optional<UUID> actionTarget,
        int tellRemainingTicks,
        int commitRemainingTicks,
        int recoveryRemainingTicks,
        Action lastCompleted,
        int signatureGapTicks,
        int secondaryGapTicks,
        int stanceRemainingTicks,
        Optional<UUID> challenger,
        Optional<UUID> recentAttacker,
        Optional<String> recentAttackerDimension,
        int recentAttackerTicks,
        ReleaseReason lastRelease,
        boolean withdrawing,
        int arrowsRemaining
    ) {
        public Combat {
            action = action == null ? Action.IDLE : action;
            actionTarget = Objects.requireNonNull(actionTarget, "actionTarget");
            lastCompleted = lastCompleted == null ? Action.IDLE : lastCompleted;
            challenger = Objects.requireNonNull(challenger, "challenger");
            recentAttacker = Objects.requireNonNull(recentAttacker, "recentAttacker");
            recentAttackerDimension = Objects.requireNonNull(
                recentAttackerDimension, "recentAttackerDimension"
            ).filter(value -> !value.isBlank());
            if (recentAttacker.isEmpty() || recentAttackerDimension.isEmpty()) {
                recentAttacker = Optional.empty();
                recentAttackerDimension = Optional.empty();
                recentAttackerTicks = 0;
            }
            lastRelease = lastRelease == null ? ReleaseReason.NONE : lastRelease;
            tellRemainingTicks = GoblinPatronRules.clampDeadline(tellRemainingTicks);
            commitRemainingTicks = GoblinPatronRules.clampDeadline(commitRemainingTicks);
            recoveryRemainingTicks = GoblinPatronRules.clampDeadline(recoveryRemainingTicks);
            signatureGapTicks = GoblinPatronRules.clampDeadline(signatureGapTicks);
            secondaryGapTicks = GoblinPatronRules.clampDeadline(secondaryGapTicks);
            stanceRemainingTicks = GoblinPatronRules.clampRemaining(
                stanceRemainingTicks, GoblinPatronRules.WARD_STANCE_TICKS
            );
            recentAttackerTicks = GoblinPatronRules.clampDeadline(recentAttackerTicks);
            arrowsRemaining = Math.clamp(arrowsRemaining, 0, GoblinPatronRules.volleyArrows(
                GoblinPatronRules.Phase.PHASE_THREE
            ));
            // Deliberately no timer-shape reconciliation. If this constructor ended the action when
            // the commit timer reached zero, the tick branch that owns ending it would never run,
            // the recovery it must arm would never be armed, and the completed action would never
            // be recorded. The recent-attacker coupling above is the identity shape instead: an
            // attacker with no dimension is not a half-valid attacker, it is no attacker.
        }

        public static Combat none() {
            return new Combat(Action.IDLE, Optional.empty(), 0, 0, 0, Action.IDLE, 0, 0, 0,
                Optional.empty(), Optional.empty(), Optional.empty(), 0, ReleaseReason.NONE,
                false, 0);
        }

        public boolean committed() {
            return action != Action.IDLE && action != Action.TRADE_HOLD;
        }

        /** True while the visible tell is running and no effect may commit yet. */
        public boolean telling() {
            return committed() && !GoblinPatronRules.isDue(tellRemainingTicks);
        }

        /** True on the tick the committed action runs out. The runtime owns the transition. */
        public boolean actionElapsed() {
            return committed() && GoblinPatronRules.isDue(commitRemainingTicks);
        }

        public boolean recovering() {
            return !GoblinPatronRules.isDue(recoveryRemainingTicks);
        }

        public boolean stanceActive() {
            return !GoblinPatronRules.isDue(stanceRemainingTicks);
        }

        public Combat tick() {
            return new Combat(
                action, actionTarget,
                Math.max(0, tellRemainingTicks - 1),
                Math.max(0, commitRemainingTicks - 1),
                Math.max(0, recoveryRemainingTicks - 1),
                lastCompleted,
                Math.max(0, signatureGapTicks - 1),
                Math.max(0, secondaryGapTicks - 1),
                Math.max(0, stanceRemainingTicks - 1),
                challenger, recentAttacker, recentAttackerDimension,
                Math.max(0, recentAttackerTicks - 1),
                lastRelease, withdrawing, arrowsRemaining
            );
        }

        /** Ends the action and arms its recovery. Only the runtime calls this. */
        public Combat completed(final Action finished, final int recovery, final int signatureGap) {
            return new Combat(Action.IDLE, Optional.empty(), 0, 0, recovery, finished,
                signatureGap > 0 ? signatureGap : signatureGapTicks, secondaryGapTicks,
                stanceRemainingTicks, challenger, recentAttacker, recentAttackerDimension,
                recentAttackerTicks, lastRelease, withdrawing, 0);
        }

        public Combat withChallenger(final Optional<UUID> updated, final ReleaseReason release) {
            return new Combat(action, actionTarget, tellRemainingTicks, commitRemainingTicks,
                recoveryRemainingTicks, lastCompleted, signatureGapTicks, secondaryGapTicks,
                stanceRemainingTicks, updated, recentAttacker, recentAttackerDimension,
                recentAttackerTicks, release, withdrawing, arrowsRemaining);
        }

        public Combat withWithdrawing(final boolean updated) {
            return new Combat(action, actionTarget, tellRemainingTicks, commitRemainingTicks,
                recoveryRemainingTicks, lastCompleted, signatureGapTicks, secondaryGapTicks,
                stanceRemainingTicks, challenger, recentAttacker, recentAttackerDimension,
                recentAttackerTicks, lastRelease, updated, arrowsRemaining);
        }
    }

    // ---------------------------------------------------------------- accord

    /** At most one mutual counterpart accord plus at most one shared challenger mark. */
    public record Accord(
        Optional<UUID> counterpart,
        Optional<CreatureKind> counterpartKind,
        Optional<String> dimension,
        long counterpartEpoch,
        long accordEpoch,
        int remainingTicks,
        Optional<UUID> sharedChallenger
    ) {
        public Accord {
            counterpart = Objects.requireNonNull(counterpart, "counterpart");
            counterpartKind = Objects.requireNonNull(counterpartKind, "counterpartKind")
                .filter(GoblinPatronRules::isPatron);
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            sharedChallenger = Objects.requireNonNull(sharedChallenger, "sharedChallenger");
            if (counterpart.isEmpty() || counterpartKind.isEmpty() || dimension.isEmpty()) {
                counterpart = Optional.empty();
                counterpartKind = Optional.empty();
                dimension = Optional.empty();
                sharedChallenger = Optional.empty();
                remainingTicks = 0;
                accordEpoch = 0L;
                counterpartEpoch = 0L;
            }
            counterpartEpoch = Math.max(0L, counterpartEpoch);
            accordEpoch = Math.max(0L, accordEpoch);
            remainingTicks = GoblinPatronRules.clampRemaining(
                remainingTicks, GoblinPatronRules.ACCORD_EXPIRY_TICKS
            );
        }

        public static Accord none() {
            return new Accord(Optional.empty(), Optional.empty(), Optional.empty(), 0L, 0L, 0,
                Optional.empty());
        }

        public static Accord formed(
            final UUID counterpart,
            final CreatureKind counterpartKind,
            final String dimension,
            final long counterpartEpoch,
            final long accordEpoch
        ) {
            return new Accord(Optional.of(counterpart), Optional.of(counterpartKind),
                Optional.of(dimension), counterpartEpoch, accordEpoch,
                GoblinPatronRules.ACCORD_EXPIRY_TICKS, Optional.empty());
        }

        public boolean present() {
            return counterpart.isPresent() && !GoblinPatronRules.isDue(remainingTicks);
        }

        /** True on the tick the accord runs out. The runtime owns clearing the derived link. */
        public boolean expired() {
            return counterpart.isPresent() && GoblinPatronRules.isDue(remainingTicks);
        }

        public Accord tick() {
            return new Accord(counterpart, counterpartKind, dimension, counterpartEpoch, accordEpoch,
                Math.max(0, remainingTicks - 1), sharedChallenger);
        }

        public Accord withSharedChallenger(final Optional<UUID> updated) {
            return new Accord(counterpart, counterpartKind, dimension, counterpartEpoch, accordEpoch,
                remainingTicks, updated);
        }

        public Accord refreshed() {
            return new Accord(counterpart, counterpartKind, dimension, counterpartEpoch, accordEpoch,
                GoblinPatronRules.ACCORD_EXPIRY_TICKS, sharedChallenger);
        }
    }

    // ---------------------------------------------------------------- published result

    /** The summary of the single currently published immutable directive. */
    public record Published(
        long resultEpoch,
        Optional<GoblinPatronRules.DirectiveKind> resultKind,
        Optional<BlockPos> anchor,
        Optional<String> dimension,
        Optional<UUID> challenger,
        int remainingTicks
    ) {
        public Published {
            resultKind = Objects.requireNonNull(resultKind, "resultKind");
            anchor = Objects.requireNonNull(anchor, "anchor").map(BlockPos::immutable);
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            challenger = Objects.requireNonNull(challenger, "challenger");
            if (resultKind.isEmpty() || anchor.isEmpty() || dimension.isEmpty()) {
                resultKind = Optional.empty();
                anchor = Optional.empty();
                dimension = Optional.empty();
                challenger = Optional.empty();
                remainingTicks = 0;
                resultEpoch = 0L;
            }
            resultEpoch = Math.max(0L, resultEpoch);
            remainingTicks = GoblinPatronRules.clampRemaining(
                remainingTicks, GoblinPatronRules.DIRECTIVE_EXPIRY_TICKS
            );
        }

        public static Published none() {
            return new Published(0L, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), 0);
        }

        public boolean present() {
            return resultKind.isPresent() && !GoblinPatronRules.isDue(remainingTicks);
        }

        /** True on the tick the published result runs out. The runtime owns withdrawing it. */
        public boolean expired() {
            return resultKind.isPresent() && GoblinPatronRules.isDue(remainingTicks);
        }

        public Published tick() {
            return new Published(resultEpoch, resultKind, anchor, dimension, challenger,
                Math.max(0, remainingTicks - 1));
        }
    }

    // ---------------------------------------------------------------- route

    /** The navigation lease, its owner epoch, and the classified route-failure backoff. */
    public record Route(
        Optional<BlockPos> destination,
        Optional<String> dimension,
        long ownerActionEpoch,
        RouteFailure lastFailure,
        int failureCount,
        int retryRemainingTicks,
        boolean stuck
    ) {
        public Route {
            destination = Objects.requireNonNull(destination, "destination").map(BlockPos::immutable);
            dimension = Objects.requireNonNull(dimension, "dimension").filter(value -> !value.isBlank());
            if (destination.isEmpty() || dimension.isEmpty()) {
                destination = Optional.empty();
                dimension = Optional.empty();
            }
            lastFailure = lastFailure == null ? RouteFailure.NONE : lastFailure;
            failureCount = Math.clamp(failureCount, 0, GoblinPatronRules.MAX_ROUTE_FAILURES);
            retryRemainingTicks = GoblinPatronRules.clampRemaining(
                retryRemainingTicks, GoblinPatronRules.ROUTE_BACKOFF_TICKS
            );
            ownerActionEpoch = Math.max(0L, ownerActionEpoch);
            if (failureCount == 0) {
                lastFailure = RouteFailure.NONE;
                stuck = false;
            }
        }

        public static Route none() {
            return new Route(Optional.empty(), Optional.empty(), 0L, RouteFailure.NONE, 0, 0, false);
        }

        public boolean held() {
            return destination.isPresent();
        }

        public Route tick() {
            return new Route(destination, dimension, ownerActionEpoch, lastFailure, failureCount,
                Math.max(0, retryRemainingTicks - 1), stuck);
        }

        /** A successful movement or safe position clears the prior classified failures. */
        public Route succeeded() {
            return new Route(destination, dimension, ownerActionEpoch, RouteFailure.NONE, 0, 0, false);
        }

        public Route failed(final RouteFailure failure) {
            final int count = GoblinPatronRules.nextFailureCount(failureCount, failure);
            final boolean exhausted = count >= GoblinPatronRules.MAX_ROUTE_FAILURES;
            return new Route(
                exhausted ? Optional.empty() : destination,
                exhausted ? Optional.empty() : dimension,
                ownerActionEpoch,
                failure,
                count,
                GoblinPatronRules.backoffTicks(count),
                failure == RouteFailure.STUCK
            );
        }
    }

    // ================================================================ factories

    public static GoblinPatronState empty(final CreatureKind kind) {
        return new GoblinPatronState(SCHEMA_VERSION, kind, 1L, 1L, Merchant.initial(),
            Empowerment.none(), Anchor.none(), Engagement.none(), Combat.none(), Accord.none(),
            Published.none(), Route.none());
    }

    public GoblinPatronState withMerchant(final Merchant updated) {
        return new GoblinPatronState(schemaVersion, kind, authorityEpoch, actionEpoch, updated,
            empowerment, anchor, engagement, combat, accord, published, route);
    }

    public GoblinPatronState withEmpowerment(final Empowerment updated) {
        return new GoblinPatronState(schemaVersion, kind, authorityEpoch, actionEpoch, merchant,
            updated, anchor, engagement, combat, accord, published, route);
    }

    public GoblinPatronState withAnchor(final Anchor updated) {
        return new GoblinPatronState(schemaVersion, kind, authorityEpoch, actionEpoch, merchant,
            empowerment, updated, engagement, combat, accord, published, route);
    }

    public GoblinPatronState withEngagement(final Engagement updated) {
        return new GoblinPatronState(schemaVersion, kind, authorityEpoch, actionEpoch, merchant,
            empowerment, anchor, updated, combat, accord, published, route);
    }

    public GoblinPatronState withCombat(final Combat updated) {
        return new GoblinPatronState(schemaVersion, kind, authorityEpoch, actionEpoch, merchant,
            empowerment, anchor, engagement, updated, accord, published, route);
    }

    public GoblinPatronState withAccord(final Accord updated) {
        return new GoblinPatronState(schemaVersion, kind, authorityEpoch, actionEpoch, merchant,
            empowerment, anchor, engagement, combat, updated, published, route);
    }

    public GoblinPatronState withPublished(final Published updated) {
        return new GoblinPatronState(schemaVersion, kind, authorityEpoch, actionEpoch, merchant,
            empowerment, anchor, engagement, combat, accord, updated, route);
    }

    public GoblinPatronState withRoute(final Route updated) {
        return new GoblinPatronState(schemaVersion, kind, authorityEpoch, actionEpoch, merchant,
            empowerment, anchor, engagement, combat, accord, published, updated);
    }

    /** A new action epoch invalidates every in-flight commit, arrow, surge, and navigation lease. */
    public GoblinPatronState withNextActionEpoch() {
        return new GoblinPatronState(schemaVersion, kind, authorityEpoch, actionEpoch + 1L, merchant,
            empowerment, anchor, engagement, combat, accord, published, route);
    }

    /** A new authority epoch invalidates every derived accord and published result at once. */
    public GoblinPatronState withNextAuthorityEpoch() {
        return new GoblinPatronState(schemaVersion, kind, authorityEpoch + 1L, actionEpoch, merchant,
            empowerment, anchor, engagement, combat, accord, published, route);
    }

    /**
     * Everything a dimension change, unload, or death invalidates at once: local anchor, accord,
     * challenger, action, route, and published result. Merchant progression, empowerment, bounded
     * relationship facts, and the schema version deliberately survive.
     */
    public GoblinPatronState releasedLocalState() {
        return new GoblinPatronState(schemaVersion, kind, authorityEpoch + 1L, actionEpoch + 1L,
            merchant, empowerment, Anchor.none(), Engagement.none(), Combat.none(), Accord.none(),
            Published.none(), Route.none());
    }

    // ================================================================ persistence

    /** Compact fixed-cardinality encoding; a populated state stays below the declared ceiling. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        tag.putString("Kind", kind.name().toLowerCase(Locale.ROOT));
        tag.putLong("Authority", authorityEpoch);
        tag.putLong("ActionEpoch", actionEpoch);
        tag.putInt("Level", merchant.level());
        tag.putInt("Xp", merchant.xp());
        tag.putInt("Restocks", merchant.restocksToday());
        tag.putInt("RestockGap", merchant.restockSpacingTicks());
        tag.putLong("RestockEpoch", merchant.restockEpoch());
        tag.putInt("Empowerment", empowerment.level());
        final ListTag facts = new ListTag();
        for (final OfferingFact fact : empowerment.facts()) {
            final CompoundTag entry = new CompoundTag();
            entry.putString("Player", fact.player().toString());
            entry.putInt("Standing", fact.standing());
            entry.putString("Event", fact.event().name().toLowerCase(Locale.ROOT));
            entry.putInt("Remaining", fact.remainingTicks());
            facts.add(entry);
        }
        tag.put("Facts", facts);
        anchor.position().ifPresent(position -> tag.putLong("AnchorPos", position.asLong()));
        anchor.dimension().ifPresent(dimension -> tag.putString("AnchorDim", dimension));
        tag.putInt("AnchorTicks", anchor.remainingTicks());
        engagement.player().ifPresent(player -> tag.putString("Window", player.toString()));
        tag.putInt("WindowTicks", engagement.remainingTicks());
        tag.putBoolean("WindowBreached", engagement.breached());
        tag.putString("Action", combat.action().name().toLowerCase(Locale.ROOT));
        combat.actionTarget().ifPresent(id -> tag.putString("ActionTarget", id.toString()));
        tag.putInt("Tell", combat.tellRemainingTicks());
        tag.putInt("Commit", combat.commitRemainingTicks());
        tag.putInt("Recovery", combat.recoveryRemainingTicks());
        tag.putString("LastAction", combat.lastCompleted().name().toLowerCase(Locale.ROOT));
        tag.putInt("SignatureGap", combat.signatureGapTicks());
        tag.putInt("SecondaryGap", combat.secondaryGapTicks());
        tag.putInt("Stance", combat.stanceRemainingTicks());
        combat.challenger().ifPresent(id -> tag.putString("Challenger", id.toString()));
        combat.recentAttacker().ifPresent(id -> tag.putString("Attacker", id.toString()));
        combat.recentAttackerDimension().ifPresent(dimension -> tag.putString("AttackerDim", dimension));
        tag.putInt("AttackerTicks", combat.recentAttackerTicks());
        tag.putString("Release", combat.lastRelease().name().toLowerCase(Locale.ROOT));
        tag.putBoolean("Withdrawing", combat.withdrawing());
        tag.putInt("Arrows", combat.arrowsRemaining());
        accord.counterpart().ifPresent(id -> tag.putString("Counterpart", id.toString()));
        accord.counterpartKind().ifPresent(other ->
            tag.putString("CounterpartKind", other.name().toLowerCase(Locale.ROOT)));
        accord.dimension().ifPresent(dimension -> tag.putString("AccordDim", dimension));
        tag.putLong("CounterpartEpoch", accord.counterpartEpoch());
        tag.putLong("AccordEpoch", accord.accordEpoch());
        tag.putInt("AccordTicks", accord.remainingTicks());
        accord.sharedChallenger().ifPresent(id -> tag.putString("Marked", id.toString()));
        tag.putLong("ResultEpoch", published.resultEpoch());
        published.resultKind().ifPresent(result ->
            tag.putString("ResultKind", result.name().toLowerCase(Locale.ROOT)));
        published.anchor().ifPresent(position -> tag.putLong("ResultPos", position.asLong()));
        published.dimension().ifPresent(dimension -> tag.putString("ResultDim", dimension));
        published.challenger().ifPresent(id -> tag.putString("ResultChallenger", id.toString()));
        tag.putInt("ResultTicks", published.remainingTicks());
        route.destination().ifPresent(position -> tag.putLong("DestPos", position.asLong()));
        route.dimension().ifPresent(dimension -> tag.putString("DestDim", dimension));
        tag.putLong("RouteOwner", route.ownerActionEpoch());
        tag.putString("RouteFail", route.lastFailure().name().toLowerCase(Locale.ROOT));
        tag.putInt("RouteFails", route.failureCount());
        tag.putInt("RouteRetry", route.retryRemainingTicks());
        tag.putBoolean("Stuck", route.stuck());
        return tag;
    }

    /**
     * Reads schema version 1 for one exact kind.
     *
     * <p>A missing payload, a wrong-kind payload, or an unknown newer schema resets to a safe idle
     * patron rather than guessing; the caller keeps the entity's own vanilla and merchant data
     * either way. A same-schema malformed payload has its coupled action window, invalid challenger
     * and accord fields, and extreme deadlines reset together, not one at a time.</p>
     *
     * <p>A committed action, its route lease, and its arrows are dropped on load unconditionally:
     * an action never spans a tick, so it can never span an unload. Elapsed deadlines expire in
     * constant time and nothing replays.</p>
     */
    public static GoblinPatronState read(
        final CompoundTag tag,
        final CreatureKind expectedKind,
        final String currentDimension
    ) {
        final GoblinPatronState fallback = empty(expectedKind);
        if (tag == null || tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return fallback;
        }
        final String storedKind = tag.getStringOr("Kind", "");
        if (!storedKind.equalsIgnoreCase(expectedKind.name())) {
            return fallback;
        }
        final Merchant merchant = new Merchant(
            tag.getIntOr("Level", GoblinPatronRules.MIN_MERCHANT_LEVEL),
            tag.getIntOr("Xp", 0),
            tag.getIntOr("Restocks", 0),
            tag.getIntOr("RestockGap", 0),
            tag.getLongOr("RestockEpoch", 0L)
        );
        final List<OfferingFact> facts = new ArrayList<>();
        final ListTag storedFacts = tag.getListOrEmpty("Facts");
        for (int index = 0; index < Math.min(storedFacts.size(), GoblinPatronRules.MAX_OFFERING_FACTS); index++) {
            final CompoundTag entry = storedFacts.getCompoundOrEmpty(index);
            readUuid(entry, "Player").ifPresent(player -> facts.add(new OfferingFact(
                player,
                entry.getIntOr("Standing", 0),
                parseEvent(entry.getStringOr("Event", "")),
                entry.getIntOr("Remaining", 0)
            )));
        }
        final Empowerment empowerment = new Empowerment(
            tag.getIntOr("Empowerment", 0),
            facts.stream().filter(fact -> !fact.expired()).limit(GoblinPatronRules.MAX_OFFERING_FACTS).toList()
        );
        final Anchor anchor = new Anchor(
            readPosition(tag, "AnchorPos"),
            readDimension(tag, "AnchorDim").filter(dimension -> dimension.equals(currentDimension)),
            tag.getIntOr("AnchorTicks", 0)
        );
        final Engagement engagement = new Engagement(
            readUuid(tag, "Window"),
            tag.getIntOr("WindowTicks", 0),
            tag.getBooleanOr("WindowBreached", false)
        );
        // Coupled repair at the load seam: an action, its target, its arrows, and its route lease
        // are dropped together rather than allowing a half-valid combination to survive a reload.
        final Combat combat = new Combat(
            Action.IDLE, Optional.empty(), 0, 0,
            GoblinPatronRules.clampDeadline(tag.getIntOr("Recovery", 0)),
            parseAction(tag.getStringOr("LastAction", "")),
            GoblinPatronRules.clampDeadline(tag.getIntOr("SignatureGap", 0)),
            GoblinPatronRules.clampDeadline(tag.getIntOr("SecondaryGap", 0)),
            0,
            readUuid(tag, "Challenger"),
            readUuid(tag, "Attacker"),
            readDimension(tag, "AttackerDim").filter(dimension -> dimension.equals(currentDimension)),
            GoblinPatronRules.clampDeadline(tag.getIntOr("AttackerTicks", 0)),
            parseRelease(tag.getStringOr("Release", "")),
            tag.getBooleanOr("Withdrawing", false),
            0
        );
        final Accord accord = new Accord(
            readUuid(tag, "Counterpart"),
            parseKind(tag.getStringOr("CounterpartKind", "")),
            readDimension(tag, "AccordDim").filter(dimension -> dimension.equals(currentDimension)),
            tag.getLongOr("CounterpartEpoch", 0L),
            tag.getLongOr("AccordEpoch", 0L),
            tag.getIntOr("AccordTicks", 0),
            readUuid(tag, "Marked")
        );
        final Published published = new Published(
            tag.getLongOr("ResultEpoch", 0L),
            parseDirective(tag.getStringOr("ResultKind", "")),
            readPosition(tag, "ResultPos"),
            readDimension(tag, "ResultDim").filter(dimension -> dimension.equals(currentDimension)),
            readUuid(tag, "ResultChallenger"),
            tag.getIntOr("ResultTicks", 0)
        );
        return new GoblinPatronState(
            SCHEMA_VERSION,
            expectedKind,
            Math.max(1L, tag.getLongOr("Authority", 1L)),
            Math.max(1L, tag.getLongOr("ActionEpoch", 1L)) + 1L,
            merchant,
            empowerment,
            anchor,
            engagement,
            combat,
            accord,
            published,
            Route.none()
        );
    }

    /**
     * Conservative 1.4 patron migration. The old shared empowerment counter is the only semantic
     * fact a pre-F12 patron carried, and the old randomly assigned Goblin profession contributes
     * nothing but a deterministic offer seed and is then discarded. Nothing is invented.
     */
    public static GoblinPatronState migrateLegacy(
        final CreatureKind kind,
        final int legacyEmpowerment,
        final int legacyVillagerXp
    ) {
        return empty(kind)
            .withEmpowerment(new Empowerment(legacyEmpowerment, List.of()))
            .withMerchant(Merchant.initial().withXp(Math.max(0, legacyVillagerXp)));
    }

    private static Action parseAction(final String value) {
        for (final Action candidate : Action.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        return Action.IDLE;
    }

    private static ReleaseReason parseRelease(final String value) {
        for (final ReleaseReason candidate : ReleaseReason.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        return ReleaseReason.NONE;
    }

    private static OfferingEvent parseEvent(final String value) {
        for (final OfferingEvent candidate : OfferingEvent.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        return OfferingEvent.NONE;
    }

    private static Optional<CreatureKind> parseKind(final String value) {
        for (final CreatureKind candidate : CreatureKind.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Optional<GoblinPatronRules.DirectiveKind> parseDirective(final String value) {
        for (final GoblinPatronRules.DirectiveKind candidate : GoblinPatronRules.DirectiveKind.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> readPosition(final CompoundTag tag, final String key) {
        final long stored = tag.getLongOr(key, Long.MIN_VALUE);
        return stored == Long.MIN_VALUE ? Optional.empty() : Optional.of(BlockPos.of(stored));
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
