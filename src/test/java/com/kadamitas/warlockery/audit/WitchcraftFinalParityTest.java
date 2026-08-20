package com.kadamitas.warlockery.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kadamitas.warlockery.item.HexGuardRules;
import com.kadamitas.warlockery.item.InfusedBrewItem;
import com.kadamitas.warlockery.item.VampiricDollRules;
import com.kadamitas.warlockery.magic.ImpContractRules;
import com.kadamitas.warlockery.magic.MagicConstructRules;
import com.kadamitas.warlockery.magic.MagicPath;
import com.kadamitas.warlockery.magic.MagicPathProfile;
import com.kadamitas.warlockery.magic.MagicPathRules;
import com.kadamitas.warlockery.ritual.HexBehaviors;
import com.kadamitas.warlockery.ritual.RitualBindTarget;
import com.kadamitas.warlockery.ritual.ManifestationRules;
import com.kadamitas.warlockery.ritual.RitualAction;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

final class WitchcraftFinalParityTest {
    private static final Path DATA = Path.of("src", "main", "resources", "data", "warlockery");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @TestFactory
    Stream<DynamicContainer> oneFailureDiagnosticAndSuccessSuitePerAffectedPage() {
        return cases().stream().map(testCase -> DynamicContainer.dynamicContainer(testCase.page(), List.of(
            DynamicTest.dynamicTest("failure is explicit", testCase.failure()),
            DynamicTest.dynamicTest("visible state is stable", testCase.visibleState()),
            DynamicTest.dynamicTest("success has executable behavior", testCase.success())
        )));
    }

    private static List<PageCase> cases() {
        return List.of(
            page("Imp Magic", WitchcraftFinalParityTest::impFailure, WitchcraftFinalParityTest::impUi,
                WitchcraftFinalParityTest::impSuccess),
            page("Infernal Infusion", () -> pathFailure(MagicPath.INFERNAL), () -> pathUi(MagicPath.INFERNAL),
                WitchcraftFinalParityTest::infernalSuccess),
            page("Infused Brew of the Grave", () -> pathFailure(MagicPath.GRAVE), () -> pathUi(MagicPath.GRAVE),
                WitchcraftFinalParityTest::graveSuccess),
            page("Infusion of Light", () -> pathFailure(MagicPath.LIGHT), () -> pathUi(MagicPath.LIGHT),
                WitchcraftFinalParityTest::lightSuccess),
            page("Infusion of Otherwhere", () -> pathFailure(MagicPath.OTHERWHERE),
                () -> pathUi(MagicPath.OTHERWHERE), WitchcraftFinalParityTest::otherwhereSuccess),
            page("Infusion of the Overworld", () -> pathFailure(MagicPath.OVERWORLD),
                () -> pathUi(MagicPath.OVERWORLD), WitchcraftFinalParityTest::overworldSuccess),
            page("Rite of Binding", WitchcraftFinalParityTest::bindingFailure,
                WitchcraftFinalParityTest::bindingUi, WitchcraftFinalParityTest::bindingSuccess),
            page("Rite of Infusion", () -> pathFailure(MagicPath.SKY), () -> pathUi(MagicPath.SKY),
                WitchcraftFinalParityTest::infusionSuccess),
            page("Rite of Manifestation", WitchcraftFinalParityTest::manifestationFailure,
                WitchcraftFinalParityTest::manifestationUi, WitchcraftFinalParityTest::manifestationSuccess),
            page("Rite of Remove Curse", WitchcraftFinalParityTest::hexbreakingFailure,
                WitchcraftFinalParityTest::hexbreakingUi, WitchcraftFinalParityTest::hexbreakingSuccess),
            page("Vampiric Doll", WitchcraftFinalParityTest::vampiricFailure,
                WitchcraftFinalParityTest::vampiricUi, WitchcraftFinalParityTest::vampiricSuccess),
            page("Hex Guard Doll", WitchcraftFinalParityTest::hexGuardFailure,
                WitchcraftFinalParityTest::hexGuardUi, WitchcraftFinalParityTest::hexGuardSuccess)
        );
    }

    private static PageCase page(
        final String page,
        final Executable failure,
        final Executable visibleState,
        final Executable success
    ) {
        return new PageCase(page, failure, visibleState, success);
    }

    private static void impFailure() {
        assertEquals(
            ImpContractRules.Diagnostic.IMP_UNIMPRESSED,
            ImpContractRules.decide(true, true, false, 1, 4, true).diagnostic()
        );
        assertEquals(
            ImpContractRules.Diagnostic.TARGET_OTHER_DIMENSION,
            ImpContractRules.decide(true, true, false, 6, 4, false).diagnostic()
        );
    }

    private static void impUi() {
        final var decision = ImpContractRules.decide(true, true, false, 6, 4, true);
        assertEquals("message.warlockery.imp_contract.ready", decision.messageKey());
    }

    private static void impSuccess() {
        assertEquals(6, ImpContractRules.Spell.values().length);
        assertEquals(ImpContractRules.Spell.LIVING_FLAME,
            ImpContractRules.Spell.forItem("ingredient_contract_blaze").orElseThrow());
        assertTagContains("item", "creature_interactions/infernal_contracts", "warlockery:ingredient_contract_torment");
        assertTagContains("item", "creature_interactions/imp_gifts", "minecraft:diamond");
    }

    private static void pathFailure(final MagicPath path) {
        final int cost = MagicPathProfile.forPath(path).selfCost();
        assertEquals(
            MagicPathRules.Diagnostic.NOT_ATTUNED,
            MagicPathRules.decide(false, path.maximumReserve(), cost, true).diagnostic()
        );
        assertEquals(
            MagicPathRules.Diagnostic.INSUFFICIENT_RESERVE,
            MagicPathRules.decide(true, Math.max(0, cost - 1), cost, true).diagnostic()
        );
    }

    private static void pathUi(final MagicPath path) {
        final int cost = MagicPathProfile.forPath(path).selfCost();
        assertEquals(
            "message.warlockery.magic.ready",
            MagicPathRules.decide(true, path.maximumReserve(), cost, true).messageKey()
        );
    }

    private static void infernalSuccess() {
        assertRitual("infusion_hell", RitualAction.INFUSE_PATH.id(), "infernal");
        assertTagContains("entity_type", "infernal_sacrifices/fire", "minecraft:blaze");
        assertTagContains("entity_type", "infernal_sacrifices/teleport", "minecraft:enderman");
    }

    private static void graveSuccess() {
        assertEquals(144_000, InfusedBrewItem.GRAVE_DURATION);
        assertRitual("infuse_brew_grave", RitualAction.SUMMON_ITEM.id(), "warlockery:ingredient_brew_grave");
        assertTagContains("entity_type", "grave_nourishing_victims", "minecraft:villager");
    }

    private static void lightSuccess() {
        assertRitual("infusion_light", RitualAction.INFUSE_PATH.id(), "light");
        assertEquals(9, MagicConstructRules.wall(BlockPos.ZERO, Direction.NORTH).size());
        assertEquals(25, MagicConstructRules.prison(BlockPos.ZERO).size());
    }

    private static void otherwhereSuccess() {
        assertRitual("infusion_ender", RitualAction.INFUSE_PATH.id(), "otherwhere");
        assertEquals("otherwhere", MagicPath.require("otherwhere").id());
        assertTrue(MagicPathProfile.forPath(MagicPath.OTHERWHERE).worldCost() > 0);
    }

    private static void overworldSuccess() {
        assertRitual("infusion_earth", RitualAction.INFUSE_PATH.id(), "overworld");
        assertTagContains("block", "magic/earth_controlled_blocks", "#minecraft:base_stone_overworld");
        assertTagContains("item", "magic/metal_drops", "#c:ingots");
    }

    private static void bindingFailure() {
        assertTrue(RitualBindTarget.find("statue").isEmpty());
        assertTrue(RitualBindTarget.find("").isEmpty());
    }

    private static void bindingUi() {
        assertEquals(
            RitualBindTarget.FAMILIAR,
            RitualBindTarget.find(RitualBindTarget.FAMILIAR.id()).orElseThrow()
        );
        assertEquals(
            RitualBindTarget.SPECTRAL,
            RitualBindTarget.find(RitualBindTarget.SPECTRAL.id()).orElseThrow()
        );
    }

    private static void bindingSuccess() {
        assertRitual("bind_familiar", RitualAction.BIND_ENTITY.id(), "familiar");
        assertRitual("bind_spectral", RitualAction.BIND_ENTITY.id(), "spectral");
        assertRitual("bind_statue_player", RitualAction.BIND_ITEM.id(), "warlockery:statueofworship");
        assertRitual("bind_waystone_player", RitualAction.BIND_ITEM.id(), "warlockery:ingredient_waystone_creature_bound");
    }

    private static void infusionSuccess() {
        final Set<String> targets = Stream.of(
            "infusion_earth",
            "infusion_ender",
            "infusion_hell",
            "infusion_light",
            "infusion_sky"
        ).map(WitchcraftFinalParityTest::ritual)
            .peek(json -> assertEquals(RitualAction.INFUSE_PATH.id(), json.get("action").getAsString()))
            .map(json -> json.get("target").getAsString())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertEquals(Set.of("overworld", "otherwhere", "infernal", "light", "sky"), targets);
        assertEquals(RitualAction.RECHARGE_PATH.id(), ritual("recharge_infusion").get("action").getAsString());
    }

    private static void manifestationFailure() {
        assertEquals(
            ManifestationRules.Diagnostic.MISSING_BOUND_TARGET,
            ManifestationRules.decide(false, false, false).diagnostic()
        );
        assertEquals(
            ManifestationRules.Diagnostic.TARGET_AWAKE,
            ManifestationRules.decide(true, false, false).diagnostic()
        );
    }

    private static void manifestationUi() {
        assertEquals("sleeping_target", ManifestationRules.Diagnostic.TARGET_AWAKE.id());
        assertEquals("manifestation_ready", ManifestationRules.Diagnostic.READY.id());
    }

    private static void manifestationSuccess() {
        assertTrue(ManifestationRules.decide(true, true, false).ready());
        assertEquals(RitualAction.MANIFEST.id(), ritual("manifestation").get("action").getAsString());
        assertTagContains("entity_type", "manifestable_spirits", "#warlockery:spectral");
    }

    private static void hexbreakingFailure() {
        assertTrue(HexBehaviors.find("no_such_hex").isEmpty());
        assertFalse(HexBehaviors.supports("no_such_hex"));
    }

    private static void hexbreakingUi() {
        assertTrue(HexBehaviors.isPersistent("heat_metal"));
        assertFalse(HexBehaviors.isPersistent("blindness"));
    }

    private static void hexbreakingSuccess() {
        final Set<String> targets = Stream.of(
            "cure_heat_metal",
            "cure_insanity",
            "cure_misfortune",
            "cure_nightmare",
            "cure_overheating",
            "cure_sinking"
        ).map(WitchcraftFinalParityTest::ritual)
            .peek(json -> assertEquals(RitualAction.CLEANSE.id(), json.get("action").getAsString()))
            .map(json -> json.get("target").getAsString())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertEquals(
            Set.of("heat_metal", "insanity", "misfortune", "nightmare", "overheating", "sinking"),
            targets
        );
        targets.forEach(target -> assertTrue(HexBehaviors.supports(target)));
    }

    private static void vampiricFailure() {
        assertEquals(
            VampiricDollRules.Diagnostic.NO_VICTIM,
            VampiricDollRules.plan(10.0F, false, false).diagnostic()
        );
        assertEquals(
            VampiricDollRules.Diagnostic.BLOCKED,
            VampiricDollRules.plan(10.0F, true, true).diagnostic()
        );
    }

    private static void vampiricUi() {
        assertEquals(
            "message.warlockery.doll.vampiric.transferred",
            VampiricDollRules.plan(10.0F, true, false).messageKey()
        );
    }

    private static void vampiricSuccess() {
        final VampiricDollRules.TransferPlan plan = VampiricDollRules.plan(10.0F, true, false);
        assertEquals(5.0F, plan.protectedDamage());
        assertEquals(5.0F, plan.victimDamage());
    }

    private static void hexGuardFailure() {
        assertFalse(HexGuardRules.resolve(false, true, false).blocked());
    }

    private static void hexGuardUi() {
        assertEquals(
            "message.warlockery.doll.hex_guard.blocked_and_retaliated",
            HexGuardRules.resolve(true, true, false).messageKey()
        );
    }

    private static void hexGuardSuccess() {
        final HexGuardRules.Resolution resolution = HexGuardRules.resolve(true, true, false);
        assertTrue(resolution.blocked());
        assertTrue(resolution.retaliates());
        assertFalse(HexGuardRules.resolve(true, true, true).retaliates());
    }

    private static void assertRitual(final String id, final String action, final String target) {
        final JsonObject json = ritual(id);
        assertEquals(action, json.get("action").getAsString());
        assertEquals(target, json.get("target").getAsString());
    }

    private static JsonObject ritual(final String id) {
        return json(DATA.resolve("ritual").resolve(id + ".json"));
    }

    private static void assertTagContains(final String registry, final String id, final String expected) {
        final JsonArray values = json(DATA.resolve("tags").resolve(registry).resolve(id + ".json"))
            .getAsJsonArray("values");
        assertTrue(values.asList().stream().anyMatch(value -> value.getAsString().equals(expected)), registry + "/" + id);
    }

    private static JsonObject json(final Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private record PageCase(String page, Executable failure, Executable visibleState, Executable success) {
    }
}
