package com.kadamitas.warlockery.entity.behavior;

/**
 * A periodic trigger: how often work may run, and how long it has been since it last ran.
 *
 * <p>Distinct from {@link PhaseTimer}, which runs a phase once. A cadence never ends anything, so it
 * has no expiry to hand back; the question it answers is only whether work is {@link #due()}.</p>
 *
 * <p>{@link #arm()} is separate from {@link #step()} on purpose. The families that got this wrong
 * armed the cadence inside the success branch, so a run that found nothing left the cadence due and
 * the same fruitless search repeated every tick. Arming records that the work <em>ran</em>, not that
 * it succeeded.</p>
 *
 * @param period ticks between runs, at least one
 * @param sinceLast ticks elapsed since the last run, saturating at the period
 */
public record Cadence(int period, int sinceLast) {

    public Cadence {
        if (period < 1) {
            throw new IllegalArgumentException("a cadence period must be at least one: " + period);
        }
        if (sinceLast < 0) {
            throw new IllegalArgumentException("elapsed ticks must not be negative: " + sinceLast);
        }
        sinceLast = Math.min(sinceLast, period);
    }

    /** A cadence that is due at once, for work that should run on the first tick it is offered. */
    public static Cadence every(final int period) {
        return new Cadence(period, period);
    }

    /** A cadence that has just run, so the next run is a full period away. */
    public static Cadence armed(final int period) {
        return new Cadence(period, 0);
    }

    public boolean due() {
        return sinceLast >= period;
    }

    public int untilDue() {
        return Math.max(0, period - sinceLast);
    }

    /** Advances one tick. Saturates at the period, so a long idle cadence cannot overflow. */
    public Cadence step() {
        return due() ? this : new Cadence(period, sinceLast + 1);
    }

    /** Records that the work ran, whatever it found. */
    public Cadence arm() {
        return new Cadence(period, 0);
    }

    /** Makes the work due on the next offer, for an event that should pre-empt the period. */
    public Cadence trigger() {
        return new Cadence(period, period);
    }

    /** The same schedule at a different period, keeping the elapsed count. */
    public Cadence withPeriod(final int newPeriod) {
        return new Cadence(newPeriod, sinceLast);
    }
}
