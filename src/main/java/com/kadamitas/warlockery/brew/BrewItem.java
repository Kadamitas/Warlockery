package com.kadamitas.warlockery.brew;

import com.kadamitas.warlockery.brew.custom.CustomBrewRuntime;
import java.util.Objects;
import net.minecraft.core.Position;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

public final class BrewItem extends SplashPotionItem {
    private final BrewKind kind;

    public BrewItem(final Item.Properties properties, final BrewKind kind) {
        super(configure(properties, kind));
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public BrewKind kind() {
        return kind;
    }

    @Override
    public Component getName(final ItemStack stack) {
        return displayName(descriptionId);
    }

    static Component displayName(final String descriptionId) {
        return Component.translatable(Objects.requireNonNull(descriptionId, "descriptionId"));
    }

    public BrewRuntime.ImpactResult onImpact(
        final ServerLevel level,
        final ItemStack stack,
        final HitResult hit,
        final @Nullable Entity directSource,
        final @Nullable Entity owner
    ) {
        final var customFormula = CustomBrewRuntime.read(stack);
        if (customFormula.isPresent()) {
            return CustomBrewRuntime.handleImpact(
                level,
                customFormula.orElseThrow(),
                hit.getLocation(),
                directSource,
                owner
            );
        }
        final BrewKind impactKind = stack.getItem() instanceof BrewItem item ? item.kind() : kind;
        final BrewRuntime.ImpactResult result = BrewRuntime.handleImpact(
            level, impactKind, hit.getLocation(), directSource, owner
        );
        if (impactKind.returnsAfterImpact() && directSource != null
            && directSource.spawnAtLocation(level, stack.copyWithCount(1), 0.25F) != null) {
            return result.plus(BrewRuntime.ImpactResult.event());
        }
        return result;
    }

    @Override
    public int getBurnTime(final ItemStack itemStack, final @Nullable RecipeType<?> recipeType) {
        final int burnTime = kind.fuelBurnTime();
        return burnTime > 0 ? burnTime : super.getBurnTime(itemStack, recipeType);
    }

    @Override
    protected AbstractThrownPotion createPotion(
        final ServerLevel level,
        final LivingEntity owner,
        final ItemStack itemStack
    ) {
        return new BrewThrownSplashPotion(level, owner, itemStack);
    }

    @Override
    protected AbstractThrownPotion createPotion(
        final Level level,
        final Position position,
        final ItemStack itemStack
    ) {
        return new BrewThrownSplashPotion(level, position.x(), position.y(), position.z(), itemStack);
    }

    private static Item.Properties configure(final Item.Properties properties, final BrewKind kind) {
        return properties.stacksTo(16)
            .component(DataComponents.POTION_CONTENTS, kind.potionContents())
            .component(DataComponents.POTION_DURATION_SCALE, 1.0F);
    }

    private static final class BrewThrownSplashPotion extends ThrownSplashPotion {
        private BrewThrownSplashPotion(
            final Level level,
            final LivingEntity owner,
            final ItemStack itemStack
        ) {
            super(level, owner, itemStack);
        }

        private BrewThrownSplashPotion(
            final Level level,
            final double x,
            final double y,
            final double z,
            final ItemStack itemStack
        ) {
            super(level, x, y, z, itemStack);
        }

        @Override
        public void onHitAsPotion(final ServerLevel level, final ItemStack potionItem, final HitResult hitResult) {
            CustomBrewRuntime.read(potionItem).ifPresentOrElse(
                formula -> CustomBrewRuntime.applyEffects(
                    level,
                    formula,
                    hitResult.getLocation(),
                    this,
                    getOwner()
                ),
                () -> super.onHitAsPotion(level, potionItem, hitResult)
            );
        }

        @Override
        protected void onHit(final HitResult hitResult) {
            final ItemStack potionItem = getItem().copy();
            super.onHit(hitResult);
            if (level() instanceof ServerLevel level && potionItem.getItem() instanceof BrewItem brewItem) {
                brewItem.onImpact(level, potionItem, hitResult, this, getOwner());
            }
        }
    }
}
