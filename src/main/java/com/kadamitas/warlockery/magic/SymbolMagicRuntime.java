package com.kadamitas.warlockery.magic;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.item.AbyssalBanishment;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.ritual.RitualWardData;
import com.kadamitas.warlockery.ritual.RitualWardType;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

public final class SymbolMagicRuntime {
    private static final int COST = 2;

    private SymbolMagicRuntime() {
    }

    public static InteractionResult cycle(final ServerPlayer player, final ItemStack branch) {
        final SymbolSpell spell = SymbolBranchState.cycle(branch);
        player.sendOverlayMessage(Component.translatable(spell.translationKey()).withStyle(ChatFormatting.LIGHT_PURPLE));
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult castSelf(final ServerPlayer player, final ItemStack branch) {
        final SymbolSpell spell = SymbolBranchState.selected(branch);
        if (!spell.supports(SymbolSpell.Target.SELF) || !prepare(player, branch, spell)) {
            return InteractionResult.FAIL;
        }
        switch (spell) {
            case GRASP_OF_AIR -> ((ServerLevel) player.level()).getEntitiesOfClass(
                ItemEntity.class,
                player.getBoundingBox().inflate(16.0),
                ItemEntity::isAlive
            ).stream().limit(32).forEach(item -> item.setDeltaMovement(
                player.position().subtract(item.position()).normalize().scale(0.65)
            ));
            case RAVENOUS_COMMUNION -> {
                player.hurtServer((ServerLevel) player.level(), player.damageSources().magic(), 4.0F);
                MagicPathState.selected(player).ifPresent(path -> MagicPathState.recharge(player, path, 12));
            }
            case AWAKEN -> restore(player);
            case MEND_FLESH -> heal(player);
            case CALM_SKIES -> clearWeather((ServerLevel) player.level());
            case SNUFF_LIGHT -> extinguish((ServerLevel) player.level(), player.blockPosition(), player);
            default -> {
                return InteractionResult.FAIL;
            }
        }
        flourish(player, spell);
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult castBlock(
        final ServerPlayer player,
        final ItemStack branch,
        final BlockPos pos,
        final Direction face
    ) {
        final SymbolSpell spell = SymbolBranchState.selected(branch);
        if (!spell.supports(SymbolSpell.Target.BLOCK) || !prepare(player, branch, spell)) {
            return InteractionResult.FAIL;
        }
        final ServerLevel level = (ServerLevel) player.level();
        final BlockPos adjacent = pos.relative(face);
        final boolean success = switch (spell) {
            case SENTINEL_WARD -> placeWard(level, pos, RitualWardType.PROTECTION, 2, 1_200);
            case DREAD_SIGIL -> placeWard(level, pos, RitualWardType.SANCTITY, 8, 600);
            case WELLSPRING -> place(level, adjacent, Blocks.WATER.defaultBlockState());
            case UNSEAL -> setOpen(level, pos, true);
            case SEAL -> setOpen(level, pos, false);
            case DELVE -> level.getBlockEntity(pos) == null
                && !level.getBlockState(pos).isAir()
                && level.getBlockState(pos).getDestroySpeed(level, pos) >= 0.0F
                && level.destroyBlock(pos, true, player);
            case KINDLE -> place(level, adjacent, Blocks.FIRE.defaultBlockState());
            case WITCHLIGHT -> place(level, adjacent, Blocks.LIGHT.defaultBlockState());
            case SNUFF_LIGHT -> extinguish(level, pos, player) > 0;
            default -> false;
        };
        if (!success) {
            refund(player);
            return InteractionResult.FAIL;
        }
        flourish(player, spell);
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult castEntity(
        final ServerPlayer player,
        final ItemStack branch,
        final LivingEntity target
    ) {
        final SymbolSpell spell = SymbolBranchState.selected(branch);
        if (!spell.supports(SymbolSpell.Target.ENTITY) || !prepare(player, branch, spell)) {
            return InteractionResult.FAIL;
        }
        final ServerLevel level = (ServerLevel) player.level();
        final boolean success = switch (spell) {
            case SOULFIRE_LANCE -> igniteSoul(level, player, target);
            case DREAD_SIGIL -> placeWard(level, target.blockPosition(), RitualWardType.SANCTITY, 8, 600);
            case ABYSSAL_BANISHMENT -> AbyssalBanishment.banish(level, target);
            case BEWILDER -> effect(target, MobEffects.NAUSEA, 240, 1);
            case AGONY -> target.hurtServer(level, player.damageSources().magic(), 4.0F);
            case AWAKEN -> restore(target);
            case MEND_FLESH -> heal(target);
            case DISARM -> disarm(player, target);
            case REPULSE -> knockback(player, target, 2.5);
            case HOBBLE -> effect(target, MobEffects.SLOWNESS, 240, 3);
            case DOMINATE -> command(player, target);
            case STUN -> stun(target);
            default -> false;
        };
        if (!success) {
            refund(player);
            return InteractionResult.FAIL;
        }
        flourish(player, spell);
        return InteractionResult.SUCCESS;
    }

    public static void handleProjectileImpact(final ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof Projectile projectile)
            || !(event.getRayTraceResult() instanceof EntityHitResult hit)
            || !(hit.getEntity() instanceof ServerPlayer player)
            || !isBranch(player.getMainHandItem()) && !isBranch(player.getOffhandItem())) {
            return;
        }
        projectile.setOwner(player);
        projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1.2));
        event.setCanceled(true);
    }

    private static boolean prepare(final ServerPlayer player, final ItemStack branch, final SymbolSpell spell) {
        final var path = MagicPathState.selected(player);
        if (path.isEmpty() || spell.infernal() && !MagicPathState.has(player, MagicPath.INFERNAL)) {
            fail(player, "message.warlockery.symbol.failure.no_infusion");
            return false;
        }
        if (!SymbolBranchState.unlocked(branch, spell) && !unlock(player, branch, spell)) {
            fail(player, "message.warlockery.symbol.failure.soul_locked");
            return false;
        }
        if (spell == SymbolSpell.RAVENOUS_COMMUNION) {
            return true;
        }
        if (!MagicPathState.spend(player, path.orElseThrow(), COST)) {
            fail(player, "message.warlockery.symbol.failure.reserve");
            return false;
        }
        return true;
    }

    private static boolean unlock(final ServerPlayer player, final ItemStack branch, final SymbolSpell spell) {
        final String required = spell.soulIngredient().orElse("");
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            final ItemStack candidate = player.getInventory().getItem(slot);
            final var key = BuiltInRegistries.ITEM.getKey(candidate.getItem());
            if (key.getNamespace().equals(Warlockery.MOD_ID) && key.getPath().equals(required)) {
                if (!player.hasInfiniteMaterials()) {
                    candidate.shrink(1);
                }
                SymbolBranchState.unlock(branch, spell);
                return true;
            }
        }
        return false;
    }

    private static void refund(final ServerPlayer player) {
        MagicPathState.selected(player).ifPresent(path -> MagicPathState.recharge(player, path, COST));
    }

    private static boolean placeWard(
        final ServerLevel level,
        final BlockPos center,
        final RitualWardType type,
        final int radius,
        final int duration
    ) {
        RitualWardData.get(level).place(level, type, center, radius, level.getGameTime() + duration);
        return true;
    }

    private static void clearWeather(final ServerLevel level) {
        final var weather = level.getWeatherData();
        weather.setRaining(false);
        weather.setThundering(false);
        weather.setRainTime(6_000);
        weather.setThunderTime(6_000);
    }

    private static boolean place(final ServerLevel level, final BlockPos pos, final net.minecraft.world.level.block.state.BlockState state) {
        return level.getBlockEntity(pos) == null
            && level.getBlockState(pos).canBeReplaced()
            && state.canSurvive(level, pos)
            && level.setBlockAndUpdate(pos, state);
    }

    private static boolean setOpen(final ServerLevel level, final BlockPos pos, final boolean open) {
        final var state = level.getBlockState(pos);
        return state.hasProperty(BlockStateProperties.OPEN)
            && level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.OPEN, open));
    }

    private static int extinguish(final ServerLevel level, final BlockPos center, final ServerPlayer player) {
        final List<BlockPos> lights = BlockPos.betweenClosedStream(center.offset(-8, -4, -8), center.offset(8, 4, 8))
            .filter(pos -> level.getBlockEntity(pos) == null
                && level.getBlockState(pos).is(WarlockeryTags.Blocks.SYMBOL_LIGHT_SOURCES))
            .limit(64)
            .map(BlockPos::immutable)
            .toList();
        lights.forEach(pos -> level.destroyBlock(pos, true, player));
        return lights.size();
    }

    private static boolean igniteSoul(final ServerLevel level, final ServerPlayer player, final LivingEntity target) {
        target.igniteForSeconds(8.0F);
        final boolean hit = target.hurtServer(level, player.damageSources().magic(), 6.0F);
        level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3.0), entity ->
            entity != player && entity != target && entity.isAlive()
        ).forEach(entity -> {
            entity.igniteForSeconds(4.0F);
            entity.hurtServer(level, player.damageSources().magic(), 3.0F);
        });
        return hit;
    }

    private static boolean effect(
        final LivingEntity target,
        final net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
        final int duration,
        final int amplifier
    ) {
        return target.addEffect(new MobEffectInstance(effect, duration, amplifier));
    }

    private static boolean restore(final LivingEntity target) {
        final boolean removed = List.of(
            MobEffects.SLOWNESS,
            MobEffects.WEAKNESS,
            MobEffects.NAUSEA,
            MobEffects.BLINDNESS,
            MobEffects.POISON
        ).stream().map(target::removeEffect).reduce(false, Boolean::logicalOr);
        return removed || target.isAlive();
    }

    private static boolean heal(final LivingEntity target) {
        target.heal(4.0F);
        if (target instanceof ServerPlayer player) {
            player.causeFoodExhaustion(2.0F);
        }
        return true;
    }

    private static boolean disarm(final ServerPlayer player, final LivingEntity target) {
        final ItemStack held = target.getMainHandItem();
        if (held.isEmpty()) {
            return false;
        }
        final ItemStack removed = held.copy();
        target.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        final ItemEntity drop = new ItemEntity(target.level(), target.getX(), target.getY() + 1.0, target.getZ(), removed);
        drop.setTarget(player.getUUID());
        target.level().addFreshEntity(drop);
        return true;
    }

    private static boolean knockback(final ServerPlayer player, final LivingEntity target, final double strength) {
        target.knockback(
            strength,
            player.getX() - target.getX(),
            player.getZ() - target.getZ(),
            player.damageSources().magic(),
            0.35F
        );
        return true;
    }

    private static boolean command(final ServerPlayer player, final LivingEntity target) {
        if (!(target instanceof Mob mob)) {
            return false;
        }
        mob.setTarget(null);
        mob.getNavigation().moveTo(player, 1.25);
        mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2));
        return true;
    }

    private static boolean stun(final LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 160, 6));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 4));
        return true;
    }

    private static void flourish(final ServerPlayer player, final SymbolSpell spell) {
        player.level().playSound(
            null,
            player.blockPosition(),
            SoundEvents.ENCHANTMENT_TABLE_USE,
            SoundSource.PLAYERS,
            0.5F,
            0.8F + spell.ordinal() % 7 * 0.05F
        );
        player.sendOverlayMessage(Component.translatable(spell.translationKey()).withStyle(ChatFormatting.AQUA));
    }

    private static void fail(final ServerPlayer player, final String message) {
        player.sendOverlayMessage(Component.translatable(message).withStyle(ChatFormatting.RED));
    }

    private static boolean isBranch(final ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        final var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.getNamespace().equals(Warlockery.MOD_ID) && key.getPath().equals("mysticbranch");
    }
}
