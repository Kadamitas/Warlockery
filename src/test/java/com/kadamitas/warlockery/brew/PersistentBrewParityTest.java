package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class PersistentBrewParityTest {
    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "warlockery");
    private static final Path RECIPES = Path.of(
        "src", "main", "resources", "data", "warlockery", "warlockery_machine"
    );
    private static final String RUNTIME = readText(Path.of(
        "src", "main", "java", "com", "kadamitas", "warlockery", "brew", "BrewPersistentRuntime.java"
    )) + readText(Path.of(
        "src", "main", "java", "com", "kadamitas", "warlockery", "brew", "BrewRuntime.java"
    ));
    private static final JsonObject TRANSLATIONS = json(ASSETS.resolve("lang/en_us.json"));
    private static final List<Case> CASES = List.of(
        marked("absorb_magic", BrewBehavior.APPLY_ABSORB_MAGIC, BrewMarkerKind.ABSORB_MAGIC, "handleDamage"),
        marked("attract_arrows", BrewBehavior.APPLY_ATTRACT_ARROWS, BrewMarkerKind.ATTRACT_ARROWS, "tickArrowAttraction"),
        fixed("bottling", BrewBehavior.BOTTLE_YIELD, "bottleYield"),
        marked("gas_immunity", BrewBehavior.APPLY_GAS_IMMUNITY, BrewMarkerKind.BREW_GAS_IMMUNITY, "tickGasImmunity"),
        marked("ender_inhibition", BrewBehavior.APPLY_ENDER_INHIBITION, BrewMarkerKind.ENDER_INHIBITION, "cancelTeleport"),
        marked("ill_fitting", BrewBehavior.APPLY_ILL_FITTING, BrewMarkerKind.ILL_FITTING, "tickIllFitting"),
        fixed("insanity", BrewBehavior.APPLY_INSANITY, "HexKind.INSANITY"),
        marked("keep_effects", BrewBehavior.APPLY_KEEP_EFFECTS, BrewMarkerKind.KEEP_EFFECTS, "storeEffects"),
        marked("keep_inventory", BrewBehavior.APPLY_KEEP_INVENTORY, BrewMarkerKind.KEEP_INVENTORY, "storeItems"),
        fixed("nightmare", BrewBehavior.APPLY_NIGHTMARE, "HexKind.WAKING_NIGHTMARE"),
        marked("poison_weapon", BrewBehavior.APPLY_POISON_WEAPON, BrewMarkerKind.POISON_WEAPON, "MobEffects.POISON"),
        marked("reflect_arrows", BrewBehavior.APPLY_REFLECT_ARROWS, BrewMarkerKind.REFLECT_ARROWS, "handleProjectileImpact"),
        marked("reflect_damage", BrewBehavior.APPLY_REFLECT_DAMAGE, BrewMarkerKind.REFLECT_DAMAGE, "reflectDamage"),
        marked("reincarnate", BrewBehavior.APPLY_REINCARNATE, BrewMarkerKind.REINCARNATE, "reincarnate"),
        marked("repel_attacker", BrewBehavior.APPLY_REPEL_ATTACKER, BrewMarkerKind.REPEL_ATTACKER, "radialVelocity"),
        marked("resizing", BrewBehavior.APPLY_RESIZING, BrewMarkerKind.RESIZING, "Attributes.SCALE"),
        fixed("shifting_seasons", BrewBehavior.SHIFT_SEASONS, "shiftSeasons"),
        fixed("summon_abyssal_regent", BrewBehavior.SUMMON_ABYSSAL_REGENT, "emberhorn_archfiend"),
        marked("tint_skin", BrewBehavior.APPLY_TINT_SKIN, BrewMarkerKind.TINT_SKIN, "tickTint"),
        marked("werewolf_lock", BrewBehavior.APPLY_WEREWOLF_LOCK, BrewMarkerKind.WEREWOLF_LOCK, "lockedForm"),
        marked("disease", BrewBehavior.APPLY_DISEASE, BrewMarkerKind.DISEASE, "tickContagion"),
        marked("infection", BrewBehavior.APPLY_INFECTION, BrewMarkerKind.INFECTION, "tickContagion"),
        marked("moonshine", BrewBehavior.APPLY_MOONSHINE, BrewMarkerKind.MOONSHINE, "tickMoonshine"),
        marked("sinking", BrewBehavior.APPLY_SINKING, BrewMarkerKind.SINKING, "tickSinking"),
        marked("undeads_curse", BrewBehavior.APPLY_SUNLIGHT_CURSE, BrewMarkerKind.SUNLIGHT_CURSE, "tickSunlightCurse"),
        marked("volatility", BrewBehavior.APPLY_VOLATILITY, BrewMarkerKind.VOLATILITY, "ExplosionInteraction.NONE")
    );

    @TestFactory
    Stream<DynamicContainer> oneDeliveryStateSuitePerBrew() {
        return CASES.stream().map(testCase -> DynamicContainer.dynamicContainer(
            "brew_" + testCase.id(),
            List.of(
                DynamicTest.dynamicTest("failure leaves no active state", () -> failureContract(testCase)),
                DynamicTest.dynamicTest("visible state is registered", () -> visibleStateContract(testCase)),
                DynamicTest.dynamicTest("success reaches a bounded server hook", () -> successContract(testCase))
            )
        ));
    }

    @Test
    void markerRulesClampReserveExpirationContagionAndSeason() {
        assertFalse(BrewMarkerRules.isActive(20L, 20L));
        assertEquals(0, BrewMarkerRules.remainingTicks(20L, 20L));
        assertEquals(BrewMarkerRules.MAX_ABSORBED_MAGIC, BrewMarkerRules.addAbsorbedMagic(99, 20.0F));
        assertEquals(8, BrewMarkerRules.contagionLimit(BrewMarkerKind.DISEASE));
        assertEquals(4, BrewMarkerRules.contagionLimit(BrewMarkerKind.INFECTION));
        assertEquals(3, BrewMarkerRules.season(72_000L));
        assertEquals(5.0F, BrewMarkerRules.moonshineDamage(10.0F));
        assertTrue(BrewMarkerRules.moonshineExhaustion() > 0.0F);
    }

    @Test
    void persistentBrewsExposeCrossModTags() {
        assertTrue(values(Path.of(
            "src", "main", "resources", "data", "warlockery", "tags", "block", "brew_gases.json"
        )).contains("warlockery:brewgas"));
        assertTrue(values(Path.of(
            "src", "main", "resources", "data", "warlockery", "tags", "damage_type", "magical_damage.json"
        )).contains("minecraft:magic"));
        assertTrue(values(Path.of(
            "src", "main", "resources", "data", "warlockery", "tags", "entity_type",
            "reincarnation_candidates.json"
        )).contains("minecraft:cow"));
    }

    private static void failureContract(final Case testCase) {
        assertTrue(BrewKind.find("missing_" + testCase.id()).isEmpty());
        assertFalse(BrewMarkerRules.isActive(100L, 100L));
        testCase.marker().ifPresent(marker -> {
            assertTrue(BrewMarkerKind.find("missing_" + marker.id()).isEmpty());
            assertTrue(marker.defaultDuration() > 0);
        });
    }

    private static void visibleStateContract(final Case testCase) {
        final String itemId = "brew_" + testCase.id();
        assertTrue(TRANSLATIONS.has("item.warlockery." + itemId));
        final JsonObject item = json(ASSETS.resolve("items/" + itemId + ".json"));
        assertEquals("warlockery:item/" + itemId, item.getAsJsonObject("model").get("model").getAsString());
        assertFalse(BrewKind.require(testCase.id()).behaviors().isEmpty());
    }

    private static void successContract(final Case testCase) {
        assertTrue(BrewKind.require(testCase.id()).behaviors().contains(testCase.behavior()));
        assertTrue(RUNTIME.contains(testCase.hook()), testCase.hook());
        final JsonObject recipe = json(RECIPES.resolve("kettle_brew_" + testCase.id() + ".json"));
        assertEquals("kettle", recipe.get("machine").getAsString());
        assertEquals(
            "warlockery:brew_" + testCase.id(),
            recipe.getAsJsonArray("outputs").get(0).getAsJsonObject().get("item").getAsString()
        );
    }

    private static Case marked(
        final String id,
        final BrewBehavior behavior,
        final BrewMarkerKind marker,
        final String hook
    ) {
        return new Case(id, behavior, Optional.of(marker), hook);
    }

    private static Case fixed(final String id, final BrewBehavior behavior, final String hook) {
        return new Case(id, behavior, Optional.empty(), hook);
    }

    private static JsonObject json(final Path path) {
        return JsonParser.parseString(readText(path)).getAsJsonObject();
    }

    private static List<String> values(final Path path) {
        return json(path).getAsJsonArray("values").asList().stream()
            .map(value -> value.getAsString())
            .toList();
    }

    private static String readText(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }

    private record Case(
        String id,
        BrewBehavior behavior,
        Optional<BrewMarkerKind> marker,
        String hook
    ) {
    }
}
