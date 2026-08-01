package com.kadamitas.warlockery.data;

import com.kadamitas.warlockery.Warlockery;
import java.util.Objects;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

public final class WarlockeryEntityData {
    private static final AttachmentType<CompoundTag> DATA = AttachmentRegistry.create(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "entity_data"),
        builder -> builder.initializer(CompoundTag::new).persistent(CompoundTag.CODEC)
    );

    private WarlockeryEntityData() {
    }

    public static void initialize() {
    }

    public static AttachmentType<CompoundTag> type() {
        return DATA;
    }

    public static CompoundTag get(final Entity entity) {
        return target(entity).getAttachedOrCreate(DATA);
    }

    public static void replace(final Entity entity, final CompoundTag data) {
        target(entity).setAttached(DATA, Objects.requireNonNull(data, "data").copy());
    }

    public static void copy(final Entity source, final Entity destination) {
        final CompoundTag data = target(source).getAttached(DATA);
        if (data == null) {
            target(destination).removeAttached(DATA);
            return;
        }
        target(destination).setAttached(DATA, data.copy());
    }

    public static void clear(final Entity entity) {
        target(entity).removeAttached(DATA);
    }

    private static AttachmentTarget target(final Entity entity) {
        return (AttachmentTarget) Objects.requireNonNull(entity, "entity");
    }
}
