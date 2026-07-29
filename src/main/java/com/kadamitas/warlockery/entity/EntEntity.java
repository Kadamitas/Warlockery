package com.kadamitas.warlockery.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class EntEntity extends IronGolem implements ArcaneCreature {
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(
        EntEntity.class,
        EntityDataSerializers.INT
    );
    private boolean variantInitialized;

    public EntEntity(final EntityType<? extends IronGolem> type, final Level level) {
        super(type, level);
    }

    @Override
    public CreatureKind creatureKind() {
        return CreatureKind.ENT;
    }

    public EntVariant variant() {
        return EntVariant.byOrdinal(entityData.get(DATA_VARIANT));
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, EntVariant.OAK.ordinal());
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        if (!variantInitialized) {
            final String biome = level.registryAccess().lookupOrThrow(Registries.BIOME)
                .getKey(level.getBiome(this.blockPosition()).value()).toString();
            initializeVariant(EntVariant.fromBiome(biome));
        }
        if ((getTarget() == null || !getTarget().isAlive())
            && CreatureBehaviorRules.shouldPulse(tickCount, getId(), 20)) {
            level.getEntitiesOfClass(
                    Player.class,
                    getBoundingBox().inflate(Math.sqrt(CreatureBehaviorRules.ENT_INTRUSION_DISTANCE_SQUARED)),
                    player -> player.isAlive() && !player.isCreative() && !player.isSpectator()
                ).stream()
                .min(java.util.Comparator.comparingDouble(this::distanceToSqr))
                .ifPresent(this::setTarget);
        }
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("WarlockeryEntVariant", variant().serializedName());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        input.getString("WarlockeryEntVariant")
            .map(EntVariant::fromSerializedName)
            .ifPresent(this::initializeVariant);
    }

    private void initializeVariant(final EntVariant selected) {
        entityData.set(DATA_VARIANT, selected.ordinal());
        setCustomName(Component.translatable("entity.warlockery.ent.variant." + selected.serializedName()));
        applyTraits(selected.traits());
        variantInitialized = true;
    }

    private void applyTraits(final EntTraits traits) {
        final double previousMaximum = getMaxHealth();
        final float healthRatio = previousMaximum <= 0.0 ? 1.0F : getHealth() / (float) previousMaximum;
        setBaseValue(Attributes.MAX_HEALTH, traits.maxHealth());
        setBaseValue(Attributes.ATTACK_DAMAGE, traits.attackDamage());
        setBaseValue(Attributes.MOVEMENT_SPEED, traits.movementSpeed());
        setBaseValue(Attributes.ARMOR, traits.armor());
        setHealth(Math.clamp((float) (traits.maxHealth() * healthRatio), 1.0F, getMaxHealth()));
    }

    private void setBaseValue(
        final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
        final double value
    ) {
        final AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    public enum EntVariant {
        OAK("oak", 0xFFFFFFFF, new EntTraits(100.0, 15.0, 0.25, 2.0)),
        BIRCH("birch", 0xFFF1E4B8, new EntTraits(88.0, 13.0, 0.29, 1.0)),
        SPRUCE("spruce", 0xFF77906A, new EntTraits(112.0, 16.0, 0.23, 4.0)),
        JUNGLE("jungle", 0xFF5FAF61, new EntTraits(104.0, 17.0, 0.27, 2.0)),
        DARK_OAK("dark_oak", 0xFF73583F, new EntTraits(120.0, 18.0, 0.21, 6.0)),
        ACACIA("acacia", 0xFFE08A52, new EntTraits(92.0, 15.0, 0.30, 1.0)),
        MANGROVE("mangrove", 0xFF8A554C, new EntTraits(110.0, 14.0, 0.22, 5.0)),
        CHERRY("cherry", 0xFFF1A8B8, new EntTraits(90.0, 13.0, 0.28, 1.0)),
        PALE_OAK("pale_oak", 0xFFD8DED1, new EntTraits(108.0, 16.0, 0.24, 3.0));

        private final String serializedName;
        private final int tint;
        private final EntTraits traits;

        EntVariant(final String serializedName, final int tint, final EntTraits traits) {
            this.serializedName = serializedName;
            this.tint = tint;
            this.traits = traits;
        }

        public String serializedName() {
            return serializedName;
        }

        public int tint() {
            return tint;
        }

        public EntTraits traits() {
            return traits;
        }

        static EntVariant fromBiome(final String biome) {
            if (biome.contains("cherry")) return CHERRY;
            if (biome.contains("pale_garden")) return PALE_OAK;
            if (biome.contains("mangrove")) return MANGROVE;
            if (biome.contains("dark_forest")) return DARK_OAK;
            if (biome.contains("jungle")) return JUNGLE;
            if (biome.contains("savanna")) return ACACIA;
            if (biome.contains("birch")) return BIRCH;
            if (biome.contains("taiga") || biome.contains("grove")) return SPRUCE;
            return OAK;
        }

        static EntVariant fromSerializedName(final String name) {
            for (final EntVariant candidate : values()) {
                if (candidate.serializedName.equals(name)) {
                    return candidate;
                }
            }
            return OAK;
        }

        static EntVariant byOrdinal(final int ordinal) {
            final EntVariant[] variants = values();
            return ordinal >= 0 && ordinal < variants.length ? variants[ordinal] : OAK;
        }
    }

    public record EntTraits(
        double maxHealth,
        double attackDamage,
        double movementSpeed,
        double armor
    ) {
        public EntTraits {
            if (maxHealth < 1.0 || attackDamage < 0.0 || movementSpeed <= 0.0 || armor < 0.0) {
                throw new IllegalArgumentException("Ent traits must be safe positive combat values");
            }
        }
    }
}
