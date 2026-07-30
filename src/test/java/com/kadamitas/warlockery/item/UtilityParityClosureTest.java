package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.kadamitas.warlockery.block.StatueProfile;
import com.kadamitas.warlockery.block.StatueRules;
import com.kadamitas.warlockery.block.UtilityDeviceBlockFactory;
import com.kadamitas.warlockery.entity.ArcaneCreature.CreatureKind;
import com.kadamitas.warlockery.entity.CreatureCombat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

final class UtilityParityClosureTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");
    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "warlockery");

    @TestFactory
    Stream<DynamicContainer> everyRestoredUtilityInteractionHasFailureDiagnosticAndSuccessCoverage() {
        return Stream.of(
            suite("manuals", this::manualFailure, this::manualDiagnostic, this::manualSuccess),
            suite("position_waystone", this::positionWaystoneFailure, this::waystoneDiagnostic,
                this::positionWaystoneSuccess),
            suite("blooded_waystone", this::bloodedWaystoneFailure, this::waystoneDiagnostic,
                this::bloodedWaystoneSuccess),
            suite("crystal_ball", this::crystalBallFailure, this::crystalBallDiagnostic,
                this::crystalBallSuccess),
            suite("seer_stone", this::seerStoneFailure, this::seerStoneDiagnostic, this::seerStoneSuccess),
            suite("cleansing_statues", this::cleansingStatueFailure, this::statueDiagnostic,
                this::cleansingStatueSuccess),
            suite("patron_statue", this::patronStatueFailure, this::statueDiagnostic,
                this::patronStatueSuccess),
            suite("ritual_inhibitors", this::ritualInhibitorFailure, this::ritualInhibitorDiagnostic,
                this::ritualInhibitorSuccess),
            suite("sun_collector", this::sunCollectorFailure, this::sunCollectorDiagnostic,
                this::sunCollectorSuccess),
            suite("sun_grenade", this::sunGrenadeFailure, this::sunGrenadeDiagnostic,
                this::sunGrenadeSuccess),
            suite("necromantic_focus", this::necromanticFocusFailure, this::necromanticFocusDiagnostic,
                this::necromanticFocusSuccess),
            suite("spectral_stone", this::spectralStoneFailure, this::spectralStoneDiagnostic,
                this::spectralStoneSuccess),
            suite("bone_bolt", this::boneBoltFailure, this::boltDiagnostic, this::boneBoltSuccess),
            suite("wooden_bolt", this::woodenBoltFailure, this::boltDiagnostic, this::woodenBoltSuccess),
            suite("armor_extensions", this::armorExtensionFailure, this::armorExtensionDiagnostic,
                this::armorExtensionSuccess)
        );
    }

    private void manualFailure() {
        assertTrue(ManualProfile.find("missing_manual").isEmpty());
    }

    private void manualDiagnostic() {
        assertEquals("ready", ManualProfile.find("ingredient_book_circle_magic").orElseThrow()
            .diagnose().diagnostic());
    }

    private void manualSuccess() {
        assertTrue(UtilityItemFactory.ids().containsAll(ManualProfile.ids()));
        assertEquals(ManualProfile.ids(), tagValues(DATA.resolve("tags/item/manuals.json")).stream()
            .map(value -> value.substring("warlockery:".length()))
            .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        final Set<String> completeManuals = ManualProfile.ids().stream()
            .filter(id -> !id.equals("ingredient_vbook_page"))
            .map(id -> "warlockery:" + id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertEquals(completeManuals, tagValues(DATA.resolve("tags/item/guide_books.json")));
        assertEquals(completeManuals, tagValues(Path.of(
            "src/main/resources/data/minecraft/tags/item/bookshelf_books.json"
        )));
        assertTrue(read(ASSETS.resolve("lang/en_us.json")).contains("manual.warlockery.circles.ritual_ui"));
        final String screen = read(Path.of(
            "src/main/java/com/kadamitas/warlockery/client/ManualScreen.java"
        ));
        assertTrue(screen.contains("EditBox"));
        assertTrue(screen.contains("navigate(-1, layout)"));
        assertTrue(screen.contains("navigate(1, layout)"));
    }

    private void positionWaystoneFailure() {
        assertTrue(WaystoneState.read(new CompoundTag()).isEmpty());
    }

    private void waystoneDiagnostic() {
        assertEquals("missing_destination", UtilityDecision.failure("missing_destination").diagnostic());
        assertTrue(read(ASSETS.resolve("lang/en_us.json")).contains("message.warlockery.waystone.travelled"));
    }

    private void positionWaystoneSuccess() {
        final CompoundTag data = new CompoundTag();
        final Identifier dimension = Identifier.parse("minecraft:overworld");
        final BlockPos position = new BlockPos(12, 70, -4);
        WaystoneState.write(data, dimension, position);
        assertEquals(new WaystoneState.Location(dimension, position), WaystoneState.read(data).orElseThrow());
        assertTrue(UtilityItemFactory.supports("ingredient_waystone_bound"));
    }

    private void bloodedWaystoneFailure() {
        assertFalse(UtilityDecision.failure("missing_creature").success());
    }

    private void bloodedWaystoneSuccess() {
        assertTrue(UtilityItemFactory.supports("ingredient_waystone_creature_bound"));
        assertTrue(read(DATA.resolve("ritual/bind_waystone_player.json"))
            .contains("warlockery:ingredient_waystone_creature_bound"));
    }

    private void crystalBallFailure() {
        assertFalse(DivinationRules.crystalBall(false, false).success());
    }

    private void crystalBallDiagnostic() {
        assertEquals("missing_focus", DivinationRules.crystalBall(false, false).diagnostic());
        assertEquals("prediction", DivinationRules.crystalBall(true, false).diagnostic());
        assertEquals("remote_view", DivinationRules.crystalBall(false, true).diagnostic());
    }

    private void crystalBallSuccess() {
        assertTrue(UtilityDeviceBlockFactory.supports("crystalball"));
        assertEquals(Set.of("warlockery:ingredient_happenstance_oil"),
            tagValues(DATA.resolve("tags/item/divination_catalysts.json")));
    }

    private void seerStoneFailure() {
        assertFalse(DivinationRules.seerStone(false, 0).success());
    }

    private void seerStoneDiagnostic() {
        assertEquals("mundane", DivinationRules.seerStone(true, 0).diagnostic());
        assertEquals("progression", DivinationRules.seerStone(true, 2).diagnostic());
    }

    private void seerStoneSuccess() {
        assertTrue(UtilityItemFactory.supports("ingredient_seer_stone"));
        assertTrue(read(ASSETS.resolve("lang/en_us.json")).contains("message.warlockery.divination.progression"));
    }

    private void cleansingStatueFailure() {
        assertFalse(StatueRules.diagnose(StatueProfile.Effect.CLEANSE, false, false, false).success());
    }

    private void statueDiagnostic() {
        assertEquals("no_hexes", StatueRules.diagnose(
            StatueProfile.Effect.CLEANSE, false, false, false
        ).diagnostic());
        assertTrue(read(ASSETS.resolve("lang/en_us.json")).contains("message.warlockery.statue.cleansed"));
    }

    private void cleansingStatueSuccess() {
        assertTrue(StatueRules.diagnose(StatueProfile.Effect.CLEANSE, true, false, false).success());
        assertTrue(UtilityDeviceBlockFactory.supports("broken_hexes_statue"));
        assertTrue(UtilityDeviceBlockFactory.supports("statuegoddess"));
    }

    private void patronStatueFailure() {
        assertFalse(StatueRules.diagnose(
            StatueProfile.Effect.PATRON_BLESSING, false, false, false
        ).success());
    }

    private void patronStatueSuccess() {
        assertTrue(StatueRules.diagnose(
            StatueProfile.Effect.PATRON_BLESSING, false, true, false
        ).success());
        assertTrue(read(DATA.resolve("tags/item/patron_offerings.json")).contains("#c:ingots/koboldite"));
    }

    private void ritualInhibitorFailure() {
        assertEquals("occlusion_inactive", StatueRules.diagnose(
            StatueProfile.Effect.OCCLUDE_RITUALS, false, false, false
        ).diagnostic());
    }

    private void ritualInhibitorDiagnostic() {
        assertTrue(read(ASSETS.resolve("lang/en_us.json")).contains("ritual_inhibitors"));
    }

    private void ritualInhibitorSuccess() {
        assertEquals("occlusion_active", StatueRules.diagnose(
            StatueProfile.Effect.OCCLUDE_RITUALS, false, false, true
        ).diagnostic());
        assertEquals(Set.of(
            "warlockery:occluded_summons_statue",
            "warlockery:voidbramble"
        ), tagValues(DATA.resolve("tags/block/ritual_inhibitors.json")));
    }

    private void sunCollectorFailure() {
        assertFalse(SunlightRules.collector(false, true, true).success());
        assertFalse(SunlightRules.collector(true, false, true).success());
        assertFalse(SunlightRules.collector(true, true, false).success());
    }

    private void sunCollectorDiagnostic() {
        assertEquals("missing_sunlight", SunlightRules.collector(true, false, true).diagnostic());
        assertTrue(read(ASSETS.resolve("lang/en_us.json")).contains("message.warlockery.sun_collector.charged"));
    }

    private void sunCollectorSuccess() {
        assertTrue(SunlightRules.collector(true, true, true).success());
        assertTrue(UtilityDeviceBlockFactory.supports("daylightcollector"));
        assertTrue(UtilityItemFactory.supports("sungrenade"));
    }

    private void sunGrenadeFailure() {
        assertEquals(5.0F, SunlightRules.grenadeDamage(false, 5.0F));
    }

    private void sunGrenadeDiagnostic() {
        assertTrue(read(DATA.resolve("tags/entity_type/sunlight_vulnerable.json"))
            .contains("#warlockery:vampires"));
    }

    private void sunGrenadeSuccess() {
        assertEquals(10.0F, SunlightRules.grenadeDamage(true, 5.0F));
    }

    private void necromanticFocusFailure() {
        assertFalse(NecromancyRules.command(false, false).success());
        assertFalse(NecromancyRules.command(true, true).success());
    }

    private void necromanticFocusDiagnostic() {
        assertEquals("bound_elsewhere", NecromancyRules.command(true, true).diagnostic());
    }

    private void necromanticFocusSuccess() {
        assertTrue(NecromancyRules.command(true, false).success());
        assertTrue(UtilityItemFactory.supports("ingredient_necro_stone"));
        assertTrue(read(DATA.resolve("tags/entity_type/necromantic_commandables.json"))
            .contains("#minecraft:undead"));
    }

    private void spectralStoneFailure() {
        assertFalse(NecromancyRules.spectralStone(false, 0, 3).success());
        assertFalse(NecromancyRules.spectralStone(true, 3, 3).success());
    }

    private void spectralStoneDiagnostic() {
        assertEquals("full", NecromancyRules.spectralStone(true, 3, 3).diagnostic());
    }

    private void spectralStoneSuccess() {
        final Identifier spectre = Identifier.parse("warlockery:spectre");
        final CompoundTag data = new CompoundTag();
        new SpectralStoneState(List.of(spectre)).write(data);
        assertEquals(List.of(spectre), SpectralStoneState.read(data).captured());
        assertTrue(UtilityItemFactory.supports("spectralstone"));
    }

    private void boneBoltFailure() {
        assertEquals(10.0F, CreatureCombat.adjustedDamage(CreatureKind.HOBGOBLIN, 10, false, false, true, false));
    }

    private void boltDiagnostic() {
        assertTrue(read(Path.of("src", "main", "resources", "data", "minecraft", "tags", "item", "arrows.json"))
            .contains("warlockery:ingredient_bolt_holy"));
    }

    private void boneBoltSuccess() {
        assertEquals(20.0F, CreatureCombat.adjustedDamage(CreatureKind.DEMON, 10, false, false, true, false));
        assertEquals(20.0F, CreatureCombat.adjustedDamage(CreatureKind.CORPSE, 10, false, false, true, false));
    }

    private void woodenBoltFailure() {
        assertEquals(10.0F, CreatureCombat.adjustedDamage(CreatureKind.HOBGOBLIN, 10, false, true, false, false));
    }

    private void woodenBoltSuccess() {
        assertEquals(20.0F, CreatureCombat.adjustedDamage(CreatureKind.ENT, 10, false, true, false, false));
        assertEquals(20.0F, CreatureCombat.adjustedDamage(CreatureKind.BRAMBLE_COLOSSUS, 10, false, true, false, false));
    }

    private void armorExtensionFailure() {
        assertFalse(tagValues(DATA.resolve("tags/item/sound_dampening_armor.json")).contains("warlockery:witchhat"));
    }

    private void armorExtensionDiagnostic() {
        assertTrue(read(Path.of("src", "main", "resources", "data", "minecraft", "tags", "item", "leg_armor.json"))
            .contains("warlockery:stonebrokers_quiver"));
    }

    private void armorExtensionSuccess() {
        assertEquals(Set.of("warlockery:earmuffs"),
            tagValues(DATA.resolve("tags/item/sound_dampening_armor.json")));
        assertTrue(tagValues(DATA.resolve("tags/item/brewing_garb.json")).containsAll(Set.of(
            "warlockery:hedge_crones_hat", "warlockery:witchhat", "warlockery:witchrobe"
        )));
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

    private static Set<String> tagValues(final Path path) {
        return JsonParser.parseString(read(path)).getAsJsonObject().getAsJsonArray("values").asList().stream()
            .map(value -> value.getAsString())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
