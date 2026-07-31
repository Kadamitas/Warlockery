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
    public static final RegistryObject<SoundEvent> MACHINE_OPEN = register("machine.open");
    public static final RegistryObject<SoundEvent> MACHINE_START = register("machine.start");
    public static final RegistryObject<SoundEvent> MACHINE_COMPLETE = register("machine.complete");
    public static final RegistryObject<SoundEvent> ALTAR_ATTUNE = register("altar.attune");
    public static final CreatureSoundSet GOBLIN = registerCreature("entity.goblin");
    public static final CreatureSoundSet HOBGOBLIN = registerCreature("entity.hobgoblin");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(final String id) {
        return REGISTRY.register(id, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, id)));
    }

    private static CreatureSoundSet registerCreature(final String id) {
        return new CreatureSoundSet(
            register(id + ".ambient"),
            register(id + ".hurt"),
            register(id + ".death"),
            register(id + ".trade"),
            register(id + ".reject"),
            register(id + ".work")
        );
    }

    public record CreatureSoundSet(
        RegistryObject<SoundEvent> ambient,
        RegistryObject<SoundEvent> hurt,
        RegistryObject<SoundEvent> death,
        RegistryObject<SoundEvent> trade,
        RegistryObject<SoundEvent> reject,
        RegistryObject<SoundEvent> work
    ) {
    }
}
