package com.kadamitas.warlockery.brew;

import com.kadamitas.warlockery.brew.custom.CustomBrewCloudRules;
import com.kadamitas.warlockery.brew.custom.CustomBrewDelivery;
import com.kadamitas.warlockery.brew.custom.CustomBrewRuntime;
import com.kadamitas.warlockery.brew.custom.CustomBrewTriggerData;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.Position;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
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
            final var formula = customFormula.orElseThrow();
            if (formula.delivery().triggered()) {
                return hit instanceof BlockHitResult blockHit
                    && CustomBrewTriggerData.get(level).arm(level, blockHit.getBlockPos(), formula, owner)
                    ? BrewRuntime.ImpactResult.event()
                    : BrewRuntime.ImpactResult.ZERO;
            }
            return CustomBrewRuntime.handleImpact(
                level,
                formula,
                hit.getLocation(),
                directSource,
                owner
            );
        }
        final BrewKind impactKind = stack.getItem() instanceof BrewItem item ? item.kind() : kind;
        final BrewRuntime.ImpactResult result = BrewRuntime.handleImpact(
            level, impactKind, hit.getLocation(), directSource, owner
        );
        if ((impactKind.returnsAfterImpact() || impactKind.recoversOnMiss() && result.equals(BrewRuntime.ImpactResult.ZERO))
            && directSource != null
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
    @SuppressWarnings("deprecation")
    public void appendHoverText(
        final ItemStack stack,
        final Item.TooltipContext context,
        final TooltipDisplay display,
        final Consumer<Component> builder,
        final TooltipFlag flag
    ) {
        final PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        if (!display.shows(DataComponents.POTION_CONTENTS) && contents.hasEffects()) {
            PotionContents.addPotionTooltip(
                contents.getAllEffects(),
                builder,
                stack.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0F),
                context.tickRate()
            );
        }
    }

    @Override
    protected AbstractThrownPotion createPotion(
        final ServerLevel level,
        final LivingEntity owner,
        final ItemStack itemStack
    ) {
        if (usesLingeringDelivery(itemStack)) {
            return new BrewThrownLingeringPotion(level, owner, itemStack);
        }
        return new BrewThrownSplashPotion(level, owner, itemStack);
    }

    @Override
    protected AbstractThrownPotion createPotion(
        final Level level,
        final Position position,
        final ItemStack itemStack
    ) {
        if (usesLingeringDelivery(itemStack)) {
            return new BrewThrownLingeringPotion(level, position.x(), position.y(), position.z(), itemStack);
        }
        return new BrewThrownSplashPotion(level, position.x(), position.y(), position.z(), itemStack);
    }

    private static boolean usesLingeringDelivery(final ItemStack stack) {
        return CustomBrewRuntime.read(stack).map(formula -> formula.delivery().lingering()).orElse(false);
    }

    private static Item.Properties configure(final Item.Properties properties, final BrewKind kind) {
        return properties.stacksTo(16)
            .component(DataComponents.POTION_CONTENTS, kind.potionContents())
            .component(DataComponents.POTION_DURATION_SCALE, 1.0F)
            .component(DataComponents.TOOLTIP_DISPLAY, brewTooltipDisplay());
    }

    static TooltipDisplay brewTooltipDisplay() {
        return TooltipDisplay.DEFAULT.withHidden(DataComponents.POTION_CONTENTS, true);
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
                formula -> {
                    if (!formula.delivery().triggered()) {
                        CustomBrewRuntime.applyEffects(
                            level,
                            formula,
                            hitResult.getLocation(),
                            this,
                            getOwner()
                        );
                    }
                },
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

    private static final class BrewThrownLingeringPotion extends ThrownLingeringPotion {
        private BrewThrownLingeringPotion(
            final Level level,
            final LivingEntity owner,
            final ItemStack itemStack
        ) {
            super(level, owner, itemStack);
        }

        private BrewThrownLingeringPotion(
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
            CustomBrewRuntime.read(potionItem).ifPresentOrElse(formula -> {
                final AreaEffectCloud cloud = new AreaEffectCloud(level, getX(), getY(), getZ());
                if (getOwner() instanceof LivingEntity owner) {
                    cloud.setOwner(owner);
                }
                final float radius = formula.delivery().cloudRadius(formula.radius());
                final int duration = formula.delivery().cloudDuration(formula.lingering());
                cloud.setRadius(radius);
                cloud.setRadiusOnUse(formula.delivery().cloudRadiusOnUse(radius));
                cloud.setDuration(duration);
                cloud.setWaitTime(formula.delivery().cloudWaitTime());
                cloud.setRadiusPerTick(formula.delivery() == CustomBrewDelivery.TRIGGER
                    ? 0.0F
                    : -radius / duration);
                cloud.applyComponentsFromItemStack(potionItem);
                cloud.setPotionContents(PotionContents.EMPTY);
                cloud.setCustomParticle(ColorParticleOption.create(
                    ParticleTypes.ENTITY_EFFECT,
                    ARGB.opaque(formula.color())
                ));
                CustomBrewCloudRules.mark(cloud, formula.delivery());
                level.addFreshEntity(cloud);
            }, () -> super.onHitAsPotion(level, potionItem, hitResult));
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
