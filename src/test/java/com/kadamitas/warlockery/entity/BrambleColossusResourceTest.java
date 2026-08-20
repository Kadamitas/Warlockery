package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

final class BrambleColossusResourceTest {
    @Test void immutableAcquisitionAndLootResourcesRemainPresent() {
        var loot=readJson("src/main/resources/data/warlockery/loot_table/entities/bramble_colossus.json");
        assertEquals("minecraft:entity",loot.get("type").getAsString());
        assertEquals("minecraft:poppy",loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("warlockery:entities/bramble_colossus",loot.get("random_sequence").getAsString());
        var recipe=readJson("src/main/resources/data/warlockery/recipe/ingredient_bramble_colossus_seed.json");
        assertEquals("minecraft:crafting_shaped",recipe.get("type").getAsString());
        assertEquals("VRVMAEVTV",recipe.getAsJsonArray("pattern").get(0).getAsString()+recipe.getAsJsonArray("pattern").get(1).getAsString()+recipe.getAsJsonArray("pattern").get(2).getAsString());
        assertEquals("warlockery:ingredient_bramble_colossus_seed",recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(36, BrambleColossusEntity.BASE_MAX_HEALTH);
        assertEquals(7, BrambleColossusEntity.BASE_ATTACK_DAMAGE);
        assertEquals(.3, BrambleColossusEntity.BASE_MOVEMENT_SPEED);
    }
    @Test void allSixIsolatedDescriptorsExist() {
        for(String name:new String[]{"post_sweep_displays_then_threshes","allowlist_and_maker_are_never_struck","circuit_and_stance_stay_inside_the_post","nerve_falters_and_recovers_deterministically","hazard_escape_and_cancellation_are_deterministic","save_reload_and_zombie_lifecycle_are_replaced"}) {
            var descriptor=readJson("src/main/resources/data/warlockery/test_instance/bramble_colossus_"+name+".json");
            assertEquals("minecraft:function",descriptor.get("type").getAsString());
            assertEquals("warlockery:bramble_colossus_"+name,descriptor.get("function").getAsString());
            assertEquals("warlockery:bramble_colossus_isolated",descriptor.get("environment").getAsString());
            assertEquals("forge:empty15x15x15",descriptor.get("structure").getAsString());
            assertTrue(descriptor.get("max_ticks").getAsInt()>=80);
        }
    }
    @Test void routeFailureBackoffRebasesOnTheExactThirdFailure() throws java.io.IOException {
        String runtime=Files.readString(Path.of("src/main/java/com/kadamitas/warlockery/entity/BrambleColossusRuntime.java"));
        assertTrue(runtime.contains("recordPost(mob.blockPosition())"));
        assertTrue(runtime.contains("routeBackoff=BrambleColossusRules.routeBackoffSentinel()"));
        String falter=runtime.substring(runtime.indexOf("private static void falter"),runtime.indexOf("public static void onAcceptedDamage"));
        assertFalse(falter.contains("cancelMovement(mob)"));
        assertTrue(falter.contains("int backoff=s.routeBackoff"));
        assertTrue(falter.contains("s.routeBackoff=backoff"));
        String sweep=runtime.substring(runtime.indexOf("private static void sweep"),runtime.indexOf("private static void alarm"));
        String mark=runtime.substring(runtime.indexOf("private static void tickMark"),runtime.indexOf("private static void tickDisplay"));
        assertTrue(sweep.contains("insideHeld(mob, candidate)"));
        assertTrue(mark.contains("insideHeld(mob,target)"));
    }
    @Test void reloadUsesTheFullTransientResetBoundary() throws java.io.IOException {
        String entity=Files.readString(Path.of("src/main/java/com/kadamitas/warlockery/entity/BrambleColossusEntity.java"));
        assertTrue(entity.contains("transientState.resetAfterLoad()"));
    }
    @Test void fixtureHasAClosedSixBlockPerimeterAndDimensionTransitionHook() throws java.io.IOException {
        String fixture=Files.readString(Path.of("src/main/java/com/kadamitas/warlockery/entity/BrambleColossusGameTests.java"));
        String entity=Files.readString(Path.of("src/main/java/com/kadamitas/warlockery/entity/BrambleColossusEntity.java"));
        assertTrue(fixture.contains("int radius=6"));
        assertTrue(fixture.contains("Blocks.BARRIER.defaultBlockState()"));
        assertTrue(fixture.contains("Math.abs(x)==wall||Math.abs(z)==wall||y==height"));
        assertFalse(fixture.contains("getEntitiesOfClass"));
        assertFalse(fixture.contains("setNoAi"));
        assertTrue(entity.contains("teleport(TeleportTransition transition)"));
        assertTrue(entity.contains("resetAfterDimensionChange"));
    }
    private static com.google.gson.JsonObject readJson(String path) {
        try { return JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject(); }
        catch (java.io.IOException failure) { throw new AssertionError(path,failure); }
    }
}
