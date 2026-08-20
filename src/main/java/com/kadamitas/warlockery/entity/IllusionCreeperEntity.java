package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.MimicryRules.Species;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Hollow Fuse. It is latent and silent until one observed actor comes close, then walks in,
 * telegraphs a blast it does not own, holds, and collapses into nothing at all.
 *
 * <p>It never explodes, never swells, never ignites, never edits a block, never knocks anything
 * back and never deals damage. Recognition ends it: sustained facing with sight, or one accepted
 * hit, collapses the episode from any phase with the pending telegraph never emitted afterwards.
 * The collapse never removes the entity, so an ordinary kill still rolls the exact existing loot
 * table including the killed-by-player heart pool.</p>
 */
public final class IllusionCreeperEntity extends AbstractMimicEntity {

    public IllusionCreeperEntity(final EntityType<? extends Monster> type, final Level level) {
        super(type, level, Species.HOLLOW_FUSE);
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.ILLUSION_CREEPER;
    }

    @Override
    protected SoundEvent ambientSound() {
        return SoundEvents.CREEPER_HURT;
    }
}


