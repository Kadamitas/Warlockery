package com.kadamitas.warlockery.ritual.hex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.item.DollCorruptionRules;
import com.kadamitas.warlockery.item.DollItem;
import com.kadamitas.warlockery.ritual.RitualAction;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

final class UpgradedHexParityTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @TestFactory
    Stream<DynamicContainer> oneFailureUiAndSuccessContainerPerUpgradedHex() {
        return cases().stream().map(testCase -> DynamicContainer.dynamicContainer(testCase.id(), List.of(
            DynamicTest.dynamicTest("failure is bounded", testCase.failure()),
            DynamicTest.dynamicTest("side overlay receives a signal", testCase.ui()),
            DynamicTest.dynamicTest("success matches the parity contract", testCase.success())
        )));
    }

    private static List<HexCase> cases() {
        return List.of(
            new HexCase("blight", UpgradedHexParityTest::blightFailure, UpgradedHexParityTest::blightUi,
                UpgradedHexParityTest::blightSuccess),
            new HexCase("corrupt_doll", UpgradedHexParityTest::corruptDollFailure,
                UpgradedHexParityTest::corruptDollUi, UpgradedHexParityTest::corruptDollSuccess),
            new HexCase("insanity", UpgradedHexParityTest::insanityFailure, () -> persistentUi(HexKind.INSANITY),
                UpgradedHexParityTest::insanitySuccess),
            new HexCase("misfortune", UpgradedHexParityTest::misfortuneFailure,
                () -> persistentUi(HexKind.MISFORTUNE), UpgradedHexParityTest::misfortuneSuccess),
            new HexCase("overheating", UpgradedHexParityTest::overheatingFailure,
                () -> persistentUi(HexKind.OVERHEATING), UpgradedHexParityTest::overheatingSuccess),
            new HexCase("raining_toads", UpgradedHexParityTest::toadRainFailure,
                UpgradedHexParityTest::toadRainUi, UpgradedHexParityTest::toadRainSuccess),
            new HexCase("sinking", UpgradedHexParityTest::sinkingFailure, () -> persistentUi(HexKind.SINKING),
                UpgradedHexParityTest::sinkingSuccess),
            new HexCase("waking_nightmare", UpgradedHexParityTest::nightmareFailure,
                () -> persistentUi(HexKind.WAKING_NIGHTMARE), UpgradedHexParityTest::nightmareSuccess)
        );
    }

    private static void blightFailure() {
        assertFalse(new BlightHex.BlightReport(0, 0, 0).changedAnything());
        assertThrows(IllegalArgumentException.class, () -> new BlightHex.BlightReport(-1, 0, 0));
    }

    private static void blightUi() {
        assertTrue(BlightHex.UI_EFFECTS.stream()
            .allMatch(effect -> effect.value().getCategory() == MobEffectCategory.HARMFUL));
    }

    private static void blightSuccess() {
        assertTrue(new BlightHex.BlightReport(2, 3, 4).changedAnything());
        assertEquals(RitualAction.BLIGHT.id(), ritual("blight").get("action").getAsString());
        assertTagContains("block", "blight_vegetation", "#warlockery:ritual_crops");
        assertTagContains("block", "blight_soils", "minecraft:farmland");
        assertTagContains("entity_type", "blight_victims", "minecraft:villager");
    }

    private static void corruptDollFailure() {
        final DollCorruptionRules.CorruptionPlan plan = DollCorruptionRules.plan(false, 0, 3);
        assertFalse(plan.foundTarget());
        assertEquals(0, plan.dollsToDamage());
        assertFalse(DollCorruptionRules.plan(true, 0, 3).intercepted());
    }

    private static void corruptDollUi() {
        assertEquals(
            DollItem.CorruptionUiSignal.DOLL_GUARD_ACTIVATION,
            new DollItem.CorruptionResult(DollItem.CorruptionOutcome.INTERCEPTED, 0).uiSignal()
        );
        assertEquals(
            DollItem.CorruptionUiSignal.INVENTORY_CHANGE,
            new DollItem.CorruptionResult(DollItem.CorruptionOutcome.DAMAGED, 2).uiSignal()
        );
    }

    private static void corruptDollSuccess() {
        final DollCorruptionRules.CorruptionPlan intercepted = DollCorruptionRules.plan(true, 5, 3);
        final DollCorruptionRules.CorruptionPlan unguarded = DollCorruptionRules.plan(false, 5, 3);
        assertTrue(intercepted.intercepted());
        assertEquals(0, intercepted.dollsToDamage());
        assertEquals(3, unguarded.dollsToDamage());
        assertEquals(32, DollCorruptionRules.destructionWear(true, 32));
        assertEquals(1, DollCorruptionRules.destructionWear(false, 0));
        assertEquals("corrupt_doll", ritual("corrupt_doll").get("target").getAsString());
    }

    private static void insanityFailure() {
        assertFalse(HallucinationRules.shouldSpawn(HallucinationRules.INSANITY, 199, 0));
        assertFalse(HallucinationRules.shouldSpawn(
            HallucinationRules.INSANITY,
            200,
            HallucinationRules.INSANITY.maximumThreats()
        ));
    }

    private static void insanitySuccess() {
        assertTrue(HallucinationRules.shouldSpawn(HallucinationRules.INSANITY, 200, 0));
        assertTagContains("entity_type", "insanity_threats", "minecraft:vex");
    }

    private static void misfortuneFailure() {
        assertThrows(
            IllegalArgumentException.class,
            () -> MisfortuneRules.outcomeIndex(UUID.randomUUID(), 0L, 0)
        );
    }

    private static void misfortuneSuccess() {
        final UUID target = UUID.fromString("8ba846f1-8598-46ed-9218-77b7dd181f7a");
        final int first = MisfortuneRules.outcomeIndex(target, 400L, 5);
        assertEquals(first, MisfortuneRules.outcomeIndex(target, 400L, 5));
        final long varied = IntStream.range(0, 24)
            .map(index -> MisfortuneRules.outcomeIndex(target, (long) index * MisfortuneRules.INTERVAL_TICKS, 5))
            .distinct()
            .count();
        assertTrue(varied >= 4);
    }

    private static void overheatingFailure() {
        assertFalse(OverheatingRules.shouldBurn(false, 0.8F, false, false));
        assertFalse(OverheatingRules.shouldBurn(true, 2.0F, true, false));
        assertFalse(OverheatingRules.shouldBurn(true, 2.0F, false, true));
    }

    private static void overheatingSuccess() {
        assertTrue(OverheatingRules.shouldBurn(true, 0.0F, false, false));
        assertTrue(OverheatingRules.shouldBurn(false, 1.0F, false, false));
        assertTagContains("worldgen/biome", "overheating", "#minecraft:is_nether");
    }

    private static void toadRainFailure() {
        assertFalse(new ToadRainHex.ToadRainReport(0, 0, 0).complete(8));
        assertThrows(IllegalArgumentException.class, () -> ToadRainRules.roleFor(-1));
    }

    private static void toadRainUi() {
        assertEquals(MobEffectCategory.HARMFUL, ToadRainRules.POISON_UI_EFFECT.value().getCategory());
    }

    private static void toadRainSuccess() {
        assertEquals(ToadRainRules.ToadRole.POISONOUS, ToadRainRules.roleFor(0));
        assertEquals(ToadRainRules.ToadRole.EXPLOSIVE, ToadRainRules.roleFor(1));
        assertTrue(new ToadRainHex.ToadRainReport(8, 4, 4).complete(8));
        assertEquals(Level.ExplosionInteraction.NONE, ToadRainRules.EXPLOSION_INTERACTION);
        assertEquals(RitualAction.TOAD_RAIN.id(), ritual("rain_of_toads").get("action").getAsString());
        assertTagContains("entity_type", "hex_toads", "minecraft:frog");
    }

    private static void sinkingFailure() {
        assertFalse(SinkingRules.shouldSink(0.0));
        assertFalse(SinkingRules.shouldSink(-0.01));
    }

    private static void sinkingSuccess() {
        assertTrue(SinkingRules.shouldSink(0.01));
        final Vec3 burdened = SinkingRules.burden(new Vec3(1.0, 0.2, -1.0));
        assertEquals(0.65, burdened.x);
        assertEquals(-0.08, burdened.y);
        assertEquals(-0.65, burdened.z);
        assertTagContains("fluid", "sinking_fluids", "#minecraft:water");
    }

    private static void nightmareFailure() {
        assertFalse(HallucinationRules.shouldSpawn(
            HallucinationRules.WAKING_NIGHTMARE,
            100,
            0,
            false
        ));
    }

    private static void nightmareSuccess() {
        assertTrue(HallucinationRules.shouldSpawn(
            HallucinationRules.WAKING_NIGHTMARE,
            100,
            0,
            true
        ));
        assertTrue(HallucinationRules.WAKING_NIGHTMARE.maximumThreats()
            > HallucinationRules.INSANITY.maximumThreats());
        assertTagContains("entity_type", "waking_nightmare_threats", "#warlockery:nightmares");
    }

    private static void persistentUi(final HexKind kind) {
        assertFalse(kind.markerEffects().isEmpty());
        assertTrue(kind.markerEffects().stream()
            .allMatch(effect -> effect.effect().value().getCategory() == MobEffectCategory.HARMFUL));
        assertNotEquals(0, kind.markerEffects().size());
    }

    private static JsonObject ritual(final String id) {
        return json(DATA.resolve("ritual").resolve(id + ".json"));
    }

    private static void assertTagContains(final String registry, final String id, final String expected) {
        final JsonArray values = json(DATA.resolve("tags").resolve(registry).resolve(id + ".json"))
            .getAsJsonArray("values");
        final Set<String> entries = values.asList().stream()
            .map(element -> element.getAsString())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertTrue(entries.contains(expected), registry + "/" + id);
    }

    private static JsonObject json(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private record HexCase(String id, Executable failure, Executable ui, Executable success) {
    }
}
