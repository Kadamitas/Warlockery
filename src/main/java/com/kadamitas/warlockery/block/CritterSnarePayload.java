package com.kadamitas.warlockery.block;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.cubemob.Slime;

public enum CritterSnarePayload implements StringRepresentable, StringIdentified {
    EMPTY("empty"),
    BAT("bat"),
    SILVERFISH("silverfish"),
    SLIME("slime"),
    MAGMA_CUBE("magma_cube");

    private static final EnumLookup<CritterSnarePayload> LOOKUP = EnumLookup.create("critter snare payload", values());
    private final String id;

    CritterSnarePayload(final String id) {
        this.id = id;
    }

    public static Optional<CritterSnarePayload> from(final Entity entity) {
        if (entity instanceof Bat) {
            return Optional.of(BAT);
        }
        if (entity instanceof Silverfish) {
            return Optional.of(SILVERFISH);
        }
        if (entity instanceof MagmaCube magmaCube && magmaCube.getSize() <= 1) {
            return Optional.of(MAGMA_CUBE);
        }
        if (entity instanceof Slime slime && slime.getSize() <= 1) {
            return Optional.of(SLIME);
        }
        return Optional.empty();
    }

    public static CritterSnarePayload byId(final String id) {
        return LOOKUP.findOrElse(id, EMPTY);
    }

    public Optional<LivingEntity> create(final ServerLevel level) {
        final Entity created = switch (this) {
            case BAT -> EntityTypes.BAT.create(level, EntitySpawnReason.EVENT);
            case SILVERFISH -> EntityTypes.SILVERFISH.create(level, EntitySpawnReason.EVENT);
            case SLIME -> EntityTypes.SLIME.create(level, EntitySpawnReason.EVENT);
            case MAGMA_CUBE -> EntityTypes.MAGMA_CUBE.create(level, EntitySpawnReason.EVENT);
            case EMPTY -> null;
        };
        if (created instanceof Slime slime) {
            slime.setSize(1, true);
        }
        return created instanceof LivingEntity living ? Optional.of(living) : Optional.empty();
    }

    public boolean occupied() {
        return this != EMPTY;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    @Override
    public String id() {
        return id;
    }
}
