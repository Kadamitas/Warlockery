package com.kadamitas.warlockery.brew.custom;

import com.kadamitas.warlockery.diagnostic.DiagnosticChecklist;
import com.kadamitas.warlockery.diagnostic.ReadinessUiState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;

public record CustomBrewCauldronState(
    boolean engaged,
    List<String> selectedEffects,
    List<String> acceptedInputs,
    CustomBrewFailure failure,
    String detail,
    int capacity,
    int capacityCost,
    int availablePower,
    int requiredPower,
    Optional<CustomBrewFormula> formula,
    int progressPercent
) implements ReadinessUiState {
    public static final CustomBrewCauldronState EMPTY = new CustomBrewCauldronState(
        false,
        List.of(),
        List.of(),
        CustomBrewFailure.NONE,
        "",
        0,
        0,
        0,
        0,
        Optional.empty(),
        0
    );
    public static final Codec<CustomBrewCauldronState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.fieldOf("engaged").forGetter(CustomBrewCauldronState::engaged),
        Codec.STRING.listOf().fieldOf("selected_effects").forGetter(CustomBrewCauldronState::selectedEffects),
        Codec.STRING.listOf().fieldOf("accepted_inputs").forGetter(CustomBrewCauldronState::acceptedInputs),
        CustomBrewFailure.CODEC.fieldOf("failure").forGetter(CustomBrewCauldronState::failure),
        Codec.STRING.optionalFieldOf("detail", "").forGetter(CustomBrewCauldronState::detail),
        Codec.INT.fieldOf("capacity").forGetter(CustomBrewCauldronState::capacity),
        Codec.INT.fieldOf("capacity_cost").forGetter(CustomBrewCauldronState::capacityCost),
        Codec.INT.fieldOf("available_power").forGetter(CustomBrewCauldronState::availablePower),
        Codec.INT.fieldOf("required_power").forGetter(CustomBrewCauldronState::requiredPower),
        CustomBrewFormula.CODEC.optionalFieldOf("formula").forGetter(CustomBrewCauldronState::formula),
        Codec.intRange(0, 100).fieldOf("progress_percent").forGetter(CustomBrewCauldronState::progressPercent)
    ).apply(instance, CustomBrewCauldronState::new));

    public CustomBrewCauldronState {
        selectedEffects = List.copyOf(selectedEffects);
        acceptedInputs = List.copyOf(acceptedInputs);
        detail = detail == null ? "" : detail;
        progressPercent = Math.clamp(progressPercent, 0, 100);
    }

    public boolean ready() {
        return engaged && failure == CustomBrewFailure.NONE && formula.isPresent();
    }

    public String selectedFormula() {
        return selectedEffects.isEmpty() ? "" : String.join(" + ", selectedEffects);
    }

    public CustomBrewCauldronState withProgress(final int percent) {
        return new CustomBrewCauldronState(
            engaged,
            selectedEffects,
            acceptedInputs,
            failure,
            detail,
            capacity,
            capacityCost,
            availablePower,
            requiredPower,
            formula,
            percent
        );
    }

    @Override
    public DiagnosticChecklist checklist() {
        return DiagnosticChecklist.from(List.of(
            engaged,
            !selectedEffects.isEmpty(),
            capacity >= capacityCost,
            availablePower >= requiredPower,
            failure == CustomBrewFailure.NONE,
            formula.isPresent()
        ));
    }
}
