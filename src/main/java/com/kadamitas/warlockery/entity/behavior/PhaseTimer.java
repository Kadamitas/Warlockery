package com.kadamitas.warlockery.entity.behavior;

import java.util.Optional;

/**
 * A phase and its remaining ticks, shaped so that nothing but an explicit tick branch can end a
 * phase.
 *
 * <p>The recurring defect this replaces is a record whose canonical constructor reconciled the two
 * fields, along the lines of {@code if (remaining <= 0) phase = NONE;}. The tick branch that owned
 * ending the phase then tested for {@code phase == ACTIVE && remaining == 0}, a pair that the
 * constructor had already destroyed, so the branch never ran and whatever it should have armed, a
 * cooldown, a backoff, a counter, an anchor clear, silently never happened.</p>
 *
 * <p>The fix is structural rather than advisory. {@link Running} rejects a remaining count below
 * one, so the reconciled pair {@code (ACTIVE, 0)} that those constructors existed to clean up cannot
 * be built in the first place. Reaching zero produces a {@link Expired} value instead, which still
 * names its phase and is a distinct state rather than a tidied away one. {@link #step()} on an
 * expired timer returns it unchanged, so a forgotten expiry stalls visibly and is caught by a test
 * rather than being absorbed. The single exit is {@link #endExpired()} or {@link #restart}, both of
 * which the owning tick branch must call by name.</p>
 *
 * <p>The variant set is sealed because it is closed by the domain: a countdown is not running, is
 * running, or has run out and is waiting for its owner. A fourth condition would be a bug.</p>
 *
 * <p>{@code P} is unconstrained, so a family may key phases by an enum, a record or anything else it
 * already has.</p>
 *
 * @param <P> the family's own phase type
 */
public sealed interface PhaseTimer<P> {

    /** No phase is running. */
    record Idle<P>() implements PhaseTimer<P> {}

    /** A phase with at least one tick left to run. */
    record Running<P>(P phase, int remaining) implements PhaseTimer<P> {
        public Running {
            if (phase == null) {
                throw new IllegalArgumentException("a running phase must be named");
            }
            if (remaining < 1) {
                throw new IllegalArgumentException(
                    "a running phase has at least one tick left, so " + remaining
                        + " is not representable; a phase that has run out is Expired");
            }
        }
    }

    /** A phase whose ticks are gone and whose ending its owner has not yet performed. */
    record Expired<P>(P phase) implements PhaseTimer<P> {
        public Expired {
            if (phase == null) {
                throw new IllegalArgumentException("an expired phase must be named");
            }
        }
    }

    /** No phase running. Spelled as the committed families spell their empty cadence. */
    static <P> PhaseTimer<P> none() {
        return new Idle<>();
    }

    /** Starts a phase. A non positive duration expires at once rather than being dropped. */
    static <P> PhaseTimer<P> start(final P phase, final int ticks) {
        return ticks < 1 ? new Expired<>(phase) : new Running<>(phase, ticks);
    }

    /**
     * Advances one tick. Running to its last tick becomes {@link Expired}; an unhandled expiry
     * stays expired rather than lapsing to idle.
     */
    default PhaseTimer<P> step() {
        return switch (this) {
            case Idle<P> idle -> idle;
            case Expired<P> expired -> expired;
            case Running<P>(final P phase, final int remaining) ->
                remaining == 1 ? new Expired<>(phase) : new Running<>(phase, remaining - 1);
        };
    }

    /** The phase that is running or has just run out, if any. */
    default Optional<P> activePhase() {
        return switch (this) {
            case Idle<P> _ -> Optional.empty();
            case Running<P>(final P phase, final int _) -> Optional.of(phase);
            case Expired<P>(final P phase) -> Optional.of(phase);
        };
    }

    /** The phase awaiting its ending, so a tick branch can dispatch on exactly what ran out. */
    default Optional<P> expiredPhase() {
        return this instanceof Expired<P>(final P phase) ? Optional.of(phase) : Optional.empty();
    }

    default boolean running() {
        return this instanceof Running<P>;
    }

    default boolean expired() {
        return this instanceof Expired<P>;
    }

    default boolean idle() {
        return this instanceof Idle<P>;
    }

    default int remaining() {
        return this instanceof Running<P>(final P _, final int remaining) ? remaining : 0;
    }

    /**
     * Ends an expired phase, leaving nothing running. Throws when the timer has not expired,
     * because ending a phase that is still running is the mistake this type exists to surface.
     */
    default PhaseTimer<P> endExpired() {
        if (!(this instanceof Expired<P>)) {
            throw new IllegalStateException("only an expired phase can be ended, not " + this);
        }
        return new Idle<>();
    }

    /**
     * Ends an expired phase and starts the next one in a single move, for the common case where the
     * branch that ends a phase arms its cooldown.
     */
    default PhaseTimer<P> restart(final P next, final int ticks) {
        if (!(this instanceof Expired<P>)) {
            throw new IllegalStateException("only an expired phase can be restarted, not " + this);
        }
        return start(next, ticks);
    }

    /** Abandons whatever is running, for cancellation such as the entity's target being lost. */
    default PhaseTimer<P> cancel() {
        return new Idle<>();
    }
}
