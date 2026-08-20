package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Contract guards for the one Death controller. The live behavior itself is asserted by the six
 * spawned self-ticking GameTest fixtures; these tests pin the structural invariants that unit
 * tests can prove deterministically and that a live fixture would only catch by accident.
 */
final class DeathRuntimeTest {
    private static final Path ENTITY_PACKAGE = Path.of("src/main/java/com/kadamitas/warlockery/entity");
    private static final Pattern PUBLIC_RUNTIME_METHOD = Pattern.compile(
        "public static [\\w.<>\\[\\]]+ (\\w+)\\("
    );
    private static final Pattern METHOD_DECLARATION = Pattern.compile(
        "(?m)^    (?:public|protected|private)[\\w\\s.<>\\[\\],@]*?\\s(\\w+)\\("
    );
    /**
     * The engine methods that actually invoke a Death override, every tick or on a real event.
     * {@code canAttack} is deliberately absent: this entity has an empty target selector and
     * LOOK-only goals, so nothing in vanilla routes a Death through {@code Mob.canAttack}, and a
     * call site there proves textual presence rather than reachability.
     */
    private static final Set<String> ENGINE_ENTRY_POINTS = Set.of(
        "customServerAiStep", "hurtServer", "remove"
    );

    @Test
    void everyPublicRuntimeMethodIsReachedFromARealEngineEntryPointOrFromTheTickItself()
        throws IOException {
        final String runtime = source("DeathRuntime.java");
        final String entity = source("DeathEntity.java");
        final Matcher matcher = PUBLIC_RUNTIME_METHOD.matcher(runtime);
        int inspected = 0;
        while (matcher.find()) {
            final String method = matcher.group(1);
            inspected++;
            if ("tick".equals(method)) {
                continue;
            }
            final String entryPoint = enclosingMethodOf(entity, "DeathRuntime." + method + "(");
            final boolean reachedByEngine = ENGINE_ENTRY_POINTS.contains(entryPoint);
            final boolean reachedByTheTick = runtime.contains(method + "(death, ");
            assertTrue(reachedByEngine || reachedByTheTick,
                () -> "DeathRuntime." + method + " is not reachable: its only DeathEntity call site is "
                    + entryPoint + ", which the engine never invokes for this entity, and the live "
                    + "tick chain never calls it either");
        }
        assertTrue(inspected >= 4, "the runtime must expose its tick, target, damage, and removal hooks");
        assertEquals("customServerAiStep", enclosingMethodOf(entity, "DeathRuntime.tick("));
        assertEquals("hurtServer", enclosingMethodOf(entity, "DeathRuntime.onAcceptedDamage("));
        assertEquals("remove", enclosingMethodOf(entity, "DeathRuntime.onRemoved("));
    }

    @Test
    void legalTargetIsLoadBearingInsideTheLiveTickRatherThanOnlyInCanAttack() throws IOException {
        final String runtime = source("DeathRuntime.java");
        final String entity = source("DeathEntity.java");
        assertEquals("canAttack", enclosingMethodOf(entity, "DeathRuntime.legalTarget("),
            "the vanilla surface stays wired for anything that does reach it");
        assertTrue(runtime.contains("legalTarget(death, subject)"),
            "legalTarget must also be consulted from the runtime itself, because an empty target "
                + "selector means DeathEntity.canAttack alone never proves that it runs");
        final String reapBody = bodyOf(runtime, "private static void tickReap(");
        assertTrue(reapBody.contains("legalTarget(death, subject)"),
            "the reaping decision is exactly where the disguise and recovery bars must be re-read");
        assertFalse(reapBody.contains("reapAllowed(\n            true,"),
            "the reaping predicate must never be hardcoded true");
    }

    @Test
    void theSingleRecoveryTransitionCountsItsReleaseAndStartsTheBackoff() throws IOException {
        final String state = source("DeathState.java");
        final String runtime = source("DeathRuntime.java");
        assertTrue(state.contains("case RECOVER, RELEASE, QUIESCENT -> phase;"),
            "the state constructor must not convert an exhausted recovery into a settled release, "
                + "which would bypass the release counter and the reappointment backoff");
        final String recoverBody = bodyOf(runtime, "private static void tickRecover(");
        assertTrue(recoverBody.contains("releaseAppointment(death)"),
            "the recovery decision is the transition that actually releases");
        final String settleBody = bodyOf(runtime, "private static void settleRelease(");
        assertFalse(settleBody.contains("REAPPOINT_COOLDOWN_TICKS"),
            "the settling phase must not be an alternate release path");
    }

    @Test
    void unloadPausesTheAppointmentWhileTerminalRemovalClearsIt() throws IOException {
        final String entity = source("DeathEntity.java");
        final String removeBody = bodyOf(entity, "public void remove(");
        assertTrue(removeBody.contains("reason.shouldDestroy()"),
            "terminal cleanup must be guarded so an unload or dimension change pauses instead of ending");
        assertTrue(removeBody.contains("DeathRuntime.onRemoved(this)"));
        assertTrue(removeBody.contains("deathTransient.resetForLoad()"),
            "execution scratch is rebuilt on load, so it is dropped for any removal reason");
    }

    /** Returns the name of the method whose body contains the first occurrence of {@code call}. */
    private static String enclosingMethodOf(final String source, final String call) {
        final int callAt = source.indexOf(call);
        if (callAt < 0) {
            return "<no call site>";
        }
        final Matcher declarations = METHOD_DECLARATION.matcher(source.substring(0, callAt));
        String enclosing = "<top level>";
        while (declarations.find()) {
            enclosing = declarations.group(1);
        }
        return enclosing;
    }

    /** Returns the source text from one method signature up to the next declaration. */
    private static String bodyOf(final String source, final String signature) {
        final int start = source.indexOf(signature);
        if (start < 0) {
            return "";
        }
        final Matcher next = METHOD_DECLARATION.matcher(source);
        int end = source.length();
        while (next.find()) {
            if (next.start() > start) {
                end = next.start();
                break;
            }
        }
        return source.substring(start, end);
    }

    @Test
    void everyEpisodePhaseIsDispatchedByTheLiveTick() throws IOException {
        final String runtime = source("DeathRuntime.java");
        List.of(
            "case QUIESCENT ->",
            "case APPOINTED, APPROACH ->",
            "case TELEGRAPH ->",
            "case REAP ->",
            "case RECOVER ->",
            "case RELEASE ->"
        ).forEach(branch -> assertTrue(runtime.contains(branch),
            () -> "the live tick must dispatch " + branch));
    }

    @Test
    void deathNeverTouchesPlayerDeathMechanicsDropsOrRespawn() throws IOException {
        final String runtime = source("DeathRuntime.java");
        final String entity = source("DeathEntity.java");
        final String rules = source("DeathRules.java");
        final String state = source("DeathState.java");
        List.of(
            "LivingDeathEvent",
            "LivingDropsEvent",
            "PlayerRespawnEvent",
            "setRespawnPosition",
            "keepInventory",
            "dropAllDeathLoot",
            "checkTotemDeathProtection",
            "setForcedChunk",
            "getAllLevels",
            "getAllEntities",
            "setBlock("
        ).forEach(forbidden -> {
            assertFalse(runtime.contains(forbidden), () -> "DeathRuntime must not use " + forbidden);
            assertFalse(entity.contains(forbidden), () -> "DeathEntity must not use " + forbidden);
            assertFalse(rules.contains(forbidden), () -> "DeathRules must not use " + forbidden);
            assertFalse(state.contains(forbidden), () -> "DeathState must not use " + forbidden);
        });
    }

    @Test
    void deathNeverReachesIntoAnotherSpiritFamily() throws IOException {
        final String runtime = source("DeathRuntime.java");
        List.of(
            "CorpseEntity",
            "CorpseRuntime",
            "BansheeEntity",
            "BansheeRuntime",
            "SpiritMob",
            "SOUL_LANTERN",
            "AmbientActivityRuntime"
        ).forEach(foreign -> assertFalse(runtime.contains(foreign),
            () -> "DeathRuntime must not reach into " + foreign));
    }

    @Test
    void navigationAndAttackAuthorityBelongToTheRuntimeAlone() throws IOException {
        final String entity = source("DeathEntity.java");
        assertFalse(entity.contains("MeleeAttackGoal"),
            "the runtime owns the single telegraphed attempt, not a melee goal");
        assertFalse(entity.contains("NearestAttackableTargetGoal"),
            "the target selector stays empty so no goal can appoint a subject");
        assertTrue(entity.contains("setFlags(EnumSet.of(Goal.Flag.LOOK))"),
            "the idle look goal must not claim the MOVE flag");
    }

    @Test
    void theRuntimeChargesBothPerDeathCadenceAndPerLevelQuota() throws IOException {
        final String runtime = source("DeathRuntime.java");
        assertTrue(runtime.contains("claimPathRequest()"));
        assertTrue(runtime.contains("claimDiscoveryScan()"));
        assertTrue(runtime.contains("DeathRules.pathRequestAllowed("));
        assertTrue(runtime.contains("WeakHashMap"),
            "the per-level quota must not retain a strong reference to any level");
    }

    @Test
    void everyPersistedFactIsSemanticRatherThanExecutionScratch() throws IOException {
        final String state = source("DeathState.java");
        List.of("Path ", "ServerPlayer", "LivingEntity", "Entity ", "ServerLevel").forEach(live ->
            assertFalse(state.contains(live), () -> "DeathState must not persist " + live));
        assertTrue(state.contains("reaped"), "the completed attempt is the only work-forbidding fact");
    }

    private static String source(final String name) throws IOException {
        return Files.readString(ENTITY_PACKAGE.resolve(name));
    }
}
