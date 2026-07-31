package com.kadamitas.warlockery.brew;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.brew.custom.CustomBrewCloudRuntime;
import com.kadamitas.warlockery.brew.custom.CustomBrewTriggerData;
import com.kadamitas.warlockery.magic.MagicPathState;
import com.kadamitas.warlockery.dream.SpiritWorldRuntime;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.ritual.hex.OverheatingRules;
import com.kadamitas.warlockery.ritual.hex.SinkingRules;
import com.kadamitas.warlockery.transformation.SupernaturalState;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TickEvent;

public final class BrewPersistentRuntime {
    private static final Identifier RESIZING_MODIFIER_ID = Identifier.fromNamespaceAndPath(
        Warlockery.MOD_ID, "brew_resizing"
    );
    private static final AttributeModifier RESIZING_MODIFIER = new AttributeModifier(
        RESIZING_MODIFIER_ID,
        -0.5,
        AttributeModifier.Operation.ADD_MULTIPLIED_BASE
    );
    private static final ThreadLocal<Boolean> REFLECTING_DAMAGE = ThreadLocal.withInitial(() -> false);
    private static boolean registered;

    private BrewPersistentRuntime() {
    }

    public static synchronized void registerEvents() {
        if (registered) {
            return;
        }
        registered = true;
        LivingDamageEvent.BUS.addListener(BrewPersistentRuntime::handleDamage);
        LivingEvent.LivingTickEvent.BUS.addListener(BrewPersistentRuntime::tick);
        LivingDropsEvent.BUS.addListener(BrewPersistentRuntime::handleDrops);
        LivingDeathEvent.BUS.addListener(BrewPersistentRuntime::handleDeath);
        ProjectileImpactEvent.BUS.addListener(BrewPersistentRuntime::handleProjectileImpact);
        PlayerEvent.Clone.BUS.addListener(BrewPersistentRuntime::handleClone);
        PlayerInteractEvent.RightClickBlock.BUS.addListener(CustomBrewTriggerData::handleBlockUse);
        TickEvent.LevelTickEvent.Post.BUS.addListener(event -> {
            if (event.level() instanceof ServerLevel level) {
                BrewWorldData.get(level).tick(level);
                CustomBrewTriggerData.get(level).tick(level);
            }
        });
        EntityTeleportEvent.EnderEntity.BUS.addListener(
            (Predicate<EntityTeleportEvent.EnderEntity>) BrewPersistentRuntime::cancelTeleport
        );
        EntityTeleportEvent.EnderPearl.BUS.addListener(
            (Predicate<EntityTeleportEvent.EnderPearl>) BrewPersistentRuntime::cancelTeleport
        );
        EntityTeleportEvent.ChorusFruit.BUS.addListener(
            (Predicate<EntityTeleportEvent.ChorusFruit>) BrewPersistentRuntime::cancelTeleport
        );
    }

    public static void tick(final LivingEvent.LivingTickEvent event) {
        final LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        BrewMarkerState.removeExpired(target);
        CustomBrewCloudRuntime.tick(level, target);
        tickResizing(target);
        if (BrewMarkerState.isActive(target, BrewMarkerKind.SINKING)) {
            tickSinking(target);
        }
        if (BrewMarkerState.isActive(target, BrewMarkerKind.ATTRACT_ARROWS) && target.tickCount % 2 == 0) {
            tickArrowAttraction(level, target);
        }
        if (BrewMarkerState.isActive(target, BrewMarkerKind.CURSED_LEAPING)) {
            tickCursedLeaping(target);
        }
        if (target.tickCount % 5 == 0) {
            tickFear(target);
            tickGrotesque(level, target);
        }
        if (target.tickCount % 20 != 0) {
            return;
        }
        tickAbsorbedMagic(target);
        tickErosion(target);
        tickGasImmunity(level, target);
        tickIllFitting(target);
        tickGruesPrey(level, target);
        tickOverheating(level, target);
        tickSleeping(level, target);
        tickSnowTrail(level, target);
        tickDepths(level, target);
        tickTint(level, target);
        tickWerewolfLock(target);
        tickSunlightCurse(level, target);
        tickMoonshine(target);
        if (target.tickCount % 40 == 0) {
            tickContagion(level, target, BrewMarkerKind.DISEASE);
            tickContagion(level, target, BrewMarkerKind.INFECTION);
        }
    }

    public static void handleDamage(final LivingDamageEvent event) {
        final LivingEntity target = event.getEntity();
        if (BrewMarkerState.isActive(target, BrewMarkerKind.MOONSHINE)) {
            event.setAmount(BrewMarkerRules.moonshineDamage(event.getAmount()));
        }
        final Entity source = event.getSource().getEntity();
        if (source instanceof LivingEntity attacker && attacker != target && event.getAmount() > 0.0F) {
            if (BrewMarkerState.isActive(attacker, BrewMarkerKind.POISON_WEAPON)) {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 160, 1));
            }
            if (BrewMarkerState.isActive(target, BrewMarkerKind.REPEL_ATTACKER)) {
                attacker.addDeltaMovement(BrewPhysics.radialVelocity(
                    target.position(), attacker.position(), 1.25, false
                ));
                attacker.hurtMarked = true;
            }
            reflectDamage(target, attacker, event.getAmount());
        }
        if (event.getAmount() > 0.0F
            && BrewMarkerState.isActive(target, BrewMarkerKind.ABSORB_MAGIC)
            && event.getSource().is(BrewCompatibilityTags.DamageTypes.MAGICAL)) {
            final float absorbed = BrewMarkerRules.absorbedDamage(event.getAmount());
            BrewMarkerState.addAbsorbedMagic(target, absorbed);
            event.setAmount(event.getAmount() - absorbed);
        }
        if (event.getAmount() > 0.0F && BrewMarkerState.isActive(target, BrewMarkerKind.VOLATILITY)) {
            BrewMarkerState.remove(target, BrewMarkerKind.VOLATILITY);
            if (target.level() instanceof ServerLevel level) {
                level.explode(
                    target,
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    2.5F,
                    false,
                    Level.ExplosionInteraction.NONE
                );
            }
        }
    }

    public static void handleProjectileImpact(final ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)
            || !(event.getRayTraceResult() instanceof EntityHitResult hit)
            || !(hit.getEntity() instanceof LivingEntity target)
            || !BrewMarkerState.isActive(target, BrewMarkerKind.REFLECT_ARROWS)
            || !(arrow.level() instanceof ServerLevel)) {
            return;
        }
        Vec3 reflected = arrow.getDeltaMovement().scale(-1.1);
        if (reflected.lengthSqr() < 1.0E-6) {
            reflected = target.getLookAngle().scale(0.8);
        }
        arrow.setOwner(target);
        arrow.setDeltaMovement(reflected);
        arrow.setPos(hit.getLocation().add(reflected.normalize().scale(0.2)));
        arrow.needsSync = true;
        event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
    }

    public static boolean cancelTeleport(final EntityTeleportEvent event) {
        return event.getEntity() instanceof LivingEntity target
            && BrewMarkerState.isActive(target, BrewMarkerKind.ENDER_INHIBITION);
    }

    public static void handleDeath(final LivingDeathEvent event) {
        final LivingEntity target = event.getEntity();
        if (BrewMarkerState.isActive(target, BrewMarkerKind.KEEP_EFFECTS)) {
            BrewMarkerState.storeEffects(target, List.copyOf(target.getActiveEffects()));
        }
        if (target instanceof Animal animal
            && BrewMarkerState.isActive(animal, BrewMarkerKind.REINCARNATE)
            && animal.level() instanceof ServerLevel level) {
            reincarnate(level, animal);
        }
    }

    public static void handleDrops(final LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player)
            || !BrewMarkerState.isActive(player, BrewMarkerKind.KEEP_INVENTORY)
            || event.getDrops().isEmpty()) {
            return;
        }
        BrewMarkerState.storeItems(
            player,
            event.getDrops().stream().map(ItemEntity::getItem).map(ItemStack::copy).toList()
        );
        event.getDrops().clear();
    }

    public static void handleClone(final PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            BrewMarkerState.copyActive(event.getOriginal(), event.getEntity());
            return;
        }
        BrewMarkerState.savedEffects(event.getOriginal()).stream()
            .map(MobEffectInstance::new)
            .forEach(event.getEntity()::addEffect);
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        BrewMarkerState.savedItems(event.getOriginal()).stream().map(ItemStack::copy).forEach(stack -> {
            event.getEntity().getInventory().add(stack);
            if (!stack.isEmpty()) {
                event.getEntity().spawnAtLocation(level, stack, 0.25F);
            }
        });
    }

    private static void tickResizing(final LivingEntity target) {
        final var scale = target.getAttribute(Attributes.SCALE);
        if (scale == null) {
            return;
        }
        if (BrewMarkerState.isActive(target, BrewMarkerKind.RESIZING)) {
            scale.addOrUpdateTransientModifier(RESIZING_MODIFIER);
        } else {
            scale.removeModifier(RESIZING_MODIFIER_ID);
        }
    }

    private static void tickSinking(final LivingEntity target) {
        if (!SinkingRules.shouldSink(target.getFluidHeight(WarlockeryTags.Fluids.SINKING_FLUIDS))) {
            return;
        }
        target.setSwimming(false);
        target.setSprinting(false);
        target.setDeltaMovement(SinkingRules.burden(target.getDeltaMovement()));
        target.hurtMarked = true;
    }

    private static void tickArrowAttraction(final ServerLevel level, final LivingEntity target) {
        final AABB area = target.getBoundingBox().inflate(8.0, 6.0, 8.0);
        level.getEntitiesOfClass(
            AbstractArrow.class,
            area,
            arrow -> arrow.isAlive() && arrow.getOwner() != target
        ).stream().limit(24).forEach(arrow -> {
            final Vec3 toTarget = target.getEyePosition().subtract(arrow.position());
            if (toTarget.lengthSqr() < 1.0E-6) {
                return;
            }
            final double speed = Math.clamp(arrow.getDeltaMovement().length(), 0.4, 3.0);
            arrow.setDeltaMovement(arrow.getDeltaMovement().lerp(toTarget.normalize().scale(speed), 0.18));
            arrow.needsSync = true;
        });
    }

    private static void tickGasImmunity(final ServerLevel level, final LivingEntity target) {
        if (!BrewMarkerState.isActive(target, BrewMarkerKind.BREW_GAS_IMMUNITY)) {
            return;
        }
        final BlockPos feet = target.blockPosition();
        final BlockPos eyes = BlockPos.containing(target.getEyePosition());
        if (!level.getBlockState(feet).is(BrewCompatibilityTags.Blocks.GASES)
            && !level.getBlockState(eyes).is(BrewCompatibilityTags.Blocks.GASES)) {
            return;
        }
        List.copyOf(target.getActiveEffects()).stream()
            .filter(effect -> !effect.getEffect().value().isBeneficial())
            .forEach(effect -> target.removeEffect(effect.getEffect()));
    }

    private static void tickMoonshine(final LivingEntity target) {
        if (target instanceof Player player && BrewMarkerState.isActive(target, BrewMarkerKind.MOONSHINE)) {
            player.causeFoodExhaustion(BrewMarkerRules.moonshineExhaustion());
        }
    }

    private static void tickIllFitting(final LivingEntity target) {
        if (!BrewMarkerState.isActive(target, BrewMarkerKind.ILL_FITTING)) {
            return;
        }
        final int armorPieces = (int) EquipmentSlot.VALUES.stream()
            .filter(EquipmentSlot::isArmor)
            .map(target::getItemBySlot)
            .filter(stack -> !stack.isEmpty())
            .count();
        if (armorPieces == 0) {
            return;
        }
        final int amplifier = Math.min(3, armorPieces - 1);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, amplifier, true, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 40, amplifier, true, false, true));
    }

    private static void tickAbsorbedMagic(final LivingEntity target) {
        if (!(target instanceof Player player)
            || !BrewMarkerState.isActive(target, BrewMarkerKind.ABSORB_MAGIC)) {
            return;
        }
        MagicPathState.selected(player).ifPresent(path -> {
            final int missing = path.maximumReserve() - MagicPathState.reserve(player, path);
            final int transferred = BrewMarkerState.consumeAbsorbedMagic(player, Math.min(4, missing));
            if (transferred > 0) {
                MagicPathState.recharge(player, path, transferred);
            }
        });
    }

    private static void tickErosion(final LivingEntity target) {
        if (!BrewMarkerState.isActive(target, BrewMarkerKind.EROSION)) {
            return;
        }
        EquipmentSlot.VALUES.stream()
            .map(slot -> java.util.Map.entry(slot, target.getItemBySlot(slot)))
            .filter(entry -> entry.getValue().isDamageableItem())
            .forEach(entry -> entry.getValue().hurtAndBreak(1, target, entry.getKey()));
    }

    private static void tickCursedLeaping(final LivingEntity target) {
        if (!target.onGround() || Math.floorMod(target.tickCount + target.getId(), 40) != 0) {
            return;
        }
        final double direction = Math.floorMod(target.getId() * 37 + target.tickCount, 360) * Math.PI / 180.0;
        target.addDeltaMovement(new Vec3(Math.cos(direction) * 0.25, 1.05, Math.sin(direction) * 0.25));
        target.hurtMarked = true;
    }

    private static void tickFear(final LivingEntity target) {
        if (!(target instanceof Mob mob) || !BrewMarkerState.isActive(target, BrewMarkerKind.FEAR)) {
            return;
        }
        BrewMarkerState.origin(target, BrewMarkerKind.FEAR)
            .map(Vec3::atCenterOf)
            .ifPresent(origin -> flee(mob, origin, 1.35));
    }

    private static void tickGrotesque(final ServerLevel level, final LivingEntity subject) {
        if (!BrewMarkerState.isActive(subject, BrewMarkerKind.GROTESQUE)) {
            return;
        }
        level.getEntitiesOfClass(
            Mob.class,
            subject.getBoundingBox().inflate(10.0),
            mob -> mob != subject && mob.isAlive()
                && !mob.typeHolder().is(BrewCompatibilityTags.EntityTypes.GROTESQUE_IMMUNE)
        ).stream().limit(48).forEach(mob -> flee(mob, subject.position(), 1.25));
    }

    private static void flee(final Mob mob, final Vec3 threat, final double speed) {
        Vec3 direction = mob.position().subtract(threat);
        if (direction.horizontalDistanceSqr() < 1.0E-6) {
            direction = mob.getLookAngle().reverse();
        }
        final Vec3 destination = mob.position().add(direction.normalize().scale(10.0));
        mob.setTarget(null);
        mob.getNavigation().moveTo(destination.x, mob.getY(), destination.z, speed);
    }

    private static void tickGruesPrey(final ServerLevel level, final LivingEntity target) {
        if (!BrewMarkerState.isActive(target, BrewMarkerKind.GRUES_PREY)
            || !BrewRules.isDarkEnoughForGrue(level.getMaxLocalRawBrightness(target.blockPosition()))) {
            return;
        }
        target.hurtServer(level, target.damageSources().magic(), 2.0F);
        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, true, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0, true, true, true));
    }

    private static void tickOverheating(final ServerLevel level, final LivingEntity target) {
        if (!BrewMarkerState.isActive(target, BrewMarkerKind.OVERHEATING)) {
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

    private static void tickSleeping(final ServerLevel level, final LivingEntity target) {
        if (!BrewMarkerState.isActive(target, BrewMarkerKind.SLEEPING)) {
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, true, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, true, false, true));
        if (target instanceof ServerPlayer player && !SpiritWorldRuntime.isDreaming(player)) {
            SpiritWorldRuntime.enterFromSleepingBrew(player);
        }
    }

    private static void tickSnowTrail(final ServerLevel level, final LivingEntity target) {
        if (!BrewMarkerState.isActive(target, BrewMarkerKind.SNOW_TRAIL)) {
            return;
        }
        final BlockPos center = target.blockPosition();
        BlockPos.betweenClosedStream(center.offset(-1, -1, -1), center.offset(1, 0, 1))
            .filter(level::isLoaded)
            .filter(pos -> level.getBlockState(pos).canBeReplaced())
            .filter(pos -> Blocks.SNOW.defaultBlockState().canSurvive(level, pos))
            .limit(9)
            .forEach(pos -> level.setBlockAndUpdate(pos, Blocks.SNOW.defaultBlockState()));
    }

    private static void tickDepths(final ServerLevel level, final LivingEntity target) {
        if (!BrewMarkerState.isActive(target, BrewMarkerKind.DEPTHS)) {
            return;
        }
        if (target.getFluidHeight(FluidTags.WATER) > target.getBbHeight() * 0.75) {
            target.setAirSupply(target.getMaxAirSupply());
            return;
        }
        target.setAirSupply(target.getAirSupply() - 40);
        if (target.getAirSupply() <= -20) {
            target.setAirSupply(0);
            target.hurtServer(level, target.damageSources().drown(), 2.0F);
        }
    }

    private static void tickTint(final ServerLevel level, final LivingEntity target) {
        if (BrewMarkerState.isActive(target, BrewMarkerKind.TINT_SKIN)) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false, true));
            level.sendParticles(
                new DustParticleOptions(BrewMarkerState.color(target, BrewMarkerKind.TINT_SKIN, 0x7FBAB4), 0.75F),
                target.getX(),
                target.getY() + target.getBbHeight() * 0.5,
                target.getZ(),
                8,
                target.getBbWidth() * 0.55,
                target.getBbHeight() * 0.45,
                target.getBbWidth() * 0.55,
                0.01
            );
        }
    }

    private static void tickWerewolfLock(final LivingEntity target) {
        if (!(target instanceof Player player)
            || !BrewMarkerState.isActive(player, BrewMarkerKind.WEREWOLF_LOCK)) {
            return;
        }
        BrewMarkerState.lockedForm(player)
            .filter(form -> SupernaturalState.getForm(player) != form)
            .ifPresent(form -> SupernaturalState.setForm(player, form));
    }

    private static void tickSunlightCurse(final ServerLevel level, final LivingEntity target) {
        if (!BrewMarkerState.isActive(target, BrewMarkerKind.SUNLIGHT_CURSE)
            || !target.typeHolder().is(EntityTypeTags.SENSITIVE_TO_SMITE)
            || target.hasEffect(MobEffects.FIRE_RESISTANCE)
            || !level.canSeeSky(target.blockPosition())) {
            return;
        }
        final long time = Math.floorMod(level.getOverworldClockTime(), 24_000L);
        if (time >= 13_000L) {
            return;
        }
        target.igniteForSeconds(4.0F);
        target.hurtServer(level, target.damageSources().onFire(), 1.0F);
    }

    private static void tickContagion(
        final ServerLevel level,
        final LivingEntity carrier,
        final BrewMarkerKind kind
    ) {
        if (!BrewMarkerState.isActive(carrier, kind)) {
            return;
        }
        final int duration = Math.max(100, BrewMarkerState.remainingTicks(carrier, kind) / 2);
        level.getEntitiesOfClass(
            LivingEntity.class,
            carrier.getBoundingBox().inflate(kind == BrewMarkerKind.DISEASE ? 3.5 : 2.5),
            target -> target != carrier && target.isAlive() && !BrewMarkerState.isActive(target, kind)
        ).stream().limit(BrewMarkerRules.contagionLimit(kind)).forEach(target -> {
            BrewMarkerState.apply(target, kind, duration);
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 240, kind == BrewMarkerKind.DISEASE ? 1 : 0));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, kind == BrewMarkerKind.DISEASE ? 1 : 0));
        });
    }

    private static void reflectDamage(
        final LivingEntity target,
        final LivingEntity attacker,
        final float incomingDamage
    ) {
        if (REFLECTING_DAMAGE.get()
            || !BrewMarkerState.isActive(target, BrewMarkerKind.REFLECT_DAMAGE)
            || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        REFLECTING_DAMAGE.set(true);
        try {
            attacker.hurtServer(
                level,
                target.damageSources().thorns(target),
                Math.clamp(incomingDamage * 0.5F, 0.5F, 8.0F)
            );
        } finally {
            REFLECTING_DAMAGE.set(false);
        }
    }

    private static void reincarnate(final ServerLevel level, final Animal original) {
        BrewMarkerState.remove(original, BrewMarkerKind.REINCARNATE);
        BuiltInRegistries.ENTITY_TYPE.getRandomElementOf(
            BrewCompatibilityTags.EntityTypes.REINCARNATION_CANDIDATES,
            level.getRandom()
        ).map(holder -> holder.value().create(level, EntitySpawnReason.EVENT))
            .filter(Animal.class::isInstance)
            .map(Animal.class::cast)
            .ifPresent(replacement -> {
                replacement.snapTo(original.getX(), original.getY(), original.getZ());
                replacement.setCustomName(original.getCustomName());
                level.addFreshEntity(replacement);
            });
    }
}
