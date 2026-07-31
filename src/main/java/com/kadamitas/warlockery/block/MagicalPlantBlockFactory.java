package com.kadamitas.warlockery.block;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class MagicalPlantBlockFactory {
    private static final Map<String, Behavior> BEHAVIORS = Map.of(
        "embermoss", Behavior.EMBER_MOSS,
        "glintweed", Behavior.GLINT_WEED,
        "leapinglily", Behavior.LEAPING_LILY,
        "bramble", Behavior.ENDER_BRAMBLE,
        "bloodrose", Behavior.BLOOD_POPPY,
        "crittersnare", Behavior.CRITTER_SNARE,
        "grassper", Behavior.GRASSPER,
        "spanishmoss", Behavior.SPANISH_MOSS
    );

    private MagicalPlantBlockFactory() {
    }

    public static boolean supports(final String id) {
        return BEHAVIORS.containsKey(id);
    }

    public static Optional<Behavior> behaviorOf(final String id) {
        return Optional.ofNullable(BEHAVIORS.get(id));
    }

    public static Set<String> supportedIds() {
        return BEHAVIORS.keySet();
    }

    public static int lightLevel(final String id) {
        return behaviorOf(id).map(Behavior::lightLevel).orElse(0);
    }

    public static Block create(final String id, final BlockBehaviour.Properties properties) {
        final Behavior behavior = behaviorOf(id)
            .orElseThrow(() -> new IllegalArgumentException("Unsupported magical plant: " + id));
        properties.noCollision().noOcclusion().instabreak().sound(SoundType.GRASS);
        if (behavior == Behavior.ENDER_BRAMBLE) {
            properties.strength(4.0F, 6.0F).requiresCorrectToolForDrops();
        }
        if (behavior.randomlyTicks()) {
            properties.randomTicks();
        }
        if (behavior.lightLevel() > 0) {
            properties.lightLevel(_ -> behavior.lightLevel());
        }
        return switch (behavior) {
            case GLINT_WEED -> new GlintWeedBlock(properties);
            case BLOOD_POPPY -> new BloodPoppyBlock(properties);
            case CRITTER_SNARE -> new CritterSnareBlock(properties);
            case GRASSPER -> new GrassperBlock(properties);
            case SPANISH_MOSS -> new SpanishMossBlock(properties.randomTicks());
            default -> new MagicalPlantBlock(behavior, properties);
        };
    }

    public enum Behavior {
        EMBER_MOSS(12, true, 8),
        GLINT_WEED(15, true, 12),
        LEAPING_LILY(0, false, 0),
        ENDER_BRAMBLE(0, true, 16),
        BLOOD_POPPY(0, false, 0),
        CRITTER_SNARE(0, false, 0),
        GRASSPER(0, false, 0),
        SPANISH_MOSS(0, true, 0);

        private final int lightLevel;
        private final boolean randomlyTicks;
        private final int spreadChance;

        Behavior(final int lightLevel, final boolean randomlyTicks, final int spreadChance) {
            this.lightLevel = lightLevel;
            this.randomlyTicks = randomlyTicks;
            this.spreadChance = spreadChance;
        }

        public int lightLevel() {
            return lightLevel;
        }

        public boolean randomlyTicks() {
            return randomlyTicks;
        }

        public boolean spreads() {
            return spreadChance > 0;
        }

        public int spreadChance() {
            return spreadChance;
        }
    }
}
