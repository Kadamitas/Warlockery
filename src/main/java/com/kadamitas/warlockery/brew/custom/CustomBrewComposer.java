package com.kadamitas.warlockery.brew.custom;

import com.kadamitas.warlockery.brew.BrewBehavior;
import com.kadamitas.warlockery.brew.BrewEffectSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public final class CustomBrewComposer {
    public static final int WATER_REQUIRED = 250;
    private static final int MAX_POWER_LEVEL = 8;
    private static final int MAX_DURATION_MULTIPLIER = 16;
    private static final int MAX_EFFECT_DURATION = 36_000;

    private CustomBrewComposer() {
    }

    public static CustomBrewCauldronState invalidInput(
        final List<Ingredient> acceptedIngredients,
        final String rejectedItem,
        final Conditions conditions
    ) {
        final List<String> accepted = acceptedIngredients.stream().map(Ingredient::id).toList();
        final List<String> selected = acceptedIngredients.stream()
            .filter(ingredient -> ingredient.definition().role() == CustomBrewComponentRole.EFFECT)
            .map(Ingredient::id)
            .toList();
        return failed(
            acceptedIngredients,
            accepted,
            selected,
            CustomBrewFailure.INVALID_INPUT,
            rejectedItem,
            0,
            0,
            conditions,
            Optional.empty()
        );
    }

    public static CustomBrewCauldronState compose(
        final List<Ingredient> ingredients,
        final Conditions conditions
    ) {
        if (ingredients.isEmpty()) {
            return CustomBrewCauldronState.EMPTY;
        }
        if (ingredients.size() > CustomBrewFormula.MAX_COMPONENTS) {
            return failed(
                ingredients,
                List.of(),
                List.of(),
                CustomBrewFailure.TOO_MANY_COMPONENTS,
                Integer.toString(CustomBrewFormula.MAX_COMPONENTS),
                0,
                0,
                conditions,
                Optional.empty()
            );
        }

        final ArrayList<String> accepted = new ArrayList<>();
        final ArrayList<String> selectedEffects = new ArrayList<>();
        final ArrayList<BrewEffectSpec> effects = new ArrayList<>();
        final ArrayList<BrewBehavior> behaviors = new ArrayList<>();
        int capacity = 0;
        int capacityCost = 0;
        int powerLevel = 0;
        int durationMultiplier = 1;
        int extent = 1;
        int lingering = 1;
        int color = 0x385A46;
        int quaff = 0;
        int highestOrder = -1;
        int containers = 0;
        float radius = 4.0F;
        float potency = 1.0F;
        boolean pendingInversion = false;
        boolean hideParticles = false;
        boolean skipBlocks = false;
        boolean skipEntities = false;
        boolean uncappedDamage = false;
        Optional<CustomBrewDelivery> explicitDelivery = Optional.empty();

        for (int index = 0; index < ingredients.size(); index++) {
            final Ingredient ingredient = ingredients.get(index);
            final CustomBrewComponentDefinition definition = ingredient.definition();
            final boolean lateInversion = definition.role() == CustomBrewComponentRole.MODIFIER
                && definition.modifier() == CustomBrewModifier.INVERT_NEXT
                && highestOrder == CustomBrewComponentRole.EFFECT.order();
            if ((!lateInversion && definition.role().order() < highestOrder)
                || containers > 0
                || definition.role() == CustomBrewComponentRole.CONTAINER && index != ingredients.size() - 1) {
                return failed(
                    ingredients,
                    accepted,
                    selectedEffects,
                    CustomBrewFailure.WRONG_ORDER,
                    ingredient.id(),
                    capacity,
                    capacityCost,
                    conditions,
                    Optional.empty()
                );
            }

            switch (definition.role()) {
                case CAPACITY -> capacity += definition.value();
                case POWER -> powerLevel += definition.value();
                case DURATION -> durationMultiplier = Math.round(durationMultiplier * definition.multiplier());
                case EXTENT -> extent = Math.max(extent, definition.value());
                case LINGERING -> lingering = Math.max(lingering, definition.value());
                case DELIVERY -> {
                    if (explicitDelivery.isPresent()) {
                        return failed(
                            ingredients,
                            accepted,
                            selectedEffects,
                            CustomBrewFailure.DELIVERY_CONFLICT,
                            ingredient.id(),
                            capacity,
                            capacityCost,
                            conditions,
                            Optional.empty()
                        );
                    }
                    explicitDelivery = definition.delivery();
                }
                case MODIFIER -> {
                    switch (definition.modifier()) {
                        case HIDE_PARTICLES -> hideParticles = true;
                        case INVERT_NEXT -> {
                            if (pendingInversion) {
                                return failed(
                                    ingredients,
                                    accepted,
                                    selectedEffects,
                                    CustomBrewFailure.INVALID_MODIFIER,
                                    ingredient.id(),
                                    capacity,
                                    capacityCost,
                                    conditions,
                                    Optional.empty()
                                );
                            }
                            pendingInversion = true;
                        }
                        case SKIP_BLOCKS -> skipBlocks = true;
                        case SKIP_ENTITIES -> skipEntities = true;
                        case COLOR_FROM_INGREDIENT -> color = ingredient.color().orElse(color);
                        case UNCAPPED_DAMAGE -> uncappedDamage = true;
                        case QUAFF -> quaff += definition.value();
                        case NONE -> {
                        }
                    }
                }
                case EFFECT -> {
                    final CustomBrewComponentDefinition.Payload payload = definition.payload(pendingInversion);
                    if (payload.empty()) {
                        return failed(
                            ingredients,
                            accepted,
                            selectedEffects,
                            CustomBrewFailure.INVALID_MODIFIER,
                            ingredient.id(),
                            capacity,
                            capacityCost,
                            conditions,
                            Optional.empty()
                        );
                    }
                    pendingInversion = false;
                    capacityCost += definition.capacityCost();
                    effects.addAll(payload.effects());
                    behaviors.addAll(payload.behaviors());
                    radius = Math.max(radius, payload.radius());
                    potency = Math.max(potency, payload.potency());
                    color = payload.color();
                    selectedEffects.add(ingredient.id());
                }
                case CONTAINER -> containers++;
            }
            accepted.add(ingredient.id());
            if (!lateInversion) {
                highestOrder = Math.max(highestOrder, definition.role().order());
            }
        }

        if (powerLevel > MAX_POWER_LEVEL) {
            return failed(
                ingredients,
                accepted,
                selectedEffects,
                CustomBrewFailure.POWER_LIMIT,
                Integer.toString(MAX_POWER_LEVEL),
                capacity,
                capacityCost,
                conditions,
                Optional.empty()
            );
        }
        if (durationMultiplier > MAX_DURATION_MULTIPLIER) {
            return failed(
                ingredients,
                accepted,
                selectedEffects,
                CustomBrewFailure.DURATION_LIMIT,
                Integer.toString(MAX_DURATION_MULTIPLIER),
                capacity,
                capacityCost,
                conditions,
                Optional.empty()
            );
        }
        if (pendingInversion) {
            return failed(
                ingredients,
                accepted,
                selectedEffects,
                CustomBrewFailure.INVALID_MODIFIER,
                "invert_next",
                capacity,
                capacityCost,
                conditions,
                Optional.empty()
            );
        }
        if (selectedEffects.isEmpty()) {
            return failed(
                ingredients,
                accepted,
                selectedEffects,
                CustomBrewFailure.MISSING_EFFECT,
                "effect",
                capacity,
                capacityCost,
                conditions,
                Optional.empty()
            );
        }
        if (containers == 0) {
            return failed(
                ingredients,
                accepted,
                selectedEffects,
                CustomBrewFailure.MISSING_CONTAINER,
                "container",
                capacity,
                capacityCost,
                conditions,
                Optional.empty()
            );
        }
        if (capacity < capacityCost) {
            return failed(
                ingredients,
                accepted,
                selectedEffects,
                CustomBrewFailure.OVER_CAPACITY,
                Integer.toString(capacityCost - capacity),
                capacity,
                capacityCost,
                conditions,
                Optional.empty()
            );
        }

        final boolean blocksSkipped = skipBlocks;
        final boolean entitiesSkipped = skipEntities;
        final int finalPowerLevel = powerLevel;
        final int finalDurationMultiplier = durationMultiplier;
        final int finalLingering = Math.clamp(lingering, 1, 4);
        final List<BrewEffectSpec> filteredEffects = entitiesSkipped ? List.of() : effects.stream()
            .limit(CustomBrewFormula.MAX_PAYLOADS)
            .map(effect -> powered(effect, finalPowerLevel, finalDurationMultiplier * finalLingering))
            .toList();
        final List<BrewBehavior> filteredBehaviors = behaviors.stream()
            .filter(behavior -> !blocksSkipped || !CustomBrewBehaviorTargets.affectsBlocks(behavior))
            .filter(behavior -> !entitiesSkipped || !CustomBrewBehaviorTargets.affectsEntities(behavior))
            .distinct()
            .limit(CustomBrewFormula.MAX_PAYLOADS)
            .toList();
        if (filteredEffects.isEmpty() && filteredBehaviors.isEmpty()) {
            return failed(
                ingredients,
                accepted,
                selectedEffects,
                CustomBrewFailure.NO_APPLICABLE_EFFECT,
                "payload",
                capacity,
                capacityCost,
                conditions,
                Optional.empty()
            );
        }

        final int requiredPower = Math.clamp(
            500
                + capacityCost * 250
                + powerLevel * 300
                + Math.max(0, durationMultiplier - 1) * 100
                + Math.max(0, extent - 1) * 200
                + Math.max(0, lingering - 1) * 150,
            0,
            50_000
        );
        final float finalRadius = Math.clamp(radius * (1.0F + (extent - 1) * 0.5F), 0.5F, 12.0F);
        final float finalPotency = Math.clamp(potency + powerLevel * 0.5F, 0.1F, 8.0F);
        final CustomBrewFormula formula = new CustomBrewFormula(
            List.copyOf(accepted),
            List.copyOf(selectedEffects),
            explicitDelivery.orElse(CustomBrewDelivery.DRINKABLE),
            filteredEffects,
            filteredBehaviors,
            capacity,
            capacityCost,
            powerLevel,
            durationMultiplier,
            Math.clamp(extent, 1, 4),
            Math.clamp(lingering, 1, 4),
            requiredPower,
            color,
            finalRadius,
            finalPotency,
            hideParticles,
            skipBlocks,
            skipEntities,
            uncappedDamage,
            Math.clamp(quaff, 0, 6)
        );

        final CustomBrewFailure environmentFailure;
        final String detail;
        if (!conditions.waterCompatible() && conditions.fluidAmount() > 0) {
            environmentFailure = CustomBrewFailure.WRONG_FLUID;
            detail = conditions.fluidId();
        } else if (conditions.fluidAmount() < WATER_REQUIRED) {
            environmentFailure = CustomBrewFailure.MISSING_WATER;
            detail = Integer.toString(WATER_REQUIRED - conditions.fluidAmount());
        } else if (conditions.availablePower() < requiredPower) {
            environmentFailure = CustomBrewFailure.MISSING_POWER;
            detail = Integer.toString(requiredPower - conditions.availablePower());
        } else if (!conditions.heated()) {
            environmentFailure = CustomBrewFailure.MISSING_HEAT;
            detail = "heat";
        } else if (!conditions.outputAvailable()) {
            environmentFailure = CustomBrewFailure.OUTPUT_BLOCKED;
            detail = "output";
        } else {
            environmentFailure = CustomBrewFailure.NONE;
            detail = "";
        }
        return failed(
            ingredients,
            accepted,
            selectedEffects,
            environmentFailure,
            detail,
            capacity,
            capacityCost,
            conditions,
            Optional.of(formula)
        );
    }

    private static BrewEffectSpec powered(
        final BrewEffectSpec effect,
        final int powerLevel,
        final int durationMultiplier
    ) {
        final long duration = Math.clamp((long) effect.duration() * durationMultiplier, 1L, MAX_EFFECT_DURATION);
        return new BrewEffectSpec(
            effect.effect(),
            (int) duration,
            Math.clamp(effect.amplifier() + powerLevel, 0, 255)
        );
    }

    private static CustomBrewCauldronState failed(
        final List<Ingredient> ingredients,
        final List<String> accepted,
        final List<String> selectedEffects,
        final CustomBrewFailure failure,
        final String detail,
        final int capacity,
        final int capacityCost,
        final Conditions conditions,
        final Optional<CustomBrewFormula> formula
    ) {
        return new CustomBrewCauldronState(
            !ingredients.isEmpty(),
            selectedEffects,
            accepted,
            failure,
            detail,
            capacity,
            capacityCost,
            conditions.availablePower(),
            formula.map(CustomBrewFormula::altarPower).orElse(0),
            formula,
            0
        );
    }

    public record Ingredient(
        String id,
        CustomBrewComponentDefinition definition,
        OptionalInt color
    ) {
        public Ingredient {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Custom brew component id cannot be blank");
            }
            color = color == null ? OptionalInt.empty() : color;
        }

        public static Ingredient of(final String id, final CustomBrewComponentDefinition definition) {
            return new Ingredient(id, definition, OptionalInt.empty());
        }
    }

    public record Conditions(
        int fluidAmount,
        boolean waterCompatible,
        String fluidId,
        int availablePower,
        boolean heated,
        boolean outputAvailable
    ) {
        public static Conditions ready(final int availablePower) {
            return new Conditions(WATER_REQUIRED, true, "minecraft:water", availablePower, true, true);
        }

        public Conditions {
            fluidAmount = Math.max(0, fluidAmount);
            availablePower = Math.max(0, availablePower);
            fluidId = fluidId == null ? "" : fluidId;
        }
    }
}
