package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.UmbralSigilRules.Phase;
import com.kadamitas.warlockery.entity.behavior.Cadence;
import com.kadamitas.warlockery.entity.behavior.PhaseTimer;
import com.kadamitas.warlockery.entity.behavior.RouteRequest;
import com.kadamitas.warlockery.entity.behavior.Ticks;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/**
 * Versioned, fixed-cardinality Umbral Sigil meaning. Four facts survive a save: which phase is
 * running and for how many more loaded ticks, how many consecutive routes have failed, whether the
 * seal has already spent its one attempt, and how long until another seal may begin.
 *
 * <p>What is deliberately absent is as much of the contract as what is present. The appointed
 * subject, the snapshot centre, the three vertices and every live path are execution scratch and
 * are never written, so a reload cannot resume a seal against a player who has since walked away,
 * and a saved Sigil carries no reference to anybody. Every duration is a remaining loaded-tick
 * count and never an absolute world deadline, so unloading pauses meaning rather than expiring it.
 *
 * <p><strong>On the compact constructor.</strong> It clamps each field into its own declared range
 * and reconciles nothing across fields. In particular it never zeroes a dependent because a timer
 * reached zero: that is the defect shape where a constructor decides a phase ended, the tick branch
 * that owned ending it never observes the pair it tests for, and the cooldown, backoff or latch
 * that ending implies is silently never armed. Here the phase and its remaining ticks are not even
 * two fields: {@link PhaseTimer} makes the reconciled pair unrepresentable, a spent phase becomes
 * {@link PhaseTimer.Expired} rather than being tidied away, and the only exits are the named
 * transitions below, each of which exactly one tick branch in {@link UmbralSigilRuntime} calls.</p>
 */
public record UmbralSigilState(
    int schemaVersion,
    PhaseTimer<Phase> timer,
    RouteRequest route,
    int strikes,
    int cooldownTicks
) {
    public static final int SCHEMA_VERSION = 1;

    public UmbralSigilState {
        timer = Objects.requireNonNull(timer, "timer");
        route = Objects.requireNonNull(route, "route");
        strikes = Math.clamp(strikes, 0, UmbralSigilRules.MAX_STRIKES);
        cooldownTicks = Ticks.clampRemaining(cooldownTicks, UmbralSigilRules.SEAL_COOLDOWN_TICKS);
    }

    public static UmbralSigilState empty() {
        return new UmbralSigilState(
            SCHEMA_VERSION, PhaseTimer.none(), UmbralSigilRules.freshRoute(), 0, 0
        );
    }

    /** The phase as the design names it. {@link PhaseTimer.Idle} is the canonical dormant shape. */
    public Phase phase() {
        return timer.activePhase().orElse(Phase.DORMANT);
    }

    public int remainingTicks() {
        return timer.remaining();
    }

    public boolean struck() {
        return strikes >= UmbralSigilRules.MAX_STRIKES;
    }

    public UmbralSigilState withTimer(final PhaseTimer<Phase> updated) {
        return new UmbralSigilState(schemaVersion, updated, route, strikes, cooldownTicks);
    }

    public UmbralSigilState withRoute(final RouteRequest updated) {
        return new UmbralSigilState(schemaVersion, timer, updated, strikes, cooldownTicks);
    }

    public UmbralSigilState withStrikes(final int updated) {
        return new UmbralSigilState(schemaVersion, timer, route, updated, cooldownTicks);
    }

    public UmbralSigilState withCooldown(final int updated) {
        return new UmbralSigilState(schemaVersion, timer, route, strikes, updated);
    }

    /** Enters a named phase for its declared duration. The one way a phase ever begins. */
    public UmbralSigilState enter(final Phase next) {
        return withTimer(PhaseTimer.start(next, UmbralSigilRules.phaseTicks(next)));
    }

    /**
     * Begins a seal: the first vertex, a route ledger whose failure run is cleared but whose open
     * backoff window is carried across, and a fresh unspent attempt.
     */
    public UmbralSigilState startSeal() {
        return new UmbralSigilState(
            schemaVersion,
            PhaseTimer.start(Phase.INSCRIBE_1, UmbralSigilRules.INSCRIBE_TICKS),
            UmbralSigilRules.routeForNewSeal(route),
            0,
            cooldownTicks
        );
    }

    /**
     * Ends a seal, however it ended. Dormancy, an armed cooldown, a cleared failure run and a fresh
     * attempt in one write, so no half-cancelled seal is ever observable. The open backoff window
     * survives, because an ending is not evidence that the surroundings became routable.
     */
    public UmbralSigilState endSeal() {
        return new UmbralSigilState(
            schemaVersion,
            PhaseTimer.none(),
            new RouteRequest(route.cadence(), 0, route.backoffRemaining()),
            0,
            UmbralSigilRules.SEAL_COOLDOWN_TICKS
        );
    }

    /** Compact fixed-cardinality encoding, asserted below the declared state-size ceiling. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", schemaVersion);
        tag.putString("Phase", phase().name().toLowerCase(Locale.ROOT));
        tag.putInt("Remaining", remainingTicks());
        tag.putInt("RouteSince", route.cadence().sinceLast());
        tag.putInt("RouteFail", route.consecutiveFailures());
        tag.putInt("RouteBackoff", route.backoffRemaining());
        tag.putInt("Strikes", strikes);
        tag.putInt("Cooldown", cooldownTicks);
        return tag;
    }

    /**
     * Reads version 1. A missing or unknown schema resets to an empty dormancy.
     *
     * <p>A saved seal is normalised to recovery here rather than in the entity, so that no path
     * into this type, including a hand-edited or corrupted tag, can produce a Sigil that resumes
     * tracing, closes, or lands inside an open strike window on load. The spent-attempt latch is
     * deliberately preserved across that normalisation, so a save cycle can never hand a seal a
     * second attempt; the latch is cleared only by {@link #endSeal()}, which the recovery branch
     * reaches in the ordinary way.</p>
     */
    public static UmbralSigilState read(final CompoundTag tag) {
        if (tag == null || tag.getIntOr("Version", 0) != SCHEMA_VERSION) {
            return empty();
        }
        final Phase stored = parsePhase(tag.getStringOr("Phase", ""));
        final Phase restored = UmbralSigilRules.sealing(stored) ? Phase.RECOVER : stored;
        final int remaining = restored == stored
            ? Ticks.clampRemaining(tag.getIntOr("Remaining", 0), UmbralSigilRules.phaseTicks(stored))
            : UmbralSigilRules.RECOVER_TICKS;
        return new UmbralSigilState(
            SCHEMA_VERSION,
            restored == Phase.DORMANT ? PhaseTimer.none() : PhaseTimer.start(restored, remaining),
            new RouteRequest(
                new Cadence(
                    UmbralSigilRules.PATH_INTERVAL_TICKS,
                    tag.getIntOr("RouteSince", UmbralSigilRules.PATH_INTERVAL_TICKS)
                ),
                tag.getIntOr("RouteFail", 0),
                tag.getIntOr("RouteBackoff", 0)
            ),
            tag.getIntOr("Strikes", 0),
            tag.getIntOr("Cooldown", 0)
        );
    }

    private static Phase parsePhase(final String value) {
        for (final Phase candidate : Phase.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        return Phase.DORMANT;
    }
}
