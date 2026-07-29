package com.kadamitas.warlockery.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.kadamitas.warlockery.util.CountedIngredient;
import java.util.List;
import java.util.Optional;

public record MachineRecipeDefinition(
    String machine,
    List<Input> inputs,
    List<Output> outputs,
    int processingTime,
    boolean requiresFuel,
    Optional<FluidInput> fluid,
    int altarPower
) {
    public static final Codec<MachineRecipeDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("machine").forGetter(MachineRecipeDefinition::machine),
        Input.CODEC.listOf().fieldOf("inputs").forGetter(MachineRecipeDefinition::inputs),
        Output.CODEC.listOf().fieldOf("outputs").forGetter(MachineRecipeDefinition::outputs),
        Codec.INT.optionalFieldOf("processing_time", 200).forGetter(MachineRecipeDefinition::processingTime),
        Codec.BOOL.optionalFieldOf("requires_fuel", false).forGetter(MachineRecipeDefinition::requiresFuel),
        FluidInput.CODEC.optionalFieldOf("fluid").forGetter(MachineRecipeDefinition::fluid),
        Codec.INT.optionalFieldOf("altar_power", 0).forGetter(MachineRecipeDefinition::altarPower)
    ).apply(instance, MachineRecipeDefinition::new));

    public MachineRecipeDefinition {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        fluid = fluid == null ? Optional.empty() : fluid;
        if (inputs.isEmpty() || inputs.size() > 6) {
            throw new IllegalArgumentException("A machine recipe needs between one and six inputs");
        }
        if (outputs.isEmpty() || outputs.size() > 4) {
            throw new IllegalArgumentException("A machine recipe needs between one and four outputs");
        }
        if (processingTime <= 0) {
            throw new IllegalArgumentException("processing_time must be positive");
        }
        if (altarPower < 0) {
            throw new IllegalArgumentException("altar_power cannot be negative");
        }
    }

    public record FluidInput(String ingredient, int amount) {
        public static final Codec<FluidInput> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("ingredient").forGetter(FluidInput::ingredient),
            Codec.INT.optionalFieldOf("amount", 250).forGetter(FluidInput::amount)
        ).apply(instance, FluidInput::new));

        public FluidInput {
            if (ingredient.isBlank() || amount <= 0) {
                throw new IllegalArgumentException("Machine fluid ingredient and amount must be valid");
            }
        }
    }

    public record Input(String ingredient, int count) implements CountedIngredient {
        public static final Codec<Input> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("ingredient").forGetter(Input::ingredient),
            Codec.INT.optionalFieldOf("count", 1).forGetter(Input::count)
        ).apply(instance, Input::new));

        public Input {
            if (ingredient.isBlank() || count <= 0) {
                throw new IllegalArgumentException("Machine input ingredient and count must be valid");
            }
        }
    }

    public record Output(String item, int count) {
        public static final Codec<Output> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("item").forGetter(Output::item),
            Codec.INT.optionalFieldOf("count", 1).forGetter(Output::count)
        ).apply(instance, Output::new));

        public Output {
            if (item.isBlank() || count <= 0) {
                throw new IllegalArgumentException("Machine output item and count must be valid");
            }
        }
    }
}
