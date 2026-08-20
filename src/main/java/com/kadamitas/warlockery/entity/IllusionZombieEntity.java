package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.MimicryRules.Species;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Hollow Decoy. It takes a station beside an existing hostile mob without ever writing to that
 * mob, draws one observer's attention, and answers a hit with nothing: no retaliation, no flight,
 * no alert and no reinforcement. The second accepted hit unmasks it.
 *
 * <p>Every hit spent on the decoy is a hit not spent elsewhere, and that is its entire payload. The
 * anchor is read only: its position is sampled to derive one station point and nothing else is read
 * from it, and nothing is ever written to it.</p>
 */
public final class IllusionZombieEntity extends AbstractMimicEntity {

    public IllusionZombieEntity(final EntityType<? extends Monster> type, final Level level) {
        super(type, level, Species.HOLLOW_DECOY);
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.ILLUSION_ZOMBIE;
    }

    @Override
    protected SoundEvent ambientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }
}


