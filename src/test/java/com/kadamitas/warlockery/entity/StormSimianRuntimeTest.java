package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.StormSimianRules.Concern;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Recurring defect one is the one that has hit every family: behaviour written and unit tested but
 * never reached from the live tick, because the tests called the helpers directly. These assertions
 * are the mechanical half of the hand trace recorded in the F28 evidence file. They read the entity
 * source and require every public {@link StormSimianRuntime} entry point to appear there, qualified
 * by the owning type, so a runtime method can never quietly become orphaned while its own unit test
 * keeps passing.
 */
final class StormSimianRuntimeTest {

    private static final Path ENTITY_SOURCE = Path.of(
        "src", "main", "java", "com", "kadamitas", "warlockery", "entity",
        "StormSimianEntity.java"
    );

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Comments are stripped before any of these assertions look at the file. The javadoc on this
     * family deliberately names the exact calls that were removed, and a scan that could not tell
     * prose from code would read those names as live wiring, which is precisely the mistake the
     * audited family sweeps made.
     */
    private static String codeOf(final Path path) {
        return read(path)
            .replaceAll("(?s)/\\*.*?\\*/", " ")
            .replaceAll("(?m)//.*$", " ");
    }

    private static List<String> publicRuntimeEntryPoints() {
        final List<String> names = new ArrayList<>();
        for (final Method method : StormSimianRuntime.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers())
                && !method.isSynthetic()) {
                names.add(method.getName());
            }
        }
        return names;
    }

    /**
     * {@code receiveAlarm} is the single deliberate exception: its production caller is the raising
     * simian's own {@code raiseAlarm}, not the receiving entity, and it is traced on the runtime
     * source by {@link #theAlarmRecipientPathIsRootedInTheRaisingSimiansOwnTick}. Naming it here
     * rather than loosening the rule keeps every future entry point covered by default.
     */
    private static final String RUNTIME_ROOTED_ENTRY_POINT = "receiveAlarm";

    @Test
    void everyPublicRuntimeEntryPointIsCalledFromTheEntityByItsOwningType() {
        final String source = codeOf(ENTITY_SOURCE);
        final List<String> entryPoints = publicRuntimeEntryPoints();
        assertFalse(entryPoints.isEmpty(), "the runtime must expose at least the tick");
        for (final String name : entryPoints) {
            if (name.equals(RUNTIME_ROOTED_ENTRY_POINT)) {
                continue;
            }
            assertTrue(source.contains("StormSimianRuntime." + name + "("),
                "no production caller reaches StormSimianRuntime." + name
                    + "; a unit test calling it directly would still pass, which is exactly the"
                    + " orphaned behaviour this family must not ship");
        }
        assertTrue(entryPoints.contains("tick"));
        assertTrue(entryPoints.contains("onAcceptedDamage"));
        assertTrue(entryPoints.contains("consumeGustCharge"));
        assertTrue(entryPoints.contains("receiveAlarm"));
    }

    @Test
    void theTickIsReachedFromTheEntityServerStepAfterTheSharedPipelineRatherThanInsteadOfIt() {
        final String source = codeOf(ENTITY_SOURCE);
        final int superCall = source.indexOf("super.customServerAiStep(level);");
        final int arbiterCall = source.indexOf("StormSimianRuntime.tick(this, level);");
        assertTrue(superCall >= 0, "the shared winged pipeline must still run");
        assertTrue(arbiterCall > superCall,
            "the arbiter runs after the frozen companion, tactical and ambient writers so it owns"
                + " the last navigation write of the tick");
        assertTrue(Arrays.stream(StormSimianEntity.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("customServerAiStep")));
    }

    /**
     * {@code receiveAlarm} is the one entry point whose production caller is the runtime rather than
     * the entity, because a simian raises an alarm into its neighbours. It is still rooted in a live
     * tick, through {@code raiseAlarm}, so it is traced here on the runtime source instead.
     */
    @Test
    void theAlarmRecipientPathIsRootedInTheRaisingSimiansOwnTick() {
        final String runtime = codeOf(Path.of("src", "main", "java", "com", "kadamitas",
            "warlockery", "entity", "StormSimianRuntime.java"));
        assertTrue(runtime.contains("receiveAlarm(neighbour)"),
            "the alarm must actually deliver to the neighbours it selected");
        assertTrue(runtime.indexOf("private static void raiseAlarm")
            < runtime.indexOf("receiveAlarm(neighbour)"));
        assertTrue(runtime.contains("case ALARM -> raiseAlarm(simian, level);"),
            "raiseAlarm must be dispatched from the tick's exhaustive concern switch");
    }

    @Test
    void theEntityLeavesTheSpecializedWingedSeamToTheImpAndKeepsTheSharedPipeline() throws Exception {
        assertEquals(WingedArcaneMob.class, StormSimianEntity.class.getSuperclass());
        assertTrue(Arrays.stream(StormSimianEntity.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("ownsSpecializedWingedAi")),
            "claiming the Imp's seam would silence the frozen bond, aura and waystone writers");
    }

    @Test
    void theOldItemChasingIdleBehaviourAndItsPickupPermissionAreBothGone() {
        final String source = codeOf(ENTITY_SOURCE);
        assertFalse(source.contains("setCanPickUpLoot(true)"),
            "curiosity inspects and never takes; vanilla pickup would take on contact");
        assertTrue(source.contains("setCanPickUpLoot(false)"));
        assertFalse(source.contains("customWingedAiStep"),
            "the unbounded nearest item stream is replaced, not merely bypassed");
        assertFalse(source.contains("getNavigation().moveTo"),
            "movement belongs to the arbiter, not to the entity class");
    }

    @Test
    void persistenceAndTheDamageBoundaryAreDeclaredOnTheEntity() {
        final List<String> declared = Arrays.stream(StormSimianEntity.class.getDeclaredMethods())
            .map(Method::getName)
            .toList();
        assertTrue(declared.contains("addAdditionalSaveData"));
        assertTrue(declared.contains("readAdditionalSaveData"));
        assertTrue(declared.contains("hurtServer"));
        assertTrue(declared.contains("performRangedAttack"));
        assertTrue(declared.contains("finalizeSpawn"),
            "the random follow range spawn bonus must be stripped or exact attribute assertions"
                + " become nondeterministic");
    }

    @Test
    void loadingClearsEveryTransientExecutionFactWithoutTouchingSemantics() {
        final StormSimianRuntime.TransientState scratch = new StormSimianRuntime.TransientState();
        scratch.resetForLoad();
        assertTrue(scratch.openWindow().isEmpty());
        assertTrue(scratch.inspectedObject().isEmpty());
        assertTrue(scratch.rememberedAttacker().isEmpty());
        assertEquals(0, scratch.lastAlarmRecipients());
        assertEquals(0, scratch.awarenessTicks());
        assertEquals(Concern.IDLE, scratch.lastConcern());
    }

    @Test
    void theCountersStartAtZeroAndTheTwoForbiddenWritersAreCountedSoTheyCanBeAsserted() {
        final StormSimianRuntime.Counters counters = new StormSimianRuntime.Counters();
        assertEquals(0L, counters.weatherWrites());
        assertEquals(0L, counters.blockWrites());
        assertEquals(0L, counters.gripsTaken());
        assertEquals(0L, counters.alarmRecipients());
        assertEquals(0L, counters.chargeSpent());
    }
}
