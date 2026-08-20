package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.kadamitas.warlockery.block.AlluringSkullRules.Diagnostic;
import com.kadamitas.warlockery.crafting.AltarUpgradeResolver;
import com.kadamitas.warlockery.crafting.AltarUpgradeResolver.UpgradeClass;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

final class UtilityDeviceParityTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");
    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "warlockery");

    @TestFactory
    Stream<DynamicContainer> everyDeviceInteractionHasFailureDiagnosticAndSuccessCoverage() {
        return Stream.of(
            suite("alluring_skull", this::alluringSkullFailure, this::alluringSkullDiagnostic,
                this::alluringSkullSuccess),
            suite("beartrap", this::beartrapFailure, this::beartrapDiagnostic, this::beartrapSuccess),
            suite("candelabra_altar_upgrade", this::candelabraFailure, this::altarDiagnostic,
                this::candelabraSuccess),
            suite("chalice_altar_upgrade", this::chaliceFailure, this::chaliceDiagnostic, this::chaliceSuccess),
            suite("filled_chalice_interaction", this::filledChaliceFailure, this::chaliceDiagnostic,
                this::filledChaliceSuccess),
            suite("pentacle_altar_upgrade", this::pentacleFailure, this::altarDiagnostic, this::pentacleSuccess)
        );
    }

    private void alluringSkullFailure() {
        assertFalse(AlluringSkullRules.canLure(false, true, true));
        assertFalse(AlluringSkullRules.canLure(true, false, true));
        assertFalse(AlluringSkullRules.canLure(true, true, false));
        assertEquals(Diagnostic.WRONG_FOCUS, AlluringSkullRules.diagnostic(false, false, false));
    }

    private void alluringSkullDiagnostic() {
        assertEquals(Diagnostic.INACTIVE, AlluringSkullRules.diagnostic(false, false, true));
        assertEquals(Diagnostic.ACTIVE, AlluringSkullRules.diagnostic(true, false, true));
        assertEquals(Diagnostic.WILL_ENABLE, AlluringSkullRules.diagnostic(false, true, false));
        assertEquals(Diagnostic.WILL_DISABLE, AlluringSkullRules.diagnostic(true, true, false));
        final String language = read(ASSETS.resolve("lang/en_us.json"));
        assertTrue(language.contains("message.warlockery.alluring_skull.wrong_focus"));
        assertTrue(language.contains("message.warlockery.alluring_skull.active"));
    }

    private void alluringSkullSuccess() {
        assertTrue(AlluringSkullRules.canLure(true, true, true));
        assertTrue(UtilityDeviceBlockFactory.supports("alluringskull"));
        assertTagContains("item/alluring_skull_activators", "warlockery:ingredient_necro_stone");
        assertTagContains("entity_type/alluring_skull_targets", "minecraft:zombie");
        final String skullTargets = read(DATA.resolve("tags/entity_type/alluring_skull_targets.json"));
        assertFalse(skullTargets.contains("warlockery:corpse"),
            "the dedicated Body is no longer lured by the Alluring Skull");
        assertTrue(skullTargets.contains("#warlockery:spectral"));
        assertTrue(read(DATA.resolve("recipe/alluringskull.json")).contains("#c:bones"));
        assertTrue(read(ASSETS.resolve("blockstates/alluringskull.json")).contains("active=true"));
    }

    private void beartrapFailure() {
        assertFalse(BearTrapRules.canTrigger(BearTrapState.DISARMED, true, true, false, false));
        assertFalse(BearTrapRules.canTrigger(BearTrapState.ARMED, true, true, true, false));
        assertFalse(BearTrapRules.canTrigger(BearTrapState.ARMED, true, true, false, true));
        assertFalse(BearTrapRules.canRestrain(BearTrapState.SPRUNG, false, true, false, false));
    }

    private void beartrapDiagnostic() {
        assertEquals(BearTrapState.ARMED, BearTrapRules.nextState(BearTrapState.DISARMED));
        assertEquals(BearTrapState.DISARMED, BearTrapRules.nextState(BearTrapState.ARMED));
        assertEquals(BearTrapState.ARMED, BearTrapRules.nextState(BearTrapState.SPRUNG));
        final String language = read(ASSETS.resolve("lang/en_us.json"));
        assertTrue(language.contains("message.warlockery.beartrap.armed"));
        assertTrue(language.contains("message.warlockery.beartrap.reset"));
        assertTrue(language.contains("message.warlockery.beartrap.triggered"));
        final String states = read(ASSETS.resolve("blockstates/beartrap.json"));
        Stream.of(BearTrapState.values()).forEach(value -> assertTrue(states.contains(value.getSerializedName())));
    }

    private void beartrapSuccess() {
        assertTrue(BearTrapRules.canTrigger(BearTrapState.ARMED, true, true, false, false));
        assertTrue(BearTrapRules.canRestrain(BearTrapState.SPRUNG, true, true, false, false));
        assertTagContains("entity_type/beartrap_immune", "#warlockery:spectral");
        assertTrue(read(DATA.resolve("recipe/beartrap.json")).contains("#c:ingots/iron"));
    }

    private void candelabraFailure() {
        final var modifiers = AltarUpgradeResolver.resolve(Stream.of(
            UpgradeClass.CANDELABRA,
            UpgradeClass.CANDELABRA
        ));
        assertEquals(1, modifiers.activeClasses().size());
        assertEquals(3, modifiers.rechargeMultiplier());
    }

    private void candelabraSuccess() {
        final var modifiers = AltarUpgradeResolver.resolve(Stream.of(UpgradeClass.CANDELABRA));
        assertEquals(3, modifiers.applyRecharge(10) / 10);
        assertTagContains("block/altar_upgrades/candelabra", "warlockery:candelabra");
        assertTagContains("item/altar_upgrades/candelabra", "warlockery:ingredient_candelabra");
        assertTrue(read(DATA.resolve("recipe/candelabra.json")).contains("#c:ingots/gold"));
    }

    private void chaliceFailure() {
        assertEquals(AltarChaliceRules.Diagnostic.WRONG_FILLER,
            AltarChaliceRules.diagnostic(false, false, false));
        final var duplicate = AltarUpgradeResolver.resolve(Stream.of(UpgradeClass.CHALICE, UpgradeClass.CHALICE));
        assertEquals(2, duplicate.capacityMultiplier());
        assertEquals(1, duplicate.activeClasses().size());
    }

    private void chaliceDiagnostic() {
        assertEquals(AltarChaliceRules.Diagnostic.EMPTY, AltarChaliceRules.diagnostic(false, false, true));
        assertEquals(AltarChaliceRules.Diagnostic.CAN_FILL, AltarChaliceRules.diagnostic(false, true, false));
        assertEquals(AltarChaliceRules.Diagnostic.FILLED, AltarChaliceRules.diagnostic(true, false, true));
        final String language = read(ASSETS.resolve("lang/en_us.json"));
        assertTrue(language.contains("message.warlockery.chalice.empty"));
        assertTrue(language.contains("message.warlockery.chalice.filled"));
    }

    private void chaliceSuccess() {
        final var modifiers = AltarUpgradeResolver.resolve(Stream.of(UpgradeClass.CHALICE));
        assertEquals(2_000, modifiers.applyCapacity(1_000));
        assertTagContains("block/altar_upgrades/chalice", "warlockery:chalice");
        assertTagContains("item/chalice_fillers", "warlockery:ingredient_redstone_soup");
        assertTrue(read(DATA.resolve("recipe/chalice.json")).contains("#c:ingots/gold"));
    }

    private void filledChaliceFailure() {
        assertEquals(AltarChaliceRules.Diagnostic.FILLED,
            AltarChaliceRules.diagnostic(true, true, false));
        final var duplicate = AltarUpgradeResolver.resolve(Stream.of(UpgradeClass.CHALICE, UpgradeClass.CHALICE));
        assertEquals(2, duplicate.capacityMultiplier());
    }

    private void filledChaliceSuccess() {
        assertTagContains("item/altar_upgrades/chalice", "warlockery:ingredient_chalice_full");
        assertTrue(read(DATA.resolve("recipe/ingredient_chalice_full.json"))
            .contains("#warlockery:chalice_fillers"));
        assertTrue(read(ASSETS.resolve("blockstates/chalice.json")).contains("filled=true"));
        assertTrue(read(DATA.resolve("loot_table/blocks/chalice.json"))
            .contains("warlockery:ingredient_chalice_full"));
    }

    private void pentacleFailure() {
        final var duplicate = AltarUpgradeResolver.resolve(Stream.of(UpgradeClass.PENTACLE, UpgradeClass.PENTACLE));
        assertEquals(2, duplicate.rechargeMultiplier());
        assertEquals(1, duplicate.activeClasses().size());
    }

    private void pentacleSuccess() {
        final var stacked = AltarUpgradeResolver.resolve(Stream.of(
            UpgradeClass.CANDELABRA,
            UpgradeClass.PENTACLE
        ));
        assertEquals(6, stacked.rechargeMultiplier());
        assertEquals(60, stacked.applyRecharge(10));
        assertTagContains("block/altar_upgrades/pentacle", "warlockery:pentacle");
        assertTagContains("item/altar_upgrades/pentacle", "warlockery:ingredient_pentacle");
        assertTrue(read(DATA.resolve("recipe/ingredient_pentacle.json")).contains("#c:ingots/goblinite"));
        assertTrue(read(DATA.resolve("loot_table/blocks/pentacle.json"))
            .contains("warlockery:ingredient_pentacle"));
    }

    private void altarDiagnostic() {
        final String language = read(ASSETS.resolve("lang/en_us.json"));
        assertTrue(language.contains("overlay.warlockery.altar.upgrades"));
        assertTrue(language.contains("capacity x%s"));
        assertTrue(language.contains("recharge x%s"));
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
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
