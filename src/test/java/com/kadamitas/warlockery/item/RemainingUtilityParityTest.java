package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.block.DollShelfRules;
import com.kadamitas.warlockery.block.MagicalPlantBlockFactory;
import com.kadamitas.warlockery.block.UtilityDeviceBlockFactory;
import com.kadamitas.warlockery.block.UtilityDeviceProfile;
import com.kadamitas.warlockery.block.UtilityDeviceRules;
import com.kadamitas.warlockery.crafting.MachineUpgradeRules;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

final class RemainingUtilityParityTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data");

    @TestFactory
    Stream<DynamicContainer> everyRemainingUtilityHasFailureDiagnosticAndSuccessCoverage() {
        return Stream.of(
            suite("baba_yaga", () -> assertFalse(DivinationRules.babaYagaEncounter(false, true, false).success()),
                () -> assertEquals("night_required", DivinationRules.babaYagaEncounter(true, false, false).diagnostic()),
                () -> assertTrue(DivinationRules.babaYagaEncounter(true, true, false).success())),
            garb("baba_yaga_hat"),
            suite("biting_belt", () -> assertEquals(BitingBeltState.EMPTY, BitingBeltState.read(new CompoundTag())),
                () -> assertEquals("missing_potion", UtilityDecision.failure("missing_potion").diagnostic()),
                this::storedBeltEffectsRoundTrip),
            device("blood_crucible", UtilityDeviceRules.bloodCrucible(false, false),
                UtilityDeviceRules.bloodCrucible(true, false), UtilityDeviceRules.bloodCrucible(true, true), "bloodcrucible"),
            resource("blood_stained_wool", "warlockery:bloodedwool", "blooded_wool_smelting.json",
                "warlockery:ingredient_woven_cruor"),
            device("coffin", UtilityDeviceRules.coffin(false, true), UtilityDeviceRules.coffin(true, false),
                UtilityDeviceRules.coffin(true, true), "coffinblock"),
            suite("creative_bat_wolf_token", () -> assertFalse(ProgressionTokenRules.diagnose(false, 1).success()),
                () -> assertEquals("level_changed", ProgressionTokenRules.diagnose(true, 1).diagnostic()),
                () -> assertEquals(0, ProgressionTokenRules.next(ProgressionTokenRules.MAX_LEVEL))),
            suite("disease", () -> assertFalse(UtilityDeviceRules.harmsDisease(false, false)),
                () -> assertTrue(read(DATA.resolve("warlockery/tags/entity_type/disease_immune.json")).contains("minecraft:undead")),
                () -> assertTrue(UtilityDeviceRules.harmsDisease(true, false))),
            pit("pit_dirt", "pitdirt"),
            suite("duplication_grenade", () -> assertFalse(ReplicationChargeRules.diagnose(false, true).success()),
                () -> assertEquals("blocked", ReplicationChargeRules.diagnose(true, false).diagnostic()),
                () -> assertTrue(UtilityItemFactory.supports("replication_charge"))),
            suite("fume_filter", () -> assertEquals(MachineUpgradeRules.Upgrade.NONE,
                    MachineUpgradeRules.combine(IntStream.empty())),
                () -> assertEquals(new MachineUpgradeRules.Upgrade(3, 2),
                    MachineUpgradeRules.combine(IntStream.of(2))),
                () -> assertTrue(read(DATA.resolve("warlockery/recipe/fume_funnel.json")).contains("minecraft:hopper")
                    && read(DATA.resolve("warlockery/recipe/filtered_fume_funnel.json"))
                        .contains("warlockery:ingredient_fume_filter"))),
            suite("garlic_garland", () -> assertFalse(UtilityDeviceRules.garlicWard(true).success()),
                () -> assertEquals("vampire_burned", UtilityDeviceRules.garlicWard(true).diagnostic()),
                () -> assertTrue(UtilityDeviceRules.garlicWard(false).success())),
            goblet("glass_goblet"),
            goblet("glass_goblet_full"),
            pit("pit_grass", "pitgrass"),
            device("leech_chest", UtilityDeviceRules.leechChest(false, false),
                UtilityDeviceRules.leechChest(true, false), UtilityDeviceRules.leechChest(true, true), "leechchest"),
            suite("archfiends_urn", () -> assertFalse(UtilityItemFactory.supports("missing_urn")),
                () -> assertEquals("missing_spawn_space", UtilityDecision.failure("missing_spawn_space").diagnostic()),
                () -> assertTrue(UtilityItemFactory.supports("archfiends_urn"))),
            mirror("infernal_mirror", "mirrorblock"),
            mirror("mirror_surface", "mirrorwall"),
            suite("necromancer_robes", () -> assertFalse(BrewingGarbRules.diagnose(0, true).success()),
                () -> assertEquals("ineligible_output", BrewingGarbRules.diagnose(1, false).diagnostic()),
                () -> assertTrue(BrewingGarbRules.duplicates(1, 0))),
            suite("doll_shelf", () -> assertFalse(DollShelfRules.accepts(false, 0)),
                () -> assertEquals("empty", DollShelfRules.diagnose(0, true).diagnostic()),
                () -> assertTrue(DollShelfRules.accepts(true, 0) && UtilityDeviceBlockFactory.supports("doll_shelf"))),
            key("rowan_door_key", 1),
            key("rowan_keyring", 16),
            suite("shaded_glass", () -> assertFalse(UtilityDeviceRules.shadedGlassActive(false)),
                () -> assertTrue(read(DATA.resolve("c/tags/block/glass_blocks.json")).contains("shadedglass_active")),
                () -> assertTrue(UtilityDeviceRules.shadedGlassActive(true))),
            device("spirit_portal", UtilityDeviceRules.spiritPortal(false),
                UtilityDecision.failure("missing_destination"), UtilityDeviceRules.spiritPortal(true), "spiritportal"),
            suite("staff_of_duplication", this::replicationMissingSelection,
                this::replicationOversizeDiagnostic, this::replicationReady),
            device("trent_effigy", UtilityDeviceRules.trentEffigy(false),
                UtilityDecision.failure("missing_sapling"), UtilityDeviceRules.trentEffigy(true), "trent"),
            suite("wild_bramble", () -> assertFalse(MagicalPlantBlockFactory.behaviorOf("missing_bramble").isPresent()),
                () -> assertTrue(MagicalPlantBlockFactory.behaviorOf("bramble").orElseThrow().randomlyTicks()),
                () -> assertTrue(MagicalPlantBlockFactory.behaviorOf("bramble").orElseThrow().spreads())),
            garb("witches_hat"),
            garb("witches_robes"),
            suite("wolf_altar", () -> assertFalse(UtilityDeviceRules.wolfAltar(false, true, true).success()),
                () -> assertEquals("moon_required", UtilityDeviceRules.wolfAltar(true, true, false).diagnostic()),
                () -> assertTrue(UtilityDeviceRules.wolfAltar(true, true, true).success())),
            suite("wolf_head", () -> assertFalse(read(DATA.resolve("minecraft/tags/item/head_armor.json")).contains("missing_head")),
                () -> assertTrue(read(DATA.resolve("warlockery/tags/item/wolf_altar_heads.json")).contains("wolfhead")),
                () -> assertTrue(read(DATA.resolve("minecraft/tags/item/head_armor.json")).contains("warlockery:wolfhead"))),
            suite("happenstance_oil", () -> assertFalse(DivinationRules.crystalBall(false, false).success()),
                () -> assertEquals("prediction", DivinationRules.crystalBall(true, false).diagnostic()),
                () -> assertTrue(read(DATA.resolve("warlockery/tags/item/divination_catalysts.json"))
                    .contains("ingredient_happenstance_oil")))
        );
    }

    private DynamicContainer garb(final String name) {
        return suite(name, () -> assertFalse(BrewingGarbRules.diagnose(0, true).success()),
            () -> assertEquals("yield_chance", BrewingGarbRules.diagnose(1, true).diagnostic()),
            () -> assertTrue(BrewingGarbRules.duplicates(4, 0)));
    }

    private DynamicContainer device(
        final String name,
        final UtilityDecision failure,
        final UtilityDecision diagnostic,
        final UtilityDecision success,
        final String blockId
    ) {
        return suite(name, () -> assertFalse(failure.success()),
            () -> assertFalse(diagnostic.diagnostic().isBlank()),
            () -> assertTrue(success.success() && UtilityDeviceProfile.find(blockId).isPresent()));
    }

    private DynamicContainer resource(
        final String name,
        final String input,
        final String recipe,
        final String output
    ) {
        return suite(name, () -> assertFalse(read(DATA.resolve("warlockery/recipe/" + recipe)).contains("missing_input")),
            () -> assertTrue(read(DATA.resolve("warlockery/recipe/" + recipe)).contains(input)),
            () -> assertTrue(read(DATA.resolve("warlockery/recipe/" + recipe)).contains(output)));
    }

    private DynamicContainer pit(final String name, final String blockId) {
        return suite(name, () -> assertFalse(UtilityDeviceRules.trapsInPit(false, false)),
            () -> assertFalse(UtilityDeviceRules.trapsInPit(true, true)),
            () -> assertTrue(UtilityDeviceRules.trapsInPit(true, false)
                && UtilityDeviceProfile.find(blockId).isPresent()));
    }

    private DynamicContainer goblet(final String name) {
        return suite(name, () -> {
            final CompoundTag data = new CompoundTag();
            assertFalse(BloodGobletState.isFull(data));
        }, () -> assertEquals("empty", UtilityDecision.failure("empty").diagnostic()), () -> {
            final CompoundTag data = new CompoundTag();
            BloodGobletState.setFull(data, true);
            assertTrue(BloodGobletState.isFull(data));
        });
    }

    private DynamicContainer mirror(final String name, final String blockId) {
        return suite(name, () -> assertFalse(UtilityDeviceRules.mirror(false).success()),
            () -> assertEquals("missing_mirror", UtilityDeviceRules.mirror(false).diagnostic()),
            () -> assertTrue(UtilityDeviceRules.mirror(true).success()
                && UtilityDeviceProfile.find(blockId).isPresent()));
    }

    private DynamicContainer key(final String name, final int capacity) {
        return suite(name, () -> assertTrue(new RowanKeyState(List.of()).doors().isEmpty()),
            () -> assertEquals("keyring_full", UtilityDecision.failure("keyring_full").diagnostic()), () -> {
                final RowanKeyState.Door door = new RowanKeyState.Door(
                    Identifier.parse("minecraft:overworld"), new BlockPos(capacity, 64, 0)
                );
                final RowanKeyState bound = new RowanKeyState(List.of()).bind(door, capacity);
                assertTrue(new RowanKeyState(List.of()).merge(bound, capacity).opens(door));
            });
    }

    private void storedBeltEffectsRoundTrip() {
        final var speed = new BitingBeltState.StoredEffect(Identifier.parse("minecraft:speed"), 200, 1);
        final var poison = new BitingBeltState.StoredEffect(Identifier.parse("minecraft:poison"), 100, 0);
        final CompoundTag data = new CompoundTag();
        new BitingBeltState(Optional.of(speed), Optional.of(poison)).write(data);
        assertEquals(new BitingBeltState(Optional.of(speed), Optional.of(poison)), BitingBeltState.read(data));
    }

    private void replicationMissingSelection() {
        assertFalse(new ReplicationSelection(
            Identifier.parse("minecraft:overworld"), BlockPos.ZERO, Optional.empty()
        ).diagnose(Identifier.parse("minecraft:overworld")).success());
    }

    private void replicationOversizeDiagnostic() {
        final var selection = new ReplicationSelection(
            Identifier.parse("minecraft:overworld"), BlockPos.ZERO, Optional.of(new BlockPos(16, 16, 16))
        );
        assertEquals("selection_too_large", selection.diagnose(Identifier.parse("minecraft:overworld")).diagnostic());
    }

    private void replicationReady() {
        final var selection = new ReplicationSelection(
            Identifier.parse("minecraft:overworld"), BlockPos.ZERO, Optional.of(new BlockPos(3, 3, 3))
        );
        assertTrue(selection.diagnose(Identifier.parse("minecraft:overworld")).success());
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
