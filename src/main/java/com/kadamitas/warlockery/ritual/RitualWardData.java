package com.kadamitas.warlockery.ritual;

import com.kadamitas.warlockery.Warlockery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public final class RitualWardData extends SavedData {
    private static final Codec<RitualWardData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Ward.CODEC.listOf().optionalFieldOf("wards", List.of()).forGetter(data -> data.wards)
    ).apply(instance, RitualWardData::new));

    public static final SavedDataType<RitualWardData> TYPE = new SavedDataType<>(
        Identifier.parse(Warlockery.MOD_ID + ":ritual_wards"),
        RitualWardData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final List<Ward> wards;

    public RitualWardData() {
        wards = new ArrayList<>();
    }

    private RitualWardData(final List<Ward> wards) {
        this.wards = new ArrayList<>(wards);
    }

    public static RitualWardData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void place(
        final ServerLevel level,
        final RitualWardType type,
        final BlockPos center,
        final int radius,
        final long expiration
    ) {
        wards.removeIf(ward -> ward.center() == center.asLong() && ward.type() == type);
        final Ward ward = new Ward(type, center.asLong(), Math.clamp(radius, 1, 32), expiration);
        wards.add(ward);
        setDirty();
        tickWard(level, ward);
    }

    public boolean contains(final RitualWardType type, final Vec3 position, final long gameTime) {
        return wards.stream().filter(ward -> ward.type() == type && ward.expiration() > gameTime)
            .anyMatch(ward -> RitualWardRules.contains(Vec3.atCenterOf(BlockPos.of(ward.center())), ward.radius(), position));
    }

    public void tick(final ServerLevel level) {
        final boolean removed = wards.removeIf(ward -> ward.expiration() <= level.getGameTime());
        if (removed) {
            setDirty();
        }
        if (level.getGameTime() % 2 != 0) {
            return;
        }
        wards.forEach(ward -> {
            tickWard(level, ward);
            if (level.getGameTime() % 20 == 0) {
                particles(level, ward);
            }
        });
    }

    private static void tickWard(final ServerLevel level, final Ward ward) {
        switch (ward.type()) {
            case IMPRISONMENT -> tickImprisonment(level, ward);
            case SANCTITY -> tickSanctity(level, ward);
            case PROTECTION -> {
            }
        }
    }

    public static void handleDamage(final LivingDamageEvent.Pre event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
            || event.getNewDamage() <= 0.0F
            || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (get(level).contains(RitualWardType.PROTECTION, event.getEntity().position(), level.getGameTime())) {
            event.setNewDamage(0.0F);
            level.sendParticles(
                ParticleTypes.ENCHANT,
                event.getEntity().getX(),
                event.getEntity().getY() + 1.0,
                event.getEntity().getZ(),
                8,
                0.4,
                0.6,
                0.4,
                0.02
            );
        }
    }

    private static void tickImprisonment(final ServerLevel level, final Ward ward) {
        final Vec3 center = Vec3.atCenterOf(BlockPos.of(ward.center()));
        final AABB area = AABB.ofSize(center, ward.radius() * 2.0 + 4.0, ward.radius() * 2.0 + 4.0, ward.radius() * 2.0 + 4.0);
        level.getEntitiesOfClass(LivingEntity.class, area, entity -> RitualWardRules.shouldRepel(
            true,
            entity.isAlive(),
            entity.getType().getCategory() == MobCategory.MONSTER
                || entity.typeHolder().is(RitualCompatibilityTags.IMPRISONABLE),
            entity.typeHolder().is(RitualCompatibilityTags.WARD_IMMUNE)
        )).forEach(entity -> {
            final double distance = entity.position().distanceTo(center);
            if (distance >= ward.radius() - 1.0) {
                entity.setDeltaMovement(RitualWardRules.inwardVelocity(center, entity.position(), entity.getDeltaMovement()));
                entity.hurtMarked = true;
            }
            entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 4, true, true));
        });
    }

    private static void tickSanctity(final ServerLevel level, final Ward ward) {
        final Vec3 center = Vec3.atCenterOf(BlockPos.of(ward.center()));
        final AABB area = AABB.ofSize(center, ward.radius() * 2.0, ward.radius() * 2.0, ward.radius() * 2.0);
        level.getEntitiesOfClass(Mob.class, area, mob -> RitualWardRules.shouldRepel(
            true,
            mob.isAlive(),
            mob.getType().getCategory() == MobCategory.MONSTER
                || mob.typeHolder().is(RitualCompatibilityTags.SANCTITY_REPELLED),
            mob.typeHolder().is(RitualCompatibilityTags.WARD_IMMUNE)
        )).forEach(mob -> {
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.setDeltaMovement(RitualWardRules.outwardVelocity(center, mob.position(), mob.getDeltaMovement()));
            mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 2, true, true));
            mob.hurtMarked = true;
        });
    }

    private static void particles(final ServerLevel level, final Ward ward) {
        final BlockPos center = BlockPos.of(ward.center());
        for (int index = 0; index < 12; index++) {
            final double angle = Math.PI * 2.0 * index / 12.0;
            level.sendParticles(
                ParticleTypes.ENCHANT,
                center.getX() + 0.5 + Math.cos(angle) * ward.radius(),
                center.getY() + 0.5,
                center.getZ() + 0.5 + Math.sin(angle) * ward.radius(),
                1,
                0.0,
                0.2,
                0.0,
                0.0
            );
        }
    }

    public record Ward(RitualWardType type, long center, int radius, long expiration) {
        private static final Codec<Ward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RitualWardType.CODEC.fieldOf("type").forGetter(Ward::type),
            Codec.LONG.fieldOf("center").forGetter(Ward::center),
            Codec.intRange(1, 32).fieldOf("radius").forGetter(Ward::radius),
            Codec.LONG.fieldOf("expiration").forGetter(Ward::expiration)
        ).apply(instance, Ward::new));
    }
}
