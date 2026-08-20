package com.kadamitas.warlockery.entity.behavior;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A total ordering over the concerns a family can act on, so hazard outranking combat outranking
 * routine is stated once instead of being spelled out in a switch.
 *
 * <p>Four committed families write this as {@code int priority(Phase phase, boolean hazard, ...)}: a
 * few leading {@code if (flag) return n;} guards, then an exhaustive switch mapping each phase to a
 * rank, then derived predicates such as {@code hazardPreempts} restated as
 * {@code priority(x, true) < priority(x, false)}. Nine more families inline the same ladder as an
 * if-chain in {@code tick}. The ordering is real and shared; the hand-written switch is where a new
 * phase gets forgotten and silently ranks as whatever the default arm returns.</p>
 *
 * <p>Building from an enum class takes declaration order as priority order and covers every constant
 * by construction, so a concern added to the enum cannot be left unranked.</p>
 *
 * <p><strong>This does not own the tick.</strong> {@link #select} is pure: it reports which concern
 * wins and runs nothing, so a family can keep its own control flow entirely and use the ladder only
 * to answer the ranking question. {@link #dispatch} is the opt-in half for families that would
 * rather hand over the per-tick branch. Neither is required by the other.</p>
 *
 * <p>The word <em>concern</em> is deliberate. Across the committed families "band" already means a
 * distance ring in three of them and a priority tier in one, and "priority" means four different
 * things, so neither word could be reused without importing the ambiguity.</p>
 *
 * @param <C> the family's own concern type, most urgent first
 */
public final class PriorityLadder<C> {

    private final List<C> order;
    private final Map<C, Integer> ranks;

    private PriorityLadder(final List<C> order, final Map<C, Integer> ranks) {
        this.order = order;
        this.ranks = ranks;
    }

    /** Declaration order is priority order, and every constant is covered. */
    public static <C extends Enum<C>> PriorityLadder<C> ofEnum(final Class<C> concerns) {
        final List<C> order = List.of(concerns.getEnumConstants());
        final Map<C, Integer> ranks = new EnumMap<>(concerns);
        for (int rank = 0; rank < order.size(); rank++) {
            ranks.put(order.get(rank), rank);
        }
        return new PriorityLadder<>(order, Map.copyOf(ranks));
    }

    /** An explicit order, for a concern type that is not an enum or a deliberate subset. */
    public static <C> PriorityLadder<C> of(final List<C> mostUrgentFirst) {
        final List<C> order = List.copyOf(mostUrgentFirst);
        final Map<C, Integer> ranks = new java.util.LinkedHashMap<>();
        for (int rank = 0; rank < order.size(); rank++) {
            if (ranks.putIfAbsent(order.get(rank), rank) != null) {
                throw new IllegalArgumentException("a concern is ranked twice: " + order.get(rank));
            }
        }
        return new PriorityLadder<>(order, Map.copyOf(ranks));
    }

    /** Most urgent first. */
    public List<C> order() {
        return order;
    }

    /** Zero is the most urgent. */
    public int rank(final C concern) {
        final Integer rank = ranks.get(concern);
        if (rank == null) {
            throw new IllegalArgumentException("this ladder does not rank " + concern);
        }
        return rank;
    }

    public boolean outranks(final C left, final C right) {
        return rank(left) < rank(right);
    }

    public Comparator<C> byUrgency() {
        return Comparator.comparingInt(this::rank);
    }

    /** The most urgent of whatever is currently applicable. */
    public Optional<C> mostUrgent(final Collection<C> active) {
        return active.stream().min(byUrgency());
    }

    /** One concern paired with the test for whether it applies and the work it would do. */
    public record Rung<C, X>(C concern, Predicate<X> applies, Consumer<X> run) {
        public Rung {
            if (concern == null || applies == null || run == null) {
                throw new IllegalArgumentException("a rung needs a concern, a test and an action");
            }
        }

        /** A rung for a family that only wants {@link PriorityLadder#select}, never dispatch. */
        public static <C, X> Rung<C, X> inspectOnly(final C concern, final Predicate<X> applies) {
            return new Rung<>(concern, applies, _ -> { });
        }
    }

    /**
     * The most urgent rung that applies, having run nothing at all. A family that keeps its own
     * dispatch uses this and then does whatever it likes with the answer.
     */
    public <X> Optional<Rung<C, X>> select(final List<Rung<C, X>> rungs, final X context) {
        final List<Rung<C, X>> ordered = new ArrayList<>(rungs);
        ordered.sort(Comparator.comparingInt(rung -> rank(rung.concern())));
        return ordered.stream().filter(rung -> rung.applies().test(context)).findFirst();
    }

    /**
     * Selects and then runs, for a family that wants the ladder to drive the tick. Returns what ran,
     * or empty when nothing applied. Entirely optional: {@link #select} is the whole primitive and
     * this is two lines on top of it.
     */
    public <X> Optional<C> dispatch(final List<Rung<C, X>> rungs, final X context) {
        final Optional<Rung<C, X>> chosen = select(rungs, context);
        chosen.ifPresent(rung -> rung.run().accept(context));
        return chosen.map(Rung::concern);
    }

    @Override
    public String toString() {
        return "PriorityLadder" + order;
    }
}
