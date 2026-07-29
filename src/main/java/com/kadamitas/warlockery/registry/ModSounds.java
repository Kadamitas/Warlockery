package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Warlockery.MOD_ID);
    public static final RegistryObject<SoundEvent> CHALK = register("random.chalk");
    public static final RegistryObject<SoundEvent> HEARTBEAT = register("random.heartbeat");
    public static final RegistryObject<SoundEvent> HYPNOSIS = register("random.hypnosis");
    public static final RegistryObject<SoundEvent> DOLL_ACTIVATE = register("doll.activate");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(final String id) {
        return REGISTRY.register(id, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, id)));
    }
}
