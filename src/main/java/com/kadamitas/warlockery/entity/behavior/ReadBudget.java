package com.kadamitas.warlockery.entity.behavior;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A world-read allowance that is spent before the value it fetched can be judged.
 *
 * <p>Hand-rolled budgets counted a read only once a candidate passed its filter. A rejected
 * candidate then cost a real world lookup and was charged nothing, so the declared cap bounded the
 * number of <em>accepted</em> candidates rather than the number of reads, and the scan could touch
 * the entire envelope while reporting that it had stayed under budget. The cap could never bind.</p>
 *
 * <p>Here the only way to obtain a value is {@link #read}, which charges first and calls the reader
 * second. There is no accessor that returns a value without spending, so {@link #spent()} is the
 * real cost by construction rather than by discipline.</p>
 *
 * <p>This is a short lived per scan value, not something to hold across ticks or subclass.</p>
 */
public final class ReadBudget {

    private final int cap;
    private int spent;

    private ReadBudget(final int cap) {
        this.cap = cap;
    }

    public static ReadBudget of(final int cap) {
        if (cap < 0) {
            throw new IllegalArgumentException("a read cap must not be negative: " + cap);
        }
        return new ReadBudget(cap);
    }

    public int cap() {
        return cap;
    }

    /** Reads actually paid for, including every read whose value was then rejected. */
    public int spent() {
        return spent;
    }

    public int remaining() {
        return cap - spent;
    }

    public boolean exhausted() {
        return spent >= cap;
    }

    /**
     * Charges one read and returns what the reader produced, or empty when nothing is left to
     * spend. The charge lands before the reader runs, so an exception from the reader still costs
     * what it consumed.
     */
    public <T> Optional<T> read(final Supplier<T> reader) {
        if (exhausted()) {
            return Optional.empty();
        }
        spent++;
        return Optional.ofNullable(reader.get());
    }

    /**
     * Charges one read, then reports whether what it fetched satisfies the filter. A false answer
     * costs exactly the same as a true one, which is the property the hand-rolled budgets lacked.
     */
    public <T> boolean accepts(final Supplier<T> reader, final Predicate<? super T> filter) {
        return read(reader).filter(filter).isPresent();
    }

    /**
     * Charges one read without fetching anything, for callers whose read is a side effecting engine
     * call such as a line of sight trace. Returns false when the budget is already spent, in which
     * case the caller must not perform the read.
     */
    public boolean charge() {
        if (exhausted()) {
            return false;
        }
        spent++;
        return true;
    }

    @Override
    public String toString() {
        return "ReadBudget[spent=" + spent + " of " + cap + "]";
    }
}
