package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.Level;

public final class BrewSatchelItem extends BundleItem {
    public BrewSatchelItem(final Properties properties) {
        super(properties);
    }

    @Override
    public boolean overrideStackedOnOther(
        final ItemStack self,
        final Slot slot,
        final ClickAction action,
        final Player player
    ) {
        return action != ClickAction.PRIMARY || slot.getItem().isEmpty() || slot.getItem().is(WarlockeryTags.Items.BREWS)
            ? super.overrideStackedOnOther(self, slot, action, player)
            : false;
    }

    @Override
    public boolean overrideOtherStackedOnMe(
        final ItemStack self,
        final ItemStack other,
        final Slot slot,
        final ClickAction action,
        final Player player,
        final SlotAccess carried
    ) {
        return other.isEmpty() || other.is(WarlockeryTags.Items.BREWS)
            ? super.overrideOtherStackedOnMe(self, other, slot, action, player, carried)
            : false;
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack satchel = player.getItemInHand(hand);
        final BundleContents contents = satchel.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        final Optional<ItemStack> selected = selected(contents);
        final BrewSatchelRules.Diagnostic diagnostic = BrewSatchelRules.diagnose(
            selected.isPresent(),
            selected.filter(stack -> stack.is(WarlockeryTags.Items.BREWS)).isPresent(),
            selected.filter(stack -> stack.getItem() instanceof ProjectileItem).isPresent()
        );
        if (diagnostic == BrewSatchelRules.Diagnostic.EMPTY) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.warlockery.brew_satchel.empty"));
            }
            return InteractionResult.FAIL;
        }
        if (diagnostic == BrewSatchelRules.Diagnostic.INVALID_BREW) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.warlockery.brew_satchel.invalid"));
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel) {
            final ItemStack brew = extractOne(satchel).orElseThrow();
            final ProjectileItem projectileItem = (ProjectileItem) brew.getItem();
            final Projectile projectile = projectileItem.asProjectile(
                serverLevel,
                player.position(),
                brew,
                player.getDirection()
            );
            projectile.setOwner(player);
            Projectile.spawnProjectile(
                projectile,
                serverLevel,
                brew,
                launched -> launched.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0F, 0.5F, 1.0F)
            );
            serverLevel.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.SPLASH_POTION_THROW,
                SoundSource.PLAYERS,
                0.5F,
                0.8F + serverLevel.getRandom().nextFloat() * 0.4F
            );
            player.awardStat(Stats.ITEM_USED.get(this));
            player.sendOverlayMessage(Component.translatable("message.warlockery.brew_satchel.thrown"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onUseTick(
        final Level level,
        final LivingEntity livingEntity,
        final ItemStack itemStack,
        final int ticksRemaining
    ) {
    }

    private static Optional<ItemStack> selected(final BundleContents contents) {
        final var selected = contents.getSelectedItem();
        return selected == null
            ? contents.itemCopyStream().findFirst()
            : Optional.of(selected.create());
    }

    private static Optional<ItemStack> extractOne(final ItemStack satchel) {
        final BundleContents contents = satchel.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        final BundleContents.Mutable mutable = new BundleContents.Mutable(contents);
        final ItemStack selected = mutable.removeOne();
        if (selected == null) {
            return Optional.empty();
        }
        final ItemStack projectile = selected.split(1);
        if (!selected.isEmpty()) {
            mutable.tryInsert(selected);
        }
        satchel.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
        return Optional.of(projectile);
    }
}
