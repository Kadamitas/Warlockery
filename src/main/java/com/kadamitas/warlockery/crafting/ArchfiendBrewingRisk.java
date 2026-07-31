package com.kadamitas.warlockery.crafting;

import com.kadamitas.warlockery.brew.CauldronChalkCircles;
import com.kadamitas.warlockery.entity.CreatureBehaviorRules;
import com.kadamitas.warlockery.entity.CreatureBehaviorTags;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class ArchfiendBrewingRisk {
    private static final int BASE_RANGE = 12;
    private static final int MAX_EXTENDERS = 2;

    private ArchfiendBrewingRisk() {
    }

    public static RiskProfile profile(final int nearbyExtenders) {
        return profile(nearbyExtenders, CauldronChalkCircles.State.EMPTY);
    }

    public static RiskProfile profile(
        final int nearbyExtenders,
        final CauldronChalkCircles.State circles
    ) {
        final int extenders = Math.clamp(nearbyExtenders, 0, MAX_EXTENDERS);
        return new RiskProfile(
            extenders,
            CreatureBehaviorRules.altarSearchRange(BASE_RANGE, extenders),
            Math.clamp(extenders * 0.125F + circles.riskDelta(), 0.0F, 0.75F)
        );
    }

    public static boolean shouldBackfire(final RiskProfile profile, final float roll) {
        if (roll < 0.0F || roll >= 1.0F) {
            throw new IllegalArgumentException("Risk roll must be between zero inclusive and one exclusive");
        }
        return profile.chance() > 0.0F && roll < profile.chance();
    }

    public static boolean apply(final ServerLevel level, final BlockPos cauldron) {
        return apply(level, cauldron, CauldronChalkCircles.State.EMPTY);
    }

    public static boolean apply(
        final ServerLevel level,
        final BlockPos cauldron,
        final CauldronChalkCircles.State circles
    ) {
        final RiskProfile profile = profile(nearbyExtenders(level, cauldron), circles);
        if (!shouldBackfire(profile, level.getRandom().nextFloat())) {
            return false;
        }
        final List<Holder<MobEffect>> backfires = backfires();
        final Holder<MobEffect> effect = backfires.get(level.getRandom().nextInt(backfires.size()));
        level.getEntitiesOfClass(
            Player.class,
            new AABB(cauldron).inflate(profile.effectRange()),
            Player::isAlive
        ).forEach(player -> {
            player.addEffect(new MobEffectInstance(effect, 300, Math.max(0, profile.extenders() - 1)));
            player.sendOverlayMessage(Component.translatable(
                circles.infernalWeight() > 0
                    ? "message.warlockery.cauldron.chalk_backfire"
                    : "message.warlockery.cauldron.archfiend_backfire"
            ));
        });
        level.sendParticles(
            ParticleTypes.SOUL_FIRE_FLAME,
            cauldron.getX() + 0.5,
            cauldron.getY() + 1.0,
            cauldron.getZ() + 0.5,
            24,
            0.8,
            0.6,
            0.8,
            0.04
        );
        level.playSound(null, cauldron, SoundEvents.EVOKER_CAST_SPELL, SoundSource.BLOCKS, 1.0F, 0.65F);
        return true;
    }

    private static int nearbyExtenders(final ServerLevel level, final BlockPos cauldron) {
        return (int) level.getEntitiesOfClass(
            Mob.class,
            new AABB(cauldron).inflate(32.0),
            creature -> creature.isAlive()
                && creature.typeHolder().is(CreatureBehaviorTags.EntityTypes.CAULDRON_RANGE_EXTENDERS)
        ).stream().limit(MAX_EXTENDERS).count();
    }

    private static List<Holder<MobEffect>> backfires() {
        return List.of(
            MobEffects.WEAKNESS,
            MobEffects.NAUSEA,
            MobEffects.POISON,
            MobEffects.DARKNESS
        );
    }

    public record RiskProfile(int extenders, int effectRange, float chance) {
        public RiskProfile {
            if (extenders < 0 || effectRange < 1 || chance < 0.0F || chance >= 1.0F) {
                throw new IllegalArgumentException("Invalid archfiend brewing risk profile");
            }
        }
    }
}
