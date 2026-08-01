package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
    private static final List<RegistrationHandle<SoundEvent>> ALL = new ArrayList<>();
    public static final RegistrationHandle<SoundEvent> CHALK = create("random.chalk");
    public static final RegistrationHandle<SoundEvent> HEARTBEAT = create("random.heartbeat");
    public static final RegistrationHandle<SoundEvent> HYPNOSIS = create("random.hypnosis");
    public static final RegistrationHandle<SoundEvent> DOLL_ACTIVATE = create("doll.activate");
    public static final RegistrationHandle<SoundEvent> MACHINE_OPEN = create("machine.open");
    public static final RegistrationHandle<SoundEvent> MACHINE_START = create("machine.start");
    public static final RegistrationHandle<SoundEvent> MACHINE_COMPLETE = create("machine.complete");
    public static final RegistrationHandle<SoundEvent> ALTAR_ATTUNE = create("altar.attune");
    public static final CreatureSoundSet GOBLIN = registerCreature("entity.goblin");
    public static final CreatureSoundSet HOBGOBLIN = registerCreature("entity.hobgoblin");

    private ModSounds() {
    }

    private static RegistrationHandle<SoundEvent> create(final String id) {
        final RegistrationHandle<SoundEvent> handle = RegistrationHandle.create(
            id,
            () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, id))
        );
        ALL.add(handle);
        return handle;
    }

    private static CreatureSoundSet registerCreature(final String id) {
        return new CreatureSoundSet(
            create(id + ".ambient"),
            create(id + ".hurt"),
            create(id + ".death"),
            create(id + ".trade"),
            create(id + ".reject"),
            create(id + ".work")
        );
    }

    public static void register() {
        ALL.forEach(handle -> handle.register(BuiltInRegistries.SOUND_EVENT));
    }

    public record CreatureSoundSet(
        RegistrationHandle<SoundEvent> ambient,
        RegistrationHandle<SoundEvent> hurt,
        RegistrationHandle<SoundEvent> death,
        RegistrationHandle<SoundEvent> trade,
        RegistrationHandle<SoundEvent> reject,
        RegistrationHandle<SoundEvent> work
    ) {
    }
}
