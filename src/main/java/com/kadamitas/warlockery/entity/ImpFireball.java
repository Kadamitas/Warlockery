package com.kadamitas.warlockery.entity;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class ImpFireball extends SmallFireball {
    public static final TagKey<Block> PROTECTED_BLOCKS = TagKey.create(
        Registries.BLOCK,
        Identifier.fromNamespaceAndPath("warlockery", "ai/imp_fireball_protected")
    );

    public ImpFireball(final Level level, final LivingEntity owner, final Vec3 direction) {
        super(level, owner, direction);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected boolean canHitEntity(final Entity entity) {
        return super.canHitEntity(entity) && !isEffectiveAlly(entity);
    }

    @Override
    protected void onHitBlock(final BlockHitResult hitResult) {
        if (firePlacementProtected(hitResult)) {
            final BlockState state = level().getBlockState(hitResult.getBlockPos());
            state.onProjectileHit(level(), state, hitResult, this);
            return;
        }
        super.onHitBlock(hitResult);
    }

    private boolean firePlacementProtected(final BlockHitResult hitResult) {
        final BlockPos struck = hitResult.getBlockPos();
        final BlockPos firePosition = struck.relative(hitResult.getDirection());
        return level().getBlockState(struck).is(PROTECTED_BLOCKS)
            || level().getBlockState(firePosition).is(PROTECTED_BLOCKS);
    }

    private boolean isEffectiveAlly(final Entity entity) {
        final Entity shooter = getOwner();
        if (shooter == null || !(entity instanceof LivingEntity)) {
            return false;
        }
        final Optional<UUID> shooterOwner = CreatureBehaviorState.owner(shooter);
        if (shooterOwner.isEmpty()) {
            return false;
        }
        final UUID ownerId = shooterOwner.orElseThrow();
        return entity.getUUID().equals(ownerId)
            || CreatureBehaviorState.isOwnedBy(entity, ownerId);
    }
}
