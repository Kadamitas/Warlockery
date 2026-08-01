package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.fabric.event.LivingDropsContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

public final class PriorIncarnationRuntime {
    private static final String OWNER = "WarlockeryPriorOwner";
    private static final String DEATH_TIME = "WarlockeryPriorDeathTime";

    private PriorIncarnationRuntime() {
    }

    public static void handleDrops(final LivingDropsContext event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || !(player.level() instanceof ServerLevel level)
            || event.getDrops().isEmpty()) {
            return;
        }
        final long deathTime = level.getGameTime();
        event.getDrops().forEach(drop -> {
            WarlockeryEntityData.get(drop).putString(OWNER, player.getUUID().toString());
            WarlockeryEntityData.get(drop).putLong(DEATH_TIME, deathTime);
            drop.setExtendedLifetime();
        });
        PriorIncarnationData.get(level).record(
            player.getUUID(),
            level.dimension().identifier(),
            player.blockPosition(),
            deathTime
        );
    }

    public static int countRecoverable(final ServerLevel destination, final UUID player) {
        return recordAndDrops(destination, player, Optional.empty(), Integer.MAX_VALUE)
            .map(RecordAndDrops::drops).map(List::size).orElse(0);
    }

    public static int countRecoverable(
        final ServerLevel destination,
        final BlockPos center,
        final UUID player,
        final int range
    ) {
        return recordAndDrops(destination, player, Optional.of(center), range)
            .map(RecordAndDrops::drops).map(List::size).orElse(0);
    }

    public static RecoveryReport recover(
        final ServerLevel destination,
        final BlockPos center,
        final UUID player
    ) {
        final Optional<RecordAndDrops> resolved = recordAndDrops(
            destination,
            player,
            Optional.empty(),
            Integer.MAX_VALUE
        );
        return recover(destination, center, player, resolved);
    }

    public static RecoveryReport recover(
        final ServerLevel destination,
        final BlockPos center,
        final UUID player,
        final int range
    ) {
        return recover(destination, center, player, recordAndDrops(destination, player, Optional.of(center), range));
    }

    private static RecoveryReport recover(
        final ServerLevel destination,
        final BlockPos center,
        final UUID player,
        final Optional<RecordAndDrops> resolved
    ) {
        if (resolved.isEmpty()) {
            return RecoveryReport.EMPTY;
        }
        final RecordAndDrops recovery = resolved.orElseThrow();
        int stacks = 0;
        int items = 0;
        for (ItemEntity drop : recovery.drops()) {
            final var stack = drop.getItem().copy();
            final ItemEntity restored = new ItemEntity(
                destination,
                center.getX() + 0.5,
                center.getY() + 1.0,
                center.getZ() + 0.5,
                stack
            );
            restored.setDefaultPickUpDelay();
            if (destination.addFreshEntity(restored)) {
                stacks++;
                items += stack.getCount();
                drop.discard();
            }
        }
        if (stacks > 0) {
            PriorIncarnationData.get(destination).clear(player);
        }
        return new RecoveryReport(stacks, items);
    }

    private static Optional<RecordAndDrops> recordAndDrops(
        final ServerLevel destination,
        final UUID player,
        final Optional<BlockPos> requiredCenter,
        final int range
    ) {
        final Optional<PriorIncarnationData.DeathRecord> record = PriorIncarnationData.get(destination).find(player);
        if (record.isEmpty()) {
            return Optional.empty();
        }
        final PriorIncarnationData.DeathRecord death = record.orElseThrow();
        final Identifier dimension = Identifier.tryParse(death.dimension());
        if (dimension == null) {
            return Optional.empty();
        }
        final BlockPos deathPos = BlockPos.of(death.position());
        if (requiredCenter.isPresent()
            && (!dimension.equals(destination.dimension().identifier())
                || deathPos.distSqr(requiredCenter.orElseThrow()) > (double) range * range)) {
            return Optional.empty();
        }
        final ServerLevel source = destination.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (source == null) {
            return Optional.empty();
        }
        source.getChunkAt(deathPos);
        final List<ItemEntity> drops = source.getEntitiesOfClass(
            ItemEntity.class,
            new AABB(deathPos).inflate(32.0),
            drop -> matches(drop, player, death.deathTime())
        );
        return Optional.of(new RecordAndDrops(death, drops));
    }

    private static boolean matches(final Entity entity, final UUID player, final long deathTime) {
        final var data = WarlockeryEntityData.get(entity);
        return data.getStringOr(OWNER, "").equals(player.toString())
            && data.getLongOr(DEATH_TIME, Long.MIN_VALUE) == deathTime;
    }

    private record RecordAndDrops(PriorIncarnationData.DeathRecord record, List<ItemEntity> drops) {
        private RecordAndDrops {
            drops = List.copyOf(drops);
        }
    }

    public record RecoveryReport(int stacks, int items) {
        public static final RecoveryReport EMPTY = new RecoveryReport(0, 0);

        public RecoveryReport {
            if (stacks < 0 || items < 0) {
                throw new IllegalArgumentException("Recovery counts must be nonnegative");
            }
        }

        public boolean recoveredAnything() {
            return stacks > 0 && items > 0;
        }
    }
}
