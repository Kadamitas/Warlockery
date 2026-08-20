package com.kadamitas.warlockery.magic;

import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.fabric.event.LivingDamageContext;
import com.kadamitas.warlockery.util.DataParsing;
import com.kadamitas.warlockery.entity.ArcaneCreature;
import com.kadamitas.warlockery.entity.CorpseEntity;
import com.kadamitas.warlockery.entity.CorpseRuntime;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.item.InfernalPactEffects;
import com.kadamitas.warlockery.ritual.ManifestationRuntime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Relative;

public final class MagicPathRuntime {
    private static final String GRAVE_OWNER = "WarlockeryGraveOwner";
    private static final String GRAVE_EXPIRATION = "WarlockeryGraveExpiration";

    private MagicPathRuntime() {
    }

    public static void tickLevel(final ServerLevel level) {
        MagicConstructData.get(level).tick(level);
        ManifestationRuntime.tick(level);
    }

    public static void infuse(final List<? extends Player> players, final MagicPath path) {
        players.forEach(player -> MagicPathState.grantPermanent(player, path));
    }

    public static void recharge(final List<? extends Player> players, final int amount) {
        players.forEach(player -> MagicPathState.active(player)
            .forEach(path -> MagicPathState.recharge(player, path, amount)));
    }

    public static InteractionResult useSelf(final ServerPlayer player, final boolean secondary) {
        final Optional<MagicPath> selected = MagicPathState.selected(player);
        if (selected.isEmpty()) {
            show(player, null, MagicPathRules.Diagnostic.NOT_ATTUNED);
            return InteractionResult.FAIL;
        }
        if (secondary && MagicPathState.active(player).size() > 1) {
            final MagicPath next = MagicPathState.cycle(player).orElseThrow();
            show(player, next, MagicPathRules.Diagnostic.READY);
            return InteractionResult.SUCCESS;
        }
        final MagicPath path = selected.orElseThrow();
        return switch (path) {
            case IMP -> selfEffect(player, path, MobEffects.FIRE_RESISTANCE, 600, 0);
            case INFERNAL -> activateInfernalPower(player, path);
            case GRAVE -> selfEffect(player, path, MobEffects.NIGHT_VISION, 1_200, 0);
            case LIGHT -> selfEffect(player, path, MobEffects.INVISIBILITY, 600, 0);
            case OTHERWHERE -> secondary ? recall(player) : shortTeleport(player, 12.0);
            case OVERWORLD -> shockwave(player);
            case SKY -> skyLaunch(player);
        };
    }

    public static InteractionResult useTarget(
        final ServerPlayer player,
        final LivingEntity target,
        final boolean secondary
    ) {
        final Optional<MagicPath> selected = MagicPathState.selected(player);
        if (selected.isEmpty()) {
            show(player, null, MagicPathRules.Diagnostic.NOT_ATTUNED);
            return InteractionResult.FAIL;
        }
        final MagicPath path = selected.orElseThrow();
        return switch (path) {
            case IMP -> impTarget(player, target, secondary);
            case INFERNAL -> infernalTarget(player, target, secondary);
            case GRAVE -> bindUndead(player, target);
            case LIGHT -> imprisonWithLight(player, target);
            case OTHERWHERE -> otherwhereTarget(player, target, secondary);
            case OVERWORLD -> overworldTarget(player, target, secondary);
            case SKY -> skyTarget(player, target);
        };
    }

    public static InteractionResult useBlock(
        final ServerPlayer player,
        final BlockPos position,
        final Direction face,
        final boolean secondary
    ) {
        final Optional<MagicPath> selected = MagicPathState.selected(player);
        if (selected.isEmpty()) {
            show(player, null, MagicPathRules.Diagnostic.NOT_ATTUNED);
            return InteractionResult.FAIL;
        }
        final MagicPath path = selected.orElseThrow();
        return switch (path) {
            case IMP -> evaporate(player, position);
            case INFERNAL -> commandOwned(player, position, InfernalPactEffects.OWNER_KEY, path);
            case GRAVE -> commandOwned(player, position, GRAVE_OWNER, path);
            case LIGHT -> lightWall(player, position.relative(face), face);
            case OTHERWHERE -> secondary ? storeRecall(player) : teleportToBlock(player, position.relative(face));
            case OVERWORLD -> useOverworldBlock(player, position, secondary);
            case SKY -> updraft(player, position);
        };
    }

    public static boolean hasOtherwhere(final Player player) {
        return MagicPathState.has(player, MagicPath.OTHERWHERE);
    }

    public static boolean teleportToRecall(final ServerPlayer player) {
        return MagicPathState.recall(player).filter(recall -> teleport(player, recall)).isPresent();
    }

    public static boolean teleportToBoundPosition(
        final ServerPlayer player,
        final net.minecraft.resources.Identifier dimension,
        final BlockPos position
    ) {
        return teleport(player, new MagicPathState.Recall(dimension, position));
    }

    public static void tick(final Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        applyInfernalMotionPassive(serverPlayer);
        if (serverPlayer.tickCount % 20 == 0) {
            refreshImpPact(serverPlayer);
            applyPassives(serverPlayer);
            tickGraveThralls(serverPlayer);
        }
        if (serverPlayer.tickCount % 5 == 0 && MagicPathState.has(serverPlayer, MagicPath.OVERWORLD)) {
            magnetizeMetal(serverPlayer);
        }
    }

    public static void handleDamage(final LivingDamageContext event) {
        if (event.getEntity() instanceof ServerPlayer player
            && MagicPathState.has(player, MagicPath.INFERNAL)) {
            final InfernalPower power = MagicPathState.lastPower(player);
            if (event.getSource().is(DamageTypeTags.IS_FALL)
                && (power == InfernalPower.LEAPING || power == InfernalPower.FLIGHT)) {
                event.setAmount(0.0F);
                player.resetFallDistance();
            }
            if (event.getSource().is(DamageTypes.LIGHTNING_BOLT) && power == InfernalPower.EXPLOSION) {
                MagicPathState.recharge(player, MagicPath.INFERNAL, MagicPath.INFERNAL.maximumReserve());
            }
        }
        if (event.getEntity() instanceof ServerPlayer player
            && event.getSource().is(DamageTypeTags.IS_FALL)
            && MagicPathState.has(player, MagicPath.OVERWORLD)) {
            cushionOverworldFall(player, event);
        }
        if (event.getEntity() instanceof ServerPlayer player
            && event.getSource().getEntity() instanceof LivingEntity attacker
            && attacker != player
            && MagicPathState.has(player, MagicPath.LIGHT)
            && MagicPathState.spend(player, MagicPath.LIGHT, 4)) {
            event.setAmount(event.getAmount() * 0.6F);
            attacker.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0));
            attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 3));
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
            && event.getEntity() != attacker
            && MagicPathState.has(attacker, MagicPath.IMP)
            && MagicPathState.spend(attacker, MagicPath.IMP, 2)) {
            event.getEntity().igniteForSeconds(4.0F);
        }
    }

    public static void handleDeath(final LivingEntity victim, final net.minecraft.world.damagesource.DamageSource source) {
        final ServerPlayer killer = ownerFrom(source.getEntity()).orElse(null);
        if (killer != null
            && MagicPathState.has(killer, MagicPath.GRAVE)
            && isNourishing(victim)) {
            killer.heal(4.0F);
            killer.getFoodData().eat(4, 0.6F);
            MagicPathState.recharge(killer, MagicPath.GRAVE, 4);
            show(killer, MagicPath.GRAVE, MagicPathRules.Diagnostic.READY);
        }
    }

    private static Optional<ServerPlayer> ownerFrom(final Entity source) {
        if (source instanceof ServerPlayer player) {
            return Optional.of(player);
        }
        if (!(source instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        return DataParsing.uuid(WarlockeryEntityData.get(mob).getStringOr(GRAVE_OWNER, ""))
            .map(level.getServer().getPlayerList()::getPlayer);
    }

    private static boolean isNourishing(final LivingEntity victim) {
        return victim instanceof Player
            || victim instanceof Villager
            || victim.getType().getCategory() == MobCategory.CREATURE
            || victim.typeHolder().is(MagicCompatibilityTags.GRAVE_NOURISHING_VICTIMS);
    }

    private static void refreshImpPact(final ServerPlayer player) {
        final boolean boundImp = ((ServerLevel) player.level()).getEntitiesOfClass(
            Mob.class,
            player.getBoundingBox().inflate(32.0),
            mob -> mob instanceof ArcaneCreature creature
                && creature.creatureKind() == ArcaneCreature.CreatureKind.IMP
                && CreatureBehaviorState.isOwnedBy(mob, player.getUUID())
        ).stream().findAny().isPresent();
        if (boundImp) {
            MagicPathState.grantTimed(player, MagicPath.IMP, 60);
        }
    }

    private static void applyPassives(final ServerPlayer player) {
        MagicPathState.active(player).forEach(path -> {
            switch (path) {
                case IMP -> effect(player, MobEffects.FIRE_RESISTANCE, 60, 0);
                case INFERNAL -> applyInfernalPassive(player);
                case GRAVE -> effect(player, MobEffects.NIGHT_VISION, 240, 0);
                case LIGHT -> effect(player, MobEffects.NIGHT_VISION, 240, 0);
                case OTHERWHERE -> effect(player, MobEffects.SPEED, 60, 0);
                case OVERWORLD -> {
                    effect(player, MobEffects.HASTE, 60, 0);
                    effect(player, MobEffects.RESISTANCE, 60, 0);
                }
                case SKY -> effect(player, MobEffects.SLOW_FALLING, 60, 0);
            }
        });
    }

    private static void tickGraveThralls(final ServerPlayer player) {
        final ServerLevel level = (ServerLevel) player.level();
        level.getEntitiesOfClass(
            Mob.class,
            player.getBoundingBox().inflate(48.0),
            mob -> !(mob instanceof CorpseEntity)
                && player.getStringUUID().equals(WarlockeryEntityData.get(mob).getStringOr(GRAVE_OWNER, ""))
        ).forEach(thrall -> {
            if (level.getGameTime() >= WarlockeryEntityData.get(thrall).getLongOr(GRAVE_EXPIRATION, 0L)
                || !MagicPathState.has(player, MagicPath.GRAVE)) {
                WarlockeryEntityData.get(thrall).remove(GRAVE_OWNER);
                WarlockeryEntityData.get(thrall).remove(GRAVE_EXPIRATION);
                return;
            }
            if (thrall.getTarget() == player) {
                thrall.setTarget(null);
            }
            final LivingEntity threat = player.getLastHurtMob() != null ? player.getLastHurtMob() : player.getLastHurtByMob();
            if (threat != null && threat.isAlive() && thrall.canAttack(threat)) {
                thrall.setTarget(threat);
            }
        });
    }

    private static InteractionResult selfEffect(
        final ServerPlayer player,
        final MagicPath path,
        final net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
        final int duration,
        final int amplifier
    ) {
        final MagicPathRules.Decision decision = decision(player, path, MagicPathRules.ActionKind.SELF, true);
        if (!decision.success()) {
            return fail(player, path, decision);
        }
        player.addEffect(new MobEffectInstance(effect, duration, amplifier));
        return succeed(player, path, decision);
    }

    private static InteractionResult impTarget(
        final ServerPlayer player,
        final LivingEntity target,
        final boolean secondary
    ) {
        final MagicPathRules.Decision decision = decision(player, MagicPath.IMP, MagicPathRules.ActionKind.TARGET, target != player);
        if (!decision.success()) {
            return fail(player, MagicPath.IMP, decision);
        }
        if (secondary) {
            target.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1_200, 0));
        } else {
            target.igniteForSeconds(10.0F);
        }
        return succeed(player, MagicPath.IMP, decision);
    }

    private static InteractionResult infernalTarget(
        final ServerPlayer player,
        final LivingEntity target,
        final boolean secondary
    ) {
        final boolean valid = target instanceof Mob
            && !target.typeHolder().is(MagicCompatibilityTags.INFERNAL_ENTHRALLMENT_IMMUNE);
        final MagicPathRules.Decision decision = decision(
            player,
            MagicPath.INFERNAL,
            MagicPathRules.ActionKind.TARGET,
            valid
        );
        if (!decision.success()) {
            return fail(player, MagicPath.INFERNAL, decision);
        }
        final Mob mob = (Mob) target;
        final boolean owned = player.getStringUUID().equals(
            WarlockeryEntityData.get(mob).getStringOr(InfernalPactEffects.OWNER_KEY, "")
        );
        if (secondary && owned) {
            final InfernalPower power = classifySacrifice(mob);
            MagicPathState.setLastPower(player, power);
            MagicPathState.recharge(player, MagicPath.INFERNAL, 24);
            mob.discard();
        } else if (secondary) {
            WarlockeryEntityData.get(mob).putString(InfernalPactEffects.OWNER_KEY, player.getStringUUID());
            mob.setTarget(null);
            mob.setPersistenceRequired();
        } else {
            commandAttack(player, mob);
        }
        return succeed(player, MagicPath.INFERNAL, decision);
    }

    private static void commandAttack(final ServerPlayer player, final LivingEntity target) {
        final ServerLevel level = (ServerLevel) player.level();
        level.getEntitiesOfClass(
            Mob.class,
            player.getBoundingBox().inflate(32.0),
            mob -> player.getStringUUID().equals(
                WarlockeryEntityData.get(mob).getStringOr(InfernalPactEffects.OWNER_KEY, "")
            )
        ).forEach(mob -> mob.setTarget(target));
    }

    private static InfernalPower classifySacrifice(final LivingEntity target) {
        if (target.typeHolder().is(MagicCompatibilityTags.INFERNAL_EXPLOSION_POWER)) {
            return InfernalPower.EXPLOSION;
        }
        if (target.typeHolder().is(MagicCompatibilityTags.INFERNAL_PROJECTILE_POWER)) {
            return InfernalPower.PROJECTILE;
        }
        if (target.typeHolder().is(MagicCompatibilityTags.INFERNAL_WEB_POWER)) {
            return InfernalPower.WEB;
        }
        if (target.typeHolder().is(MagicCompatibilityTags.INFERNAL_LEAPING_POWER)) {
            return InfernalPower.LEAPING;
        }
        if (target.typeHolder().is(MagicCompatibilityTags.INFERNAL_FLIGHT_POWER)) {
            return InfernalPower.FLIGHT;
        }
        if (target.typeHolder().is(MagicCompatibilityTags.INFERNAL_FIRE_POWER)) {
            return InfernalPower.FIRE;
        }
        if (target.typeHolder().is(MagicCompatibilityTags.INFERNAL_TELEPORT_POWER)) {
            return InfernalPower.TELEPORT;
        }
        if (target.typeHolder().is(MagicCompatibilityTags.INFERNAL_AQUATIC_POWER)) {
            return InfernalPower.AQUATIC;
        }
        if (target.typeHolder().is(MagicCompatibilityTags.INFERNAL_UNDEAD_POWER)
            || target.typeHolder().is(EntityTypeTags.UNDEAD)) {
            return InfernalPower.UNDEAD;
        }
        if (target.typeHolder().is(MagicCompatibilityTags.INFERNAL_SPEED_POWER)) {
            return InfernalPower.SPEED;
        }
        return InfernalPower.HEALING;
    }

    private static InteractionResult activateInfernalPower(final ServerPlayer player, final MagicPath path) {
        final MagicPathRules.Decision decision = decision(player, path, MagicPathRules.ActionKind.SELF, true);
        if (!decision.success()) {
            return fail(player, path, decision);
        }
        switch (MagicPathState.lastPower(player)) {
            case EXPLOSION -> infernalExplosion(player);
            case PROJECTILE -> infernalArrow(player);
            case WEB -> infernalWeb(player);
            case FIRE -> infernalFireball(player);
            case SPEED -> effect(player, MobEffects.SPEED, 400, 3);
            case HEALING -> {
                player.heal(8.0F);
                player.getFoodData().eat(6, 0.8F);
            }
            case TELEPORT -> player.randomTeleport(
                player.getX() + player.getRandom().nextIntBetweenInclusive(-16, 16),
                player.getY(),
                player.getZ() + player.getRandom().nextIntBetweenInclusive(-16, 16),
                true
            );
            case LEAPING -> effect(player, MobEffects.JUMP_BOOST, 600, 2);
            case FLIGHT -> effect(player, MobEffects.NIGHT_VISION, 400, 0);
            case AQUATIC -> infernalBlind(player);
            case UNDEAD -> {
                effect(player, MobEffects.RESISTANCE, 600, 1);
                effect(player, MobEffects.STRENGTH, 600, 1);
            }
        }
        return succeed(player, path, decision);
    }

    private static void applyInfernalMotionPassive(final ServerPlayer player) {
        if (!MagicPathState.has(player, MagicPath.INFERNAL)
            || MagicPathState.lastPower(player) != InfernalPower.WEB
            || !player.horizontalCollision) {
            return;
        }
        final Vec3 movement = player.getDeltaMovement();
        player.setDeltaMovement(movement.x, Math.max(0.2, movement.y), movement.z);
        player.resetFallDistance();
        player.hurtMarked = true;
    }

    private static void applyInfernalPassive(final ServerPlayer player) {
        switch (MagicPathState.lastPower(player)) {
            case PROJECTILE -> effect(player, MobEffects.WATER_BREATHING, 60, 0);
            case FIRE -> effect(player, MobEffects.FIRE_RESISTANCE, 60, 0);
            case SPEED -> effect(player, MobEffects.SPEED, 60, 1);
            case LEAPING -> effect(player, MobEffects.JUMP_BOOST, 60, 0);
            case FLIGHT -> effect(player, MobEffects.SLOW_FALLING, 60, 0);
            case AQUATIC -> {
                effect(player, MobEffects.WATER_BREATHING, 60, 0);
                effect(player, MobEffects.DOLPHINS_GRACE, 60, 0);
            }
            case EXPLOSION, WEB, HEALING, TELEPORT, UNDEAD -> {
            }
        }
    }

    private static void infernalExplosion(final ServerPlayer player) {
        final Vec3 center = player.getEyePosition().add(player.getLookAngle().scale(2.5));
        player.level().explode(
            player,
            center.x,
            center.y,
            center.z,
            3.0F,
            Level.ExplosionInteraction.NONE
        );
    }

    private static void infernalArrow(final ServerPlayer player) {
        final ServerLevel level = (ServerLevel) player.level();
        final Arrow arrow = new Arrow(level, player, Items.ARROW.getDefaultInstance(), player.getMainHandItem());
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setBaseDamage(4.0);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 2.4F, 0.4F);
        level.addFreshEntity(arrow);
    }

    private static void infernalWeb(final ServerPlayer player) {
        final Vec3 start = player.getEyePosition();
        final BlockHitResult hit = player.level().clip(new ClipContext(
            start,
            start.add(player.getLookAngle().scale(16.0)),
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        ));
        final BlockPos position = hit.getBlockPos().relative(hit.getDirection());
        if (player.level().getBlockState(position).canBeReplaced()) {
            player.level().setBlockAndUpdate(position, Blocks.COBWEB.defaultBlockState());
        }
    }

    private static void infernalFireball(final ServerPlayer player) {
        final Vec3 direction = player.getLookAngle();
        final SmallFireball fireball = new SmallFireball(player.level(), player, direction);
        fireball.setPos(player.getX(), player.getEyeY() - 0.15, player.getZ());
        player.level().addFreshEntity(fireball);
    }

    private static void infernalBlind(final ServerPlayer player) {
        final Vec3 look = player.getLookAngle();
        ((ServerLevel) player.level()).getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(12.0),
            target -> target != player && target.isAlive()
                && target.position().subtract(player.position()).normalize().dot(look) > 0.65
        ).stream().min(Comparator.comparingDouble(player::distanceToSqr)).ifPresent(target ->
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0))
        );
    }

    private static InteractionResult bindUndead(final ServerPlayer player, final LivingEntity target) {
        final boolean valid = target instanceof Mob && target.typeHolder().is(EntityTypeTags.UNDEAD);
        final MagicPathRules.Decision decision = decision(
            player,
            MagicPath.GRAVE,
            MagicPathRules.ActionKind.TARGET,
            valid
        );
        if (!decision.success()) {
            return fail(player, MagicPath.GRAVE, decision);
        }
        final Mob mob = (Mob) target;
        WarlockeryEntityData.get(mob).putString(GRAVE_OWNER, player.getStringUUID());
        WarlockeryEntityData.get(mob).putLong(GRAVE_EXPIRATION, player.level().getGameTime() + 144_000L);
        mob.setTarget(null);
        mob.setPersistenceRequired();
        if (mob instanceof CorpseEntity corpse) {
            CorpseRuntime.notifyGraveBind(corpse, (ServerLevel) player.level());
        }
        return succeed(player, MagicPath.GRAVE, decision);
    }

    private static InteractionResult imprisonWithLight(final ServerPlayer player, final LivingEntity target) {
        final MagicPathRules.Decision decision = decision(
            player,
            MagicPath.LIGHT,
            MagicPathRules.ActionKind.TARGET,
            target != player
        );
        if (!decision.success()) {
            return fail(player, MagicPath.LIGHT, decision);
        }
        target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 0));
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 5));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
        MagicConstructData.get((ServerLevel) player.level()).place(
            (ServerLevel) player.level(),
            MagicConstructRules.prison(target.blockPosition()),
            player.level().getGameTime() + 200L
        );
        return succeed(player, MagicPath.LIGHT, decision);
    }

    private static InteractionResult otherwhereTarget(
        final ServerPlayer player,
        final LivingEntity target,
        final boolean secondary
    ) {
        final MagicPathRules.Decision decision = decision(
            player,
            MagicPath.OTHERWHERE,
            MagicPathRules.ActionKind.TARGET,
            target != player
        );
        if (!decision.success()) {
            return fail(player, MagicPath.OTHERWHERE, decision);
        }
        final boolean moved;
        if (secondary && target instanceof ServerPlayer targetPlayer) {
            moved = MagicPathState.recall(player).map(recall -> teleport(player, recall) && teleport(targetPlayer, recall)).orElse(false);
        } else {
            player.teleportTo(player.getX(), player.getY() + 8.0, player.getZ());
            target.teleportTo(target.getX(), target.getY() + 8.0, target.getZ());
            moved = true;
        }
        return moved ? succeed(player, MagicPath.OTHERWHERE, decision) : failAfterSpend(player, MagicPath.OTHERWHERE);
    }

    private static InteractionResult overworldTarget(
        final ServerPlayer player,
        final LivingEntity target,
        final boolean secondary
    ) {
        final boolean metalArmor = List.of(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
        ).stream().map(target::getItemBySlot).anyMatch(stack -> stack.is(MagicCompatibilityTags.METAL_EQUIPMENT));
        final Optional<EquipmentSlot> heldMetal = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND).stream()
            .filter(slot -> target.getItemBySlot(slot).is(MagicCompatibilityTags.METAL_EQUIPMENT))
            .findFirst();
        final boolean valid = secondary ? heldMetal.isPresent() : metalArmor;
        final MagicPathRules.Decision decision = decision(
            player,
            MagicPath.OVERWORLD,
            MagicPathRules.ActionKind.TARGET,
            valid
        );
        if (!decision.success()) {
            return fail(player, MagicPath.OVERWORLD, decision);
        }
        if (secondary) {
            final EquipmentSlot slot = heldMetal.orElseThrow();
            final ItemStack removed = target.getItemBySlot(slot).copy();
            target.setItemSlot(slot, ItemStack.EMPTY);
            final ItemEntity dropped = new ItemEntity(target.level(), target.getX(), target.getY() + 1.0, target.getZ(), removed);
            dropped.setTarget(player.getUUID());
            target.level().addFreshEntity(dropped);
        } else {
            target.knockback(3.0, player.getX() - target.getX(), player.getZ() - target.getZ(), player.damageSources().magic(), 0.0F);
        }
        return succeed(player, MagicPath.OVERWORLD, decision);
    }

    private static InteractionResult skyTarget(final ServerPlayer player, final LivingEntity target) {
        final MagicPathRules.Decision decision = decision(
            player,
            MagicPath.SKY,
            MagicPathRules.ActionKind.TARGET,
            target != player
        );
        if (!decision.success()) {
            return fail(player, MagicPath.SKY, decision);
        }
        target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 1));
        target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 240, 0));
        return succeed(player, MagicPath.SKY, decision);
    }

    private static InteractionResult commandOwned(
        final ServerPlayer player,
        final BlockPos position,
        final String ownerKey,
        final MagicPath path
    ) {
        final ServerLevel level = (ServerLevel) player.level();
        final List<Mob> owned = level.getEntitiesOfClass(
            Mob.class,
            player.getBoundingBox().inflate(32.0),
            mob -> !(mob instanceof CorpseEntity)
                && player.getStringUUID().equals(WarlockeryEntityData.get(mob).getStringOr(ownerKey, ""))
        );
        final List<CorpseEntity> bodies = GRAVE_OWNER.equals(ownerKey)
            ? boundedOwnedBodies(player, level)
            : List.of();
        final MagicPathRules.Decision decision = decision(
            player,
            path,
            MagicPathRules.ActionKind.WORLD,
            !owned.isEmpty() || !bodies.isEmpty()
        );
        if (!decision.success()) {
            return fail(player, path, decision);
        }
        owned.forEach(mob -> {
            mob.setTarget(null);
            mob.getNavigation().moveTo(position.getX() + 0.5, position.getY() + 1.0, position.getZ() + 0.5, 1.1);
        });
        for (final CorpseEntity body : bodies) {
            if (!CorpseRuntime.takeGraveDirectiveToken(level)) {
                break;
            }
            CorpseRuntime.deliverGraveDirective(body, level, position);
        }
        return succeed(player, path, decision);
    }

    /**
     * Bounded exact-Body Grave command discovery: at most two scans per level tick,
     * a raw snapshot of at most 64 Bodies inside the existing 32-block box before
     * owner filtering, deterministic UUID ordering, and normal failure when the
     * budget is exhausted or no owned Body is inside the bounded snapshot.
     */
    private static List<CorpseEntity> boundedOwnedBodies(final ServerPlayer player, final ServerLevel level) {
        if (!CorpseRuntime.takeGraveScanToken(level)) {
            return List.of();
        }
        final java.util.ArrayList<CorpseEntity> raw = new java.util.ArrayList<>();
        com.kadamitas.warlockery.entity.BoundedEntityQuery.visit(level,
            net.minecraft.world.level.entity.EntityTypeTest.forClass(CorpseEntity.class),
            player.getBoundingBox().inflate(32.0),
            body -> {
                raw.add(body);
                return raw.size() >= 64
                    ? net.minecraft.util.AbortableIterationConsumer.Continuation.ABORT
                    : net.minecraft.util.AbortableIterationConsumer.Continuation.CONTINUE;
            }
        );
        return raw.stream()
            .filter(body -> player.getStringUUID().equals(
                WarlockeryEntityData.get(body).getStringOr(GRAVE_OWNER, "")))
            .sorted(Comparator.comparing(CorpseEntity::getUUID))
            .toList();
    }

    private static InteractionResult lightWall(
        final ServerPlayer player,
        final BlockPos origin,
        final Direction face
    ) {
        final ServerLevel level = (ServerLevel) player.level();
        final List<BlockPos> plan = MagicConstructRules.wall(origin, face.getAxis().isHorizontal() ? face : player.getDirection());
        final boolean room = plan.stream().anyMatch(pos -> level.getBlockEntity(pos) == null && level.getBlockState(pos).canBeReplaced());
        final MagicPathRules.Decision decision = decision(
            player,
            MagicPath.LIGHT,
            MagicPathRules.ActionKind.WORLD,
            room
        );
        if (!decision.success()) {
            return fail(player, MagicPath.LIGHT, decision);
        }
        final int placed = MagicConstructData.get(level).place(level, plan, level.getGameTime() + 600L);
        return placed > 0 ? succeed(player, MagicPath.LIGHT, decision) : failAfterSpend(player, MagicPath.LIGHT);
    }

    private static InteractionResult evaporate(final ServerPlayer player, final BlockPos center) {
        final ServerLevel level = (ServerLevel) player.level();
        final List<BlockPos> fluids = BlockPos.betweenClosedStream(center.offset(-2, -2, -2), center.offset(2, 2, 2))
            .filter(pos -> level.getFluidState(pos).is(MagicCompatibilityTags.IMP_EVAPORATABLE_FLUIDS))
            .limit(32)
            .map(BlockPos::immutable)
            .toList();
        final MagicPathRules.Decision decision = decision(
            player,
            MagicPath.IMP,
            MagicPathRules.ActionKind.WORLD,
            !fluids.isEmpty()
        );
        if (!decision.success()) {
            return fail(player, MagicPath.IMP, decision);
        }
        fluids.forEach(pos -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
        level.sendParticles(ParticleTypes.CLOUD, center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5, 24, 2, 1, 2, 0.02);
        return succeed(player, MagicPath.IMP, decision);
    }

    private static InteractionResult storeRecall(final ServerPlayer player) {
        final MagicPathRules.Decision decision = decision(
            player,
            MagicPath.OTHERWHERE,
            MagicPathRules.ActionKind.WORLD,
            true
        );
        if (!decision.success()) {
            return fail(player, MagicPath.OTHERWHERE, decision);
        }
        MagicPathState.setRecall(player, player.level().dimension().identifier(), player.blockPosition());
        return succeed(player, MagicPath.OTHERWHERE, decision);
    }

    private static InteractionResult recall(final ServerPlayer player) {
        final boolean present = MagicPathState.recall(player).isPresent();
        final MagicPathRules.Decision decision = decision(
            player,
            MagicPath.OTHERWHERE,
            MagicPathRules.ActionKind.SELF,
            present
        );
        if (!decision.success()) {
            return fail(player, MagicPath.OTHERWHERE, decision);
        }
        return teleportToRecall(player)
            ? succeed(player, MagicPath.OTHERWHERE, decision)
            : failAfterSpend(player, MagicPath.OTHERWHERE);
    }

    private static boolean teleport(final ServerPlayer player, final MagicPathState.Recall recall) {
        final ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, recall.dimension());
        final ServerLevel destination = player.level().getServer().getLevel(dimension);
        if (destination == null) {
            return false;
        }
        destination.getChunkAt(recall.position());
        return player.teleportTo(
            destination,
            recall.position().getX() + 0.5,
            recall.position().getY() + 1.0,
            recall.position().getZ() + 0.5,
            Set.of(),
            player.getYRot(),
            player.getXRot(),
            true
        );
    }

    private static InteractionResult teleportToBlock(final ServerPlayer player, final BlockPos destination) {
        final ServerLevel level = (ServerLevel) player.level();
        final boolean safe = level.isEmptyBlock(destination)
            && level.isEmptyBlock(destination.above())
            && level.getBlockState(destination.below()).isFaceSturdy(level, destination.below(), Direction.UP);
        final MagicPathRules.Decision decision = decision(
            player,
            MagicPath.OTHERWHERE,
            MagicPathRules.ActionKind.WORLD,
            safe
        );
        if (!decision.success()) {
            return fail(player, MagicPath.OTHERWHERE, decision);
        }
        player.teleportTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5);
        return succeed(player, MagicPath.OTHERWHERE, decision);
    }

    private static InteractionResult shortTeleport(final ServerPlayer player, final double range) {
        final Vec3 look = player.getLookAngle();
        final Optional<BlockPos> destination = java.util.stream.IntStream.iterate((int) range, value -> value > 0, value -> value - 1)
            .mapToObj(distance -> BlockPos.containing(player.getEyePosition().add(look.scale(distance))))
            .filter(pos -> player.level().isEmptyBlock(pos))
            .filter(pos -> player.level().isEmptyBlock(pos.above()))
            .filter(pos -> player.level().getBlockState(pos.below()).isFaceSturdy(player.level(), pos.below(), Direction.UP))
            .findFirst();
        final MagicPathRules.Decision decision = decision(
            player,
            MagicPath.OTHERWHERE,
            MagicPathRules.ActionKind.SELF,
            destination.isPresent()
        );
        if (!decision.success()) {
            return fail(player, MagicPath.OTHERWHERE, decision);
        }
        final BlockPos pos = destination.orElseThrow();
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return succeed(player, MagicPath.OTHERWHERE, decision);
    }

    private static InteractionResult raiseEarth(final ServerPlayer player, final BlockPos origin) {
        final ServerLevel level = (ServerLevel) player.level();
        final var state = level.getBlockState(origin);
        final List<BlockPos> positions = java.util.stream.IntStream.rangeClosed(1, 3)
            .mapToObj(origin::above)
            .filter(pos -> level.getBlockState(pos).canBeReplaced() && level.getBlockEntity(pos) == null)
            .toList();
        final boolean valid = state.is(MagicCompatibilityTags.EARTH_CONTROLLED_BLOCKS) && !positions.isEmpty();
        final MagicPathRules.Decision decision = decision(
            player,
            MagicPath.OVERWORLD,
            MagicPathRules.ActionKind.WORLD,
            valid
        );
        if (!decision.success()) {
            return fail(player, MagicPath.OVERWORLD, decision);
        }
        positions.forEach(pos -> level.setBlockAndUpdate(pos, state));
        return succeed(player, MagicPath.OVERWORLD, decision);
    }

    private static InteractionResult useOverworldBlock(
        final ServerPlayer player,
        final BlockPos position,
        final boolean secondary
    ) {
        final ServerLevel level = (ServerLevel) player.level();
        final BlockState state = level.getBlockState(position);
        if (secondary) {
            return state.is(MagicCompatibilityTags.EARTH_CONTROLLED_BLOCKS)
                ? launchEarthBlock(player, position)
                : pullMetal(player);
        }
        return state.is(MagicCompatibilityTags.OVERWORLD_TRANSMUTABLE_ORES)
            ? transmuteOre(player, position)
            : raiseEarth(player, position);
    }

    private static InteractionResult transmuteOre(final ServerPlayer player, final BlockPos position) {
        final ServerLevel level = (ServerLevel) player.level();
        final BlockState state = level.getBlockState(position);
        final List<ItemStack> drops = state.is(MagicCompatibilityTags.OVERWORLD_TRANSMUTABLE_ORES)
            ? Block.getDrops(state, level, position, level.getBlockEntity(position), player, new ItemStack(Items.NETHERITE_PICKAXE))
            : List.of();
        final boolean valid = !drops.isEmpty()
            && level.getBlockEntity(position) == null
            && level.mayInteract(player, position);
        final MagicPathRules.Decision decision = decision(
            player,
            MagicPath.OVERWORLD,
            MagicPathRules.ActionKind.WORLD,
            valid
        );
        if (!decision.success()) {
            return fail(player, MagicPath.OVERWORLD, decision);
        }
        if (!level.setBlockAndUpdate(position, Blocks.STONE.defaultBlockState())) {
            return failAfterSpend(player, MagicPath.OVERWORLD);
        }
        drops.forEach(drop -> java.util.stream.IntStream.range(0, OverworldInfusionRules.transmutationDropCopies())
            .forEach(copy -> Block.popResource(level, position, drop.copyWithCount(1))));
        return succeed(player, MagicPath.OVERWORLD, decision);
    }

    private static InteractionResult launchEarthBlock(final ServerPlayer player, final BlockPos position) {
        final ServerLevel level = (ServerLevel) player.level();
        final BlockState state = level.getBlockState(position);
        final boolean valid = state.is(MagicCompatibilityTags.EARTH_CONTROLLED_BLOCKS)
            && !state.isAir()
            && level.getBlockEntity(position) == null
            && level.mayInteract(player, position);
        final MagicPathRules.Decision decision = decision(
            player,
            MagicPath.OVERWORLD,
            MagicPathRules.ActionKind.WORLD,
            valid
        );
        if (!decision.success()) {
            return fail(player, MagicPath.OVERWORLD, decision);
        }
        final FallingBlockEntity projectile = FallingBlockEntity.fall(level, position, state);
        projectile.setDeltaMovement(OverworldInfusionRules.launchedBlockVelocity(player.getLookAngle()));
        projectile.setHurtsEntities(6.0F, 20);
        return succeed(player, MagicPath.OVERWORLD, decision);
    }

    private static InteractionResult pullMetal(final ServerPlayer player) {
        final ServerLevel level = (ServerLevel) player.level();
        final List<ItemEntity> items = level.getEntitiesOfClass(
            ItemEntity.class,
            player.getBoundingBox().inflate(16.0),
            item -> item.getItem().is(MagicCompatibilityTags.METAL_DROPS)
        );
        final MagicPathRules.Decision decision = decision(
            player,
            MagicPath.OVERWORLD,
            MagicPathRules.ActionKind.WORLD,
            !items.isEmpty()
        );
        if (!decision.success()) {
            return fail(player, MagicPath.OVERWORLD, decision);
        }
        items.forEach(item -> {
            item.setTarget(player.getUUID());
            item.setDeltaMovement(player.position().subtract(item.position()).normalize().scale(0.7));
        });
        return succeed(player, MagicPath.OVERWORLD, decision);
    }

    private static void magnetizeMetal(final ServerPlayer player) {
        ((ServerLevel) player.level()).getEntitiesOfClass(
            ItemEntity.class,
            player.getBoundingBox().inflate(6.0),
            item -> item.getItem().is(MagicCompatibilityTags.METAL_DROPS)
        ).forEach(item -> {
            item.setTarget(player.getUUID());
            item.setDeltaMovement(player.position().subtract(item.position()).normalize().scale(0.3));
        });
    }

    private static InteractionResult shockwave(final ServerPlayer player) {
        final List<LivingEntity> targets = ((ServerLevel) player.level()).getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(6.0),
            target -> target != player && target.isAlive()
        );
        final MagicPathRules.Decision decision = decision(
            player,
            MagicPath.OVERWORLD,
            MagicPathRules.ActionKind.SELF,
            !targets.isEmpty()
        );
        if (!decision.success()) {
            return fail(player, MagicPath.OVERWORLD, decision);
        }
        targets.forEach(target -> {
            target.knockback(2.0, player.getX() - target.getX(), player.getZ() - target.getZ(), player.damageSources().magic(), 0.0F);
            target.hurtServer((ServerLevel) player.level(), player.damageSources().magic(), 4.0F);
        });
        return succeed(player, MagicPath.OVERWORLD, decision);
    }

    private static InteractionResult skyLaunch(final ServerPlayer player) {
        final MagicPathRules.Decision decision = decision(player, MagicPath.SKY, MagicPathRules.ActionKind.SELF, true);
        if (!decision.success()) {
            return fail(player, MagicPath.SKY, decision);
        }
        player.setDeltaMovement(player.getDeltaMovement().add(0.0, 1.1, 0.0));
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0));
        return succeed(player, MagicPath.SKY, decision);
    }

    private static InteractionResult updraft(final ServerPlayer player, final BlockPos center) {
        final List<LivingEntity> targets = ((ServerLevel) player.level()).getEntitiesOfClass(
            LivingEntity.class,
            new AABB(center).inflate(4.0),
            LivingEntity::isAlive
        );
        final MagicPathRules.Decision decision = decision(
            player,
            MagicPath.SKY,
            MagicPathRules.ActionKind.WORLD,
            !targets.isEmpty()
        );
        if (!decision.success()) {
            return fail(player, MagicPath.SKY, decision);
        }
        targets.forEach(target -> {
            target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 0));
            target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 240, 0));
        });
        return succeed(player, MagicPath.SKY, decision);
    }

    private static void cushionOverworldFall(final ServerPlayer player, final LivingDamageContext event) {
        final BlockPos below = player.blockPosition().below();
        final ServerLevel level = (ServerLevel) player.level();
        if (!level.getBlockState(below).is(MagicCompatibilityTags.OVERWORLD_LANDING_BLOCKS)
            || level.getBlockEntity(below) != null
            || !MagicPathState.spend(player, MagicPath.OVERWORLD, 4)) {
            return;
        }
        level.destroyBlock(below, true, player);
        event.setAmount(0.0F);
        player.resetFallDistance();
        if (player.isShiftKeyDown()) {
            level.explode(player, player.getX(), player.getY(), player.getZ(), 2.0F, Level.ExplosionInteraction.NONE);
        }
    }

    private static MagicPathRules.Decision decision(
        final ServerPlayer player,
        final MagicPath path,
        final MagicPathRules.ActionKind action,
        final boolean valid
    ) {
        final int cost = MagicPathProfile.forPath(path).cost(action);
        return MagicPathRules.decide(
            MagicPathState.has(player, path),
            MagicPathState.reserve(player, path),
            cost,
            valid
        );
    }

    private static InteractionResult succeed(
        final ServerPlayer player,
        final MagicPath path,
        final MagicPathRules.Decision decision
    ) {
        MagicPathState.spend(player, path, decision.reserveSpent());
        show(player, path, decision.diagnostic());
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult fail(
        final ServerPlayer player,
        final MagicPath path,
        final MagicPathRules.Decision decision
    ) {
        show(player, path, decision.diagnostic());
        return InteractionResult.FAIL;
    }

    private static InteractionResult failAfterSpend(final ServerPlayer player, final MagicPath path) {
        show(player, path, MagicPathRules.Diagnostic.INVALID_TARGET);
        return InteractionResult.FAIL;
    }

    private static void show(
        final ServerPlayer player,
        final MagicPath path,
        final MagicPathRules.Diagnostic diagnostic
    ) {
        final ChatFormatting color = diagnostic == MagicPathRules.Diagnostic.READY
            ? ChatFormatting.GREEN
            : ChatFormatting.RED;
        final Component pathName = path == null
            ? Component.translatable("item.warlockery.arcane_focus")
            : Component.translatable("magic_path.warlockery." + path.id());
        final Component message = switch (diagnostic) {
            case NOT_ATTUNED -> Component.translatable(diagnostic.messageKey());
            case INVALID_TARGET -> Component.translatable(diagnostic.messageKey(), pathName);
            case INSUFFICIENT_RESERVE, READY -> Component.translatable(
                diagnostic.messageKey(),
                pathName,
                path == null ? 0 : MagicPathState.reserve(player, path),
                path == null ? 0 : path.maximumReserve()
            );
        };
        player.sendOverlayMessage(message.copy().withStyle(color));
    }

    private static void effect(
        final LivingEntity target,
        final net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
        final int duration,
        final int amplifier
    ) {
        target.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false));
    }
}
