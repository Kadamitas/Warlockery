package com.kadamitas.warlockery.brew;

import com.kadamitas.warlockery.block.ConnectedGlyphBlock;
import com.kadamitas.warlockery.brew.custom.CustomBrewFormula;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class CauldronChalkCircles {
    private static final List<Offset> SMALL_OFFSETS = List.of(
        new Offset(-1, -2), new Offset(0, -2), new Offset(1, -2),
        new Offset(-2, -1), new Offset(2, -1),
        new Offset(-2, 0), new Offset(2, 0),
        new Offset(-2, 1), new Offset(2, 1),
        new Offset(-1, 2), new Offset(0, 2), new Offset(1, 2)
    );
    private static final List<Offset> MEDIUM_OFFSETS = List.of(
        new Offset(-2, -4), new Offset(-1, -4), new Offset(0, -4), new Offset(1, -4), new Offset(2, -4),
        new Offset(-3, -3), new Offset(3, -3),
        new Offset(-4, -2), new Offset(4, -2),
        new Offset(-4, -1), new Offset(4, -1),
        new Offset(-4, 0), new Offset(4, 0),
        new Offset(-4, 1), new Offset(4, 1),
        new Offset(-4, 2), new Offset(4, 2),
        new Offset(-3, 3), new Offset(3, 3),
        new Offset(-2, 4), new Offset(-1, 4), new Offset(0, 4), new Offset(1, 4), new Offset(2, 4)
    );

    private CauldronChalkCircles() {
    }

    public static State inspect(final BlockGetter level, final BlockPos cauldron) {
        final LinkedHashSet<Offset> ritual = new LinkedHashSet<>();
        final LinkedHashSet<Offset> infernal = new LinkedHashSet<>();
        final LinkedHashSet<Offset> other = new LinkedHashSet<>();
        allOffsets().forEach(offset -> {
            final BlockState state = level.getBlockState(offset.at(cauldron));
            if (state.is(ModBlocks.ALL.get("circleglyphritual").get())) {
                ritual.add(offset);
            } else if (state.is(ModBlocks.ALL.get("circleglyphinfernal").get())) {
                infernal.add(offset);
            } else if (state.getBlock() instanceof ConnectedGlyphBlock) {
                other.add(offset);
            }
        });
        return evaluate(ritual, infernal, other);
    }

    public static State evaluate(
        final Set<Offset> ritual,
        final Set<Offset> infernal,
        final Set<Offset> other
    ) {
        return new State(
            diagnose(SMALL_OFFSETS, ritual, infernal, other),
            diagnose(MEDIUM_OFFSETS, ritual, infernal, other)
        );
    }

    public static List<Offset> offsets(final Size size) {
        return size == Size.SMALL ? SMALL_OFFSETS : MEDIUM_OFFSETS;
    }

    public static CustomBrewFormula influence(final CustomBrewFormula formula, final State circles) {
        final int amplifierBonus = circles.infernalWeight() / 2;
        final List<BrewEffectSpec> effects = formula.effects().stream()
            .map(effect -> new BrewEffectSpec(
                effect.effect(),
                effect.duration(),
                Math.clamp(effect.amplifier() + amplifierBonus, 0, 255)
            ))
            .toList();
        return new CustomBrewFormula(
            formula.components(),
            formula.selectedEffects(),
            formula.delivery(),
            effects,
            formula.behaviors(),
            formula.capacity(),
            formula.capacityCost(),
            formula.powerLevel(),
            formula.durationMultiplier(),
            formula.extent(),
            formula.lingering(),
            formula.altarPower(),
            formula.color(),
            Math.clamp(formula.radius(), 0.5F, 12.0F),
            Math.clamp(formula.potency() * circles.potencyMultiplier(), 0.1F, 8.0F),
            formula.hideParticles(),
            formula.skipBlocks(),
            formula.skipEntities(),
            formula.uncappedDamage(),
            formula.quaff()
        );
    }

    private static Ring diagnose(
        final List<Offset> expected,
        final Set<Offset> ritual,
        final Set<Offset> infernal,
        final Set<Offset> other
    ) {
        final int ritualMarks = matching(expected, ritual);
        final int infernalMarks = matching(expected, infernal);
        final int otherMarks = matching(expected, other);
        final int present = ritualMarks + infernalMarks + otherMarks;
        final RingKind kind;
        if (ritualMarks == expected.size()) {
            kind = RingKind.RITUAL;
        } else if (infernalMarks == expected.size()) {
            kind = RingKind.INFERNAL;
        } else if (present == 0) {
            kind = RingKind.EMPTY;
        } else if (present == expected.size()) {
            kind = RingKind.MIXED;
        } else {
            kind = RingKind.INCOMPLETE;
        }
        return new Ring(kind, ritualMarks, infernalMarks, otherMarks, expected.size());
    }

    private static int matching(final List<Offset> expected, final Set<Offset> actual) {
        return (int) expected.stream().filter(actual::contains).count();
    }

    private static List<Offset> allOffsets() {
        return java.util.stream.Stream.concat(SMALL_OFFSETS.stream(), MEDIUM_OFFSETS.stream()).toList();
    }

    public enum Size {
        SMALL,
        MEDIUM
    }

    public enum RingKind implements StringIdentified {
        EMPTY("empty"),
        RITUAL("ritual"),
        INFERNAL("infernal"),
        INCOMPLETE("incomplete"),
        MIXED("mixed");

        private static final EnumLookup<RingKind> LOOKUP = EnumLookup.create("cauldron ring", values());
        private static final Codec<RingKind> CODEC = LOOKUP.fallbackCodec(EMPTY);
        private final String id;

        RingKind(final String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

    }

    public record Offset(int x, int z) {
        public BlockPos at(final BlockPos center) {
            return center.offset(x, 0, z);
        }
    }

    public record Ring(RingKind kind, int ritualMarks, int infernalMarks, int otherMarks, int required) {
        private static final Codec<Ring> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RingKind.CODEC.fieldOf("kind").forGetter(Ring::kind),
            Codec.INT.fieldOf("ritual_marks").forGetter(Ring::ritualMarks),
            Codec.INT.fieldOf("infernal_marks").forGetter(Ring::infernalMarks),
            Codec.INT.fieldOf("other_marks").forGetter(Ring::otherMarks),
            Codec.INT.fieldOf("required").forGetter(Ring::required)
        ).apply(instance, Ring::new));

        public Ring {
            ritualMarks = Math.clamp(ritualMarks, 0, required);
            infernalMarks = Math.clamp(infernalMarks, 0, required);
            otherMarks = Math.clamp(otherMarks, 0, required);
            required = Math.max(1, required);
        }

        public int present() {
            return Math.min(required, ritualMarks + infernalMarks + otherMarks);
        }
    }

    public record State(Ring small, Ring medium) {
        public static final State EMPTY = new State(
            new Ring(RingKind.EMPTY, 0, 0, 0, SMALL_OFFSETS.size()),
            new Ring(RingKind.EMPTY, 0, 0, 0, MEDIUM_OFFSETS.size())
        );
        public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Ring.CODEC.fieldOf("small").forGetter(State::small),
            Ring.CODEC.fieldOf("medium").forGetter(State::medium)
        ).apply(instance, State::new));

        public boolean hasMarks() {
            return small.present() > 0 || medium.present() > 0;
        }

        public int ritualWeight() {
            return weight(small, 1, RingKind.RITUAL) + weight(medium, 2, RingKind.RITUAL);
        }

        public int infernalWeight() {
            return weight(small, 1, RingKind.INFERNAL) + weight(medium, 2, RingKind.INFERNAL);
        }

        public float potencyMultiplier() {
            return 1.0F + infernalWeight() * 0.25F;
        }

        public float stability() {
            return ritualWeight() * 0.10F;
        }

        public float mishapRisk() {
            return infernalWeight() * 0.10F;
        }

        public float riskDelta() {
            return mishapRisk() - stability();
        }

        private static int weight(final Ring ring, final int value, final RingKind expected) {
            return ring.kind() == expected ? value : 0;
        }
    }
}
