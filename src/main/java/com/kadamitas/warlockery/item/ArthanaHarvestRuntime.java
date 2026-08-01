package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.ModItems;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.fabric.event.LivingDropsContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

public final class ArthanaHarvestRuntime {
    private static final List<BonusDrop> BONUS_DROPS = List.of(
        new BonusDrop(WarlockeryTags.EntityTypes.ARTHANA_BAT_SOURCES, "ingredient_bat_wool", 0.50F),
        new BonusDrop(WarlockeryTags.EntityTypes.ARTHANA_DOG_SOURCES, "ingredient_dog_tongue", 0.50F),
        new BonusDrop(WarlockeryTags.EntityTypes.ARTHANA_OWL_SOURCES, "ingredient_owlets_wing", 0.35F),
        new BonusDrop(WarlockeryTags.EntityTypes.ARTHANA_FROG_SOURCES, "ingredient_toe_of_frog", 0.50F),
        new BonusDrop(WarlockeryTags.EntityTypes.ARTHANA_HEART_SOURCES, "ingredient_creeper_heart", 0.12F),
        new BonusDrop(WarlockeryTags.EntityTypes.ARTHANA_SPECTRAL_SOURCES, "ingredient_spectral_dust", 0.15F)
    );

    private ArthanaHarvestRuntime() {
    }

    public static void addDrops(final LivingDropsContext event, final ServerLevel level) {
        final ItemStack weapon = event.getSource().getWeaponItem();
        if (weapon == null || !weapon.is(WarlockeryTags.Items.ARTHANAS)) {
            return;
        }
        final int looting = EnchantmentHelper.getItemEnchantmentLevel(
            level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),
            weapon
        );
        BONUS_DROPS.stream()
            .filter(drop -> event.getEntity().typeHolder().is(drop.sources()))
            .filter(drop -> level.getRandom().nextFloat() < chance(drop.baseChance(), looting))
            .map(drop -> new ItemStack(ModItems.ALL.get(drop.itemId()).get()))
            .map(stack -> entity(event.getEntity(), stack))
            .forEach(event.getDrops()::add);
        headStack(event.getEntity())
            .filter(stack -> level.getRandom().nextFloat() < headChance(event.getEntity(), looting))
            .map(stack -> entity(event.getEntity(), stack))
            .ifPresent(event.getDrops()::add);
    }

    static float chance(final float baseChance, final int looting) {
        return Math.clamp(baseChance + Math.max(0, looting) * 0.05F, 0.0F, 0.95F);
    }

    static Set<String> bonusDropItemIds() {
        return BONUS_DROPS.stream().map(BonusDrop::itemId).collect(Collectors.toUnmodifiableSet());
    }

    static Set<String> bonusSourceTagIds() {
        return BONUS_DROPS.stream()
            .map(BonusDrop::sources)
            .map(tag -> tag.location().toString())
            .collect(Collectors.toUnmodifiableSet());
    }

    static float headChance(final LivingEntity target, final int looting) {
        final float base = target instanceof Player ? 0.005F : target.getType() == EntityTypes.SKELETON ? 0.05F : 0.02F;
        return Math.clamp(base + Math.max(0, looting) * 0.01F, 0.0F, 0.25F);
    }

    static Optional<Item> headItem(final EntityType<?> type) {
        if (type == EntityTypes.SKELETON) {
            return Optional.of(Items.SKELETON_SKULL);
        }
        if (type == EntityTypes.ZOMBIE) {
            return Optional.of(Items.ZOMBIE_HEAD);
        }
        if (type == EntityTypes.CREEPER) {
            return Optional.of(Items.CREEPER_HEAD);
        }
        if (type == EntityTypes.PIGLIN || type == EntityTypes.PIGLIN_BRUTE) {
            return Optional.of(Items.PIGLIN_HEAD);
        }
        return Optional.empty();
    }

    private static Optional<ItemStack> headStack(final LivingEntity target) {
        if (target instanceof Player player) {
            final ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
            return Optional.of(head);
        }
        return headItem(target.getType()).map(ItemStack::new);
    }

    private static ItemEntity entity(final LivingEntity target, final ItemStack stack) {
        return new ItemEntity(target.level(), target.getX(), target.getY(), target.getZ(), stack);
    }

    private record BonusDrop(
        net.minecraft.tags.TagKey<EntityType<?>> sources,
        String itemId,
        float baseChance
    ) {
    }
}
