package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

public final class ManifestationRuntime {
    private static final Identifier SPIRIT = Identifier.fromNamespaceAndPath("warlockery", "spirit");
    private static final String DREAMER = "WarlockeryManifestationDreamer";
    private static final String EXPIRATION = "WarlockeryManifestationExpiration";

    private ManifestationRuntime() {
    }

    public static ManifestationRules.Decision diagnose(final ServerPlayer target) {
        return ManifestationRules.decide(
            true,
            target.isSleeping(),
            findManifestation(target).isPresent()
        );
    }

    public static boolean manifest(
        final ServerLevel level,
        final BlockPos center,
        final ServerPlayer dreamer,
        final int duration
    ) {
        if (!diagnose(dreamer).ready()) {
            return false;
        }
        return BuiltInRegistries.ENTITY_TYPE.get(SPIRIT)
            .map(holder -> holder.value().create(level, EntitySpawnReason.EVENT))
            .filter(Mob.class::isInstance)
            .map(Mob.class::cast)
            .filter(spirit -> {
                spirit.snapTo(center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5);
                return level.noCollision(spirit);
            })
            .map(spirit -> {
                CreatureBehaviorState.bind(spirit, dreamer.getUUID());
                spirit.setPersistenceRequired();
                spirit.setCustomName(Component.translatable("entity.warlockery.manifested_spirit", dreamer.getDisplayName()));
                spirit.getPersistentData().putString(DREAMER, dreamer.getStringUUID());
                spirit.getPersistentData().putLong(EXPIRATION, level.getGameTime() + Math.max(20, duration));
                return level.addFreshEntity(spirit);
            })
            .orElse(false);
    }

    public static void tick(final ServerLevel level) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        level.getEntitiesOfClass(
            Mob.class,
            new AABB(-30_000_000, level.getMinY(), -30_000_000, 30_000_000, level.getMaxY(), 30_000_000),
            mob -> mob.getPersistentData().contains(DREAMER)
        ).forEach(spirit -> {
            final ServerPlayer dreamer = parse(spirit.getPersistentData().getStringOr(DREAMER, ""))
                .map(level.getServer().getPlayerList()::getPlayer)
                .orElse(null);
            final boolean expired = level.getGameTime() >= spirit.getPersistentData().getLongOr(EXPIRATION, 0L);
            if (expired || dreamer == null || !dreamer.isSleeping()) {
                spirit.discard();
            }
        });
    }

    private static Optional<Mob> findManifestation(final ServerPlayer dreamer) {
        return StreamSupport.stream(dreamer.level().getServer().getAllLevels().spliterator(), false)
            .flatMap(level -> level.getEntitiesOfClass(
                Mob.class,
                new AABB(-30_000_000, level.getMinY(), -30_000_000, 30_000_000, level.getMaxY(), 30_000_000),
                mob -> dreamer.getStringUUID().equals(mob.getPersistentData().getStringOr(DREAMER, ""))
            ).stream())
            .findFirst();
    }

    private static Optional<UUID> parse(final String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
