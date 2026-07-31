package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.registry.ModSounds;
import com.kadamitas.warlockery.util.DataParsing;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class RitualSessionData extends SavedData {
    private static final Codec<RitualSessionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Session.CODEC.listOf().optionalFieldOf("sessions", List.of()).forGetter(data -> data.sessions)
    ).apply(instance, RitualSessionData::new));

    public static final SavedDataType<RitualSessionData> TYPE = new SavedDataType<>(
        Identifier.parse(Warlockery.MOD_ID + ":ritual_sessions"),
        RitualSessionData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final List<Session> sessions;

    public RitualSessionData() {
        sessions = new ArrayList<>();
    }

    private RitualSessionData(final List<Session> sessions) {
        this.sessions = new ArrayList<>(sessions);
    }

    public static RitualSessionData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean start(
        final BlockPos center,
        final Identifier ritual,
        final UUID caster,
        final int castingTime
    ) {
        return start(center, ritual, caster, castingTime, 0);
    }

    public boolean start(
        final BlockPos center,
        final Identifier ritual,
        final UUID caster,
        final int castingTime,
        final int variant
    ) {
        if (sessions.stream().anyMatch(session -> session.center() == center.asLong())) {
            return false;
        }
        sessions.add(new Session(
            center.asLong(), ritual.toString(), caster.toString(), 0, Math.max(1, castingTime), variant
        ));
        setDirty();
        return true;
    }

    public boolean isActive(final BlockPos center) {
        return sessions.stream().anyMatch(session -> session.center() == center.asLong());
    }

    public void tick(final ServerLevel level) {
        if (sessions.isEmpty()) {
            return;
        }

        final List<Session> next = new ArrayList<>(sessions.size());
        for (Session session : sessions) {
            final Identifier ritualId = Identifier.tryParse(session.ritual());
            final BlockPos center = BlockPos.of(session.center());
            if (ritualId == null || !RitualManager.INSTANCE.isSessionValid(level, center, ritualId, session.variant())) {
                notifyCancelled(level, session, center);
                continue;
            }

            final int elapsed = session.elapsed() + 1;
            emitProgressEffects(level, center, elapsed, session.castingTime());
            if (elapsed >= session.castingTime()) {
                final var caster = DataParsing.uuid(session.caster()).map(level::getPlayerByUUID).orElse(null);
                RitualManager.INSTANCE.complete(level, center, caster, ritualId, session.variant());
            } else {
                next.add(new Session(
                    session.center(), session.ritual(), session.caster(), elapsed, session.castingTime(), session.variant()
                ));
            }
        }

        sessions.clear();
        sessions.addAll(next);
        setDirty();
    }

    private static void emitProgressEffects(
        final ServerLevel level,
        final BlockPos center,
        final int elapsed,
        final int total
    ) {
        if (elapsed % 2 == 0) {
            final double angle = elapsed * 0.32;
            final double radius = 2.0 + 2.0 * elapsed / total;
            level.sendParticles(
                ParticleTypes.WITCH,
                center.getX() + 0.5 + Math.cos(angle) * radius,
                center.getY() + 0.35 + elapsed / (double) total,
                center.getZ() + 0.5 + Math.sin(angle) * radius,
                2,
                0.12,
                0.08,
                0.12,
                0.01
            );
        }
        if (elapsed == 1 || elapsed % 20 == 0) {
            final float pitch = 0.65F + 0.7F * elapsed / total;
            level.playSound(null, center, elapsed % 40 == 0 ? ModSounds.HEARTBEAT.get() : SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, pitch);
        }
    }

    private static void notifyCancelled(final ServerLevel level, final Session session, final BlockPos center) {
        DataParsing.uuid(session.caster()).map(level::getPlayerByUUID).ifPresent(player ->
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.warlockery.ritual.cancelled"))
        );
        level.sendParticles(ParticleTypes.SMOKE, center.getX() + 0.5, center.getY() + 0.2, center.getZ() + 0.5, 24, 1.0, 0.3, 1.0, 0.02);
    }

    private record Session(long center, String ritual, String caster, int elapsed, int castingTime, int variant) {
        private static final Codec<Session> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("center").forGetter(Session::center),
            Codec.STRING.fieldOf("ritual").forGetter(Session::ritual),
            Codec.STRING.fieldOf("caster").forGetter(Session::caster),
            Codec.INT.optionalFieldOf("elapsed", 0).forGetter(Session::elapsed),
            Codec.INT.fieldOf("casting_time").forGetter(Session::castingTime),
            Codec.INT.optionalFieldOf("variant", 0).forGetter(Session::variant)
        ).apply(instance, Session::new));
    }
}
