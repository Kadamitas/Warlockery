package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.block.entity.AltarBlockEntity;
import com.kadamitas.warlockery.registry.ModSounds;
import com.kadamitas.warlockery.util.DataParsing;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

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
        final int castingTime,
        final int variant
    ) {
        return start(center, ritual, caster, castingTime, variant, center, 0);
    }

    /**
     * Records a cast, including which altar is holding power for it and how much. The session is the only
     * record of that promise, which is what lets an interrupted cast give the power back.
     */
    public boolean start(
        final BlockPos center,
        final Identifier ritual,
        final UUID caster,
        final int castingTime,
        final int variant,
        final BlockPos escrowAltar,
        final int escrowPower
    ) {
        if (sessions.stream().anyMatch(session -> session.center() == center.asLong())) {
            return false;
        }
        sessions.add(new Session(
            center.asLong(), ritual.toString(), caster.toString(), 0, Math.max(1, castingTime), variant,
            escrowAltar.asLong(), Math.max(0, escrowPower)
        ));
        setDirty();
        return true;
    }

    public boolean isActive(final BlockPos center) {
        return sessions.stream().anyMatch(session -> session.center() == center.asLong());
    }

    /** How much power live sessions have promised the altar at this position. */
    public int escrowedAt(final BlockPos altar) {
        return sessions.stream()
            .filter(session -> session.escrowAltar() == altar.asLong())
            .mapToInt(Session::escrowPower)
            .sum();
    }

    /**
     * Stops the cast at this centre on the caster's own say-so, returning whatever it was holding.
     *
     * <p>Without this the only way out of a long cast is an interruption the player did not choose, so the
     * refund path would only ever run when something had already gone wrong.</p>
     */
    public boolean cancel(final ServerLevel level, final BlockPos center, final UUID caster) {
        final Optional<Session> owned = sessions.stream()
            .filter(session -> session.center() == center.asLong())
            .filter(session -> session.caster().equals(caster.toString()))
            .findFirst();
        owned.ifPresent(session -> {
            releaseEscrow(level, session);
            sessions.remove(session);
            setDirty();
            level.sendParticles(
                ParticleTypes.SMOKE, center.getX() + 0.5, center.getY() + 0.2, center.getZ() + 0.5,
                24, 1.0, 0.3, 1.0, 0.02
            );
        });
        return owned.isPresent();
    }

    public void tick(final ServerLevel level) {
        tick(level, RitualManager.INSTANCE::complete);
    }

    void tick(final ServerLevel level, final TerminalEffect terminalEffect) {
        if (sessions.isEmpty()) {
            return;
        }

        final List<Session> next = new ArrayList<>(sessions.size());
        final List<Finished> finished = new ArrayList<>();
        for (Session session : sessions) {
            final Identifier ritualId = Identifier.tryParse(session.ritual());
            final BlockPos center = BlockPos.of(session.center());
            final var caster = DataParsing.uuid(session.caster()).map(level::getPlayerByUUID).orElse(null);
            if (ritualId == null) {
                Warlockery.LOGGER.error("Cancelling ritual session with unreadable ritual id {}", session.ritual());
                releaseEscrow(level, session);
                notifyCancelled(level, session, center, List.of());
                continue;
            }
            final List<RitualManager.RequirementStatus> obstacles =
                RitualManager.INSTANCE.castingObstacles(level, center, ritualId, session.variant(), caster);
            if (!obstacles.isEmpty()) {
                releaseEscrow(level, session);
                notifyCancelled(level, session, center, obstacles);
                continue;
            }

            final int elapsed = session.elapsed() + 1;
            emitProgressEffects(level, center, elapsed, session.castingTime());
            if (elapsed >= session.castingTime()) {
                // Settled here rather than beside the terminal effect below. Both the settlement and the
                // refunds above are decided in this loop and committed by the assignment that follows it, so a
                // terminal effect that fails cannot make the sweep run either of them a second time.
                settleEscrow(level, session);
                finished.add(new Finished(center, caster, ritualId, session.variant(), session.caster()));
            } else {
                next.add(new Session(
                    session.center(), session.ritual(), session.caster(), elapsed, session.castingTime(),
                    session.variant(), session.escrowAltar(), session.escrowPower()
                ));
            }
        }

        if (!next.equals(sessions)) {
            sessions.clear();
            sessions.addAll(next);
            setDirty();
        }

        // The session list is committed above before any terminal effect runs. A terminal effect mutates the
        // world and can fail part way through; if it ran while the list still held the finished session, the
        // escaping failure would discard the rebuilt list, leave the session at its old elapsed, and replay the
        // partial mutation on every following tick. Containing the failure here also keeps one bad cast from
        // ending the level tick for every other system swept alongside this one.
        for (final Finished completion : finished) {
            try {
                terminalEffect.run(
                    level,
                    completion.center(),
                    completion.caster(),
                    completion.ritual(),
                    completion.variant()
                );
            } catch (RuntimeException failure) {
                Warlockery.LOGGER.error(
                    "Ritual {} at {} cast by {} failed to complete",
                    completion.ritual(),
                    completion.center(),
                    completion.casterId(),
                    failure
                );
            }
        }
    }

    private static void releaseEscrow(final ServerLevel level, final Session session) {
        altarFor(level, session).ifPresent(altar -> altar.releaseEscrow(session.escrowPower()));
    }

    private static void settleEscrow(final ServerLevel level, final Session session) {
        altarFor(level, session).ifPresent(altar -> altar.settleEscrow(session.escrowPower()));
    }

    private static Optional<AltarBlockEntity> altarFor(final ServerLevel level, final Session session) {
        if (session.escrowPower() <= 0) {
            return Optional.empty();
        }
        final BlockPos position = BlockPos.of(session.escrowAltar());
        return level.isLoaded(position) && level.getBlockEntity(position) instanceof AltarBlockEntity altar
            ? Optional.of(altar)
            : Optional.empty();
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

    private static void notifyCancelled(
        final ServerLevel level,
        final Session session,
        final BlockPos center,
        final List<RitualManager.RequirementStatus> obstacles
    ) {
        // The requirements that ended the cast were computed one call ago and used to be dropped here, leaving
        // the caster a single sentence that blamed the circle no matter what had actually lapsed.
        onlineCaster(level, session.caster()).ifPresent(player -> player.sendSystemMessage(
            RitualRequirementText.notice(
                obstacles,
                "message.warlockery.ritual.cancelled_requirements",
                "message.warlockery.ritual.cancelled"
            )
        ));
        level.sendParticles(ParticleTypes.SMOKE, center.getX() + 0.5, center.getY() + 0.2, center.getZ() + 0.5, 24, 1.0, 0.3, 1.0, 0.02);
    }

    /**
     * The caster wherever they are on the server, for telling them what became of their rite.
     *
     * <p>Resolving through this level alone cannot tell a caster who logged out from one who stepped through a
     * portal, so a cast that collapsed while its owner was in another dimension said nothing to anybody. This
     * answers only "is this player still connected"; it must never be handed to anything that changes the
     * world, because the player it returns may belong to a different level than the one being swept.</p>
     */
    static Optional<ServerPlayer> onlineCaster(final ServerLevel level, final String caster) {
        return DataParsing.uuid(caster).map(uuid -> level.getServer().getPlayerList().getPlayer(uuid));
    }

    /**
     * The world mutation a finished cast performs. Named separately from the sweep because the sweep owns the
     * session list and the terminal effect does not, so the two must not share a failure.
     */
    @FunctionalInterface
    interface TerminalEffect {
        void run(
            ServerLevel level,
            BlockPos center,
            @Nullable Player caster,
            Identifier ritual,
            int variant
        );
    }

    private record Finished(
        BlockPos center,
        @Nullable Player caster,
        Identifier ritual,
        int variant,
        String casterId
    ) {
    }

    private record Session(
        long center,
        String ritual,
        String caster,
        int elapsed,
        int castingTime,
        int variant,
        long escrowAltar,
        int escrowPower
    ) {
        private static final Codec<Session> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("center").forGetter(Session::center),
            Codec.STRING.fieldOf("ritual").forGetter(Session::ritual),
            Codec.STRING.fieldOf("caster").forGetter(Session::caster),
            Codec.INT.optionalFieldOf("elapsed", 0).forGetter(Session::elapsed),
            Codec.INT.fieldOf("casting_time").forGetter(Session::castingTime),
            Codec.INT.optionalFieldOf("variant", 0).forGetter(Session::variant),
            // Absent on sessions written before casts held their power in escrow. Those drained at activation,
            // so they owe nothing back and a zero promise is the correct reading of them.
            Codec.LONG.optionalFieldOf("escrow_altar", 0L).forGetter(Session::escrowAltar),
            Codec.INT.optionalFieldOf("escrow_power", 0).forGetter(Session::escrowPower)
        ).apply(instance, Session::new));
    }
}
