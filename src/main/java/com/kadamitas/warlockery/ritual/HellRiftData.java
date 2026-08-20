package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.crafting.AltarPowerNetwork;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class HellRiftData extends SavedData {
    private static final Codec<HellRiftData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Rift.CODEC.listOf().optionalFieldOf("rifts", List.of()).forGetter(data -> data.rifts)
    ).apply(instance, HellRiftData::new));
    public static final SavedDataType<HellRiftData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "hell_rifts"),
        HellRiftData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final List<Rift> rifts;

    public HellRiftData() {
        rifts = new ArrayList<>();
    }

    private HellRiftData(final List<Rift> rifts) {
        this.rifts = new ArrayList<>(rifts);
    }

    public static HellRiftData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void open(
        final ServerLevel level,
        final BlockPos center,
        final BlockPos powerCenter,
        final int radius,
        final int duration
    ) {
        rifts.removeIf(rift -> rift.center() == center.asLong());
        final long now = level.getGameTime();
        rifts.add(new Rift(
            center.asLong(),
            powerCenter.asLong(),
            Math.clamp(radius, 2, 24),
            now + Math.max(20, duration),
            now
        ));
        spawnDemon(level, center, radius);
        setDirty();
    }

    public void tick(final ServerLevel level) {
        final long now = level.getGameTime();
        boolean changed = false;
        for (int index = rifts.size() - 1; index >= 0; index--) {
            Rift rift = rifts.get(index);
            final BlockPos center = BlockPos.of(rift.center());
            final BlockPos powerCenter = BlockPos.of(rift.powerCenter());
            if (!HellRiftRules.active(now, rift.expiration())
                || HellRiftRules.drainsPower(now)
                    && !AltarPowerNetwork.consume(level, powerCenter, HellRiftRules.POWER_PER_SECOND)) {
                rifts.remove(index);
                changed = true;
                continue;
            }
            if (now >= rift.nextSpawn()) {
                spawnDemon(level, center, rift.radius());
                rift = new Rift(
                    rift.center(),
                    rift.powerCenter(),
                    rift.radius(),
                    rift.expiration(),
                    HellRiftRules.nextSpawn(now)
                );
                rifts.set(index, rift);
                changed = true;
            }
            if (now % 10L == 0L) {
                level.sendParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    center.getX() + 0.5,
                    center.getY() + 0.4,
                    center.getZ() + 0.5,
                    8,
                    rift.radius() * 0.4,
                    0.5,
                    rift.radius() * 0.4,
                    0.01
                );
            }
        }
        if (changed) {
            setDirty();
        }
    }

    private static void spawnDemon(final ServerLevel level, final BlockPos center, final int radius) {
        BuiltInRegistries.ENTITY_TYPE.getRandomElementOf(WarlockeryTags.EntityTypes.DEMONS, level.getRandom())
            .map(holder -> holder.value().create(level, EntitySpawnReason.TRIGGERED))
            .filter(java.util.Objects::nonNull)
            .ifPresent(entity -> {
                final double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
                final double distance = Math.max(2.0, radius * 0.6);
                final int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
                final int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
                final int y = Math.max(center.getY() + 1, level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    x,
                    z
                ));
                entity.snapTo(x + 0.5, y, z + 0.5);
                level.addFreshEntity(entity);
            });
    }

    public record Rift(long center, long powerCenter, int radius, long expiration, long nextSpawn) {
        private static final Codec<Rift> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("center").forGetter(Rift::center),
            Codec.LONG.optionalFieldOf("power_center", Long.MIN_VALUE).forGetter(rift ->
                rift.powerCenter() == rift.center() ? Long.MIN_VALUE : rift.powerCenter()
            ),
            Codec.intRange(2, 24).fieldOf("radius").forGetter(Rift::radius),
            Codec.LONG.fieldOf("expiration").forGetter(Rift::expiration),
            Codec.LONG.fieldOf("next_spawn").forGetter(Rift::nextSpawn)
        ).apply(instance, (center, powerCenter, radius, expiration, nextSpawn) -> new Rift(
            center,
            powerCenter == Long.MIN_VALUE ? center : powerCenter,
            radius,
            expiration,
            nextSpawn
        )));
    }
}
