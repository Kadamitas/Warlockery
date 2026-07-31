package com.kadamitas.warlockery.brew.custom;

import com.kadamitas.warlockery.brew.BrewFactory;
import com.kadamitas.warlockery.brew.BrewKind;
import com.kadamitas.warlockery.brew.BrewRuntime;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import org.jspecify.annotations.Nullable;

public final class CustomBrewRuntime {
    private static final String FORMULA_KEY = "WarlockeryCustomBrew";
    private static final int MAX_TARGETS = 128;

    private CustomBrewRuntime() {
    }

    public static ItemStack createOutput(final CustomBrewFormula formula) {
        final Item item = formula.delivery() == CustomBrewDelivery.DRINKABLE
            ? Items.POTION
            : BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(
                "warlockery",
                BrewFactory.itemId(BrewKind.BOTTLING)
            ));
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        final ItemStack output = new ItemStack(item);
        output.setCount(formula.outputCount());
        output.set(DataComponents.POTION_CONTENTS, formula.potionContents());
        output.set(DataComponents.POTION_DURATION_SCALE, 1.0F);
        output.set(DataComponents.CUSTOM_NAME, Component.translatable("item.warlockery.custom_brew"));
        output.set(DataComponents.LORE, new ItemLore(List.of(
            Component.translatable("tooltip.warlockery.custom_brew.effects", formula.selectedEffects().size()),
            Component.translatable(
                "tooltip.warlockery.custom_brew.delivery",
                Component.translatable("custom_brew_delivery.warlockery." + formula.delivery().id())
            ),
            Component.translatable("tooltip.warlockery.custom_brew.power", formula.powerLevel())
        )));
        if (formula.delivery() == CustomBrewDelivery.DRINKABLE) {
            output.set(DataComponents.CONSUMABLE, Consumable.builder()
                .consumeSeconds(formula.drinkSeconds())
                .animation(ItemUseAnimation.DRINK)
                .sound(SoundEvents.GENERIC_DRINK)
                .hasConsumeParticles(false)
                .build());
        }
        write(output, formula);
        return output;
    }

    public static void write(final ItemStack stack, final CustomBrewFormula formula) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.store(
            FORMULA_KEY,
            CustomBrewFormula.CODEC,
            formula
        ));
    }

    public static Optional<CustomBrewFormula> read(final ItemStack stack) {
        return read(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY));
    }

    public static Optional<CustomBrewFormula> read(final Entity entity) {
        return read(Optional.ofNullable(entity.get(DataComponents.CUSTOM_DATA)).orElse(CustomData.EMPTY));
    }

    private static Optional<CustomBrewFormula> read(final CustomData data) {
        final CompoundTag tag = data.copyTag();
        return tag.read(FORMULA_KEY, CustomBrewFormula.CODEC);
    }

    public static BrewRuntime.ImpactResult handleImpact(
        final ServerLevel level,
        final CustomBrewFormula formula,
        final Vec3 center,
        final @Nullable Entity directSource,
        final @Nullable Entity owner
    ) {
        return formula.behaviors().isEmpty()
            ? BrewRuntime.ImpactResult.ZERO
            : BrewRuntime.handleImpact(level, formula.behaviorKind(), center, directSource, owner);
    }

    public static BrewRuntime.ImpactResult handleImpactTo(
        final ServerLevel level,
        final CustomBrewFormula formula,
        final LivingEntity target,
        final @Nullable Entity directSource,
        final @Nullable Entity owner
    ) {
        return formula.behaviors().isEmpty()
            ? BrewRuntime.ImpactResult.ZERO
            : BrewRuntime.handleImpactTo(
                level,
                formula.behaviorKind(),
                target.position(),
                directSource,
                owner,
                target
            );
    }

    public static BrewRuntime.ImpactResult handleCloudImpactTo(
        final ServerLevel level,
        final CustomBrewFormula formula,
        final LivingEntity target,
        final Entity directSource,
        final @Nullable Entity owner
    ) {
        final var kind = formula.entityBehaviorKind();
        return kind.behaviors().isEmpty()
            ? BrewRuntime.ImpactResult.ZERO
            : BrewRuntime.handleImpactTo(level, kind, target.position(), directSource, owner, target);
    }

    public static int applyEffects(
        final ServerLevel level,
        final CustomBrewFormula formula,
        final Vec3 center,
        final @Nullable Entity directSource,
        final @Nullable Entity owner
    ) {
        if (formula.effects().isEmpty() || formula.skipEntities()) {
            return 0;
        }
        final double radius = formula.radius();
        final AABB area = AABB.ofSize(center, radius * 2.0, radius * 1.5, radius * 2.0);
        final List<LivingEntity> targets = level.getEntitiesOfClass(
            LivingEntity.class,
            area,
            entity -> entity.isAlive() && entity.isAffectedByPotions() && entity.distanceToSqr(center) <= radius * radius
        ).stream().limit(MAX_TARGETS).toList();
        for (LivingEntity target : targets) {
            final double distance = Math.sqrt(target.distanceToSqr(center));
            final double scale = Math.clamp(1.0 - distance / Math.max(0.5, radius), 0.25, 1.0);
            for (MobEffectInstance template : formula.potionContents().customEffects()) {
                applyEffect(level, formula, template, target, directSource, owner, scale);
            }
        }
        return targets.size();
    }

    public static int applyDrinkEffects(
        final ServerLevel level,
        final CustomBrewFormula formula,
        final LivingEntity drinker
    ) {
        if (formula.effects().isEmpty() || formula.skipEntities()) {
            return 0;
        }
        return (int) formula.potionContents().customEffects().stream()
            .filter(effect -> applyEffect(level, formula, effect, drinker, drinker, drinker, 1.0))
            .count();
    }

    public static int applyEffectsTo(
        final ServerLevel level,
        final CustomBrewFormula formula,
        final LivingEntity target,
        final @Nullable Entity directSource,
        final @Nullable Entity owner
    ) {
        if (formula.effects().isEmpty() || formula.skipEntities() || !target.isAffectedByPotions()) {
            return 0;
        }
        return (int) formula.potionContents().customEffects().stream()
            .filter(effect -> applyEffect(level, formula, effect, target, directSource, owner, 1.0))
            .count();
    }

    public static void handleFinishUse(final LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        read(event.getItem())
            .filter(formula -> formula.delivery() == CustomBrewDelivery.DRINKABLE)
            .ifPresent(formula -> {
                applyDrinkEffects(level, formula, event.getEntity());
                handleImpact(
                    level,
                    formula,
                    event.getEntity().position(),
                    event.getEntity(),
                    event.getEntity()
                );
            });
    }

    private static boolean applyEffect(
        final ServerLevel level,
        final CustomBrewFormula formula,
        final MobEffectInstance template,
        final LivingEntity target,
        final @Nullable Entity directSource,
        final @Nullable Entity owner,
        final double scale
    ) {
        if (template.getEffect().value() == MobEffects.INSTANT_DAMAGE.value() && !target.isInvertedHealAndHarm()) {
            final float amount = CustomBrewDamageRules.instantDamage(
                template.getAmplifier(),
                scale,
                formula.uncappedDamage()
            );
            return target.hurtServer(
                level,
                directSource == null
                    ? target.damageSources().magic()
                    : target.damageSources().indirectMagic(directSource, owner),
                amount
            );
        }
        if (template.getEffect().value().isInstantaneous()) {
            template.getEffect().value().applyInstantaneousEffect(
                level,
                directSource,
                owner,
                target,
                template.getAmplifier(),
                scale
            );
            return true;
        }
        final int duration = Math.max(21, (int) Math.round(template.getDuration() * scale));
        return target.addEffect(new MobEffectInstance(
            template.getEffect(),
            duration,
            template.getAmplifier(),
            template.isAmbient(),
            template.isVisible(),
            template.showIcon()
        ), directSource);
    }
}
