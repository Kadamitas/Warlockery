package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.kadamitas.warlockery.block.PlantMineRules.Diagnostic;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

final class PlantMineBehaviorTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");
    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "warlockery");

    @TestFactory
    Stream<DynamicContainer> everyPayloadAndBasePageFamilyHasFailureDiagnosticAndSuccessCoverage() {
        return Stream.of(
            suite("ink_dandelion_poppy_shrub_bramble", this::inkFailure, () -> diagnostics(PlantMinePayload.INK),
                this::inkSuccess),
            suite("sprouting_dandelion_poppy_shrub_bramble", this::sproutingFailure,
                () -> diagnostics(PlantMinePayload.SPROUTING), this::sproutingSuccess),
            suite("thorns_dandelion_poppy_shrub_bramble", this::thornsFailure,
                () -> diagnostics(PlantMinePayload.THORNS), this::thornsSuccess),
            suite("webs_dandelion_poppy_shrub_bramble", this::websFailure,
                () -> diagnostics(PlantMinePayload.WEBS), this::websSuccess)
        );
    }

    private void inkFailure() {
        assertFalse(PlantMineRules.canTrigger(PlantMinePayload.UNARMED, true, true, false, false));
        assertFalse(PlantMineRules.canTrigger(PlantMinePayload.INK, true, true, true, false));
        assertFalse(PlantMineRules.canAffect(true, true, false, true));
    }

    private void inkSuccess() {
        assertTrue(PlantMineRules.canTrigger(PlantMinePayload.INK, true, true, false, false));
        assertEquals(4, PlantMinePayload.INK.radius());
        assertEquals(240, PlantMinePayload.INK.duration());
        assertTagContains("item/plant_mine_payloads/ink", "warlockery:brew_blindness");
        assertTagContains("entity_type/plant_mine_immune", "#warlockery:spectral");
        assertTagContains("item/plant_mine_bases", "minecraft:dandelion");
        assertTrue(read(DATA.resolve("recipe/plantmine.json")).contains("warlockery:plantmine"));
    }

    private void sproutingFailure() {
        assertFalse(PlantMineRules.canGrowVegetation(false, true, true, true));
        assertFalse(PlantMineRules.canGrowVegetation(true, false, true, true));
        assertFalse(PlantMineRules.canPlaceTerrain(true, false, true, true));
        assertFalse(PlantMineRules.canPlaceTerrain(true, true, false, true));
    }

    private void sproutingSuccess() {
        assertTrue(PlantMineRules.canGrowVegetation(true, true, true, true));
        assertTrue(PlantMineRules.canPlaceTerrain(true, true, true, true));
        assertTagContains("item/plant_mine_payloads/sprouting", "warlockery:brew_grow_sapling");
        assertTagContains("block/plant_mine_growables", "#minecraft:crops");
        assertTagContains("block/plant_mine_growth_ground", "minecraft:moss_block");
    }

    private void thornsFailure() {
        assertFalse(PlantMineRules.canPlaceThorn(false, true, true, true, true));
        assertFalse(PlantMineRules.canPlaceThorn(true, false, true, true, true));
        assertFalse(PlantMineRules.canPlaceThorn(true, true, false, true, true));
        assertFalse(PlantMineRules.canPlaceThorn(true, true, true, true, false));
    }

    private void thornsSuccess() {
        assertTrue(PlantMineRules.canPlaceThorn(true, true, true, true, true));
        assertTagContains("item/plant_mine_payloads/thorns", "warlockery:ingredient_brew_thorns");
        assertTagContains("block/plant_mine_cactus_ground", "#minecraft:sand");
        assertTagContains("block/plant_mine_thorn_ground", "#minecraft:dirt");
    }

    private void websFailure() {
        assertFalse(PlantMineRules.canPlaceWeb(false, true, true));
        assertFalse(PlantMineRules.canPlaceWeb(true, false, true));
        assertFalse(PlantMineRules.canPlaceWeb(true, true, false));
    }

    private void websSuccess() {
        assertTrue(PlantMineRules.canPlaceWeb(true, true, true));
        assertEquals(100, PlantMinePayload.WEBS.duration());
        assertTagContains("item/plant_mine_payloads/webs", "warlockery:brew_webs");
        assertTagContains("block/plant_mine_web_supports", "#minecraft:planks");
    }

    private void diagnostics(final PlantMinePayload payload) {
        assertEquals(Diagnostic.UNARMED,
            PlantMineRules.diagnostic(PlantMinePayload.UNARMED, Optional.empty(), true));
        assertEquals(Diagnostic.WRONG,
            PlantMineRules.diagnostic(PlantMinePayload.UNARMED, Optional.empty(), false));
        assertEquals(Diagnostic.READY,
            PlantMineRules.diagnostic(PlantMinePayload.UNARMED, Optional.of(payload), false));
        assertEquals(Diagnostic.READY,
            PlantMineRules.diagnostic(payload, Optional.empty(), true));
        final PlantMinePayload wrong = payload == PlantMinePayload.INK ? PlantMinePayload.WEBS : PlantMinePayload.INK;
        assertEquals(Diagnostic.WRONG, PlantMineRules.diagnostic(payload, Optional.of(wrong), false));
        final String language = read(ASSETS.resolve("lang/en_us.json"));
        assertTrue(language.contains("message.warlockery.plant_mine.unarmed"));
        assertTrue(language.contains("message.warlockery.plant_mine.wrong"));
        assertTrue(language.contains("message.warlockery.plant_mine.ready"));
        final String states = read(ASSETS.resolve("blockstates/plantmine.json"));
        assertTrue(states.contains("payload=" + payload.getSerializedName()));
    }

    private static void assertTagContains(final String relative, final String expected) {
        final String json = read(DATA.resolve("tags/" + relative + ".json"));
        JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("values");
        assertTrue(json.contains(expected), relative + " must contain " + expected);
    }

    private static DynamicContainer suite(
        final String name,
        final Runnable failure,
        final Runnable diagnostic,
        final Runnable success
    ) {
        return DynamicContainer.dynamicContainer(name, Stream.of(
            DynamicTest.dynamicTest("failure", failure::run),
            DynamicTest.dynamicTest("diagnostic", diagnostic::run),
            DynamicTest.dynamicTest("success", success::run)
        ));
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
