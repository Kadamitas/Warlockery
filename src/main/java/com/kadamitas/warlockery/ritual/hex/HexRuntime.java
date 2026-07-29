package com.kadamitas.warlockery.ritual.hex;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class HexRuntime {
    private static final List<HexKind.EffectSpec> MISFORTUNE_OUTCOMES = List.of(
        new HexKind.EffectSpec(MobEffects.SLOWNESS, 1),
        new HexKind.EffectSpec(MobEffects.WEAKNESS, 1),
        new HexKind.EffectSpec(MobEffects.HUNGER, 1),
        new HexKind.EffectSpec(MobEffects.MINING_FATIGUE, 1),
        new HexKind.EffectSpec(MobEffects.BLINDNESS, 0)
    );

    private HexRuntime() {
    }

    public static void tick(final EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target)
            || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        if (tickTemporaryEntity(level, target)) {
            return;
        }
        HexState.removeExpired(target).forEach(kind -> clearEffects(target, kind));
        HexState.active(target).forEach(active -> tickActiveHex(level, target, active.kind()));
    }

    public static void handleDrops(final LivingDropsEvent event) {
        if (HexEntityMarkers.isTemporary(event.getEntity())) {
            event.getDrops().clear();
        }
    }

    public static void apply(final LivingEntity target, final HexKind kind, final int durationTicks) {
        HexState.apply(target, kind, durationTicks);
        refreshMarkerEffects(target, kind);
    }

    public static void remove(final LivingEntity target, final HexKind kind) {
        HexState.remove(target, kind);
        clearEffects(target, kind);
    }

    public static void clearEffects(final LivingEntity target, final HexKind kind) {
        kind.markerEffects().forEach(effect -> target.removeEffect(effect.effect()));
        if (kind == HexKind.MISFORTUNE) {
            MISFORTUNE_OUTCOMES.forEach(effect -> target.removeEffect(effect.effect()));
        }
    }

    private static void tickActiveHex(
        final ServerLevel level,
        final LivingEntity target,
        final HexKind kind
    ) {
        if (target.tickCount % 20 == 0) {
            refreshMarkerEffects(target, kind);
        }
        switch (kind) {
            case MISFORTUNE -> tickMisfortune(level, target);
            case INSANITY -> tickHallucinations(level, target, kind, HallucinationRules.INSANITY, true);
            case OVERHEATING -> tickOverheating(level, target);
            case SINKING -> tickSinking(target);
            case WAKING_NIGHTMARE -> tickHallucinations(
                level,
                target,
                kind,
                HallucinationRules.WAKING_NIGHTMARE,
                !target.isSleeping()
            );
        }
    }

    private static void refreshMarkerEffects(final LivingEntity target, final HexKind kind) {
        kind.markerEffects().forEach(effect -> target.addEffect(
            new MobEffectInstance(effect.effect(), 60, effect.amplifier(), true, false)
        ));
    }

    private static void tickMisfortune(final ServerLevel level, final LivingEntity target) {
        if (!MisfortuneRules.shouldTrigger(target.tickCount)) {
            return;
        }
        final int index = MisfortuneRules.outcomeIndex(target.getUUID(), level.getGameTime(), MISFORTUNE_OUTCOMES.size());
        final HexKind.EffectSpec outcome = MISFORTUNE_OUTCOMES.get(index);
        target.addEffect(new MobEffectInstance(outcome.effect(), 140, outcome.amplifier(), true, false));
    }

    private static void tickOverheating(final ServerLevel level, final LivingEntity target) {
        if (target.tickCount % 20 != 0) {
            return;
        }
        final var biome = level.getBiome(target.blockPosition());
        if (!OverheatingRules.shouldBurn(
            biome.is(WarlockeryTags.Biomes.OVERHEATING),
            biome.value().getBaseTemperature(),
            target.isInWaterOrRain(),
            target.hasEffect(MobEffects.FIRE_RESISTANCE)
        )) {
            return;
        }
        target.igniteForSeconds(3.0F);
        target.hurtServer(level, target.damageSources().onFire(), 1.0F);
    }

    private static void tickSinking(final LivingEntity target) {
        if (!SinkingRules.shouldSink(target.getFluidHeight(WarlockeryTags.Fluids.SINKING_FLUIDS))) {
            return;
        }
        target.setSwimming(false);
        target.setSprinting(false);
        target.setDeltaMovement(SinkingRules.burden(target.getDeltaMovement()));
    }

    private static void tickHallucinations(
        final ServerLevel level,
        final LivingEntity target,
        final HexKind kind,
        final HallucinationRules.ThreatProfile profile,
        final boolean eligible
    ) {
        final int activeThreats = countThreats(level, target, kind);
        if (!HallucinationRules.shouldSpawn(profile, target.tickCount, activeThreats, eligible)) {
            return;
        }
        final TagKey<EntityType<?>> tag = kind == HexKind.INSANITY
            ? WarlockeryTags.EntityTypes.INSANITY_THREATS
            : WarlockeryTags.EntityTypes.WAKING_NIGHTMARE_THREATS;
        BuiltInRegistries.ENTITY_TYPE.getRandomElementOf(tag, level.getRandom())
            .map(holder -> holder.value().create(level, EntitySpawnReason.EVENT))
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .ifPresent(threat -> spawnThreat(level, target, threat, kind, profile));
    }

    private static int countThreats(final ServerLevel level, final LivingEntity target, final HexKind kind) {
        return level.getEntities(
            (Entity) null,
            target.getBoundingBox().inflate(24.0),
            entity -> HexEntityMarkers.threat(entity)
                .filter(marker -> marker.kind() == kind && marker.targetId().equals(target.getUUID()))
                .isPresent()
        ).size();
    }

    private static void spawnThreat(
        final ServerLevel level,
        final LivingEntity target,
        final LivingEntity threat,
        final HexKind kind,
        final HallucinationRules.ThreatProfile profile
    ) {
        final double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
        final double distance = 4.0 + level.getRandom().nextDouble() * 3.0;
        threat.snapTo(
            target.getX() + Math.cos(angle) * distance,
            target.getY() + 0.25,
            target.getZ() + Math.sin(angle) * distance
        );
        if (!level.isLoaded(threat.blockPosition()) || !level.noCollision(threat)) {
            threat.discard();
            return;
        }
        HexEntityMarkers.markThreat(
            threat,
            kind,
            target.getUUID(),
            level.getGameTime() + profile.lifetimeTicks()
        );
        if (threat instanceof Mob mob) {
            mob.setTarget(target);
        }
        level.addFreshEntity(threat);
    }

    private static boolean tickTemporaryEntity(final ServerLevel level, final LivingEntity entity) {
        final var threat = HexEntityMarkers.threat(entity);
        if (threat.isPresent()) {
            tickThreat(level, entity, threat.orElseThrow());
            return true;
        }
        final var toad = HexEntityMarkers.toad(entity);
        if (toad.isPresent()) {
            tickToad(level, entity, toad.orElseThrow());
            return true;
        }
        return false;
    }

    private static void tickThreat(
        final ServerLevel level,
        final LivingEntity entity,
        final HexEntityMarkers.ThreatMarker marker
    ) {
        final Entity resolved = level.getEntity(marker.targetId());
        if (level.getGameTime() >= marker.expiration()
            || !(resolved instanceof LivingEntity target)
            || !target.isAlive()) {
            entity.discard();
            return;
        }
        if (entity instanceof Mob mob) {
            mob.setTarget(target);
        }
    }

    private static void tickToad(
        final ServerLevel level,
        final LivingEntity toad,
        final HexEntityMarkers.ToadMarker marker
    ) {
        final long gameTime = level.getGameTime();
        if (gameTime >= marker.expiration()) {
            toad.discard();
            return;
        }
        if (marker.role() == ToadRainRules.ToadRole.EXPLOSIVE && gameTime >= marker.detonation()) {
            level.explode(
                toad,
                toad.getX(),
                toad.getY(),
                toad.getZ(),
                ToadRainRules.EXPLOSION_RADIUS,
                ToadRainRules.EXPLOSION_INTERACTION
            );
            toad.discard();
            return;
        }
        if (marker.role() == ToadRainRules.ToadRole.POISONOUS && toad.tickCount % 20 == 0) {
            level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(toad.blockPosition()).inflate(2.0),
                target -> target != toad && HexEntityMarkers.toad(target).isEmpty()
            ).forEach(target -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0)));
        }
    }
}
