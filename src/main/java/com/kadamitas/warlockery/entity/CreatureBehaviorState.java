package com.kadamitas.warlockery.entity;

import com.kadamitas.warlockery.util.DataParsing;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

public final class CreatureBehaviorState {
    private static final String OWNER = "WarlockeryCreatureOwner";
    private static final String EMPOWERMENT = "WarlockeryCreatureEmpowerment";
    private static final String SAMPLE_BLOCK = "WarlockeryCreatureSampleBlock";
    private static final String STORED_EFFECT = "WarlockeryCreatureStoredEffect";
    private static final String STORED_EFFECT_DURATION = "WarlockeryCreatureStoredEffectDuration";
    private static final String STORED_EFFECT_AMPLIFIER = "WarlockeryCreatureStoredEffectAmplifier";
    private static final String IMP_FAVOR = "WarlockeryImpFavor";

    private CreatureBehaviorState() {
    }

    public static Optional<UUID> owner(final Entity entity) {
        return DataParsing.uuid(entity.getPersistentData().getStringOr(OWNER, ""));
    }

    public static boolean bind(final Entity entity, final UUID ownerId) {
        final Optional<UUID> current = owner(entity);
        if (current.isPresent() && !current.orElseThrow().equals(ownerId)) {
            return false;
        }
        entity.getPersistentData().putString(OWNER, ownerId.toString());
        return true;
    }

    public static boolean isOwnedBy(final Entity entity, final UUID ownerId) {
        return owner(entity).filter(ownerId::equals).isPresent();
    }

    public static void unbind(final Entity entity) {
        entity.getPersistentData().remove(OWNER);
    }

    public static int empowerment(final Entity entity) {
        return Math.clamp(
            entity.getPersistentData().getIntOr(EMPOWERMENT, 0),
            0,
            CreatureBehaviorRules.MAX_EMPOWERMENT
        );
    }

    public static EmpowermentResult empower(final Entity entity, final int amount) {
        final int before = empowerment(entity);
        final int after = CreatureBehaviorRules.empoweredLevel(before, amount);
        entity.getPersistentData().putInt(EMPOWERMENT, after);
        return new EmpowermentResult(before, after);
    }

    public static void setSampleBlock(final Entity entity, final Identifier blockId) {
        entity.getPersistentData().putString(SAMPLE_BLOCK, blockId.toString());
    }

    public static Optional<Identifier> sampleBlock(final Entity entity) {
        return DataParsing.identifier(entity.getPersistentData().getStringOr(SAMPLE_BLOCK, ""));
    }

    public static void storeEffect(final Entity entity, final StoredEffect effect) {
        entity.getPersistentData().putString(STORED_EFFECT, effect.effectId().toString());
        entity.getPersistentData().putInt(STORED_EFFECT_DURATION, effect.durationTicks());
        entity.getPersistentData().putInt(STORED_EFFECT_AMPLIFIER, effect.amplifier());
    }

    public static Optional<StoredEffect> storedEffect(final Entity entity) {
        return DataParsing.identifier(entity.getPersistentData().getStringOr(STORED_EFFECT, ""))
            .map(id -> new StoredEffect(
                id,
                Math.max(20, entity.getPersistentData().getIntOr(STORED_EFFECT_DURATION, 200)),
                Math.max(0, entity.getPersistentData().getIntOr(STORED_EFFECT_AMPLIFIER, 0))
            ));
    }

    public static void clearStoredEffect(final Entity entity) {
        entity.getPersistentData().remove(STORED_EFFECT);
        entity.getPersistentData().remove(STORED_EFFECT_DURATION);
        entity.getPersistentData().remove(STORED_EFFECT_AMPLIFIER);
    }

    public static Snapshot snapshot(final Entity entity) {
        return new Snapshot(owner(entity), empowerment(entity), sampleBlock(entity), storedEffect(entity));
    }

    public static int impFavor(final Entity entity) {
        return Math.clamp(entity.getPersistentData().getIntOr(IMP_FAVOR, 0), 0, 6);
    }

    public static int impressImp(final Entity entity) {
        final int next = Math.min(6, impFavor(entity) + 1);
        entity.getPersistentData().putInt(IMP_FAVOR, next);
        return next;
    }

    public record EmpowermentResult(int before, int after) {
        public boolean changed() {
            return after > before;
        }
    }

    public record StoredEffect(Identifier effectId, int durationTicks, int amplifier) {
        public StoredEffect {
            if (durationTicks < 1 || amplifier < 0) {
                throw new IllegalArgumentException("Stored effects require positive duration and nonnegative amplifier");
            }
        }
    }

    public record Snapshot(
        Optional<UUID> owner,
        int empowerment,
        Optional<Identifier> sampleBlock,
        Optional<StoredEffect> storedEffect
    ) {
    }
}
