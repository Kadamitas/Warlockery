package com.kadamitas.warlockery.item;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public record SympatheticBinding(UUID targetId, String targetName, String targetType) {
    private static final String TARGET_UUID = "WarlockeryTargetUuid";
    private static final String TARGET_NAME = "WarlockeryTargetName";
    private static final String TARGET_TYPE = "WarlockeryTargetType";

    public SympatheticBinding {
        Objects.requireNonNull(targetId, "targetId");
        targetName = targetName == null || targetName.isBlank() ? "?" : targetName.strip();
        targetType = targetType == null ? "" : targetType.strip();
    }

    public static SympatheticBinding from(final LivingEntity target) {
        return new SympatheticBinding(
            target.getUUID(),
            target.getName().getString(),
            BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString()
        );
    }

    public static Optional<SympatheticBinding> read(final ItemStack stack) {
        final var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        final String id = tag.getStringOr(TARGET_UUID, "");
        if (id.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new SympatheticBinding(
                UUID.fromString(id),
                tag.getStringOr(TARGET_NAME, "?"),
                tag.getStringOr(TARGET_TYPE, "")
            ));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public void write(final ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
            data.putString(TARGET_UUID, targetId.toString());
            data.putString(TARGET_NAME, targetName);
            if (!targetType.isBlank()) {
                data.putString(TARGET_TYPE, targetType);
            }
        });
    }

    public boolean targets(final LivingEntity entity) {
        return targetId.equals(entity.getUUID()) && matchesType(entity);
    }

    public Optional<LivingEntity> resolve(final MinecraftServer server) {
        final ServerPlayer player = server.getPlayerList().getPlayer(targetId);
        if (player != null && matchesType(player)) {
            return Optional.of(player);
        }
        return StreamSupport.stream(server.getAllLevels().spliterator(), false)
            .map(level -> level.getEntity(targetId))
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .filter(this::targets)
            .findFirst();
    }

    private boolean matchesType(final LivingEntity entity) {
        return targetType.isBlank()
            || targetType.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }
}
