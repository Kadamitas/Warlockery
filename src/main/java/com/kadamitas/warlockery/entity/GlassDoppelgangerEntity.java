package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.entity.MimicryRules.Species;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The Presented Likeness. It binds one living subject, presents that subject through a closed
 * allow-list of exactly two members, and shadows it at a deliberate band it never closes.
 *
 * <p>No player data is read or written, ever: not inventory, equipment stacks, item components,
 * enchantments, NBT, persistent data, advancements, statistics, permission level, gamemode,
 * experience, hunger, attributes or active effects. The copied surface is exactly the two members
 * of {@link MimicryPresentation}. That is the family signature, and it replaces the previous
 * reflection loop that copied six equipment stacks off a player every second for the
 * entity's whole life.</p>
 *
 * <p>It is the only mimic that ever deals damage, and only reactively: a fresh accepted-damage
 * attribution opens a bounded confrontation window, and nothing else ever mints a target.</p>
 */
public final class GlassDoppelgangerEntity extends AbstractMimicEntity {

    public GlassDoppelgangerEntity(final EntityType<? extends Monster> type, final Level level) {
        super(type, level, Species.PRESENTED_LIKENESS);
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.GLASS_DOPPELGANGER;
    }

    @Override
    protected SoundEvent ambientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }
}

