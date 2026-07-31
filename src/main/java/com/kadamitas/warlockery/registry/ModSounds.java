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
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_OPEN = register("machine.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_START = register("machine.start");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_COMPLETE = register("machine.complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> ALTAR_ATTUNE = register("altar.attune");
    public static final CreatureSoundSet GOBLIN = registerCreature("entity.goblin");
    public static final CreatureSoundSet HOBGOBLIN = registerCreature("entity.hobgoblin");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(final String id) {
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
        DeferredHolder<SoundEvent, SoundEvent> ambient,
        DeferredHolder<SoundEvent, SoundEvent> hurt,
        DeferredHolder<SoundEvent, SoundEvent> death,
        DeferredHolder<SoundEvent, SoundEvent> trade,
        DeferredHolder<SoundEvent, SoundEvent> reject,
        DeferredHolder<SoundEvent, SoundEvent> work
    ) {
    }
}
