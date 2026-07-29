package com.kadamitas.warlockery.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record MachineDisplay(
    MachineRecipeManager.Diagnostic diagnostic,
    MachineStatus status,
    int progressPercent
) {
    public static final MachineDisplay EMPTY = new MachineDisplay(
        MachineRecipeManager.Diagnostic.EMPTY,
        MachineStatus.EMPTY,
        0
    );
    public static final Codec<MachineDisplay> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        MachineRecipeManager.Diagnostic.CODEC.fieldOf("diagnostic").forGetter(MachineDisplay::diagnostic),
        MachineStatus.CODEC.fieldOf("status").forGetter(MachineDisplay::status),
        Codec.INT.fieldOf("progress_percent").forGetter(MachineDisplay::progressPercent)
    ).apply(instance, MachineDisplay::new));

    public MachineDisplay {
        progressPercent = Math.clamp(progressPercent, 0, 100);
    }
}
