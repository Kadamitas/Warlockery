package com.kadamitas.warlockery.util;

import java.util.Optional;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Assertions over optional recorded state.
 *
 * <p>{@code GameTestHelper.assertValueEqual} dereferences its first argument, so handing it
 * {@code optional.orElse(null)} turns "the fixture recorded nothing" into a
 * {@link NullPointerException}. The framework then reports that as an unknown internal error and
 * the assertion's own message, which says what the fixture was actually checking, is lost. These
 * helpers keep the comparison exactly as strict and report the empty case in words.</p>
 */
public final class GameTestAssertions {
    private GameTestAssertions() {
    }

    /** Fails naming {@code what} when nothing was recorded, then compares as usual. */
    public static <T> void assertPresentValueEqual(
        final GameTestHelper helper,
        final Optional<T> actual,
        final T expected,
        final String what
    ) {
        helper.assertTrue(actual.isPresent(), what + " (nothing was recorded)");
        helper.assertValueEqual(actual.get(), expected, what);
    }

    /** Both sides must have recorded something, and the two records must agree. */
    public static <T> void assertPresentValueEqual(
        final GameTestHelper helper,
        final Optional<T> actual,
        final Optional<T> expected,
        final String what
    ) {
        helper.assertTrue(actual.isPresent(), what + " (nothing was recorded)");
        helper.assertTrue(expected.isPresent(), what + " (nothing was recorded to compare against)");
        helper.assertValueEqual(actual.get(), expected.get(), what);
    }
}
