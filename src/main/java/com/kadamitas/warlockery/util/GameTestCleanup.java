package com.kadamitas.warlockery.util;

import com.kadamitas.warlockery.mixin.GameTestHelperAccessor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;

/** Fabric equivalent of Forge's helper cleanup callback. */
public final class GameTestCleanup {
    private GameTestCleanup() {
    }

    public static void add(final GameTestHelper helper, final Consumer<Boolean> cleanup) {
        final AtomicBoolean completed = new AtomicBoolean();
        ((GameTestHelperAccessor) helper).warlockery$getTestInfo().addListener(new GameTestListener() {
            private void finish(final boolean passed) {
                if (completed.compareAndSet(false, true)) {
                    cleanup.accept(passed);
                }
            }

            @Override public void testStructureLoaded(final GameTestInfo info) { }
            @Override public void testPassed(final GameTestInfo info, final GameTestRunner runner) { finish(true); }
            @Override public void testFailed(final GameTestInfo info, final GameTestRunner runner) { finish(false); }
            @Override public void testAddedForRerun(
                final GameTestInfo original,
                final GameTestInfo rerun,
                final GameTestRunner runner
            ) { }
        });
    }
}
