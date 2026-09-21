package com.kadamitas.warlockery.util;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;

/** Fabric equivalent of Forge's helper cleanup callback. */
public final class GameTestCleanup {
    // Test-only, read-only access: attach cleanup to the real vanilla test, without injecting
    // an accessor into GameTestHelper or replacing its lifecycle. Fail loudly on API drift.
    private static final Field TEST_INFO = testInfoField();

    private GameTestCleanup() {
    }

    public static void add(final GameTestHelper helper, final Consumer<Boolean> cleanup) {
        final AtomicBoolean completed = new AtomicBoolean();
        testInfo(helper).addListener(new GameTestListener() {
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

    private static Field testInfoField() {
        try {
            final Field field = GameTestHelper.class.getDeclaredField("testInfo");
            if (field.getType() != GameTestInfo.class || !field.trySetAccessible()) {
                throw new IllegalStateException("Cannot read vanilla GameTestHelper.testInfo for test cleanup");
            }
            return field;
        } catch (ReflectiveOperationException | SecurityException exception) {
            throw new IllegalStateException("Vanilla GameTestHelper.testInfo is unavailable for test cleanup", exception);
        }
    }

    private static GameTestInfo testInfo(final GameTestHelper helper) {
        try {
            final GameTestInfo info = (GameTestInfo) TEST_INFO.get(helper);
            if (info == null) {
                throw new IllegalStateException("Vanilla GameTestHelper has no live testInfo");
            }
            return info;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read vanilla GameTestHelper.testInfo for test cleanup", exception);
        }
    }
}
