package com.kadamitas.warlockery.ritual.marriage;

import com.kadamitas.warlockery.Warlockery;
import com.kadamitas.warlockery.entity.NamiEntity;
import com.kadamitas.warlockery.registry.ModItems;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class MarriageRuntime {
    private static final Identifier HEART_ID = Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "marriage_heart");
    private static final AttributeModifier HEART = new AttributeModifier(
        HEART_ID,
        2.0,
        AttributeModifier.Operation.ADD_VALUE
    );

    private MarriageRuntime() {
    }

    public static void tick(final Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.tickCount % 20 != 0) {
            return;
        }
        final MarriageData data = MarriageData.get((ServerLevel) player.level());
        final var health = player.getAttribute(Attributes.MAX_HEALTH);
        final Optional<MarriageData.Bond> bond = data.bond(player.getUUID());
        if (bond.isEmpty()) {
            if (health != null && health.removeModifier(HEART_ID)) {
                player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
            }
            return;
        }
        if (health != null && !health.hasModifier(HEART_ID)) {
            health.addTransientModifier(HEART);
        }
        resolvePartner(serverPlayer, bond.orElseThrow()).filter(partner -> partner.distanceToSqr(player) <= 576.0).ifPresent(partner -> {
            player.addEffect(new MobEffectInstance(MobEffects.HASTE, 40, 0, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, true, false, true));
            if (partner instanceof NamiEntity nami) {
                nami.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, true, false, true));
            }
        });
    }

    public static Optional<Entity> resolvePartner(final ServerPlayer player, final MarriageData.Bond bond) {
        if (bond.isPlayer()) {
            return Optional.ofNullable(player.level().getServer().getPlayerList().getPlayer(bond.partnerUuid()));
        }
        for (final ServerLevel level : player.level().getServer().getAllLevels()) {
            final Entity entity = level.getEntity(bond.partnerUuid());
            if (entity != null) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }

    public static Optional<NamiEntity> nearestUnmarriedNami(
        final ServerLevel level,
        final net.minecraft.core.BlockPos center,
        final int radius
    ) {
        final MarriageData data = MarriageData.get(level);
        return level.getEntitiesOfClass(
                NamiEntity.class,
                new AABB(center).inflate(radius),
                nami -> data.ownerForNami(nami.getUUID()).isEmpty()
            ).stream()
            .min(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(center))));
    }

    public static boolean isWeddingRing(final net.minecraft.world.item.ItemStack stack) {
        return stack.is(ModItems.ALL.get("wedding_ring").get());
    }
}
