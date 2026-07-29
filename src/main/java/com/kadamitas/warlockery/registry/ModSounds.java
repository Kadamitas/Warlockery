package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(Registries.SOUND_EVENT, Warlockery.MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> CHALK = register("random.chalk");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEARTBEAT = register("random.heartbeat");
    public static final DeferredHolder<SoundEvent, SoundEvent> HYPNOSIS = register("random.hypnosis");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOLL_ACTIVATE = register("doll.activate");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(final String id) {
        return REGISTRY.register(id, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, id)));
    }
}
