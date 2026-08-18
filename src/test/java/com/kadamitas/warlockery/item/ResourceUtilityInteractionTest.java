package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.transformation.SupernaturalForm;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class ResourceUtilityInteractionTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data");
    private static final Path LANG = Path.of("src", "main", "resources", "assets", "warlockery", "lang", "en_us.json");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void factoryDispatchIsExplicit() {
        assertEquals(Set.of(
            "circletalisman",
            "bucketbrew",
            "bucketerosionbrew",
            "bucketspirit",
            "buckethollowtears",
            "mutator",
            "seedsdreamroot",
            "ingredient_apple_wormy",
            "ingredient_artichoke",
            "ingredient_attuned_stone",
            "ingredient_attuned_stone_charged",
            "ingredient_bat_ball",
            "ingredient_berries_rowan",
            "ingredient_bone_needle",
            "ingredient_creeper_heart",
            "ingredient_graveyard_dust",
            "ingredient_icy_needle",
            "ingredient_purified_milk",
            "ingredient_bramble_colossus_seed",
            "ingredient_rock",
            "ingredient_redstone_soup",
            "ingredient_sleeping_apple",
            "ingredient_subdued_spirit",
            "ingredient_subdued_spirit_village",
            "ingredient_wolfsbane"
        ), ResourceUtilityItemFactory.ids());
        assertThrows(IllegalArgumentException.class, () ->
            ResourceUtilityItemFactory.create(new net.minecraft.world.item.Item.Properties(), "mirror")
        );
    }

    @TestFactory
    Stream<DynamicContainer> oneSuitePerStatefulInteraction() {
        return Stream.of(
            suite("circle_talisman", this::emptyCircleFails, this::circleUiExists, this::circleStateRoundTrips),
            suite("bat_ball", this::emptyBatBallFails, this::batBallUiExists, this::batBallStateRoundTrips),
            suite("disturbed_cotton", this::dayHarvestFails, this::disturbedCottonUiExists, this::nightmareHarvestSucceeds),
            suite("drop_of_luck", this::luckRouteRequiresPower, this::luckPowerAppearsInUiContract, this::luckRouteProducesJar),
            suite("wolf_form_lamb", this::ordinaryPlayerDoesNotReceiveLamb, this::wolfMeatUsesExtensionTags, this::wolfFormReceivesLamb),
            suite("village_spirit", this::emptyVillageSpiritFails, this::villageSpiritUiExists, this::villageSpiritStateRoundTrips),
            suite("rock", this::ordinarySnowballsAreNotTaggedRocks, this::rockUsesPrivateExtensionTag, this::rockHasRenewableRecipe),
            suite("flowing_spirit", this::spiritRecipeRequiresAltarPower, this::spiritUiAndFluidTagExist, this::spiritBucketHasMachineRoute),
            suite("goblinite_dust", this::goblinDustCannotBypassItsFormTag, this::goblinMiningTagExists, this::goblinDustHasRenewableMobRoute)
        );
    }

    private void emptyCircleFails() {
        assertTrue(CircleTalismanState.read(new CompoundTag()).isEmpty());
    }

    private void circleUiExists() {
        assertLanguageKeys(
            "message.warlockery.circle_talisman.empty",
            "message.warlockery.circle_talisman.blocked",
            "message.warlockery.circle_talisman.restored"
        );
    }

    private void circleStateRoundTrips() {
        final CircleTalismanState expected = new CircleTalismanState(List.of(
            new CircleTalismanState.Glyph(0, 0, 0, "warlockery:circle"),
            new CircleTalismanState.Glyph(2, 0, -1, "warlockery:circleglyphritual")
        ));
        assertEquals(expected, CircleTalismanState.read(expected.toTag()).orElseThrow());
        final JsonObject ritual = readJson(DATA.resolve("warlockery/ritual/bind_circle.json"));
        assertEquals("bind_circle", ritual.get("action").getAsString());
        assertFalse(ritual.getAsJsonObject("requirements").getAsJsonArray("ingredients")
            .get(0).getAsJsonObject().get("consume").getAsBoolean());
    }

    private void emptyBatBallFails() {
        assertEquals(0, BatBallItem.captured(new CompoundTag()));
    }

    private void batBallUiExists() {
        assertLanguageKeys(
            "message.warlockery.bat_ball.cannot_capture",
            "message.warlockery.bat_ball.empty",
            "message.warlockery.bat_ball.released"
        );
    }

    private void batBallStateRoundTrips() {
        final CompoundTag data = new CompoundTag();
        BatBallItem.setCaptured(data, 6);
        assertEquals(6, BatBallItem.captured(data));
        assertEquals("warlockery:ingredient_bat_ball", recipeResult("ingredient_bat_ball"));
    }

    private void dayHarvestFails() {
        assertFalse(com.kadamitas.warlockery.block.DisturbedCottonHarvestRules.qualifies(false, false, false));
    }

    private void disturbedCottonUiExists() {
        assertLanguageKeys(
            "message.warlockery.disturbed_cotton.harvested",
            "message.warlockery.disturbed_cotton.dormant"
        );
    }

    private void nightmareHarvestSucceeds() {
        assertTrue(com.kadamitas.warlockery.block.DisturbedCottonHarvestRules.qualifies(true, false, false));
        assertTrue(com.kadamitas.warlockery.block.DisturbedCottonHarvestRules.qualifies(false, true, false));
        assertTrue(com.kadamitas.warlockery.block.DisturbedCottonHarvestRules.qualifies(false, false, true));
        assertTrue(read(DATA.resolve("warlockery/tags/item/disturbed_fibers.json"))
            .contains("warlockery:ingredient_disturbed_cotton"));
    }

    private void luckRouteRequiresPower() {
        assertEquals(3000, machine("cauldron_drop_of_luck").get("altar_power").getAsInt());
    }

    private void luckPowerAppearsInUiContract() {
        assertTrue(read(Path.of("src/main/java/com/kadamitas/warlockery/crafting/MachineRecipeManager.java"))
            .contains("warlockery:altar_power"));
    }

    private void luckRouteProducesJar() {
        assertMachineOutput("cauldron_drop_of_luck", "warlockery:ingredient_drop_of_luck");
        assertTrue(read(DATA.resolve("warlockery/tags/item/luck_essences.json"))
            .contains("warlockery:ingredient_drop_of_luck"));
    }

    private void ordinaryPlayerDoesNotReceiveLamb() {
        assertFalse(ResourceInteractionEvents.isWolfFormLamb(true, SupernaturalForm.NONE));
    }

    private void wolfMeatUsesExtensionTags() {
        assertTrue(read(DATA.resolve("warlockery/tags/entity_type/wolf_form_lamb_sources.json")).contains("minecraft:sheep"));
        assertTrue(read(DATA.resolve("warlockery/tags/item/wolf_form_meats.json")).contains("minecraft:mutton"));
    }

    private void wolfFormReceivesLamb() {
        assertTrue(ResourceInteractionEvents.isWolfFormLamb(true, SupernaturalForm.WEREWOLF));
        assertFalse(ResourceInteractionEvents.isWolfFormLamb(false, SupernaturalForm.WEREWOLF));
    }

    private void emptyVillageSpiritFails() {
        assertTrue(VillageSpiritItem.readVillage(new CompoundTag()).isEmpty());
    }

    private void villageSpiritUiExists() {
        assertLanguageKeys(
            "message.warlockery.village_spirit.no_village",
            "message.warlockery.village_spirit.captured",
            "message.warlockery.village_spirit.distance"
        );
    }

    private void villageSpiritStateRoundTrips() {
        final CompoundTag data = new CompoundTag();
        final BlockPos position = new BlockPos(17, 64, -9);
        VillageSpiritItem.writeVillage(data, "minecraft:overworld", position);
        assertEquals(
            new VillageSpiritItem.VillageBinding("minecraft:overworld", position),
            VillageSpiritItem.readVillage(data).orElseThrow()
        );
    }

    private void ordinarySnowballsAreNotTaggedRocks() {
        assertFalse(read(DATA.resolve("warlockery/tags/item/throwing_stones.json")).contains("minecraft:snowball"));
    }

    private void rockUsesPrivateExtensionTag() {
        assertTrue(read(DATA.resolve("warlockery/tags/item/throwing_stones.json")).contains("warlockery:ingredient_rock"));
    }

    private void rockHasRenewableRecipe() {
        final String recipe = read(DATA.resolve("warlockery/recipe/ingredient_rock.json"));
        assertTrue(recipe.contains("#c:cobblestones"));
        assertTrue(recipe.contains("warlockery:ingredient_rock"));
    }

    private void spiritRecipeRequiresAltarPower() {
        assertEquals(1200, machine("cauldron_flowing_spirit").get("altar_power").getAsInt());
    }

    private void spiritUiAndFluidTagExist() {
        assertLanguageKeys("fluid_type.warlockery.spirit");
        final String tag = read(DATA.resolve("warlockery/tags/fluid/spirit.json"));
        assertTrue(tag.contains("warlockery:spirit"));
        assertTrue(tag.contains("warlockery:flowing_spirit"));
    }

    private void spiritBucketHasMachineRoute() {
        assertMachineOutput("cauldron_flowing_spirit", "warlockery:bucketspirit");
        assertTrue(read(DATA.resolve("warlockery/warlockery_machine/distill_condensed_fear.json"))
            .contains("#warlockery:spirit"));
    }

    private void goblinDustCannotBypassItsFormTag() {
        assertFalse(read(DATA.resolve("c/tags/item/dusts/goblinite.json")).contains("warlockery:raw_delvealloy"));
    }

    private void goblinMiningTagExists() {
        assertTrue(read(DATA.resolve("warlockery/tags/block/hobgoblin_mineables.json")).contains("#c:cobblestones"));
    }

    private void goblinDustHasRenewableMobRoute() {
        final String source = read(Path.of(
            "src/main/java/com/kadamitas/warlockery/entity/HobgoblinJourneyRuntime.java"));
        assertTrue(source.contains("HOBGOBLIN_MINEABLES"));
        assertTrue(source.contains("ingredient_delvealloydust"));
        assertTrue(source.contains("commitMining("));
    }

    private static DynamicContainer suite(
        final String id,
        final Runnable failure,
        final Runnable ui,
        final Runnable success
    ) {
        return DynamicContainer.dynamicContainer(id, List.of(
            DynamicTest.dynamicTest("failure", failure::run),
            DynamicTest.dynamicTest("ui or compatibility signal", ui::run),
            DynamicTest.dynamicTest("success", success::run)
        ));
    }

    private static JsonObject machine(final String id) {
        return readJson(DATA.resolve("warlockery/warlockery_machine/" + id + ".json"));
    }

    private static void assertMachineOutput(final String id, final String expected) {
        assertTrue(machine(id).getAsJsonArray("outputs").asList().stream()
            .map(value -> value.getAsJsonObject().get("item").getAsString())
            .anyMatch(expected::equals));
    }

    private static String recipeResult(final String id) {
        return readJson(DATA.resolve("warlockery/recipe/" + id + ".json"))
            .getAsJsonObject("result")
            .get("id")
            .getAsString();
    }

    private static void assertLanguageKeys(final String... keys) {
        final JsonObject language = readJson(LANG);
        Stream.of(keys).forEach(key -> assertTrue(language.has(key), key));
    }

    private static JsonObject readJson(final Path path) {
        return JsonParser.parseString(read(path)).getAsJsonObject();
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
