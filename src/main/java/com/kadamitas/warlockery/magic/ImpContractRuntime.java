package com.kadamitas.warlockery.magic;

import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.fabric.event.BlockBreakContext;
import com.kadamitas.warlockery.entity.CreatureBehaviorProfile;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.item.SympatheticBinding;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class ImpContractRuntime {
    private static final String MELTING_EXPIRATION = "WarlockeryImpMeltingExpiration";

    private ImpContractRuntime() {
    }

    public static InteractionResult interact(
        final Mob imp,
        final Player player,
        final ItemStack held,
        final CreatureBehaviorProfile profile
    ) {
        if (held.is(MagicCompatibilityTags.IMP_GIFTS)
            && CreatureBehaviorState.isOwnedBy(imp, player.getUUID())) {
            final int favor = CreatureBehaviorState.impressImp(imp);
            consume(player, held);
            player.sendSystemMessage(Component.translatable("message.warlockery.imp_contract.favor", favor, 6));
            return InteractionResult.SUCCESS;
        }
        if (profile.offering().stream().noneMatch(held::is)) {
            return InteractionResult.PASS;
        }
        final Optional<java.util.UUID> owner = CreatureBehaviorState.owner(imp);
        if (owner.isEmpty()) {
            if (!player.hasInfiniteMaterials() && player.experienceLevel < ImpContractRules.BINDING_LEVEL_COST) {
                player.sendSystemMessage(Component.translatable("message.warlockery.imp_contract.experience"));
                return InteractionResult.FAIL;
            }
            CreatureBehaviorState.bind(imp, player.getUUID());
            imp.setTarget(null);
            imp.setPersistenceRequired();
            if (!player.hasInfiniteMaterials()) {
                player.giveExperienceLevels(-ImpContractRules.BINDING_LEVEL_COST);
            }
            consume(player, held);
            player.sendSystemMessage(Component.translatable("message.warlockery.creature.bound", imp.getDisplayName()));
            return InteractionResult.SUCCESS;
        }
        if (!CreatureBehaviorState.isOwnedBy(imp, player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.warlockery.creature.bound_elsewhere", imp.getDisplayName()));
            return InteractionResult.FAIL;
        }
        final String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
        final Optional<ImpContractRules.Spell> spell = ImpContractRules.Spell.forItem(itemId);
        if (spell.isEmpty()) {
            return InteractionResult.FAIL;
        }
        final LivingEntity target = contractTarget(held, player).orElse(player);
        final ImpContractRules.Decision decision = ImpContractRules.decide(
            true,
            true,
            false,
            CreatureBehaviorState.impFavor(imp),
            spell.orElseThrow().favor(),
            target.level() == imp.level()
        );
        if (!decision.success()) {
            player.sendSystemMessage(Component.translatable(decision.messageKey()));
            return InteractionResult.FAIL;
        }
        cast(spell.orElseThrow(), imp, target, player);
        consume(player, held);
        player.sendSystemMessage(Component.translatable(decision.messageKey()));
        return InteractionResult.SUCCESS;
    }

    public static void handleBlockBreak(final BlockBreakContext event) {
        final ServerPlayer player = event.getPlayer();
        final ServerLevel level = event.getLevel();
        if (level.getGameTime() >= WarlockeryEntityData.get(player).getLongOr(MELTING_EXPIRATION, 0L)
            || !event.getState().is(MagicCompatibilityTags.IMP_SMELTABLE_BLOCKS)) {
            return;
        }
        final List<ItemStack> drops = Block.getDrops(
            event.getState(),
            level,
            event.getPos(),
            level.getBlockEntity(event.getPos()),
            player,
            player.getMainHandItem()
        );
        final List<ItemStack> converted = drops.stream().map(drop -> smelt(level, drop).orElse(drop)).toList();
        if (converted.equals(drops)) {
            return;
        }
        event.cancel();
        level.setBlockAndUpdate(event.getPos(), Blocks.AIR.defaultBlockState());
        converted.forEach(stack -> Block.popResource(level, event.getPos(), stack));
    }

    private static Optional<LivingEntity> contractTarget(final ItemStack contract, final Player player) {
        if (player.level() instanceof ServerLevel level) {
            final Optional<LivingEntity> bound = SympatheticBinding.read(contract)
                .flatMap(binding -> binding.resolve(level.getServer()));
            if (bound.isPresent()) {
                return bound;
            }
        }
        return Optional.ofNullable(player.getLastHurtMob() != null ? player.getLastHurtMob() : player.getLastHurtByMob());
    }

    private static void cast(
        final ImpContractRules.Spell spell,
        final Mob imp,
        final LivingEntity target,
        final Player caster
    ) {
        final ServerLevel level = (ServerLevel) imp.level();
        switch (spell) {
            case FIERY_TOUCH -> target.igniteForSeconds(20.0F);
            case EVAPORATION -> BlockPos.betweenClosedStream(
                    target.blockPosition().offset(-3, -2, -3),
                    target.blockPosition().offset(3, 2, 3)
                )
                .filter(pos -> level.getFluidState(pos).is(MagicCompatibilityTags.IMP_EVAPORATABLE_FLUIDS))
                .limit(64)
                .forEach(pos -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
            case FIRE_TOLERANCE -> target.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 36_000, 0));
            case MELTING_TOUCH -> WarlockeryEntityData.get(target).putLong(
                MELTING_EXPIRATION,
                level.getGameTime() + 36_000L
            );
            case LIVING_FLAME -> {
                final Mob blaze = EntityTypes.BLAZE.create(level, EntitySpawnReason.EVENT);
                if (blaze != null) {
                    blaze.snapTo(target.getX() + 2.0, target.getY(), target.getZ() + 2.0);
                    blaze.setTarget(target);
                    level.addFreshEntity(blaze);
                }
            }
            case TORMENT -> BuiltInRegistries.ENTITY_TYPE.get(
                Identifier.fromNamespaceAndPath("warlockery", "abyssal_regent")
            ).map(holder -> holder.value().create(level, EntitySpawnReason.EVENT)).ifPresent(entity -> {
                entity.snapTo(caster.getX() + 3.0, caster.getY(), caster.getZ() + 3.0);
                level.addFreshEntity(entity);
            });
        }
    }

    private static Optional<ItemStack> smelt(final ServerLevel level, final ItemStack stack) {
        final SingleRecipeInput input = new SingleRecipeInput(stack.copyWithCount(1));
        return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level)
            .map(recipe -> recipe.value().assemble(input))
            .filter(result -> !result.isEmpty())
            .map(result -> result.copyWithCount(Math.min(
                result.getMaxStackSize(),
                result.getCount() * stack.getCount()
            )));
    }

    private static void consume(final Player player, final ItemStack stack) {
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
    }
}
