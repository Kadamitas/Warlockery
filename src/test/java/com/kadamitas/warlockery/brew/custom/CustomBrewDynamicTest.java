package com.kadamitas.warlockery.brew.custom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.kadamitas.warlockery.brew.BrewBehavior;
import com.kadamitas.warlockery.brew.BrewEffectSpec;
import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.diagnostic.DiagnosticChecklist;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestFactory;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

final class CustomBrewDynamicTest {
    private static final Path DEFINITIONS = Path.of(
        "src", "main", "resources", "data", "warlockery", "custom_brew_component"
    );
    private static final CustomBrewComponentDefinition CAPACITY = component(
        CustomBrewComponentRole.CAPACITY, 2, 1.0F, CustomBrewModifier.NONE, Optional.empty()
    );
    private static final CustomBrewComponentDefinition POWER = component(
        CustomBrewComponentRole.POWER, 1, 1.0F, CustomBrewModifier.NONE, Optional.empty()
    );
    private static final CustomBrewComponentDefinition DURATION = component(
        CustomBrewComponentRole.DURATION, 0, 2.0F, CustomBrewModifier.NONE, Optional.empty()
    );
    private static final CustomBrewComponentDefinition EFFECT = new CustomBrewComponentDefinition(
        "minecraft:sugar",
        CustomBrewComponentRole.EFFECT,
        0,
        1.0F,
        CustomBrewModifier.NONE,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        List.of(new BrewEffectSpec("minecraft:speed", 200, 0)),
        List.of(BrewBehavior.PUSH),
        2,
        0x55AAFF,
        0
    );
    private static final CustomBrewComponentDefinition CONTAINER = component(
        CustomBrewComponentRole.CONTAINER, 0, 1.0F, CustomBrewModifier.NONE, Optional.empty()
    );
    private static final CustomBrewComponentDefinition THROWABLE = component(
        CustomBrewComponentRole.DELIVERY, 0, 1.0F, CustomBrewModifier.NONE, Optional.of(CustomBrewDelivery.THROWABLE)
    );

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @TestFactory
    Stream<DynamicContainer> customBrewSuite() {
        return Stream.of(DynamicContainer.dynamicContainer("Custom Brew", List.of(
            DynamicTest.dynamicTest("invalid input updates the floating UI", this::invalidInput),
            DynamicTest.dynamicTest("wrong order is rejected", this::wrongOrder),
            DynamicTest.dynamicTest("component bound is enforced", this::tooManyComponents),
            DynamicTest.dynamicTest("over capacity reports the exact deficit", this::overCapacity),
            DynamicTest.dynamicTest("power modifier bound is enforced", this::powerLimit),
            DynamicTest.dynamicTest("duration modifier bound is enforced", this::durationLimit),
            DynamicTest.dynamicTest("conflicting delivery is rejected", this::deliveryConflict),
            DynamicTest.dynamicTest("invalid inversion is rejected", this::invalidModifier),
            DynamicTest.dynamicTest("missing effect is reported", this::missingEffect),
            DynamicTest.dynamicTest("missing bottle is reported", this::missingContainer),
            DynamicTest.dynamicTest("empty filtered payload is rejected", this::noApplicableEffect),
            DynamicTest.dynamicTest("missing water is reported", this::missingWater),
            DynamicTest.dynamicTest("wrong fluid is reported", this::wrongFluid),
            DynamicTest.dynamicTest("missing altar power is reported", this::missingPower),
            DynamicTest.dynamicTest("missing heat is reported", this::missingHeat),
            DynamicTest.dynamicTest("blocked output is reported", this::outputBlocked),
            DynamicTest.dynamicTest("floating UI turns green when ready", this::readyUi),
            DynamicTest.dynamicTest("formula serialization round trips", this::serialization),
            DynamicTest.dynamicTest("successful formula has bounded executable payload", this::success),
            DynamicTest.dynamicTest("drinkable delivery works", this::drinkableDelivery),
            DynamicTest.dynamicTest("throwable delivery works", this::throwableDelivery),
            DynamicTest.dynamicTest("JSON definitions expose every fixed brew and extension tags", this::dataDefinitions)
        )));
    }

    private void invalidInput() {
        final CustomBrewCauldronState state = CustomBrewComposer.invalidInput(
            List.of(ingredient("effect", EFFECT)),
            "examplemod:unknown_reagent",
            readyConditions()
        );
        assertFailure(state, CustomBrewFailure.INVALID_INPUT);
        assertEquals("examplemod:unknown_reagent", state.detail());
        assertEquals(List.of("effect"), state.acceptedInputs());
    }

    private void wrongOrder() {
        assertFailure(compose(List.of(
            ingredient("effect", EFFECT),
            ingredient("capacity", CAPACITY),
            ingredient("bottle", CONTAINER)
        )), CustomBrewFailure.WRONG_ORDER);
    }

    private void tooManyComponents() {
        final List<CustomBrewComposer.Ingredient> ingredients = java.util.stream.IntStream
            .range(0, CustomBrewFormula.MAX_COMPONENTS + 1)
            .mapToObj(index -> ingredient("capacity_" + index, CAPACITY))
            .toList();
        assertFailure(compose(ingredients), CustomBrewFailure.TOO_MANY_COMPONENTS);
    }

    private void overCapacity() {
        final CustomBrewComponentDefinition one = component(
            CustomBrewComponentRole.CAPACITY, 1, 1.0F, CustomBrewModifier.NONE, Optional.empty()
        );
        final CustomBrewCauldronState state = compose(List.of(
            ingredient("capacity", one),
            ingredient("effect", EFFECT),
            ingredient("bottle", CONTAINER)
        ));
        assertFailure(state, CustomBrewFailure.OVER_CAPACITY);
        assertEquals("1", state.detail());
        assertEquals(1, state.capacity());
        assertEquals(2, state.capacityCost());
    }

    private void powerLimit() {
        final ArrayList<CustomBrewComposer.Ingredient> ingredients = new ArrayList<>();
        ingredients.add(ingredient("capacity", CAPACITY));
        java.util.stream.IntStream.range(0, 9).forEach(index -> ingredients.add(ingredient("power_" + index, POWER)));
        ingredients.add(ingredient("effect", EFFECT));
        ingredients.add(ingredient("bottle", CONTAINER));
        assertFailure(compose(ingredients), CustomBrewFailure.POWER_LIMIT);
    }

    private void durationLimit() {
        final ArrayList<CustomBrewComposer.Ingredient> ingredients = new ArrayList<>();
        ingredients.add(ingredient("capacity", CAPACITY));
        java.util.stream.IntStream.range(0, 5).forEach(index -> ingredients.add(ingredient("duration_" + index, DURATION)));
        ingredients.add(ingredient("effect", EFFECT));
        ingredients.add(ingredient("bottle", CONTAINER));
        assertFailure(compose(ingredients), CustomBrewFailure.DURATION_LIMIT);
    }

    private void deliveryConflict() {
        assertFailure(compose(List.of(
            ingredient("capacity", CAPACITY),
            ingredient("throw_one", THROWABLE),
            ingredient("throw_two", THROWABLE),
            ingredient("effect", EFFECT),
            ingredient("bottle", CONTAINER)
        )), CustomBrewFailure.DELIVERY_CONFLICT);
    }

    private void invalidModifier() {
        final CustomBrewComponentDefinition invert = component(
            CustomBrewComponentRole.MODIFIER, 0, 1.0F, CustomBrewModifier.INVERT_NEXT, Optional.empty()
        );
        assertFailure(compose(List.of(
            ingredient("capacity", CAPACITY),
            ingredient("invert", invert),
            ingredient("effect", EFFECT),
            ingredient("bottle", CONTAINER)
        )), CustomBrewFailure.INVALID_MODIFIER);
    }

    private void missingEffect() {
        assertFailure(compose(List.of(
            ingredient("capacity", CAPACITY),
            ingredient("bottle", CONTAINER)
        )), CustomBrewFailure.MISSING_EFFECT);
    }

    private void missingContainer() {
        assertFailure(compose(List.of(
            ingredient("capacity", CAPACITY),
            ingredient("effect", EFFECT)
        )), CustomBrewFailure.MISSING_CONTAINER);
    }

    private void noApplicableEffect() {
        final CustomBrewComponentDefinition skipEntities = component(
            CustomBrewComponentRole.MODIFIER, 0, 1.0F, CustomBrewModifier.SKIP_ENTITIES, Optional.empty()
        );
        final CustomBrewComponentDefinition statusOnly = new CustomBrewComponentDefinition(
            "minecraft:sugar",
            CustomBrewComponentRole.EFFECT,
            0,
            1.0F,
            CustomBrewModifier.NONE,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            List.of(new BrewEffectSpec("minecraft:speed", 200, 0)),
            List.of(),
            2,
            0x55AAFF,
            0
        );
        assertFailure(compose(List.of(
            ingredient("capacity", CAPACITY),
            ingredient("skip_entities", skipEntities),
            ingredient("effect", statusOnly),
            ingredient("bottle", CONTAINER)
        )), CustomBrewFailure.NO_APPLICABLE_EFFECT);
    }

    private void missingWater() {
        assertFailure(compose(validIngredients(), new CustomBrewComposer.Conditions(
            0, true, "minecraft:empty", 50_000, true, true
        )), CustomBrewFailure.MISSING_WATER);
    }

    private void wrongFluid() {
        assertFailure(compose(validIngredients(), new CustomBrewComposer.Conditions(
            250, false, "minecraft:lava", 50_000, true, true
        )), CustomBrewFailure.WRONG_FLUID);
    }

    private void missingPower() {
        final CustomBrewCauldronState state = compose(validIngredients(), new CustomBrewComposer.Conditions(
            250, true, "minecraft:water", 0, true, true
        ));
        assertFailure(state, CustomBrewFailure.MISSING_POWER);
        assertTrue(state.requiredPower() > state.availablePower());
        assertNotNull(state.formula().orElseThrow());
    }

    private void missingHeat() {
        assertFailure(compose(validIngredients(), new CustomBrewComposer.Conditions(
            250, true, "minecraft:water", 50_000, false, true
        )), CustomBrewFailure.MISSING_HEAT);
    }

    private void outputBlocked() {
        assertFailure(compose(validIngredients(), new CustomBrewComposer.Conditions(
            250, true, "minecraft:water", 50_000, true, false
        )), CustomBrewFailure.OUTPUT_BLOCKED);
    }

    private void readyUi() {
        final CustomBrewCauldronState ready = compose(validIngredients());
        assertEquals(CustomBrewFailure.NONE, ready.failure());
        assertTrue(ready.ready());
        assertTrue(ready.showGreenCheck());
        assertEquals(DiagnosticChecklist.COMPLETE_MARKER, ready.checklist().marker());
        assertEquals(List.of("capacity", "effect", "bottle"), ready.acceptedInputs());
        assertEquals("effect", ready.selectedFormula());
    }

    private void serialization() {
        final CustomBrewFormula formula = compose(validIngredients()).formula().orElseThrow();
        final var encoded = CustomBrewFormula.CODEC.encodeStart(JsonOps.INSTANCE, formula).getOrThrow();
        final CustomBrewFormula decoded = CustomBrewFormula.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(formula, decoded);
    }

    private void success() {
        final CustomBrewFormula formula = compose(validIngredients()).formula().orElseThrow();
        assertFalse(formula.effects().isEmpty());
        assertFalse(formula.behaviors().isEmpty());
        assertTrue(formula.radius() <= 12.0F);
        assertTrue(formula.potency() <= 8.0F);
        assertTrue(formula.components().size() <= CustomBrewFormula.MAX_COMPONENTS);
        assertEquals(2, formula.capacity());
        assertEquals(2, formula.capacityCost());
    }

    private void drinkableDelivery() {
        final CustomBrewFormula formula = compose(validIngredients()).formula().orElseThrow();
        assertEquals(CustomBrewDelivery.DRINKABLE, formula.delivery());
        assertTrue(formula.drinkSeconds() >= 0.4F);
        assertFalse(formula.potionContents().customEffects().isEmpty());
    }

    private void throwableDelivery() {
        final CustomBrewFormula formula = compose(List.of(
            ingredient("capacity", CAPACITY),
            ingredient("throwable", THROWABLE),
            ingredient("effect", EFFECT),
            ingredient("bottle", CONTAINER)
        )).formula().orElseThrow();
        assertEquals(CustomBrewDelivery.THROWABLE, formula.delivery());
        assertFalse(formula.behaviorKind().behaviors().isEmpty());
    }

    private void dataDefinitions() {
        try (Stream<Path> files = Files.walk(DEFINITIONS)) {
            final List<Path> paths = files.filter(path -> path.toString().endsWith(".json")).toList();
            final List<CustomBrewComponentDefinition> definitions = paths.stream().map(path -> {
                try {
                    return CustomBrewComponentDefinition.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString(Files.readString(path))
                    ).getOrThrow(message -> new IllegalArgumentException(path + ": " + message));
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }).toList();
            assertEquals(BrewKind.builtIns().size(), definitions.stream()
                .filter(definition -> definition.role() == CustomBrewComponentRole.EFFECT)
                .count());
            assertTrue(definitions.stream().allMatch(CustomBrewComponentDefinition::structurallyValid));
            assertTrue(definitions.stream().anyMatch(definition -> definition.ingredient().startsWith("#")));
            assertTrue(definitions.stream().anyMatch(definition ->
                definition.delivery().filter(delivery -> delivery == CustomBrewDelivery.THROWABLE).isPresent()
            ));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static CustomBrewCauldronState compose(final List<CustomBrewComposer.Ingredient> ingredients) {
        return compose(ingredients, readyConditions());
    }

    private static CustomBrewCauldronState compose(
        final List<CustomBrewComposer.Ingredient> ingredients,
        final CustomBrewComposer.Conditions conditions
    ) {
        return CustomBrewComposer.compose(ingredients, conditions);
    }

    private static List<CustomBrewComposer.Ingredient> validIngredients() {
        return List.of(
            ingredient("capacity", CAPACITY),
            ingredient("effect", EFFECT),
            ingredient("bottle", CONTAINER)
        );
    }

    private static CustomBrewComposer.Conditions readyConditions() {
        return CustomBrewComposer.Conditions.ready(50_000);
    }

    private static CustomBrewComposer.Ingredient ingredient(
        final String id,
        final CustomBrewComponentDefinition definition
    ) {
        return CustomBrewComposer.Ingredient.of(id, definition);
    }

    private static CustomBrewComponentDefinition component(
        final CustomBrewComponentRole role,
        final int value,
        final float multiplier,
        final CustomBrewModifier modifier,
        final Optional<CustomBrewDelivery> delivery
    ) {
        return new CustomBrewComponentDefinition(
            "minecraft:stone",
            role,
            value,
            multiplier,
            modifier,
            delivery,
            Optional.empty(),
            Optional.empty(),
            List.of(),
            List.of(),
            0,
            0x385A46,
            0
        );
    }

    private static void assertFailure(
        final CustomBrewCauldronState state,
        final CustomBrewFailure expected
    ) {
        assertEquals(expected, state.failure());
        assertFalse(state.ready());
        assertFalse(state.showGreenCheck());
        assertEquals(DiagnosticChecklist.INCOMPLETE_MARKER, state.checklist().marker());
    }
}
